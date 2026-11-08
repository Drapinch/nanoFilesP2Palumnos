package es.um.redes.nanoFiles.logic;

import java.net.InetSocketAddress;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.nio.file.Files;
import java.nio.file.Path;

import es.um.redes.nanoFiles.tcp.client.NFConnector;
import es.um.redes.nanoFiles.application.NanoFiles;
import es.um.redes.nanoFiles.tcp.server.NFServer;
import es.um.redes.nanoFiles.util.FileDigest;
import es.um.redes.nanoFiles.util.FileNameUtil;
import es.um.redes.nanoFiles.util.FileInfo;
import es.um.redes.nanoFiles.tcp.message.PeerMessage;
import es.um.redes.nanoFiles.tcp.message.PeerMessageOps;

public class NFControllerLogicP2P {
	// Servidor TCP local para compartir ficheros con otros peers
	private NFServer fileServer = null;

	protected NFControllerLogicP2P() {
	}

	/**
	 * Método para ejecutar un servidor de ficheros en segundo plano con tamaño de fragmento configurable.
	 * 
	 * @param dirLogic Objeto de lógica del directorio para registrar el servidor
	 * @param chunkSizeKB Tamaño del fragmento en KB en que se dividen los ficheros al transferir
	 * @return Verdadero si se ha arrancado con éxito y registrado en el directorio
	 */
	protected boolean startFileServer(NFControllerLogicDir dirLogic, int chunkSizeKB) {
		boolean serverRunning = false;
		if (fileServer != null) {
			System.err.println("* El servidor de ficheros ya está en ejecución en el puerto " + fileServer.getPort());
		} else {
			try {
				fileServer = new NFServer(chunkSizeKB);
				if (fileServer.getPort() > 0) {
					fileServer.startServer();
					System.out.println("* Servidor de ficheros arrancado en segundo plano, escuchando en el puerto "
							+ fileServer.getPort() + " (tamaño de fragmento configurado: " + chunkSizeKB + " KB)");
					
					// Registrar en el directorio
					if (dirLogic.registerFileServer(fileServer.getPort())) {
						serverRunning = true;
					} else {
						System.err.println("* Error: No se pudo registrar el servidor de ficheros en el directorio.");
						fileServer.stopServer();
						fileServer = null;
					}
				} else {
					System.err.println("* Error: Configuración de puerto no válida.");
				}
			} catch (java.io.IOException e) {
				System.err.println("* Error crítico al iniciar servidor de ficheros: " + e.getMessage());
				fileServer = null;
			}
		}
		return serverRunning;
	}

	protected boolean startFileServer(NFControllerLogicDir dirLogic) {
		return startFileServer(dirLogic, NFServer.DEFAULT_CHUNK_SIZE_KB);
	}

	protected void testTCPServer() {
		assert (NanoFiles.testModeTCP);
		assert (fileServer == null);
		try {
			fileServer = new NFServer();
			fileServer.test();
		} catch (IOException e1) {
			e1.printStackTrace();
			System.err.println("Cannot start the file server");
			fileServer = null;
		}
	}

	public void testTCPClient() {
		assert (NanoFiles.testModeTCP);
		try {
			InetSocketAddress testServerAddress = new InetSocketAddress("localhost", 10000);
			System.out.println("Iniciando cliente TCP de prueba hacia " + testServerAddress);
			NFConnector connector = new NFConnector(testServerAddress);
			connector.test();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Método para listar los ficheros de un peer concreto vía TCP e imprimirlos por pantalla.
	 */
	protected boolean listPeerFiles(InetSocketAddress peerAddr) {
		boolean success = false;
		try {
			NFConnector connector = new NFConnector(peerAddr);
			System.out.println("* Requesting file list from " + peerAddr);
			success = connector.getPeerFileList(); 
		} catch (IOException e) {
			System.err.println("* Error: No se pudo conectar al peer " + peerAddr + " - " + e.getMessage());
		}
		return success;
	}

	/**
	 * Descarga un fichero identificado por subcadena de hash desde uno o varios peers.
	 * Si se pasa "*" como nickname, usa el directorio para localizar los peers que tienen el hash
	 * y realiza una descarga secuencial entre ellos.
	 */
	protected boolean downloadFromPeers(NFControllerLogicDir dirLogic, String targetPeerNickname, String targetHashSubstring) {
		if (targetPeerNickname.equals("*")) {
			// 1. Buscar los peers que tienen el archivo por substring de hash
			System.out.println(" [*] Buscando servidores que compartan un fichero coincidente con: " + targetHashSubstring);
			Map<String, InetSocketAddress[]> searchResults = dirLogic.searchFilesByHash(targetHashSubstring);
			if (searchResults.isEmpty()) {
				System.err.println(" [X] No se encontraron servidores compartiendo ningún fichero que coincida con el hash: " + targetHashSubstring);
				return false;
			}
			if (searchResults.size() > 1) {
				System.err.println(" [X] Búsqueda ambigua. El hash coincide con múltiples ficheros:");
				for (String hash : searchResults.keySet()) {
					System.err.println("   - " + hash);
				}
				return false;
			}
			String fullHash = searchResults.keySet().iterator().next();
			InetSocketAddress[] serverList = searchResults.get(fullHash);
			System.out.println(" [*] Fichero encontrado con hash completo: " + fullHash);
			System.out.println(" [*] Compartido por " + serverList.length + " servidor(es).");
			
			return downloadFileFromServersSequential(serverList, targetHashSubstring, fullHash);
		} else {
			InetSocketAddress peerAddress = dirLogic.lookupUserAddress(targetPeerNickname); 
			if (peerAddress == null) {
				System.err.println("* Error: No se ha encontrado la dirección del peer '" + targetPeerNickname + "'. ¿Le pediste la lista al directorio con 'peers'?");
				return false;
			}
			InetSocketAddress[] serverList = new InetSocketAddress[] { peerAddress };
			return downloadFileFromServersSequential(serverList, targetHashSubstring, null);
		}
	}

	/**
	 * Descarga secuencial de fragmentos (chunks) desde uno o varios servidores de ficheros.
	 * Alterna secuencialmente entre los servidores disponibles para cada fragmento,
	 * respetando el tamaño de fragmento configurado en cada servidor ("serve N").
	 * Muestra un resumen detallado de las acciones llevadas a cabo.
	 */
	protected boolean downloadFileFromServersSequential(InetSocketAddress[] serverAddressList, String targetHashSubstring, String expectedFullHash) {
		if (serverAddressList == null || serverAddressList.length == 0) {
			System.err.println(" [X] Lista de servidores vacía.");
			return false;
		}

		int numServers = serverAddressList.length;
		System.out.println("\n----------------------------------------------------------------------");
		System.out.println(" [*] Iniciando descarga secuencial por fragmentos desde " + numServers + " servidor(es)...");
		System.out.println("----------------------------------------------------------------------");

		// 1. Obtener metadatos (nombre, tamaño total, hash completo) usando el primer servidor que responda
		String fileName = null;
		long totalSize = -1;
		String resolvedHash = expectedFullHash;

		for (InetSocketAddress addr : serverAddressList) {
			try {
				System.out.println(" [*] Obteniendo metadatos del fichero desde: " + addr);
				NFConnector conn = new NFConnector(addr);
				// Petición con offset 0 y size 0 para sondeo de metadatos
				PeerMessage response = conn.downloadFileChunk(targetHashSubstring, 0, 0);
				if (response != null && response.getOpcode() == PeerMessageOps.OPCODE_FILE_DATA) {
					fileName = response.getName();
					totalSize = response.getTotalFileSize();
					if (resolvedHash == null || resolvedHash.isEmpty()) {
						resolvedHash = response.getHash();
					}
					System.out.println(" [V] Metadatos obtenidos con éxito:");
					System.out.println("     - Nombre: " + fileName);
					System.out.println("     - Tamaño total: " + totalSize + " bytes");
					System.out.println("     - Hash completo: " + resolvedHash);
					break;
				} else if (response != null && response.getOpcode() == PeerMessageOps.OPCODE_ERR_AMBIGUOUS_HASH) {
					System.err.println(" [X] Error: Hash ambiguo devuelto por " + addr);
					return false;
				} else if (response != null && response.getOpcode() == PeerMessageOps.OPCODE_ERR_NOT_FOUND) {
					System.err.println(" [X] Fichero no encontrado en el servidor " + addr);
				}
			} catch (IOException e) {
				System.err.println(" [X] No se pudo obtener metadatos de " + addr + ": " + e.getMessage());
			}
		}

		if (fileName == null || totalSize < 0) {
			System.err.println(" [X] ERROR: No se pudieron obtener los metadatos del fichero de ningún servidor disponible.");
			return false;
		}

		// Manejo de fichero vacío
		if (totalSize == 0) {
			String fullPath = NanoFiles.sharedDirname + java.io.File.separator + fileName;
			Path safePath = FileNameUtil.chooseAvailableName(fullPath);
			try {
				Files.write(safePath, new byte[0]);
				System.out.println(" [V] Fichero vacío descargado y creado: " + safePath.getFileName());
				printDownloadSummary(fileName, totalSize, resolvedHash, serverAddressList,
						new LinkedHashMap<InetSocketAddress, Long>(), new LinkedHashMap<InetSocketAddress, Integer>(), safePath.toString());
				return true;
			} catch (IOException e) {
				System.err.println(" [X] Error al crear fichero vacío: " + e.getMessage());
				return false;
			}
		}

		// 2. Descarga secuencial por fragmentos alternando entre los distintos servidores
		byte[] fileBuffer = new byte[(int) totalSize];
		Map<InetSocketAddress, Long> bytesPerServer = new LinkedHashMap<>();
		Map<InetSocketAddress, Integer> chunksPerServer = new LinkedHashMap<>();
		for (InetSocketAddress addr : serverAddressList) {
			bytesPerServer.put(addr, 0L);
			chunksPerServer.put(addr, 0);
		}

		long currentOffset = 0;
		int serverIndex = 0;
		int chunkNumber = 0;

		while (currentOffset < totalSize) {
			boolean chunkDownloaded = false;

			// Intentar con el servidor en turno; si falla, probar los demás servidores para este fragmento
			for (int attempt = 0; attempt < numServers; attempt++) {
				int targetIdx = (serverIndex + attempt) % numServers;
				InetSocketAddress targetServer = serverAddressList[targetIdx];

				try {
					NFConnector conn = new NFConnector(targetServer);
					// Solicitamos el fragmento que parte de currentOffset (size -1 para que el servidor use su chunk size configurado)
					PeerMessage response = conn.downloadFileChunk(resolvedHash, currentOffset, -1);

					if (response != null && response.getOpcode() == PeerMessageOps.OPCODE_FILE_DATA) {
						byte[] chunkData = response.getFileData();
						if (chunkData != null && chunkData.length > 0) {
							System.arraycopy(chunkData, 0, fileBuffer, (int) currentOffset, chunkData.length);

							bytesPerServer.put(targetServer, bytesPerServer.get(targetServer) + chunkData.length);
							chunksPerServer.put(targetServer, chunksPerServer.get(targetServer) + 1);

							long endRange = currentOffset + chunkData.length - 1;
							System.out.println("  [->] Fragmento #" + chunkNumber + " [" + currentOffset + ".." + endRange
									+ "] (" + chunkData.length + " bytes) descargado exitosamente de " + targetServer);

							currentOffset += chunkData.length;
							chunkNumber++;
							chunkDownloaded = true;

							// Siguiente fragmento será servido por el siguiente servidor (rotación secuencial)
							serverIndex = (targetIdx + 1) % numServers;
							break;
						} else {
							System.err.println("  [!] Servidor " + targetServer + " devolvió fragmento de 0 bytes en offset " + currentOffset);
						}
					}
				} catch (IOException e) {
					System.err.println("  [!] Error descargando fragmento desde " + targetServer + ": " + e.getMessage() + ". Probando siguiente servidor...");
				}
			}

			if (!chunkDownloaded) {
				System.err.println(" [X] ERROR FATAL: No se pudo descargar el fragmento en offset " + currentOffset + " de ningún servidor.");
				return false;
			}
		}

		// 3. Guardar el archivo en el directorio local compartido sin sobreescribir existentes
		String fullPath = NanoFiles.sharedDirname + java.io.File.separator + fileName;
		Path safePath = FileNameUtil.chooseAvailableName(fullPath);
		try {
			Files.write(safePath, fileBuffer);
		} catch (IOException e) {
			System.err.println(" [X] Error de escritura en disco: " + e.getMessage());
			return false;
		}

		// 4. Verificar integridad con hash completo SHA-256
		String localHash = FileDigest.computeFileChecksumString(safePath.toString());
		if (localHash.equalsIgnoreCase(resolvedHash)) {
			System.out.println(" [V] ¡Verificación de integridad de hash OK! Checksum: " + localHash);
		} else {
			System.err.println(" [X] ¡Error de integridad de hash! Checksum local: " + localHash + ", esperado: " + resolvedHash + ". Borrando archivo corrupto.");
			try {
				Files.deleteIfExists(safePath);
			} catch (IOException e) {}
			return false;
		}

		// 5. Imprimir resumen de las acciones llevadas a cabo durante la descarga
		printDownloadSummary(fileName, totalSize, resolvedHash, serverAddressList, bytesPerServer, chunksPerServer, safePath.toString());

		return true;
	}

	private void printDownloadSummary(String fileName, long totalSize, String fileHash,
			InetSocketAddress[] servers, Map<InetSocketAddress, Long> bytesPerServer,
			Map<InetSocketAddress, Integer> chunksPerServer, String savedPath) {
		System.out.println("\n======================================================================");
		System.out.println("                   RESUMEN DE DESCARGA SECUENCIAL                     ");
		System.out.println("======================================================================");
		System.out.println("Fichero descargado:    " + fileName);
		System.out.println("Tamaño total:          " + totalSize + " bytes (" + String.format("%.2f", totalSize / 1024.0) + " KB)");
		System.out.println("Hash SHA-256:          " + fileHash);
		System.out.println("Ruta local:            " + savedPath);
		System.out.println("Integridad SHA-256:    VERIFICADA (OK)");
		System.out.println("\nDetalle de acciones por servidor:");
		for (InetSocketAddress s : servers) {
			long bytes = bytesPerServer.getOrDefault(s, 0L);
			int chunks = chunksPerServer.getOrDefault(s, 0);
			double pct = totalSize > 0 ? (bytes * 100.0 / totalSize) : 0.0;
			System.out.println(String.format(" - Servidor %-22s: %8d bytes en %3d fragmento(s)  (%5.1f%%)",
					s.toString(), bytes, chunks, pct));
		}
		System.out.println("======================================================================\n");
	}

	/**
	 * Clase auxiliar para registrar ficheros encontrados en la búsqueda 'whohas'.
	 * Agrupa por hash para distinguir ficheros distintos aunque tengan el mismo nombre.
	 */
	public static class WhoHasMatch {
		public final String hash;
		public final Set<String> fileNames = new LinkedHashSet<>();
		public long fileSize = -1;
		public final List<String> sharers = new ArrayList<>();

		public WhoHasMatch(String hash) {
			this.hash = hash;
		}
	}

	/**
	 * Implementación del comando 'whohas <filename_substring>'.
	 * Busca ficheros compartidos por otros peers y por el directorio que coincidan
	 * con la subcadena de nombre dada. Muestra el hash de cada fichero y los pares
	 * que lo comparten, contemplando colisiones de nombre con distinto hash.
	 */
	public boolean whoHas(NFControllerLogicDir dirLogic, String filenameSubstring) {
		String needle = filenameSubstring.trim().toLowerCase();
		System.out.println("\n* Buscando ficheros compartidos por otros que contengan: \"" + filenameSubstring + "\"...");

		// Mapa agrupado por hash SHA-256 para diferenciar ficheros con mismo nombre pero distinto hash
		Map<String, WhoHasMatch> resultsByHash = new LinkedHashMap<>();

		// 1. Consultar ficheros servidos directamente por el directorio
		System.out.println("* Consultando ficheros disponibles directamente en el directorio...");
		FileInfo[] dirFiles = dirLogic.fetchFileList();
		if (dirFiles != null) {
			for (FileInfo df : dirFiles) {
				if (df.fileName != null && df.fileName.toLowerCase().contains(needle)) {
					WhoHasMatch match = resultsByHash.get(df.fileHash);
					if (match == null) {
						match = new WhoHasMatch(df.fileHash);
						match.fileSize = df.fileSize;
						resultsByHash.put(df.fileHash, match);
					}
					match.fileNames.add(df.fileName);
					if (match.fileSize < 0) {
						match.fileSize = df.fileSize;
					}
					String dirSource = "Directorio @ " + dirLogic.getDirectoryHostname() + ":" + NFDirectoryServer.DIRECTORY_PORT;
					if (!match.sharers.contains(dirSource)) {
						match.sharers.add(dirSource);
					}
				}
			}
		}

		// 2. Consultar la lista de peers registrados en el directorio
		Map<String, InetSocketAddress> peerList = dirLogic.fetchPeerList();
		int peersQueried = 0;
		if (peerList != null && !peerList.isEmpty()) {
			System.out.println("* Consultando ficheros compartidos por los peers registrados...");
			for (Map.Entry<String, InetSocketAddress> entry : peerList.entrySet()) {
				String peerNick = entry.getKey();
				InetSocketAddress peerAddr = entry.getValue();

				// Excluir nuestro propio peer ("ficheros compartidos por otros")
				if (peerNick.equals(NanoFiles.peerNickname)) {
					continue;
				}

				peersQueried++;
				try {
					NFConnector conn = new NFConnector(peerAddr);
					FileInfo[] files = conn.getPeerFileListData();
					if (files != null) {
						for (FileInfo pf : files) {
							if (pf.fileName != null && pf.fileName.toLowerCase().contains(needle)) {
								WhoHasMatch match = resultsByHash.get(pf.fileHash);
								if (match == null) {
									match = new WhoHasMatch(pf.fileHash);
									match.fileSize = pf.fileSize;
									resultsByHash.put(pf.fileHash, match);
								}
								match.fileNames.add(pf.fileName);
								if (match.fileSize < 0) {
									match.fileSize = pf.fileSize;
								}
								String peerSource = peerNick + " @ " + peerAddr;
								if (!match.sharers.contains(peerSource)) {
									match.sharers.add(peerSource);
								}
							}
						}
					}
				} catch (IOException e) {
					System.err.println("  [!] Aviso: No se pudo conectar al peer '" + peerNick + "' en " + peerAddr + ": " + e.getMessage());
				}
			}
		}

		// 3. Mostrar resultados
		if (resultsByHash.isEmpty()) {
			System.out.println("* No se encontraron ficheros compartidos coincidentes con: \"" + filenameSubstring + "\"\n");
			return true;
		}

		// Contar coincidencias de nombres para avisar al usuario si hay ficheros con mismo nombre pero distinto hash
		Map<String, Integer> nameCounts = new LinkedHashMap<>();
		for (WhoHasMatch m : resultsByHash.values()) {
			for (String n : m.fileNames) {
				String lower = n.toLowerCase();
				nameCounts.put(lower, nameCounts.getOrDefault(lower, 0) + 1);
			}
		}

		System.out.println("\n======================================================================");
		System.out.println("                 RESULTADOS DE BÚSQUEDA 'WHOHAS'                      ");
		System.out.println("======================================================================");
		System.out.println("Subcadena buscada: \"" + filenameSubstring + "\"");
		System.out.println("Ficheros coincidentes encontrados: " + resultsByHash.size() + "\n");

		int itemIndex = 1;
		for (WhoHasMatch match : resultsByHash.values()) {
			String displayNames = String.join(", ", match.fileNames);
			boolean isAmbiguousName = false;
			for (String n : match.fileNames) {
				if (nameCounts.getOrDefault(n.toLowerCase(), 0) > 1) {
					isAmbiguousName = true;
					break;
				}
			}

			System.out.println("----------------------------------------------------------------------");
			System.out.println(" [" + itemIndex + "] Fichero: " + displayNames);
			System.out.println("     Tamaño:  " + (match.fileSize >= 0 ? match.fileSize + " bytes (" + String.format("%.2f", match.fileSize / 1024.0) + " KB)" : "desconocido"));
			System.out.println("     Hash:    " + match.hash);
			if (isAmbiguousName) {
				System.out.println("     AVISO:   Existe más de un fichero con este nombre y distinto hash.");
			}
			System.out.println("     Pares/Fuentes que lo comparten (" + match.sharers.size() + "):");
			for (String s : match.sharers) {
				System.out.println("       - " + s);
			}
			itemIndex++;
		}
		System.out.println("======================================================================\n");

		return true;
	}

	protected int getServerPort() {
		return fileServer != null ? fileServer.getPort() : 0;
	}

	protected void stopFileServer(NFControllerLogicDir dirLogic) {
		if (fileServer != null) {
			fileServer.stopServer();
			fileServer = null;
			System.out.println("* Servidor detenido. Ya no compartes ficheros.");
		} else {
			System.out.println("* El servidor no estaba ejecutándose.");
		}
	}
	
	protected void getAndPrintPeerFileInfo(NFControllerLogicDir dirLogic, String targetPeerNickname) {
		InetSocketAddress peerAddress = dirLogic.lookupUserAddress(targetPeerNickname);
		if (peerAddress == null) {
			System.err.println("* Error: Peer '" + targetPeerNickname + "' no encontrado en el directorio.");
			return;
		}

		try {
			NFConnector connector = new NFConnector(peerAddress);
			connector.getPeerFileList();
		} catch (IOException e) {
			System.err.println("* Error al conectar con el peer: " + e.getMessage());
		}
	}
	
	protected boolean serving() {
		return fileServer != null;
	}
}

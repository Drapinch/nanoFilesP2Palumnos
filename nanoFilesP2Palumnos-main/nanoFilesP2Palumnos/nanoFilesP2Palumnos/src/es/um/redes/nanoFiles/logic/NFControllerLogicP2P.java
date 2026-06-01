package es.um.redes.nanoFiles.logic;

import java.net.InetSocketAddress;
import java.io.IOException;
import es.um.redes.nanoFiles.tcp.client.NFConnector;
import es.um.redes.nanoFiles.application.NanoFiles;
import es.um.redes.nanoFiles.tcp.server.NFServer;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import es.um.redes.nanoFiles.util.FileDigest;
import es.um.redes.nanoFiles.util.FileNameUtil;
import es.um.redes.nanoFiles.tcp.message.PeerMessage;
import es.um.redes.nanoFiles.tcp.message.PeerMessageOps;

public class NFControllerLogicP2P {
	// Servidor TCP local para compartir ficheros con otros peers
	private NFServer fileServer = null;

	protected NFControllerLogicP2P() {
	}

	/**
	 * Método para ejecutar un servidor de ficheros en segundo plano. Debe arrancar
	 * el servidor en un nuevo hilo creado a tal efecto.
	 * * @return Verdadero si se ha arrancado en un nuevo hilo con el servidor de
	 * ficheros, y está a la escucha en un puerto, falso en caso contrario.
	 * */
	protected boolean startFileServer(NFControllerLogicDir dirLogic) {
		boolean serverRunning = false;
		/*
		 * Comprobar que no existe ya un objeto NFServer previamente creado, en cuyo
		 * caso el servidor ya está en marcha.
		 */
		if (fileServer != null) {
			System.err.println("File server is already running");
		} else {
			/*
			 * Arrancar servidor en segundo plano creando un nuevo hilo, comprobar que el 
			 * servidor está escuchando en un puerto válido (>0), imprimir mensaje 
			 * informando sobre el puerto de escucha, y devolver verdadero.
			 */
			try {
				fileServer = new NFServer();
				if (fileServer.getPort() > 0) {
					fileServer.startServer();
					System.out.println("* File server started in background, listening on port " + fileServer.getPort());
					
					// Registrar en el directorio
					if (dirLogic.registerFileServer(fileServer.getPort())) {
						serverRunning = true;
					} else {
						System.err.println("* Error: Could not register file server in the directory.");
						fileServer.stopServer();
						fileServer = null;
					}
				} else {
					System.err.println("* Error: Invalid port configuration.");
				}
				
			} catch (java.io.IOException e) {
				/*
				 * Capturamos excepciones de E/S (ej. puerto ya en uso)
				 * Informamos al usuario sin abortar el programa principal.
				 */
				System.err.println("* Critical error starting file server: " + e.getMessage());
				fileServer = null; // Nos aseguramos de que quede a null para poder reintentar
			}
		}
		return serverRunning;
	}

	protected void testTCPServer() {
		assert (NanoFiles.testModeTCP);
		/*
		 * Comprobar que no existe ya un objeto NFServer previamente creado, en cuyo
		 * caso el servidor ya está en marcha.
		 */
		assert (fileServer == null);
		try {

			fileServer = new NFServer();
			/*
			 * (Boletín SocketsTCP) Inicialmente, se creará un NFServer y se ejecutará su
			 * método "test" (servidor minimalista en primer plano, que sólo puede atender a
			 * un cliente conectado). Posteriormente, se desactivará "testModeTCP" para
			 * implementar un servidor en segundo plano, que se ejecute en un hilo
			 * secundario para permitir que este hilo (principal) siga procesando comandos
			 * introducidos mediante el shell.
			 */
			fileServer.test();
			// Este código es inalcanzable: el método 'test' nunca retorna...
		} catch (IOException e1) {
			e1.printStackTrace();
			System.err.println("Cannot start the file server");
			fileServer = null;
		}
	}

	public void testTCPClient() {

		assert (NanoFiles.testModeTCP);
		/*
		 * (Boletín SocketsTCP) Inicialmente, se creará un NFConnector (cliente TCP)
		 * para conectarse a un servidor que esté escuchando en la misma máquina y un
		 * puerto fijo. Después, se ejecutará el método "test" para comprobar la
		 * comunicación mediante el socket TCP. Posteriormente, se desactivará
		 * "testModeTCP" para implementar la descarga de un fichero desde múltiples
		 * servidores.
		 */

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
	 * Método para listar los ficheros de un peer concreto vía TCP e imprimirlos por
	 * pantalla.
	 * * @param peerAddr La dirección del peer cuyos ficheros se quiere listar
	 * @return Verdadero si se ha obtenido exitosamente el listado de ficheros del peer
	 */
	protected boolean listPeerFiles(InetSocketAddress peerAddr) {
		boolean success = false;
		try {
			// Nos conectamos al peer específico
			NFConnector connector = new NFConnector(peerAddr);
			// Solicitamos y mostramos la lista de ficheros (Asumiendo método en NFConnector)
			System.out.println("* Requesting file list from " + peerAddr);
			
			/*
			 * Nota: el método exacto dependerá de tu NFConnector,
			 * si devuelve un array de FileInfo o simplemente los imprime y devuelve boolean.
			 * Generalmente se usa algo como getFileList()
			 */
			success = connector.getPeerFileList(); 
			
		} catch (IOException e) {
			System.err.println("* Error: No se pudo conectar al peer " + peerAddr + " - " + e.getMessage());
		}
		return success;
	}

	/**
	 * Descarga un fichero identificado por subcadena de hash desde uno o varios
	 * peers. Si se pasa "*" como nickname, usa el directorio para localizar los
	 * peers que tienen el hash.
	 */
	protected boolean downloadFromPeers(NFControllerLogicDir dirLogic, String targetPeerNickname, String targetHashSubstring) {
		InetSocketAddress[] serverList = null;

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
			// Encontrado exactamente un hash
			String fullHash = searchResults.keySet().iterator().next();
			serverList = searchResults.get(fullHash);
			System.out.println(" [*] Fichero encontrado con hash completo: " + fullHash);
			System.out.println(" [*] Compartido por " + serverList.length + " servidor(es).");
			
			// Lógica de descarga multitarea desde varios servidores alternando fragmentos
			return downloadFileFromServersMultisource(serverList, fullHash);
		} else {
			// Lógica para descargar desde un peer concreto
			InetSocketAddress peerAddress = dirLogic.lookupUserAddress(targetPeerNickname); 

			if (peerAddress == null) {
				System.err.println("* Error: No se ha encontrado la IP del peer '" + targetPeerNickname + "'. ¿Le pediste la lista al directorio?");
				return false;
			}
			serverList = new InetSocketAddress[] { peerAddress };
			return downloadFileFromServers(serverList, targetHashSubstring);
		}
	}

	protected boolean downloadFileFromServersMultisource(InetSocketAddress[] serverAddressList, String expectedFullHash) {
		if (serverAddressList == null || serverAddressList.length == 0) {
			System.err.println(" [X] Lista de servidores vacía.");
			return false;
		}

		int numServers = serverAddressList.length;
		System.out.println(" [*] Iniciando descarga secuencial multitarea desde " + numServers + " servidor(es)...");

		// 1. Obtener metadatos (nombre de archivo y tamaño total) usando el primer servidor que responda
		String fileName = null;
		long totalSize = -1;
		
		for (InetSocketAddress addr : serverAddressList) {
			try {
				System.out.println(" [*] Obteniendo metadatos del archivo desde: " + addr);
				NFConnector conn = new NFConnector(addr);
				PeerMessage response = conn.downloadFileChunk(expectedFullHash, 0, 0); // Petición de tamaño 0
				if (response != null && response.getOpcode() == PeerMessageOps.OPCODE_FILE_DATA) {
					fileName = response.getName();
					totalSize = response.getTotalFileSize();
					System.out.println(" [V] Metadatos obtenidos. Fichero: " + fileName + ", Tamaño: " + totalSize + " bytes");
					break;
				}
			} catch (IOException e) {
				System.err.println(" [X] No se pudo obtener metadatos de " + addr + ": " + e.getMessage());
			}
		}

		if (fileName == null || totalSize <= 0) {
			System.err.println(" [X] Error: No se pudieron obtener los metadatos del fichero de ningún servidor.");
			return false;
		}

		// 2. Descargar por fragmentos secuenciales
		long chunkSize = (totalSize + numServers - 1) / numServers;
		byte[] fileBuffer = new byte[(int) totalSize];
		long[] bytesDownloadedPerServer = new long[numServers];

		for (int i = 0; i < numServers; i++) {
			long offset = i * chunkSize;
			int len = (int) Math.min(chunkSize, totalSize - offset);
			if (len <= 0) break;

			boolean chunkDownloaded = false;
			// Intentar con cada servidor alternativo si el principal para este chunk falla
			for (int attempt = 0; attempt < numServers; attempt++) {
				int serverIndex = (i + attempt) % numServers;
				InetSocketAddress addr = serverAddressList[serverIndex];
				try {
					System.out.println(" [*] Descargando fragmento " + i + " (" + len + " bytes desde offset " + offset + ") del servidor " + addr);
					NFConnector conn = new NFConnector(addr);
					PeerMessage response = conn.downloadFileChunk(expectedFullHash, offset, len);
					if (response != null && response.getOpcode() == PeerMessageOps.OPCODE_FILE_DATA) {
						byte[] data = response.getFileData();
						if (data != null && data.length == len) {
							System.arraycopy(data, 0, fileBuffer, (int) offset, len);
							bytesDownloadedPerServer[serverIndex] += len;
							chunkDownloaded = true;
							System.out.println(" [V] Fragmento " + i + " descargado con éxito.");
							break;
						} else {
							System.err.println(" [X] Tamaño de datos incorrecto devuelto por " + addr);
						}
					}
				} catch (IOException e) {
					System.err.println(" [X] Error al descargar fragmento de " + addr + ": " + e.getMessage());
				}
			}

			if (!chunkDownloaded) {
				System.err.println(" [X] Error: No se pudo descargar el fragmento " + i + " de ningún servidor disponible.");
				return false;
			}
		}

		// 3. Guardar el archivo en el disco
		String fullPath = es.um.redes.nanoFiles.application.NanoFiles.sharedDirname + java.io.File.separator + fileName;
		Path safePath = FileNameUtil.chooseAvailableName(fullPath);
		try {
			Files.write(safePath, fileBuffer);
		} catch (IOException e) {
			System.err.println(" [X] Error de escritura en disco: " + e.getMessage());
			return false;
		}

		// 4. Verificar integridad con hash completo
		String localHash = FileDigest.computeFileChecksumString(safePath.toString());
		if (localHash.equals(expectedFullHash)) {
			System.out.println(" [V] ¡Verificación de integridad de hash OK! Checksum: " + localHash);
		} else {
			System.err.println(" [X] ¡Error de integridad de hash! Checksum local: " + localHash + ", esperado: " + expectedFullHash + ". Borrando archivo.");
			try {
				Files.deleteIfExists(safePath);
			} catch (IOException e) {}
			return false;
		}

		// 5. Imprimir resumen final de las acciones llevadas a cabo
		System.out.println("\n================ RESUMEN DE DESCARGA PEERDL * ================");
		System.out.println("Fichero: " + fileName + " (" + totalSize + " bytes)");
		System.out.println("Hash esperado: " + expectedFullHash);
		System.out.println("Acciones realizadas:");
		for (int j = 0; j < numServers; j++) {
			System.out.println(" - Conexión al servidor " + serverAddressList[j] + ": descargados " + bytesDownloadedPerServer[j] + " bytes");
		}
		System.out.println("===============================================================\n");

		return true;
	}

	/**
	 * Método para descargar un fichero del peer servidor de ficheros
	 * * @param serverAddressList   La lista de direcciones de los servidores a los
	 * que se conectará
	 * @param targetHashSubstring Subcadena del hash del fichero a descargar
	 */
	protected boolean downloadFileFromServers(InetSocketAddress[] serverAddressList, String targetHashSubstring) {
		boolean downloaded = false;

		if (serverAddressList == null || serverAddressList.length == 0) {
			System.err.println("* Cannot start download - No list of server addresses provided");
			return false;
		}

		// Recorremos la lista de servidores hasta que alguno nos pueda enviar el fichero
		// TODO Avanzado: Crear lógica para descargar a trozos (chunks) de múltiples servidores.
		for (InetSocketAddress serverAddr : serverAddressList) {
			try {
				System.out.println("* Intentando descargar desde el servidor: " + serverAddr);
				NFConnector connector = new NFConnector(serverAddr);
				
				// Intentamos la descarga (El conector debería encargarse de verificar el hash final internamente)
				downloaded = connector.downloadFile(targetHashSubstring);
				
				if (downloaded) {
					System.out.println("* Descarga completada con éxito desde " + serverAddr);
					break; // Si logramos descargar el archivo, salimos del bucle
				} else {
					System.err.println("* El servidor " + serverAddr + " no pudo proporcionar el fichero completo.");
				}
				
			} catch (IOException e) {
				System.err.println("* Error de conexión con el servidor " + serverAddr + ": " + e.getMessage());
			}
		}

		return downloaded;
	}

	@SuppressWarnings("unused")
	private String toDisplayPath(java.nio.file.Path path) {
		java.nio.file.Path abs = path.toAbsolutePath().normalize();
		java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath().normalize();
		if (abs.startsWith(cwd)) {
			return cwd.relativize(abs).toString();
		}
		return path.toString();
	}

	/**
	 * Método para obtener el puerto de escucha de nuestro servidor de ficheros
	 * * @return El puerto en el que escucha el servidor, o 0 en caso de error.
	 */
	protected int getServerPort() {
		int port = 0;
		if (fileServer != null) {
			port = fileServer.getPort(); 
		}
		return port;
	}

	/**
	 * Método para detener nuestro servidor de ficheros en segundo plano
	 * */
	protected void stopFileServer(NFControllerLogicDir dirLogic) {
		/*
		 * Enviar señal para detener nuestro servidor de ficheros en segundo plano
		 * y unregister en el directorio.
		 */
		if (fileServer != null) { // Cambiado de backgroundServer a fileServer para mantener consistencia
			// 1. Detenemos el hilo y cerramos el socket
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
			System.err.println("* Error al conecatar el peer: " + e.getMessage());
		}
	}
	
	protected boolean serving() {
		// Estamos sirviendo ficheros si la instancia de nuestro servidor no es nula
		return fileServer != null;
	}

}
package es.um.redes.nanoFiles.tcp.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;

import es.um.redes.nanoFiles.tcp.message.PeerMessage;
import es.um.redes.nanoFiles.tcp.message.PeerMessageOps;
import es.um.redes.nanoFiles.util.FileDigest;
import es.um.redes.nanoFiles.util.FileNameUtil;
import es.um.redes.nanoFiles.util.FileInfo;

// Esta clase proporciona la funcionalidad necesaria para intercambiar mensajes entre el cliente y el servidor
public class NFConnector {
	private Socket socket;
	private InetSocketAddress serverAddr;

	public NFConnector(InetSocketAddress fserverAddr) throws UnknownHostException, IOException {
		this.serverAddr = fserverAddr;
		this.socket = new Socket(serverAddr.getAddress(), serverAddr.getPort());
	}

	public void test() {
		try {
			DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
			DataInputStream dis = new DataInputStream(socket.getInputStream());

			System.out.println(" [->] Conectado al servidor. Enviando petición de prueba...");

			PeerMessage request = new PeerMessage(PeerMessageOps.OPCODE_DOWNLOAD_REQ);
			request.setHash("hash_inventado_de_prueba");
			request.setChunkOffset(0);
			request.setChunkSize(0);
			request.writeMessageToOutputStream(dos);

			PeerMessage response = PeerMessage.readMessageFromInputStream(dis);
			System.out.println(" [<-] Cliente recibe respuesta del servidor con Opcode: " + response.getOpcode());

			socket.close();
		} catch (IOException e) {
			System.err.println("Error en el cliente de pruebas: " + e.getMessage());
		}
	}

	public boolean downloadFile(String targetHashSubstring) {
		boolean success = false;
		try {
			DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
			DataInputStream dis = new DataInputStream(socket.getInputStream());

			System.out.println(" [->] Pidiendo al peer el fichero con hash: " + targetHashSubstring);

			// 1. Enviar petición de descarga (chunkOffset 0, chunkSize -1 para usar chunk del servidor)
			PeerMessage request = new PeerMessage(PeerMessageOps.OPCODE_DOWNLOAD_REQ);
			request.setHash(targetHashSubstring);
			request.setChunkOffset(0);
			request.setChunkSize(-1);
			request.writeMessageToOutputStream(dos);

			// 2. Esperar la respuesta
			PeerMessage response = PeerMessage.readMessageFromInputStream(dis);

			// 3. Evaluar qué nos ha respondido el servidor
			if (response.getOpcode() == PeerMessageOps.OPCODE_FILE_DATA) {
				String fileName = response.getName();
				byte[] fileBytes = response.getFileData();
				String serverHash = response.getHash();

				String fullPath = es.um.redes.nanoFiles.application.NanoFiles.sharedDirname + java.io.File.separator + fileName;
				Path safePath = FileNameUtil.chooseAvailableName(fullPath);

				Files.write(safePath, fileBytes);

				String localHash = FileDigest.computeFileChecksumString(safePath.toString());

				if (localHash.equalsIgnoreCase(serverHash)) {
					System.out.println(" [V] ¡Éxito! Fichero guardado como '" + safePath.getFileName() + "'. Integridad 100% verificada.");
					success = true;
				} else {
					System.err.println(" [X] ¡Error de Integridad! Checksum local: " + localHash + ", esperado: " + serverHash);
					Files.deleteIfExists(safePath);
				}
			} else if (response.getOpcode() == PeerMessageOps.OPCODE_ERR_NOT_FOUND) {
				System.err.println(" [X] Error: El servidor no tiene el fichero.");
			} else if (response.getOpcode() == PeerMessageOps.OPCODE_ERR_AMBIGUOUS_HASH) {
				System.err.println(" [X] Error: Hash ambiguo. Hay varios ficheros que coinciden con esa subcadena.");
			} else {
				System.err.println(" [X] Error desconocido. Opcode devuelto: " + response.getOpcode());
			}

			socket.close();
		} catch (IOException e) {
			System.err.println("Error durante la descarga del fichero: " + e.getMessage());
		}
		return success;
	}

	public InetSocketAddress getServerAddr() {
		return serverAddr;
	}
	
	public boolean getPeerFileList() {
		boolean success = false;
		try {
			DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
			DataInputStream dis = new DataInputStream(socket.getInputStream());

			PeerMessage request = new PeerMessage(PeerMessageOps.OPCODE_FILELIST_REQ);
			request.writeMessageToOutputStream(dos);

			PeerMessage response = PeerMessage.readMessageFromInputStream(dis);

			if (response.getOpcode() == PeerMessageOps.OPCODE_FILELIST_RESP) {
				System.out.println("\n--- Ficheros del Peer ---");
				System.out.println(response.getFileList());
				System.out.println("-------------------------");
				success = true;
			} else {
				System.err.println(" [X] Respuesta inesperada del peer: " + response.getOpcode());
			}
			socket.close();
		} catch (java.io.IOException e) {
			System.err.println("Error al obtener la lista de ficheros: " + e.getMessage());
		}
		return success;
	}

	public FileInfo[] getPeerFileListData() {
		try {
			DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
			DataInputStream dis = new DataInputStream(socket.getInputStream());

			PeerMessage request = new PeerMessage(PeerMessageOps.OPCODE_FILELIST_REQ);
			request.writeMessageToOutputStream(dos);

			PeerMessage response = PeerMessage.readMessageFromInputStream(dis);

			if (response.getOpcode() == PeerMessageOps.OPCODE_FILELIST_RESP) {
				String rawList = response.getFileList();
				if (rawList == null || rawList.isBlank()) {
					return new FileInfo[0];
				}
				String[] lines = rawList.split("\n");
				java.util.List<FileInfo> list = new java.util.ArrayList<>();
				for (String line : lines) {
					if (line.isBlank()) continue;
					int dashIdx = line.indexOf(" - ");
					if (dashIdx != -1) {
						String hash = line.substring(0, dashIdx).trim();
						String rest = line.substring(dashIdx + 3).trim();
						int openParenIdx = rest.lastIndexOf(" (");
						if (openParenIdx != -1) {
							String name = rest.substring(0, openParenIdx).trim();
							String sizeStr = rest.substring(openParenIdx + 2).trim();
							int spaceIdx = sizeStr.indexOf(" ");
							if (spaceIdx != -1) {
								sizeStr = sizeStr.substring(0, spaceIdx).trim();
							}
							long size = Long.parseLong(sizeStr);
							list.add(new FileInfo(hash, name, size, null));
						}
					}
				}
				return list.toArray(new FileInfo[0]);
			}
		} catch (Exception e) {
			System.err.println("Error parsing peer file list: " + e.getMessage());
		} finally {
			try { socket.close(); } catch (IOException e) {}
		}
		return null;
	}

	public PeerMessage downloadFileChunk(String targetHashSubstring, long offset, int size) {
		try {
			DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
			DataInputStream dis = new DataInputStream(socket.getInputStream());

			PeerMessage request = new PeerMessage(PeerMessageOps.OPCODE_DOWNLOAD_REQ);
			request.setHash(targetHashSubstring);
			request.setChunkOffset(offset);
			request.setChunkSize(size);
			request.writeMessageToOutputStream(dos);

			PeerMessage response = PeerMessage.readMessageFromInputStream(dis);
			return response;
		} catch (IOException e) {
			System.err.println("Error downloading chunk at offset " + offset + ": " + e.getMessage());
			return null;
		} finally {
			try { socket.close(); } catch (IOException e) {}
		}
	}
}

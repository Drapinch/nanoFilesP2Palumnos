package es.um.redes.nanoFiles.tcp.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;

import es.um.redes.nanoFiles.application.NanoFiles;
import es.um.redes.nanoFiles.tcp.message.PeerMessage;
import es.um.redes.nanoFiles.tcp.message.PeerMessageOps;
import es.um.redes.nanoFiles.util.FileInfo;

public class NFServer implements Runnable {

	public static final int PORT = 10000;
	public static final int DEFAULT_CHUNK_SIZE_KB = 10;

	private static int defaultChunkSize = DEFAULT_CHUNK_SIZE_KB * 1024;

	private int chunkSize; // Tamaño del chunk en bytes para este servidor
	private Thread serverBackgroundThread;
	private ServerSocket serverSocket;

	public NFServer() throws IOException {
		this(DEFAULT_CHUNK_SIZE_KB);
	}

	public NFServer(int chunkSizeKB) throws IOException {
		if (chunkSizeKB <= 0) {
			chunkSizeKB = DEFAULT_CHUNK_SIZE_KB;
		}
		this.chunkSize = chunkSizeKB * 1024;
		defaultChunkSize = this.chunkSize;

		serverSocket = new ServerSocket();
		try {
			serverSocket.bind(new InetSocketAddress(PORT));
		} catch (java.net.BindException e) {
			// Fallback: si el puerto 10000 está ocupado, ligar a un puerto libre cualquiera (puerto 0)
			serverSocket.bind(new InetSocketAddress(0));
		}
	}

	public int getPort() {
		return serverSocket != null ? serverSocket.getLocalPort() : 0;
	}

	public int getChunkSize() {
		return chunkSize;
	}

	public int getChunkSizeKB() {
		return chunkSize / 1024;
	}

	/**
	 * Método para ejecutar el servidor de ficheros en primer plano. Sólo es capaz
	 * de atender una conexión de un cliente. Una vez se lanza, ya no es posible
	 * interactuar con la aplicación.
	 */
	public void test() {
		if (serverSocket == null || !serverSocket.isBound()) {
			System.err.println(
					"[fileServerTestMode] Failed to run file server, server socket is null or not bound to any port");
			return;
		} else {
			System.out
					.println("[fileServerTestMode] NFServer running on " + serverSocket.getLocalSocketAddress() + ".");
		}

		while (true) {
			try {
				System.out.println(" [*] Servidor TCP de prueba escuchando en el puerto " + getPort());
				Socket clientSocket = serverSocket.accept();
				System.out.println(" [*] ¡Cliente conectado desde: " + clientSocket.getInetAddress() + "!");
				serveFilesToClient(clientSocket);
			} catch (IOException e) {
				System.err.println("Error al aceptar conexión de cliente: " + e.getMessage());
			}
		}
	}

	/**
	 * Método que ejecuta el hilo principal del servidor en segundo plano, esperando
	 * conexiones de clientes.
	 */
	public void run() {
		System.out.println(" [*] Servidor TCP CONCURRENTE escuchando en el puerto " + getPort()
				+ " con tamaño de chunk configurado en " + (chunkSize / 1024) + " KB (" + chunkSize + " bytes)");
		
		while (!serverSocket.isClosed()){
			try {
				Socket clientSocket = serverSocket.accept();
				System.out.println(" [*] ¡Nueva conexión aceptada desde: " + clientSocket.getInetAddress() + "!");
				
				// Creamos un hilo nuevo para atender a este cliente pasando la instancia del servidor
				NFServerThread workerThread = new NFServerThread(this, clientSocket);
				workerThread.start();
				
			} catch (java.io.IOException e) {
				if (!serverSocket.isClosed()) {
					System.err.println("Error al aceptar conexión de cliente: " + e.getMessage());
				}
			}
		}
		System.out.println(" [*] Servidor TCP apagado.");
	}

	public void startServer() {
		serverBackgroundThread = new Thread(this);
		serverBackgroundThread.start();
	}

	public void stopServer() {
		try {
			if (serverSocket != null && !serverSocket.isClosed()) {
				serverSocket.close();
			}
		} catch (java.io.IOException e) {
			System.err.println("Error apagando el servidor: " + e.getMessage());
		}
	}

	/**
	 * Método que atiende a un cliente conectado usando el tamaño de chunk de esta instancia.
	 */
	public void serveFilesToClient(Socket socket) {
		serveFilesToClientInternal(socket, this.chunkSize);
	}

	/**
	 * Método de compatibilidad estático.
	 */
	public static void serveFilesToClient(Socket socket) {
		serveFilesToClientInternal(socket, defaultChunkSize);
	}

	private static void serveFilesToClientInternal(Socket socket, int configuredChunkSize) {
		try (
				DataInputStream dis = new DataInputStream(socket.getInputStream());
				DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
			) {
			PeerMessage request = PeerMessage.readMessageFromInputStream(dis);
			System.out.println(" [*] Servidor recibe petición con Opcode: " + request.getOpcode());
			
			if (request.getOpcode() == PeerMessageOps.OPCODE_DOWNLOAD_REQ) {
				String requestedHash = request.getHash();
				
				// 1. Obtener los ficheros que compartimos y buscar el hash
				FileInfo[] files = NanoFiles.db.getFiles();
				FileInfo[] matches = FileInfo.lookupHashSubstring(files, requestedHash);

				if (matches == null || matches.length == 0) {
					// No existe
					PeerMessage response = new PeerMessage(PeerMessageOps.OPCODE_ERR_NOT_FOUND);
					response.writeMessageToOutputStream(dos);
					
				} else if (matches.length > 1) {
					// Hay varios archivos que empiezan con esos mismos caracteres
					PeerMessage response = new PeerMessage(PeerMessageOps.OPCODE_ERR_AMBIGUOUS_HASH);
					response.writeMessageToOutputStream(dos);
					
				} else {
					// 2. Encontrado.
					String fullHash = matches[0].fileHash;
					String filePath = NanoFiles.db.lookupFilePath(fullHash);
					File file = new File(filePath);
					
					long chunkOffset = request.getChunkOffset();
					int requestedChunkSize = request.getChunkSize();
					
					if (chunkOffset < 0) {
						chunkOffset = 0;
					}
					
					// Determinar el tamaño efectivo del fragmento:
					// El servidor no enviará fragmentos más grandes que su tamaño de fragmento configurado
					int effectiveChunkSize = configuredChunkSize;
					if (requestedChunkSize > 0) {
						effectiveChunkSize = Math.min(requestedChunkSize, configuredChunkSize);
					} else if (requestedChunkSize == 0) {
						effectiveChunkSize = 0; // Petición de metadatos/sondeo
					}

					long endOffset = Math.min(file.length(), chunkOffset + effectiveChunkSize);
					int actualChunkSize = (int) Math.max(0, endOffset - chunkOffset);
					byte[] fileData = new byte[actualChunkSize];
					
					if (actualChunkSize > 0) {
						try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
							raf.seek(chunkOffset);
							raf.readFully(fileData);
						}
					}

					// 3. Montamos el mensaje con los datos y lo enviamos
					PeerMessage response = new PeerMessage(PeerMessageOps.OPCODE_FILE_DATA);
					response.setHash(fullHash);
					response.setName(file.getName());
					response.setSize(fileData.length);
					response.setFileData(fileData);
					response.setChunkOffset(chunkOffset);
					response.setChunkSize(configuredChunkSize);
					response.setTotalFileSize(file.length());

					response.writeMessageToOutputStream(dos);
					System.out.println(" [*] Fichero/fragmento '" + file.getName() + "' (offset: " + chunkOffset
							+ ", len: " + fileData.length + ", total: " + file.length()
							+ ", serverChunkSize: " + configuredChunkSize + ") enviado al cliente.");
				}
			}
			else if (request.getOpcode() == PeerMessageOps.OPCODE_FILELIST_REQ) {
				FileInfo[] files = NanoFiles.db.getFiles();
				StringBuilder sb = new StringBuilder();
				for (FileInfo f : files) {
					sb.append(f.fileHash).append(" - ").append(f.fileName).append(" (").append(f.fileSize).append(" bytes)\n");
				}
				
				PeerMessage response = new PeerMessage(PeerMessageOps.OPCODE_FILELIST_RESP);
				response.setFileList(sb.toString()); 
				response.writeMessageToOutputStream(dos);
				
				System.out.println(" [*] Enviada lista de ficheros al cliente (" + files.length + " fichero(s)).");
			}
		} catch (Exception e) {
			System.err.println("Error durante la comunicación con el cliente: " + e.getMessage());
		} finally {
			try { socket.close(); } catch (IOException e) {}
		}
	}
}

package es.um.redes.nanoFiles.udp.client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.LinkedHashMap;

import es.um.redes.nanoFiles.application.NanoFiles;
import es.um.redes.nanoFiles.udp.message.DirMessage;
import es.um.redes.nanoFiles.udp.message.DirMessageOps;
import es.um.redes.nanoFiles.util.FileInfo;

/**
 * Cliente con métodos de consulta y actualización específicos del directorio
 */
public class DirectoryConnector {
	/**
	 * Puerto en el que atienden los servidores de directorio
	 */
	private static final int DIRECTORY_PORT = 6868;
	/**
	 * Tiempo máximo en milisegundos que se esperará a recibir una respuesta por el
	 * socket antes de que se deba lanzar una excepción SocketTimeoutException para
	 * recuperar el control
	 */
	private static final int TIMEOUT = 1000;
	/**
	 * Número de intentos máximos para obtener del directorio una respuesta a una
	 * solicitud enviada. Cada vez que expira el timeout sin recibir respuesta se
	 * cuenta como un intento.
	 */
	private static final int MAX_NUMBER_OF_ATTEMPTS = 5;

	/**
	 * Socket UDP usado para la comunicación con el directorio
	 */
	private DatagramSocket socket;
	/**
	 * Dirección de socket del directorio (IP:puertoUDP)
	 */
	private InetSocketAddress directoryAddress;
	/**
	 * Nombre/IP del host donde se ejecuta el directorio
	 */
	private String directoryHostname;

	public static class DownloadedFile {
		public final String filename;
		public final long filesize;
		public final byte[] data;
		public final String filehash;

		public DownloadedFile(String filename, long fsize, byte[] data, String filehash) {
			this.filename = filename;
			this.filesize = fsize;
			this.data = data;
			this.filehash = filehash;
		}
	}

	public DirectoryConnector(String hostname) throws IOException {
		directoryHostname = hostname;
		InetAddress serverAddr = InetAddress.getByName(hostname);
		directoryAddress = new InetSocketAddress(serverAddr, DIRECTORY_PORT);
		socket = new DatagramSocket();
	}

	public InetSocketAddress getDirectoryAddress() {
		return directoryAddress;
	}

	/**
	 * Método para enviar y recibir datagramas al/del directorio
	 * 
	 * @param requestData los datos a enviar al directorio (mensaje de solicitud)
	 * @return los datos recibidos del directorio (mensaje de respuesta)
	 */
	private byte[] sendAndReceiveDatagrams(byte[] requestData) {
		byte responseData[] = new byte[DirMessage.PACKET_MAX_SIZE];
		byte response[] = null;
		if (directoryAddress == null) {
			System.err.println("DirectoryConnector.sendAndReceiveDatagrams: UDP server destination address is null!");
			System.err.println(
					"DirectoryConnector.sendAndReceiveDatagrams: make sure constructor initializes field \"directoryAddress\"");
			System.exit(-1);
		}
		if (socket == null) {
			System.err.println("DirectoryConnector.sendAndReceiveDatagrams: UDP socket is null!");
			System.err.println(
					"DirectoryConnector.sendAndReceiveDatagrams: make sure constructor initializes field \"socket\"");
			System.exit(-1);
		}

		DatagramPacket packetToServer = new DatagramPacket(requestData, requestData.length, directoryAddress);
		DatagramPacket packetFromServer = new DatagramPacket(responseData, responseData.length);

		int attempts = 0;
		boolean received = false;
	    
		while (attempts < MAX_NUMBER_OF_ATTEMPTS && !received) {
			try {
				socket.send(packetToServer);
				socket.setSoTimeout(TIMEOUT);
				socket.receive(packetFromServer);
				received = true;
			} catch (SocketTimeoutException e) {
				attempts++;
				System.err.println("Timeout, reintentando... (" + attempts + "/" + MAX_NUMBER_OF_ATTEMPTS + ")");
			} catch (IOException e) {
				System.err.println("Error de E/S: " + e.getMessage());
				System.exit(-1);
			}
		}

		if (received) {
			response = new byte[packetFromServer.getLength()];
			System.arraycopy(responseData, 0, response, 0, packetFromServer.getLength());
		} else {
			System.err.println("No se obtuvo respuesta después de " + MAX_NUMBER_OF_ATTEMPTS + " intentos.");
		}

		return response;
	}

	/**
	 * Método para probar la comunicación con el directorio mediante el envío y
	 * recepción de mensajes sin formatear ("en crudo")
	 * 
	 * @return verdadero si se ha enviado un datagrama y recibido una respuesta
	 */
	public boolean testSendAndReceive() {
		boolean success = false;
		byte[] requestData = "ping".getBytes();
		byte[] responseData = sendAndReceiveDatagrams(requestData);
	    
		if (responseData != null) {
			String responseStr = new String(responseData);
			if (responseStr.startsWith("pingok")) {
				success = true;
			}
		}

		return success;
	}

	public String getDirectoryHostname() {
		return directoryHostname;
	}

	/**
	 * Método para "hacer ping" al directorio, comprobar que está operativo y que
	 * usa un protocolo compatible. Este método no usa mensajes bien formados.
	 */
	public boolean pingDirectoryRaw() {
		boolean success = false;
		String requestStr = "ping&" + NanoFiles.PROTOCOL_ID;
		byte[] requestData = requestStr.getBytes();
		byte[] responseData = sendAndReceiveDatagrams(requestData);
	    
		if (responseData != null) {
			String responseStr = new String(responseData);
			if (responseStr.equals("welcome")) {
				System.out.println("* Éxito: El directorio está activo y es compatible.");
				success = true;
			} else {
				System.err.println("* Error: El directorio rechazó la conexión o respondió: " + responseStr);
				success = false;
			}
		} else {
			System.err.println("* Error: No se pudo contactar con el directorio.");
		}

		return success;
	}

	/**
	 * Método para "hacer ping" al directorio, comprobar que está operativo y que es
	 * compatible.
	 */
	public boolean pingDirectory() {
		DirMessage request = new DirMessage(DirMessageOps.OPERATION_PING);
		request.setProtocolID(NanoFiles.PROTOCOL_ID);

		byte[] requestData = request.toString().getBytes();
		byte[] responseData = sendAndReceiveDatagrams(requestData);

		if (responseData != null) {
			String responseStr = new String(responseData);
			DirMessage response = DirMessage.fromString(responseStr);

			if (response.getStatus() != null && response.getStatus().equals("welcome")) {
				return true;
			}
		}
		return false;
	}

	private byte[] buildAndSendAndReceive(DirMessage request) {
		byte[] requestData = request.toString().getBytes();
		return sendAndReceiveDatagrams(requestData);
	}

	/**
	 * Método para dar de alta como servidor de ficheros en el puerto indicado.
	 */
	public boolean registerFileServer(int serverPort) {
		boolean success = false;
		DirMessage request = DirMessage.buildRegisterServerRequest(serverPort);
		request.setNickname(NanoFiles.peerNickname); 

		byte[] responseBytes = buildAndSendAndReceive(request);

		if (responseBytes != null) {
			String responseString = new String(responseBytes);
			DirMessage responseMessage = DirMessage.fromString(responseString);

			if (responseMessage.getOperation().equals(DirMessageOps.OPERATION_REGISTER_SERVER_OK)) {
				System.out.println(" [*] Servidor registrado correctamente en el directorio.");
				success = true;
			} else {
				System.err.println(" [X] Error registrando el servidor en el directorio.");
			}
		} else {
			System.err.println(" [X] Timeout: El directorio no responde al registro.");
		}

		return success;
	}

	/**
	 * Método para obtener la lista de ficheros alojados en el directorio.
	 */
	public FileInfo[] getFileList() {
		FileInfo[] filelist = new FileInfo[0];
		
		DirMessage request = DirMessage.build(DirMessageOps.OPERATION_FILELIST);
		byte[] responseBytes = buildAndSendAndReceive(request);

		if (responseBytes != null) {
			String responseString = new String(responseBytes);
			DirMessage responseMessage = DirMessage.fromString(responseString);

			if (responseMessage.getOperation().equals(DirMessageOps.OPERATION_FILELIST_OK)) {
				String rawList = responseMessage.getFileList();
				if (rawList != null && !rawList.isEmpty()) {
					String[] files = rawList.split(";");
					filelist = new FileInfo[files.length];
					for (int i = 0; i < files.length; i++) {
						String[] fileData = files[i].split(",");
						if (fileData.length == 3) {
							filelist[i] = new FileInfo(fileData[0], fileData[1], Long.parseLong(fileData[2]), "");
						}
					}
				}
			} else {
				System.err.println(" [X] Error solicitando la lista de ficheros al directorio.");
			}
		} else {
			System.err.println(" [X] Timeout: El directorio no responde a la petición de ficheros.");
		}

		return filelist;
	}
	
	public Map<String, InetSocketAddress> getPeerList() {
		Map<String, InetSocketAddress> peers = new LinkedHashMap<String, InetSocketAddress>();

		DirMessage request = DirMessage.build(DirMessageOps.OPERATION_PEERLIST);
		byte[] responseBytes = buildAndSendAndReceive(request);

		if (responseBytes != null) {
			String responseString = new String(responseBytes);
			DirMessage responseMessage = DirMessage.fromString(responseString);

			if (responseMessage.getOperation().equals(DirMessageOps.OPERATION_PEERLIST_OK)) {
				String rawList = responseMessage.getPeerList();
				if (rawList != null && !rawList.isEmpty()) {
					String[] peerEntries = rawList.split(";");
					for (String entry : peerEntries) {
						String[] peerData = entry.split(",");
						if (peerData.length == 3) {
							String nick = peerData[0];
							String ip = peerData[1];
							int port = Integer.parseInt(peerData[2]);
							peers.put(nick, new InetSocketAddress(ip, port));
						}
					}
				}
			} else {
				System.err.println(" [X] Error solicitando la lista de peers al directorio.");
			}
		} else {
			System.err.println(" [X] Timeout: El directorio no responde a la petición de peers.");
		}

		return peers;
	}

	public Map<String, InetSocketAddress[]> searchFilesByHash(String hashSubstring) {
		Map<String, InetSocketAddress[]> results = new LinkedHashMap<String, InetSocketAddress[]>();

		Map<String, InetSocketAddress> peerList = getPeerList();
		if (peerList == null || peerList.isEmpty()) {
			return results;
		}

		Map<String, java.util.List<InetSocketAddress>> matchesMap = new LinkedHashMap<>();
		for (Map.Entry<String, InetSocketAddress> entry : peerList.entrySet()) {
			String peerName = entry.getKey();
			InetSocketAddress peerAddr = entry.getValue();

			// Evitar conectarnos a nosotros mismos
			if (peerName.equals(NanoFiles.peerNickname)) {
				continue;
			}

			try {
				es.um.redes.nanoFiles.tcp.client.NFConnector conn = new es.um.redes.nanoFiles.tcp.client.NFConnector(peerAddr);
				FileInfo[] files = conn.getPeerFileListData();
				if (files != null) {
					FileInfo[] matched = FileInfo.lookupHashSubstring(files, hashSubstring);
					for (FileInfo f : matched) {
						java.util.List<InetSocketAddress> list = matchesMap.get(f.fileHash);
						if (list == null) {
							list = new java.util.ArrayList<>();
							matchesMap.put(f.fileHash, list);
						}
						list.add(peerAddr);
					}
				}
			} catch (IOException e) {
				System.err.println("* Error connecting to peer " + peerName + " @ " + peerAddr + " during search: " + e.getMessage());
			}
		}

		for (Map.Entry<String, java.util.List<InetSocketAddress>> entry : matchesMap.entrySet()) {
			results.put(entry.getKey(), entry.getValue().toArray(new InetSocketAddress[0]));
		}

		return results;
	}

	public DownloadedFile downloadFileFromDirectory(String hashSubstring) {
		byte[] fileData = null;
		String filename = null;
		long filesize = -1;
		String filehash = null;

		return new DownloadedFile(filename, filesize, fileData, filehash);
	}

	/**
	 * Método para darse de baja como servidor de ficheros.
	 */
	public boolean unregisterFileServer() {
		boolean success = false;

		DirMessage request = DirMessage.build(DirMessageOps.OPERATION_UNREGISTER_SERVER);
		request.setNickname(NanoFiles.peerNickname);

		byte[] responseBytes = buildAndSendAndReceive(request);

		if (responseBytes != null) {
			String responseString = new String(responseBytes);
			DirMessage responseMessage = DirMessage.fromString(responseString);

			if (responseMessage.getOperation().equals(DirMessageOps.OPERATION_UNREGISTER_SERVER_OK)) {
				System.out.println(" [*] Te has dado de baja correctamente en el directorio.");
				success = true;
			} else {
				System.err.println(" [X] Error al darse de baja en el directorio.");
			}
		} else {
			System.err.println(" [X] Timeout: El directorio no responde a la baja.");
		}

		return success;
	}
}

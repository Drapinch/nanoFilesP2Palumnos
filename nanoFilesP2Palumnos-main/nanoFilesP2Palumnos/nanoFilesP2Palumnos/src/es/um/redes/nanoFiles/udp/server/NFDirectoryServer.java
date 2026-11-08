package es.um.redes.nanoFiles.udp.server;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;

import es.um.redes.nanoFiles.application.NanoFiles;
import es.um.redes.nanoFiles.udp.message.DirMessage;
import es.um.redes.nanoFiles.udp.message.DirMessageOps;
import es.um.redes.nanoFiles.util.FileInfo;

public class NFDirectoryServer {
	/**
	 * Número de puerto UDP en el que escucha el directorio
	 */
	public static final int DIRECTORY_PORT = 6868;
	private static final double MESSAGE_PROBABILITY = 15;
	/**
	 * Socket de comunicación UDP con el cliente UDP (DirectoryConnector)
	 */
	private DatagramSocket socket = null;
	/**
	 * Lista de ficheros alojados en el directorio.
	 */
	private FileInfo[] directoryFiles;
	/**
	 * Lista de servidores registrados (IP, puerto TCP).
	 */
	private LinkedHashMap<String, InetSocketAddress> registeredPeers;

	/**
	 * Probabilidad de descartar un mensaje recibido en el directorio (para simular
	 * enlace no confiable y testear el código de retransmisión)
	 */
	private double messageDiscardProbability;

	public NFDirectoryServer(double corruptionProbability, String directoryFilesPath) throws SocketException {
		messageDiscardProbability = corruptionProbability;
		File dir = new File(directoryFilesPath);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		directoryFiles = FileInfo.loadFilesFromFolder(directoryFilesPath);
		System.out.println("* Directory loaded " + directoryFiles.length + " files from " + directoryFilesPath);

		socket = new DatagramSocket(DIRECTORY_PORT);
		registeredPeers = new LinkedHashMap<String, InetSocketAddress>();

		if (NanoFiles.testModeUDP) {
			if (socket == null) {
				System.err.println("[testMode] NFDirectoryServer: code not yet fully functional.\n"
						+ "Check that all TODOs in its constructor and 'run' methods have been correctly addressed!");
				System.exit(-1);
			}
		}
	}

	public DatagramPacket receiveDatagram() throws IOException {
		DatagramPacket datagramReceivedFromClient = null;
		boolean datagramReceived = false;
		while (!datagramReceived) {
			byte[] buf = new byte[DirMessage.PACKET_MAX_SIZE];
			datagramReceivedFromClient = new DatagramPacket(buf, buf.length);
			socket.receive(datagramReceivedFromClient);

			boolean isNull = (datagramReceivedFromClient == null);
			if (isNull) {
				System.err.println("[testMode] NFDirectoryServer.receiveDatagram: code not yet fully functional.\n"
						+ "Check that all TODOs have been correctly addressed!");
				System.exit(-1);
			} else {
				// Vemos si el mensaje debe ser ignorado (simulación de un canal no confiable)
				double rand = Math.random();
				if (rand < messageDiscardProbability) {
					System.err.println(
							"Directory ignored datagram from " + datagramReceivedFromClient.getSocketAddress());
				} else {
					datagramReceived = true;
				}
			}
		}
		return datagramReceivedFromClient;
	}

	public void runTest() throws IOException {
		System.out.println("[testMode] Directory starting...");

		System.out.println("[testMode] Attempting to receive 'ping' message...");
		DatagramPacket rcvDatagram = receiveDatagram();
		sendResponseTestMode(rcvDatagram);

		System.out.println("[testMode] Attempting to receive 'ping&PROTOCOL_ID' message...");
		rcvDatagram = receiveDatagram();
		sendResponseTestMode(rcvDatagram);
	}

	private void sendResponseTestMode(DatagramPacket pkt) throws IOException {
		String messageFromClient = new String(pkt.getData(), 0, pkt.getLength());
		System.out.println("Data received: " + messageFromClient);
		
		String responseStr = "invalid";
		if (messageFromClient.equals("ping")) {
			responseStr = "pingok";
		}
		else if (messageFromClient.startsWith("ping&")) {
			String[] parts = messageFromClient.split("&");
			if (parts.length == 2 && parts[1].equals(NanoFiles.PROTOCOL_ID)) {
				responseStr = "welcome";
			} else {
				responseStr = "denied";
			}
		}
		
		byte[] responseData = responseStr.getBytes();
		DatagramPacket responsePkt = new DatagramPacket(responseData, responseData.length, pkt.getSocketAddress());
		socket.send(responsePkt);
	}

	public void run() throws IOException {
		byte[] receptionBuffer = new byte[DirMessage.PACKET_MAX_SIZE];
		DatagramPacket requestPacket = new DatagramPacket(receptionBuffer, receptionBuffer.length);

		System.out.println("Directory starting...");

		while (true) {
			System.out.println("Waiting for requests...");
			socket.receive(requestPacket);

			double randomValue = Math.random();
			if (randomValue >= MESSAGE_PROBABILITY) {
				System.out.println("Message dropped due to simulated packet loss.");
				continue;
			}

			sendResponse(requestPacket);
		}
	}

	private void sendResponse(DatagramPacket pkt) throws IOException {
		String messageFromClient = new String(pkt.getData(), 0, pkt.getLength());
		System.out.println("Data received:\n" + messageFromClient);
		DirMessage request = DirMessage.fromString(messageFromClient);
		InetSocketAddress clientAddr = (InetSocketAddress) pkt.getSocketAddress();

		String operation = request.getOperation();
		DirMessage response = null;

		System.out.println("Received request: " + operation + " from " + clientAddr);

		switch (operation) {
		case DirMessageOps.OPERATION_PING:
			response = DirMessage.build(DirMessageOps.OPERATION_PING_OK);
			String clientProtocolId = request.getProtocolId();
			if (clientProtocolId != null && clientProtocolId.equals(NanoFiles.PROTOCOL_ID)) {
				response.setStatus("welcome");
			} else {
				response.setStatus("denied");
			}
			System.out.println("* Servidor procesó PING. Resultado: " + response.getStatus());
			break;

		case DirMessageOps.OPERATION_LOGIN:
			String nickname = request.getNickname();
			if (nickname != null && !nickname.isEmpty()) {
				response = DirMessage.build(DirMessageOps.OPERATION_LOGIN_OK);
				System.out.println("User logged in: " + nickname);
			} else {
				response = DirMessage.build(DirMessageOps.OPERATION_ERROR);
			}
			break;

		case DirMessageOps.OPERATION_REGISTER_SERVER:
			String serverNick = request.getNickname();
			int serverPort = request.getServerPort();
			if (serverNick != null && serverPort > 0) {
				InetSocketAddress serverAddr = new InetSocketAddress(clientAddr.getAddress(), serverPort);
				registeredPeers.put(serverNick, serverAddr);
				response = DirMessage.build(DirMessageOps.OPERATION_REGISTER_SERVER_OK);
				System.out.println("Registered server for " + serverNick + " at " + serverAddr);
			} else {
				response = DirMessage.build(DirMessageOps.OPERATION_ERROR);
			}
			break;

		case DirMessageOps.OPERATION_UNREGISTER_SERVER:
			String nickToRemove = request.getNickname();
			boolean removed = false;
			if (nickToRemove != null && registeredPeers.containsKey(nickToRemove)) {
				registeredPeers.remove(nickToRemove);
				removed = true;
			} else {
				// Fallback: buscar por dirección IP del cliente si el nickname no coincide
				for (Map.Entry<String, InetSocketAddress> entry : new ArrayList<>(registeredPeers.entrySet())) {
					if (entry.getValue().getAddress().equals(clientAddr.getAddress())) {
						registeredPeers.remove(entry.getKey());
						removed = true;
						nickToRemove = entry.getKey();
						break;
					}
				}
			}
			if (removed) {
				response = DirMessage.build(DirMessageOps.OPERATION_UNREGISTER_SERVER_OK);
				System.out.println("Unregistered server: " + nickToRemove);
			} else {
				response = DirMessage.build(DirMessageOps.OPERATION_ERROR);
			}
			break;

		case DirMessageOps.OPERATION_FILELIST:
			StringBuilder filesSb = new StringBuilder();
			if (directoryFiles != null && directoryFiles.length > 0) {
				for (int i = 0; i < directoryFiles.length; i++) {
					FileInfo f = directoryFiles[i];
					filesSb.append(f.fileHash).append(",").append(f.fileName).append(",").append(f.fileSize);
					if (i < directoryFiles.length - 1) {
						filesSb.append(";");
					}
				}
			}
			response = DirMessage.build(DirMessageOps.OPERATION_FILELIST_OK);
			response.setFileList(filesSb.toString());
			break;

		case DirMessageOps.OPERATION_PEERLIST:
			StringBuilder peersSb = new StringBuilder();
			int count = 0;
			for (Map.Entry<String, InetSocketAddress> entry : registeredPeers.entrySet()) {
				peersSb.append(entry.getKey()).append(",")
					   .append(entry.getValue().getAddress().getHostAddress()).append(",")
					   .append(entry.getValue().getPort());
				count++;
				if (count < registeredPeers.size()) {
					peersSb.append(";");
				}
			}
			response = DirMessage.build(DirMessageOps.OPERATION_PEERLIST_OK);
			response.setPeerList(peersSb.toString());
			break;

		default:
			System.out.println("Unknown operation: " + operation);
			response = DirMessage.build(DirMessageOps.OPERATION_ERROR);
			break;
		}

		if (response != null) {
			String responseString = response.toString();
			byte[] responseData = responseString.getBytes();
			DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, clientAddr);
			socket.send(responsePacket);
			System.out.println("Response sent to " + clientAddr);
		}
	}
}

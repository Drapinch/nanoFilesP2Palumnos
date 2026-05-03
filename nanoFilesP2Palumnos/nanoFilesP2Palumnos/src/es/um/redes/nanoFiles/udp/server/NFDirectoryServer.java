package es.um.redes.nanoFiles.udp.server;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.LinkedHashMap;

import es.um.redes.nanoFiles.application.NanoFiles;
import es.um.redes.nanoFiles.udp.message.DirMessage;
import es.um.redes.nanoFiles.udp.message.DirMessageOps;
import es.um.redes.nanoFiles.util.FileInfo;
import es.um.redes.nanoFiles.util.NickGenerator;

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
	/*
	 * TODO: Añadir aquí como atributos las estructuras de datos que sean necesarias
	 * para mantener en el directorio cualquier información necesaria para la
	 * funcionalidad del sistema nanoFilesP2P: ficheros alojados, servidores
	 * registrados, etc.
	 */
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
		/*
		 * Guardar la probabilidad de pérdida de datagramas (simular enlace no
		 * confiable)
		 */
		messageDiscardProbability = corruptionProbability;
		/*
		 * Cargar los ficheros del directorio compartido.
		 */
		File dir = new File(directoryFilesPath);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		directoryFiles = FileInfo.loadFilesFromFolder(directoryFilesPath);
		System.out.println("* Directory loaded " + directoryFiles.length + " files from " + directoryFilesPath);
		/*
		 * TODO: (Boletín SocketsUDP) Inicializar el atributo socket: Crear un socket
		 * UDP ligado al puerto especificado por el argumento directoryPort en la
		 * máquina local,
		 */
		socket = new DatagramSocket(DIRECTORY_PORT);
		/*
		 * TODO: (Boletín SocketsUDP) Inicializar atributos que mantienen el estado del
		 * servidor de directorio: peers registrados, etc.)
		 */
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
			/*
			 * TODO: (Boletín SocketsUDP) Crear un búfer para recibir datagramas y un
			 * datagrama asociado al búfer (datagramReceivedFromClient)
			 */
			byte[] buf = new byte[DirMessage.PACKET_MAX_SIZE];
	        datagramReceivedFromClient = new DatagramPacket(buf, buf.length);
			/*
			 * TODO: (Boletín SocketsUDP) Recibimos a través del socket un datagrama
			 */

	        socket.receive(datagramReceivedFromClient);

			if (datagramReceivedFromClient == null) {
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
		/*
		 * TODO: (Boletín SocketsUDP) Construir un String partir de los datos recibidos
		 * en el datagrama pkt. A continuación, imprimir por pantalla dicha cadena a
		 * modo de depuración.
		 */
		String messageFromClient = new String(pkt.getData(), 0, pkt.getLength());
	    System.out.println("Data received: " + messageFromClient);
	    
	    String responseStr = "invalid";
		/*
		 * TODO: (Boletín SocketsUDP) Después, usar la cadena para comprobar que su
		 * valor es "ping"; en ese caso, enviar como respuesta un datagrama con la
		 * cadena "pingok". Si el mensaje recibido no es "ping", se informa del error y
		 * se envía "invalid" como respuesta.
		 */
	    if (messageFromClient.equals("ping")) {
	        responseStr = "pingok";
	    }
		/*
		 * TODO: (Boletín Estructura-NanoFiles) Ampliar el código para que, en el caso
		 * de que la cadena recibida no sea exactamente "ping", comprobar si comienza
		 * por "ping&" (es del tipo "ping&PROTOCOL_ID", donde PROTOCOL_ID será el
		 * identificador del protocolo diseñado por el grupo de prácticas (ver
		 * NanoFiles.PROTOCOL_ID). Se debe extraer el "protocol_id" de la cadena
		 * recibida y comprobar que su valor coincide con el de NanoFiles.PROTOCOL_ID,
		 * en cuyo caso se responderá con "welcome" (en otro caso, "denied").
		 */
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
			// Recibimos un datagrama del cliente
			socket.receive(requestPacket);

			// Simulamos pérdida de paquetes si es necesario
			double randomValue = Math.random();
			if (randomValue >= MESSAGE_PROBABILITY) {
				System.out.println("Message dropped due to simulated packet loss.");
				continue;
			}

			// Extraemos la IP y el puerto de origen para poder responder
			InetSocketAddress clientAddr = (InetSocketAddress) requestPacket.getSocketAddress();

			// Extraemos los datos del datagrama recibido
			String messageString = new String(requestPacket.getData(), 0, requestPacket.getLength());
			
			// Reconstruimos el objeto DirMessage a partir de la cadena recibida
			DirMessage requestMessage = DirMessage.fromString(messageString);

			// Procesamos la petición y generamos la respuesta
			DirMessage responseMessage = processRequestFromClient(requestMessage, clientAddr);

			// Si hay una respuesta que enviar, la enviamos
			if (responseMessage != null) {
				String responseString = responseMessage.toString();
				byte[] responseData = responseString.getBytes();
				DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, clientAddr);
				socket.send(responsePacket);
				System.out.println("Response sent to " + clientAddr);
			}
		}
	}

	private void sendResponse(DatagramPacket pkt) throws IOException {
		/*
		 * TODO: (Boletín MensajesASCII) Construir String partir de los datos recibidos
		 * en el datagrama pkt. A continuación, imprimir por pantalla dicha cadena a
		 * modo de depuración. Después, usar la cadena para construir un objeto
		 * DirMessage que contenga en sus atributos los valores del mensaje. A partir de
		 * este objeto, se podrá obtener los valores de los campos del mensaje mediante
		 * métodos "getter" para procesar el mensaje y consultar/modificar el estado del
		 * servidor.
		 */

		String messageFromClient = new String(pkt.getData(), 0, pkt.getLength());
		System.out.println("Data received:\n" + messageFromClient);
		
		DirMessage request = DirMessage.fromString(messageFromClient);

		/*
		 * TODO: Una vez construido un objeto DirMessage con el contenido del datagrama
		 * recibido, obtener el tipo de operación solicitada por el mensaje y actuar en
		 * consecuencia, enviando uno u otro tipo de mensaje en respuesta.
		 */
		String operation = request.getOperation(); // TODO: Cambiar!

		/*
		 * TODO: (Boletín MensajesASCII) Construir un objeto DirMessage (msgToSend) con
		 * la respuesta a enviar al cliente, en función del tipo de mensaje recibido,
		 * leyendo/modificando según sea necesario el "estado" guardado en el servidor
		 * de directorio (atributos files, etc.). Los atributos del objeto DirMessage
		 * contendrán los valores adecuados para los diferentes campos del mensaje a
		 * enviar como respuesta (operation, etc.)
		 */
		
		DirMessage msgToSend = new DirMessage(operation);
		
		switch (operation) {
		case DirMessageOps.OPERATION_PING: {




			/*
			 * TODO: (Boletín MensajesASCII) Comprobamos si el protocolId del mensaje del
			 * cliente coincide con el nuestro.
			 */
			String clientProtocolId = request.getProtocolId();
			/*
			 * TODO: (Boletín MensajesASCII) Construimos un mensaje de respuesta que indique
			 * el éxito/fracaso del ping (compatible, incompatible), y lo devolvemos como
			 * resultado del método.
			 */
			if (clientProtocolId != null && clientProtocolId.equals(NanoFiles.PROTOCOL_ID)) {
				msgToSend.setStatus("welcome");
			} else {
				msgToSend.setStatus("denied");
			}
			/*
			 * TODO: (Boletín MensajesASCII) Imprimimos por pantalla el resultado de
			 * procesar la petición recibida (éxito o fracaso) con los datos relevantes, a
			 * modo de depuración en el servidor
			 */
			System.out.println("* Servidor procesó PING. Resultado: " + msgToSend.getStatus() + 
					" (Protocolo cliente: " + clientProtocolId + ")");

			break;
		}



		default:
			System.err.println("Unexpected message operation: \"" + operation + "\"");
			System.exit(-1);
		}

		/*
		 * TODO: (Boletín MensajesASCII) Convertir a String el objeto DirMessage
		 * (msgToSend) con el mensaje de respuesta a enviar, extraer los bytes en que se
		 * codifica el string y finalmente enviarlos en un datagrama
		 */

		String responseStr = msgToSend.toString();
		byte[] responseData = responseStr.getBytes();
		
		// Preparamos el datagrama para enviarlo a la IP y puerto de origen del cliente
		DatagramPacket responsePkt = new DatagramPacket(responseData, responseData.length, pkt.getSocketAddress());
		socket.send(responsePkt);

	}
	
	private DirMessage processRequestFromClient(DirMessage request, InetSocketAddress clientAddr) {
		String operation = request.getOperation();
		DirMessage response = null;

		System.out.println("Received request: " + operation + " from " + clientAddr);

		switch (operation) {
		case DirMessageOps.OPERATION_PING:
			// Respuesta simple para comprobar que el directorio está vivo
			response = DirMessage.build(DirMessageOps.OPERATION_PING_OK);
			break;

		case DirMessageOps.OPERATION_LOGIN:
			String nickname = request.getNickname();
			if (nickname != null && !nickname.isEmpty()) {
				// Puedes añadir lógica aquí para comprobar si el nick ya está en uso, 
				// pero para la versión básica, simplemente lo aceptamos.
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
				// Guardamos la IP desde donde nos habla el cliente y el puerto TCP que nos indica
				InetSocketAddress serverAddr = new InetSocketAddress(clientAddr.getAddress(), serverPort);
				peers.put(serverNick, serverAddr);
				response = DirMessage.build(DirMessageOps.OPERATION_REGISTER_SERVER_OK);
				System.out.println("Registered server for " + serverNick + " at " + serverAddr);
			} else {
				response = DirMessage.build(DirMessageOps.OPERATION_ERROR);
			}
			break;

		case DirMessageOps.OPERATION_UNREGISTER_SERVER:
			String nickToRemove = request.getNickname();
			if (nickToRemove != null && peers.containsKey(nickToRemove)) {
				Peers.remove(nickToRemove);
				response = DirMessage.build(DirMessageOps.OPERATION_UNREGISTER_SERVER_OK);
				System.out.println("Unregistered server: " + nickToRemove);
			} else {
				response = DirMessage.build(DirMessageOps.OPERATION_ERROR);
			}
			break;

		case DirMessageOps.OPERATION_FILELIST:
			// El cliente quiere saber qué archivos tiene el directorio
			StringBuilder filesSb = new StringBuilder();
			if (directoryFiles != null && directoryFiles.length > 0) {
				for (int i = 0; i < directoryFiles.length; i++) {
					FileInfo f = directoryFiles[i];
					// Formato: hash,nombre,tamaño;hash,nombre,tamaño...
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
			// El cliente quiere saber qué peers están registrados como servidores
			StringBuilder peersSb = new StringBuilder();
			int count = 0;
			for (Map.Entry<String, InetSocketAddress> entry : peers.entrySet()) {
				// Formato: nick,IP,puerto;nick,IP,puerto...
				peersSb.append(entry.getKey()).append(",")
					   .append(entry.getValue().getAddress().getHostAddress()).append(",")
					   .append(entry.getValue().getPort());
				
				count++;
				if (count < peers.size()) {
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

		return response;
	}

}
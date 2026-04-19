package es.um.redes.nanoFiles.tcp.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import es.um.redes.nanoFiles.tcp.message.PeerMessage;
import es.um.redes.nanoFiles.tcp.message.PeerMessageOps;




public class NFServer implements Runnable {

	public static final int PORT = 10000;



	private ServerSocket serverSocket;

	public NFServer() throws IOException {
		/*
		 * TODO: (Boletín SocketsTCP) Crear una direción de socket a partir del puerto
		 * especificado (PORT)
		 */
		serverSocket = new ServerSocket();
		serverSocket.bind(new InetSocketAddress(PORT));
		/*
		 * TODO: (Boletín SocketsTCP) Crear un socket servidor y ligarlo a la dirección
		 * de socket anterior
		 */



	}

	/**
	 * Método para ejecutar el servidor de ficheros en primer plano. Sólo es capaz
	 * de atender una conexión de un cliente. Una vez se lanza, ya no es posible
	 * interactuar con la aplicación.
	 * 
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
			/*
			 * TODO: (Boletín SocketsTCP) Usar el socket servidor para esperar conexiones de
			 * otros peers que soliciten descargar ficheros.
			 */
			System.out.println(" [*] Servidor TCP de prueba escuchando en el puerto " + PORT);
			Socket clientSocket = serverSocket.accept();
			System.out.println(" [*] ¡Cliente conectado desde: " + clientSocket.getInetAddress() + "!");
			/*
			 * TODO: (Boletín SocketsTCP) Tras aceptar la conexión con un peer cliente, la
			 * comunicación con dicho cliente para servir los ficheros solicitados se debe
			 * implementar en el método serveFilesToClient, al cual hay que pasarle el
			 * socket devuelto por accept.
			 */
			serveFilesToClient(clientSocket);
		} catch (IOException e) {
			System.err.println("Error al aceptar conexión de cliente: " + e.getMessage());
			// El bucle continúa para que el servidor no se detenga por un error puntual
		}
		}
	}

	/**
	 * Método que ejecuta el hilo principal del servidor en segundo plano, esperando
	 * conexiones de clientes.
	 * 
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		/*
		 * TODO: (Boletín SocketsTCP) Usar el socket servidor para esperar conexiones de
		 * otros peers que soliciten descargar ficheros
		 */
		/*
		 * TODO: (Boletín SocketsTCP) Al establecerse la conexión con un peer, la
		 * comunicación con dicho cliente se hace en el método
		 * serveFilesToClient(socket), al cual hay que pasarle el socket devuelto por
		 * accept
		 */
		/*
		 * TODO: (Boletín TCPConcurrente) Crear un hilo nuevo de la clase
		 * NFServerThread, que llevará a cabo la comunicación con el cliente que se
		 * acaba de conectar, mientras este hilo vuelve a quedar a la escucha de
		 * conexiones de nuevos clientes (para soportar múltiples clientes). Si este
		 * hilo es el que se encarga de atender al cliente conectado, no podremos tener
		 * más de un cliente conectado a este servidor.
		 */




	}
	/*
	 * TODO: (Boletín SocketsTCP) Añadir métodos a esta clase para: 1) Arrancar el
	 * servidor en un hilo nuevo que se ejecutará en segundo plano 2) Detener el
	 * servidor (stopserver) 3) Obtener el puerto de escucha del servidor etc.
	 */




	/**
	 * Método de clase que implementa el extremo del servidor del protocolo de
	 * transferencia de ficheros entre pares.
	 * 
	 * @param socket El socket para la comunicación con un cliente que desea
	 *               descargar ficheros.
	 */
	public static void serveFilesToClient(Socket socket) {
		/*
		 * TODO: (Boletín SocketsTCP) Crear dis/dos a partir del socket
		 */
		try (
				// Usamos try-with-resources para asegurar que los canales se cierran al terminar [cite: 100, 106]
				DataInputStream dis = new DataInputStream(socket.getInputStream());
				DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
			) {
		/*
		 * TODO: (Boletín SocketsTCP) Mientras el cliente esté conectado, leer mensajes
		 * de socket, convertirlo a un objeto PeerMessage y luego actuar en función del
		 * tipo de mensaje recibido, enviando los correspondientes mensajes de
		 * respuesta.
		 */
			PeerMessage request = PeerMessage.readMessageFromInputStream(dis);
			System.out.println(" [*] Servidor recibe petición con Opcode: " + request.getOpcode());
			
			if (request.getOpcode() == PeerMessageOps.OPCODE_DOWNLOAD_REQ) {
				String requestedHash = request.getHash();
				
				// 1. Obtener los ficheros que compartimos y buscar el hash
				es.um.redes.nanoFiles.util.FileInfo[] files = es.um.redes.nanoFiles.application.NanoFiles.db.getFiles();
				es.um.redes.nanoFiles.util.FileInfo[] matches = es.um.redes.nanoFiles.util.FileInfo.lookupHashSubstring(files, requestedHash);

				if (matches == null || matches.length == 0) {
					// No existe
					PeerMessage response = new PeerMessage(PeerMessageOps.OPCODE_ERR_NOT_FOUND);
					response.writeMessageToOutputStream(dos);
					
				} else if (matches.length > 1) {
					// Hay varios archivos que empiezan con esos mismos caracteres
					PeerMessage response = new PeerMessage(PeerMessageOps.OPCODE_ERR_AMBIGUOUS_HASH);
					response.writeMessageToOutputStream(dos);
					
				} else {
					// 2. Encontrado. Lo leemos del disco duro
					String fullHash = matches[0].fileHash;
					String filePath = es.um.redes.nanoFiles.application.NanoFiles.db.lookupFilePath(fullHash);
					java.io.File file = new java.io.File(filePath);
					byte[] fileData = java.nio.file.Files.readAllBytes(file.toPath());

					// 3. Montamos el mensaje con los datos y lo enviamos
					PeerMessage response = new PeerMessage(PeerMessageOps.OPCODE_FILE_DATA);
					response.setHash(fullHash);
					response.setName(file.getName());
					response.setSize(file.length());
					response.setFileData(fileData);

					response.writeMessageToOutputStream(dos);
					System.out.println(" [*] Fichero '" + file.getName() + "' enviado exitosamente al cliente.");
				}
			}
		} catch (Exception e) {
			System.err.println("Error durante la comunicación con el cliente: " + e.getMessage());
		} finally {
			try { socket.close(); } catch (IOException e) {}
		}
		/*
		 * TODO: (Boletín SocketsTCP) Para servir un fichero, hay que localizarlo a
		 * partir de su hash (o subcadena) en nuestra base de datos de ficheros
		 * compartidos. Los ficheros compartidos se pueden obtener con
		 * NanoFiles.db.getFiles(). Los métodos lookupHashSubstring y
		 * lookupFilenameSubstring de la clase FileInfo son útiles para buscar ficheros
		 * coincidentes con una subcadena dada del hash o del nombre del fichero. El
		 * método lookupFilePath() de FileDatabase devuelve la ruta al fichero a partir
		 * de su hash completo.
		 */



	}




}

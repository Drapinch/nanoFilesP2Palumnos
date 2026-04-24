package es.um.redes.nanoFiles.tcp.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import es.um.redes.nanoFiles.tcp.message.PeerMessage;
import es.um.redes.nanoFiles.tcp.message.PeerMessageOps;
import es.um.redes.nanoFiles.util.FileInfo;

//Esta clase proporciona la funcionalidad necesaria para intercambiar mensajes entre el cliente y el servidor
public class NFConnector {
	private Socket socket;
	private InetSocketAddress serverAddr;




	public NFConnector(InetSocketAddress fserverAddr) throws UnknownHostException, IOException {
		serverAddr = fserverAddr;
		/*
		 * TODO: (Boletín SocketsTCP) Se crea el socket a partir de la dirección del
		 * servidor (IP, puerto). La creación exitosa del socket significa que la
		 * conexión TCP ha sido establecida.
		 */
		this.serverAddr = serverAddr;
		this.socket = new Socket(serverAddr.getAddress(), serverAddr.getPort());
		/*
		 * TODO: (Boletín SocketsTCP) Se crean los DataInputStream/DataOutputStream a
		 * partir de los streams de entrada/salida del socket creado. Se usarán para
		 * enviar (dos) y recibir (dis) datos del servidor.
		 */



	}

	public void test() {
		/*
		 * TODO: (Boletín SocketsTCP) Enviar entero cualquiera a través del socket y
		 * después recibir otro entero, comprobando que se trata del mismo valor.
		 */
		try {
			// Creamos los canales de lectura y escritura a partir del socket
			DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
			DataInputStream dis = new DataInputStream(socket.getInputStream());

			System.out.println(" [->] Conectado al servidor. Enviando petición de prueba...");

			// FASE 2: Creamos un mensaje simulando pedir un archivo y lo enviamos
			PeerMessage request = new PeerMessage(PeerMessageOps.OPCODE_DOWNLOAD_REQ);
			request.setHash("hash_inventado_de_prueba");
			request.writeMessageToOutputStream(dos);

			// FASE 2: Nos quedamos esperando la respuesta del servidor
			PeerMessage response = PeerMessage.readMessageFromInputStream(dis);
			System.out.println(" [<-] Cliente recibe respuesta del servidor con Opcode: " + response.getOpcode());

			// Cerramos la conexión
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

			// 1. Enviar petición de descarga
			PeerMessage request = new PeerMessage(PeerMessageOps.OPCODE_DOWNLOAD_REQ);
			request.setHash(targetHashSubstring);
			request.writeMessageToOutputStream(dos);

			// 2. Esperar la respuesta
			PeerMessage response = PeerMessage.readMessageFromInputStream(dis);

			// 3. Evaluar qué nos ha respondido el servidor
			if (response.getOpcode() == PeerMessageOps.OPCODE_FILE_DATA) {
				String fileName = response.getName();
				byte[] fileBytes = response.getFileData();
				String serverHash = response.getHash(); // El hash que dice el servidor que tiene el fichero

				// 1. MEJORA: Evitar colisiones de nombres de fichero
				java.nio.file.Path safePath = es.um.redes.nanoFiles.util.FileNameUtil.chooseAvailableName(fileName);

				// Escribir los bytes en el disco
				java.nio.file.Files.write(safePath, fileBytes);

				// 2. MEJORA: Comprobar la integridad (Verificar el hash final)
				String localHash = es.um.redes.nanoFiles.util.FileDigest.computeFileChecksumString(safePath.toString());

				if (localHash.equals(serverHash)) {
					System.out.println(" [V] ¡Éxito! Fichero guardado como '" + safePath.getFileName() + "'. Integridad 100% verificada.");
					success = true;
				} else {
					System.err.println(" [X] ¡Error de Integridad! El fichero se ha descargado pero está corrupto (Los hashes no coinciden).");
					// Si está corrupto, lo más seguro es borrarlo
					java.nio.file.Files.deleteIfExists(safePath);
				}
				
			} else if (response.getOpcode() == PeerMessageOps.OPCODE_ERR_NOT_FOUND) {
				System.err.println(" [X] Error: El servidor no tiene el fichero.");
			} else if (response.getOpcode() == PeerMessageOps.OPCODE_ERR_AMBIGUOUS_HASH) {
				System.err.println(" [X] Error: Hash ambiguo. Hay varios ficheros que empiezan así, introduce más caracteres.");
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

			// 1. Enviar la petición
			PeerMessage request = new PeerMessage(PeerMessageOps.OPCODE_FILELIST_REQ);
			request.writeMessageToOutputStream(dos);

			// 2. Leer la respuesta
			PeerMessage response = PeerMessage.readMessageFromInputStream(dis);

			if (response.getOpcode() == PeerMessageOps.OPCODE_FILELIST_RESP) {
				System.out.println("\n--- Ficheros del Peer ---");
				// Imprimir el String que nos ha mandado el servidor
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
}

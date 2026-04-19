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

				// Escribir los bytes en un archivo local
				java.io.FileOutputStream fos = new java.io.FileOutputStream(fileName);
				fos.write(fileBytes);
				fos.close();

				System.out.println(" [V] ¡Éxito! Fichero '" + fileName + "' descargado y guardado en tu carpeta.");
				success = true;
				
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
	
}

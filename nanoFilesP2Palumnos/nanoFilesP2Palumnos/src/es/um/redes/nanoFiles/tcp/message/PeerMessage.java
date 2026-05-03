package es.um.redes.nanoFiles.tcp.message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import es.um.redes.nanoFiles.util.FileInfo;

public class PeerMessage {




	private byte opcode;

	/*
	 * TODO: (Boletín MensajesBinarios) Añadir atributos u otros constructores
	 * específicos para crear mensajes con otros campos, según sea necesario
	 * 
	 */
	
	private String hash;         // Hash solicitado o devuelto
	private String name;         // Nombre original del fichero
	private long size;           // Tamaño del fichero o del fragmento
	private byte[] fileData;
	private String fileList;
	
	public PeerMessage() {
		opcode = PeerMessageOps.OPCODE_INVALID_CODE;
	}

	public PeerMessage(byte op) {
		opcode = op;
	}

	/*
	 * TODO: (Boletín MensajesBinarios) Crear métodos getter y setter para obtener
	 * los valores de los atributos de un mensaje. Se aconseja incluir código que
	 * compruebe que no se modifica/obtiene el valor de un campo (atributo) que no
	 * esté definido para el tipo de mensaje dado por "operation".
	 */
	public byte getOpcode() {
		return opcode;
	}
	
	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		// Opcional: Comprobar que el opcode tiene sentido para este dato
		this.hash = hash;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public byte[] getFileData() {
		return fileData;
	}

	public void setFileData(byte[] fileData) {
		this.fileData = fileData;
	}

	public String getFileList() {
		return fileList;
	}

	public void setFileList(String fileList) {
		this.fileList = fileList;
	}




	/**
	 * Método de clase para parsear los campos de un mensaje y construir el objeto
	 * DirMessage que contiene los datos del mensaje recibido
	 * 
	 * @param data El array de bytes recibido
	 * @return Un objeto de esta clase cuyos atributos contienen los datos del
	 *         mensaje recibido.
	 * @throws IOException
	 */
	public static PeerMessage readMessageFromInputStream(DataInputStream dis) throws IOException {
		/*
		 * TODO: (Boletín MensajesBinarios) En función del tipo de mensaje, leer del
		 * socket a través del "dis" el resto de campos para ir extrayendo con los
		 * valores y establecer los atributos del un objeto DirMessage que contendrá
		 * toda la información del mensaje, y que será devuelto como resultado. NOTA:
		 * Usar dis.readFully para leer un array de bytes, dis.readInt para leer un
		 * entero, etc.
		 */
		byte opcode = dis.readByte();
		PeerMessage message = new PeerMessage(opcode);
		switch (opcode) {

		case PeerMessageOps.OPCODE_FILELIST_REQ:
		case PeerMessageOps.OPCODE_ERR_NOT_FOUND:
		case PeerMessageOps.OPCODE_ERR_AMBIGUOUS_HASH:
			// TODO: Formato "Control". No hay más campos que leer.
			break;

		case PeerMessageOps.OPCODE_DOWNLOAD_REQ:
			// TODO: Leer el string del hash enviado por el cliente
			String requestedHash = dis.readUTF();
			message.setHash(requestedHash);
			break;

		case PeerMessageOps.OPCODE_FILE_DATA:
			// TODO: Leer los datos del fichero que nos envía el servidor
			// Respetar el orden del writeMessageToOutputStream:
			message.setHash(dis.readUTF());       // 1. Leemos el hash
			message.setName(dis.readUTF());       // 2. Leemos el nombre
			
			long dataSize = dis.readLong();       // 3. Leemos el tamaño
			message.setSize(dataSize);
			
			// 4. Preparamos el array y leemos los bytes exactos
			byte[] data = new byte[(int) dataSize];
			dis.readFully(data);                  // Muy importante usar readFully para binario
			message.setFileData(data);
			break;
			
		// TODO: Añadir case para OPCODE_FILELIST_RESP

		default:
			System.err.println("PeerMessage.readMessageFromInputStream doesn't know how to parse this message opcode: "
					+ PeerMessageOps.opcodeToOperation(opcode));
			System.exit(-1);
		}
		return message;
	}

	public void writeMessageToOutputStream(DataOutputStream dos) throws IOException {
		/*
		 * TODO (Boletín MensajesBinarios): Escribir los bytes en los que se codifica el
		 * mensaje en el socket a través del "dos", teniendo en cuenta opcode del
		 * mensaje del que se trata y los campos relevantes en cada caso. NOTA: Usar
		 * dos.write para leer un array de bytes, dos.writeInt para escribir un entero,
		 * etc.
		 */
		dos.writeByte(opcode);
		switch (opcode) {
		case PeerMessageOps.OPCODE_FILELIST_REQ:
		case PeerMessageOps.OPCODE_ERR_NOT_FOUND:
		case PeerMessageOps.OPCODE_ERR_AMBIGUOUS_HASH:
			// TODO: Estos mensajes son de formato "Control" (Solo opcode). 
			// No hay que enviar nada más.
			break;
		case PeerMessageOps.OPCODE_DOWNLOAD_REQ:
			// TODO: El cliente envía el hash que quiere descargar.
			// dos.writeUTF(...) facilita el envío de Strings.
			dos.writeUTF(hash);
			break;

		case PeerMessageOps.OPCODE_FILE_DATA:
			// TODO: El servidor envía los datos del fichero.
			// ¡El orden en el que escribes aquí debe ser EXACTAMENTE el mismo
			// en el que lees en el readMessageFromInputStream!
			dos.writeUTF(hash);       // 1. Mandamos el hash original
			dos.writeUTF(name);       // 2. Mandamos el nombre
			dos.writeLong(size);      // 3. Mandamos cuántos bytes ocupan los datos
			dos.write(fileData);      // 4. Mandamos el array de bytes con el contenido
			break;
		default:
			System.err.println("PeerMessage.writeMessageToOutputStream found unexpected message opcode " + opcode + "("
					+ PeerMessageOps.opcodeToOperation(opcode) + ")");
		}
		dos.flush();
	}




}

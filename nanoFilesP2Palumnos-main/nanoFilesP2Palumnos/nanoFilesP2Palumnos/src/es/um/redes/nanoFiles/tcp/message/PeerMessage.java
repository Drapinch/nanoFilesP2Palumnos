package es.um.redes.nanoFiles.tcp.message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class PeerMessage {

	private byte opcode;

	/*
	 * Atributos para los campos de los mensajes
	 */
	private String hash;         // Hash solicitado o devuelto
	private String name;         // Nombre original del fichero
	private long size;           // Tamaño del fichero o del fragmento
	private byte[] fileData;
	private String fileList;
	private long chunkOffset = -1;
	private int chunkSize = -1;
	private long totalFileSize = -1;
	
	public PeerMessage() {
		opcode = PeerMessageOps.OPCODE_INVALID_CODE;
	}

	public PeerMessage(byte op) {
		opcode = op;
	}

	/*
	 * Métodos getter y setter para obtener y modificar los atributos
	 */
	public byte getOpcode() {
		return opcode;
	}
	
	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
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

	public long getChunkOffset() {
		return chunkOffset;
	}

	public void setChunkOffset(long chunkOffset) {
		this.chunkOffset = chunkOffset;
	}

	public int getChunkSize() {
		return chunkSize;
	}

	public void setChunkSize(int chunkSize) {
		this.chunkSize = chunkSize;
	}

	public long getTotalFileSize() {
		return totalFileSize;
	}

	public void setTotalFileSize(long totalFileSize) {
		this.totalFileSize = totalFileSize;
	}

	/**
	 * Método de clase para parsear los campos de un mensaje y construir el objeto
	 * PeerMessage que contiene los datos del mensaje recibido
	 * 
	 * @param dis El DataInputStream del socket
	 * @return Un objeto de esta clase cuyos atributos contienen los datos del
	 *         mensaje recibido.
	 * @throws IOException
	 */
	public static PeerMessage readMessageFromInputStream(DataInputStream dis) throws IOException {
		byte opcode = dis.readByte();
		PeerMessage message = new PeerMessage(opcode);
		switch (opcode) {

		case PeerMessageOps.OPCODE_FILELIST_REQ:
		case PeerMessageOps.OPCODE_ERR_NOT_FOUND:
		case PeerMessageOps.OPCODE_ERR_AMBIGUOUS_HASH:
			// Formato "Control". No hay más campos que leer.
			break;

		case PeerMessageOps.OPCODE_DOWNLOAD_REQ:
			String requestedHash = dis.readUTF();
			message.setHash(requestedHash);
			message.setChunkOffset(dis.readLong());
			message.setChunkSize(dis.readInt());
			break;

		case PeerMessageOps.OPCODE_FILE_DATA:
			// Leer los datos del fichero que envía el servidor
			message.setHash(dis.readUTF());       // 1. Hash
			message.setName(dis.readUTF());       // 2. Nombre
			
			long dataSize = dis.readLong();       // 3. Tamaño de datos
			message.setSize(dataSize);
			
			// 4. Preparamos el array y leemos los bytes exactos
			byte[] data = new byte[(int) dataSize];
			dis.readFully(data);                  // Leer array completo binario
			message.setFileData(data);
			
			message.setChunkOffset(dis.readLong());
			message.setTotalFileSize(dis.readLong());
			break;
			
		case PeerMessageOps.OPCODE_FILELIST_RESP:
			message.setFileList(dis.readUTF());
			break;
		default:
			System.err.println("PeerMessage.readMessageFromInputStream doesn't know how to parse this message opcode: "
					+ PeerMessageOps.opcodeToOperation(opcode));
			System.exit(-1);
		}
		return message;
	}

	public void writeMessageToOutputStream(DataOutputStream dos) throws IOException {
		dos.writeByte(opcode);
		switch (opcode) {
		case PeerMessageOps.OPCODE_FILELIST_REQ:
		case PeerMessageOps.OPCODE_ERR_NOT_FOUND:
		case PeerMessageOps.OPCODE_ERR_AMBIGUOUS_HASH:
			// Mensajes de control (solo opcode)
			break;
		case PeerMessageOps.OPCODE_DOWNLOAD_REQ:
			dos.writeUTF(hash != null ? hash : "");
			dos.writeLong(chunkOffset);
			dos.writeInt(chunkSize);
			break;

		case PeerMessageOps.OPCODE_FILE_DATA:
			dos.writeUTF(hash != null ? hash : "");
			dos.writeUTF(name != null ? name : "");
			dos.writeLong(size);
			if (fileData != null && fileData.length > 0) {
				dos.write(fileData);
			}
			dos.writeLong(chunkOffset);
			dos.writeLong(totalFileSize);
			break;
		case PeerMessageOps.OPCODE_FILELIST_RESP:
			dos.writeUTF(fileList != null ? fileList : "");
			break;
		default:
			System.err.println("PeerMessage.writeMessageToOutputStream found unexpected message opcode " + opcode + "("
					+ PeerMessageOps.opcodeToOperation(opcode) + ")");
		}
		dos.flush();
	}
}

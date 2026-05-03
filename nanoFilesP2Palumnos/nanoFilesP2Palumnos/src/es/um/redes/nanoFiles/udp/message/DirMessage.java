package es.um.redes.nanoFiles.udp.message;




/**
 * Clase que modela los mensajes del protocolo de comunicación entre pares para
 * implementar el explorador de ficheros remoto (servidor de ficheros). Estos
 * mensajes son intercambiados entre las clases DirectoryServer y
 * DirectoryConnector, y se codifican como texto en formato "campo:valor".
 * 
 * @author rtitos
 *
 */
public class DirMessage {
	public static final int PACKET_MAX_SIZE = 65507; // 65535 - 8 (UDP header) - 20 (IP header)

	private static final char DELIMITER = ':'; // Define el delimitador
	private static final char END_LINE = '\n'; // Define el carácter de fin de línea

	/**
	 * Nombre del campo que define el tipo de mensaje (primera línea)
	 */
	private static final String FIELDNAME_OPERATION = "operation";
	/*
	 * TODO: (Boletín MensajesASCII) Definir de manera simbólica los nombres de
	 * todos los campos que pueden aparecer en los mensajes de este protocolo
	 * (formato campo:valor)
	 */
	private static final String FIELDNAME_PROTOCOL_ID = "protocolid";
	private static final String FIELDNAME_STATUS = "status";
	private static final String FIELDNAME_NICKNAME = "nickname";
	private static final String FIELDNAME_PORT = "serverport";
	private static final String FIELDNAME_FILELIST = "filelist";
	private static final String FIELDNAME_PEERLIST = "peerlist";


	/**
	 * Tipo del mensaje, de entre los tipos definidos en PeerMessageOps.
	 */
	private String operation = DirMessageOps.OPERATION_INVALID;
	/**
	 * Identificador de protocolo usado, para comprobar compatibilidad del directorio.
	 */
	private String protocolId;
	/*
	 * TODO: (Boletín MensajesASCII) Crear un atributo correspondiente a cada uno de
	 * los campos de los diferentes mensajes de este protocolo.
	 */
	
	private String status;
	private String nickname;
	private int serverPort = -1; // -1 indica que no se ha establecido
	private String fileList;
	private String peerList;


	public DirMessage(String op) {
		this.operation = op;
		this.nickname = null;
		this.serverPort = 0;
		this.fileList = null;
		this.peerList = null;
	}

	/*
	 * TODO: (Boletín MensajesASCII) Crear diferentes constructores adecuados para
	 * construir mensajes de diferentes tipos con sus correspondientes argumentos
	 * (campos del mensaje)
	 */

	public DirMessage(String op, String status) {
		this.operation = op;
		this.status = status;
	}


	/*
	 * TODO: (Boletín MensajesASCII) Crear métodos getter y setter para obtener los
	 * valores de los atributos de un mensaje. Se aconseja incluir código que
	 * compruebe que no se modifica/obtiene el valor de un campo (atributo) que no
	 * esté definido para el tipo de mensaje dado por "operation".
	 */
	public void setProtocolID(String protocolIdent) {
		if (!operation.equals(DirMessageOps.OPERATION_PING)) {
			throw new RuntimeException(
					"DirMessage: setProtocolId called for message of unexpected type (" + operation + ")");
		}
		protocolId = protocolIdent;
	}

	public String getProtocolId() {
		return protocolId;
	}
	
	public String getOperation() { return operation; }
	public String getNickname() { return nickname; }
	public int getServerPort() { return serverPort; }
	public String getFileList() { return fileList; }
	public String getPeerList() { return peerList; }
	public String getStatus() { return status; }

	// --- Setters ---
	public void setNickname(String nickname) { this.nickname = nickname; }
	public void setServerPort(int serverPort) { this.serverPort = serverPort; }
	public void setFileList(String fileList) { this.fileList = fileList; }
	public void setPeerList(String peerList) { this.peerList = peerList; }
	public void setStatus(String status) { this.status = status; }

	/**
	 * Método que convierte un mensaje codificado como una cadena de caracteres, a
	 * un objeto de la clase PeerMessage, en el cual los atributos correspondientes
	 * han sido establecidos con el valor de los campos del mensaje.
	 * 
	 * @param message El mensaje recibido por el socket, como cadena de caracteres
	 * @return Un objeto PeerMessage que modela el mensaje recibido (tipo, valores,
	 *         etc.)
	 */
	public static DirMessage fromString(String message) {
		/*
		 * TODO: (Boletín MensajesASCII) Usar un bucle para parsear el mensaje línea a
		 * línea, extrayendo para cada línea el nombre del campo y el valor, usando el
		 * delimitador DELIMITER, y guardarlo en variables locales.
		 */
		DirMessage m = new DirMessage(DirMessageOps.OPERATION_INVALID);
		// System.out.println("DirMessage read from socket:");
		// System.out.println(message);
		String[] lines = message.split(END_LINE + "");



		for (String line : lines) {
			if (line.trim().isEmpty()) {
				continue;
			}
			int idx = line.indexOf(DELIMITER); // Posición del delimitador
			String fieldName = line.substring(0, idx).toLowerCase(); // minúsculas
			String value = line.substring(idx + 1).trim();

			switch (fieldName) {
			case FIELDNAME_OPERATION: {
				assert (m == null);
				m = new DirMessage(value);
				break;
			}
			case FIELDNAME_PROTOCOL_ID:
				assert (m != null);
				m.setProtocolID(value);
				break;
			case FIELDNAME_STATUS:
				assert (m != null);
				m.setStatus(value);
				break;
			case FIELDNAME_NICKNAME:
				assert (m != null);
				m.setNickname(value);
				break;
			case FIELDNAME_PORT:
				assert (m != null);
				m.setServerPort(Integer.parseInt(value));
				break;
			case FIELDNAME_FILELIST:
				assert (m != null);
				m.setFileList(value);
				break;
			case FIELDNAME_PEERLIST:
				assert (m != null);
				m.setPeerList(value);
				break;
			default:
				System.err.println("PANIC: DirMessage.fromString - message with unknown field name " + fieldName);
				System.err.println("Message was:\n" + message);
				System.exit(-1);
			}
		}
		return m;
	}

	/**
	 * Método que devuelve una cadena de caracteres con la codificación del mensaje
	 * según el formato campo:valor, a partir del tipo y los valores almacenados en
	 * los atributos.
	 * 
	 * @return La cadena de caracteres con el mensaje a enviar por el socket.
	 */
	public String toString() {

		StringBuffer sb = new StringBuffer();
		sb.append(FIELDNAME_OPERATION + DELIMITER + operation + END_LINE); // Construimos el campo
		/*
		 * TODO: (Boletín MensajesASCII) En función de la operación del mensaje, crear
		 * una cadena la operación y concatenar el resto de campos necesarios usando los
		 * valores de los atributos del objeto.
		 */
		if (protocolId != null) {
			sb.append(FIELDNAME_PROTOCOL_ID + DELIMITER + protocolId + END_LINE);
		}
		if (status != null) {
			sb.append(FIELDNAME_STATUS + DELIMITER + status + END_LINE);
		}
		if (nickname != null) {
			sb.append(FIELDNAME_NICKNAME + DELIMITER + nickname + END_LINE);
		}
		if (serverPort != -1) {
			sb.append(FIELDNAME_PORT + DELIMITER + serverPort + END_LINE);
		}
		if (fileList != null) {
			sb.append(FIELDNAME_FILELIST + DELIMITER + fileList + END_LINE);
		}
		if (peerList != null) {
			sb.append(FIELDNAME_PEERLIST + DELIMITER + peerList + END_LINE);
		}

		sb.append(END_LINE); // Marcamos el final del mensaje
		return sb.toString();
	}
	
	public static DirMessage build(String operation) {
		return new DirMessage(operation);
	}

	public static DirMessage buildLoginRequest(String nickname) {
		DirMessage m = new DirMessage(DirMessageOps.OPERATION_LOGIN);
		m.setNickname(nickname);
		return m;
	}

	public static DirMessage buildRegisterServerRequest(int serverPort) {
		DirMessage m = new DirMessage(DirMessageOps.OPERATION_REGISTER_SERVER);
		m.setServerPort(serverPort);
		return m;
	}

}

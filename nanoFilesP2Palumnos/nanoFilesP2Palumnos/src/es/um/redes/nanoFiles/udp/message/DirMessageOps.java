package es.um.redes.nanoFiles.udp.message;

public class DirMessageOps {

	/*
	 * TODO: (Boletín MensajesASCII) Añadir aquí todas las constantes que definen
	 * los diferentes tipos de mensajes del protocolo de comunicación con el
	 * directorio (valores posibles del campo "operation").
	 */
	public static final String OPERATION_INVALID = "invalid_operation";
	public static final String OPERATION_PING = "ping";
	// TODO: definir las operaciones del protocolo de directorio
	public static final String OPERATION_PING_OK = "ping_ok";

	// Operación de Login / Nickname
	public static final String OPERATION_LOGIN = "login";
	public static final String OPERATION_LOGIN_OK = "login_ok";

	// Operación para consultar ficheros del directorio
	public static final String OPERATION_FILELIST = "filelist";
	public static final String OPERATION_FILELIST_OK = "filelist_ok";

	// Operación para consultar la lista de peers (servidores)
	public static final String OPERATION_PEERLIST = "peerlist";
	public static final String OPERATION_PEERLIST_OK = "peerlist_ok";
	
	// Operación para registrarse como servidor de ficheros
	public static final String OPERATION_REGISTER_SERVER = "register_server";
	public static final String OPERATION_REGISTER_SERVER_OK = "register_server_ok";

	// Operación para darse de baja como servidor de ficheros
	public static final String OPERATION_UNREGISTER_SERVER = "unregister_server";
	public static final String OPERATION_UNREGISTER_SERVER_OK = "unregister_server_ok";
	
	// Opcodes para errores generales
	public static final String OPERATION_ERROR = "error";

}

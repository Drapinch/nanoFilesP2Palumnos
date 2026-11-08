package es.um.redes.nanoFiles.logic;

import java.net.InetSocketAddress;

import es.um.redes.nanoFiles.application.NanoFiles;
import es.um.redes.nanoFiles.shell.NFCommands;
import es.um.redes.nanoFiles.shell.NFShell;
import es.um.redes.nanoFiles.tcp.server.NFServer;
import es.um.redes.nanoFiles.util.FileInfo;

public class NFController {
	/**
	 * Diferentes estados del cliente de acuerdo con el autómata
	 */
	private static final byte OFFLINE = 0;
	private static final byte CONNECTED_TO_DIR = 1;
	private static final byte SERVER_RUNNING = 2;

	/**
	 * Shell para leer comandos de usuario de la entrada estándar
	 */
	private NFShell shell;
	/**
	 * Último comando proporcionado por el usuario
	 */
	private byte currentCommand;

	/**
	 * Objeto controlador encargado de la comunicación con el directorio
	 */
	private NFControllerLogicDir controllerDir;
	/**
	 * Objeto controlador encargado de la comunicación con otros peers (como
	 * servidor o cliente)
	 */
	private NFControllerLogicP2P controllerPeer;

	/**
	 * El estado en que se encuentra este peer (según el autómata). El estado debe
	 * actualizarse cuando se produce un evento (comando) que supone un cambio en el
	 * autómata.
	 */
	private byte currentState;
	/**
	 * Atributos donde se establecen los argumentos pasados a los distintos comandos
	 * del shell.
	 */
	private String targetHashSubstring; // Nombre del fichero a descargar
	private String targetPeerNickname; // Nickname para listar ficheros de un peer (peerfiles)
	private String newNickname; // Nuevo nickname solicitado por el usuario
	private String targetFilenameSubstring; // Subcadena para búsqueda con whohas
	private int serverChunkSizeKB = NFServer.DEFAULT_CHUNK_SIZE_KB; // Tamaño de fragmento para serve N

	// Constructor
	public NFController(String defaultDirectory) {
		shell = new NFShell();

		String directory = shell.chooseDirectory(defaultDirectory);

		controllerDir = new NFControllerLogicDir(directory);
		controllerPeer = new NFControllerLogicP2P();
		// Estado inicial del autómata
		currentState = OFFLINE;

		// Gancho de cierre de la JVM para garantizar que se da de baja en el directorio si se cierra la app
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				if (controllerPeer != null && controllerPeer.serving()) {
					controllerPeer.stopFileServer(controllerDir);
				}
				if (controllerDir != null && controllerDir.isRegisteredAsServer()) {
					controllerDir.unregisterFileServer();
				}
			} catch (Exception e) {}
		}));
	}

	/**
	 * Método que procesa los comandos introducidos por un usuario. Se encarga
	 * principalmente de invocar los métodos adecuados de NFControllerLogicDir y
	 * NFControllerLogicP2P según el comando.
	 */
	public void testCommunication() {
		assert (NanoFiles.testModeUDP);
		System.out
				.println("[testMode] Attempting to reach directory server at " + controllerDir.getDirectoryHostname());
		controllerDir.testCommunicationWithDirectory();
		System.out.println("[testMode] Test terminated!");
		return;
	}

	/**
	 * Método que procesa los comandos introducidos por un usuario. Se encarga
	 * principalmente de invocar los métodos adecuados de NFControllerLogicDir y
	 * NFControllerLogicP2P según el comando.
	 */
	public void processCommand() {

		if (!canProcessCommandInCurrentState()) {
			return;
		}

		boolean commandSucceeded = false;
		switch (currentCommand) {
		case NFCommands.COM_MYFILES:
			showMyLocalFiles(); // Muestra los ficheros en el directorio local compartido
			break;
		case NFCommands.COM_PING:
			commandSucceeded = controllerDir.ping();
			break;
		case NFCommands.COM_FILELIST_DIR:
			controllerDir.getAndPrintFileList();
			break;
		case NFCommands.COM_PEERLIST:
			controllerDir.getAndPrintPeerList();
			break;
		case NFCommands.COM_FILELIST_PEER:
			java.util.Map<String, InetSocketAddress> peers = controllerDir.fetchPeerList();
			InetSocketAddress peerAddr = peers != null ? peers.get(targetPeerNickname) : null;
			if (peerAddr == null) {
				System.err.println("* Peer '" + targetPeerNickname + "' not found in directory");
				break;
			}
			commandSucceeded = controllerPeer.listPeerFiles(peerAddr);
			break;
		case NFCommands.COM_DOWNLOAD_PEER:
			commandSucceeded = controllerPeer.downloadFromPeers(controllerDir, targetPeerNickname,
					targetHashSubstring);
			break;
		case NFCommands.COM_SERVE:
			if (NanoFiles.testModeTCP) {
				controllerPeer.testTCPServer();
			} else {
				// Arrancar servidor de ficheros con el tamaño de fragmento indicado (en KB) y registrarlo
				commandSucceeded = controllerPeer.startFileServer(controllerDir, serverChunkSizeKB);
			}
			break;
		case NFCommands.COM_DOWNLOAD_DIR:
			commandSucceeded = controllerDir.downloadAndSaveFromDirectory(targetHashSubstring);
			break;
		case NFCommands.COM_WHOHAS:
			commandSucceeded = controllerPeer.whoHas(controllerDir, targetFilenameSubstring);
			break;
		case NFCommands.COM_QUIT:
			// Detener el servidor de ficheros si está en ejecución
			if (controllerPeer.serving()) {
				controllerPeer.stopFileServer(controllerDir);
			}
			// Dar de baja a los peers registrados en el directorio
			if (controllerDir.isRegisteredAsServer()) {
				commandSucceeded = controllerDir.unregisterFileServer();
			} else {
				commandSucceeded = true;
			}
			break;
		case NFCommands.COM_NICK:
			if (controllerPeer.serving()) {
				System.err.println("* Cannot change nickname while serving files. Stop the server first.");
				break;
			}
			if (newNickname == null || newNickname.isBlank()) {
				System.err.println("* Invalid nickname");
				break;
			}
			NanoFiles.peerNickname = newNickname;
			System.out.println("* Nickname changed to: " + NanoFiles.peerNickname);
			commandSucceeded = true;
			break;
		default:
		}

		updateCurrentState(commandSucceeded);
	}

	/**
	 * Método que comprueba si se puede procesar un comando introducidos por un
	 * usuario, en función del estado del autómata en el que nos encontramos.
	 */
	private boolean canProcessCommandInCurrentState() {
		boolean commandAllowed = true;
		switch (currentCommand) {
		case NFCommands.COM_MYFILES:
		case NFCommands.COM_HELP:
		case NFCommands.COM_QUIT:
		case NFCommands.COM_PING:
			commandAllowed = true;
			break;
		case NFCommands.COM_NICK:
			if (currentState == SERVER_RUNNING) {
				System.err.println("* Cannot change nickname while serving files. Stop the server first.");
				commandAllowed = false;
			}
			break;
		case NFCommands.COM_FILELIST_DIR:
		case NFCommands.COM_PEERLIST:
		case NFCommands.COM_FILELIST_PEER:
		case NFCommands.COM_DOWNLOAD_PEER:
		case NFCommands.COM_SERVE:
		case NFCommands.COM_DOWNLOAD_DIR:
		case NFCommands.COM_WHOHAS:
			if (currentState == OFFLINE) {
				System.err.println("* You must ping the directory first before running this command.");
				commandAllowed = false;
			}
			break;
		default:
		}
		return commandAllowed;
	}

	private void updateCurrentState(boolean success) {
		if (!success) {
			return;
		}
		switch (currentCommand) {
		case NFCommands.COM_PING:
			if (currentState == OFFLINE) {
				currentState = CONNECTED_TO_DIR;
			}
			break;
		case NFCommands.COM_SERVE:
			currentState = SERVER_RUNNING;
			break;
		case NFCommands.COM_QUIT:
			currentState = OFFLINE;
			break;
		default:
		}
	}

	private void showMyLocalFiles() {
		System.out.println("List of files in local folder:");
		FileInfo.printToSysout(NanoFiles.db.getFiles());
	}

	/**
	 * Método que comprueba si el usuario ha introducido el comando para salir de la
	 * aplicación
	 */
	public boolean shouldQuit() {
		return currentCommand == NFCommands.COM_QUIT;
	}

	/**
	 * Establece el comando actual
	 */
	private void setCurrentCommand(byte command) {
		currentCommand = command;
	}

	/**
	 * Registra en atributos internos los posibles parámetros del comando tecleado
	 * por el usuario.
	 */
	private void setCurrentCommandArguments(String[] args) {
		switch (currentCommand) {
		case NFCommands.COM_SERVE:
			if (args.length == 1) {
				serverChunkSizeKB = Integer.parseInt(args[0]);
			} else {
				serverChunkSizeKB = NFServer.DEFAULT_CHUNK_SIZE_KB;
			}
			break;
		case NFCommands.COM_WHOHAS:
			targetFilenameSubstring = args[0];
			break;
		case NFCommands.COM_DOWNLOAD_DIR:
			targetHashSubstring = args[0];
			break;
		case NFCommands.COM_DOWNLOAD_PEER:
			targetPeerNickname = args[0];
			targetHashSubstring = args[1];
			break;
		case NFCommands.COM_FILELIST_PEER:
			targetPeerNickname = args[0];
			break;
		case NFCommands.COM_NICK:
			newNickname = args[0];
			break;
		default:
		}
	}

	/**
	 * Método para leer un comando general
	 */
	public void readGeneralCommandFromShell() {
		shell.readGeneralCommand();
		setCurrentCommand(shell.getCommand());
		setCurrentCommandArguments(shell.getCommandArguments());
	}
}

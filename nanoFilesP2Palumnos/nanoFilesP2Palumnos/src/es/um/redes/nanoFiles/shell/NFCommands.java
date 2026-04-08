package es.um.redes.nanoFiles.shell;

public class NFCommands {
	/**
	 * Códigos para todos los comandos soportados por el shell
	 */
	public static final byte COM_INVALID = 0;
	public static final byte COM_QUIT = 1;
	public static final byte COM_MYFILES = 2;
	public static final byte COM_PING = 3;
	public static final byte COM_FILELIST_DIR = 4;
	public static final byte COM_SERVE = 11;
	public static final byte COM_DOWNLOAD_DIR = 25;
	public static final byte COM_DOWNLOAD_PEER = 26;
	public static final byte COM_HELP = 50;
	public static final byte COM_SOCKET_IN = 100;
	public static final byte COM_PEERLIST = 12;
	public static final byte COM_FILELIST_PEER = 13;
// Upload command removed
	public static final byte COM_NICK = 14;


	
	/**
	 * Códigos de los comandos válidos que puede
	 * introducir el usuario del shell. El orden
	 * es importante para relacionarlos con la cadena
	 * que debe introducir el usuario y con la ayuda
	 */
	private static final Byte[] _valid_user_commands = { 
		COM_QUIT,
		COM_MYFILES,
		COM_PING,
		COM_FILELIST_DIR,
		COM_FILELIST_PEER,
		COM_SERVE,
		COM_PEERLIST,
		COM_DOWNLOAD_DIR,
		COM_DOWNLOAD_PEER,
		COM_NICK,
		COM_HELP,
		COM_SOCKET_IN
		};

	/**
	 * cadena exacta de cada orden
	 */
	private static final String[] _valid_user_commands_str = {
			"quit",
			"myfiles",
			"ping",
			"dirfiles",
			"peerfiles",
			"serve",
			"peers",
			"dirdl",
			"peerdl",
			"nick",
			"help"
		};

	/**
	 * Mensaje de ayuda para cada orden
	 */
	private static final String[] _valid_user_commands_help = {
			"quit the application",
			"show contents of local folder (files that may be served)",
			"ping directory to check protocol compatibility",
			"show list of files served by the directory",
			"show list of files served by a peer (by nickname)",
			"run file server and register it with directory",
			"show list of peers registered in the directory",
			"download file from directory by hash substring (keeps remote name)",
			"download file from a specific peer by hash substring (keeps remote name)",
			"change local nickname before serving files",
			"shows this information"
			};

	/**
	 * Transforma una cadena introducida en el código de comando correspondiente
	 */
	public static byte stringToCommand(String comStr) {
		//Busca entre los comandos si es válido y devuelve su código
		for (int i = 0;
		i < _valid_user_commands_str.length; i++) {
			if (_valid_user_commands_str[i].equalsIgnoreCase(comStr)) {
				return _valid_user_commands[i];
			}
		}
		//Si no se corresponde con ninguna cadena entonces devuelve el código de comando no válido
		return COM_INVALID;
	}

	public static String commandToString(byte command) {
		for (int i = 0;
		i < _valid_user_commands.length; i++) {
			if (_valid_user_commands[i] == command) {
				return _valid_user_commands_str[i];
			}
		}
		return null;
	}

	/**
	 * Imprime la lista de comandos y la ayuda de cada uno
	 */
	public static void printCommandsHelp() {
		System.out.println("List of commands:");
		for (int i = 0; i < _valid_user_commands_str.length; i++) {
			System.out.println(String.format("%1$15s", _valid_user_commands_str[i]) + " -- "
					+ _valid_user_commands_help[i]);
		}		
	}
}	

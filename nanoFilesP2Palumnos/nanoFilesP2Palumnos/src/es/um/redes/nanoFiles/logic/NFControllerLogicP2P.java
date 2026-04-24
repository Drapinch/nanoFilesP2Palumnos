package es.um.redes.nanoFiles.logic;

import java.net.InetSocketAddress;
import java.io.IOException;
import es.um.redes.nanoFiles.tcp.client.NFConnector;
import es.um.redes.nanoFiles.application.NanoFiles;
import es.um.redes.nanoFiles.tcp.server.NFServer;

public class NFControllerLogicP2P {
	// Servidor TCP local para compartir ficheros con otros peers
	private NFServer fileServer = null;

	protected NFControllerLogicP2P() {
	}

	/**
	 * Método para ejecutar un servidor de ficheros en segundo plano. Debe arrancar
	 * el servidor en un nuevo hilo creado a tal efecto.
	 * * @return Verdadero si se ha arrancado en un nuevo hilo con el servidor de
	 * ficheros, y está a la escucha en un puerto, falso en caso contrario.
	 * */
	protected boolean startFileServer(NFControllerLogicDir dirLogic) {
		boolean serverRunning = false;
		/*
		 * Comprobar que no existe ya un objeto NFServer previamente creado, en cuyo
		 * caso el servidor ya está en marcha.
		 */
		if (fileServer != null) {
			System.err.println("File server is already running");
		} else {
			/*
			 * Arrancar servidor en segundo plano creando un nuevo hilo, comprobar que el 
			 * servidor está escuchando en un puerto válido (>0), imprimir mensaje 
			 * informando sobre el puerto de escucha, y devolver verdadero.
			 */
			try {
				// 1. Instanciamos el servidor (abre el ServerSocket)
				fileServer = new NFServer();
				
				// 2. Comprobamos que el puerto sea válido antes de seguir
				int port = NFServer.PORT; // Asumiendo que el puerto está en esta constante estática
				
				if (port > 0) {
					// 3. Arrancamos el servidor en un hilo nuevo (segundo plano)
					fileServer.startServer();
					
					// 4. Informamos por pantalla
					System.out.println("* File server started in background, listening on port " + port);
					
					// 5. Registramos nuestro servidor en el directorio para que otros nos vean
					if (dirLogic.registerFileServer(port)) {
						serverRunning = true;
					} else {
						System.err.println("* Error: Could not register file server in the directory.");
						// Si falla el registro, detenemos el servidor para limpiar recursos
						fileServer.stopServer();
						fileServer = null;
					}
				} else {
					System.err.println("* Error: Invalid port configuration (" + port + ")");
					fileServer = null;
				}
				
			} catch (java.io.IOException e) {
				/*
				 * Capturamos excepciones de E/S (ej. puerto ya en uso)
				 * Informamos al usuario sin abortar el programa principal.
				 */
				System.err.println("* Critical error starting file server: " + e.getMessage());
				fileServer = null; // Nos aseguramos de que quede a null para poder reintentar
			}
		}
		return serverRunning;
	}

	protected void testTCPServer() {
		assert (NanoFiles.testModeTCP);
		/*
		 * Comprobar que no existe ya un objeto NFServer previamente creado, en cuyo
		 * caso el servidor ya está en marcha.
		 */
		assert (fileServer == null);
		try {

			fileServer = new NFServer();
			/*
			 * (Boletín SocketsTCP) Inicialmente, se creará un NFServer y se ejecutará su
			 * método "test" (servidor minimalista en primer plano, que sólo puede atender a
			 * un cliente conectado). Posteriormente, se desactivará "testModeTCP" para
			 * implementar un servidor en segundo plano, que se ejecute en un hilo
			 * secundario para permitir que este hilo (principal) siga procesando comandos
			 * introducidos mediante el shell.
			 */
			fileServer.test();
			// Este código es inalcanzable: el método 'test' nunca retorna...
		} catch (IOException e1) {
			e1.printStackTrace();
			System.err.println("Cannot start the file server");
			fileServer = null;
		}
	}

	public void testTCPClient() {

		assert (NanoFiles.testModeTCP);
		/*
		 * (Boletín SocketsTCP) Inicialmente, se creará un NFConnector (cliente TCP)
		 * para conectarse a un servidor que esté escuchando en la misma máquina y un
		 * puerto fijo. Después, se ejecutará el método "test" para comprobar la
		 * comunicación mediante el socket TCP. Posteriormente, se desactivará
		 * "testModeTCP" para implementar la descarga de un fichero desde múltiples
		 * servidores.
		 */

		try {
			InetSocketAddress testServerAddress = new InetSocketAddress("localhost", 10000);
			System.out.println("Iniciando cliente TCP de prueba hacia " + testServerAddress);
			
			NFConnector connector = new NFConnector(testServerAddress);
			connector.test();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Método para listar los ficheros de un peer concreto vía TCP e imprimirlos por
	 * pantalla.
	 * * @param peerAddr La dirección del peer cuyos ficheros se quiere listar
	 * @return Verdadero si se ha obtenido exitosamente el listado de ficheros del peer
	 */
	protected boolean listPeerFiles(InetSocketAddress peerAddr) {
		boolean success = false;
		try {
			// Nos conectamos al peer específico
			NFConnector connector = new NFConnector(peerAddr);
			// Solicitamos y mostramos la lista de ficheros (Asumiendo método en NFConnector)
			System.out.println("* Requesting file list from " + peerAddr);
			
			/*
			 * Nota: el método exacto dependerá de tu NFConnector,
			 * si devuelve un array de FileInfo o simplemente los imprime y devuelve boolean.
			 * Generalmente se usa algo como getFileList()
			 */
			success = connector.getPeerFileList(); 
			
		} catch (IOException e) {
			System.err.println("* Error: No se pudo conectar al peer " + peerAddr + " - " + e.getMessage());
		}
		return success;
	}

	/**
	 * Descarga un fichero identificado por subcadena de hash desde uno o varios
	 * peers. Si se pasa "*" como nickname, usa el directorio para localizar los
	 * peers que tienen el hash.
	 */
	protected boolean downloadFromPeers(NFControllerLogicDir dirLogic, String targetPeerNickname, String targetHashSubstring) {
		InetSocketAddress[] serverList = null;

		if (targetPeerNickname.equals("*")) {
			// Lógica para descargar desde cualquier/todos los peers que tengan el archivo
			// (Depende de si tu dirLogic tiene un método para buscar IPs por hash de archivo)
			// serverList = dirLogic.lookupServersSharingFile(targetHashSubstring);
			System.err.println("* Descarga desde múltiples fuentes (*) pendiente de implementación en el Directorio");
			return false;
		} else {
			// Lógica para descargar desde un peer concreto
			InetSocketAddress peerAddress = dirLogic.lookupUserAddress(targetPeerNickname); 

			if (peerAddress == null) {
				System.err.println("* Error: No se ha encontrado la IP del peer '" + targetPeerNickname + "'. ¿Le pediste la lista al directorio?");
				return false;
			}
			serverList = new InetSocketAddress[] { peerAddress };
		}

		return downloadFileFromServers(serverList, targetHashSubstring);
	}

	/**
	 * Método para descargar un fichero del peer servidor de ficheros
	 * * @param serverAddressList   La lista de direcciones de los servidores a los
	 * que se conectará
	 * @param targetHashSubstring Subcadena del hash del fichero a descargar
	 */
	protected boolean downloadFileFromServers(InetSocketAddress[] serverAddressList, String targetHashSubstring) {
		boolean downloaded = false;

		if (serverAddressList == null || serverAddressList.length == 0) {
			System.err.println("* Cannot start download - No list of server addresses provided");
			return false;
		}

		// Recorremos la lista de servidores hasta que alguno nos pueda enviar el fichero
		// TODO Avanzado: Crear lógica para descargar a trozos (chunks) de múltiples servidores.
		for (InetSocketAddress serverAddr : serverAddressList) {
			try {
				System.out.println("* Intentando descargar desde el servidor: " + serverAddr);
				NFConnector connector = new NFConnector(serverAddr);
				
				// Intentamos la descarga (El conector debería encargarse de verificar el hash final internamente)
				downloaded = connector.downloadFile(targetHashSubstring);
				
				if (downloaded) {
					System.out.println("* Descarga completada con éxito desde " + serverAddr);
					break; // Si logramos descargar el archivo, salimos del bucle
				} else {
					System.err.println("* El servidor " + serverAddr + " no pudo proporcionar el fichero completo.");
				}
				
			} catch (IOException e) {
				System.err.println("* Error de conexión con el servidor " + serverAddr + ": " + e.getMessage());
			}
		}

		return downloaded;
	}

	private String toDisplayPath(java.nio.file.Path path) {
		java.nio.file.Path abs = path.toAbsolutePath().normalize();
		java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath().normalize();
		if (abs.startsWith(cwd)) {
			return cwd.relativize(abs).toString();
		}
		return path.toString();
	}

	/**
	 * Método para obtener el puerto de escucha de nuestro servidor de ficheros
	 * * @return El puerto en el que escucha el servidor, o 0 en caso de error.
	 */
	protected int getServerPort() {
		int port = 0;
		if (fileServer != null) {
			// Se asume que NFServer usa la constante estática PORT, o un getter equivalente
			port = NFServer.PORT; 
		}
		return port;
	}

	/**
	 * Método para detener nuestro servidor de ficheros en segundo plano
	 * */
	protected void stopFileServer(NFControllerLogicDir dirLogic) {
		/*
		 * Enviar señal para detener nuestro servidor de ficheros en segundo plano
		 * y unregister en el directorio.
		 */
		if (fileServer != null) { // Cambiado de backgroundServer a fileServer para mantener consistencia
			// 1. Detenemos el hilo y cerramos el socket
			fileServer.stopServer();
			fileServer = null;
			
			// 2. Nos damos de baja en el directorio
			dirLogic.unregisterFileServer();
			
			System.out.println("* Servidor detenido. Ya no compartes ficheros.");
		} else {
			System.out.println("* El servidor no estaba ejecutándose.");
		}
	}

	protected boolean serving() {
		// Estamos sirviendo ficheros si la instancia de nuestro servidor no es nula
		return fileServer != null;
	}

}
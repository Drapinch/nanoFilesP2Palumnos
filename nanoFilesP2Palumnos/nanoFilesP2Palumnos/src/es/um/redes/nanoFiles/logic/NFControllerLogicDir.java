package es.um.redes.nanoFiles.logic;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import es.um.redes.nanoFiles.application.NanoFiles;
import es.um.redes.nanoFiles.udp.client.DirectoryConnector;
import es.um.redes.nanoFiles.util.FileInfo;

public class NFControllerLogicDir {

	// Conector para enviar y recibir mensajes del directorio
	private DirectoryConnector directoryConnector;

	/**
	 * Construye el controlador encargado de implementar la lógica de los comandos
	 * que requieren interactuar con el servidor de directorio dado a través de la
	 * clase DirectoryConnector.
	 * 
	 * @param directoryHostname el nombre de host/IP en el que se está ejecutando el
	 *                          directorio
	 */
	protected NFControllerLogicDir(String directoryHostname) {
		try {
			directoryConnector = new DirectoryConnector(directoryHostname);
		} catch (IOException e1) {
			System.err.println(
					"* Check your connection, the directory server at " + directoryHostname + " is not available.");
			System.exit(-1);
		}
	}

	/**
	 * Método para comprobar que la comunicación con el directorio es exitosa (se
	 * pueden enviar y recibir datagramas) haciendo uso de la clase
	 * DirectoryConnector
	 * 
	 * @return true si se ha conseguido contactar con el directorio.
	 */
	protected void testCommunicationWithDirectory() {
		assert (NanoFiles.testModeUDP);
		System.out.println(
				"[testMode] Testing communication with directory: " + this.directoryConnector.getDirectoryHostname());
		/*
		 * Utiliza el DirectoryConnector para hacer una prueba de comunicación con el
		 * directorio. Primero testSendAndReceive envía un mensaje "ping" y espera
		 * obtener "welcome" como respuesta. Luego pingDirectoryRaw hace lo mismo que
		 * testSendAndReceive pero enviando además el "protocol ID" para ver si el
		 * directorio es compatible
		 */
		if (directoryConnector.testSendAndReceive()) {
			System.out.println("[testMode] testSendAndReceived - TEST PASSED!");
			/*
			 * (Boletín EstructuraNanoFiles) Test similar al de testSendAndReceive, pero
			 * ampliado para comprobar si el directorio es compatible con el protocol ID,
			 * usando para la comunicación mensajes "en crudo" (sin un formato bien
			 * definido).
			 */
			if (directoryConnector.pingDirectoryRaw()) {
				System.out.println("[testMode] pingDirectoryRaw - SUCCESS!");
			} else {
				System.err.println("[testMode] pingDirectoryRaw - FAILED!");
			}
		} else {
			System.err.println("[testMode] testSendAndReceived - TEST FAILED!");
		}
	}

	/**
	 * Método para comprobar el directorio utiliza un protocolo compatible
	 * 
	 * @return true si se ha conseguido contactar con el directorio.
	 */
	protected boolean ping() {
		boolean result = false;
		System.out.println(
				"* Checking if the directory at " + directoryConnector.getDirectoryHostname() + " is available...");
		result = directoryConnector.pingDirectory();
		if (result) {
			System.out.println("* Directory is active and uses compatible protocol " + NanoFiles.PROTOCOL_ID);
		} else {
			System.err.println("* Ping failed");
		}
		return result;
	}

	/**
	 * Método para obtener y mostrar la lista de ficheros alojados en el directorio
	 */
	protected void getAndPrintFileList() {
		FileInfo[] trackedFiles = directoryConnector.getFileList(); //
		System.out.println(
				"* These are the files tracked by the directory at " + directoryConnector.getDirectoryHostname());
		FileInfo.printToSysout(trackedFiles);
	}

	/**
	 * Método para obtener y mostrar el censo de pares servidor registrados en el
	 * directorio
	 */
	protected void getAndPrintPeerList() {
		Map<String, InetSocketAddress> peers = directoryConnector.getPeerList();
		System.out.println("* Registered peers at " + directoryConnector.getDirectoryHostname());
		if (peers.isEmpty()) {
			System.out.println("  (none)");
			return;
		}
		for (Map.Entry<String, InetSocketAddress> entry : peers.entrySet()) {
			System.out.println("  - " + entry.getKey() + " @ " + entry.getValue());
		}
	}

	/**
	 * Método para obtener el listado de pares servidor registrados en el directorio
	 */
	protected Map<String, InetSocketAddress> fetchPeerList() {
		return directoryConnector.getPeerList();
	}

	/**
	 * Método para registrarse en el directorio como servidor de ficheros en un
	 * puerto determinado. Si el nickname ya está registrado, el directorio debe
	 * devolver el nuevo nickname asignado a este peer durante el registro.
	 * 
	 * @param serverPort El puerto TCP en el que está escuchando el servidor de
	 *                   ficheros.
	 * @return Verdadero si el registro se hace con éxito
	 */
	protected boolean registerFileServer(int serverPort) {
		boolean result = false;
		if (this.directoryConnector.registerFileServer(serverPort)) {



			System.out.println("* File server successfully registered with the directory");
			result = true;
		} else {
			System.err.println("* File server failed to register with the directory");



		}
		return result;
	}





	/**
	 * Método para descargar un fichero del directorio y guardarlo con su nombre
	 * remoto, añadiendo sufijos si hay colisión.
	 */
	protected boolean downloadAndSaveFromDirectory(String hashSubstring) {
		DirectoryConnector.DownloadedFile dl = directoryConnector.downloadFileFromDirectory(hashSubstring);
		if (dl == null) {
			System.err.println("* Failed to download file given by hash substring " + hashSubstring);
			return false;
		}
		try {
			java.nio.file.Path dest = es.um.redes.nanoFiles.util.FileNameUtil.chooseAvailableName(dl.filename);
			java.nio.file.Files.write(dest, dl.data);
			String checksum = es.um.redes.nanoFiles.util.FileDigest.computeFileChecksumString(dest.toString());
			System.out.println("* Downloaded directory file to " + toDisplayPath(dest) + " (" + dl.data.length
					+ " bytes)");
			if (dl.filehash != null) {
				if (dl.filehash.equals(checksum)) {
					System.out.println("* Checksum verified: computed value matches expected hash (" + checksum + ")");
				} else {
					System.err.println("* WARNING: computed checksum (" + checksum + ") does not match expected hash ("
							+ dl.filehash + ")");
				}
			} else {
				System.out.println("* Computed SHA-256: " + checksum);
			}
			return true;
		} catch (java.io.IOException e) {
			System.err.println("* Failed to write downloaded file: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Método para dar de baja a nuestro servidor de ficheros en el directorio.
	 * 
	 * @return Éxito o fracaso de la operación
	 */
	protected boolean unregisterFileServer() {
		boolean result = false;
		if (this.directoryConnector.unregisterFileServer()) {
			System.out.println("* File server successfully unregistered with the directory");
			result = true;
		} else {
			System.err.println("* File server failed to unregister with the directory");
		}
		return result;
	}

	protected String getDirectoryHostname() {
		return directoryConnector.getDirectoryHostname();
	}

	private String toDisplayPath(java.nio.file.Path path) {
		java.nio.file.Path abs = path.toAbsolutePath().normalize();
		java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath().normalize();
		if (abs.startsWith(cwd)) {
			return cwd.relativize(abs).toString();
		}
		return path.toString();
	}

}

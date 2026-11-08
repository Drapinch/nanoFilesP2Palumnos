package es.um.redes.nanoFiles.tcp.server;

import java.net.Socket;

public class NFServerThread extends Thread {
	/*
	 * Esta clase modela los hilos que son creados desde NFServer y cada uno
	 * de los cuales se encarga de invocar a NFServer.serveFilesToClient con el socket
	 * retornado por el método accept.
	 */
	private NFServer server;
	private Socket clientSocket;

	public NFServerThread(NFServer server, Socket socket) {
		this.server = server;
		this.clientSocket = socket;
	}

	public NFServerThread(Socket socket) {
		this(null, socket);
	}

	@Override
	public void run() {
		System.out.println(" [Hilo] Atendiendo a un nuevo cliente en segundo plano...");
		if (server != null) {
			server.serveFilesToClient(clientSocket);
		} else {
			NFServer.serveFilesToClient(clientSocket);
		}
	}
}

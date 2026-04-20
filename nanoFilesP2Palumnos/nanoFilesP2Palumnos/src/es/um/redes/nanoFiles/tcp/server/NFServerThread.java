package es.um.redes.nanoFiles.tcp.server;

import java.net.Socket;

public class NFServerThread extends Thread {
	/*
	 * TODO: Esta clase modela los hilos que son creados desde NFServer y cada uno
	 * de los cuales simplemente se encarga de invocar a
	 * NFServer.serveFilesToClient con el socket retornado por el método accept
	 * (un socket distinto para "conversar" con un cliente)
	 */
	private Socket clientSocket;

	// Constructor: recibe el socket del cliente aceptado
	public NFServerThread(Socket socket) {
		this.clientSocket = socket;
	}

	@Override
	public void run() {
		/*
		 * 
		 * Delegamos la atención del cliente en el método que ya teníamos hecho
		 */
		System.out.println(" [Hilo] Atendiendo a un nuevo cliente en segundo plano...");
		NFServer.serveFilesToClient(clientSocket);
	}
}

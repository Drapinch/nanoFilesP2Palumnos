package es.um.redes.nanoFiles.application;

import java.io.IOException;
import java.net.BindException;
import java.net.SocketException;

import es.um.redes.nanoFiles.udp.server.NFDirectoryServer;

public class Directory {
	public static final double DEFAULT_CORRUPTION_PROBABILITY = 0.0;
	public static final String DEFAULT_DIRECTORY_FILES_PATH = "dir-shared";

	public static void main(String[] args) {
		double datagramCorruptionProbability = DEFAULT_CORRUPTION_PROBABILITY;
		String directoryFilesPath = DEFAULT_DIRECTORY_FILES_PATH;

		/**
		 * Command line argument to directory is optional, if not specified, default
		 * value is used: -loss: probability of corruption of received datagrams
		 */
		String arg;

		int i = 0;
		while (i < args.length) {
			arg = args[i];
			if (!arg.startsWith("-")) {
				System.err.println("Illegal argument " + arg);
				return;
			}
			if (arg.equals("-loss")) {
				if (i + 1 < args.length) {
					try {
						datagramCorruptionProbability = Double.parseDouble(args[i + 1]);
					} catch (NumberFormatException e) {
						System.err.println("Wrong value passed to option " + arg);
						return;
					}
					i += 2;
				} else {
					System.err.println("option " + arg + " requires a value");
					return;
				}
			} else if (arg.equals("-dir")) {
				if (i + 1 < args.length) {
					directoryFilesPath = args[i + 1];
					i += 2;
				} else {
					System.err.println("option " + arg + " requires a value");
					return;
				}
			} else {
				System.err.println("Illegal option " + arg);
				return;
			}
		}
		System.out.println("Probability of corruption for received datagrams: " + datagramCorruptionProbability);
		System.out.println("Directory files path: " + directoryFilesPath);
		NFDirectoryServer dir = null;
		try {
			dir = new NFDirectoryServer(datagramCorruptionProbability, directoryFilesPath);
		} catch (SocketException e) {
			if (isBindFailure(e)) {
				System.err.println("Directory cannot create UDP socket");
				System.err.println("Most likely a Directory process is already running and listening on that port...");
			} else {
				System.err.println("Directory failed to initialize UDP socket: " + e.getMessage());
				e.printStackTrace();
			}
			System.exit(-1);
		}
		try {
			if (NanoFiles.testModeUDP) {
				dir.runTest();
			} else {
				dir.run();
			}
		} catch (SocketException e) {
			System.err.println("Socket error while running directory: " + e.getMessage());
			e.printStackTrace();
			System.exit(-1);
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Unexpected I/O error when running NFDirectoryServer.run");
			System.exit(-1);
		}

	}

	private static boolean isBindFailure(SocketException e) {
		if (e instanceof BindException) {
			return true;
		}
		String msg = e.getMessage();
		return msg != null && msg.toLowerCase().contains("address already in use");
	}

}

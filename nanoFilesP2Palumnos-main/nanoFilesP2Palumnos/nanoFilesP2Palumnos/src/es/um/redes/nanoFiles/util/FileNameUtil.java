package es.um.redes.nanoFiles.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileNameUtil {

	/**
	 * Devuelve una ruta disponible a partir de un nombre base. Si ya existe,
	 * añade sufijos .1, .2, etc. hasta encontrar un nombre libre.
	 */
	public static Path chooseAvailableName(String baseName) {
		Path path = Paths.get(baseName);
		int suffix = 1;
		while (Files.exists(path)) {
			path = Paths.get(baseName + "." + suffix);
			suffix++;
		}
		return path;
	}
}

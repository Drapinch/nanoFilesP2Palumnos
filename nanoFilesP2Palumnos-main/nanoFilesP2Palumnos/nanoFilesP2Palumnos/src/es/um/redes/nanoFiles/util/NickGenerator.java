package es.um.redes.nanoFiles.util;

public class NickGenerator {

	private static final String[] TEMPLATES = { "jim", "tim", "jay", "sam", "pat", "max", "liz", "ivy", "zoe", "mia",
			"bea", "gus", "ted", "ana", "eva", "amy", "leo", "ben", "lou", "joel", "ivan", "otto", "alex", "casey",
			"riley", "toby", "felix", "edith", "fran", "simon", "eric", "danny", "roger" };

	/**
	 * Genera un nickname aleatorio base: prefijo de la lista y un dígito.
	 */
	public static String randomNickname() {
		int idx = (int) (Math.random() * TEMPLATES.length);
		int digit = (int) (Math.random() * 10);
		return TEMPLATES[idx] + digit;
	}

	/**
	 * Genera una variante del nickname original añadiendo un único dígito.
	 */
	public static String variantWithDigit(String base) {
		int digit = (int) (Math.random() * 10);
		return base + digit;
	}
}

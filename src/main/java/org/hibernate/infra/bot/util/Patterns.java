package org.hibernate.infra.bot.util;

import java.util.regex.Pattern;

public class Patterns {

	public static Pattern compile(String pattern) {
		return Pattern.compile(pattern, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	}

	public static boolean find(String pattern, String string) {
		return compile(pattern).matcher(string).find();
	}

	private Patterns() {
	}
}

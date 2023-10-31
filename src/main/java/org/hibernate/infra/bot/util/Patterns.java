package org.hibernate.infra.bot.util;

import java.util.regex.Pattern;

public class Patterns {

	public static Pattern compile(String pattern) {
		return Pattern.compile(pattern, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	}

	private Patterns() {
	}
}

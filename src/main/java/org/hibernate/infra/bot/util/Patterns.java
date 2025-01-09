package org.hibernate.infra.bot.util;

import java.util.Set;
import java.util.regex.Pattern;

public class Patterns {
	private static final Set<Character> REGEX_ESCAPE_CHARS = Set.of( '(', ')', '[', ']', '{', '}', '\\', '.', '?', '*', '+' );

	public static Pattern compile(String pattern) {
		return Pattern.compile(pattern, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	}

	/**
	 * We want to replace any new line character sequences ({@code \n \r \n\r}) with a more predictable one
	 * which should allow us to use {@link String#contains(CharSequence)} in our text-based checks.
	 * <p>
	 * Apparently GitHub body/config files may be returned containing various combinations of these new line character sequences,
	 * and we may end up failing the checks when we shouldn't.
	 */
	public static String sanitizeNewLines(String value) {
		return value == null ? null : value.replaceAll("\\R", "\n");
	}

	public static String escapeSpecialCharacters(String value) {
		if ( value == null ) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for ( char c : value.toCharArray() ) {
			if ( REGEX_ESCAPE_CHARS.contains( c ) ) {
				sb.append( '\\' );
			}
			if ( c == '\n' ) {
				sb.append( '\\' ).append( 'n' );
			}
			else {
				sb.append( c );
			}
		}
		return sb.toString();
	}

	private Patterns() {
	}
}

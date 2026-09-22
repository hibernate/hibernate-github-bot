package org.hibernate.infra.bot.util;

import java.util.List;

public class Markdown {

	public static String joinWithSoftWrapping(List<String> items, String separator, String lineBreak,
			int preferredLineLength) {
		if ( items.isEmpty() ) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		int lineLength = 0;
		for ( int i = 0; i < items.size(); i++ ) {
			String item = items.get( i );
			if ( i > 0 ) {
				if ( lineLength + separator.length() + item.length() > preferredLineLength ) {
					sb.append( lineBreak );
					lineLength = 0;
				}
				else {
					sb.append( separator );
					lineLength += separator.length();
				}
			}
			sb.append( item );
			lineLength += item.length();
		}
		return sb.toString();
	}

	private Markdown() {
	}
}

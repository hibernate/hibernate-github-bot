package org.hibernate.infra.bot.util;

import java.util.regex.Pattern;

public record JenkinsRunId(String job, int run) {

	private static final Pattern FORMAT = Pattern.compile( "(.+)#(\\d+)" );

	public static JenkinsRunId parse(String string) {
		var matcher = FORMAT.matcher( string );
		if ( !matcher.matches() ) {
			throw new IllegalArgumentException( "Invalid format for a Jenkins run ID: " + string );
		}
		return new JenkinsRunId( matcher.group( 1 ), Integer.parseInt( matcher.group( 2 ) ) );
	}
}

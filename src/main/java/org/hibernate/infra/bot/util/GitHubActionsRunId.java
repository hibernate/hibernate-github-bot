package org.hibernate.infra.bot.util;

import java.net.URL;
import java.util.regex.Pattern;

public record GitHubActionsRunId(long run) {

	private static final Pattern FORMAT = Pattern.compile( "actions/runs/(\\d+)/" );

	public static GitHubActionsRunId parse(URL detailsUrl) {
		var matcher = FORMAT.matcher( detailsUrl.toString() );
		if ( !matcher.find() ) {
			throw new IllegalArgumentException( "Invalid format for a GitHub Actions run URL: " + detailsUrl );
		}
		return new GitHubActionsRunId( Long.parseLong( matcher.group( 1 ) ) );
	}
}

package org.hibernate.infra.bot.util;

import java.net.URL;
import java.util.OptionalLong;
import java.util.regex.Pattern;

// The optional /job/{id} segment is not present in URLs published by the Develocity CCUD plugin today
// (it always publishes run-level URLs like .../actions/runs/{runId}/attempts/{attempt}),
// but check run details URLs do contain it, and we parse it speculatively for future-proofing.
public record GitHubActionsRunId(long run, OptionalLong job) {

	private static final Pattern FORMAT = Pattern.compile( "(?:actions/)?runs/(\\d+)(?:/job/(\\d+))?" );

	public GitHubActionsRunId(long run) {
		this( run, OptionalLong.empty() );
	}

	public static GitHubActionsRunId parse(URL detailsUrl) {
		var matcher = FORMAT.matcher( detailsUrl.toString() );
		if ( !matcher.find() ) {
			throw new IllegalArgumentException( "Invalid format for a GitHub Actions run URL: " + detailsUrl );
		}
		long run = Long.parseLong( matcher.group( 1 ) );
		OptionalLong job = matcher.group( 2 ) != null
				? OptionalLong.of( Long.parseLong( matcher.group( 2 ) ) )
				: OptionalLong.empty();
		return new GitHubActionsRunId( run, job );
	}
}

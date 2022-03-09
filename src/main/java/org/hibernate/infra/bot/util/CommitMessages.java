package org.hibernate.infra.bot.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommitMessages {

	private CommitMessages() {
	}

	private static final Pattern issueKeySeparatorPattern = Pattern.compile( "[\\[\\] ,]+" );

	public static List<String> extractIssueKeys(Pattern issueKeyPattern, String commitMessage) {
		Matcher commitMessageIssueKeyMatcher = issueKeyPattern.matcher( commitMessage );
		int previousMatchEndIndex = 0;
		List<String> issueKeys = new ArrayList<>();
		while ( commitMessageIssueKeyMatcher.find() ) {
			int thisMatchStartIndex = commitMessageIssueKeyMatcher.start();
			if ( previousMatchEndIndex != thisMatchStartIndex
					&& !issueKeySeparatorPattern.matcher(
							commitMessage.substring( previousMatchEndIndex, thisMatchStartIndex )
					).matches() ) {
				break;
			}
			issueKeys.add( commitMessageIssueKeyMatcher.group() );
			previousMatchEndIndex = commitMessageIssueKeyMatcher.end();
		}
		return issueKeys;
	}

}

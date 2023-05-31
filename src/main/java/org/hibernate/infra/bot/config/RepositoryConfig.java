package org.hibernate.infra.bot.config;

import java.util.Optional;
import java.util.regex.Pattern;

import javax.swing.text.html.Option;

import org.hibernate.infra.bot.util.Patterns;

public class RepositoryConfig {

	public JiraConfig jira;

	public static class JiraConfig {
		private Optional<Pattern> issueKeyPattern = Optional.empty();

		private Optional<Boolean> insertLinksInPullRequests = Optional.empty();

		public void setProjectKey(String key) {
			issueKeyPattern = Optional.of( Patterns.compile( key + "-\\d+" ) );
		}

		public Optional<Pattern> getIssueKeyPattern() {
			return issueKeyPattern;
		}

		public void setInsertLinksInPullRequests(boolean insertLinksInPullRequests) {
			this.insertLinksInPullRequests = Optional.of( insertLinksInPullRequests );
		}

		public Optional<Boolean> getInsertLinksInPullRequests() {
			return insertLinksInPullRequests;
		}
	}
}

package org.hibernate.infra.bot.config;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.hibernate.infra.bot.util.Patterns;

public class RepositoryConfig {

	public JiraConfig jira;

	public static class JiraConfig {
		private Optional<Pattern> issueKeyPattern = Optional.empty();

		private Optional<Boolean> insertLinksInPullRequests = Optional.empty();

		private List<IgnoreConfiguration> ignore = Collections.emptyList();

		private List<String> ignoreFiles = Collections.emptyList();

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

		public List<IgnoreConfiguration> getIgnore() {
			return ignore;
		}

		public void setIgnore(List<IgnoreConfiguration> ignore) {
			this.ignore = ignore;
		}

		public List<String> getIgnoreFiles() {
			return ignoreFiles;
		}

		public void setIgnoreFiles(List<String> ignoreFiles) {
			this.ignoreFiles = ignoreFiles;
		}
	}

	public static class IgnoreConfiguration {

		private String user;
		private Pattern titlePattern;

		public String getUser() {
			return user;
		}

		public void setUser(String user) {
			this.user = user;
		}

		public Pattern getTitlePattern() {
			return titlePattern;
		}

		public void setTitlePattern(String titlePattern) {
			this.titlePattern = Pattern.compile( titlePattern );
		}
	}
}

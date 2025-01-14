package org.hibernate.infra.bot.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.hibernate.infra.bot.util.Patterns;

public class RepositoryConfig {

	public JiraConfig jira;

	public Develocity develocity;

	public LicenseAgreement licenseAgreement;

	public TaskList pullRequestTasks;

	public static class JiraConfig {
		private Optional<Pattern> issueKeyPattern = Optional.empty();

		private Optional<Boolean> insertLinksInPullRequests = Optional.empty();
		private Integer issueLinksLimit = 3;

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

		public Integer getIssueLinksLimit() {
			return issueLinksLimit;
		}

		public void setIssueLinksLimit(Integer issueLinksLimit) {
			this.issueLinksLimit = issueLinksLimit;
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
			this.titlePattern = Patterns.compile( titlePattern );
		}
	}

	public static class Develocity {
		public BuildScan buildScan;

		public static class BuildScan {

			public boolean addCheck = false;

			public List<ColumnRule> tags = new ArrayList<>();

			public BuildScan() {
			}

			public BuildScan(boolean addCheck, List<ColumnRule> tags) {
				this.addCheck = addCheck;
				this.tags = tags;
			}
		}

		public static class ColumnRule {
			public String column;
			private Pattern pattern;
			public Optional<String> replacement = Optional.empty();

			public ColumnRule() {
			}

			public ColumnRule(String column, Pattern pattern, Optional<String> replacement) {
				this.column = column;
				this.pattern = pattern;
				this.replacement = replacement;
			}

			public Pattern getPattern() {
				return pattern;
			}

			public void setPattern(String pattern) {
				this.pattern = Patterns.compile( pattern );
			}
		}
	}

	public static class LicenseAgreement {
		private Optional<Boolean> enabled = Optional.empty();
		private Pattern pullRequestTemplatePattern = Patterns.compile( ".+([-]{22}.+[-]{22}).++" );
		private List<IgnoreConfiguration> ignore = Collections.emptyList();

		public Optional<Boolean> getEnabled() {
			return enabled;
		}

		public void setEnabled(Boolean enabled) {
			this.enabled = Optional.of( enabled );
		}

		public Pattern getPullRequestTemplatePattern() {
			return pullRequestTemplatePattern;
		}

		public void setPullRequestTemplatePattern(String pullRequestTemplatePattern) {
			this.pullRequestTemplatePattern = Patterns.compile( pullRequestTemplatePattern );
		}

		public List<IgnoreConfiguration> getIgnore() {
			return ignore;
		}

		public void setIgnore(List<IgnoreConfiguration> ignore) {
			this.ignore = ignore;
		}
	}

	public static class TaskList {
		private static final String DEFAULT_TASKS_CATEGORY = "default";
		private Optional<Boolean> enabled = Optional.empty();
		private Map<String, List<String>> tasks = new HashMap<>();
		private List<IgnoreConfiguration> ignore = Collections.emptyList();

		public Optional<Boolean> getEnabled() {
			return enabled;
		}

		public void setEnabled(Boolean enabled) {
			this.enabled = Optional.of( enabled );
		}

		public Map<String, List<String>> getTasks() {
			return tasks;
		}

		public void setTasks(Map<String, List<String>> tasks) {
			this.tasks = tasks;
		}

		public List<String> defaultTasks() {
			return tasks.getOrDefault( DEFAULT_TASKS_CATEGORY, List.of() );
		}

		public List<IgnoreConfiguration> getIgnore() {
			return ignore;
		}

		public void setIgnore(List<IgnoreConfiguration> ignore) {
			this.ignore = ignore;
		}
	}
}

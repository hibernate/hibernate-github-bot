package org.hibernate.infra.bot;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.hibernate.infra.bot.config.DeploymentConfig;
import org.hibernate.infra.bot.config.RepositoryConfig;
import org.hibernate.infra.bot.jira.JiraIssue;
import org.hibernate.infra.bot.jira.JiraIssues;
import org.hibernate.infra.bot.jira.JiraRestClient;
import org.hibernate.infra.bot.util.CommitMessages;
import org.hibernate.infra.bot.util.Patterns;

import org.jboss.logging.Logger;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.PullRequest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;

public class EditPullRequestBodyAddTaskList {
	private static final Logger LOG = Logger.getLogger( EditPullRequestBodyAddTaskList.class );

	private static final String START_MARKER = "<!-- Hibernate GitHub Bot task list start -->";

	private static final String END_MARKER = "<!-- Hibernate GitHub Bot task list end -->";

	@Inject
	DeploymentConfig deploymentConfig;
	@RestClient
	JiraRestClient jiraRestClient;

	void pullRequestChanged(
			@PullRequest.Opened @PullRequest.Reopened @PullRequest.Edited @PullRequest.Synchronize
			GHEventPayload.PullRequest payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig
	) throws IOException {
		addUpdateTaskList( payload.getRepository(), repositoryConfig, payload.getPullRequest() );
	}

	private void addUpdateTaskList(
			GHRepository repository,
			RepositoryConfig repositoryConfig,
			GHPullRequest pullRequest
	) throws IOException {
		if ( repositoryConfig == null || repositoryConfig.pullRequestTasks == null
				|| !repositoryConfig.pullRequestTasks.getEnabled().orElse( Boolean.FALSE ) ) {
			return;
		}

		if ( !shouldCheck( repository, pullRequest, repositoryConfig.pullRequestTasks.getIgnore() ) ) {
			return;
		}

		final Set<String> issueKeys = new HashSet<>();
		if ( repositoryConfig.jira.getIssueKeyPattern().isPresent() ) {
			Pattern issueKeyPattern = repositoryConfig.jira.getIssueKeyPattern().get();

			for ( GHPullRequestCommitDetail commitDetails : pullRequest.listCommits() ) {
				final GHPullRequestCommitDetail.Commit commit = commitDetails.getCommit();
				final List<String> commitIssueKeys = CommitMessages.extractIssueKeys(
						issueKeyPattern,
						commit.getMessage()
				);
				issueKeys.addAll( commitIssueKeys );
			}
		}

		final String originalBody = Objects.toString( pullRequest.getBody(), "" );
		final String currentTasks = currentTaskBody( originalBody );
		final String tasks = generateTaskList( repositoryConfig.pullRequestTasks, issueKeys );

		String body;

		if ( currentTasks == null && tasks == null ) {
			return;
		}

		if ( tasks == null ) {
			body = originalBody.replace( currentTasks, "" );
		}
		else if ( currentTasks != null ) {
			if (tasksAreTheSame( currentTasks, tasks ) ) {
				return;
			}
			body = originalBody.replace( currentTasks, tasks );
		}
		else {
			body = "%s\n\n---\n%s\n%s\n%s".formatted( originalBody, START_MARKER, tasks, END_MARKER );
		}

		if ( !deploymentConfig.isDryRun() ) {
			pullRequest.setBody( body );
		}
		else {
			LOG.info( "Pull request #" + pullRequest.getNumber() + " - Updated PR body: " + body );
		}
	}

	private boolean tasksAreTheSame(String currentTasks, String tasks) {

		return Patterns.compile( Patterns.escapeSpecialCharacters( Patterns.sanitizeNewLines( tasks.trim() ) ).replace( "- \\[ \\]", "- \\[.\\]" ) )
				.matcher( Patterns.sanitizeNewLines( currentTasks.trim() ) )
				.matches();
	}

	private String generateTaskList(RepositoryConfig.TaskList taskListConfiguration, Set<String> issueKeys) {
		if ( issueKeys.isEmpty() ) {
			return null;
		}
		StringBuilder taskList = new StringBuilder();
		taskList.append( "Please make sure that the following tasks are completed:\n" );
		JiraIssues issues = jiraRestClient.find( "key IN (" + String.join( ",", issueKeys ) + ") ORDER BY KEY DESC", "issuetype,key" );
		for ( JiraIssue issue : issues.issues ) {
			taskList.append( "Tasks specific to " )
					.append( issue.key )
					.append( " (" )
					.append( issue.fields.issuetype.name )
					.append( "):\n" );
			addTasks( taskList, taskListConfiguration.getTasks().getOrDefault( issue.fields.issuetype.name.toLowerCase( Locale.ROOT ), taskListConfiguration.defaultTasks() ) );
		}

		return taskList.toString();
	}

	private void addTasks(StringBuilder sb, List<String> tasks) {
		for ( String task : tasks ) {
			sb.append( "- [ ] " )
					.append( task )
					.append( "\n" );
		}
		sb.append( "\n" );
	}

	public static boolean containsUnfinishedTasks(String body) {
		if ( body == null || body.isEmpty() ) {
			return false;
		}
		String taskBody = currentTaskBody( body );
		if ( taskBody == null ) {
			return false;
		}
		return taskBody.contains( "- [ ]" );
	}

	private static String currentTaskBody(String originalBody) {
		// Check if the body already contains the tasks
		final int startIndex = originalBody.indexOf( START_MARKER );
		final int endIndex = startIndex > -1 ? originalBody.indexOf( END_MARKER ) : -1;
		if ( startIndex > -1 && endIndex > -1 ) {
			return originalBody.substring( startIndex + START_MARKER.length() + 1, endIndex - 1 );
		}
		else {
			return null;
		}
	}

	private boolean shouldCheck(GHRepository repository, GHPullRequest pullRequest, List<RepositoryConfig.IgnoreConfiguration> ignoredPRConfigurations) throws IOException {
		GHUser author = pullRequest.getUser();
		String title = pullRequest.getTitle();
		for ( RepositoryConfig.IgnoreConfiguration ignore : ignoredPRConfigurations ) {
			if ( ignore.getUser().equals( author.getLogin() )
					&& ignore.getTitlePattern().matcher( title ).matches() ) {
				return false;
			}
		}
		return !GHIssueState.CLOSED.equals( pullRequest.getState() )
				&& repository.getId() == pullRequest.getBase().getRepository().getId();
	}
}

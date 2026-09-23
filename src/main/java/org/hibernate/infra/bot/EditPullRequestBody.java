package org.hibernate.infra.bot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.hibernate.infra.bot.config.DeploymentConfig;
import org.hibernate.infra.bot.config.Feature;
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

public class EditPullRequestBody {
	private static final Logger LOG = Logger.getLogger( EditPullRequestBody.class );

	private static final int LOCK_STRIPE_COUNT = 1024;
	private static final Lock[] LOCKS;
	static {
		LOCKS = new Lock[LOCK_STRIPE_COUNT];
		for ( int i = 0; i < LOCK_STRIPE_COUNT; i++ ) {
			LOCKS[i] = new ReentrantLock();
		}
	}

	private static final int MAX_RETRIES = 3;

	private static final String TASK_LIST_START_MARKER = "<!-- Hibernate GitHub Bot task list start -->";
	private static final String TASK_LIST_END_MARKER = "<!-- Hibernate GitHub Bot task list end -->";

	private static final String ISSUE_LINKS_START_MARKER = "<!-- Hibernate GitHub Bot issue links start -->";
	private static final String ISSUE_LINKS_END_MARKER = "<!-- Hibernate GitHub Bot issue links end -->";
	private static final String ISSUE_LINKS_EDITOR_WARNING = "<!-- THIS SECTION IS AUTOMATICALLY GENERATED, ANY MANUAL CHANGES WILL BE LOST -->\n";
	private static final String ISSUE_LINKS_LINK_TEMPLATE = "https://hibernate.atlassian.net/browse/%s";

	@Inject
	DeploymentConfig deploymentConfig;
	@RestClient
	JiraRestClient jiraRestClient;

	void pullRequestChanged(
			@PullRequest.Opened @PullRequest.Reopened @PullRequest.Edited @PullRequest.Synchronize
			GHEventPayload.PullRequest payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig
	) throws IOException {
		GHPullRequest pullRequest = payload.getPullRequest();
		GHRepository repository = payload.getRepository();

		if ( GHIssueState.CLOSED.equals( pullRequest.getState() )
				|| repository.getId() != pullRequest.getBase().getRepository().getId() ) {
			return;
		}

		Lock lock = LOCKS[(int) ( pullRequest.getId() % LOCK_STRIPE_COUNT )];
		lock.lock();
		try {
			editBody( repository, repositoryConfig, pullRequest );
		}
		finally {
			lock.unlock();
		}
	}

	private void editBody(
			GHRepository repository,
			RepositoryConfig repositoryConfig,
			GHPullRequest pullRequest
	) throws IOException {
		String originalBody = Objects.toString( pullRequest.getBody(), "" );

		for ( int attempt = 0; attempt < MAX_RETRIES; attempt++ ) {
			String body = originalBody;
			body = addUpdateTaskList( body, repository, repositoryConfig, pullRequest );
			body = addIssueLinks( body, repository, repositoryConfig, pullRequest );

			if ( body.equals( originalBody ) ) {
				return;
			}

			if ( deploymentConfig.isDryRun() ) {
				LOG.info( "Pull request #" + pullRequest.getNumber() + " - Updated PR body: " + body );
				return;
			}

			pullRequest.refresh();
			String currentBody = Objects.toString( pullRequest.getBody(), "" );
			if ( currentBody.equals( originalBody ) ) {
				pullRequest.setBody( body );
				return;
			}

			LOG.info( "Pull request #" + pullRequest.getNumber()
					+ " - Body was modified concurrently, retrying (attempt " + ( attempt + 1 ) + ")" );
			originalBody = currentBody;
		}

		LOG.warn( "Pull request #" + pullRequest.getNumber()
				+ " - Giving up body update after " + MAX_RETRIES + " retries due to concurrent modifications" );
	}

	// ---- Task list ----

	private String addUpdateTaskList(
			String body,
			GHRepository repository,
			RepositoryConfig repositoryConfig,
			GHPullRequest pullRequest
	) throws IOException {
		if ( !Feature.EDIT_PULL_REQUEST_BODY_ADD_TASK_LIST.isEnabled( repositoryConfig ) ) {
			return body;
		}
		if ( repositoryConfig.pullRequestTasks == null
				|| !repositoryConfig.pullRequestTasks.getEnabled().orElse( Boolean.FALSE ) ) {
			return body;
		}
		if ( !shouldAddTaskList( repository, pullRequest, repositoryConfig.pullRequestTasks.getIgnore() ) ) {
			return body;
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

		final String currentTasks = currentTaskBody( body );
		final String tasks = generateTaskList( repositoryConfig.pullRequestTasks, issueKeys );

		if ( currentTasks == null && tasks == null ) {
			return body;
		}

		if ( tasks == null ) {
			return body.replace( currentTasks, "" );
		}
		else if ( currentTasks != null ) {
			if ( tasksAreTheSame( currentTasks, tasks ) ) {
				return body;
			}
			return body.replace( currentTasks, tasks );
		}
		else {
			return "%s\n\n---\n%s\n%s\n%s".formatted( body, TASK_LIST_START_MARKER, tasks, TASK_LIST_END_MARKER );
		}
	}

	private boolean shouldAddTaskList(GHRepository repository, GHPullRequest pullRequest,
			List<RepositoryConfig.IgnoreConfiguration> ignoredPRConfigurations) throws IOException {
		GHUser author = pullRequest.getUser();
		String title = pullRequest.getTitle();
		for ( RepositoryConfig.IgnoreConfiguration ignore : ignoredPRConfigurations ) {
			if ( ignore.getUser().equals( author.getLogin() )
					&& ignore.getTitlePattern().matcher( title ).matches() ) {
				return false;
			}
		}
		return true;
	}

	private boolean tasksAreTheSame(String currentTasks, String tasks) {
		return Patterns.compile( Patterns.escapeSpecialCharacters( Patterns.sanitizeNewLines( tasks.trim() ) )
				.replace( "- \\[ \\]", "- \\[.\\]" ) )
				.matcher( Patterns.sanitizeNewLines( currentTasks.trim() ) )
				.matches();
	}

	private String generateTaskList(RepositoryConfig.TaskList taskListConfiguration, Set<String> issueKeys) {
		if ( issueKeys.isEmpty() ) {
			return null;
		}
		StringBuilder taskList = new StringBuilder();
		taskList.append( "Please make sure that the following tasks are completed:\n" );
		JiraIssues issues = jiraRestClient.find(
				"key IN (" + String.join( ",", issueKeys ) + ") ORDER BY KEY DESC", "issuetype,key" );
		for ( JiraIssue issue : issues.issues ) {
			taskList.append( "Tasks specific to " )
					.append( issue.key )
					.append( " (" )
					.append( issue.fields.issuetype.name )
					.append( "):\n" );
			addTasks( taskList, taskListConfiguration.getTasks().getOrDefault(
					issue.fields.issuetype.name.toLowerCase( Locale.ROOT ),
					taskListConfiguration.defaultTasks() ) );
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
		final int startIndex = originalBody.indexOf( TASK_LIST_START_MARKER );
		final int endIndex = startIndex > -1 ? originalBody.indexOf( TASK_LIST_END_MARKER ) : -1;
		if ( startIndex > -1 && endIndex > -1 ) {
			return originalBody.substring( startIndex + TASK_LIST_START_MARKER.length() + 1, endIndex - 1 );
		}
		else {
			return null;
		}
	}

	// ---- Issue links ----

	private String addIssueLinks(
			String body,
			GHRepository repository,
			RepositoryConfig repositoryConfig,
			GHPullRequest pullRequest
	) {
		if ( !Feature.EDIT_PULL_REQUEST_BODY_ADD_ISSUE_LINKS.isEnabled( repositoryConfig ) ) {
			return body;
		}
		if ( repositoryConfig.jira == null
				|| repositoryConfig.jira.getIssueKeyPattern().isEmpty()
				|| repositoryConfig.jira.getInsertLinksInPullRequests().isEmpty()
				|| repositoryConfig.jira.getInsertLinksInPullRequests().get().equals( Boolean.FALSE ) ) {
			return body;
		}

		final Set<String> issueKeys = new HashSet<>();
		repositoryConfig.jira.getIssueKeyPattern().ifPresent( issueKeyPattern -> {
			for ( GHPullRequestCommitDetail commitDetails : pullRequest.listCommits() ) {
				final GHPullRequestCommitDetail.Commit commit = commitDetails.getCommit();
				final List<String> commitIssueKeys = CommitMessages.extractIssueKeys(
						issueKeyPattern,
						commit.getMessage()
				);
				issueKeys.addAll( commitIssueKeys );
			}
		} );

		if ( issueKeys.isEmpty() ) {
			LOG.debug( "Found no issue keys in commits, terminating." );
			return body;
		}
		else if ( issueKeys.size() > repositoryConfig.jira.getIssueLinksLimit() ) {
			LOG.debug( "Found more issues than the configured limit, terminating." );
			return body;
		}

		final int startIndex = body.indexOf( ISSUE_LINKS_START_MARKER );
		final int endIndex = startIndex > -1 ? body.indexOf( ISSUE_LINKS_END_MARKER ) : -1;
		final String bodyWithoutLinks = removeLinksSection( body, startIndex, endIndex );

		final String linksSection = constructLinksSection( issueKeys, bodyWithoutLinks );
		if ( linksSection == null ) {
			return body;
		}

		return bodyWithoutLinks.isEmpty() ? linksSection : bodyWithoutLinks + "\n\n" + linksSection;
	}

	private String constructLinksSection(Set<String> issueKeys, String body) {
		final String lowerCaseBody = body.toLowerCase( Locale.ROOT );
		final List<String> links = new ArrayList<>( issueKeys.size() );
		for ( String key : issueKeys ) {
			String link = String.format( Locale.ROOT, ISSUE_LINKS_LINK_TEMPLATE, key );
			if ( !lowerCaseBody.contains( link.toLowerCase( Locale.ROOT ) ) ) {
				links.add( link );
			}
		}

		if ( links.isEmpty() ) {
			return null;
		}

		return String.format( Locale.ROOT, "%s\n%s%s\n%s",
				ISSUE_LINKS_START_MARKER, ISSUE_LINKS_EDITOR_WARNING,
				String.join( "\n", links ), ISSUE_LINKS_END_MARKER );
	}

	private static String removeLinksSection(String originalBody, int startIndex, int endIndex) {
		if ( startIndex > -1 && endIndex > -1 ) {
			final StringBuilder sb = new StringBuilder();
			sb.append( originalBody.substring( 0, startIndex ).trim() );
			final String following = originalBody.substring( endIndex + ISSUE_LINKS_END_MARKER.length() ).trim();
			if ( !following.isEmpty() ) {
				sb.append( "\n\n" );
				sb.append( following );
			}
			return sb.toString();
		}
		else {
			return originalBody.trim();
		}
	}
}

package org.hibernate.infra.bot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Inject;

import org.hibernate.infra.bot.prcheck.PullRequestCheck;
import org.hibernate.infra.bot.prcheck.PullRequestCheckRunContext;
import org.hibernate.infra.bot.prcheck.PullRequestCheckRunOutput;
import org.hibernate.infra.bot.prcheck.PullRequestCheckRunRule;
import org.hibernate.infra.bot.config.DeploymentConfig;
import org.hibernate.infra.bot.config.RepositoryConfig;
import org.hibernate.infra.bot.util.CommitMessages;
import org.hibernate.infra.bot.util.GlobMatcher;
import org.hibernate.infra.bot.util.Patterns;

import org.jboss.logging.Logger;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.CheckRun;
import io.quarkiverse.githubapp.event.CheckSuite;
import io.quarkiverse.githubapp.event.PullRequest;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;

public class CheckPullRequestContributionRules {

	private static final Logger LOG = Logger.getLogger( CheckPullRequestContributionRules.class );

	private static final Pattern SPACE_PATTERN = Pattern.compile( "\\s+" );

	private static final String COMMENT_INTRO_PASSED = """
			Thanks for your pull request!

			This pull request appears to follow the contribution rules.""";
	private static final String COMMENT_INTRO_FAILED = """
			Thanks for your pull request!

			This pull request does not follow the contribution rules. Could you have a look?
			""";
	private static final String COMMENT_FOOTER = "\n\n› This message was automatically generated.";

	@Inject
	DeploymentConfig deploymentConfig;

	void pullRequestChanged(
			@PullRequest.Opened @PullRequest.Reopened @PullRequest.Edited @PullRequest.Synchronize
			GHEventPayload.PullRequest payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig,
			@ConfigFile("PULL_REQUEST_TEMPLATE.md") String pullRequestTemplate) throws IOException {
		checkPullRequestContributionRules( payload.getRepository(), repositoryConfig, pullRequestTemplate, payload.getPullRequest() );
	}

	void checkRunRequested(@CheckRun.Rerequested GHEventPayload.CheckRun payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig,
			@ConfigFile("PULL_REQUEST_TEMPLATE.md") String pullRequestTemplate) throws IOException {
		for ( GHPullRequest pullRequest : payload.getCheckRun().getPullRequests() ) {
			checkPullRequestContributionRules( payload.getRepository(), repositoryConfig, pullRequestTemplate, pullRequest );
		}
	}

	void checkSuiteRequested(@CheckSuite.Requested @CheckSuite.Rerequested GHEventPayload.CheckSuite payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig,
			@ConfigFile("PULL_REQUEST_TEMPLATE.md") String pullRequestTemplate) throws IOException {
		for ( GHPullRequest pullRequest : payload.getCheckSuite().getPullRequests() ) {
			checkPullRequestContributionRules( payload.getRepository(), repositoryConfig, pullRequestTemplate, pullRequest );
		}
	}

	private void checkPullRequestContributionRules(GHRepository repository, RepositoryConfig repositoryConfig,
			String pullRequestTemplate,
			GHPullRequest pullRequest)
			throws IOException {
		if ( !shouldCheck( repository, pullRequest ) ) {
			return;
		}

		PullRequestCheckRunContext context = new PullRequestCheckRunContext( deploymentConfig, repository, repositoryConfig, pullRequest );
		List<PullRequestCheck> checks = createChecks( repositoryConfig, pullRequestTemplate );
		List<PullRequestCheckRunOutput> outputs = new ArrayList<>();
		for ( PullRequestCheck check : checks ) {
			outputs.add( PullRequestCheck.run( context, check ) );
		}

		boolean passed = outputs.stream().allMatch( PullRequestCheckRunOutput::passed );
		GHIssueComment existingComment = findExistingComment( pullRequest );
		// Avoid creating noisy comments for no reason, in particular if checks passed
		// or if the pull request was already closed.
		if ( existingComment == null && ( passed || GHIssueState.CLOSED.equals( pullRequest.getState() ) ) ) {
			return;
		}

		StringBuilder message = new StringBuilder( passed ? COMMENT_INTRO_PASSED : COMMENT_INTRO_FAILED );
		outputs.forEach( output -> output.appendFailingRules( message ) );
		message.append( COMMENT_FOOTER );

		if ( !deploymentConfig.isDryRun() ) {
			if ( existingComment == null ) {
				pullRequest.comment( message.toString() );
			}
			else {
				existingComment.update( message.toString() );
			}
		}
		else {
			LOG.info( "Pull request #" + pullRequest.getNumber() + " - Add comment " + message.toString() );
		}
	}

	// GitHub sometimes mentions pull requests in the payload that are definitely not related to the changes,
	// such as very old pull requests on the branch that just got updated,
	// or pull requests on different repositories.
	// We have to ignore those, otherwise we'll end up creating comments on old pull requests.
	private boolean shouldCheck(GHRepository repository, GHPullRequest pullRequest) {
		return !GHIssueState.CLOSED.equals( pullRequest.getState() )
				&& repository.getId() == pullRequest.getBase().getRepository().getId();
	}

	private GHIssueComment findExistingComment(GHPullRequest pullRequest) throws IOException {
		for ( GHIssueComment comment : pullRequest.listComments() ) {
			if ( comment.getBody().startsWith( COMMENT_INTRO_PASSED )
					|| comment.getBody().startsWith( COMMENT_INTRO_FAILED ) ) {
				return comment;
			}
		}
		return null;
	}

	private List<PullRequestCheck> createChecks(RepositoryConfig repositoryConfig, String pullRequestTemplate) {
		List<PullRequestCheck> checks = new ArrayList<>();
		checks.add( new TitleCheck() );

		if ( repositoryConfig != null && repositoryConfig.jira != null ) {
			final Integer issueLinksLimit = repositoryConfig.jira.getInsertLinksInPullRequests().isPresent()
					&& repositoryConfig.jira.getInsertLinksInPullRequests().get().equals( Boolean.TRUE )
					? repositoryConfig.jira.getIssueLinksLimit()
					: null;
			repositoryConfig.jira.getIssueKeyPattern()
					.ifPresent( issueKeyPattern -> checks.add(
							new JiraIssuesCheck(
									issueKeyPattern, issueLinksLimit, repositoryConfig.jira.getIgnore(),
									repositoryConfig.jira.getIgnoreFiles()
							) ) );
		}

		if ( repositoryConfig != null
				&& repositoryConfig.licenseAgreement != null
				&& repositoryConfig.licenseAgreement.getEnabled().orElse( Boolean.FALSE ) ) {
			Matcher matcher = repositoryConfig.licenseAgreement.getPullRequestTemplatePattern().matcher( pullRequestTemplate );
			if ( matcher.matches() && matcher.groupCount() == 1 ) {
				checks.add( new LicenseCheck( matcher.group( 1 ).trim(), repositoryConfig.licenseAgreement.getIgnore() ) );
			}
			else {
				throw new IllegalArgumentException( "Misconfigured license agreement check. Pattern should contain exactly 1 match group. Pattern: %s. Fetched Pull Request template: %s".formatted( repositoryConfig.licenseAgreement.getPullRequestTemplatePattern(), pullRequestTemplate ) );
			}
		}

		if ( repositoryConfig != null && repositoryConfig.pullRequestTasks != null
				&& repositoryConfig.pullRequestTasks.getEnabled().orElse( Boolean.FALSE ) ) {
			checks.add( new TasksCompletedCheck(repositoryConfig.pullRequestTasks.getIgnore() ) );
		}

		return checks;
	}

	static class TitleCheck extends PullRequestCheck {

		TitleCheck() {
			super( "Contribution — Title" );
		}

		@Override
		public void perform(PullRequestCheckRunContext context, PullRequestCheckRunOutput output) {
			String title = context.pullRequest.getTitle();

			output.rule( "The pull request title should contain at least 2 words to describe the change properly" )
					.result( title != null && SPACE_PATTERN.split( title.trim() ).length >= 2 );
			output.rule( "The pull request title should not end with an ellipsis (make sure the title is complete)" )
					.result( title == null || !title.endsWith( "…" ) );
		}
	}

	static class JiraIssuesCheck extends IgnorablePullRequestCheck {

		private final Pattern issueKeyPattern;

		private final Integer issueLinksLimit;

		private final GlobMatcher ignoredFilesMatcher;

		JiraIssuesCheck(Pattern issueKeyPattern, Integer issueLinksLimit,
				List<RepositoryConfig.IgnoreConfiguration> ignoredPRConfigurations,
				List<String> ignoreFilePatterns) {
			super( "Contribution — JIRA issues", ignoredPRConfigurations );
			this.issueKeyPattern = issueKeyPattern;
			this.issueLinksLimit = issueLinksLimit;
			this.ignoredFilesMatcher = new GlobMatcher( ignoreFilePatterns );
		}

		@Override
		public void doPerform(PullRequestCheckRunContext context, PullRequestCheckRunOutput output) throws IOException {
			String title = context.pullRequest.getTitle();
			String body = context.pullRequest.getBody();

			Set<String> issueKeys = new LinkedHashSet<>();
			Set<String> commitsWithMessageNotStartingWithIssueKey = new LinkedHashSet<>();
			for ( GHPullRequestCommitDetail commitDetails : context.pullRequest.listCommits() ) {
				GHPullRequestCommitDetail.Commit commit = commitDetails.getCommit();
				String sha = commitDetails.getSha();
				List<String> commitIssueKeys = CommitMessages.extractIssueKeys( issueKeyPattern, commit.getMessage() );
				if ( commitIssueKeys.isEmpty() && !ignoredFilesMatcher.allFilesMatch( context.repository, sha ) ) {
					commitsWithMessageNotStartingWithIssueKey.add( sha );
				}
				else {
					issueKeys.addAll( commitIssueKeys );
				}
			}

			PullRequestCheckRunRule commitRule =
					output.rule( "All commit messages should start with a JIRA issue key matching pattern `"
							+ issueKeyPattern + "`" );
			if ( commitsWithMessageNotStartingWithIssueKey.isEmpty() ) {
				commitRule.passed();
			}
			else {
				commitRule.failed( "Offending commits: " + commitsWithMessageNotStartingWithIssueKey );
			}

			if ( issueLinksLimit == null || issueKeys.size() > issueLinksLimit ) {
				// We only need to check mentions if automatic body editing is disabled
				PullRequestCheckRunRule pullRequestRule = output.rule(
						"The PR title or body should list the keys of all JIRA issues mentioned in the commits" );
				List<String> issueKeysNotMentionedInPullRequest = issueKeys.stream()
						.filter( issueKey -> ( title == null || !title.contains( issueKey ) )
								&& ( body == null || !body.contains( issueKey ) ) )
						.toList();
				if ( issueKeysNotMentionedInPullRequest.isEmpty() ) {
					pullRequestRule.passed();
				}
				else {
					pullRequestRule.failed(
							"Issue keys mentioned in commits but missing from the PR title or body: " + issueKeysNotMentionedInPullRequest );
				}
			}
		}
	}

	static class LicenseCheck extends IgnorablePullRequestCheck {

		private final String agreementText;

		protected LicenseCheck(String agreementText, List<RepositoryConfig.IgnoreConfiguration> ignoredPRConfigurations) {
			super( "Contribution — License agreement", ignoredPRConfigurations );
			this.agreementText = Patterns.sanitizeNewLines( agreementText );
		}

		@Override
		public void doPerform(PullRequestCheckRunContext context, PullRequestCheckRunOutput output) {
			String body = Patterns.sanitizeNewLines( context.pullRequest.getBody() );
			PullRequestCheckRunRule rule = output.rule( "The pull request description must contain the license agreement text." );
			if ( body != null && body.contains( agreementText ) ) {
				rule.passed();
			}
			else {
				rule.failed( """
						The description of this pull request must contain the following license agreement text:
						```
						%s
						```
						""".formatted( agreementText ) );
			}
		}
	}

	static class TasksCompletedCheck extends IgnorablePullRequestCheck {

		protected TasksCompletedCheck(List<RepositoryConfig.IgnoreConfiguration> ignoredPRConfigurations) {
			super( "Contribution — Review tasks", ignoredPRConfigurations );
		}

		@Override
		public void doPerform(PullRequestCheckRunContext context, PullRequestCheckRunOutput output) {
			String body = context.pullRequest.getBody();
			output.rule( "All pull request tasks should be completed." )
					.result( !EditPullRequestBodyAddTaskList.containsUnfinishedTasks( body ) );
		}
	}

	static abstract class IgnorablePullRequestCheck extends PullRequestCheck {

		private final List<RepositoryConfig.IgnoreConfiguration> ignoredPRConfigurations;

		protected IgnorablePullRequestCheck(String title, List<RepositoryConfig.IgnoreConfiguration> ignoredPRConfigurations) {
			super( title );
			this.ignoredPRConfigurations = ignoredPRConfigurations;
		}

		@Override
		public final void perform(PullRequestCheckRunContext context, PullRequestCheckRunOutput output) throws IOException {
			if ( !shouldCheckPullRequest( context ) ) {
				// Means we have an ignore rule configured that matches our pull request.
				// No need to check anything else.
				return;
			}

			doPerform( context, output );
		}

		abstract void doPerform(PullRequestCheckRunContext context, PullRequestCheckRunOutput output) throws IOException;

		protected boolean shouldCheckPullRequest(PullRequestCheckRunContext context) throws IOException {
			GHUser author = context.pullRequest.getUser();
			String title = context.pullRequest.getTitle();
			for ( RepositoryConfig.IgnoreConfiguration ignore : ignoredPRConfigurations ) {
				if ( ignore.getUser().equals( author.getLogin() )
						&& ignore.getTitlePattern().matcher( title ).matches() ) {
					return false;
				}
			}

			return true;
		}
	}

}

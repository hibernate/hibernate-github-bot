package org.hibernate.infra.bot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;

import org.hibernate.infra.bot.check.Check;
import org.hibernate.infra.bot.check.CheckRunContext;
import org.hibernate.infra.bot.check.CheckRunOutput;
import org.hibernate.infra.bot.check.CheckRunRule;
import org.hibernate.infra.bot.config.DeploymentConfig;
import org.hibernate.infra.bot.config.RepositoryConfig;

import org.jboss.logging.Logger;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.CheckRun;
import io.quarkiverse.githubapp.event.PullRequest;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHRepository;

class CheckPullRequestContributionRules {

	private static final Logger LOG = Logger.getLogger( CheckPullRequestContributionRules.class );

	private static final Pattern SPACE_PATTERN = Pattern.compile( "\\s+" );

	@Inject
	DeploymentConfig deploymentConfig;

	void run(@PullRequest.Opened @PullRequest.Edited GHEventPayload.PullRequest payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig) throws IOException {
		checkPullRequestContributionRules( payload.getRepository(), repositoryConfig,
				payload.getPullRequest()
		);
	}

	void rerun(@CheckRun.Rerequested GHEventPayload.CheckRun payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig) throws IOException {
		checkPullRequestContributionRules( payload.getRepository(), repositoryConfig,
				// TODO this won't work if multiple pull requests have the same HEAD.
				payload.getCheckRun().getPullRequests().get( 0 )
		);
	}

	private void checkPullRequestContributionRules(GHRepository repository, RepositoryConfig repositoryConfig,
			GHPullRequest pullRequest)
			throws IOException {
		CheckRunContext context = new CheckRunContext( deploymentConfig, repository, repositoryConfig, pullRequest );
		List<Check> checks = createChecks( repositoryConfig );
		List<CheckRunOutput> outputs = new ArrayList<>();
		for ( Check check : checks ) {
			outputs.add( Check.run( context, check ) );
		}

		if ( outputs.stream().allMatch( CheckRunOutput::passed ) ) {
			return;
		}

		// Something failed; make sure to trigger a notification.
		StringBuilder comment = new StringBuilder( "Thanks for your pull request!\n\n"
				+ "This pull request does not follow the contribution rules. Could you have a look?\n" );

		outputs.forEach( output -> output.appendFailingRules( comment ) );

		comment.append( "\n\n› This message was automatically generated." );

		if ( !deploymentConfig.isDryRun() ) {
			pullRequest.comment( comment.toString() );
		}
		else {
			LOG.info( "Pull request #" + pullRequest.getNumber() + " - Add comment " + comment.toString() );
		}
	}

	private List<Check> createChecks(RepositoryConfig repositoryConfig) {
		List<Check> checks = new ArrayList<>();
		checks.add( new TitleCheck() );

		if ( repositoryConfig != null && repositoryConfig.jira != null ) {
			repositoryConfig.jira.getIssueKeyPattern()
					.ifPresent( issueKeyPattern -> checks.add( new JiraIssuesCheck( issueKeyPattern ) ) );
		}

		return checks;
	}

	static class TitleCheck extends Check {

		TitleCheck() {
			super( "Contribution — Title" );
		}

		@Override
		public void perform(CheckRunContext context, CheckRunOutput output) {
			String title = context.pullRequest.getTitle();

			output.rule( "The pull request title should not be empty" )
					.result( title != null && !title.isEmpty() );
			output.rule( "The pull request title should not end with a dot" )
					.result( title == null || !title.endsWith( "." ) );
			output.rule( "The pull request title should not end with an ellipsis (make sure the title is complete)" )
					.result( title == null || !title.endsWith( "…" ) );
			output.rule( "The pull request title should contain at least 2 words to describe the change properly" )
					.result( title == null || SPACE_PATTERN.split( title.trim() ).length >= 2 );
		}
	}

	static class JiraIssuesCheck extends Check {

		private final Pattern issueKeyPattern;

		JiraIssuesCheck(Pattern issueKeyPattern) {
			super( "Contribution — JIRA issues" );
			this.issueKeyPattern = issueKeyPattern;
		}

		@Override
		public void perform(CheckRunContext context, CheckRunOutput output) throws IOException {
			String title = context.pullRequest.getTitle();
			String body = context.pullRequest.getBody();

			Set<String> issueKeys = new LinkedHashSet<>();
			Set<String> commitsWithMessageNotStartingWithIssueKey = new LinkedHashSet<>();
			for ( GHPullRequestCommitDetail commitDetails : context.pullRequest.listCommits() ) {
				GHPullRequestCommitDetail.Commit commit = commitDetails.getCommit();
				String message = commit.getMessage();
				Matcher commitMessageIssueKeyMatcher = issueKeyPattern.matcher( message );
				int issueKeyIndex = commitMessageIssueKeyMatcher.find() ? commitMessageIssueKeyMatcher.start() : -1;
				if ( issueKeyIndex == 0 ) {
					issueKeys.add( commitMessageIssueKeyMatcher.group() );
				}
				else {
					commitsWithMessageNotStartingWithIssueKey.add( commitDetails.getSha() );
				}
			}

			CheckRunRule commitRule =
					output.rule( "All commit messages should start with a JIRA issue key matching pattern `"
							+ issueKeyPattern + "`" );
			if ( commitsWithMessageNotStartingWithIssueKey.isEmpty() ) {
				commitRule.passed();
			}
			else {
				commitRule.failed( "Offending commits: " + commitsWithMessageNotStartingWithIssueKey );
			}

			CheckRunRule pullRequestRule = output.rule(
					"All JIRA issues addressed by commits should be mentioned in the PR title or body" );
			List<String> issueKeysNotMentionedInPullRequest = issueKeys.stream()
					.filter( issueKey -> !title.contains( issueKey ) && !body.contains( issueKey ) )
					.collect( Collectors.toList() );
			if ( issueKeysNotMentionedInPullRequest.isEmpty() ) {
				pullRequestRule.passed();
			}
			else {
				pullRequestRule.failed( "Issues not mentioned: " + issueKeysNotMentionedInPullRequest );
			}
		}
	}

}

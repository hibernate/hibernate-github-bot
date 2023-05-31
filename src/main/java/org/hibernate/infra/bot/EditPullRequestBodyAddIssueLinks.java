package org.hibernate.infra.bot;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.hibernate.infra.bot.config.DeploymentConfig;
import org.hibernate.infra.bot.config.RepositoryConfig;
import org.hibernate.infra.bot.util.CommitMessages;

import org.jboss.logging.Logger;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.CheckRun;
import io.quarkiverse.githubapp.event.CheckSuite;
import io.quarkiverse.githubapp.event.PullRequest;
import jakarta.inject.Inject;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHRepository;

/**
 * @author Marco Belladelli
 */
public class EditPullRequestBodyAddIssueLinks {
	private static final Logger LOG = Logger.getLogger( EditPullRequestBodyAddIssueLinks.class );

	private static final String START_MARKER = "<!-- Hibernate GitHub Bot issue links start -->";

	private static final String END_MARKER = "<!-- Hibernate GitHub Bot issue links end -->";

	private static final String EDITOR_WARNING = "<!-- THIS SECTION IS AUTOMATICALLY GENERATED, ANY MANUAL CHANGES WILL BE LOST -->\n";

	private static final String LINK_TEMPLATE = "https://hibernate.atlassian.net/browse/%s";

	@Inject
	DeploymentConfig deploymentConfig;

	void pullRequestChanged(
			@PullRequest.Opened @PullRequest.Reopened @PullRequest.Edited @PullRequest.Synchronize
			GHEventPayload.PullRequest payload,
			@ConfigFile( "hibernate-github-bot.yml" ) RepositoryConfig repositoryConfig) throws IOException {
		editPullRequestBodyAddIssueLinks( payload.getRepository(), repositoryConfig, payload.getPullRequest() );
	}

	private void editPullRequestBodyAddIssueLinks(
			GHRepository repository,
			RepositoryConfig repositoryConfig,
			GHPullRequest pullRequest) throws IOException {
		if ( repositoryConfig == null || repositoryConfig.jira == null
				|| repositoryConfig.jira.getIssueKeyPattern().isEmpty()
				|| repositoryConfig.jira.getInsertLinksInPullRequests().isEmpty()
				|| repositoryConfig.jira.getInsertLinksInPullRequests().get().equals( Boolean.FALSE ) ) {
			return;
		}

		if ( !shouldCheck( repository, pullRequest ) ) {
			return;
		}

		final Set<String> issueKeys = new HashSet<>();
		// Collect all issue keys from commit messages
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
			return;
		}

		final String originalBody = pullRequest.getBody();
		final StringBuilder sb = new StringBuilder();
		if ( originalBody != null ) {
			// Check if the body already contains the link section
			final int startIndex = originalBody.indexOf( START_MARKER );
			final int endIndex = startIndex > -1 ? originalBody.indexOf( END_MARKER ) : -1;
			if ( startIndex > -1 && endIndex > -1 ) {
				// Remove the whole section, it will be re-appended at the end of the body
				sb.append( originalBody.substring( 0, startIndex ).trim() );
				final String following = originalBody.substring( endIndex + END_MARKER.length() ).trim();
				if ( following.length() > 0 ) {
					sb.append( "\n\n" );
					sb.append( following );
				}
			}
			else {
				sb.append( originalBody.trim() );
			}
		}

		final String body = sb.toString();
		final String linksSection = constructLinksSection( issueKeys, body );
		if ( linksSection == null ) {
			// All issue links were already found in the request body, nothing to do
			return;
		}

		final String newBody = body.length() == 0
				? linksSection
				: body + "\n\n" + linksSection;
		if ( !deploymentConfig.isDryRun() ) {
			pullRequest.setBody( newBody );
		}
		else {
			LOG.info( "Pull request #" + pullRequest.getNumber() + " - Updated PR body: " + newBody );
		}
	}

	private String constructLinksSection(Set<String> issueKeys, String originalBody) {
		final String lowerCaseBody = originalBody.toLowerCase( Locale.ROOT );
		final StringBuilder sb = new StringBuilder();
		for ( String key : issueKeys ) {
			if ( !lowerCaseBody.contains( key.toLowerCase( Locale.ROOT ) ) ) {
				// Only add links for issue keys that are not already found
				// in the original PR body
				sb.append( String.format( LINK_TEMPLATE, key ) ).append( '\n' );
			}
		}

		if ( sb.isEmpty() ) {
			return null;
		}

		return START_MARKER + "\n" + EDITOR_WARNING + sb + END_MARKER;
	}

	private boolean shouldCheck(GHRepository repository, GHPullRequest pullRequest) {
		return !GHIssueState.CLOSED.equals( pullRequest.getState() )
				&& repository.getId() == pullRequest.getBase().getRepository().getId();
	}
}

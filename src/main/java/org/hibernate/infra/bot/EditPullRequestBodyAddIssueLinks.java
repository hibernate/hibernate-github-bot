package org.hibernate.infra.bot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.hibernate.infra.bot.config.DeploymentConfig;
import org.hibernate.infra.bot.config.RepositoryConfig;
import org.hibernate.infra.bot.util.CommitMessages;

import org.jboss.logging.Logger;

import io.quarkiverse.githubapp.ConfigFile;
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
		else if ( issueKeys.size() > repositoryConfig.jira.getIssueLinksLimit() ) {
			LOG.debug( "Found more issues than the configured limit, terminating." );
			return;
		}

		final String originalBody = Objects.toString( pullRequest.getBody(), "" );

		// Check if the body already contains the link section
		final int startIndex = originalBody.indexOf( START_MARKER );
		final int endIndex = startIndex > -1 ? originalBody.indexOf( END_MARKER ) : -1;
		final String body = removeLinksSection( originalBody, startIndex, endIndex );

		final String linksSection = constructLinksSection( issueKeys, body );
		if ( linksSection == null ) {
			// All issue links were already found in the request body, nothing to do
			return;
		}

		final String newBody = body.isEmpty() ? linksSection : body + "\n\n" + linksSection;
		if ( !deploymentConfig.isDryRun() ) {
			pullRequest.setBody( newBody );
		}
		else {
			LOG.info( "Pull request #" + pullRequest.getNumber() + " - Updated PR body: " + newBody );
		}
	}

	private String constructLinksSection(Set<String> issueKeys, String body) {
		final String lowerCaseBody = body.toLowerCase( Locale.ROOT );
		final List<String> links = new ArrayList<>( issueKeys.size() );
		for ( String key : issueKeys ) {
			// Add links for issue keys that are not already found in the original PR body
			String link = String.format( Locale.ROOT, LINK_TEMPLATE, key );
			if ( !lowerCaseBody.contains( link.toLowerCase( Locale.ROOT ) ) ) {
				links.add( link );
			}
		}

		if ( links.isEmpty() ) {
			return null;
		}

		return String.format( Locale.ROOT, "%s\n%s%s\n%s", START_MARKER, EDITOR_WARNING, String.join( "\n", links ), END_MARKER );
	}

	private static String removeLinksSection(String originalBody, int startIndex, int endIndex) {
		if ( startIndex > -1 && endIndex > -1 ) {
			final StringBuilder sb = new StringBuilder();
			// Remove the whole section, it will be re-appended at the end of the body
			sb.append( originalBody.substring( 0, startIndex ).trim() );
			final String following = originalBody.substring( endIndex + END_MARKER.length() ).trim();
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

	private boolean shouldCheck(GHRepository repository, GHPullRequest pullRequest) {
		return !GHIssueState.CLOSED.equals( pullRequest.getState() )
				&& repository.getId() == pullRequest.getBase().getRepository().getId();
	}
}

package org.hibernate.infra.bot;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.hibernate.infra.bot.config.DeploymentConfig;
import org.hibernate.infra.bot.config.RepositoryConfig;

import org.jboss.logging.Logger;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.PullRequest;
import jakarta.inject.Inject;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;

public class EditPullRequestAddBranchTagLabel {
	private static final Logger LOG = Logger.getLogger( EditPullRequestAddBranchTagLabel.class );

	@Inject
	DeploymentConfig deploymentConfig;

	void pullRequestChanged(
			@PullRequest.Opened @PullRequest.Reopened @PullRequest.Edited @PullRequest.Synchronize
			GHEventPayload.PullRequest payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig
	) throws IOException {
		addTagOrLabel( payload.getRepository(), repositoryConfig, payload.getPullRequest() );
	}

	private void addTagOrLabel(
			GHRepository repository,
			RepositoryConfig repositoryConfig,
			GHPullRequest pullRequest
	) throws IOException {
		if ( repositoryConfig == null || repositoryConfig.branches == null
				|| !repositoryConfig.branches.getEnabled().orElse( Boolean.FALSE ) ) {
			return;
		}

		if ( !shouldCheck( repository, pullRequest, repositoryConfig.branches.getIgnore() ) ) {
			return;
		}

		String base = pullRequest.getBase().getRef();
		if ( "main".equalsIgnoreCase( base ) ) {
			return;
		}

		if ( repositoryConfig.branches.getTitlePrefix().isPresent() ) {
			String prefix = String.format( Locale.ROOT, repositoryConfig.branches.getTitlePrefix().get(), base );
			String title = pullRequest.getTitle();
			if ( !title.startsWith( prefix ) ) {
				if ( !deploymentConfig.isDryRun() ) {
					pullRequest.setTitle( prefix + " " + title );
				}
				else {
					LOG.info( "Pull request #" + pullRequest.getNumber() + " - Setting title to: " + title );
				}
			}
		}

		if ( repositoryConfig.branches.getLabel().isPresent() ) {
			String labelName = String.format( Locale.ROOT, repositoryConfig.branches.getLabel().get(), base );
			for ( GHLabel ghLabel : pullRequest.getLabels() ) {
				if ( ghLabel.getName().equals( labelName ) ) {
					return;
				}
			}

			GHLabel label;
			try {
				label = repository.getLabel( base );
			}
			catch (GHFileNotFoundException e) {
				label = repository.createLabel(
						base,
						String.format( Locale.ROOT, "%06x", new Random().nextInt( 0xffffff + 1 ) ),
						"Label for pull requests targeting [" + base + "] branch."
				);
			}

			if ( !deploymentConfig.isDryRun() ) {
				pullRequest.addLabels( label );
			}
			else {
				LOG.info( "Pull request #" + pullRequest.getNumber() + " - Adding label: " + label.getName() );
			}
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

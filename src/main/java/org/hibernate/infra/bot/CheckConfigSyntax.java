package org.hibernate.infra.bot;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;

import jakarta.inject.Inject;

import org.hibernate.infra.bot.config.Feature;
import org.hibernate.infra.bot.config.RepositoryConfig;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.GitHubConfigFileProvider;
import io.quarkiverse.githubapp.event.CheckRun;
import io.quarkiverse.githubapp.event.CheckSuite;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.logging.Log;

public class CheckConfigSyntax {

	private static final String CHECK_RUN_NAME = "Hibernate GitHub Bot Config";
	private static final String CONFIG_FILE_NAME = "hibernate-github-bot.yml";
	private static final String CONFIG_FILE_ABSOLUTE_PATH = ".github/" + CONFIG_FILE_NAME;

	@Inject
	GitHubConfigFileProvider configFileProvider;

	void pullRequestChanged(
			@PullRequest.Opened @PullRequest.Reopened @PullRequest.Synchronize
			GHEventPayload.PullRequest payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig) throws IOException {
		if ( !Feature.CHECK_CONFIG_SYNTAX.isEnabled( repositoryConfig ) ) {
			return;
		}
		checkConfig( payload.getRepository(), payload.getPullRequest() );
	}

	void checkRunRequested(@CheckRun.Rerequested GHEventPayload.CheckRun payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig) throws IOException {
		if ( !Feature.CHECK_CONFIG_SYNTAX.isEnabled( repositoryConfig ) ) {
			return;
		}
		var checkRun = payload.getCheckRun();
		if ( !CHECK_RUN_NAME.equals( checkRun.getName() ) ) {
			return;
		}
		for ( GHPullRequest pullRequest : checkRun.getPullRequests() ) {
			checkConfig( payload.getRepository(), pullRequest );
		}
	}

	void checkSuiteRequested(@CheckSuite.Requested @CheckSuite.Rerequested GHEventPayload.CheckSuite payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig)
			throws IOException {
		if ( !Feature.CHECK_CONFIG_SYNTAX.isEnabled( repositoryConfig ) ) {
			return;
		}
		for ( GHPullRequest pullRequest : payload.getCheckSuite().getPullRequests() ) {
			checkConfig( payload.getRepository(), pullRequest );
		}
	}

	private void checkConfig(GHRepository repository, GHPullRequest pullRequest) throws IOException {
		if ( !shouldCheck( repository, pullRequest ) ) {
			return;
		}
		boolean configChanged = false;
		for ( var file : pullRequest.listFiles() ) {
			if ( CONFIG_FILE_ABSOLUTE_PATH.equals( file.getFilename() ) ) {
				configChanged = true;
				break;
			}
		}
		if ( !configChanged ) {
			return;
		}
		String sha = pullRequest.getHead().getSha();
		GHCheckRun checkRun = repository.createCheckRun( CHECK_RUN_NAME, sha )
				.withStartedAt( Date.from( Instant.now() ) )
				.withStatus( GHCheckRun.Status.IN_PROGRESS )
				.create();
		try {
			configFileProvider.fetchConfigFile( repository, sha,
					CONFIG_FILE_NAME, ConfigFile.Source.CURRENT_REPOSITORY, RepositoryConfig.class );
			repository.updateCheckRun( checkRun.getId() )
					.withCompletedAt( Date.from( Instant.now() ) )
					.withStatus( GHCheckRun.Status.COMPLETED )
					.withConclusion( GHCheckRun.Conclusion.SUCCESS )
					.add( new GHCheckRunBuilder.Output(
							CONFIG_FILE_ABSOLUTE_PATH + " passed syntax checks", "" ) )
					.create();
		}
		catch (Exception e) {
			Log.errorf( e, "Config file syntax check failed for %s", CONFIG_FILE_ABSOLUTE_PATH );
			repository.updateCheckRun( checkRun.getId() )
					.withCompletedAt( Date.from( Instant.now() ) )
					.withStatus( GHCheckRun.Status.COMPLETED )
					.withConclusion( GHCheckRun.Conclusion.FAILURE )
					.add( new GHCheckRunBuilder.Output(
							CONFIG_FILE_ABSOLUTE_PATH + " failed syntax checks",
							"```\n" + ExceptionUtils.getStackTrace( e ) + "\n```" ) )
					.create();
		}
	}

	private boolean shouldCheck(GHRepository repository, GHPullRequest pullRequest) {
		return !GHIssueState.CLOSED.equals( pullRequest.getState() )
				&& repository.getId() == pullRequest.getBase().getRepository().getId();
	}
}

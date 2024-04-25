package org.hibernate.infra.bot;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.hibernate.infra.bot.config.DeploymentConfig;
import org.hibernate.infra.bot.config.RepositoryConfig;
import org.hibernate.infra.bot.develocity.DevelocityCIBuildScan;
import org.hibernate.infra.bot.develocity.DevelocityReportFormatter;

import com.gradle.develocity.api.BuildsApi;
import com.gradle.develocity.model.Build;
import com.gradle.develocity.model.BuildAttributesValue;
import com.gradle.develocity.model.BuildModelName;
import com.gradle.develocity.model.BuildsQuery;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.CheckRun;
import io.quarkus.logging.Log;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;

public class ExtractDevelocityBuildScans {
	private static final String DEVELOCITY_CHECK_RUN_NAME = "Develocity Build Scans";

	@Inject
	DeploymentConfig deploymentConfig;

	@RestClient
	BuildsApi develocityBuildsApi;

	@Inject
	DevelocityReportFormatter reportFormatter;

	void checkRunRerequested(@CheckRun.Rerequested GHEventPayload.CheckRun payload) {
		var repository = payload.getRepository();
		var checkRun = payload.getCheckRun();
		if ( !DEVELOCITY_CHECK_RUN_NAME.equals( checkRun.getName() ) ) {
			return;
		}
		String sha = checkRun.getHeadSha();
		extractCIBuildScans( repository, sha );
	}

	void checkRunCompleted(@CheckRun.Completed GHEventPayload.CheckRun payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig) {
		if ( repositoryConfig == null
				|| repositoryConfig.develocity == null
				|| repositoryConfig.develocity.buildScan == null
				|| !repositoryConfig.develocity.buildScan.addCheck ) {
			return;
		}
		var repository = payload.getRepository();
		var checkRun = payload.getCheckRun();
		if ( checkRun.getApp().getId() != deploymentConfig.jenkins().githubAppId()
				&& !"github-actions".equals( checkRun.getApp().getSlug() ) ) {
			return;
		}
		String sha = checkRun.getHeadSha();
		extractCIBuildScans( repository, sha );
	}

	private void extractCIBuildScans(GHRepository repository, String sha) {
		try {
			long checkId = createDevelocityCheck( repository, sha );
			Throwable failure = null;
			List<DevelocityCIBuildScan> buildScans = new ArrayList<>();
			try {
				for ( Build build : develocityBuildsApi.getBuilds( new BuildsQuery.BuildsQueryQueryParam()
						.fromInstant( 0L )
						.query( "tag:CI value:\"Git commit id=%s\"".formatted( sha ) )
						.models( List.of( BuildModelName.GRADLE_MINUS_ATTRIBUTES,
								BuildModelName.MAVEN_MINUS_ATTRIBUTES ) ) ) ) {
					try {
						buildScans.add( toCIBuildScan( build ) );
					}
					catch (RuntimeException e) {
						if ( failure == null ) {
							failure = e;
						}
						else {
							failure.addSuppressed( e );
						}
					}
				}
				buildScans.sort( DevelocityCIBuildScan.COMPARATOR );
			}
			catch (RuntimeException e) {
				if ( failure == null ) {
					failure = e;
				}
				else {
					failure.addSuppressed( e );
				}
			}
			if ( failure != null ) {
				Log.errorf( failure, "Failed to extract all build scans from commit %s" + sha );
			}
			updateDevelocityCheck( repository, checkId, buildScans, failure );
		}
		catch (IOException | RuntimeException e) {
			Log.errorf( e, "Failed to report build scans from commit %s" + sha );
		}
	}

	private DevelocityCIBuildScan toCIBuildScan(Build build) {
		URI buildScanURI = deploymentConfig.develocity().uri().resolve( "/s/" + build.getId() );
		List<BuildAttributesValue> customValues;
		List<String> tags;
		List<String> goals;
		boolean hasFailed;
		Boolean hasVerificationFailure;
		var maven = build.getModels().getMavenAttributes();
		var gradle = build.getModels().getGradleAttributes();
		if ( maven != null && maven.getModel() != null ) {
			var model = maven.getModel();
			tags = model.getTags();
			customValues = model.getValues();
			goals = model.getRequestedGoals();
			hasFailed = model.getHasFailed();
			hasVerificationFailure = model.getHasVerificationFailure();
		}
		else if ( gradle != null && gradle.getModel() != null ) {
			var model = gradle.getModel();
			tags = model.getTags();
			customValues = model.getValues();
			goals = model.getRequestedTasks();
			hasFailed = model.getHasFailed();
			hasVerificationFailure = model.getHasVerificationFailure();
		}
		else {
			throw new IllegalStateException( "No Maven or Gradle model in build scan " + buildScanURI );
		}
		String provider = "";
		String jobOrWorkflow = "";
		String stage = "";
		for ( BuildAttributesValue customValue : customValues ) {
			if ( customValue.getName().equals( "CI provider" ) ) {
				provider = customValue.getValue();
			}
			else if ( customValue.getName().equals( "CI job" )
					|| customValue.getName().equals( "CI workflow" ) ) {
				jobOrWorkflow = customValue.getValue();
			}
			else if ( customValue.getName().equals( "CI stage" ) ) {
				stage = customValue.getValue();
			}
		}
		tags = tags.stream()
				.filter( Predicate.not( Predicate.isEqual( "CI" ) ) )
				.sorted()
				.toList();
		return new DevelocityCIBuildScan(
				provider,
				jobOrWorkflow,
				stage,
				build.getAvailableAt(),
				tags,
				goals,
				hasFailed ? DevelocityCIBuildScan.Status.FAILURE : DevelocityCIBuildScan.Status.SUCCESS,
				hasVerificationFailure != null && !hasVerificationFailure
						? DevelocityCIBuildScan.Status.FAILURE
						: DevelocityCIBuildScan.Status.SUCCESS,
				buildScanURI,
				deploymentConfig.develocity().uri().resolve( "/s/" + build.getId() + "/failure" ),
				deploymentConfig.develocity().uri().resolve( "/s/" + build.getId() + "/tests" ),
				deploymentConfig.develocity().uri().resolve( "/s/" + build.getId() + "/console-log" )
		);
	}

	private long createDevelocityCheck(GHRepository repository, String sha) throws IOException {
		return repository.createCheckRun( DEVELOCITY_CHECK_RUN_NAME, sha )
				.withStatus( GHCheckRun.Status.IN_PROGRESS )
				.create()
				.getId();
	}

	private void updateDevelocityCheck(GHRepository repository, long checkId,
			List<DevelocityCIBuildScan> buildScans, Throwable failure)
			throws IOException {
		String formattedBuildScanList = "";
		try {
			formattedBuildScanList = reportFormatter.summary( buildScans );
		}
		catch (RuntimeException e) {
			if ( failure == null ) {
				failure = e;
			}
			else {
				failure.addSuppressed( e );
			}
		}

		GHCheckRun.Conclusion conclusion;
		String title;
		String text;
		if ( failure == null ) {
			conclusion = GHCheckRun.Conclusion.NEUTRAL;
			title = "Found %s build scan%s".formatted( buildScans.size(), buildScans.size() != 1 ? "s" : "" );
			text = formattedBuildScanList;
		}
		else {
			conclusion = GHCheckRun.Conclusion.FAILURE;
			title = "Develocity Build Scans extraction failed with exception";
			text = formattedBuildScanList + "\n\n```\n" + ExceptionUtils.getStackTrace( failure ) + "\n```";
		}

		if ( deploymentConfig.isDryRun() ) {
			Log.infof( "SHA %s - Update check run '%s' with conclusion '%s' and text:\n%s",
					checkId, DEVELOCITY_CHECK_RUN_NAME,
					failure == null ? GHCheckRun.Conclusion.NEUTRAL : GHCheckRun.Conclusion.FAILURE );
			return;
		}
		repository.updateCheckRun( checkId )
				.withStatus( GHCheckRun.Status.COMPLETED )
				.withConclusion( conclusion )
				.add( new GHCheckRunBuilder.Output(
						title,
						text
				) )
				.create();
	}

}

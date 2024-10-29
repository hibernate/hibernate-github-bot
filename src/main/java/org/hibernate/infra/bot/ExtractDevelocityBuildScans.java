package org.hibernate.infra.bot;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.hibernate.infra.bot.config.DeploymentConfig;
import org.hibernate.infra.bot.config.RepositoryConfig;
import org.hibernate.infra.bot.develocity.DevelocityCIBuildScan;
import org.hibernate.infra.bot.develocity.DevelocityReportFormatter;
import org.hibernate.infra.bot.util.GitHubActionsRunId;
import org.hibernate.infra.bot.util.JenkinsRunId;

import com.gradle.develocity.api.BuildsApi;
import com.gradle.develocity.model.Build;
import com.gradle.develocity.model.BuildAttributesEnvironment;
import com.gradle.develocity.model.BuildAttributesLink;
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

	void checkRunRerequested(@CheckRun.Rerequested GHEventPayload.CheckRun payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig) {
		var repository = payload.getRepository();
		var checkRun = payload.getCheckRun();
		if ( !DEVELOCITY_CHECK_RUN_NAME.equals( checkRun.getName() ) ) {
			return;
		}
		String sha = checkRun.getHeadSha();
		extractCIBuildScans( repository, repositoryConfig.develocity.buildScan, sha );
	}

	void checkRunCompleted(@CheckRun.Completed GHEventPayload.CheckRun payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig) {
		if ( repositoryConfig == null
				|| repositoryConfig.develocity == null
				|| repositoryConfig.develocity.buildScan == null ) {
			return;
		}
		var buildScanConfig = repositoryConfig.develocity.buildScan;
		if ( !buildScanConfig.addCheck ) {
			return;
		}
		var repository = payload.getRepository();
		var checkRun = payload.getCheckRun();
		if ( DEVELOCITY_CHECK_RUN_NAME.equals( checkRun.getName() ) ) {
			// Don't react to our own checks.
			return;
		}
		var sha = payload.getCheckRun().getHeadSha();
		extractCIBuildScans( repository, buildScanConfig, sha );
	}

	private void extractCIBuildScans(GHRepository repository, RepositoryConfig.Develocity.BuildScan config,
			String sha) {
		try {
			var checkRuns = repository.getCheckRuns( sha ).toList();
			if ( checkRuns.stream().noneMatch( this::isJobOrWorkflow ) ) {
				return;
			}
			long checkId = createDevelocityCheck( repository, sha );
			Throwable failure = null;
			String query = "";
			List<DevelocityCIBuildScan> buildScans = new ArrayList<>();
			try {
				query = createBuildScansQuery( checkRuns );
				for ( Build build : develocityBuildsApi.getBuilds( new BuildsQuery.BuildsQueryQueryParam()
						.fromInstant( 0L )
						.query( query )
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
			updateDevelocityCheck( repository, config, checkId, query, buildScans, failure );
		}
		catch (IOException | RuntimeException e) {
			Log.errorf( e, "Failed to report build scans from commit %s" + sha );
		}
	}

	private String createBuildScansQuery(List<GHCheckRun> checkRuns) throws IOException {
		Set<String> queries = new HashSet<>();
		for ( GHCheckRun checkRun : checkRuns ) {
			// This may not catch build scans based on merge commits created within CI runs,
			// but will catch build scans that have no corresponding GH check run,
			// and will allow the query link we put in reports to catch (some) *future* GH check runs.
			var sha = checkRun.getHeadSha();
			if ( sha != null && !sha.isBlank() ) {
				queries.add( "tag:CI and value:\"Git commit id=%s\"".formatted( sha ) );
			}

			// This will catch build scans based on merge commits created within CI runs,
			// but will not catch build scans that have no corresponding GH check run,
			// and will not allow the query link we put in reports to catch *future* GH check runs.
			if ( isJenkinsBuild( checkRun ) ) {
				var runId = JenkinsRunId.parse( checkRun.getExternalId() );
				queries.add( "value:\"CI job=%s\" and value:\"CI build number=%s\""
						.formatted( runId.job(), runId.run() ) );
			}
			else if ( isGitHubActionsWorkflow( checkRun ) ) {
				var runId = GitHubActionsRunId.parse( checkRun.getDetailsUrl() );
				queries.add( "value:\"CI run=%s\""
						.formatted( runId.run() ) );
			}
			// else: unsupported check, ignore
		}
		return queries.stream().collect( Collectors.joining( ") or (", "(", ")" ) );
	}

	private boolean isJobOrWorkflow(GHCheckRun checkRun) {
		return isGitHubActionsWorkflow( checkRun ) || isJenkinsBuild( checkRun );
	}

	private boolean isGitHubActionsWorkflow(GHCheckRun checkRun) {
		return "github-actions".equals( checkRun.getApp().getSlug() );
	}

	private boolean isJenkinsBuild(GHCheckRun checkRun) {
		return checkRun.getApp().getId() == deploymentConfig.jenkins().githubAppId()
				&& checkRun.getExternalId() != null && !checkRun.getExternalId().isBlank();
	}

	private DevelocityCIBuildScan toCIBuildScan(Build build) {
		URI buildScanURI = deploymentConfig.develocity().uri().resolve( "/s/" + build.getId() );
		BuildAttributesEnvironment environment;
		List<BuildAttributesValue> customValues;
		List<BuildAttributesLink> links;
		List<String> tags;
		List<String> goals;
		boolean hasFailed;
		Boolean hasVerificationFailure;
		var maven = build.getModels().getMavenAttributes();
		var gradle = build.getModels().getGradleAttributes();
		if ( maven != null && maven.getModel() != null ) {
			var model = maven.getModel();
			environment = model.getEnvironment();
			tags = model.getTags();
			customValues = model.getValues();
			links = model.getLinks();
			goals = model.getRequestedGoals();
			hasFailed = model.getHasFailed();
			hasVerificationFailure = model.getHasVerificationFailure();
		}
		else if ( gradle != null && gradle.getModel() != null ) {
			var model = gradle.getModel();
			environment = model.getEnvironment();
			tags = model.getTags();
			customValues = model.getValues();
			links = model.getLinks();
			goals = model.getRequestedTasks();
			hasFailed = model.getHasFailed();
			hasVerificationFailure = model.getHasVerificationFailure();
		}
		else {
			throw new IllegalStateException( "No Maven or Gradle model in build scan " + buildScanURI );
		}
		String provider = "";
		String hostname = environment.getPublicHostname();
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
		URI jobOrWorkflowUri = null;
		for ( BuildAttributesLink link : links ) {
			if ( link.getLabel().equals( "GitHub Actions build" )
					|| link.getLabel().equals( "Jenkins build" ) ) {
				jobOrWorkflowUri = URI.create( link.getUrl() );
			}
		}

		tags = tags.stream()
				.filter( Predicate.not( Predicate.isEqual( "CI" ) ) )
				.sorted()
				.toList();
		return new DevelocityCIBuildScan(
				provider, hostname,
				jobOrWorkflow,
				jobOrWorkflowUri,
				stage,
				build.getAvailableAt(),
				tags,
				goals,
				hasFailed ? DevelocityCIBuildScan.Status.FAILURE : DevelocityCIBuildScan.Status.SUCCESS,
				hasVerificationFailure != null && hasVerificationFailure
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

	private void updateDevelocityCheck(GHRepository repository, RepositoryConfig.Develocity.BuildScan config,
			long checkId, String query, List<DevelocityCIBuildScan> buildScans, Throwable failure)
			throws IOException {
		String formattedBuildScanList = "";
		String footer = "";
		try {
			formattedBuildScanList = reportFormatter.summary( buildScans, config );
			footer = reportFormatter.footer( query, false );
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
			text = formattedBuildScanList + footer;
		}
		else {
			conclusion = GHCheckRun.Conclusion.FAILURE;
			title = "Develocity Build Scans extraction failed with exception";
			try {
				footer = reportFormatter.footer( query, true );
			}
			catch (RuntimeException e) {
				failure.addSuppressed( e );
			}
			text = formattedBuildScanList + "\n\n```\n" + ExceptionUtils.getStackTrace( failure ) + "\n```" + footer;
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

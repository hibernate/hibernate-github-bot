package org.hibernate.infra.bot;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.hibernate.infra.bot.config.DeploymentConfig;
import org.hibernate.infra.bot.config.Feature;
import org.hibernate.infra.bot.config.RepositoryConfig;
import org.hibernate.infra.bot.develocity.CIJobInfo;
import org.hibernate.infra.bot.develocity.DevelocityCIBuildScan;
import org.hibernate.infra.bot.develocity.DevelocityFailingTest;
import org.hibernate.infra.bot.develocity.DevelocityReportFormatter;
import org.hibernate.infra.bot.util.GitHubActionsRunId;
import org.hibernate.infra.bot.util.JenkinsRunId;

import com.gradle.develocity.api.BuildsApi;
import com.gradle.develocity.api.TestsApi;
import com.gradle.develocity.model.Build;
import com.gradle.develocity.model.BuildAttributesEnvironment;
import com.gradle.develocity.model.BuildAttributesLink;
import com.gradle.develocity.model.BuildAttributesValue;
import com.gradle.develocity.model.BuildModelName;
import com.gradle.develocity.model.BuildTestOrContainer;
import com.gradle.develocity.model.BuildTestOrContainerOutcome;
import com.gradle.develocity.model.BuildTestWorkUnit;
import com.gradle.develocity.model.BuildTestsQuery;
import com.gradle.develocity.model.BuildTestsResponse;
import com.gradle.develocity.model.BuildsQuery;
import com.gradle.develocity.model.TestContainersQuery;
import com.gradle.develocity.model.TestIncludeFields;
import com.gradle.develocity.model.TestOrContainer;
import com.gradle.develocity.model.TestOutcome;
import com.gradle.develocity.model.TestOutcomeDistribution;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.CheckRun;
import io.quarkiverse.githubapp.event.CheckSuite;
import io.quarkiverse.githubapp.event.WorkflowRun;
import io.quarkus.logging.Log;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;

public class ExtractDevelocityBuildScans {
	private static final String DEVELOCITY_CHECK_RUN_NAME = "📋 Build reports";
	private static final String LEGACY_CHECK_RUN_NAME = "Develocity Build Scans";
	private static final int GITHUB_CHECK_RUN_TEXT_LIMIT = 65535;

	@Inject
	DeploymentConfig deploymentConfig;

	@RestClient
	BuildsApi develocityBuildsApi;

	@RestClient
	TestsApi develocityTestsApi;

	@Inject
	DevelocityReportFormatter reportFormatter;

	private static final Pattern PR_TAG_PATTERN = Pattern.compile( "PR-\\d+" );

	private static final long DEBOUNCE_MILLIS = 10_000;
	private static final ConcurrentHashMap<String, Long> recentlyProcessedStartEvents = new ConcurrentHashMap<>();

	void checkRunRerequested(@CheckRun.Rerequested GHEventPayload.CheckRun payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig) {
		if ( !Feature.EXTRACT_DEVELOCITY_BUILD_SCANS.isEnabled( repositoryConfig ) ) {
			return;
		}
		var repository = payload.getRepository();
		var checkRun = payload.getCheckRun();
		if ( !isOwnCheckRun( checkRun.getName() ) ) {
			return;
		}
		String sha = checkRun.getHeadSha();
		extractCIBuildScans( repository, repositoryConfig.develocity.buildScan, sha );
	}

	void checkSuiteRerequested(@CheckSuite.Rerequested GHEventPayload.CheckSuite payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig) {
		if ( !Feature.EXTRACT_DEVELOCITY_BUILD_SCANS.isEnabled( repositoryConfig ) ) {
			return;
		}
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
		var sha = payload.getCheckSuite().getHeadSha();
		extractCIBuildScans( repository, buildScanConfig, sha );
	}

	void workflowRunCompleted(@WorkflowRun.Completed GHEventPayload.WorkflowRun payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig) {
		if ( !Feature.EXTRACT_DEVELOCITY_BUILD_SCANS.isEnabled( repositoryConfig ) ) {
			return;
		}
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
		var sha = payload.getWorkflowRun().getHeadSha();
		extractCIBuildScans( repository, buildScanConfig, sha );
	}

	void checkRunCompleted(@CheckRun.Completed GHEventPayload.CheckRun payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig) {
		if ( !Feature.EXTRACT_DEVELOCITY_BUILD_SCANS.isEnabled( repositoryConfig ) ) {
			return;
		}
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
		if ( isOwnCheckRun( checkRun.getName() ) ) {
			// Don't react to our own checks.
			return;
		}
		var sha = payload.getCheckRun().getHeadSha();
		extractCIBuildScans( repository, buildScanConfig, sha );
	}

	void checkRunCreated(@CheckRun.Created GHEventPayload.CheckRun payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig) {
		if ( !Feature.EXTRACT_DEVELOCITY_BUILD_SCANS.isEnabled( repositoryConfig ) ) {
			return;
		}
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
		if ( isOwnCheckRun( checkRun.getName() ) ) {
			return;
		}
		var sha = checkRun.getHeadSha();
		if ( shouldDebounceStartEvent( sha ) ) {
			return;
		}
		extractCIBuildScans( repository, buildScanConfig, sha );
	}

	private boolean shouldDebounceStartEvent(String sha) {
		long now = System.currentTimeMillis();
		AtomicBoolean debounced = new AtomicBoolean( false );
		recentlyProcessedStartEvents.compute( sha, (key, lastProcessed) -> {
			if ( lastProcessed != null && now - lastProcessed < DEBOUNCE_MILLIS ) {
				debounced.set( true );
				return lastProcessed;
			}
			return now;
		} );
		if ( recentlyProcessedStartEvents.size() > 1000 ) {
			recentlyProcessedStartEvents.entrySet().removeIf( e -> now - e.getValue() > 60_000 );
		}
		return debounced.get();
	}

	private void extractCIBuildScans(GHRepository repository, RepositoryConfig.Develocity.BuildScan config,
			String sha) {
		try {
			var checkRuns = repository.getCheckRuns( sha ).toList();
			if ( checkRuns.stream().noneMatch( this::isJobOrWorkflow ) ) {
				return;
			}
			supersedeLegacyCheckRuns( repository, checkRuns );
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
				try {
					buildScans = enrichGHABuildScansWithJobNames( buildScans, checkRuns );
				}
				catch (RuntimeException e) {
					Log.warnf( e, "Failed to enrich GHA build scans with job names for commit %s", sha );
				}
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

			List<DevelocityFailingTest> failingTests = List.of();
			if ( config.isTestSummaryEnabled() ) {
				try {
					failingTests = extractFailingTests( buildScans, config );
				}
				catch (RuntimeException e) {
					if ( failure == null ) {
						failure = e;
					}
					else {
						failure.addSuppressed( e );
					}
					Log.errorf( e, "Failed to extract failing tests from commit %s", sha );
				}
			}

			updateDevelocityCheck( repository, config, checkId, query, buildScans, failingTests, checkRuns,
					failure );
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

	private static boolean isOwnCheckRun(String name) {
		return DEVELOCITY_CHECK_RUN_NAME.equals( name ) || LEGACY_CHECK_RUN_NAME.equals( name );
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

	private List<DevelocityCIBuildScan> enrichGHABuildScansWithJobNames(List<DevelocityCIBuildScan> buildScans,
			List<GHCheckRun> checkRuns) {
		// Group GHA check runs by workflow run ID, indexed by check run ID for direct lookup
		Map<Long, GHCheckRun> ghaCheckRunById = new HashMap<>();
		Map<Long, List<GHCheckRun>> ghaCheckRunsByRunId = new HashMap<>();
		for ( GHCheckRun cr : checkRuns ) {
			if ( !isGitHubActionsWorkflow( cr ) || isOwnCheckRun( cr.getName() ) ) {
				continue;
			}
			try {
				var parsed = GitHubActionsRunId.parse( cr.getDetailsUrl() );
				if ( parsed.job().isPresent() ) {
					ghaCheckRunById.put( parsed.job().getAsLong(), cr );
				}
				ghaCheckRunsByRunId.computeIfAbsent( parsed.run(), k -> new ArrayList<>() ).add( cr );
			}
			catch (RuntimeException e) {
				// Not a valid GHA URL, skip
			}
		}

		if ( ghaCheckRunById.isEmpty() && ghaCheckRunsByRunId.isEmpty() ) {
			return buildScans;
		}

		List<DevelocityCIBuildScan> result = new ArrayList<>( buildScans.size() );
		for ( DevelocityCIBuildScan scan : buildScans ) {
			result.add( tryEnrichWithJobName( scan, ghaCheckRunById, ghaCheckRunsByRunId ) );
		}
		return result;
	}

	private DevelocityCIBuildScan tryEnrichWithJobName(DevelocityCIBuildScan scan,
			Map<Long, GHCheckRun> ghaCheckRunById,
			Map<Long, List<GHCheckRun>> ghaCheckRunsByRunId) {
		if ( scan.jobOrWorkflowUri() == null ) {
			return scan;
		}
		GitHubActionsRunId parsed;
		try {
			parsed = GitHubActionsRunId.parse( scan.jobOrWorkflowUri().toURL() );
		}
		catch (Exception e) {
			return scan;
		}

		// Strategy 1: direct match by job ID in the build scan URL
		if ( parsed.job().isPresent() ) {
			GHCheckRun matched = ghaCheckRunById.get( parsed.job().getAsLong() );
			if ( matched != null ) {
				return applyCheckRunInfo( scan, matched );
			}
		}

		// Strategy 2: match by tag — find a build scan tag that appears in exactly one check run name
		List<GHCheckRun> candidates = ghaCheckRunsByRunId.getOrDefault( parsed.run(), List.of() );
		if ( candidates.size() <= 1 ) {
			if ( candidates.size() == 1 ) {
				return applyCheckRunInfo( scan, candidates.get( 0 ) );
			}
			return scan;
		}
		GHCheckRun matched = matchByTags( scan, candidates );
		if ( matched != null ) {
			return applyCheckRunInfo( scan, matched );
		}

		return scan;
	}

	private static GHCheckRun matchByTags(DevelocityCIBuildScan scan, List<GHCheckRun> candidates) {
		GHCheckRun uniqueMatch = null;
		for ( String tag : scan.tags() ) {
			if ( tag.length() < 2 ) {
				continue;
			}
			GHCheckRun tagMatch = null;
			int matchCount = 0;
			Pattern tagPattern = Pattern.compile( "\\b" + Pattern.quote( tag ) + "\\b",
					Pattern.CASE_INSENSITIVE );
			for ( GHCheckRun cr : candidates ) {
				if ( tagPattern.matcher( cr.getName() ).find() ) {
					tagMatch = cr;
					matchCount++;
				}
			}
			if ( matchCount == 1 ) {
				if ( uniqueMatch == null || uniqueMatch == tagMatch ) {
					uniqueMatch = tagMatch;
				}
				else {
					// Different tags point to different check runs — ambiguous
					return null;
				}
			}
		}
		return uniqueMatch;
	}

	private static DevelocityCIBuildScan applyCheckRunInfo(DevelocityCIBuildScan scan, GHCheckRun checkRun) {
		try {
			return scan.withJobInfo( checkRun.getName(), checkRun.getDetailsUrl().toURI() );
		}
		catch (Exception e) {
			return scan;
		}
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

	private List<DevelocityFailingTest> extractFailingTests(List<DevelocityCIBuildScan> buildScans,
			RepositoryConfig.Develocity.BuildScan config) {
		List<DevelocityCIBuildScan> failingScans = buildScans.stream()
				.filter( s -> s.testStatus() == DevelocityCIBuildScan.Status.FAILURE )
				.toList();
		if ( failingScans.isEmpty() ) {
			return List.of();
		}

		Set<String> currentScanIds = buildScans.stream()
				.map( ExtractDevelocityBuildScans::extractScanId )
				.collect( Collectors.toSet() );

		// Step 1: Get failing test containers per build scan via the per-build tests endpoint.
		Map<String, List<DevelocityCIBuildScan>> testToFailingScans = new LinkedHashMap<>();
		for ( DevelocityCIBuildScan scan : failingScans ) {
			String scanId = extractScanId( scan );
			try {
				BuildTestsResponse response = develocityTestsApi.getBuildTests( scanId,
						new BuildTestsQuery.BuildTestsQueryQueryParam()
								.testOutcomes( List.of( TestOutcome.FAILED, TestOutcome.FLAKY ) ) );
				if ( response != null && response.getWorkUnits() != null ) {
					for ( BuildTestWorkUnit workUnit : response.getWorkUnits() ) {
						collectFailingContainerNames( workUnit.getTests(), scan, testToFailingScans );
					}
				}
			}
			catch (RuntimeException e) {
				Log.warnf( e, "Failed to fetch failing tests for build scan %s", scanId );
			}
		}

		if ( testToFailingScans.isEmpty() ) {
			return List.of();
		}

		// Step 2: Get cross-build history for each failing test (only if historyQuery is configured)
		boolean historyEnabled = config.isHistoryEnabled();
		String prTag = historyEnabled ? findPrTag( buildScans ) : null;

		List<DevelocityFailingTest> failingTests = new ArrayList<>();
		int historyLookups = 0;
		for ( var entry : testToFailingScans.entrySet() ) {
			String testName = entry.getKey();
			List<DevelocityCIBuildScan> scans = entry.getValue();
			scans.sort( DevelocityCIBuildScan.COMPARATOR );

			TestOutcomeDistribution historyDistribution = null;
			boolean historyChecked = false;
			boolean failsOutsideThisRun = false;

			if ( historyEnabled && historyLookups < config.testSummary.maxTestHistoryLookups ) {
				try {
					String historyQuery = config.testSummary.historyQuery
							+ " and buildStartTime>=-" + config.testSummary.historyDays + "d";
					if ( prTag != null ) {
						historyQuery += " and -tag:" + prTag;
					}
					var historyResponse = develocityTestsApi.getTestContainers(
							new TestContainersQuery.TestContainersQueryQueryParam()
									.container( testName )
									.testOutcomes( List.of( TestOutcome.FAILED, TestOutcome.FLAKY ) )
									.query( historyQuery )
									.include( List.of( TestIncludeFields.BUILD_SCAN_IDS ) ) );

					historyChecked = true;
					if ( historyResponse != null && historyResponse.getContent() != null ) {
						for ( TestOrContainer historyContainer : historyResponse.getContent() ) {
							if ( testName.equals( historyContainer.getName() ) ) {
								historyDistribution = historyContainer.getOutcomeDistribution();
								if ( historyContainer.getBuildScanIdsByOutcome() != null ) {
									Set<String> allHistoricalIds = new HashSet<>();
									if ( historyContainer.getBuildScanIdsByOutcome().getFailed() != null ) {
										allHistoricalIds.addAll(
												historyContainer.getBuildScanIdsByOutcome().getFailed() );
									}
									if ( historyContainer.getBuildScanIdsByOutcome().getFlaky() != null ) {
										allHistoricalIds.addAll(
												historyContainer.getBuildScanIdsByOutcome().getFlaky() );
									}
									failsOutsideThisRun = allHistoricalIds.stream()
											.anyMatch( id -> !currentScanIds.contains( id ) );
								}
								break;
							}
						}
					}
					historyLookups++;
				}
				catch (RuntimeException e) {
					Log.warnf( e, "Failed to fetch test history for %s", testName );
					historyLookups++;
				}
			}

			failingTests.add( new DevelocityFailingTest( testName, scans,
					historyChecked, historyDistribution, failsOutsideThisRun ) );
		}
		return failingTests;
	}

	private void collectFailingContainerNames(List<BuildTestOrContainer> containers,
			DevelocityCIBuildScan scan, Map<String, List<DevelocityCIBuildScan>> result) {
		if ( containers == null ) {
			return;
		}
		for ( BuildTestOrContainer container : containers ) {
			BuildTestOrContainerOutcome outcome = container.getOutcome();
			if ( outcome != null && outcome.getOverall() != null
					&& (outcome.getOverall() == TestOutcome.FAILED
							|| outcome.getOverall() == TestOutcome.FLAKY) ) {
				if ( hasGrandchildren( container ) ) {
					collectFailingContainerNames( container.getChildren(), scan, result );
				}
				else {
					result.computeIfAbsent( container.getName(), k -> new ArrayList<>() ).add( scan );
				}
			}
		}
	}

	private boolean hasGrandchildren(BuildTestOrContainer container) {
		if ( container.getChildren() == null || container.getChildren().isEmpty() ) {
			return false;
		}
		return container.getChildren().stream()
				.anyMatch( child -> child.getChildren() != null && !child.getChildren().isEmpty() );
	}

	private static String extractScanId(DevelocityCIBuildScan scan) {
		var path = scan.buildScanUri().getPath();
		return path.substring( path.lastIndexOf( '/' ) + 1 );
	}

	private String findPrTag(List<DevelocityCIBuildScan> buildScans) {
		for ( DevelocityCIBuildScan scan : buildScans ) {
			for ( String tag : scan.tags() ) {
				if ( PR_TAG_PATTERN.matcher( tag ).matches() ) {
					return tag;
				}
			}
		}
		return null;
	}

	private void supersedeLegacyCheckRuns(GHRepository repository, List<GHCheckRun> checkRuns) {
		if ( deploymentConfig.isDryRun() ) {
			return;
		}
		for ( GHCheckRun checkRun : checkRuns ) {
			if ( LEGACY_CHECK_RUN_NAME.equals( checkRun.getName() ) ) {
				try {
					repository.updateCheckRun( checkRun.getId() )
							.withStatus( GHCheckRun.Status.COMPLETED )
							.withConclusion( GHCheckRun.Conclusion.NEUTRAL )
							.add( new GHCheckRunBuilder.Output(
									"Superseded by " + DEVELOCITY_CHECK_RUN_NAME,
									"This check has been renamed. See the **" + DEVELOCITY_CHECK_RUN_NAME
											+ "** check for build reports."
							) )
							.create();
				}
				catch (IOException | RuntimeException e) {
					Log.warnf( e, "Failed to supersede legacy check run %s", checkRun.getId() );
				}
			}
		}
	}

	private long createDevelocityCheck(GHRepository repository, String sha) throws IOException {
		return repository.createCheckRun( DEVELOCITY_CHECK_RUN_NAME, sha )
				.withStatus( GHCheckRun.Status.IN_PROGRESS )
				.create()
				.getId();
	}

	private void updateDevelocityCheck(GHRepository repository, RepositoryConfig.Develocity.BuildScan config,
			long checkId, String query, List<DevelocityCIBuildScan> buildScans,
			List<DevelocityFailingTest> failingTests, List<GHCheckRun> allCheckRuns, Throwable failure)
			throws IOException {
		List<CIJobInfo> runningChecks = new ArrayList<>();
		List<CIJobInfo> failedChecksWithoutScan = new ArrayList<>();
		List<CIJobInfo> checksWithoutScan = new ArrayList<>();
		try {
			Set<Long> coveredGHARunIds = extractCoveredGHARunIds( buildScans );
			Set<String> coveredJenkinsJobs = extractCoveredJenkinsJobs( buildScans );
			for ( GHCheckRun checkRun : allCheckRuns ) {
				if ( !isJobOrWorkflow( checkRun ) ) {
					continue;
				}
				if ( checkRun.getStatus() != GHCheckRun.Status.COMPLETED ) {
					runningChecks.add( toCIJobInfo( checkRun ) );
				}
				else if ( !isCoveredByBuildScan( checkRun, coveredGHARunIds, coveredJenkinsJobs ) ) {
					if ( isFailedConclusion( checkRun.getConclusion() ) ) {
						failedChecksWithoutScan.add( toCIJobInfo( checkRun ) );
					}
					else {
						checksWithoutScan.add( toCIJobInfo( checkRun ) );
					}
				}
			}
		}
		catch (RuntimeException e) {
			Log.warnf( e, "Failed to compute CI job status for check run %s", checkId );
		}

		String formattedBuildScanList = "";
		String formattedFailingTests = "";
		String formattedCIStatus = "";
		String footer = "";
		try {
			formattedBuildScanList = reportFormatter.summary( buildScans, config );
			formattedFailingTests = reportFormatter.failingTests( failingTests, config );
			formattedCIStatus = reportFormatter.ciStatus( runningChecks, failedChecksWithoutScan,
					checksWithoutScan );
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

		long failedScanCount = buildScans.stream()
				.filter( s -> s.status() == DevelocityCIBuildScan.Status.FAILURE )
				.count();
		boolean hasFailures = failure != null || failedScanCount > 0 || !failedChecksWithoutScan.isEmpty();
		boolean hasRunning = !runningChecks.isEmpty();

		GHCheckRun.Status status;
		GHCheckRun.Conclusion conclusion;
		String title;
		String text;
		if ( failure != null ) {
			status = GHCheckRun.Status.COMPLETED;
			conclusion = GHCheckRun.Conclusion.FAILURE;
			title = "Develocity Build Scans extraction failed with exception";
			try {
				footer = reportFormatter.footer( query, true );
			}
			catch (RuntimeException e) {
				failure.addSuppressed( e );
			}
			text = formattedCIStatus + formattedFailingTests + formattedBuildScanList + "\n\n```\n"
					+ ExceptionUtils.getStackTrace( failure ) + "\n```" + footer;
		}
		else if ( hasFailures ) {
			status = GHCheckRun.Status.COMPLETED;
			conclusion = GHCheckRun.Conclusion.FAILURE;
			long totalFailed = failedScanCount + failedChecksWithoutScan.size();
			long totalKnown = buildScans.size() + failedChecksWithoutScan.size();
			if ( buildScans.isEmpty() && failedChecksWithoutScan.isEmpty() ) {
				title = "No build scan found";
			}
			else if ( hasRunning ) {
				title = "%s/%s failed — %s still running".formatted( totalFailed, totalKnown,
						runningChecks.size() );
			}
			else {
				title = "%s/%s failed".formatted( totalFailed, totalKnown );
			}
			text = formattedCIStatus + formattedFailingTests + formattedBuildScanList + footer;
		}
		else if ( hasRunning ) {
			status = GHCheckRun.Status.IN_PROGRESS;
			conclusion = null;
			if ( buildScans.isEmpty() ) {
				title = "%s still running".formatted( runningChecks.size() );
			}
			else {
				title = "%s scans collected — %s still running".formatted( buildScans.size(),
						runningChecks.size() );
			}
			text = formattedCIStatus + formattedFailingTests + formattedBuildScanList + footer;
		}
		else {
			status = GHCheckRun.Status.COMPLETED;
			if ( buildScans.isEmpty() ) {
				conclusion = GHCheckRun.Conclusion.NEUTRAL;
				title = "No build scan found";
			}
			else {
				conclusion = GHCheckRun.Conclusion.SUCCESS;
				title = "%s succeeded".formatted( buildScans.size() );
			}
			text = formattedCIStatus + formattedFailingTests + formattedBuildScanList + footer;
		}

		if ( text.length() > GITHUB_CHECK_RUN_TEXT_LIMIT ) {
			int maxFailingTestsLength = GITHUB_CHECK_RUN_TEXT_LIMIT
					- ( text.length() - formattedFailingTests.length() );
			formattedFailingTests = reportFormatter.failingTests( failingTests, config,
					Math.max( 0, maxFailingTestsLength ) );
			if ( failure != null ) {
				text = formattedCIStatus + formattedFailingTests + formattedBuildScanList + "\n\n```\n"
						+ ExceptionUtils.getStackTrace( failure ) + "\n```" + footer;
			}
			else {
				text = formattedCIStatus + formattedFailingTests + formattedBuildScanList + footer;
			}
		}

		if ( deploymentConfig.isDryRun() ) {
			Log.infof( "SHA %s - Update check run '%s' with status '%s', conclusion '%s' and text:\n%s",
					checkId, DEVELOCITY_CHECK_RUN_NAME, status, conclusion, text );
			return;
		}
		var builder = repository.updateCheckRun( checkId )
				.withStatus( status );
		if ( conclusion != null ) {
			builder.withConclusion( conclusion );
		}
		builder.add( new GHCheckRunBuilder.Output(
						title,
						text
				) )
				.create();
	}

	private static boolean isFailedConclusion(GHCheckRun.Conclusion conclusion) {
		return conclusion == GHCheckRun.Conclusion.FAILURE
				|| conclusion == GHCheckRun.Conclusion.CANCELLED
				|| conclusion == GHCheckRun.Conclusion.TIMED_OUT;
	}

	private CIJobInfo toCIJobInfo(GHCheckRun checkRun) {
		return new CIJobInfo( checkRun.getName(),
				Optional.ofNullable( checkRun.getDetailsUrl() ) );
	}

	private Set<Long> extractCoveredGHARunIds(List<DevelocityCIBuildScan> buildScans) {
		Set<Long> runIds = new HashSet<>();
		for ( var scan : buildScans ) {
			if ( scan.jobOrWorkflowUri() != null ) {
				try {
					runIds.add( GitHubActionsRunId.parse( scan.jobOrWorkflowUri().toURL() ).run() );
				}
				catch (Exception e) {
					// Not a GHA URL, ignore
				}
			}
		}
		return runIds;
	}

	private Set<String> extractCoveredJenkinsJobs(List<DevelocityCIBuildScan> buildScans) {
		return buildScans.stream()
				.filter( s -> "Jenkins".equals( s.provider() ) )
				.map( DevelocityCIBuildScan::jobOrWorkflow )
				.collect( Collectors.toSet() );
	}

	private boolean isCoveredByBuildScan(GHCheckRun checkRun, Set<Long> coveredGHARunIds,
			Set<String> coveredJenkinsJobs) {
		if ( isGitHubActionsWorkflow( checkRun ) ) {
			try {
				long runId = GitHubActionsRunId.parse( checkRun.getDetailsUrl() ).run();
				return coveredGHARunIds.contains( runId );
			}
			catch (Exception e) {
				return false;
			}
		}
		else if ( isJenkinsBuild( checkRun ) ) {
			try {
				String job = JenkinsRunId.parse( checkRun.getExternalId() ).job();
				return coveredJenkinsJobs.contains( job );
			}
			catch (Exception e) {
				return false;
			}
		}
		return false;
	}

}

package org.hibernate.infra.bot.tests;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.gradle.develocity.api.BuildsApi;
import com.gradle.develocity.api.TestsApi;
import com.gradle.develocity.model.Build;
import com.gradle.develocity.model.BuildAttributesEnvironment;
import com.gradle.develocity.model.BuildAttributesLink;
import com.gradle.develocity.model.BuildAttributesValue;
import com.gradle.develocity.model.BuildModels;
import com.gradle.develocity.model.BuildModelsMavenAttributes;
import com.gradle.develocity.model.BuildsQuery;
import com.gradle.develocity.model.MavenAttributes;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@QuarkusTest
@GitHubAppTest
@ExtendWith(MockitoExtension.class)
public class ExtractDevelocityBuildScansTest {

	private static final String HEAD_SHA = "f280d7c37ff6828f4f0c07f6635c235a243d54d9";
	private static final String WORKFLOW_HEAD_SHA = "ddbf12d7d8ff89a85c579c98c75358d8e9015cc5";
	private static final String REPO_NAME = "hibernate/hibernate-orm";
	private static final long DEVELOCITY_CHECK_RUN_ID = 999L;
	private static final String DEVELOCITY_BUILD_SCAN_CONFIG = """
			features: [ EXTRACT_DEVELOCITY_BUILD_SCANS ]
			develocity:
			  buildScan:
			    addCheck: true
			""";

	@InjectMock
	@RestClient
	BuildsApi develocityBuildsApiMock;

	@InjectMock
	@RestClient
	TestsApi develocityTestsApiMock;

	@BeforeEach
	void setUp() {
		when( develocityBuildsApiMock.getBuilds( any() ) ).thenReturn( Collections.emptyList() );
	}

	@Test
	void checkRunCompleted_githubActions() throws IOException {
		var updateBuilderRef = new java.util.concurrent.atomic.AtomicReference<GHCheckRunBuilder>();
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( DEVELOCITY_BUILD_SCAN_CONFIG );

					GHRepository repoMock = mocks.repository( REPO_NAME );

					mockGetCheckRuns( repoMock, HEAD_SHA,
							mockGitHubActionsCheckRun( HEAD_SHA ) );
					updateBuilderRef.set( mockDevelocityCheckRun( repoMock, HEAD_SHA ) );
				} )
				.when()
				.payloadFromClasspath( "/check-run-completed-github-actions.json" )
				.event( GHEvent.CHECK_RUN )
				.then()
				.github( mocks -> {
					GHRepository repoMock = mocks.repository( REPO_NAME );
					verify( repoMock ).createCheckRun( "📋 Build reports", HEAD_SHA );
					verify( repoMock ).updateCheckRun( DEVELOCITY_CHECK_RUN_ID );
					var queryCaptor = ArgumentCaptor.forClass( BuildsQuery.BuildsQueryQueryParam.class );
					verify( develocityBuildsApiMock ).getBuilds( queryCaptor.capture() );
					String query = queryCaptor.getValue().getQuery();
					assertThat( query )
							.contains( "value:\"Git commit id=" + HEAD_SHA + "\"" )
							.contains( "value:\"CI run=27276276443\"" );
					var outputCaptor = ArgumentCaptor.forClass( GHCheckRunBuilder.Output.class );
					verify( updateBuilderRef.get() ).add( outputCaptor.capture() );
					assertThat( outputCaptor.getValue() ).extracting( "title" )
							.isEqualTo( "No build scan found" );
				} );
	}

	@Test
	void checkRunCompleted_jenkins() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( DEVELOCITY_BUILD_SCAN_CONFIG );

					GHRepository repoMock = mocks.repository( REPO_NAME );

					mockGetCheckRuns( repoMock, HEAD_SHA,
							mockJenkinsCheckRun( HEAD_SHA ) );
					mockDevelocityCheckRun( repoMock, HEAD_SHA );
				} )
				.when()
				.payloadFromClasspath( "/check-run-completed-jenkins.json" )
				.event( GHEvent.CHECK_RUN )
				.then()
				.github( mocks -> {
					GHRepository repoMock = mocks.repository( REPO_NAME );
					verify( repoMock ).createCheckRun( "📋 Build reports", HEAD_SHA );
					verify( repoMock ).updateCheckRun( DEVELOCITY_CHECK_RUN_ID );
					var queryCaptor = ArgumentCaptor.forClass( BuildsQuery.BuildsQueryQueryParam.class );
					verify( develocityBuildsApiMock ).getBuilds( queryCaptor.capture() );
					String query = queryCaptor.getValue().getQuery();
					assertThat( query )
							.contains( "value:\"Git commit id=" + HEAD_SHA + "\"" )
							.contains( "value:\"CI job=hibernate-orm-pipeline\"" )
							.contains( "value:\"CI build number=1234\"" );
				} );
	}

	@Test
	void checkRunCompleted_noConfig() throws IOException {
		given()
				.github( mocks -> {
				} )
				.when()
				.payloadFromClasspath( "/check-run-completed-github-actions.json" )
				.event( GHEvent.CHECK_RUN )
				.then()
				.github( mocks -> {
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void checkRunCompleted_ownCheckRun() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( DEVELOCITY_BUILD_SCAN_CONFIG );
				} )
				.when()
				.payloadFromString( """
						{
						  "action": "completed",
						  "check_run": {
						    "id": 80558394108,
						    "name": "📋 Build reports",
						    "head_sha": "%s",
						    "external_id": "",
						    "status": "completed",
						    "conclusion": "neutral",
						    "app": {
						      "id": 15368,
						      "slug": "github-actions"
						    },
						    "pull_requests": []
						  },
						  "repository": {
						    "id": 961036,
						    "name": "hibernate-orm",
						    "full_name": "%s",
						    "private": false,
						    "owner": {
						      "login": "hibernate",
						      "id": 348262
						    }
						  },
						  "installation": {
						    "id": 15390286
						  }
						}
						""".formatted( HEAD_SHA, REPO_NAME ) )
				.event( GHEvent.CHECK_RUN )
				.then()
				.github( mocks -> {
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void workflowRunCompleted() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( DEVELOCITY_BUILD_SCAN_CONFIG );

					GHRepository repoMock = mocks.repository( REPO_NAME );

					mockGetCheckRuns( repoMock, WORKFLOW_HEAD_SHA,
							mockGitHubActionsCheckRun( WORKFLOW_HEAD_SHA ) );
					mockDevelocityCheckRun( repoMock, WORKFLOW_HEAD_SHA );
				} )
				.when()
				.payloadFromClasspath( "/workflow-run-completed.json" )
				.event( GHEvent.WORKFLOW_RUN )
				.then()
				.github( mocks -> {
					GHRepository repoMock = mocks.repository( REPO_NAME );
					verify( repoMock ).createCheckRun( "📋 Build reports", WORKFLOW_HEAD_SHA );
					verify( repoMock ).updateCheckRun( DEVELOCITY_CHECK_RUN_ID );
					var queryCaptor = ArgumentCaptor.forClass( BuildsQuery.BuildsQueryQueryParam.class );
					verify( develocityBuildsApiMock ).getBuilds( queryCaptor.capture() );
					String query = queryCaptor.getValue().getQuery();
					assertThat( query )
							.contains( "value:\"Git commit id=" + WORKFLOW_HEAD_SHA + "\"" );
				} );
	}

	@Test
	void checkSuiteRerequested() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( DEVELOCITY_BUILD_SCAN_CONFIG );

					GHRepository repoMock = mocks.repository( REPO_NAME );

					mockGetCheckRuns( repoMock, HEAD_SHA,
							mockGitHubActionsCheckRun( HEAD_SHA ) );
					mockDevelocityCheckRun( repoMock, HEAD_SHA );
				} )
				.when()
				.payloadFromString( """
						{
						  "action": "rerequested",
						  "check_suite": {
						    "id": 73312401811,
						    "head_sha": "%s",
						    "head_branch": "some-branch",
						    "status": "queued",
						    "conclusion": null,
						    "pull_requests": []
						  },
						  "repository": {
						    "id": 961036,
						    "name": "hibernate-orm",
						    "full_name": "%s",
						    "private": false,
						    "owner": {
						      "login": "hibernate",
						      "id": 348262
						    }
						  },
						  "installation": {
						    "id": 15390286
						  }
						}
						""".formatted( HEAD_SHA, REPO_NAME ) )
				.event( GHEvent.CHECK_SUITE )
				.then()
				.github( mocks -> {
					GHRepository repoMock = mocks.repository( REPO_NAME );
					verify( repoMock ).createCheckRun( "📋 Build reports", HEAD_SHA );
					verify( repoMock ).updateCheckRun( DEVELOCITY_CHECK_RUN_ID );
					verify( develocityBuildsApiMock ).getBuilds( any() );
				} );
	}

	@Test
	void checkSuiteRerequested_noConfig() throws IOException {
		given()
				.github( mocks -> {
				} )
				.when()
				.payloadFromString( """
						{
						  "action": "rerequested",
						  "check_suite": {
						    "id": 73312401811,
						    "head_sha": "%s",
						    "head_branch": "some-branch",
						    "status": "queued",
						    "conclusion": null,
						    "pull_requests": []
						  },
						  "repository": {
						    "id": 961036,
						    "name": "hibernate-orm",
						    "full_name": "%s",
						    "private": false,
						    "owner": {
						      "login": "hibernate",
						      "id": 348262
						    }
						  },
						  "installation": {
						    "id": 15390286
						  }
						}
						""".formatted( HEAD_SHA, REPO_NAME ) )
				.event( GHEvent.CHECK_SUITE )
				.then()
				.github( mocks -> {
					GHRepository repoMock = mocks.repository( REPO_NAME );
					verify( repoMock, never() ).createCheckRun( "📋 Build reports", HEAD_SHA );
					verifyNoMoreInteractions( develocityBuildsApiMock );
				} );
	}

	@Test
	void checkRunCompleted_titleAllSucceeded() throws IOException {
		when( develocityBuildsApiMock.getBuilds( any() ) ).thenReturn( List.of(
				createBuildScan( "scan1", false, false ),
				createBuildScan( "scan2", false, false ) ) );

		var updateBuilderRef = new java.util.concurrent.atomic.AtomicReference<GHCheckRunBuilder>();
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( DEVELOCITY_BUILD_SCAN_CONFIG );

					GHRepository repoMock = mocks.repository( REPO_NAME );
					mockGetCheckRuns( repoMock, HEAD_SHA,
							mockGitHubActionsCheckRun( HEAD_SHA ) );
					updateBuilderRef.set( mockDevelocityCheckRun( repoMock, HEAD_SHA ) );
				} )
				.when()
				.payloadFromClasspath( "/check-run-completed-github-actions.json" )
				.event( GHEvent.CHECK_RUN )
				.then()
				.github( mocks -> {
					var outputCaptor = ArgumentCaptor.forClass( GHCheckRunBuilder.Output.class );
					verify( updateBuilderRef.get() ).add( outputCaptor.capture() );
					assertThat( outputCaptor.getValue() ).extracting( "title" )
							.isEqualTo( "2 succeeded" );
				} );
	}

	@Test
	void checkRunCompleted_titleSomeFailed() throws IOException {
		when( develocityBuildsApiMock.getBuilds( any() ) ).thenReturn( List.of(
				createBuildScan( "scan1", false, false ),
				createBuildScan( "scan2", true, false ),
				createBuildScan( "scan3", false, false ) ) );

		var updateBuilderRef = new java.util.concurrent.atomic.AtomicReference<GHCheckRunBuilder>();
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( DEVELOCITY_BUILD_SCAN_CONFIG );

					GHRepository repoMock = mocks.repository( REPO_NAME );
					mockGetCheckRuns( repoMock, HEAD_SHA,
							mockGitHubActionsCheckRun( HEAD_SHA ) );
					updateBuilderRef.set( mockDevelocityCheckRun( repoMock, HEAD_SHA ) );
				} )
				.when()
				.payloadFromClasspath( "/check-run-completed-github-actions.json" )
				.event( GHEvent.CHECK_RUN )
				.then()
				.github( mocks -> {
					var outputCaptor = ArgumentCaptor.forClass( GHCheckRunBuilder.Output.class );
					verify( updateBuilderRef.get() ).add( outputCaptor.capture() );
					assertThat( outputCaptor.getValue() ).extracting( "title" )
							.isEqualTo( "1/3 failed" );
					verify( updateBuilderRef.get() ).withConclusion( GHCheckRun.Conclusion.FAILURE );
					verify( updateBuilderRef.get() ).withStatus( GHCheckRun.Status.COMPLETED );
				} );
	}

	@Test
	void checkRunCompleted_allSucceeded_conclusionSuccess() throws IOException {
		when( develocityBuildsApiMock.getBuilds( any() ) ).thenReturn( List.of(
				createBuildScan( "scan1", false, false ),
				createBuildScan( "scan2", false, false ) ) );

		var updateBuilderRef = new java.util.concurrent.atomic.AtomicReference<GHCheckRunBuilder>();
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( DEVELOCITY_BUILD_SCAN_CONFIG );

					GHRepository repoMock = mocks.repository( REPO_NAME );
					mockGetCheckRuns( repoMock, HEAD_SHA,
							mockGitHubActionsCheckRun( HEAD_SHA ) );
					updateBuilderRef.set( mockDevelocityCheckRun( repoMock, HEAD_SHA ) );
				} )
				.when()
				.payloadFromClasspath( "/check-run-completed-github-actions.json" )
				.event( GHEvent.CHECK_RUN )
				.then()
				.github( mocks -> {
					verify( updateBuilderRef.get() ).withConclusion( GHCheckRun.Conclusion.SUCCESS );
					verify( updateBuilderRef.get() ).withStatus( GHCheckRun.Status.COMPLETED );
				} );
	}

	@Test
	void checkRunCompleted_noScans_conclusionNeutral() throws IOException {
		var updateBuilderRef = new java.util.concurrent.atomic.AtomicReference<GHCheckRunBuilder>();
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( DEVELOCITY_BUILD_SCAN_CONFIG );

					GHRepository repoMock = mocks.repository( REPO_NAME );
					mockGetCheckRuns( repoMock, HEAD_SHA,
							mockGitHubActionsCheckRun( HEAD_SHA ) );
					updateBuilderRef.set( mockDevelocityCheckRun( repoMock, HEAD_SHA ) );
				} )
				.when()
				.payloadFromClasspath( "/check-run-completed-github-actions.json" )
				.event( GHEvent.CHECK_RUN )
				.then()
				.github( mocks -> {
					verify( updateBuilderRef.get() ).withConclusion( GHCheckRun.Conclusion.NEUTRAL );
					verify( updateBuilderRef.get() ).withStatus( GHCheckRun.Status.COMPLETED );
				} );
	}

	@Test
	void checkRunCompleted_otherChecksStillRunning_inProgress() throws IOException {
		when( develocityBuildsApiMock.getBuilds( any() ) ).thenReturn( List.of(
				createBuildScan( "scan1", false, false ) ) );

		var updateBuilderRef = new java.util.concurrent.atomic.AtomicReference<GHCheckRunBuilder>();
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( DEVELOCITY_BUILD_SCAN_CONFIG );

					GHRepository repoMock = mocks.repository( REPO_NAME );
					mockGetCheckRuns( repoMock, HEAD_SHA,
							mockGitHubActionsCheckRun( HEAD_SHA ),
							mockGitHubActionsCheckRun( HEAD_SHA, "GraalVM", 99999L,
									GHCheckRun.Status.IN_PROGRESS, null ) );
					updateBuilderRef.set( mockDevelocityCheckRun( repoMock, HEAD_SHA ) );
				} )
				.when()
				.payloadFromClasspath( "/check-run-completed-github-actions.json" )
				.event( GHEvent.CHECK_RUN )
				.then()
				.github( mocks -> {
					verify( updateBuilderRef.get() ).withStatus( GHCheckRun.Status.IN_PROGRESS );
					verify( updateBuilderRef.get(), never() ).withConclusion( any() );
					var outputCaptor = ArgumentCaptor.forClass( GHCheckRunBuilder.Output.class );
					verify( updateBuilderRef.get() ).add( outputCaptor.capture() );
					assertThat( outputCaptor.getValue() ).extracting( "title" )
							.isEqualTo( "1 scans collected — 1 still running" );
				} );
	}

	@Test
	void checkRunCompleted_failedScanAndStillRunning_failureImmediate() throws IOException {
		when( develocityBuildsApiMock.getBuilds( any() ) ).thenReturn( List.of(
				createBuildScan( "scan1", true, false ),
				createBuildScan( "scan2", false, false ) ) );

		var updateBuilderRef = new java.util.concurrent.atomic.AtomicReference<GHCheckRunBuilder>();
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( DEVELOCITY_BUILD_SCAN_CONFIG );

					GHRepository repoMock = mocks.repository( REPO_NAME );
					mockGetCheckRuns( repoMock, HEAD_SHA,
							mockGitHubActionsCheckRun( HEAD_SHA ),
							mockGitHubActionsCheckRun( HEAD_SHA, "GraalVM", 99999L,
									GHCheckRun.Status.IN_PROGRESS, null ) );
					updateBuilderRef.set( mockDevelocityCheckRun( repoMock, HEAD_SHA ) );
				} )
				.when()
				.payloadFromClasspath( "/check-run-completed-github-actions.json" )
				.event( GHEvent.CHECK_RUN )
				.then()
				.github( mocks -> {
					verify( updateBuilderRef.get() ).withConclusion( GHCheckRun.Conclusion.FAILURE );
					verify( updateBuilderRef.get() ).withStatus( GHCheckRun.Status.COMPLETED );
					var outputCaptor = ArgumentCaptor.forClass( GHCheckRunBuilder.Output.class );
					verify( updateBuilderRef.get() ).add( outputCaptor.capture() );
					assertThat( outputCaptor.getValue() ).extracting( "title" )
							.isEqualTo( "1/2 failed — 1 still running" );
				} );
	}

	@Test
	void checkRunCompleted_failedCheckWithoutScan() throws IOException {
		var updateBuilderRef = new java.util.concurrent.atomic.AtomicReference<GHCheckRunBuilder>();
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( DEVELOCITY_BUILD_SCAN_CONFIG );

					GHRepository repoMock = mocks.repository( REPO_NAME );
					mockGetCheckRuns( repoMock, HEAD_SHA,
							mockGitHubActionsCheckRun( HEAD_SHA ),
							mockGitHubActionsCheckRun( HEAD_SHA, "actionlint", 88888L,
									GHCheckRun.Status.COMPLETED, GHCheckRun.Conclusion.FAILURE ) );
					updateBuilderRef.set( mockDevelocityCheckRun( repoMock, HEAD_SHA ) );
				} )
				.when()
				.payloadFromClasspath( "/check-run-completed-github-actions.json" )
				.event( GHEvent.CHECK_RUN )
				.then()
				.github( mocks -> {
					verify( updateBuilderRef.get() ).withConclusion( GHCheckRun.Conclusion.FAILURE );
					verify( updateBuilderRef.get() ).withStatus( GHCheckRun.Status.COMPLETED );
					var outputCaptor = ArgumentCaptor.forClass( GHCheckRunBuilder.Output.class );
					verify( updateBuilderRef.get() ).add( outputCaptor.capture() );
					assertThat( outputCaptor.getValue() ).extracting( "title" )
							.isEqualTo( "1/1 failed" );
				} );
	}

	private GHCheckRun mockGitHubActionsCheckRun(String sha) throws IOException {
		return mockGitHubActionsCheckRun( sha, GHCheckRun.Status.COMPLETED, GHCheckRun.Conclusion.SUCCESS );
	}

	private GHCheckRun mockGitHubActionsCheckRun(String sha, GHCheckRun.Status status,
			GHCheckRun.Conclusion conclusion) throws IOException {
		GHCheckRun checkRun = mock( GHCheckRun.class );
		GHApp app = mock( GHApp.class );
		when( checkRun.getApp() ).thenReturn( app );
		when( app.getSlug() ).thenReturn( "github-actions" );
		when( checkRun.getHeadSha() ).thenReturn( sha );
		when( checkRun.getDetailsUrl() ).thenReturn(
				new URL( "https://github.com/hibernate/hibernate-orm/actions/runs/27276276443/job/80558394108" ) );
		lenient().when( checkRun.getName() ).thenReturn( "Build" );
		when( checkRun.getStatus() ).thenReturn( status );
		if ( conclusion != null ) {
			lenient().when( checkRun.getConclusion() ).thenReturn( conclusion );
		}
		return checkRun;
	}

	private GHCheckRun mockGitHubActionsCheckRun(String sha, String name, long runId,
			GHCheckRun.Status status, GHCheckRun.Conclusion conclusion) throws IOException {
		GHCheckRun checkRun = mock( GHCheckRun.class );
		GHApp app = mock( GHApp.class );
		when( checkRun.getApp() ).thenReturn( app );
		when( app.getSlug() ).thenReturn( "github-actions" );
		when( checkRun.getHeadSha() ).thenReturn( sha );
		when( checkRun.getDetailsUrl() ).thenReturn(
				new URL( "https://github.com/hibernate/hibernate-orm/actions/runs/" + runId + "/job/1" ) );
		lenient().when( checkRun.getName() ).thenReturn( name );
		when( checkRun.getStatus() ).thenReturn( status );
		if ( conclusion != null ) {
			lenient().when( checkRun.getConclusion() ).thenReturn( conclusion );
		}
		return checkRun;
	}

	private GHCheckRun mockJenkinsCheckRun(String sha) {
		return mockJenkinsCheckRun( sha, GHCheckRun.Status.COMPLETED, GHCheckRun.Conclusion.SUCCESS );
	}

	private GHCheckRun mockJenkinsCheckRun(String sha, GHCheckRun.Status status,
			GHCheckRun.Conclusion conclusion) {
		GHCheckRun checkRun = mock( GHCheckRun.class );
		GHApp app = mock( GHApp.class );
		when( checkRun.getApp() ).thenReturn( app );
		when( app.getId() ).thenReturn( 347853L );
		when( checkRun.getHeadSha() ).thenReturn( sha );
		when( checkRun.getExternalId() ).thenReturn( "hibernate-orm-pipeline#1234" );
		lenient().when( checkRun.getName() ).thenReturn( "Jenkins CI (hibernate-orm-pipeline)" );
		when( checkRun.getStatus() ).thenReturn( status );
		if ( conclusion != null ) {
			lenient().when( checkRun.getConclusion() ).thenReturn( conclusion );
		}
		return checkRun;
	}

	@SuppressWarnings("unchecked")
	private void mockGetCheckRuns(GHRepository repoMock, String sha, GHCheckRun... checkRuns) throws IOException {
		PagedIterable<GHCheckRun> iterable = mock( PagedIterable.class );
		when( repoMock.getCheckRuns( sha ) ).thenReturn( iterable );
		when( iterable.toList() ).thenReturn( List.of( checkRuns ) );
	}

	private GHCheckRunBuilder mockDevelocityCheckRun(GHRepository repoMock, String sha) throws IOException {
		GHCheckRunBuilder createBuilder = mock( GHCheckRunBuilder.class,
				withSettings().defaultAnswer( Answers.RETURNS_SELF ) );
		when( repoMock.createCheckRun( "📋 Build reports", sha ) ).thenReturn( createBuilder );
		GHCheckRun checkRunMock = mock( GHCheckRun.class );
		when( checkRunMock.getId() ).thenReturn( DEVELOCITY_CHECK_RUN_ID );
		when( createBuilder.create() ).thenReturn( checkRunMock );

		GHCheckRunBuilder updateBuilder = mock( GHCheckRunBuilder.class,
				withSettings().defaultAnswer( Answers.RETURNS_SELF ) );
		when( repoMock.updateCheckRun( DEVELOCITY_CHECK_RUN_ID ) ).thenReturn( updateBuilder );
		when( updateBuilder.create() ).thenReturn( checkRunMock );
		return updateBuilder;
	}

	private Build createBuildScan(String id, boolean hasFailed, boolean hasVerificationFailure) {
		var env = new BuildAttributesEnvironment();
		env.setPublicHostname( "host1" );

		var providerValue = new BuildAttributesValue();
		providerValue.setName( "CI provider" );
		providerValue.setValue( "GitHub" );

		var jobValue = new BuildAttributesValue();
		jobValue.setName( "CI workflow" );
		jobValue.setValue( "Build" );

		var link = new BuildAttributesLink();
		link.setLabel( "GitHub Actions build" );
		link.setUrl( "https://github.com/hibernate/hibernate-orm/actions/runs/123" );

		var attrs = new MavenAttributes();
		attrs.setEnvironment( env );
		attrs.setTags( List.of( "CI" ) );
		attrs.setValues( List.of( providerValue, jobValue ) );
		attrs.setLinks( List.of( link ) );
		attrs.setRequestedGoals( List.of( "clean", "install" ) );
		attrs.setHasFailed( hasFailed );
		attrs.setHasVerificationFailure( hasVerificationFailure );

		var mavenBuild = new BuildModelsMavenAttributes();
		mavenBuild.setModel( attrs );

		var models = new BuildModels();
		models.setMavenAttributes( mavenBuild );

		var build = new Build();
		build.setId( id );
		build.setAvailableAt( 1000L );
		build.setModels( models );
		return build;
	}
}

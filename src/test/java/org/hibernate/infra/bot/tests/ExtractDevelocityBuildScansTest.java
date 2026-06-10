package org.hibernate.infra.bot.tests;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import com.gradle.develocity.model.BuildsQuery;

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
			develocity:
			  buildScan:
			    addCheck: true
			""";

	@InjectMock
	@RestClient
	BuildsApi develocityBuildsApiMock;

	@BeforeEach
	void setUp() {
		when( develocityBuildsApiMock.getBuilds( any() ) ).thenReturn( Collections.emptyList() );
	}

	@Test
	void checkRunCompleted_githubActions() throws IOException {
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
				.payloadFromClasspath( "/check-run-completed-github-actions.json" )
				.event( GHEvent.CHECK_RUN )
				.then()
				.github( mocks -> {
					GHRepository repoMock = mocks.repository( REPO_NAME );
					verify( repoMock ).createCheckRun( "Develocity Build Scans", HEAD_SHA );
					verify( repoMock ).updateCheckRun( DEVELOCITY_CHECK_RUN_ID );
					var queryCaptor = ArgumentCaptor.forClass( BuildsQuery.BuildsQueryQueryParam.class );
					verify( develocityBuildsApiMock ).getBuilds( queryCaptor.capture() );
					String query = queryCaptor.getValue().getQuery();
					assertThat( query )
							.contains( "value:\"Git commit id=" + HEAD_SHA + "\"" )
							.contains( "value:\"CI run=27276276443\"" );
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
					verify( repoMock ).createCheckRun( "Develocity Build Scans", HEAD_SHA );
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
						    "name": "Develocity Build Scans",
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
					verify( repoMock ).createCheckRun( "Develocity Build Scans", WORKFLOW_HEAD_SHA );
					verify( repoMock ).updateCheckRun( DEVELOCITY_CHECK_RUN_ID );
					var queryCaptor = ArgumentCaptor.forClass( BuildsQuery.BuildsQueryQueryParam.class );
					verify( develocityBuildsApiMock ).getBuilds( queryCaptor.capture() );
					String query = queryCaptor.getValue().getQuery();
					assertThat( query )
							.contains( "value:\"Git commit id=" + WORKFLOW_HEAD_SHA + "\"" );
				} );
	}

	private GHCheckRun mockGitHubActionsCheckRun(String sha) throws IOException {
		GHCheckRun checkRun = mock( GHCheckRun.class );
		GHApp app = mock( GHApp.class );
		when( checkRun.getApp() ).thenReturn( app );
		when( app.getSlug() ).thenReturn( "github-actions" );
		when( checkRun.getHeadSha() ).thenReturn( sha );
		when( checkRun.getDetailsUrl() ).thenReturn(
				new URL( "https://github.com/hibernate/hibernate-orm/actions/runs/27276276443/job/80558394108" ) );
		return checkRun;
	}

	private GHCheckRun mockJenkinsCheckRun(String sha) {
		GHCheckRun checkRun = mock( GHCheckRun.class );
		GHApp app = mock( GHApp.class );
		when( checkRun.getApp() ).thenReturn( app );
		when( app.getId() ).thenReturn( 347853L );
		when( checkRun.getHeadSha() ).thenReturn( sha );
		when( checkRun.getExternalId() ).thenReturn( "hibernate-orm-pipeline#1234" );
		return checkRun;
	}

	@SuppressWarnings("unchecked")
	private void mockGetCheckRuns(GHRepository repoMock, String sha, GHCheckRun... checkRuns) throws IOException {
		PagedIterable<GHCheckRun> iterable = mock( PagedIterable.class );
		when( repoMock.getCheckRuns( sha ) ).thenReturn( iterable );
		when( iterable.toList() ).thenReturn( List.of( checkRuns ) );
	}

	private void mockDevelocityCheckRun(GHRepository repoMock, String sha) throws IOException {
		GHCheckRunBuilder createBuilder = mock( GHCheckRunBuilder.class,
				withSettings().defaultAnswer( Answers.RETURNS_SELF ) );
		when( repoMock.createCheckRun( "Develocity Build Scans", sha ) ).thenReturn( createBuilder );
		GHCheckRun checkRunMock = mock( GHCheckRun.class );
		when( checkRunMock.getId() ).thenReturn( DEVELOCITY_CHECK_RUN_ID );
		when( createBuilder.create() ).thenReturn( checkRunMock );

		GHCheckRunBuilder updateBuilder = mock( GHCheckRunBuilder.class,
				withSettings().defaultAnswer( Answers.RETURNS_SELF ) );
		when( repoMock.updateCheckRun( DEVELOCITY_CHECK_RUN_ID ) ).thenReturn( updateBuilder );
		when( updateBuilder.create() ).thenReturn( checkRunMock );
	}
}

package org.hibernate.infra.bot.tests;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.hibernate.infra.bot.config.Feature;
import org.hibernate.infra.bot.config.RepositoryConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.githubapp.GitHubConfigFileProvider;
import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHRepository;
import org.mockito.junit.jupiter.MockitoExtension;

@QuarkusTest
@GitHubAppTest
@ExtendWith(MockitoExtension.class)
public class CheckConfigSyntaxTest extends AbstractPullRequestTest {

	private static final long REPO_ID = 344815557L;
	private static final long PR_ID = 585627026L;
	private static final String HEAD_SHA = "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156";
	private static final String CONFIG_CHECK_RUN_NAME = "Hibernate GitHub Bot Config";

	@InjectMock
	GitHubConfigFileProvider configFileProvider;

	final GHCheckRunBuilder configCheckRunCreateBuilderMock = mockCheckRunBuilder();
	final GHCheckRunBuilder configCheckRunUpdateBuilderMock = mockCheckRunBuilder();

	@BeforeEach
	void setupConfigFileProvider() {
		RepositoryConfig featureConfig = new RepositoryConfig();
		featureConfig.features = Set.of( Feature.CHECK_CONFIG_SYNTAX );
		lenient().when( configFileProvider.fetchConfigFile(
				any(), eq( "hibernate-github-bot.yml" ), any(), any() ) )
				.thenReturn( Optional.of( featureConfig ) );
	}

	@Test
	void configFileChanged_validConfig() throws IOException {
		given()
				.github( mocks -> {
					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( REPO_ID );

					PullRequestMockHelper.start( mocks, PR_ID, repoMock )
							.commit( "Some commit" )
							.file( ".github/hibernate-github-bot.yml" )
							.noComments();

					mockCheckRuns( repoMock, HEAD_SHA );

					GHCheckRun configCheckRunMock = mock( GHCheckRun.class );
					mockCreateCheckRun( repoMock, CONFIG_CHECK_RUN_NAME, HEAD_SHA,
							configCheckRunCreateBuilderMock, configCheckRunMock, 100L );
					mockUpdateCheckRun( repoMock, 100L,
							configCheckRunUpdateBuilderMock, configCheckRunMock );

					when( configFileProvider.fetchConfigFile(
							any(), anyString(), anyString(),
							any(), any() ) )
							.thenReturn( Optional.of( new RepositoryConfig() ) );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					verify( configCheckRunUpdateBuilderMock )
							.withConclusion( GHCheckRun.Conclusion.SUCCESS );
				} );
	}

	@Test
	void configFileChanged_invalidConfig() throws IOException {
		given()
				.github( mocks -> {
					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( REPO_ID );

					PullRequestMockHelper.start( mocks, PR_ID, repoMock )
							.commit( "Some commit" )
							.file( ".github/hibernate-github-bot.yml" )
							.noComments();

					mockCheckRuns( repoMock, HEAD_SHA );

					GHCheckRun configCheckRunMock = mock( GHCheckRun.class );
					mockCreateCheckRun( repoMock, CONFIG_CHECK_RUN_NAME, HEAD_SHA,
							configCheckRunCreateBuilderMock, configCheckRunMock, 100L );
					mockUpdateCheckRun( repoMock, 100L,
							configCheckRunUpdateBuilderMock, configCheckRunMock );

					when( configFileProvider.fetchConfigFile(
							any(), anyString(), anyString(),
							any(), any() ) )
							.thenThrow( new RuntimeException( "Invalid pattern: [unclosed" ) );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					verify( configCheckRunUpdateBuilderMock )
							.withConclusion( GHCheckRun.Conclusion.FAILURE );
				} );
	}

	@Test
	void configFileNotChanged() throws IOException {
		given()
				.github( mocks -> {
					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( REPO_ID );

					PullRequestMockHelper.start( mocks, PR_ID, repoMock )
							.commit( "Some commit" )
							.file( "src/main/java/SomeFile.java" )
							.noComments();

					mockCheckRuns( repoMock, HEAD_SHA );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					verify( repoMock, never() ).createCheckRun(
							eq( CONFIG_CHECK_RUN_NAME ), any() );
				} );
	}
}

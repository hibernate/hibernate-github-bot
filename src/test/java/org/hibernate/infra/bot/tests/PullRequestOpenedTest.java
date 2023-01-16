package org.hibernate.infra.bot.tests;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.junit.QuarkusTest;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@QuarkusTest
@GitHubAppTest
@ExtendWith(MockitoExtension.class)
public class PullRequestOpenedTest {
	private final GHCheckRunBuilder titleCheckRunCreateBuilderMock = mockCheckRunBuilder();
	private final GHCheckRunBuilder titleCheckRunUpdateBuilderMock = mockCheckRunBuilder();
	private final GHCheckRunBuilder jiraCheckRunCreateBuilderMock = mockCheckRunBuilder();
	private final GHCheckRunBuilder jiraCheckRunUpdateBuilderMock = mockCheckRunBuilder();

	@Test
	void titleEndsWithEllipsis() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFileFromString(
							"hibernate-github-bot.yml",
							"""
									jira:
									  projectKey: "HSEARCH"
									"""
					);

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( 344815557L );

					PullRequestMockHelper.start( mocks, 585627026L, repoMock )
							.commit( "HSEARCH-1111 Correct message" )
							.comment( "Some comment" )
							.comment( "Some other comment" );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111-title-ends-with-ellipsis.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHPullRequest prMock = mocks.pullRequest( 585627026L );
					ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass( String.class );
					verify( prMock ).comment( messageCaptor.capture() );
					assertThat( messageCaptor.getValue() )
							.isEqualTo( """
									Thanks for your pull request!

									This pull request does not follow the contribution rules. Could you have a look?

									❌ The pull request title should not end with an ellipsis (make sure the title is complete)

									› This message was automatically generated.""" );
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void commitMessageNotStartingWithIssueKey() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFileFromString(
							"hibernate-github-bot.yml",
							"""
									jira:
									  projectKey: "HSEARCH"
									"""
					);

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( 344815557L );

					PullRequestMockHelper.start( mocks, 585627026L, repoMock )
							.commit( "HSEARCH-1111 Correct message" )
							.commit( "Incorrect message", "06b6d27ba19615d14cdcd509d9eb3334e5571bf2" )
							.comment( "Some comment" )
							.comment( "Some other comment" );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHPullRequest prMock = mocks.pullRequest( 585627026L );
					ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass( String.class );
					verify( prMock ).comment( messageCaptor.capture() );
					assertThat( messageCaptor.getValue() )
							.isEqualTo( """
									Thanks for your pull request!

									This pull request does not follow the contribution rules. Could you have a look?

									❌ All commit messages should start with a JIRA issue key matching pattern `HSEARCH-\\d+`
									    ↳ Offending commits: [06b6d27ba19615d14cdcd509d9eb3334e5571bf2]

									› This message was automatically generated.""" );
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void titleOrBodyNotMentioningCommitIssueKey() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFileFromString(
							"hibernate-github-bot.yml",
							"""
									jira:
									  projectKey: "HSEARCH"
									"""
					);

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( 344815557L );

					PullRequestMockHelper.start( mocks, 585627026L, repoMock )
							.commit( "HSEARCH-1111 Commit 1" )
							.commit( "HSEARCH-1112 Commit 2" )
							.comment( "Some comment" )
							.comment( "Some other comment" );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHPullRequest prMock = mocks.pullRequest( 585627026 );
					ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass( String.class );
					verify( prMock ).comment( messageCaptor.capture() );
					assertThat( messageCaptor.getValue() )
							.isEqualTo( """
									Thanks for your pull request!

									This pull request does not follow the contribution rules. Could you have a look?

									❌ The PR title or body should list the keys of all JIRA issues mentioned in the commits
									    ↳ Issue keys mentioned in commits but missing from the PR title or body: [HSEARCH-1112]

									› This message was automatically generated.""" );
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void titleOrBodyNotMentioningCommitIssueKey_multipleIssuesInSingleCommit() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFileFromString(
							"hibernate-github-bot.yml",
							"""
									jira:
									  projectKey: "HSEARCH"
									"""
					);

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( 344815557L );

					PullRequestMockHelper.start( mocks, 585627026L, repoMock )
							.commit( "HSEARCH-1111 HSEARCH-1112 Commit 1" )
							.comment( "Some comment" )
							.comment( "Some other comment" );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHPullRequest prMock = mocks.pullRequest( 585627026 );
					ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass( String.class );
					verify( prMock ).comment( messageCaptor.capture() );
					assertThat( messageCaptor.getValue() )
							.isEqualTo( """
									Thanks for your pull request!

									This pull request does not follow the contribution rules. Could you have a look?

									❌ The PR title or body should list the keys of all JIRA issues mentioned in the commits
									    ↳ Issue keys mentioned in commits but missing from the PR title or body: [HSEARCH-1112]

									› This message was automatically generated.""" );
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void titleOrBodyNotMentioningCommitIssueKey_nullBody() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFileFromString(
							"hibernate-github-bot.yml",
							"""
									jira:
									  projectKey: "HSEARCH"
									"""
					);

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( 344815557L );

					PullRequestMockHelper.start( mocks, 585627026L, repoMock )
							.commit( "HSEARCH-1111 Commit 1" )
							.commit( "HSEARCH-1112 Commit 2" )
							.comment( "Some comment" )
							.comment( "Some other comment" );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111-no-body.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHPullRequest prMock = mocks.pullRequest( 585627026 );
					ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass( String.class );
					verify( prMock ).comment( messageCaptor.capture() );
					assertThat( messageCaptor.getValue() )
							.isEqualTo( """
									Thanks for your pull request!

									This pull request does not follow the contribution rules. Could you have a look?

									❌ The PR title or body should list the keys of all JIRA issues mentioned in the commits
									    ↳ Issue keys mentioned in commits but missing from the PR title or body: [HSEARCH-1112]

									› This message was automatically generated.""" );
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}


	private GHCheckRunBuilder mockCheckRunBuilder() {
		return mock( GHCheckRunBuilder.class, withSettings().defaultAnswer( Answers.RETURNS_SELF ) );
	}

	private void mockCheckRuns(GHRepository repoMock, String headSHA) throws IOException {
		GHCheckRun titleCheckRunMock = mock( GHCheckRun.class );
		mockCreateCheckRun( repoMock, "Contribution — Title", headSHA,
				titleCheckRunCreateBuilderMock, titleCheckRunMock, 42L
		);
		mockUpdateCheckRun( repoMock, 42L, titleCheckRunUpdateBuilderMock, titleCheckRunMock );

		GHCheckRun jiraCheckRunMock = mock( GHCheckRun.class );
		mockCreateCheckRun( repoMock, "Contribution — JIRA issues", headSHA,
				jiraCheckRunCreateBuilderMock, jiraCheckRunMock, 43L
		);
		mockUpdateCheckRun( repoMock, 43L, jiraCheckRunUpdateBuilderMock, jiraCheckRunMock );
	}

	private void mockCreateCheckRun(GHRepository repoMock, String name, String headSHA,
			GHCheckRunBuilder checkRunBuilderMock, GHCheckRun checkRunMock, long checkRunId) throws IOException {
		when( repoMock.createCheckRun( name, headSHA ) )
				.thenReturn( checkRunBuilderMock );
		when( checkRunMock.getId() ).thenReturn( checkRunId );
		when( checkRunBuilderMock.create() ).thenReturn( checkRunMock );
	}

	private void mockUpdateCheckRun(GHRepository repoMock, long checkRunId,
			GHCheckRunBuilder checkRunBuilderMock, GHCheckRun checkRunMock) throws IOException {
		when( repoMock.updateCheckRun( checkRunId ) ).thenReturn( checkRunBuilderMock );
		when( checkRunBuilderMock.create() ).thenReturn( checkRunMock );
	}

}

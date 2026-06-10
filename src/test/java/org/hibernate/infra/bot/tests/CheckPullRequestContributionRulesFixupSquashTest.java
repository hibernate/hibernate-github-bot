package org.hibernate.infra.bot.tests;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@QuarkusTest
@GitHubAppTest
@ExtendWith(MockitoExtension.class)
public class CheckPullRequestContributionRulesFixupSquashTest extends AbstractPullRequestTest {
	@Test
	void noFixupSquashCommits() throws IOException {
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					PullRequestMockHelper.start( mocks, prId, repoMock )
							.commit( "HSEARCH-1111 Correct message" )
							.comment( "Some comment" )
							.comment( "Some other comment" );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					verify( mergeCommitsCheckRunUpdateBuilderMock ).withConclusion( GHCheckRun.Conclusion.SUCCESS );

					var outputCaptor = ArgumentCaptor.forClass( GHCheckRunBuilder.Output.class );
					verify( mergeCommitsCheckRunUpdateBuilderMock ).add( outputCaptor.capture() );
					var output = outputCaptor.getValue();
					assertThat( output )
							.extracting( "title", InstanceOfAssertFactories.STRING )
							.contains( "All rules passed" );

					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void hasFixupCommit() throws IOException {
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					PullRequestMockHelper.start( mocks, prId, repoMock )
							.commit( "HSEARCH-1111 Some work" )
							.commit( "fixup! HSEARCH-1111 Some work", "abc123fixup" )
							.comment( "Some comment" )
							.comment( "Some other comment" );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					verify( mergeCommitsCheckRunUpdateBuilderMock ).withConclusion( GHCheckRun.Conclusion.FAILURE );

					var outputCaptor = ArgumentCaptor.forClass( GHCheckRunBuilder.Output.class );
					verify( mergeCommitsCheckRunUpdateBuilderMock ).add( outputCaptor.capture() );
					var output = outputCaptor.getValue();
					assertThat( output )
							.extracting( "title", InstanceOfAssertFactories.STRING )
							.isEqualTo( "The pull request should not contain fixup! or squash! commits" );
					assertThat( output )
							.extracting( "summary", InstanceOfAssertFactories.STRING )
							.contains(
									"The pull request should not contain fixup! or squash! commits",
									"abc123fixup",
									"interactive rebase",
									"https://git-scm.com/docs/git-rebase#_interactive_mode"
							);

					GHPullRequest prMock = mocks.pullRequest( prId );
					ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass( String.class );
					verify( prMock ).comment( messageCaptor.capture() );
					assertThat( messageCaptor.getValue() )
							.contains(
									"The pull request should not contain fixup! or squash! commits",
									"abc123fixup",
									"interactive rebase",
									"https://git-scm.com/docs/git-rebase#_interactive_mode"
							);
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void hasSquashCommit() throws IOException {
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					PullRequestMockHelper.start( mocks, prId, repoMock )
							.commit( "HSEARCH-1111 Some work" )
							.commit( "squash! HSEARCH-1111 Some work", "def456squash" )
							.comment( "Some comment" )
							.comment( "Some other comment" );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					verify( mergeCommitsCheckRunUpdateBuilderMock ).withConclusion( GHCheckRun.Conclusion.FAILURE );

					var outputCaptor = ArgumentCaptor.forClass( GHCheckRunBuilder.Output.class );
					verify( mergeCommitsCheckRunUpdateBuilderMock ).add( outputCaptor.capture() );
					var output = outputCaptor.getValue();
					assertThat( output )
							.extracting( "title", InstanceOfAssertFactories.STRING )
							.isEqualTo( "The pull request should not contain fixup! or squash! commits" );
					assertThat( output )
							.extracting( "summary", InstanceOfAssertFactories.STRING )
							.contains(
									"The pull request should not contain fixup! or squash! commits",
									"def456squash",
									"interactive rebase",
									"https://git-scm.com/docs/git-rebase#_interactive_mode"
							);

					GHPullRequest prMock = mocks.pullRequest( prId );
					ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass( String.class );
					verify( prMock ).comment( messageCaptor.capture() );
					assertThat( messageCaptor.getValue() )
							.contains(
									"The pull request should not contain fixup! or squash! commits",
									"def456squash",
									"interactive rebase",
									"https://git-scm.com/docs/git-rebase#_interactive_mode"
							);
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}
}

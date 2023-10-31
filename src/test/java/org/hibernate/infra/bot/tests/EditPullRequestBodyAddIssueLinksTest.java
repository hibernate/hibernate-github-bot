package org.hibernate.infra.bot.tests;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.infra.bot.tests.PullRequestMockHelper.mockPagedIterable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.PagedIterable;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@QuarkusTest
@GitHubAppTest
@ExtendWith(MockitoExtension.class)
public class EditPullRequestBodyAddIssueLinksTest extends AbstractPullRequestTest {
	@Test
	void simple() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									  insertLinksInPullRequests: true
									""" );

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
					verify( prMock ).setBody( messageCaptor.capture() );
					assertThat( messageCaptor.getValue() )
							.isEqualTo( """
									Original pull request body
									
									<!-- Hibernate GitHub Bot issue links start -->
									<!-- THIS SECTION IS AUTOMATICALLY GENERATED, ANY MANUAL CHANGES WILL BE LOST -->
									https://hibernate.atlassian.net/browse/HSEARCH-1111
									https://hibernate.atlassian.net/browse/HSEARCH-1112
									<!-- Hibernate GitHub Bot issue links end -->""" );
				} );
	}

	@Test
	void multipleIssuesInSingleCommit() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									  insertLinksInPullRequests: true
									""" );

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
					verify( prMock ).setBody( messageCaptor.capture() );
					assertThat( messageCaptor.getValue() )
							.isEqualTo( """
									Original pull request body
									
									<!-- Hibernate GitHub Bot issue links start -->
									<!-- THIS SECTION IS AUTOMATICALLY GENERATED, ANY MANUAL CHANGES WILL BE LOST -->
									https://hibernate.atlassian.net/browse/HSEARCH-1111
									https://hibernate.atlassian.net/browse/HSEARCH-1112
									<!-- Hibernate GitHub Bot issue links end -->""" );
				} );
	}

	@Test
	void nullBody() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									  insertLinksInPullRequests: true
									""" );

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
					verify( prMock ).setBody( messageCaptor.capture() );
					assertThat( messageCaptor.getValue() )
							.isEqualTo( """
									<!-- Hibernate GitHub Bot issue links start -->
									<!-- THIS SECTION IS AUTOMATICALLY GENERATED, ANY MANUAL CHANGES WILL BE LOST -->
									https://hibernate.atlassian.net/browse/HSEARCH-1111
									https://hibernate.atlassian.net/browse/HSEARCH-1112
									<!-- Hibernate GitHub Bot issue links end -->""" );
				} );
	}

	@Test
	void alreadyEditedBody() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									  insertLinksInPullRequests: true
									""" );

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
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111-already-edited.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHPullRequest prMock = mocks.pullRequest( 585627026 );
					ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass( String.class );
					verify( prMock ).setBody( messageCaptor.capture() );
					assertThat( messageCaptor.getValue() )
							.isEqualTo( """
									Before links section
									
									After links section

									<!-- Hibernate GitHub Bot issue links start -->
									<!-- THIS SECTION IS AUTOMATICALLY GENERATED, ANY MANUAL CHANGES WILL BE LOST -->
									https://hibernate.atlassian.net/browse/HSEARCH-1111
									https://hibernate.atlassian.net/browse/HSEARCH-1112
									<!-- Hibernate GitHub Bot issue links end -->""" );
				} );
	}

	@Test
	void bodyContainsAllIssues() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									""" );

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
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111-with-issues.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHPullRequest prMock = mocks.pullRequest( 585627026 );
					// setBody should never be invoked since the PR's body already contains both issue keys
					verify( prMock, times( 0 ) ).setBody( null );
				} );
	}

}

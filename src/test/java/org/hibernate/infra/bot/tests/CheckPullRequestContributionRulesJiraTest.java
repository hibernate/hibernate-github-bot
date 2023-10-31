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
import java.util.List;

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
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@QuarkusTest
@GitHubAppTest
@ExtendWith(MockitoExtension.class)
public class CheckPullRequestContributionRulesJiraTest extends AbstractPullRequestTest {
	@Test
	void commitMessageNotStartingWithIssueKey() throws IOException {
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					PullRequestMockHelper.start( mocks, prId, repoMock )
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
					GHPullRequest prMock = mocks.pullRequest( prId );
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
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					PullRequestMockHelper.start( mocks, prId, repoMock )
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
					GHPullRequest prMock = mocks.pullRequest( prId );
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
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					PullRequestMockHelper.start( mocks, prId, repoMock )
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
					GHPullRequest prMock = mocks.pullRequest( prId );
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
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					PullRequestMockHelper.start( mocks, prId, repoMock )
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
					GHPullRequest prMock = mocks.pullRequest( prId );
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
	void titleOrBodyNotMentioningCommitIssueKey_failure() throws IOException {
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					GHPullRequest pullRequestMock = mocks.pullRequest( prId );
					when( pullRequestMock.listCommits() ).thenThrow( new IllegalStateException( "Simulated failure" ) );
					GHCommitPointer baseMock = mock( GHCommitPointer.class, withSettings().stubOnly() );
					when( pullRequestMock.getBase() ).thenReturn( baseMock );
					when( baseMock.getRepository() ).thenReturn( repoMock );
					PagedIterable<GHIssueComment> commentIterableMock = mockPagedIterable( Collections.emptyList() );
					when( pullRequestMock.listComments() ).thenReturn( commentIterableMock );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111.json" )
				.event( GHEvent.PULL_REQUEST, true )
				.then()
				.github( mocks -> {
					verify( jiraCheckRunUpdateBuilderMock ).withConclusion( GHCheckRun.Conclusion.FAILURE );

					var outputCaptor = ArgumentCaptor.forClass( GHCheckRunBuilder.Output.class );
					verify( jiraCheckRunUpdateBuilderMock ).add( outputCaptor.capture() );
					var output = outputCaptor.getValue();
					assertThat( output )
							.extracting( "title", InstanceOfAssertFactories.STRING )
							.contains( "Contribution — JIRA issues", "failed with exception" );
					assertThat( output )
							.extracting( "summary", InstanceOfAssertFactories.STRING )
							.contains( "java.lang.IllegalStateException: Simulated failure" );

					GHPullRequest prMock = mocks.pullRequest( prId );
					ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass( String.class );
					verify( prMock ).comment( messageCaptor.capture() );
					assertThat( messageCaptor.getValue() )
							.isEqualTo( """
									Thanks for your pull request!
									
									This pull request does not follow the contribution rules. Could you have a look?
									
									❌ Contribution — JIRA issues
									    ↳ Failed with exception java.lang.IllegalStateException: Simulated failure
									
									› This message was automatically generated.""" );
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void ignore_dependabotOpensBuildDependencyUpgradePullRequest() throws IOException {
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									  ignore:
									    - user: dependabot[bot]
									      titlePattern: ".*\\\\bmaven\\\\b.*\\\\bplugin\\\\b.*"
									""" );

					var repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					PullRequestMockHelper.start( mocks, prId, repoMock )
							// we don't need to mock commits, since we won't reach them, we will return fast as soon as we
							// determine that the PR was created by dependabot, and it is a build dependency upgrade.

							// mock no comments so when bot checks if it needs to modify any previous comments - it won't fail
							.noComments();

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111-dependabot-upgrades-build-dependencies.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					verify( jiraCheckRunUpdateBuilderMock ).withConclusion( GHCheckRun.Conclusion.SUCCESS );

					var outputCaptor = ArgumentCaptor.forClass( GHCheckRunBuilder.Output.class );
					verify( jiraCheckRunUpdateBuilderMock ).add( outputCaptor.capture() );
					var output = outputCaptor.getValue();
					assertThat( output )
							.extracting( "title", InstanceOfAssertFactories.STRING )
							.contains( "All rules passed" );
					assertThat( output )
							.extracting( "summary", InstanceOfAssertFactories.STRING )
							.isBlank();

					var pullRequest = mocks.pullRequest( prId );
					// no new comments are added
					verify( pullRequest, times( 0 ) ).comment( any() );
					// we were accessing user to see if it is dependabot or not
					verify( pullRequest ).getUser();
					var user = mocks.ghObject( GHUser.class, 49699333L );
					verify( user ).getLogin();
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void ignore_someoneOtherThanDependabotPretendingToOpenBuildDependencyUpgradePullRequest() throws IOException {
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									  ignore:
									    - user: dependabot[bot]
									      titlePattern: ".*\\\\bmaven\\\\b.*\\\\bplugin\\\\b.*"
									""" );

					var repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					PullRequestMockHelper.start( mocks, prId, repoMock )
							.commit( "Bump maven-clean-plugin from 3.2.0 to 3.3.1" )
							// mock no comments so when bot checks if it needs to modify any previous comments - it won't fail
							.noComments();

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111-non-dependabot-upgrades-build-dependencies.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					verify( jiraCheckRunUpdateBuilderMock ).withConclusion( GHCheckRun.Conclusion.FAILURE );

					var outputCaptor = ArgumentCaptor.forClass( GHCheckRunBuilder.Output.class );
					verify( jiraCheckRunUpdateBuilderMock ).add( outputCaptor.capture() );
					var output = outputCaptor.getValue();
					assertThat( output )
							.extracting( "title", InstanceOfAssertFactories.STRING )
							.contains( "All commit messages should start with a JIRA issue key matching pattern `HSEARCH-\\d+`" );
					assertThat( output )
							.extracting( "summary", InstanceOfAssertFactories.STRING )
							.contains( """
									❌ All commit messages should start with a JIRA issue key matching pattern `HSEARCH-\\d+`
									    ↳ Offending commits: [null]
									✔ The PR title or body should list the keys of all JIRA issues mentioned in the commits""" );

					var pullRequest = mocks.pullRequest( prId );

					var commentCaptor = ArgumentCaptor.forClass( String.class );
					verify( pullRequest ).comment( commentCaptor.capture() );
					assertThat( commentCaptor.getValue() )
							.isEqualTo( """
									Thanks for your pull request!
									
									This pull request does not follow the contribution rules. Could you have a look?
									
									❌ All commit messages should start with a JIRA issue key matching pattern `HSEARCH-\\d+`
									    ↳ Offending commits: [null]
									
									› This message was automatically generated.""" );

					// we were accessing user to see if it is dependabot or not
					verify( pullRequest ).getUser();
					var user = mocks.ghObject( GHUser.class, 412878L );
					verify( user ).getLogin();
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void ignoreFiles_commitMessageNotStartingWithIssueKey_allFilesIgnored() throws IOException {
		long repoId = 344815557L;
		long prId = 1580647794L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									  ignoreFiles:
									    - ".github"
									    - "ci"
									    - "build/config"
									    - "Jenkinsfile"
									    - "*/Jenkinsfile"
									    - "*.Jenkinsfile"
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					PullRequestMockHelper.start( mocks, prId, repoMock )
							.commit( "Change Jenkinsfiles", "98822fa0c6d633704216dae11591e7204251e85d",
									List.of( "Jenkinsfile", "bar/foo.Jenkinsfile", "foo/Jenkinsfile" ) )
							.commit( "Change some build config", "1991366c665fec3d9f2e9dc03a052d9ac604d587",
									List.of( ".github/foo.yml", "build/config/pom.xml", "ci/somefile.xml" ) )
							.noComments();

					mockCheckRuns( repoMock, "1991366c665fec3d9f2e9dc03a052d9ac604d587" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-jira-all-files-ignored.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					verify( jiraCheckRunUpdateBuilderMock ).withConclusion( GHCheckRun.Conclusion.SUCCESS );

					var pullRequest = mocks.pullRequest( prId );
					// no new comments are added
					verify( pullRequest, times( 0 ) ).comment( any() );
				} );
	}

	@Test
	void ignoreFiles_commitMessageNotStartingWithIssueKey_someFilesIgnored() throws IOException {
		long repoId = 344815557L;
		long prId = 1580647565L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									  ignoreFiles:
									    - ".github"
									    - "ci"
									    - "build/config"
									    - "Jenkinsfile"
									    - "*/Jenkinsfile"
									    - "*.Jenkinsfile"
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					PullRequestMockHelper.start( mocks, prId, repoMock )
							.commit( "Change Jenkinsfiles", "391faa2d04116673fdc86dfddef5782622bfc233",
									List.of( "Jenkinsfile", "bar/foo.Jenkinsfile", "foo/Jenkinsfile" ) )
							.commit( "Change some build config and POM", "22e9e04d6df246e00268df84fb3d783f37c96312",
									List.of( ".github/foo.yml", "build/config/pom.xml", "ci/somefile.xml", "pom.xml" ) )
							.noComments();

					mockCheckRuns( repoMock, "22e9e04d6df246e00268df84fb3d783f37c96312" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-jira-some-files-ignored.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHPullRequest prMock = mocks.pullRequest( prId );
					ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass( String.class );
					verify( prMock ).comment( messageCaptor.capture() );
					assertThat( messageCaptor.getValue() )
							.isEqualTo( """
									Thanks for your pull request!

									This pull request does not follow the contribution rules. Could you have a look?

									❌ All commit messages should start with a JIRA issue key matching pattern `HSEARCH-\\d+`
									    ↳ Offending commits: [22e9e04d6df246e00268df84fb3d783f37c96312]

									› This message was automatically generated.""" );
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}
}

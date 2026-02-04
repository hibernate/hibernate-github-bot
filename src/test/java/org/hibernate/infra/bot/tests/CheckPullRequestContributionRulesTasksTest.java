package org.hibernate.infra.bot.tests;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.hibernate.infra.bot.jira.JiraFields;
import org.hibernate.infra.bot.jira.JiraIssue;
import org.hibernate.infra.bot.jira.JiraIssues;
import org.hibernate.infra.bot.jira.JiraRestClient;
import org.hibernate.infra.bot.jira.JiraSimpleObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.rest.client.inject.RestClient;
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
public class CheckPullRequestContributionRulesTasksTest extends AbstractPullRequestTest {

	@InjectMock
	@RestClient
	JiraRestClient mock;

	final GHCheckRunBuilder taskCheckRunCreateBuilderMock = mockCheckRunBuilder();
	final GHCheckRunBuilder taskCheckRunUpdateBuilderMock = mockCheckRunBuilder();

	@Override
	void mockCheckRuns(GHRepository repoMock, String headSHA) throws IOException {
		super.mockCheckRuns( repoMock, headSHA );
		GHCheckRun taskCheckRunMock = mock( GHCheckRun.class );
		mockCreateCheckRun( repoMock, "Contribution — Review tasks", headSHA,
				taskCheckRunCreateBuilderMock, taskCheckRunMock, 45L
		);
		mockUpdateCheckRun( repoMock, 45L, taskCheckRunUpdateBuilderMock, taskCheckRunMock );
	}


	@BeforeEach
	public void setUp() {
		JiraIssues issues = new JiraIssues();
		JiraIssue issue1 = new JiraIssue();
		issue1.key = "HSEARCH-1111";
		issue1.fields = new JiraFields();
		issue1.fields.issuetype = new JiraSimpleObject();
		issue1.fields.issuetype.name = "bug";
		issues.issues = List.of( issue1 );
		when(mock.find(anyString(),anyString())).thenReturn( issues );
	}

	@Test
	void createTasks() throws IOException {
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									pullRequestTasks:
									  enabled: true
									  tasks:
									    default:
									      - task1
									      - task2
									      - task3
									      - task4
									    bug:
									      - bug task1
									      - bug task2
									      - bug task3
									      - bug task4
									    improvement:
									      - improvement task1
									      - improvement task2
									      - improvement task3
									      - improvement task4
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					PullRequestMockHelper.start( mocks, prId, repoMock )
							.commit( "HSEARCH-1111 Correct message" )
							.commit( "Commit with no Jira key" )
							.comment( "some comment" );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111-no-body.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHPullRequest prMock = mocks.pullRequest( prId );
					ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass( String.class );
					verify( prMock ).comment( commentCaptor.capture() );
					assertThat( commentCaptor.getValue() )
							.isEqualTo( """
									Thanks for your pull request!

									This pull request does not follow the contribution rules. Could you have a look?

									❌ All commit messages should start with a JIRA issue key matching pattern `HSEARCH-\\d+`
									    ↳ Offending commits: [null]

									› This message was automatically generated.""" );
					ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass( String.class );
					verify( prMock ).setBody( bodyCaptor.capture() );
					assertThat( bodyCaptor.getValue() )
							.isEqualTo( """
									

									 ---
									 <!-- Hibernate GitHub Bot task list start -->
									 Please make sure that the following tasks are completed:
									 Tasks specific to HSEARCH-1111 (bug):
									 - [ ] bug task1
									 - [ ] bug task2
									 - [ ] bug task3
									 - [ ] bug task4


									 <!-- Hibernate GitHub Bot task list end -->""" );
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void bodyContainsTasks() throws IOException {
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									pullRequestTasks:
									  enabled: true
									  tasks:
									    default:
									      - task1
									      - task2
									      - task3
									      - task4
									    bug:
									      - bug task1
									      - bug task2
									      - bug task3
									      - bug task4
									    improvement:
									      - improvement task1
									      - improvement task2
									      - improvement task3
									      - improvement task4
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					PullRequestMockHelper.start( mocks, prId, repoMock )
							.commit( "HSEARCH-1111 Correct message" )
							.comment( "Some other comment" );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111-with-tasks.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHPullRequest prMock = mocks.pullRequest( prId );
					verify( prMock, times(0) ).comment( any() );
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void tasksCheckIgnored() throws IOException {
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									  # We also ignore jira keys check as dependabot PRs won't have them anyways:
									  ignore:
									    - user: dependabot[bot]
									      titlePattern: ".*\\\\bmaven\\\\b.*\\\\bplugin\\\\b.*"
									pullRequestTasks:
									  enabled: true
									  tasks:
									    default:
									      - task1
									      - task2
									      - task3
									      - task4
									    bug:
									      - bug task1
									      - bug task2
									      - bug task3
									      - bug task4
									    improvement:
									      - improvement task1
									      - improvement task2
									      - improvement task3
									      - improvement task4
									  ignore:
									    - user: dependabot[bot]
									      titlePattern: ".*\\\\bmaven\\\\b.*\\\\bplugin\\\\b.*"
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					PullRequestMockHelper.start( mocks, prId, repoMock )
							.noComments();

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111-dependabot-upgrades-build-dependencies.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

}

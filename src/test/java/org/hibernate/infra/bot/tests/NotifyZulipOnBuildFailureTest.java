package org.hibernate.infra.bot.tests;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.util.List;

import org.hibernate.infra.bot.zulip.ZulipClient;

import org.junit.jupiter.api.Test;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRunQueryBuilder;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.mockito.Answers;

@QuarkusTest
@GitHubAppTest
public class NotifyZulipOnBuildFailureTest {

	private static final String REPO_NAME = "hibernate/hibernate-orm";

	@InjectMock
	@RestClient
	ZulipClient zulipClientMock;

	@Test
	void scheduleFailure_notifiesZulip() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( "features: [ NOTIFY_ZULIP_ON_BUILD_FAILURE ]" );
				} )
				.when()
				.payloadFromClasspath( "/workflow-run-completed-failure-schedule.json" )
				.event( GHEvent.WORKFLOW_RUN )
				.then()
				.github( mocks -> {
					verify( zulipClientMock ).sendMessage(
							"stream",
							"hibernate-orm-dev",
							"GitHub workflow failures",
							"[**Hibernate ORM CI**](https://github.com/hibernate/hibernate-orm/actions/runs/14112498346) failed on `main` in hibernate/hibernate-orm."
					);
				} );
	}

	@Test
	void scheduleFailure_customChannelAndTopic() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( """
									features: [ NOTIFY_ZULIP_ON_BUILD_FAILURE ]
									zulipNotification:
									  channel: custom-channel
									  topic: custom topic
									""" );
				} )
				.when()
				.payloadFromClasspath( "/workflow-run-completed-failure-schedule.json" )
				.event( GHEvent.WORKFLOW_RUN )
				.then()
				.github( mocks -> {
					verify( zulipClientMock ).sendMessage(
							"stream",
							"custom-channel",
							"custom topic",
							"[**Hibernate ORM CI**](https://github.com/hibernate/hibernate-orm/actions/runs/14112498346) failed on `main` in hibernate/hibernate-orm."
					);
				} );
	}

	@Test
	void scheduleFailure_unknownRepo_defaultsToInfraChannel() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( "features: [ NOTIFY_ZULIP_ON_BUILD_FAILURE ]" );
				} )
				.when()
				.payloadFromString( """
						{
						  "action": "completed",
						  "workflow_run": {
						    "id": 14112498346,
						    "name": "Some CI",
						    "head_branch": "main",
						    "head_sha": "ddbf12d7d8ff89a85c579c98c75358d8e9015cc5",
						    "event": "schedule",
						    "status": "completed",
						    "conclusion": "failure",
						    "workflow_id": 124546728,
						    "url": "https://api.github.com/repos/hibernate/hibernate-tools/actions/runs/14112498346",
						    "html_url": "https://github.com/hibernate/hibernate-tools/actions/runs/14112498346",
						    "pull_requests": [],
						    "created_at": "2025-03-27T17:05:01Z",
						    "updated_at": "2025-03-27T17:19:44Z",
						    "run_attempt": 1,
						    "run_started_at": "2025-03-27T17:17:28Z",
						    "jobs_url": "https://api.github.com/repos/hibernate/hibernate-tools/actions/runs/14112498346/jobs",
						    "logs_url": "https://api.github.com/repos/hibernate/hibernate-tools/actions/runs/14112498346/logs",
						    "check_suite_url": "https://api.github.com/repos/hibernate/hibernate-tools/check-suites/36324304144",
						    "artifacts_url": "https://api.github.com/repos/hibernate/hibernate-tools/actions/runs/14112498346/artifacts",
						    "cancel_url": "https://api.github.com/repos/hibernate/hibernate-tools/actions/runs/14112498346/cancel",
						    "rerun_url": "https://api.github.com/repos/hibernate/hibernate-tools/actions/runs/14112498346/rerun",
						    "workflow_url": "https://api.github.com/repos/hibernate/hibernate-tools/actions/workflows/124546728",
						    "head_commit": {
						      "id": "ddbf12d7d8ff89a85c579c98c75358d8e9015cc5",
						      "message": "Some commit",
						      "timestamp": "2025-03-27T16:54:54Z",
						      "author": { "name": "Test", "email": "test@test.com" },
						      "committer": { "name": "Test", "email": "test@test.com" }
						    },
						    "repository": {
						      "id": 123456, "name": "hibernate-tools", "full_name": "hibernate/hibernate-tools",
						      "private": false, "owner": { "login": "hibernate", "id": 348262 },
						      "html_url": "https://github.com/hibernate/hibernate-tools"
						    },
						    "head_repository": {
						      "id": 123456, "name": "hibernate-tools", "full_name": "hibernate/hibernate-tools",
						      "private": false, "owner": { "login": "hibernate", "id": 348262 },
						      "html_url": "https://github.com/hibernate/hibernate-tools"
						    }
						  },
						  "workflow": {
						    "id": 124546728, "name": "Some CI",
						    "path": ".github/workflows/ci.yml", "state": "active"
						  },
						  "repository": {
						    "id": 123456, "name": "hibernate-tools", "full_name": "hibernate/hibernate-tools",
						    "private": false, "owner": { "login": "hibernate", "id": 348262 },
						    "html_url": "https://github.com/hibernate/hibernate-tools"
						  },
						  "installation": { "id": 15390286 }
						}
						""" )
				.event( GHEvent.WORKFLOW_RUN )
				.then()
				.github( mocks -> {
					verify( zulipClientMock ).sendMessage(
							"stream",
							"hibernate-infra",
							"GitHub workflow failures",
							"[**Some CI**](https://github.com/hibernate/hibernate-tools/actions/runs/14112498346) failed on `main` in hibernate/hibernate-tools."
					);
				} );
	}

	@Test
	void workflowRunFailure_resolvesTriggeringBranch() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( "features: [ NOTIFY_ZULIP_ON_BUILD_FAILURE ]" );

					GHRepository repoMock = mocks.repository( REPO_NAME );
					mockTriggeringWorkflowRun( repoMock, "ddbf12d7d8ff89a85c579c98c75358d8e9015cc5",
							"6.6", GHEvent.PUSH );
				} )
				.when()
				.payloadFromClasspath( "/workflow-run-completed-failure-workflow-run.json" )
				.event( GHEvent.WORKFLOW_RUN )
				.then()
				.github( mocks -> {
					verify( zulipClientMock ).sendMessage(
							"stream",
							"hibernate-orm-dev",
							"GitHub workflow failures",
							"[**GH Actions CI reporting**](https://github.com/hibernate/hibernate-orm/actions/runs/14200000000) failed on `6.6` in hibernate/hibernate-orm."
					);
				} );
	}

	@Test
	void featureDisabled_noNotification() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( "features: [ CHECK_CONTRIBUTION_RULES ]" );
				} )
				.when()
				.payloadFromClasspath( "/workflow-run-completed-failure-schedule.json" )
				.event( GHEvent.WORKFLOW_RUN )
				.then()
				.github( mocks -> {
					verifyNoInteractions( zulipClientMock );
				} );
	}

	@Test
	void noConfig_noNotification() throws IOException {
		given()
				.github( mocks -> {
				} )
				.when()
				.payloadFromClasspath( "/workflow-run-completed-failure-schedule.json" )
				.event( GHEvent.WORKFLOW_RUN )
				.then()
				.github( mocks -> {
					verifyNoInteractions( zulipClientMock );
				} );
	}

	@Test
	void successConclusion_noNotification() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( "features: [ NOTIFY_ZULIP_ON_BUILD_FAILURE ]" );
				} )
				.when()
				.payloadFromClasspath( "/workflow-run-completed.json" )
				.event( GHEvent.WORKFLOW_RUN )
				.then()
				.github( mocks -> {
					verifyNoInteractions( zulipClientMock );
				} );
	}

	@Test
	void pullRequestEvent_noNotification() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile( "hibernate-github-bot.yml" )
							.fromString( "features: [ NOTIFY_ZULIP_ON_BUILD_FAILURE ]" );
				} )
				.when()
				.payloadFromString( """
						{
						  "action": "completed",
						  "workflow_run": {
						    "id": 14112498346,
						    "name": "Hibernate ORM CI",
						    "head_branch": "pr-branch",
						    "head_sha": "ddbf12d7d8ff89a85c579c98c75358d8e9015cc5",
						    "event": "pull_request",
						    "status": "completed",
						    "conclusion": "failure",
						    "workflow_id": 124546728,
						    "url": "https://api.github.com/repos/hibernate/hibernate-orm/actions/runs/14112498346",
						    "html_url": "https://github.com/hibernate/hibernate-orm/actions/runs/14112498346",
						    "pull_requests": [{"number": 42}],
						    "created_at": "2025-03-27T17:05:01Z",
						    "updated_at": "2025-03-27T17:19:44Z",
						    "run_attempt": 1,
						    "run_started_at": "2025-03-27T17:17:28Z",
						    "jobs_url": "https://api.github.com/repos/hibernate/hibernate-orm/actions/runs/14112498346/jobs",
						    "logs_url": "https://api.github.com/repos/hibernate/hibernate-orm/actions/runs/14112498346/logs",
						    "check_suite_url": "https://api.github.com/repos/hibernate/hibernate-orm/check-suites/36324304144",
						    "artifacts_url": "https://api.github.com/repos/hibernate/hibernate-orm/actions/runs/14112498346/artifacts",
						    "cancel_url": "https://api.github.com/repos/hibernate/hibernate-orm/actions/runs/14112498346/cancel",
						    "rerun_url": "https://api.github.com/repos/hibernate/hibernate-orm/actions/runs/14112498346/rerun",
						    "workflow_url": "https://api.github.com/repos/hibernate/hibernate-orm/actions/workflows/124546728",
						    "head_commit": {
						      "id": "ddbf12d7d8ff89a85c579c98c75358d8e9015cc5",
						      "message": "Some PR commit",
						      "timestamp": "2025-03-27T16:54:54Z",
						      "author": { "name": "Test", "email": "test@test.com" },
						      "committer": { "name": "Test", "email": "test@test.com" }
						    },
						    "repository": {
						      "id": 961036, "name": "hibernate-orm", "full_name": "hibernate/hibernate-orm",
						      "private": false, "owner": { "login": "hibernate", "id": 348262 },
						      "html_url": "https://github.com/hibernate/hibernate-orm"
						    },
						    "head_repository": {
						      "id": 961036, "name": "hibernate-orm", "full_name": "hibernate/hibernate-orm",
						      "private": false, "owner": { "login": "hibernate", "id": 348262 },
						      "html_url": "https://github.com/hibernate/hibernate-orm"
						    }
						  },
						  "workflow": {
						    "id": 124546728, "name": "Hibernate ORM CI",
						    "path": ".github/workflows/ci.yml", "state": "active"
						  },
						  "repository": {
						    "id": 961036, "name": "hibernate-orm", "full_name": "hibernate/hibernate-orm",
						    "private": false, "owner": { "login": "hibernate", "id": 348262 },
						    "html_url": "https://github.com/hibernate/hibernate-orm"
						  },
						  "installation": { "id": 15390286 }
						}
						""" )
				.event( GHEvent.WORKFLOW_RUN )
				.then()
				.github( mocks -> {
					verifyNoInteractions( zulipClientMock );
				} );
	}

	@SuppressWarnings("unchecked")
	private void mockTriggeringWorkflowRun(GHRepository repoMock, String headSha,
			String triggeringBranch, GHEvent triggeringEvent) throws IOException {
		GHWorkflowRunQueryBuilder queryBuilder = mock( GHWorkflowRunQueryBuilder.class,
				withSettings().defaultAnswer( Answers.RETURNS_SELF ) );
		when( repoMock.queryWorkflowRuns() ).thenReturn( queryBuilder );

		GHWorkflowRun triggeringRun = mock( GHWorkflowRun.class );
		when( triggeringRun.getHeadBranch() ).thenReturn( triggeringBranch );
		when( triggeringRun.getEvent() ).thenReturn( triggeringEvent );

		PagedIterable<GHWorkflowRun> iterable = mock( PagedIterable.class );
		when( queryBuilder.list() ).thenReturn( iterable );
		PagedIterator<GHWorkflowRun> iterator = mock( PagedIterator.class );
		when( iterable.iterator() ).thenReturn( iterator );
		when( iterator.hasNext() ).thenReturn( true, false );
		when( iterator.next() ).thenReturn( triggeringRun );
	}
}

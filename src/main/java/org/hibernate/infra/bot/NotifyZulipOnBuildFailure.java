package org.hibernate.infra.bot;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.hibernate.infra.bot.config.DeploymentConfig;
import org.hibernate.infra.bot.config.Feature;
import org.hibernate.infra.bot.config.RepositoryConfig;
import io.quarkiverse.githubapi.GitHubApiUtil;
import org.hibernate.infra.bot.zulip.ZulipClient;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.WorkflowRun;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.GHWorkflowRun;

public class NotifyZulipOnBuildFailure {

	private static final String DEFAULT_TOPIC = "GitHub workflow failures";
	private static final String DEFAULT_CHANNEL = "hibernate-infra";
	private static final Map<String, String> REPO_TO_CHANNEL = Map.of(
			"hibernate-orm", "hibernate-orm-dev",
			"hibernate-search", "hibernate-search-dev",
			"hibernate-validator", "hibernate-validator-dev",
			"hibernate-reactive", "hibernate-reactive-dev"
	);

	@Inject
	DeploymentConfig deploymentConfig;

	@RestClient
	ZulipClient zulipClient;

	void workflowRunCompleted(@WorkflowRun.Completed GHEventPayload.WorkflowRun payload,
			@ConfigFile("hibernate-github-bot.yml") RepositoryConfig repositoryConfig) throws IOException {
		if ( !Feature.NOTIFY_ZULIP_ON_BUILD_FAILURE.isEnabled( repositoryConfig ) ) {
			return;
		}

		GHWorkflowRun workflowRun = payload.getWorkflowRun();

		if ( workflowRun.getConclusion() != GHWorkflowRun.Conclusion.FAILURE ) {
			return;
		}

		GHEvent event = workflowRun.getEvent();
		if ( event != GHEvent.SCHEDULE && event != GHEvent.WORKFLOW_RUN ) {
			return;
		}

		GHRepository repository = payload.getRepository();
		String repoFullName = repository.getFullName();
		String repoName = repository.getName();
		String channel = resolveChannel( repositoryConfig, repoName );
		String topic = resolveTopic( repositoryConfig );
		String branch = resolveBranch( workflowRun, repository );
		StringBuilder message = new StringBuilder(
				"**[%s](%s)** failed on `%s` in %s.".formatted(
						workflowRun.getName(),
						workflowRun.getHtmlUrl().toString(),
						branch,
						repoFullName
				) );
		appendFailingJobs( message, workflowRun );

		if ( deploymentConfig.isDryRun() ) {
			Log.infof( "Zulip notification (dry run) - channel: %s, topic: %s, message: %s",
					channel, topic, message.toString() );
			return;
		}

		try {
			zulipClient.sendMessage( "stream", channel, topic, message.toString() );
		}
		catch (RuntimeException e) {
			Log.errorf( e, "Failed to send Zulip notification for %s workflow run %s",
					repoFullName, workflowRun.getHtmlUrl() );
		}
	}

	private void appendFailingJobs(StringBuilder message, GHWorkflowRun workflowRun) {
		try {
			List<GHWorkflowJob> failingJobs = Streams.toStream( workflowRun.listJobs() )
					.filter( job -> job.getConclusion() == GHWorkflowRun.Conclusion.FAILURE )
					.toList();
			if ( !failingJobs.isEmpty() ) {
				message.append( "\nFailing jobs: " );
				for ( int i = 0; i < failingJobs.size(); i++ ) {
					if ( i > 0 ) {
						message.append( ", " );
					}
					GHWorkflowJob job = failingJobs.get( i );
					message.append( "**[%s](%s)**".formatted( job.getName(), job.getHtmlUrl().toString() ) );
				}
			}
		}
		catch (Exception e) {
			Log.warnf( e, "Failed to retrieve failing jobs for workflow run %s", workflowRun.getJobsUrl() );
		}
	}

	private String resolveBranch(GHWorkflowRun workflowRun, GHRepository repository) throws IOException {
		if ( workflowRun.getEvent() != GHEvent.WORKFLOW_RUN ) {
			return workflowRun.getHeadBranch();
		}
		// For workflow_run-triggered runs, head_branch is always the default branch.
		// Find the triggering run (same head_sha, different event type) to get the actual branch.
		try {
			Optional<GHWorkflowRun> triggeringRun = GitHubApiUtil.toStream( repository.queryWorkflowRuns()
					.headSha( workflowRun.getHeadSha() )
					.list() )
					.filter( run -> run.getEvent() != GHEvent.WORKFLOW_RUN )
					.findFirst();
			if ( triggeringRun.isPresent() ) {
				return triggeringRun.get().getHeadBranch();
			}
		}
		catch (Exception e) {
			Log.warnf( e, "Failed to find triggering workflow run for %s, falling back to head branch",
					workflowRun.getHtmlUrl() );
		}
		return workflowRun.getHeadBranch();
	}

	private String resolveChannel(RepositoryConfig repositoryConfig, String repoName) {
		if ( repositoryConfig != null && repositoryConfig.zulipNotification != null
				&& repositoryConfig.zulipNotification.channel.isPresent() ) {
			return repositoryConfig.zulipNotification.channel.get();
		}
		return REPO_TO_CHANNEL.getOrDefault( repoName, DEFAULT_CHANNEL );
	}

	private String resolveTopic(RepositoryConfig repositoryConfig) {
		if ( repositoryConfig != null && repositoryConfig.zulipNotification != null
				&& repositoryConfig.zulipNotification.topic.isPresent() ) {
			return repositoryConfig.zulipNotification.topic.get();
		}
		return DEFAULT_TOPIC;
	}
}

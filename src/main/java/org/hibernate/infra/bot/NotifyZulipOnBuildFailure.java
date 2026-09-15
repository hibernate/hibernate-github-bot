package org.hibernate.infra.bot;

import java.io.IOException;

import jakarta.inject.Inject;

import org.hibernate.infra.bot.config.DeploymentConfig;
import org.hibernate.infra.bot.config.Feature;
import org.hibernate.infra.bot.config.RepositoryConfig;
import org.hibernate.infra.bot.zulip.ZulipClient;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.WorkflowRun;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHWorkflowRun;

public class NotifyZulipOnBuildFailure {

	private static final String DEFAULT_TOPIC = "GitHub workflow failures";

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

		String repoFullName = payload.getRepository().getFullName();
		String repoName = payload.getRepository().getName();
		String channel = resolveChannel( repositoryConfig, repoName );
		String topic = resolveTopic( repositoryConfig );
		String message = "**%s** failed on `%s` in [%s](%s).".formatted(
				workflowRun.getName(),
				workflowRun.getHeadBranch(),
				repoFullName,
				workflowRun.getHtmlUrl().toString()
		);

		if ( deploymentConfig.isDryRun() ) {
			Log.infof( "Zulip notification (dry run) - channel: %s, topic: %s, message: %s",
					channel, topic, message );
			return;
		}

		try {
			zulipClient.sendMessage( "stream", channel, topic, message );
		}
		catch (RuntimeException e) {
			Log.errorf( e, "Failed to send Zulip notification for %s workflow run %s",
					repoFullName, workflowRun.getHtmlUrl() );
		}
	}

	private String resolveChannel(RepositoryConfig repositoryConfig, String repoName) {
		if ( repositoryConfig.zulipNotification != null
				&& repositoryConfig.zulipNotification.channel.isPresent() ) {
			return repositoryConfig.zulipNotification.channel.get();
		}
		return repoName + "-dev";
	}

	private String resolveTopic(RepositoryConfig repositoryConfig) {
		if ( repositoryConfig.zulipNotification != null
				&& repositoryConfig.zulipNotification.topic.isPresent() ) {
			return repositoryConfig.zulipNotification.topic.get();
		}
		return DEFAULT_TOPIC;
	}
}

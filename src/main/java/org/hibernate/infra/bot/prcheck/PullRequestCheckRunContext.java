package org.hibernate.infra.bot.prcheck;

import org.hibernate.infra.bot.config.DeploymentConfig;
import org.hibernate.infra.bot.config.RepositoryConfig;

import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public final class PullRequestCheckRunContext {

	public final DeploymentConfig deploymentConfig;
	public final GitHub gitHub;
	public final GHRepository repository;
	public final GHPullRequest pullRequest;
	public final RepositoryConfig repositoryConfig;

	public PullRequestCheckRunContext(DeploymentConfig deploymentConfig, GitHub gitHub, GHRepository repository,
                                      RepositoryConfig repositoryConfig, GHPullRequest pullRequest) {
		this.deploymentConfig = deploymentConfig;
		this.gitHub = gitHub;
		this.repository = repository;
		this.repositoryConfig = repositoryConfig;
		this.pullRequest = pullRequest;
	}

}

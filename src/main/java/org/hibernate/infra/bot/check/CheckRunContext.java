package org.hibernate.infra.bot.check;

import org.hibernate.infra.bot.config.DeploymentConfig;
import org.hibernate.infra.bot.config.RepositoryConfig;

import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

public final class CheckRunContext {

	public final DeploymentConfig deploymentConfig;
	public final GHRepository repository;
	public final GHPullRequest pullRequest;
	public final RepositoryConfig repositoryConfig;

	public CheckRunContext(DeploymentConfig deploymentConfig, GHRepository repository,
			RepositoryConfig repositoryConfig, GHPullRequest pullRequest) {
		this.deploymentConfig = deploymentConfig;
		this.repository = repository;
		this.repositoryConfig = repositoryConfig;
		this.pullRequest = pullRequest;
	}

}

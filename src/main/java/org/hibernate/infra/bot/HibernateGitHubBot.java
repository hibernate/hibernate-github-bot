package org.hibernate.infra.bot;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.hibernate.infra.bot.config.DeploymentConfig;

import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;

public class HibernateGitHubBot {

	private static final Logger LOG = Logger.getLogger( HibernateGitHubBot.class );

	@Inject
	DeploymentConfig deploymentConfig;

	void init(@Observes StartupEvent startupEvent) {
		if ( deploymentConfig.isDryRun() ) {
			LOG.warn( "››› Hibernate GitHub Bot running in dry-run mode" );
		}
	}
}

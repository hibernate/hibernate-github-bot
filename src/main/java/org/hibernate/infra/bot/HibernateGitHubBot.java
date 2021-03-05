/*
 *
 *  * Hibernate Search, full-text search for your domain model
 *  *
 *  * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 *  * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 *
 */
package org.hibernate.infra.bot;

import javax.enterprise.event.Observes;
import javax.inject.Inject;

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

/*
 *
 *  * Hibernate Search, full-text search for your domain model
 *  *
 *  * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 *  * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 *
 */
package org.hibernate.infra.bot.config;

import java.util.Optional;

import io.quarkus.arc.config.ConfigProperties;

@ConfigProperties
public class DeploymentConfig {

	Optional<Boolean> dryRun;

	public void setDryRun(Optional<Boolean> dryRun) {
		this.dryRun = dryRun;
	}

	public boolean isDryRun() {
		return dryRun.isPresent() && dryRun.get();
	}
}

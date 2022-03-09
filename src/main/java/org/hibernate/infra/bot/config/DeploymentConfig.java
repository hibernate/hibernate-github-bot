package org.hibernate.infra.bot.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "hibernate-github-bot")
public interface DeploymentConfig {

	Optional<Boolean> dryRun();

	public default boolean isDryRun() {
		Optional<Boolean> dryRun = dryRun();
		return dryRun.isPresent() && dryRun.get();
	}
}

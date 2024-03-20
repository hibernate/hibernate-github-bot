package org.hibernate.infra.bot.config;

import java.net.URI;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "hibernate-github-bot")
public interface DeploymentConfig {

	Optional<Boolean> dryRun();

	Develocity develocity();

	Jenkins jenkins();

	default boolean isDryRun() {
		Optional<Boolean> dryRun = dryRun();
		return dryRun.isPresent() && dryRun.get();
	}

	interface Develocity {
		URI uri();
		String accessKey();
	}

	interface Jenkins {
		long githubAppId();
	}
}

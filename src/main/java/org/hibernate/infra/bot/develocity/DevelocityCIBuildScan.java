package org.hibernate.infra.bot.develocity;

import java.net.URI;
import java.util.List;

public record DevelocityCIBuildScan(String provider, String jobOrWorkflow, String stage,
									Long availableAt, List<String> tags, List<String> goals,
									Status status, Status testStatus,
									URI buildScan, URI tests, URI logs) {
	public enum Status {
		SUCCESS,
		FAILURE
	}
}

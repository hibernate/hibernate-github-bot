package org.hibernate.infra.bot.develocity;

import java.net.URI;
import java.util.Comparator;
import java.util.List;

public record DevelocityCIBuildScan(String provider, String hostname,
									String jobOrWorkflow, URI jobOrWorkflowUri, String stage,
									Long availableAt, List<String> tags, List<String> goals,
									Status status, Status testStatus,
									URI buildScanUri, URI failuresUri, URI testsUri, URI logsUri) {

	public static final Comparator<? super DevelocityCIBuildScan> COMPARATOR =
			Comparator.comparing( DevelocityCIBuildScan::provider )
					.thenComparing( DevelocityCIBuildScan::jobOrWorkflow )
					// Ideally we'd sort by "job run ID", but we don't have that information.
					// Since each host only runs one job at any given time,
					// we approximate it by sorting by hostname then date.
					.thenComparing( DevelocityCIBuildScan::hostname )
					.thenComparing( DevelocityCIBuildScan::availableAt );

	public enum Status {
		SUCCESS,
		FAILURE
	}
}

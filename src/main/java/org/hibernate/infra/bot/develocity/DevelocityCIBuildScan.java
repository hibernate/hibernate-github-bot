package org.hibernate.infra.bot.develocity;

import java.net.URI;
import java.util.Comparator;
import java.util.List;

public record DevelocityCIBuildScan(String provider, String jobOrWorkflow, String stage,
									Long availableAt, List<String> tags, List<String> goals,
									Status status, Status testStatus,
									URI buildScanUri, URI failuresUri, URI testsUri, URI logsUri) {

	public static final Comparator<? super DevelocityCIBuildScan> COMPARATOR =
			Comparator.comparing( DevelocityCIBuildScan::provider )
					.thenComparing( DevelocityCIBuildScan::jobOrWorkflow )
					.thenComparing( DevelocityCIBuildScan::stage )
					.thenComparing( DevelocityCIBuildScan::availableAt );

	public enum Status {
		SUCCESS,
		FAILURE
	}
}

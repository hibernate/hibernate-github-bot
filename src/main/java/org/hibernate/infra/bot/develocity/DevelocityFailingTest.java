package org.hibernate.infra.bot.develocity;

import java.util.List;

import com.gradle.develocity.model.TestOutcomeDistribution;

public record DevelocityFailingTest(String name,
									List<DevelocityCIBuildScan> failingScans,
									boolean historyChecked,
									TestOutcomeDistribution historyDistribution,
									boolean failsOutsideThisRun) {

	public String shortName() {
		int lastDot = name.lastIndexOf( '.' );
		return lastDot >= 0 ? name.substring( lastDot + 1 ) : name;
	}
}

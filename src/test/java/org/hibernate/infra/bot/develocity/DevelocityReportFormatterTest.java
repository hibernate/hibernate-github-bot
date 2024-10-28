/*
 *
 *  * Hibernate Search, full-text search for your domain model
 *  *
 *  * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 *  * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 *
 */
package org.hibernate.infra.bot.develocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.hibernate.infra.bot.config.RepositoryConfig;
import org.hibernate.infra.bot.util.Patterns;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DevelocityReportFormatterTest {

	@Inject
	DevelocityReportFormatter formatter;

	@BeforeEach
	void setup() {
		Instant now = LocalDateTime.of( 2024, 10, 28, 0, 0 ).toInstant( ZoneOffset.UTC );
		var clockMock = Clock.fixed( now, ZoneOffset.UTC );
		QuarkusMock.installMockForType( clockMock, Clock.class );
	}

	@Test
	void summary_simple() {
		var buildScans = List.of(
				buildScanStub( "GitHub", "GitHub Actions Build", "",
						List.of( "Linux", "hibernate-search", "jdk-17", "main" ),
						List.of( "clean install" ),
						DevelocityCIBuildScan.Status.SUCCESS,
						DevelocityCIBuildScan.Status.SUCCESS ),
				buildScanStub( "GitHub", "GitHub Actions Build", "",
						List.of( "Linux", "elasticsearch", "elasticsearch-8.13", "h2", "hibernate-search", "jdk-17", "lucene",
								"main" ),
						List.of( "clean install" ),
						DevelocityCIBuildScan.Status.SUCCESS,
						DevelocityCIBuildScan.Status.SUCCESS ),
				buildScanStub( "GitHub", "GitHub Actions Build", "",
						List.of( "Windows Server 2022", "hibernate-search", "jdk-17", "main" ),
						List.of( "clean install" ),
						DevelocityCIBuildScan.Status.SUCCESS,
						DevelocityCIBuildScan.Status.SUCCESS ),
				buildScanStub( "GitHub", "GitHub Actions Build", "",
						List.of( "Windows Server 2022", "h2", "hibernate-search", "jdk-17", "lucene", "main" ),
						List.of( "clean install" ),
						DevelocityCIBuildScan.Status.SUCCESS,
						DevelocityCIBuildScan.Status.SUCCESS ),
				buildScanStub( "Jenkins", "hibernate-search/PR-4125", "Default build",
						List.of( "Linux", "hibernate-search", "jdk-17", "PR-4125" ),
						List.of( "clean install" ),
						DevelocityCIBuildScan.Status.SUCCESS,
						DevelocityCIBuildScan.Status.SUCCESS ),
				buildScanStub( "Jenkins", "hibernate-search/PR-4125", "Default build",
						List.of( "Linux", "elasticsearch", "elasticsearch-8.13", "h2", "hibernate-search", "jdk-17", "lucene" ),
						List.of( "clean verify" ),
						DevelocityCIBuildScan.Status.FAILURE,
						DevelocityCIBuildScan.Status.FAILURE )
		);
		assertThat( formatter.summary( buildScans, new RepositoryConfig.Develocity.BuildScan() ) )
				.isEqualTo( """
						| Job/Workflow | Tags | Goals/Tasks | Results |
						| -- | -- | -- | :-: |
						|[GitHub Actions Build](https://ci.hibernate.org/job/hibernate-search/job/PR-4125/4/ "Build")\
						|`Linux` `hibernate-search` `jdk-17` `main`\
						|`clean install`\
						|[:green_circle:](https://ge.hibernate.org/s/45fv2rr67ofuy "Build Scan")\
						 [:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests "Tests")\
						 [:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log "Logs")\
						|
						|[GitHub Actions Build](https://ci.hibernate.org/job/hibernate-search/job/PR-4125/4/ "Build")\
						|`Linux` `elasticsearch` `elasticsearch-8.13` `h2` `hibernate-search` `jdk-17` `lucene` `main`\
						|`clean install`\
						|[:green_circle:](https://ge.hibernate.org/s/45fv2rr67ofuy "Build Scan")\
						 [:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests "Tests")\
						 [:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log "Logs")\
						|
						|[GitHub Actions Build](https://ci.hibernate.org/job/hibernate-search/job/PR-4125/4/ "Build")\
						|`Windows Server 2022` `hibernate-search` `jdk-17` `main`\
						|`clean install`\
						|[:green_circle:](https://ge.hibernate.org/s/45fv2rr67ofuy "Build Scan")\
						 [:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests "Tests")\
						 [:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log "Logs")\
						|
						|[GitHub Actions Build](https://ci.hibernate.org/job/hibernate-search/job/PR-4125/4/ "Build")\
						|`Windows Server 2022` `h2` `hibernate-search` `jdk-17` `lucene` `main`\
						|`clean install`\
						|[:green_circle:](https://ge.hibernate.org/s/45fv2rr67ofuy "Build Scan")\
						 [:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests "Tests")\
						 [:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log "Logs")\
						|
						|[hibernate-search/PR-4125](https://ci.hibernate.org/job/hibernate-search/job/PR-4125/4/ "Build")\
						|`Linux` `hibernate-search` `jdk-17` `PR-4125`\
						|`clean install`\
						|[:green_circle:](https://ge.hibernate.org/s/45fv2rr67ofuy "Build Scan")\
						 [:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests "Tests")\
						 [:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log "Logs")\
						|
						|[hibernate-search/PR-4125](https://ci.hibernate.org/job/hibernate-search/job/PR-4125/4/ "Build")\
						|`Linux` `elasticsearch` `elasticsearch-8.13` `h2` `hibernate-search` `jdk-17` `lucene`\
						|`clean verify`\
						|[:red_circle:](https://ge.hibernate.org/s/45fv2rr67ofuy/failures "Build Scan")\
						 [:x:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests?outcome=FAILED&outcome=FLAKY "Tests")\
						 [:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log "Logs")\
						|
						""" );
	}

	@Test
	void summary_tagsConfig() {
		var buildScans = List.of(
				buildScanStub( "GitHub", "GitHub Actions Build", "",
						List.of( "Linux", "hibernate-search", "jdk-17", "main" ),
						List.of( "clean install" ),
						DevelocityCIBuildScan.Status.SUCCESS,
						DevelocityCIBuildScan.Status.SUCCESS ),
				buildScanStub( "GitHub", "GitHub Actions Build", "",
						List.of( "Linux", "elasticsearch", "elasticsearch-8.13", "h2", "hibernate-search", "jdk-17", "lucene",
								"main" ),
						List.of( "clean install" ),
						DevelocityCIBuildScan.Status.SUCCESS,
						DevelocityCIBuildScan.Status.SUCCESS ),
				buildScanStub( "GitHub", "GitHub Actions Build", "",
						List.of( "Windows Server 2022", "hibernate-search", "jdk-17", "main" ),
						List.of( "clean install" ),
						DevelocityCIBuildScan.Status.SUCCESS,
						DevelocityCIBuildScan.Status.SUCCESS ),
				buildScanStub( "GitHub", "GitHub Actions Build", "",
						List.of( "Windows Server 2022", "h2", "hibernate-search", "jdk-17", "lucene", "main" ),
						List.of( "clean install" ),
						DevelocityCIBuildScan.Status.SUCCESS,
						DevelocityCIBuildScan.Status.SUCCESS ),
				buildScanStub( "Jenkins", "hibernate-search/PR-4125", "Default build",
						List.of( "Linux", "hibernate-search", "jdk-17", "PR-4125" ),
						List.of( "clean install" ),
						DevelocityCIBuildScan.Status.SUCCESS,
						DevelocityCIBuildScan.Status.SUCCESS ),
				buildScanStub( "Jenkins", "hibernate-search/PR-4125", "Default build",
						List.of( "Linux", "elasticsearch", "elasticsearch-8.13", "h2", "hibernate-search", "jdk-17", "lucene" ),
						List.of( "clean verify" ),
						DevelocityCIBuildScan.Status.FAILURE,
						DevelocityCIBuildScan.Status.FAILURE )
		);
		assertThat( formatter.summary( buildScans, new RepositoryConfig.Develocity.BuildScan(
				true,
				List.of(
						new RepositoryConfig.Develocity.ColumnRule(
								"OS",
								Patterns.compile( "Linux" ),
								Optional.empty()
						),
						new RepositoryConfig.Develocity.ColumnRule(
								"OS",
								Patterns.compile( "Windows.*" ),
								Optional.of( "Windows" )
						),
						new RepositoryConfig.Develocity.ColumnRule(
								"Java",
								Patterns.compile( "jdk-(.*)" ),
								Optional.of( "$1" )
						),
						new RepositoryConfig.Develocity.ColumnRule(
								"Backend",
								Patterns.compile( "elasticsearch-(.*)" ),
								Optional.of( "es-$1" )
						),
						new RepositoryConfig.Develocity.ColumnRule(
								"Backend",
								Patterns.compile( "lucene" ),
								Optional.empty()
						),
						new RepositoryConfig.Develocity.ColumnRule(
								"DB",
								Patterns.compile( "h2|postgres" ),
								Optional.of( "$0" )
						),
						new RepositoryConfig.Develocity.ColumnRule(
								"EmptyColumn",
								Patterns.compile( "neverMatches" ),
								Optional.empty()
						),
						// Remove some tags
						new RepositoryConfig.Develocity.ColumnRule(
								null,
								Patterns.compile( "hibernate.search|elasticsearch|main|\\d+.\\d+|PR-\\d+" ),
								Optional.of( "" )
						)
				)
		) ) )
				.isEqualTo( """
						| Job/Workflow | OS | Java | Backend | DB | Goals/Tasks | Results |
						| -- | -- | -- | -- | -- | -- | :-: |
						|[GitHub Actions Build](https://ci.hibernate.org/job/hibernate-search/job/PR-4125/4/ "Build")\
						|`Linux`|`17`||\
						|`clean install`\
						|[:green_circle:](https://ge.hibernate.org/s/45fv2rr67ofuy "Build Scan")\
						 [:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests "Tests")\
						 [:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log "Logs")\
						|
						|[GitHub Actions Build](https://ci.hibernate.org/job/hibernate-search/job/PR-4125/4/ "Build")\
						|`Linux`|`17`|`es-8.13` `lucene`|`h2`\
						|`clean install`\
						|[:green_circle:](https://ge.hibernate.org/s/45fv2rr67ofuy "Build Scan")\
						 [:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests "Tests")\
						 [:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log "Logs")\
						|
						|[GitHub Actions Build](https://ci.hibernate.org/job/hibernate-search/job/PR-4125/4/ "Build")\
						|`Windows`|`17`||\
						|`clean install`\
						|[:green_circle:](https://ge.hibernate.org/s/45fv2rr67ofuy "Build Scan")\
						 [:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests "Tests")\
						 [:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log "Logs")\
						|
						|[GitHub Actions Build](https://ci.hibernate.org/job/hibernate-search/job/PR-4125/4/ "Build")\
						|`Windows`|`17`|`lucene`|`h2`\
						|`clean install`\
						|[:green_circle:](https://ge.hibernate.org/s/45fv2rr67ofuy "Build Scan")\
						 [:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests "Tests")\
						 [:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log "Logs")\
						|
						|[hibernate-search/PR-4125](https://ci.hibernate.org/job/hibernate-search/job/PR-4125/4/ "Build")\
						|`Linux`|`17`||\
						|`clean install`\
						|[:green_circle:](https://ge.hibernate.org/s/45fv2rr67ofuy "Build Scan")\
						 [:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests "Tests")\
						 [:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log "Logs")\
						|
						|[hibernate-search/PR-4125](https://ci.hibernate.org/job/hibernate-search/job/PR-4125/4/ "Build")\
						|`Linux`|`17`|`es-8.13` `lucene`|`h2`\
						|`clean verify`\
						|[:red_circle:](https://ge.hibernate.org/s/45fv2rr67ofuy/failures "Build Scan")\
						 [:x:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests?outcome=FAILED&outcome=FLAKY "Tests")\
						 [:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log "Logs")\
						|
								""" );
	}

	private DevelocityCIBuildScan buildScanStub(String provider, String jobOrWorkflow, String stage, List<String> tags,
			List<String> goals, DevelocityCIBuildScan.Status status, DevelocityCIBuildScan.Status testStatus) {
		return new DevelocityCIBuildScan( provider, "hostname does not matter (not rendered)", jobOrWorkflow,
				URI.create( "https://ci.hibernate.org/job/hibernate-search/job/PR-4125/4/" ),
				stage, 0L,
				tags, goals, status, testStatus,
				URI.create( "https://ge.hibernate.org/s/45fv2rr67ofuy" ),
				URI.create( "https://ge.hibernate.org/s/45fv2rr67ofuy/failures" ),
				URI.create( "https://ge.hibernate.org/s/45fv2rr67ofuy/tests" ),
				URI.create( "https://ge.hibernate.org/s/45fv2rr67ofuy/console-log" ) );
	}

	@Test
	void footer_simple() {
		assertThat( formatter.footer(
				"(value:\"CI job=hibernate-orm-pipeline/PR-9171\" and value:\"CI build number=1\") or (value:\"CI run=11552691889\")",
				false ) )
				.isEqualTo(
						"""


								Didn't find your build? Try [this search query](https://ge.hibernate.org/scans?search.query=%28value%3A%22CI+job%3Dhibernate-orm-pipeline%2FPR-9171%22+and+value%3A%22CI+build+number%3D1%22%29+or+%28value%3A%22CI+run%3D11552691889%22%29&search.startTimeMin=1&search.startTimeMax=1761609600000&search.timeZoneId=UTC)
								""" );
	}

	@Test
	void footer_debug() {
		assertThat( formatter.footer(
				"(value:\"CI job=hibernate-orm-pipeline/PR-9171\" and value:\"CI build number=1\") or (value:\"CI run=11552691889\")",
				true ) )
				.isEqualTo(
						"""


								Didn't find your build? Try [this search query](https://ge.hibernate.org/scans?search.query=%28value%3A%22CI+job%3Dhibernate-orm-pipeline%2FPR-9171%22+and+value%3A%22CI+build+number%3D1%22%29+or+%28value%3A%22CI+run%3D11552691889%22%29&search.startTimeMin=1&search.startTimeMax=1761609600000&search.timeZoneId=UTC)

								Full query sent to Develocity:

								```
								(
								value:"CI job=hibernate-orm-pipeline/PR-9171" and value:"CI build number=1"
								) or (
								value:"CI run=11552691889"
								)
								```
								""" );
	}
}

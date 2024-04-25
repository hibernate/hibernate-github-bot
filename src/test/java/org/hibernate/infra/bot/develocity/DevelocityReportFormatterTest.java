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
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DevelocityReportFormatterTest {

	@Inject
	DevelocityReportFormatter formatter;

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
		assertThat( formatter.summary( buildScans ) )
				.isEqualTo( """
						| Status | Job/Workflow | Tags | Goals | Build Scan | Tests | Logs |
						| :-: | -- | -- | -- | :-: | :-: | :-: |
						|[:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy)\
						|`GitHub Actions Build`\
						|`Linux` `hibernate-search` `jdk-17` `main`\
						|`clean install`\
						|[:mag:](https://ge.hibernate.org/s/45fv2rr67ofuy)\
						|[:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests)\
						|[:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log)\
						|
						|[:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy)\
						|`GitHub Actions Build`\
						|`Linux` `elasticsearch` `elasticsearch-8.13` `h2` `hibernate-search` `jdk-17` `lucene` `main`\
						|`clean install`\
						|[:mag:](https://ge.hibernate.org/s/45fv2rr67ofuy)\
						|[:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests)\
						|[:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log)\
						|
						|[:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy)\
						|`GitHub Actions Build`\
						|`Windows Server 2022` `hibernate-search` `jdk-17` `main`\
						|`clean install`\
						|[:mag:](https://ge.hibernate.org/s/45fv2rr67ofuy)\
						|[:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests)\
						|[:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log)\
						|
						|[:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy)\
						|`GitHub Actions Build`\
						|`Windows Server 2022` `h2` `hibernate-search` `jdk-17` `lucene` `main`\
						|`clean install`\
						|[:mag:](https://ge.hibernate.org/s/45fv2rr67ofuy)\
						|[:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests)\
						|[:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log)\
						|
						|[:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy)\
						|`hibernate-search/PR-4125 Default build`\
						|`Linux` `hibernate-search` `jdk-17` `PR-4125`\
						|`clean install`\
						|[:mag:](https://ge.hibernate.org/s/45fv2rr67ofuy)\
						|[:white_check_mark:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests)\
						|[:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log)\
						|
						|[:x:](https://ge.hibernate.org/s/45fv2rr67ofuy/failures)\
						|`hibernate-search/PR-4125 Default build`\
						|`Linux` `elasticsearch` `elasticsearch-8.13` `h2` `hibernate-search` `jdk-17` `lucene`\
						|`clean verify`\
						|[:mag:](https://ge.hibernate.org/s/45fv2rr67ofuy)\
						|[:x:](https://ge.hibernate.org/s/45fv2rr67ofuy/tests)\
						|[:page_with_curl:](https://ge.hibernate.org/s/45fv2rr67ofuy/console-log)\
						|
						""" );
	}

	private DevelocityCIBuildScan buildScanStub(String provider, String jobOrWorkflow, String stage, List<String> tags,
			List<String> goals, DevelocityCIBuildScan.Status status, DevelocityCIBuildScan.Status testStatus) {
		return new DevelocityCIBuildScan( provider, jobOrWorkflow, stage, 0L,
				tags, goals, status, testStatus,
				URI.create( "https://ge.hibernate.org/s/45fv2rr67ofuy" ),
				URI.create( "https://ge.hibernate.org/s/45fv2rr67ofuy/failures" ),
				URI.create( "https://ge.hibernate.org/s/45fv2rr67ofuy/tests" ),
				URI.create( "https://ge.hibernate.org/s/45fv2rr67ofuy/console-log" ) );
	}

}

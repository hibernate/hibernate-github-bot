package org.hibernate.infra.bot.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;

import org.junit.jupiter.api.Test;

class GitHubActionsRunIdTest {

	@Test
	void actionsRunsFormat() throws Exception {
		var url = new URL( "https://github.com/hibernate/hibernate-orm/actions/runs/12345678901/" );
		assertThat( GitHubActionsRunId.parse( url ).run() ).isEqualTo( 12345678901L );
	}

	@Test
	void actionsRunsFormatNoTrailingSlash() throws Exception {
		var url = new URL( "https://github.com/hibernate/hibernate-orm/actions/runs/12345678901" );
		assertThat( GitHubActionsRunId.parse( url ).run() ).isEqualTo( 12345678901L );
	}

	@Test
	void runsFormat() throws Exception {
		var url = new URL( "https://github.com/hibernate/hibernate-orm/runs/106274028198" );
		assertThat( GitHubActionsRunId.parse( url ).run() ).isEqualTo( 106274028198L );
	}

	@Test
	void actionsRunsWithJobId() throws Exception {
		var url = new URL(
				"https://github.com/hibernate/hibernate-orm/actions/runs/27276276443/job/80558394108" );
		var parsed = GitHubActionsRunId.parse( url );
		assertThat( parsed.run() ).isEqualTo( 27276276443L );
		assertThat( parsed.job() ).isPresent().hasValue( 80558394108L );
	}

	@Test
	void actionsRunsWithAttemptButNoJobId() throws Exception {
		var url = new URL(
				"https://github.com/hibernate/hibernate-orm/actions/runs/39791269270/attempts/2" );
		var parsed = GitHubActionsRunId.parse( url );
		assertThat( parsed.run() ).isEqualTo( 39791269270L );
		assertThat( parsed.job() ).isEmpty();
	}

	@Test
	void actionsRunsNoJobId() throws Exception {
		var url = new URL( "https://github.com/hibernate/hibernate-orm/actions/runs/12345678901" );
		var parsed = GitHubActionsRunId.parse( url );
		assertThat( parsed.run() ).isEqualTo( 12345678901L );
		assertThat( parsed.job() ).isEmpty();
	}

	@Test
	void invalidFormat() throws Exception {
		var url = new URL( "https://github.com/hibernate/hibernate-orm/pull/123" );
		assertThatThrownBy( () -> GitHubActionsRunId.parse( url ) )
				.isInstanceOf( IllegalArgumentException.class );
	}
}

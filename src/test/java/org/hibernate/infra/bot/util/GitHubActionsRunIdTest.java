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
	void invalidFormat() throws Exception {
		var url = new URL( "https://github.com/hibernate/hibernate-orm/pull/123" );
		assertThatThrownBy( () -> GitHubActionsRunId.parse( url ) )
				.isInstanceOf( IllegalArgumentException.class );
	}
}

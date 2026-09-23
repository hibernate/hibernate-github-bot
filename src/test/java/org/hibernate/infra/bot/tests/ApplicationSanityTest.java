package org.hibernate.infra.bot.tests;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.infra.bot.CheckPullRequestContributionRules;
import org.hibernate.infra.bot.EditPullRequestBody;

import org.junit.jupiter.api.Test;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ApplicationSanityTest {

	@Test
	void checkApplicationIncludesCheckPullRequestContributionRules() {
		assertThat( Arc.container().instance( CheckPullRequestContributionRules.class ) ).isNotNull();
		assertThat( Arc.container().instance( EditPullRequestBody.class ) ).isNotNull();
	}

}

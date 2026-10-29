package org.hibernate.infra.bot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FeatureTest {

	@ParameterizedTest
	@EnumSource(value = Feature.class, names = { "CHECK_CONFIG_SYNTAX", "NOTIFY_ZULIP_ON_BUILD_FAILURE" })
	void noConfig_defaultOnFeature_enabled(Feature feature) {
		assertThat( feature.isEnabled( null ) ).isTrue();
	}

	@ParameterizedTest
	@EnumSource(value = Feature.class, names = {
			"ALL",
			"CHECK_CONTRIBUTION_RULES",
			"EDIT_PULL_REQUEST_ADD_BRANCH_TAG_LABEL",
			"EDIT_PULL_REQUEST_BODY_ADD_ISSUE_LINKS",
			"EDIT_PULL_REQUEST_BODY_ADD_TASK_LIST",
			"EXTRACT_DEVELOCITY_BUILD_SCANS"
	})
	void noConfig_optInFeature_disabled(Feature feature) {
		assertThat( feature.isEnabled( null ) ).isFalse();
	}

	@Test
	void configWithAllFeatures_allEnabled() {
		RepositoryConfig config = new RepositoryConfig();
		config.features = Set.of( Feature.ALL );
		for ( Feature feature : Feature.values() ) {
			if ( feature == Feature.ALL ) {
				continue;
			}
			assertThat( feature.isEnabled( config ) )
					.as( "Feature %s should be enabled when ALL is listed", feature )
					.isTrue();
		}
	}

	@Test
	void configWithSpecificFeature_onlyThatFeatureEnabled() {
		RepositoryConfig config = new RepositoryConfig();
		config.features = Set.of( Feature.CHECK_CONTRIBUTION_RULES );
		assertThat( Feature.CHECK_CONTRIBUTION_RULES.isEnabled( config ) ).isTrue();
		assertThat( Feature.NOTIFY_ZULIP_ON_BUILD_FAILURE.isEnabled( config ) ).isFalse();
		assertThat( Feature.CHECK_CONFIG_SYNTAX.isEnabled( config ) ).isFalse();
	}

	@Test
	void configWithNoFeaturesField_allEnabled() {
		RepositoryConfig config = new RepositoryConfig();
		// features field is null by default — means "everything enabled"
		for ( Feature feature : Feature.values() ) {
			if ( feature == Feature.ALL ) {
				continue;
			}
			assertThat( feature.isEnabled( config ) )
					.as( "Feature %s should be enabled when features field is null", feature )
					.isTrue();
		}
	}

	@Test
	void configWithEmptyFeatureSet_noneEnabled() {
		RepositoryConfig config = new RepositoryConfig();
		config.features = Set.of();
		for ( Feature feature : Feature.values() ) {
			if ( feature == Feature.ALL ) {
				continue;
			}
			assertThat( feature.isEnabled( config ) )
					.as( "Feature %s should be disabled when features set is empty", feature )
					.isFalse();
		}
	}
}

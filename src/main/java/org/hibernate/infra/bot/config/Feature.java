package org.hibernate.infra.bot.config;

public enum Feature {

	ALL( false ),
	CHECK_CONTRIBUTION_RULES( false ),
	CHECK_CONFIG_SYNTAX( true ),
	EDIT_PULL_REQUEST_ADD_BRANCH_TAG_LABEL( false ),
	EDIT_PULL_REQUEST_BODY_ADD_ISSUE_LINKS( false ),
	EDIT_PULL_REQUEST_BODY_ADD_TASK_LIST( false ),
	EXTRACT_DEVELOCITY_BUILD_SCANS( false ),
	NOTIFY_ZULIP_ON_BUILD_FAILURE( true );

	public final boolean enabledByDefault;

	Feature(boolean enabledByDefault) {
		this.enabledByDefault = enabledByDefault;
	}

	public boolean isEnabled(RepositoryConfig repositoryConfig) {
		if ( repositoryConfig == null ) {
			return enabledByDefault;
		}
		return repositoryConfig.isFeatureEnabled( this );
	}
}

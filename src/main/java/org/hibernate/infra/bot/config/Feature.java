package org.hibernate.infra.bot.config;

public enum Feature {

	ALL,
	CHECK_CONTRIBUTION_RULES,
	CHECK_CONFIG_SYNTAX,
	EDIT_PULL_REQUEST_ADD_BRANCH_TAG_LABEL,
	EDIT_PULL_REQUEST_BODY_ADD_ISSUE_LINKS,
	EDIT_PULL_REQUEST_BODY_ADD_TASK_LIST,
	EXTRACT_DEVELOCITY_BUILD_SCANS,
	NOTIFY_ZULIP_ON_BUILD_FAILURE;

	public boolean isEnabled(RepositoryConfig repositoryConfig) {
		if ( repositoryConfig == null ) {
			return false;
		}
		return repositoryConfig.isFeatureEnabled( this );
	}
}

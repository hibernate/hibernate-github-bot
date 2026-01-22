package org.hibernate.infra.bot.prcheck;

public final class PullRequestCheckRunRule {

	final String description;

	boolean passed;
    boolean includeComment;
	String commentOrNull;

	public PullRequestCheckRunRule(String description, boolean includeComment) {
		this.description = description;
        this.includeComment = includeComment;
	}

	public void result(boolean passed) {
		result( passed, null );
	}

	public void result(boolean passed, String commentOrNull) {
		this.passed = passed;
		this.commentOrNull = commentOrNull;
	}

	public void passed() {
		this.passed = true;
		this.commentOrNull = null;
	}

	public void failed(String commentOrNull) {
		this.passed = false;
		this.commentOrNull = commentOrNull;
	}
}

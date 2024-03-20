package org.hibernate.infra.bot.prcheck;

public final class PullRequestCheckRunRule {

	final String description;

	boolean passed;
	String commentOrNull;

	public PullRequestCheckRunRule(String description) {
		this.description = description;
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

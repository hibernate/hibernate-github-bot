package org.hibernate.infra.bot.prcheck;

import java.io.IOException;

public abstract class PullRequestCheck {

	public static PullRequestCheckRunOutput run(PullRequestCheckRunContext context, PullRequestCheck check) throws IOException {
		PullRequestCheckRun run = PullRequestCheckRun.create( context, check );
		return run.perform();
	}

	final String name;

	protected PullRequestCheck(String name) {
		this.name = name;
	}

	public abstract void perform(PullRequestCheckRunContext context, PullRequestCheckRunOutput output) throws IOException;
}

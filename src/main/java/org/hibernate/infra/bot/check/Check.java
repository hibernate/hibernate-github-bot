package org.hibernate.infra.bot.check;

import java.io.IOException;

public abstract class Check {

	public static CheckRunOutput run(CheckRunContext context, Check check) throws IOException {
		CheckRun run = CheckRun.create( context, check );
		return run.perform();
	}

	final String name;

	protected Check(String name) {
		this.name = name;
	}

	public abstract void perform(CheckRunContext context, CheckRunOutput output) throws IOException;
}

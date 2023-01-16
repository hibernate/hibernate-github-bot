package org.hibernate.infra.bot.check;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;

import org.jboss.logging.Logger;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRunBuilder;

public final class CheckRun {

	private static final Logger LOG = Logger.getLogger( CheckRun.class );

	static CheckRun create(CheckRunContext context, Check check) throws IOException {
		if ( !context.deploymentConfig.isDryRun() ) {
			GHCheckRun checkRun = context.repository.createCheckRun(
					check.name, context.pullRequest.getHead().getSha() )
					.withStartedAt( Date.from( Instant.now() ) )
					.withStatus( GHCheckRun.Status.IN_PROGRESS )
					.create();
			return new CheckRun( context, check, checkRun.getId() );
		}
		else {
			LOG.info( "Pull request #" + context.pullRequest.getNumber() + " - Create check run '" + check.name + "'" );
			return new CheckRun( context, check, 42L );
		}
	}

	private final CheckRunContext context;
	private final Check check;
	public final long id;

	CheckRun(CheckRunContext context, Check check, long id) {
		this.context = context;
		this.check = check;
		this.id = id;
	}

	CheckRunOutput perform() throws IOException {
		CheckRunOutput output = new CheckRunOutput( id, check.name );

		check.perform( context, output );

		GHCheckRun.Conclusion conclusion;
		if ( output.passed() ) {
			conclusion = GHCheckRun.Conclusion.SUCCESS;
		}
		else {
			conclusion = GHCheckRun.Conclusion.FAILURE;
		}

		if ( !context.deploymentConfig.isDryRun() ) {
			context.repository.updateCheckRun( id )
					.withCompletedAt( Date.from( Instant.now() ) )
					.withStatus( GHCheckRun.Status.COMPLETED )
					.withConclusion( conclusion )
					.add( new GHCheckRunBuilder.Output( output.title(), output.summary() ) )
					.create();
		}
		else {
			LOG.info(
					"Pull request #" + context.pullRequest.getNumber() + " - Update check run '" + check.name
							+ "' with conclusion '" + conclusion + "'" );
		}

		return output;
	}
}

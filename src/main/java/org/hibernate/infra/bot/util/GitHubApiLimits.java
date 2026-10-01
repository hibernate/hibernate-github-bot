package org.hibernate.infra.bot.util;

import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

import org.kohsuke.github.GHException;

import io.quarkus.logging.Log;

// Adapted from https://github.com/quarkusio/quarkus-github-lottery
public final class GitHubApiLimits {

	private static final int MAX_RETRY = 3;

	private static final long RETRY_WAIT_MILLIS = Long.getLong(
			"github-api.retry-wait-millis",
			61 * 1000 );

	private static final long READ_THROTTLE_MILLIS = Long.getLong(
			"github-api.read-throttle-millis",
			200 );

	private GitHubApiLimits() {
	}

	public static <T> T executeWithRetry(Supplier<T> action) {
		RuntimeException rateLimitException = null;
		for ( int i = 0; i < MAX_RETRY; i++ ) {
			if ( rateLimitException != null ) {
				waitBeforeRetry();
			}
			try {
				return action.get();
			}
			catch (RuntimeException e) {
				if ( isSecondaryRateLimitReached( e ) ) {
					if ( rateLimitException == null ) {
						rateLimitException = e;
					}
					else {
						rateLimitException.addSuppressed( e );
					}
				}
				else {
					throw e;
				}
			}
		}
		throw rateLimitException;
	}

	private static boolean isSecondaryRateLimitReached(RuntimeException e) {
		return e instanceof GHException
				&& e.getCause() != null && e.getCause().getMessage().contains( "secondary rate limit" );
	}

	private static void waitBeforeRetry() {
		Log.infof( "GitHub API reached a secondary rate limit; waiting %s ms before retrying...", RETRY_WAIT_MILLIS );
		try {
			Thread.sleep( RETRY_WAIT_MILLIS );
		}
		catch (InterruptedException ex) {
			throw new UncheckedIOException( (InterruptedIOException) new InterruptedIOException().initCause( ex ) );
		}
	}

	public static void sleepForReadThrottling() {
		try {
			Thread.sleep( READ_THROTTLE_MILLIS );
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new UncheckedIOException( (InterruptedIOException) new InterruptedIOException().initCause( e ) );
		}
	}
}

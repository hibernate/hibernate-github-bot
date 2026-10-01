package org.hibernate.infra.bot.util;

import static org.hibernate.infra.bot.util.GitHubApiLimits.executeWithRetry;
import static org.hibernate.infra.bot.util.GitHubApiLimits.sleepForReadThrottling;

import java.util.Iterator;

import org.kohsuke.github.PagedIterator;

// Workaround for https://github.com/hub4j/github-api/issues/2009
// Adapted from https://github.com/quarkusio/quarkus-github-lottery
class RetryingIterator<T> implements Iterator<T> {

	private final PagedIterator<T> delegate;

	public RetryingIterator(PagedIterator<T> delegate) {
		this.delegate = delegate;
	}

	@Override
	public boolean hasNext() {
		return executeWithRetry( delegate::hasNext );
	}

	@Override
	public T next() {
		sleepForReadThrottling();
		return executeWithRetry( delegate::next );
	}
}

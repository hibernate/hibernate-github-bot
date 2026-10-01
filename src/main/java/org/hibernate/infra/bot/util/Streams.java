package org.hibernate.infra.bot.util;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.kohsuke.github.PagedIterable;

// Adapted from https://github.com/quarkusio/quarkus-github-lottery
public final class Streams {

	private Streams() {
	}

	public static <T> Stream<T> toStream(PagedIterable<T> iterable) {
		return StreamSupport.stream( spliterator( iterable ), false );
	}

	private static <T> Spliterator<T> spliterator(PagedIterable<T> iterable) {
		var pagedIterator = iterable.iterator();
		var workaroundIterator = new RetryingIterator<>( pagedIterator );
		return Spliterators.spliteratorUnknownSize( workaroundIterator, 0 );
	}
}

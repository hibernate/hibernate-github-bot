package org.hibernate.infra.bot.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.hrakaroo.glob.GlobPattern;
import com.hrakaroo.glob.MatchingEngine;
import io.quarkus.cache.CacheResult;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHRepository;

public final class GlobMatcher {

	private final List<MatchingEngine> matchingEngines = new ArrayList<>();

	public GlobMatcher(Collection<String> blogPatterns) {
		for ( String filenamePattern : blogPatterns ) {
			matchingEngines.add( compileGlob( filenamePattern ) );
			// Exclude files recursively
			matchingEngines.add( compileGlob( filenamePattern
					+ ( filenamePattern.endsWith( "/" ) ? "*" : "/*" ) ) );
		}
	}

	public boolean allFilesMatch(GHRepository repository, String sha)
			throws IOException {
		if ( matchingEngines.isEmpty() ) {
			return false;
		}

		GHCommit commit = repository.getCommit( sha );
		var files = commit.listFiles();
		for ( GHCommit.File file : files ) {
			boolean matched = false;
			for ( MatchingEngine matchingEngine : matchingEngines ) {
				try {
					if ( matchingEngine.matches( file.getFileName() ) ) {
						matched = true;
						break;
					}
				}
				catch (Exception e) {
					throw new IllegalStateException( "Error matching glob expression '" + matchingEngine
							+ "' against file '" + file.getFileName() + "': " + e.getMessage(), e );
				}
			}
			if ( !matched ) {
				return false;
			}
		}
		return true;
	}

	@CacheResult(cacheName = "glob-cache")
	static MatchingEngine compileGlob(String filenamePattern) {
		try {
			return GlobPattern.compile( filenamePattern );
		}
		catch (Exception e) {
			throw new IllegalStateException( "Error compiling glob expression '" + filenamePattern + "': "
					+ e.getMessage(), e );
		}
	}
}

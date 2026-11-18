package org.kohsuke.github;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GHCheckRunAnnotationItem {

	private String annotationLevel;
	private String title;
	private String message;

	public GHCheckRunAnnotationItem() {
	}

	public String getAnnotationLevel() {
		return annotationLevel;
	}

	public String getTitle() {
		return title;
	}

	public String getMessage() {
		return message;
	}

	public static List<GHCheckRunAnnotationItem> list(GHRepository repository, long checkRunId)
			throws IOException {
		GHCheckRunAnnotationItem[] result = repository.root().createRequest()
				.withUrlPath( "/repos/" + repository.getFullName() + "/check-runs/" + checkRunId + "/annotations" )
				.fetch( GHCheckRunAnnotationItem[].class );
		return result != null ? Arrays.asList( result ) : Collections.emptyList();
	}
}

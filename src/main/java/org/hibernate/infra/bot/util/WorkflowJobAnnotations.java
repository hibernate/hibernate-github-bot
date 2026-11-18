package org.hibernate.infra.bot.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.kohsuke.github.GHCheckRunAnnotationItem;
import org.kohsuke.github.GHRepository;

@ApplicationScoped
public class WorkflowJobAnnotations {

	public record Annotation(String title, String message) {
	}

	public List<Annotation> getAnnotations(GHRepository repository, long checkRunId) {
		try {
			return GHCheckRunAnnotationItem.list( repository, checkRunId )
					.stream()
					.filter( a -> "failure".equals( a.getAnnotationLevel() ) )
					.filter( a -> a.getTitle() != null && !a.getTitle().isBlank() )
					.map( a -> new Annotation( a.getTitle(), a.getMessage() ) )
					.toList();
		}
		catch (IOException e) {
			throw new UncheckedIOException( e );
		}
	}
}

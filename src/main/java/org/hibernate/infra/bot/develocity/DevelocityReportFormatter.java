package org.hibernate.infra.bot.develocity;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import org.hibernate.infra.bot.config.RepositoryConfig;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateExtension;
import io.quarkus.qute.TemplateInstance;

@ApplicationScoped
public class DevelocityReportFormatter {

	public String summary(List<DevelocityCIBuildScan> buildScans, RepositoryConfig.Develocity.BuildScan config) {
		Collection<TagColumn> tagColumns = extractTagColumns( buildScans, config );
		return Templates.summary( buildScans, tagColumns )
				.render();
	}

	public record TagColumn(String name, Map<DevelocityCIBuildScan, Set<String>> content) {
	}

	private Collection<TagColumn> extractTagColumns(List<DevelocityCIBuildScan> buildScans,
			RepositoryConfig.Develocity.BuildScan config) {
		Map<String, TagColumn> tagColumns = new LinkedHashMap<>();
		// Preserve the order in which groups are declared
		for ( RepositoryConfig.Develocity.ColumnRule rule : config.tags ) {
			tagColumns.put( rule.column, new TagColumn( rule.column, new HashMap<>() ) );
		}
		// Put the default groups last
		tagColumns.put( null, new TagColumn( null, new HashMap<>() ) );

		for ( DevelocityCIBuildScan buildScan : buildScans ) {
			for ( String tag : buildScan.tags() ) {
				boolean matched = false;
				for ( RepositoryConfig.Develocity.ColumnRule rule : config.tags ) {
					var matcher = rule.getPattern().matcher( tag );
					if ( matcher.matches() ) {
						matched = true;
						var addedTag = tag;
						if ( rule.replacement.isPresent() ) {
							addedTag = matcher.replaceAll( rule.replacement.get() );
						}
						if ( !addedTag.isBlank() ) {
							tagColumns.get( rule.column ).content()
									.computeIfAbsent( buildScan, ignored -> new TreeSet<>() )
									.add( addedTag );
						}
						break;
					}
				}
				if ( !matched ) {
					tagColumns.get( null ).content()
							// Use LinkedHashSet for the default column to preserve tag order
							.computeIfAbsent( buildScan, ignored -> new LinkedHashSet<>() )
							.add( tag );
				}
			}
		}

		// Remove empty columns
		for ( var it = tagColumns.entrySet().iterator(); it.hasNext(); ) {
			var column = it.next().getValue();
			if ( column.content.isEmpty() ) {
				it.remove();
			}
		}

		return tagColumns.values();
	}

	@CheckedTemplate
	private static class Templates {
		public static native TemplateInstance summary(List<DevelocityCIBuildScan> buildScans,
				Collection<TagColumn> tagColumns);
	}

	@TemplateExtension
	@SuppressWarnings("unused")
	private static class TemplateExtensions {

		static String backQuoted(String content) {
			return content == null || content.isBlank() ? null : '`' + content + '`';
		}

		static List<String> backQuoted(Collection<String> content) {
			return content.stream()
					.map( TemplateExtensions::backQuoted )
					.toList();
		}

		static String spaceDelimited(Collection<String> content) {
			return content.stream()
					.filter( s -> s != null && !s.isBlank() )
					.collect( Collectors.joining( " " ) );
		}

		static URI statusUri(DevelocityCIBuildScan buildScan) {
			return switch ( buildScan.status() ) {
				case SUCCESS -> buildScan.buildScanUri();
				case FAILURE -> buildScan.failuresUri();
			};
		}

		static String circleEmoji(DevelocityCIBuildScan.Status status) {
			return switch ( status ) {
				case SUCCESS -> ":green_circle:";
				case FAILURE -> ":red_circle:";
			};
		}

		static String checkEmoji(DevelocityCIBuildScan.Status status) {
			return switch ( status ) {
				case SUCCESS -> ":white_check_mark:";
				case FAILURE -> ":x:";
			};
		}
	}

	@TemplateExtension(namespace = "list")
	@SuppressWarnings("unused")
	private static class ListTemplateExtensions {
		static List<String> create(String... content) {
			return List.of( content );
		}
	}
}

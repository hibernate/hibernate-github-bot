package org.hibernate.infra.bot.develocity;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateExtension;
import io.quarkus.qute.TemplateInstance;

@ApplicationScoped
public class DevelocityReportFormatter {

	public String summary(List<DevelocityCIBuildScan> buildScans) {
		return Templates.summary( buildScans )
				.render();
	}

	@CheckedTemplate
	private static class Templates {
		public static native TemplateInstance summary(List<DevelocityCIBuildScan> buildScans);
	}

	@TemplateExtension
	@SuppressWarnings("unused")
	private static class TemplateExtensions {

		static String backQuoted(String content) {
			return content == null || content.isBlank() ? null : '`' + content + '`';
		}

		static List<String> backQuoted(List<String> content) {
			return content.stream()
					.map( TemplateExtensions::backQuoted )
					.toList();
		}

		static String spaceDelimited(List<String> content) {
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

		static String emoji(DevelocityCIBuildScan.Status status) {
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

package org.hibernate.infra.bot.jackson;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.quarkiverse.githubapp.runtime.UtilsProducer;
import io.quarkus.runtime.Startup;

@Singleton
@Startup
public class ObjectMapperCustomizer {

	@Inject
	public void addModules(ObjectMapper jsonMapper, @UtilsProducer.Yaml ObjectMapper yamlMapper) {
		for ( ObjectMapper mapper : List.of( jsonMapper, yamlMapper ) ) {
			mapper.registerModule( new Jdk8Module() );
		}
	}

}

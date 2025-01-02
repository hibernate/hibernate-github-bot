package org.hibernate.infra.bot.jira;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

public class JiraBaseObject {
	@JsonAnyGetter
	@JsonAnySetter
	private Map<String, Object> properties = new HashMap<>();

	public Map<String, Object> properties() {
		return properties;
	}

}

package org.hibernate.infra.bot.jira;

import java.net.URI;

public class JiraIssue extends JiraBaseObject {
	public Long id;
	public String key;
	public URI self;
	public JiraFields fields;
}

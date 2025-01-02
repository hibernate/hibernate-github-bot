package org.hibernate.infra.bot.jira;

import io.quarkus.rest.client.reactive.ClientBasicAuth;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/rest/api/3")
@RegisterRestClient(configKey = "jira")
@ClientBasicAuth(username = "${hibernate-github-bot.jira.username}", password = "${hibernate-github-bot.jira.token}")
public interface JiraRestClient {
	@GET
	@Path("/search/jql")
	JiraIssues find(@QueryParam("jql") String jql, @QueryParam("fields") String fields);
}

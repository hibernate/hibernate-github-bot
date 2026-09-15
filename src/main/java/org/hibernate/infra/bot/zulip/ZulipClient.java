package org.hibernate.infra.bot.zulip;

import io.quarkus.rest.client.reactive.ClientBasicAuth;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/v1")
@RegisterRestClient(configKey = "zulip")
@ClientBasicAuth(username = "${hibernate-github-bot.zulip.email}",
		password = "${hibernate-github-bot.zulip.api-key}")
public interface ZulipClient {

	@POST
	@Path("/messages")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	void sendMessage(@FormParam("type") String type,
			@FormParam("to") String stream,
			@FormParam("topic") String topic,
			@FormParam("content") String content);
}

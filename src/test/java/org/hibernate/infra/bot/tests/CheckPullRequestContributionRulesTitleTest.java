package org.hibernate.infra.bot.tests;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.infra.bot.tests.PullRequestMockHelper.mockPagedIterable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.PagedIterable;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@QuarkusTest
@GitHubAppTest
@ExtendWith(MockitoExtension.class)
public class CheckPullRequestContributionRulesTitleTest extends AbstractPullRequestTest {
	@Test
	void title_endsWithEllipsis() throws IOException {
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( 344815557L );

					PullRequestMockHelper.start( mocks, 585627026L, repoMock )
							.commit( "HSEARCH-1111 Correct message" )
							.comment( "Some comment" )
							.comment( "Some other comment" );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111-title-ends-with-ellipsis.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHPullRequest prMock = mocks.pullRequest( 585627026L );
					ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass( String.class );
					verify( prMock ).comment( messageCaptor.capture() );
					assertThat( messageCaptor.getValue() )
							.isEqualTo( """
									Thanks for your pull request!

									This pull request does not follow the contribution rules. Could you have a look?

									❌ The pull request title should not end with an ellipsis (make sure the title is complete)

									› This message was automatically generated.""" );
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

}

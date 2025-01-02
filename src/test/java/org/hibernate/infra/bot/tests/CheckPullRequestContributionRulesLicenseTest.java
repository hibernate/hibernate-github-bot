package org.hibernate.infra.bot.tests;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.junit.QuarkusTest;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@QuarkusTest
@GitHubAppTest
@ExtendWith(MockitoExtension.class)
public class CheckPullRequestContributionRulesLicenseTest extends AbstractPullRequestTest {

	final GHCheckRunBuilder licenseCheckRunCreateBuilderMock = mockCheckRunBuilder();
	final GHCheckRunBuilder licenseCheckRunUpdateBuilderMock = mockCheckRunBuilder();

	@Override
	void mockCheckRuns(GHRepository repoMock, String headSHA) throws IOException {
		super.mockCheckRuns( repoMock, headSHA );
		GHCheckRun licenseCheckRunMock = mock( GHCheckRun.class );
		mockCreateCheckRun( repoMock, "Contribution — License agreement", headSHA,
				licenseCheckRunCreateBuilderMock, licenseCheckRunMock, 44L
		);
		mockUpdateCheckRun( repoMock, 44L, licenseCheckRunUpdateBuilderMock, licenseCheckRunMock );
	}

	@Test
	void bodyMissingLicenseAgreement() throws IOException {
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									licenseAgreement:
									  enabled: true
									""" );
					mocks.configFile("PULL_REQUEST_TEMPLATE.md")
							.fromString( """
									[Please describe here what your change is about]
									
									<!--
									Please read and do not remove the following lines:
									-->
									----------------------
									By submitting this pull request, I confirm that my contribution is made under the terms of the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.txt)
									and can be relicensed under the terms of the [LGPL v2.1 license](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt) in the future at the maintainers' discretion.
									For more information on licensing, please check [here](https://github.com/hibernate/hibernate-search/blob/main/CONTRIBUTING.md#legal).
									
									----------------------
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					PullRequestMockHelper.start( mocks, prId, repoMock )
							.commit( "HSEARCH-1111 Correct message" )
							.comment( "Some comment" )
							.comment( "Some other comment" );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHPullRequest prMock = mocks.pullRequest( prId );
					ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass( String.class );
					verify( prMock ).comment( messageCaptor.capture() );
					assertThat( messageCaptor.getValue() )
							.isEqualTo( """
									Thanks for your pull request!
									
									This pull request does not follow the contribution rules. Could you have a look?
									
									❌ The pull request description must contain the license agreement text.
									    ↳ The description of this pull request must contain the following license agreement text:
									```
									----------------------
									By submitting this pull request, I confirm that my contribution is made under the terms of the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.txt)
									and can be relicensed under the terms of the [LGPL v2.1 license](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt) in the future at the maintainers' discretion.
									For more information on licensing, please check [here](https://github.com/hibernate/hibernate-search/blob/main/CONTRIBUTING.md#legal).
									
									----------------------
									```
									
									
									› This message was automatically generated.""" );
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void bodyContainsLicenseAgreement() throws IOException {
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									licenseAgreement:
									  enabled: true
									""" );
					mocks.configFile("PULL_REQUEST_TEMPLATE.md")
							.fromString( """
									[Please describe here what your change is about]
									
									<!--
									Please read and do not remove the following lines:
									-->
									----------------------
									By submitting this pull request, I confirm that my contribution is made under the terms of the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.txt)
									and can be relicensed under the terms of the [LGPL v2.1 license](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt) in the future at the maintainers' discretion.
									For more information on licensing, please check [here](https://github.com/hibernate/hibernate-search/blob/main/CONTRIBUTING.md#legal).
									
									----------------------
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );

					PullRequestMockHelper.start( mocks, prId, repoMock )
							.commit( "HSEARCH-1111 Correct message" )
							.comment( "Some comment" )
							.comment( "Some other comment" );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111-with-license.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

}

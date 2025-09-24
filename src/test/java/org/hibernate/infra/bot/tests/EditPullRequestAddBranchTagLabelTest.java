package org.hibernate.infra.bot.tests;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.junit.QuarkusTest;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@QuarkusTest
@GitHubAppTest
@ExtendWith(MockitoExtension.class)
public class EditPullRequestAddBranchTagLabelTest extends AbstractPullRequestTest {
	@Test
	void simple() throws IOException {
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									branches:
									  enabled: true
									  label: "%s"
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );
					when( repoMock.getLabel( any() ) ).thenAnswer( invocation -> {
						GHLabel label = mock( GHLabel.class );
						when( label.getName() ).thenReturn( "base-ref" );
						return label;
					} );

					PullRequestMockHelper.start( mocks, prId, repoMock )
							.commit( "HSEARCH-1111 Correct message" )
							.comment( "Some comment" )
							.comment( "Some other comment" );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111-non-main-branch.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHPullRequest prMock = mocks.pullRequest( prId );
					ArgumentCaptor<GHLabel> labelArgumentCaptor = ArgumentCaptor.forClass( GHLabel.class );
					verify( prMock ).addLabels( labelArgumentCaptor.capture() );
					assertThat( labelArgumentCaptor.getValue().getName() ).isEqualTo( "base-ref" );

					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void labelDoesNotExist() throws IOException {
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									branches:
									  enabled: true
									  label: "%s"
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );
					when( repoMock.getLabel( any() ) ).thenThrow( GHFileNotFoundException.class );

					when( repoMock.createLabel( any(), any(), any() ) ).thenAnswer( invocation -> {
						GHLabel label = mock( GHLabel.class );
						when( label.getName() ).thenReturn( "base-ref" );
						when( label.getColor() ).thenReturn( invocation.getArgument( 1 ) );
						when( label.getDescription() ).thenReturn( invocation.getArgument( 1 ) );
						return label;
					} );

					PullRequestMockHelper.start( mocks, prId, repoMock )
							.commit( "HSEARCH-1111 Correct message" )
							.comment( "Some comment" )
							.comment( "Some other comment" );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111-non-main-branch.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					GHPullRequest prMock = mocks.pullRequest( prId );

					ArgumentCaptor<String> name = ArgumentCaptor.forClass( String.class );
					ArgumentCaptor<String> color = ArgumentCaptor.forClass( String.class );
					ArgumentCaptor<String> description = ArgumentCaptor.forClass( String.class );
					verify( repoMock ).createLabel( name.capture(), color.capture(), description.capture() );
					assertThat( color.getValue() ).hasSize( 6 );
					assertThat( description.getValue() ).contains( "Label for pull requests targeting" );

					ArgumentCaptor<GHLabel> labelArgumentCaptor = ArgumentCaptor.forClass( GHLabel.class );
					verify( prMock ).addLabels( labelArgumentCaptor.capture() );
					assertThat( labelArgumentCaptor.getValue().getName() ).isEqualTo( "base-ref" );

					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void updateTitle() throws IOException {
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									branches:
									  enabled: true
									  titlePrefix: "[%s]"
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
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111-non-main-branch.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHPullRequest prMock = mocks.pullRequest( prId );

					ArgumentCaptor<String> title = ArgumentCaptor.forClass( String.class );
					verify( prMock ).setTitle( title.capture() );
					assertThat( title.getValue() ).startsWith( "[" );

					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}

	@Test
	void updateLabelAndTitle() throws IOException {
		long repoId = 344815557L;
		long prId = 585627026L;
		given()
				.github( mocks -> {
					mocks.configFile("hibernate-github-bot.yml")
							.fromString( """
									jira:
									  projectKey: "HSEARCH"
									branches:
									  enabled: true
									  titlePrefix: "[%s]"
									  label: "%s"
									""" );

					GHRepository repoMock = mocks.repository( "yrodiere/hibernate-github-bot-playground" );
					when( repoMock.getId() ).thenReturn( repoId );
					when( repoMock.getLabel( any() ) ).thenAnswer( invocation -> {
						GHLabel label = mock( GHLabel.class );
						when( label.getName() ).thenReturn( "base-ref" );
						return label;
					} );

					PullRequestMockHelper.start( mocks, prId, repoMock )
							.commit( "HSEARCH-1111 Correct message" )
							.comment( "Some comment" )
							.comment( "Some other comment" );

					mockCheckRuns( repoMock, "6e9f11a1e2946b207c6eb245ec942f2b5a3ea156" );
				} )
				.when()
				.payloadFromClasspath( "/pullrequest-opened-hsearch-1111-non-main-branch.json" )
				.event( GHEvent.PULL_REQUEST )
				.then()
				.github( mocks -> {
					GHPullRequest prMock = mocks.pullRequest( prId );
					ArgumentCaptor<GHLabel> labelArgumentCaptor = ArgumentCaptor.forClass( GHLabel.class );
					verify( prMock ).addLabels( labelArgumentCaptor.capture() );
					assertThat( labelArgumentCaptor.getValue().getName() ).isEqualTo( "base-ref" );

					ArgumentCaptor<String> title = ArgumentCaptor.forClass( String.class );
					verify( prMock ).setTitle( title.capture() );
					assertThat( title.getValue() ).startsWith( "[" );

					verifyNoMoreInteractions( mocks.ghObjects() );
				} );
	}
}

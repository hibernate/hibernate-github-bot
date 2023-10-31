package org.hibernate.infra.bot.tests;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;

import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.junit.QuarkusTest;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHRepository;
import org.mockito.Answers;
import org.mockito.junit.jupiter.MockitoExtension;

abstract class AbstractPullRequestTest {
	final GHCheckRunBuilder titleCheckRunCreateBuilderMock = mockCheckRunBuilder();
	final GHCheckRunBuilder titleCheckRunUpdateBuilderMock = mockCheckRunBuilder();
	final GHCheckRunBuilder jiraCheckRunCreateBuilderMock = mockCheckRunBuilder();
	final GHCheckRunBuilder jiraCheckRunUpdateBuilderMock = mockCheckRunBuilder();

	GHCheckRunBuilder mockCheckRunBuilder() {
		return mock( GHCheckRunBuilder.class, withSettings().defaultAnswer( Answers.RETURNS_SELF ) );
	}

	void mockCheckRuns(GHRepository repoMock, String headSHA) throws IOException {
		GHCheckRun titleCheckRunMock = mock( GHCheckRun.class );
		mockCreateCheckRun( repoMock, "Contribution — Title", headSHA,
				titleCheckRunCreateBuilderMock, titleCheckRunMock, 42L
		);
		mockUpdateCheckRun( repoMock, 42L, titleCheckRunUpdateBuilderMock, titleCheckRunMock );

		GHCheckRun jiraCheckRunMock = mock( GHCheckRun.class );
		mockCreateCheckRun( repoMock, "Contribution — JIRA issues", headSHA,
				jiraCheckRunCreateBuilderMock, jiraCheckRunMock, 43L
		);
		mockUpdateCheckRun( repoMock, 43L, jiraCheckRunUpdateBuilderMock, jiraCheckRunMock );
	}

	void mockCreateCheckRun(GHRepository repoMock, String name, String headSHA,
			GHCheckRunBuilder checkRunBuilderMock, GHCheckRun checkRunMock, long checkRunId) throws IOException {
		when( repoMock.createCheckRun( name, headSHA ) ).thenReturn( checkRunBuilderMock );
		when( checkRunMock.getId() ).thenReturn( checkRunId );
		when( checkRunBuilderMock.create() ).thenReturn( checkRunMock );
	}

	void mockUpdateCheckRun(GHRepository repoMock, long checkRunId,
			GHCheckRunBuilder checkRunBuilderMock, GHCheckRun checkRunMock) throws IOException {
		when( repoMock.updateCheckRun( checkRunId ) ).thenReturn( checkRunBuilderMock );
		when( checkRunBuilderMock.create() ).thenReturn( checkRunMock );
	}

}

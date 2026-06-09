package org.hibernate.infra.bot.tests;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.quarkiverse.githubapp.testing.dsl.GitHubMockContext;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;

public class PullRequestMockHelper {

	public static PullRequestMockHelper start(GitHubMockContext context, long prId, GHRepository repoMock) {
		GHPullRequest pullRequestMock = context.pullRequest( prId );
		GHCommitPointer baseMock = stub( GHCommitPointer.class );
		when( pullRequestMock.getBase() ).thenReturn( baseMock );
		when( baseMock.getRepository() ).thenReturn( repoMock );
		return new PullRequestMockHelper( repoMock, pullRequestMock, baseMock );
	}

	private final GHRepository repoMock;
	private final GHPullRequest pullRequestMock;
	private final GHCommitPointer baseMock;

	private List<GHIssueComment> commentsMocks;
	private final List<GHPullRequestCommitDetail> commitDetailsMocks;

	private PullRequestMockHelper(GHRepository repoMock, GHPullRequest pullRequestMock, GHCommitPointer baseMock) {
		this.repoMock = repoMock;
		this.pullRequestMock = pullRequestMock;
		this.baseMock = baseMock;
		this.commitDetailsMocks = new ArrayList<>();
		PagedIterable<GHPullRequestCommitDetail> commitIterableMock = mockPagedIterable( commitDetailsMocks );
		when( pullRequestMock.listCommits() ).thenReturn( commitIterableMock );
	}

	public PullRequestMockHelper baseRef(String ref) {
		when( baseMock.getRef() ).thenReturn( ref );
		return this;
	}

	public PullRequestMockHelper commit(String message) throws IOException {
		return commit( message, null );
	}

	public PullRequestMockHelper commit(String message, String sha) throws IOException {
		return commit( message, sha, null );
	}

	public PullRequestMockHelper commit(String message, String sha, List<String> files) throws IOException {
		GHPullRequestCommitDetail commitDetailMock = stub( GHPullRequestCommitDetail.class );
		commitDetailsMocks.add( commitDetailMock );
		GHPullRequestCommitDetail.Commit commitMock = stub( GHPullRequestCommitDetail.Commit.class );
		when( commitDetailMock.getCommit() ).thenReturn( commitMock );
		when( commitMock.getMessage() ).thenReturn( message );
		GHPullRequestCommitDetail.CommitPointer parentMock = stub( GHPullRequestCommitDetail.CommitPointer.class );
		when( commitDetailMock.getParents() )
				.thenReturn( new GHPullRequestCommitDetail.CommitPointer[] { parentMock } );
		if ( sha != null ) {
			when( commitDetailMock.getSha() ).thenReturn( sha );
		}
		if ( files != null ) {
			GHCommit ghCommitMock = stub( GHCommit.class );
			when( repoMock.getCommit( sha ) ).thenReturn( ghCommitMock );

			List<GHCommit.File> ghFiles = new ArrayList<>();
			PagedIterable<GHCommit.File> commitFilesIterableMock = mockPagedIterable( ghFiles );
			when( ghCommitMock.listFiles() ).thenReturn( commitFilesIterableMock );
			for ( String file : files ) {
				GHCommit.File stub = stub( GHCommit.File.class );
				when( stub.getFileName() ).thenReturn( file );
				ghFiles.add( stub );
			}
		}
		return this;
	}

	public PullRequestMockHelper mergeCommit(String message, String sha) {
		GHPullRequestCommitDetail commitDetailMock = stub( GHPullRequestCommitDetail.class );
		commitDetailsMocks.add( commitDetailMock );
		GHPullRequestCommitDetail.Commit commitMock = stub( GHPullRequestCommitDetail.Commit.class );
		when( commitDetailMock.getCommit() ).thenReturn( commitMock );
		when( commitMock.getMessage() ).thenReturn( message );
		GHPullRequestCommitDetail.CommitPointer parentMock1 = stub( GHPullRequestCommitDetail.CommitPointer.class );
		GHPullRequestCommitDetail.CommitPointer parentMock2 = stub( GHPullRequestCommitDetail.CommitPointer.class );
		when( commitDetailMock.getParents() )
				.thenReturn( new GHPullRequestCommitDetail.CommitPointer[] { parentMock1, parentMock2 } );
		if ( sha != null ) {
			when( commitDetailMock.getSha() ).thenReturn( sha );
		}
		return this;
	}

	public PullRequestMockHelper comment(String body) throws IOException {
		initCommentsMocks();
		GHIssueComment commentMock = stub( GHIssueComment.class );
		when( commentMock.getBody() ).thenReturn( body );
		commentsMocks.add( commentMock );
		return this;
	}

	public PullRequestMockHelper noComments() throws IOException {
		initCommentsMocks();
		return this;
	}

	private void initCommentsMocks() throws IOException {
		if ( commentsMocks == null ) {
			commentsMocks = new ArrayList<>();
			PagedIterable<GHIssueComment> commitIterableMock = mockPagedIterable( commentsMocks );
			when( pullRequestMock.listComments() ).thenReturn( commitIterableMock );
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> PagedIterable<T> mockPagedIterable(List<T> contentMocks) {
		PagedIterable<T> iterableMock = mock( PagedIterable.class );
		when( iterableMock.iterator() ).thenAnswer( ignored -> {
			PagedIterator<T> iteratorMock = mock( PagedIterator.class );
			Iterator<T> actualIterator = contentMocks.iterator();
			when( iteratorMock.next() ).thenAnswer( ignored2 -> actualIterator.next() );
			when( iteratorMock.hasNext() ).thenAnswer( ignored2 -> actualIterator.hasNext() );
			return iteratorMock;
		} );
		return iterableMock;
	}

	private static <T> T stub(Class<T> clazz) {
		return mock( clazz, withSettings().stubOnly() );
	}
}

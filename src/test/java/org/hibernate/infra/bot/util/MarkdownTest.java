package org.hibernate.infra.bot.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class MarkdownTest {

	@Test
	void joinWithSoftWrapping_empty() {
		assertThat( Markdown.joinWithSoftWrapping( List.of(), " ", " <br>", 30 ) )
				.isEqualTo( "" );
	}

	@Test
	void joinWithSoftWrapping_singleItem() {
		assertThat( Markdown.joinWithSoftWrapping( List.of( "alpha" ), " ", " <br>", 30 ) )
				.isEqualTo( "alpha" );
	}

	@Test
	void joinWithSoftWrapping_allFitOnOneLine() {
		assertThat( Markdown.joinWithSoftWrapping( List.of( "alpha", "beta" ), " ", " <br>", 30 ) )
				.isEqualTo( "alpha beta" );
	}

	@Test
	void joinWithSoftWrapping_wrapWhenExceedingLimit() {
		assertThat( Markdown.joinWithSoftWrapping(
				List.of( "alpha", "beta", "gamma" ), " ", " <br>", 12 ) )
				.isEqualTo( "alpha beta <br>gamma" );
	}

	@Test
	void joinWithSoftWrapping_eachItemExceedsLimit() {
		assertThat( Markdown.joinWithSoftWrapping(
				List.of( "longword", "another" ), " ", " <br>", 5 ) )
				.isEqualTo( "longword <br>another" );
	}

	@Test
	void joinWithSoftWrapping_multipleWraps() {
		assertThat( Markdown.joinWithSoftWrapping(
				List.of( "aa", "bb", "cc", "dd", "ee" ), " ", " <br>", 6 ) )
				.isEqualTo( "aa bb <br>cc dd <br>ee" );
	}

	@Test
	void joinWithSoftWrapping_wrapAtExactLimit() {
		// "aaa bbb" is 7 chars, limit is 7 => fits
		assertThat( Markdown.joinWithSoftWrapping(
				List.of( "aaa", "bbb", "ccc" ), " ", " <br>", 7 ) )
				.isEqualTo( "aaa bbb <br>ccc" );
	}

	@Test
	void joinWithSoftWrapping_wrapJustPastLimit() {
		// "aaa bbb" is 7 chars, limit is 6 => wraps
		assertThat( Markdown.joinWithSoftWrapping(
				List.of( "aaa", "bbb", "ccc" ), " ", " <br>", 6 ) )
				.isEqualTo( "aaa <br>bbb <br>ccc" );
	}
}

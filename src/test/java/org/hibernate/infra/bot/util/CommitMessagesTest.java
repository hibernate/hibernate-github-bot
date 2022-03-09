package org.hibernate.infra.bot.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;

import org.hibernate.infra.bot.StringListArgumentConverter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

class CommitMessagesTest {
	@ParameterizedTest
	@CsvSource({
			"nomatch HSEARCH-124,",
			"HSEARCH-123 blahblah,HSEARCH-123",
			"HSEARCH-123 HSEARCH-124 blahblah,HSEARCH-123;HSEARCH-124",
			"HSEARCH-123 blahblah HSEARCH-124 blahblah,HSEARCH-123",
			"[HSEARCH-123] blahblah,HSEARCH-123",
			"[HSEARCH-123] [HSEARCH-124] blahblah,HSEARCH-123;HSEARCH-124",
			"[HSEARCH-123] blahblah HSEARCH-124 blahblah,HSEARCH-123",
			"'HSEARCH-123,HSEARCH-124 blahblah',HSEARCH-123;HSEARCH-124",
	})
	public void extractIssueKeys(String commitMessage, @ConvertWith(StringListArgumentConverter.class) List<String> expected) {
		Pattern issueKeyPattern = Pattern.compile("HSEARCH-\\d+");
		assertThat( CommitMessages.extractIssueKeys( issueKeyPattern, commitMessage ) )
				.containsExactlyElementsOf( expected );
	}

}
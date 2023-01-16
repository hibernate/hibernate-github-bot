package org.hibernate.infra.bot.check;

import java.util.ArrayList;
import java.util.List;

public final class CheckRunOutput {

	public final long id;
	public final String name;

	private final List<CheckRunRule> rules = new ArrayList<>();

	CheckRunOutput(long id, String name) {
		this.id = id;
		this.name = name;
	}

	public CheckRunRule rule(String description) {
		CheckRunRule rule = new CheckRunRule( description );
		rules.add( rule );
		return rule;
	}

	public boolean passed() {
		return rules.stream().allMatch( r -> r.passed );
	}

	public String title() {
		List<CheckRunRule> failingRules = rules.stream().filter( r -> !r.passed ).toList();
		if ( failingRules.isEmpty() ) {
			return "All rules passed";
		}
		else if ( failingRules.size() == 1 ) {
			return failingRules.get( 0 ).description;
		}
		else {
			return failingRules.size() + " rules failed";
		}
	}

	public String summary() {
		StringBuilder builder = new StringBuilder();
		appendRules( builder, true );
		return builder.toString();
	}

	public void appendFailingRules(StringBuilder comment) {
		appendRules( comment, false );
	}

	private void appendRules(StringBuilder comment, boolean includePassed) {
		for ( CheckRunRule rule : rules ) {
			if ( rule.passed && !includePassed ) {
				continue;
			}
			String emoji = rule.passed ? "✔" : "❌";
			comment.append( "\n" ).append( emoji ).append( "\u00A0" )
					.append( rule.description );
			if ( rule.commentOrNull != null ) {
				comment.append( "\n\u00A0\u00A0\u00A0\u00A0↳\u00A0" ).append( rule.commentOrNull );
			}
		}
	}
}

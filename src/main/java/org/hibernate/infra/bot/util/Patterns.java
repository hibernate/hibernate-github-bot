/*
 *
 *  * Hibernate Search, full-text search for your domain model
 *  *
 *  * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 *  * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 *
 */
package org.hibernate.infra.bot.util;

import java.util.regex.Pattern;

public class Patterns {

	public static Pattern compile(String pattern) {
		return Pattern.compile(pattern, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	}

	public static boolean find(String pattern, String string) {
		return compile(pattern).matcher(string).find();
	}

	private Patterns() {
	}
}

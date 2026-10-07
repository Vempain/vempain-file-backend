package fi.poltsi.vempain.file.tools;

import java.util.Locale;

/**
 * Builds {@code LIKE} patterns from request text. Request text is data, never pattern syntax: the wildcard characters
 * {@code %} and {@code _} and the escape character {@code \} are escaped so that a caller cannot widen a search to every row
 * or craft expensive patterns, and the pattern is capped in length. PostgreSQL uses {@code \} as the default escape character
 * of {@code LIKE}/{@code ILIKE}, which is what the escaping relies on.
 */
public final class LikePatterns {

	/**
	 * Longest request text accepted into a pattern; longer input is truncated.
	 */
	public static final int MAX_TEXT_LENGTH = 200;

	/**
	 * Escape character used by {@link #escape(String)}; pass it to {@code CriteriaBuilder.like(expr, pattern, ESCAPE_CHAR)}.
	 */
	public static final char ESCAPE_CHAR = '\\';

	/**
	 * SQL clause declaring {@link #ESCAPE_CHAR} for hand-written {@code LIKE} conditions.
	 */
	public static final String ESCAPE_CLAUSE = " ESCAPE '\\'";

	/**
	 * Maximum number of search tokens a request may turn into LIKE conditions.
	 */
	public static final int MAX_TOKENS = 10;

	private LikePatterns() {
	}

	/**
	 * Escapes {@code \}, {@code %} and {@code _} and truncates the text to {@link #MAX_TEXT_LENGTH}.
	 */
	public static String escape(String text) {
		if (text == null) {
			return "";
		}
		var bounded = text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
		var escaped = new StringBuilder(bounded.length() + 8);
		for (int i = 0; i < bounded.length(); i++) {
			char c = bounded.charAt(i);
			if (c == '\\' || c == '%' || c == '_') {
				escaped.append('\\');
			}
			escaped.append(c);
		}
		return escaped.toString();
	}

	/**
	 * Pattern matching values that contain {@code text} literally.
	 */
	public static String contains(String text) {
		return "%" + escape(text) + "%";
	}

	/**
	 * Lower-cased variant of {@link #contains(String)} for case-insensitive comparisons against {@code LOWER(column)}.
	 */
	public static String containsIgnoreCase(String text) {
		return contains(text == null ? null : text.toLowerCase(Locale.ROOT));
	}

	/**
	 * The first {@link #MAX_TOKENS} tokens of a request; the rest are ignored to bound the query cost.
	 */
	public static <T> java.util.List<T> limitTokens(java.util.List<T> tokens) {
		if (tokens == null) {
			return java.util.List.of();
		}
		return tokens.size() <= MAX_TOKENS ? tokens : tokens.subList(0, MAX_TOKENS);
	}

	/**
	 * Pattern matching values that start with {@code text} literally.
	 */
	public static String prefix(String text) {
		return escape(text) + "%";
	}
}

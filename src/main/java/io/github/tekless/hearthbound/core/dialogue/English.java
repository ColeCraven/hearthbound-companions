package io.github.tekless.hearthbound.core.dialogue;

import java.util.Locale;

/** Small English helpers for filling lines naturally. */
public final class English {
	private English() {
	}

	/** "zombie" -> "a zombie", "Enderman" -> "an enderman", "sweet berries" -> "some sweet berries". */
	public static String withArticle(String noun) {
		String lower = noun.toLowerCase(Locale.ROOT).trim();
		if (lower.isEmpty()) {
			return "something";
		}
		if (lower.endsWith("s") && !lower.endsWith("ss")) {
			return "some " + lower;
		}
		return ("aeiou".indexOf(lower.charAt(0)) >= 0 ? "an " : "a ") + lower;
	}
}

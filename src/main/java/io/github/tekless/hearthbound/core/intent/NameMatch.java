package io.github.tekless.hearthbound.core.intent;

/** Forgiving name matching for "@name": short forms and one-letter typos still find the companion. */
public final class NameMatch {
	private NameMatch() {
	}

	/** A prefix of three letters or more, or one slip of the keyboard (a wrong, missing, extra or swapped letter). */
	public static boolean closeEnough(String typed, String name) {
		if (typed.length() >= 3 && name.startsWith(typed)) {
			return true;
		}
		return typed.length() >= 4 && Math.abs(typed.length() - name.length()) <= 1 && editDistance(typed, name) <= 1;
	}

	/** Optimal string alignment distance: insertions, deletions, substitutions and adjacent swaps. */
	public static int editDistance(String a, String b) {
		int[][] d = new int[a.length() + 1][b.length() + 1];
		for (int i = 0; i <= a.length(); i++) {
			d[i][0] = i;
		}
		for (int j = 0; j <= b.length(); j++) {
			d[0][j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
				if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
					d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
				}
			}
		}
		return d[a.length()][b.length()];
	}
}

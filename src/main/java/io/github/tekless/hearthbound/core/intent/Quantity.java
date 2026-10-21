package io.github.tekless.hearthbound.core.intent;

import java.util.List;
import java.util.Map;

/**
 * Reads amounts at the start of a token list: "16", "x16", "sixteen", "twenty five", "a dozen",
 * "a stack of", "half a stack", "two stacks", "a couple of", "a few", "some".
 */
final class Quantity {
	private static final Map<String, Integer> UNITS = Map.ofEntries(
			Map.entry("zero", 0), Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3),
			Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7),
			Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("eleven", 11),
			Map.entry("twelve", 12), Map.entry("thirteen", 13), Map.entry("fourteen", 14),
			Map.entry("fifteen", 15), Map.entry("sixteen", 16), Map.entry("seventeen", 17),
			Map.entry("eighteen", 18), Map.entry("nineteen", 19));
	private static final Map<String, Integer> TENS = Map.of(
			"twenty", 20, "thirty", 30, "forty", 40, "fifty", 50,
			"sixty", 60, "seventy", 70, "eighty", 80, "ninety", 90);

	/** Amount stated, or null if the words only said "some"/"more"/"lots". */
	record Result(Integer value, int consumed) {
	}

	private Quantity() {
	}

	/** Parses a quantity at {@code start}; returns consumed=0 if there is none. */
	static Result parse(List<String> tokens, int start) {
		int i = start;
		// Filler words that don't change the amount.
		while (i < tokens.size() && isFiller(tokens.get(i))) {
			i++;
		}
		if (i >= tokens.size()) {
			return new Result(null, i - start);
		}

		String t = tokens.get(i);
		Integer stackResult = parseStacks(tokens, i);
		if (stackResult != null) {
			int used = stackWordsUsed(tokens, i);
			return new Result(stackResult, i - start + used + skipOf(tokens, i + used));
		}

		if (t.equals("a") || t.equals("an")) {
			if (i + 1 < tokens.size()) {
				String next = tokens.get(i + 1);
				switch (next) {
					case "dozen" -> {
						return new Result(12, i - start + 2 + skipOf(tokens, i + 2));
					}
					case "couple" -> {
						return new Result(2, i - start + 2 + skipOf(tokens, i + 2));
					}
					case "few" -> {
						return new Result(3, i - start + 2 + skipOf(tokens, i + 2));
					}
					case "lot", "bunch", "load", "ton", "heap", "pile" -> {
						return new Result(null, i - start + 2 + skipOf(tokens, i + 2));
					}
					default -> {
						if (next.equals("hundred")) {
							return new Result(100, i - start + 2);
						}
						// "a log" -> 1
						return new Result(1, i - start + 1);
					}
				}
			}
			return new Result(null, i - start);
		}
		switch (t) {
			case "dozen" -> {
				return new Result(12, i - start + 1 + skipOf(tokens, i + 1));
			}
			case "couple" -> {
				return new Result(2, i - start + 1 + skipOf(tokens, i + 1));
			}
			case "few" -> {
				return new Result(3, i - start + 1 + skipOf(tokens, i + 1));
			}
			case "several" -> {
				return new Result(5, i - start + 1 + skipOf(tokens, i + 1));
			}
			case "lots", "plenty", "loads", "tons", "bunch", "many" -> {
				return new Result(null, i - start + 1 + skipOf(tokens, i + 1));
			}
			default -> {
			}
		}

		Integer number = parseNumber(tokens, i);
		if (number != null) {
			int used = numberWordsUsed(tokens, i);
			// "16 stacks"? treat "<n> stack(s)" as n*64
			if (i + used < tokens.size() && isStackWord(tokens.get(i + used))) {
				return new Result(number * 64, i - start + used + 1 + skipOf(tokens, i + used + 1));
			}
			return new Result(number, i - start + used + skipOf(tokens, i + used));
		}
		return new Result(null, i - start);
	}

	private static boolean isFiller(String token) {
		return token.equals("some") || token.equals("more") || token.equals("about") || token.equals("around")
				|| token.equals("like") || token.equals("roughly") || token.equals("maybe") || token.equals("another")
				|| token.equals("exactly") || token.equals("at") || token.equals("least") || token.equals("just");
	}

	private static int skipOf(List<String> tokens, int index) {
		return index < tokens.size() && tokens.get(index).equals("of") ? 1 : 0;
	}

	private static boolean isStackWord(String token) {
		return token.equals("stack") || token.equals("stacks");
	}

	private static Integer parseStacks(List<String> tokens, int i) {
		String t = tokens.get(i);
		if (isStackWord(t)) {
			return 64;
		}
		if ((t.equals("a") || t.equals("one")) && i + 1 < tokens.size() && isStackWord(tokens.get(i + 1))) {
			return 64;
		}
		if (t.equals("half") && i + 2 < tokens.size() && tokens.get(i + 1).equals("a") && isStackWord(tokens.get(i + 2))) {
			return 32;
		}
		if (t.equals("half") && i + 1 < tokens.size() && isStackWord(tokens.get(i + 1))) {
			return 32;
		}
		return null;
	}

	private static int stackWordsUsed(List<String> tokens, int i) {
		String t = tokens.get(i);
		if (isStackWord(t)) {
			return 1;
		}
		if (t.equals("half") && tokens.get(i + 1).equals("a")) {
			return 3;
		}
		return 2;
	}

	static Integer parseNumber(List<String> tokens, int i) {
		String t = tokens.get(i);
		String digits = t;
		if (digits.startsWith("x") && digits.length() > 1) {
			digits = digits.substring(1);
		} else if (digits.endsWith("x") && digits.length() > 1) {
			digits = digits.substring(0, digits.length() - 1);
		}
		if (!digits.isEmpty() && digits.chars().allMatch(Character::isDigit)) {
			if (digits.length() > 6) {
				return Integer.MAX_VALUE;
			}
			return Integer.parseInt(digits);
		}
		if (UNITS.containsKey(t)) {
			return UNITS.get(t);
		}
		if (t.equals("hundred")) {
			return 100;
		}
		// "twenty-five" arrives as "twenty", "five" after tokenising hyphens
		if (TENS.containsKey(t)) {
			int value = TENS.get(t);
			if (i + 1 < tokens.size() && UNITS.containsKey(tokens.get(i + 1)) && UNITS.get(tokens.get(i + 1)) < 10) {
				value += UNITS.get(tokens.get(i + 1));
			}
			return value;
		}
		return null;
	}

	private static int numberWordsUsed(List<String> tokens, int i) {
		String t = tokens.get(i);
		if (TENS.containsKey(t) && i + 1 < tokens.size() && UNITS.containsKey(tokens.get(i + 1))
				&& UNITS.get(tokens.get(i + 1)) < 10) {
			return 2;
		}
		return 1;
	}
}

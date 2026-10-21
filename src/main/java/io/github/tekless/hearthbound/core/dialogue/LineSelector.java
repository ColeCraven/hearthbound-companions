package io.github.tekless.hearthbound.core.dialogue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks a line for a situation without repeating recent ones. Each companion owns one selector,
 * so two companions of the same archetype still vary independently.
 */
public final class LineSelector {
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");
	private static final String FALLBACK_KEY = "talk.generic";

	private final Random random;
	private final Map<String, Deque<Integer>> recent = new HashMap<>();
	private String lastText = "";

	public LineSelector(Random random) {
		this.random = random;
	}

	public LineSelector() {
		this(new Random());
	}

	/**
	 * Picks a line for {@code key} (falling back to generic talk if the bank has none) and fills
	 * its placeholders from {@code vars}.
	 */
	public String pick(LineBank bank, String key, Map<String, String> vars) {
		String usedKey = bank.has(key) ? key : FALLBACK_KEY;
		List<String> options = bank.get(usedKey);
		if (options.isEmpty()) {
			return "...";
		}
		int index = chooseIndex(usedKey, options.size());
		String text = fill(options.get(index), vars);
		if (text.equals(lastText) && options.size() > 1) {
			// The same words twice in a row sound like a stuck record, even across different situations.
			index = chooseIndex(usedKey, options.size());
			text = fill(options.get(index), vars);
		}
		lastText = text;
		return text;
	}

	private int chooseIndex(String key, int size) {
		Deque<Integer> used = recent.computeIfAbsent(key, k -> new ArrayDeque<>());
		// Remember up to size-1 recent picks (at most 6), so every line comes round before repeating.
		int memory = Math.min(size - 1, 6);
		List<Integer> candidates = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			if (!used.contains(i)) {
				candidates.add(i);
			}
		}
		int choice = candidates.isEmpty() ? random.nextInt(size) : candidates.get(random.nextInt(candidates.size()));
		used.addLast(choice);
		while (used.size() > memory) {
			used.removeFirst();
		}
		return choice;
	}

	/** Replaces {name}-style placeholders; unknown ones get a neutral word so text never shows braces. */
	public static String fill(String template, Map<String, String> vars) {
		Matcher m = PLACEHOLDER.matcher(template);
		StringBuilder out = new StringBuilder();
		int last = 0;
		while (m.find()) {
			out.append(template, last, m.start());
			last = m.end();
			String value = vars.get(m.group(1));
			if (value == null) {
				value = defaultFor(m.group(1));
			}
			// "{thing}? Sure." -> "A shelter? Sure." when the placeholder starts a sentence.
			String before = template.substring(0, m.start()).stripTrailing();
			boolean sentenceStart = before.isEmpty() || before.endsWith(".") || before.endsWith("!") || before.endsWith("?");
			if (sentenceStart && !value.isEmpty() && Character.isLowerCase(value.charAt(0))) {
				value = Character.toUpperCase(value.charAt(0)) + value.substring(1);
			}
			agreeWith(out, value);
			out.append(value);
		}
		out.append(template, last, template.length());
		return out.toString();
	}

	/**
	 * Fixes the word in front of a filled-in value: "a {item}" becomes "an apple" or "some sweet
	 * berries", "this {item}" becomes "these cookies".
	 */
	private static void agreeWith(StringBuilder out, String value) {
		if (value.isEmpty() || out.length() < 2 || out.charAt(out.length() - 1) != ' ') {
			return;
		}
		int end = out.length() - 1;
		int start = end;
		while (start > 0 && Character.isLetter(out.charAt(start - 1))) {
			start--;
		}
		String word = out.substring(start, end);
		String lower = value.toLowerCase(Locale.ROOT);
		boolean plural = lower.endsWith("s") && !lower.endsWith("ss") && !lower.endsWith("us") && !lower.endsWith("is");
		String replacement = switch (word.toLowerCase(Locale.ROOT)) {
			case "a", "an" -> plural ? "some" : "aeiou".indexOf(lower.charAt(0)) >= 0 ? "an" : "a";
			case "this", "these" -> plural ? "these" : "this";
			default -> null;
		};
		if (replacement != null && !replacement.equalsIgnoreCase(word)) {
			if (Character.isUpperCase(word.charAt(0))) {
				replacement = Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
			}
			out.replace(start, end, replacement);
		}
	}

	private static String defaultFor(String placeholder) {
		return switch (placeholder) {
			case "player" -> "friend";
			case "count", "have", "max" -> "some";
			case "item" -> "those";
			case "mob" -> "creature";
			case "biome" -> "these lands";
			case "task" -> "that";
			default -> "that";
		};
	}

	/** Placeholder names used in a template, for validation in tests. */
	public static List<String> placeholders(String template) {
		List<String> names = new ArrayList<>();
		Matcher m = PLACEHOLDER.matcher(template);
		while (m.find()) {
			names.add(m.group(1));
		}
		return names;
	}
}

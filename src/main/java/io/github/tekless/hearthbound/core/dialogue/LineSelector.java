package io.github.tekless.hearthbound.core.dialogue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
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
		return fill(options.get(index), vars);
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
		while (m.find()) {
			String value = vars.get(m.group(1));
			if (value == null) {
				value = defaultFor(m.group(1));
			}
			m.appendReplacement(out, Matcher.quoteReplacement(value));
		}
		m.appendTail(out);
		return out.toString();
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

package io.github.tekless.hearthbound.core.memory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

/**
 * What one player has told a companion about themselves: what to call them, and a few things they
 * like and can't stand. Only ever filled from the player's own words, so a companion can bring them
 * up later ("Diamonds! You said you liked those.") without making anything up.
 */
public final class PlayerFacts {
	public static final int MAX_EACH = 5;
	private static final Set<String> FILLER_WORDS = Set.of(
			"the", "a", "an", "to", "and", "or", "my", "your", "of", "in", "on", "at", "with", "being", "very", "big",
			"lot", "lots", "all", "some", "any", "really", "just", "good", "nice", "cool", "thing", "things", "stuff");

	private String nickname;
	private final Deque<String> likes = new ArrayDeque<>();
	private final Deque<String> dislikes = new ArrayDeque<>();

	public Optional<String> nickname() {
		return Optional.ofNullable(nickname);
	}

	public void setNickname(String name) {
		nickname = name;
	}

	public void forgetNickname() {
		nickname = null;
	}

	/** Remembers a like (most recent last). Saying you like something you'd said you hated changes your mind. */
	public void like(String thing) {
		remove(dislikes, thing);
		add(likes, thing);
	}

	public void dislike(String thing) {
		remove(likes, thing);
		add(dislikes, thing);
	}

	public List<String> likes() {
		return new ArrayList<>(likes);
	}

	public List<String> dislikes() {
		return new ArrayList<>(dislikes);
	}

	public boolean isEmpty() {
		return nickname == null && likes.isEmpty() && dislikes.isEmpty();
	}

	/** A like that names this thing in the world: "the ocean" for a deep ocean, "diamonds" for diamond ore. */
	public Optional<String> likedMatch(String worldThing) {
		return firstMatch(likes, worldThing);
	}

	public Optional<String> dislikedMatch(String worldThing) {
		return firstMatch(dislikes, worldThing);
	}

	/**
	 * Everything known, as one clause a companion can say back:
	 * "you go by Captain, you like diamonds and the ocean, and you can't stand spiders".
	 */
	public String summary() {
		List<String> parts = new ArrayList<>();
		if (nickname != null) {
			parts.add("you go by " + nickname);
		}
		if (!likes.isEmpty()) {
			parts.add("you like " + list(likes));
		}
		if (!dislikes.isEmpty()) {
			parts.add("you can't stand " + list(dislikes));
		}
		if (parts.size() == 1) {
			return parts.get(0);
		}
		return String.join(", ", parts.subList(0, parts.size() - 1)) + ", and " + parts.get(parts.size() - 1);
	}

	/** True if any meaningful word of {@code fact} appears in {@code worldThing} (ignoring plurals). */
	public static boolean names(String fact, String worldThing) {
		Set<String> world = words(worldThing);
		for (String word : words(fact)) {
			if (word.length() >= 3 && world.contains(word)) {
				return true;
			}
		}
		return false;
	}

	private static Optional<String> firstMatch(Deque<String> facts, String worldThing) {
		for (String fact : facts) {
			if (names(fact, worldThing)) {
				return Optional.of(fact);
			}
		}
		return Optional.empty();
	}

	private static Set<String> words(String text) {
		Set<String> words = new HashSet<>();
		for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
			if (!word.isEmpty() && !FILLER_WORDS.contains(word)) {
				words.add(singular(word));
			}
		}
		return words;
	}

	static String singular(String word) {
		if (word.endsWith("ies") && word.length() > 4) {
			return word.substring(0, word.length() - 3) + "y";
		}
		if (word.endsWith("es") && (word.endsWith("ches") || word.endsWith("shes") || word.endsWith("xes") || word.endsWith("sses"))) {
			return word.substring(0, word.length() - 2);
		}
		if (word.endsWith("s") && !word.endsWith("ss") && word.length() > 3) {
			return word.substring(0, word.length() - 1);
		}
		return word;
	}

	private static String list(Deque<String> items) {
		List<String> all = new ArrayList<>(items);
		if (all.size() == 1) {
			return all.get(0);
		}
		StringJoiner joiner = new StringJoiner(", ");
		all.subList(0, all.size() - 1).forEach(joiner::add);
		return joiner + " and " + all.get(all.size() - 1);
	}

	private static void add(Deque<String> items, String thing) {
		remove(items, thing);
		items.addLast(thing);
		while (items.size() > MAX_EACH) {
			items.removeFirst();
		}
	}

	private static void remove(Deque<String> items, String thing) {
		items.removeIf(existing -> existing.equalsIgnoreCase(thing));
	}
}

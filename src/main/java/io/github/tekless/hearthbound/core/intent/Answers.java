package io.github.tekless.hearthbound.core.intent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads short answers to a companion's own questions ("What should I call you?", "What's your
 * favourite thing to find?", "How are you holding up?"). Kept strict: an answer that doesn't look
 * like a name or a thing is treated as plain conversation instead of being remembered.
 */
public final class Answers {
	public enum Sentiment {
		GOOD,
		BAD,
		NEUTRAL
	}

	private static final int MAX_THING_WORDS = 4;
	private static final int MAX_THING_LENGTH = 32;
	private static final Pattern NOT_BAD = Pattern.compile("\\b(not (bad|too bad|so bad|terrible)|cant complain|no complaints)\\b");
	private static final Pattern BAD = Pattern.compile("\\b(not (good|great|well|so good|too good|fine|ok|okay|really|amazing)|bad|terrible|awful|tired|sad|rough|horrible|"
			+ "exhausted|meh|lonely|stressed|angry|hurt|sick|worse|scared|bored|miserable|could be better|been better|ugh|sucks|upset|down|hungry|dying)\\b");
	private static final Pattern GOOD = Pattern.compile("\\b(good|great|fine|well|ok|okay|alright|awesome|amazing|happy|fantastic|wonderful|excellent|brilliant|"
			+ "lovely|nice|better|cool|grand|decent|perfect|chill|glad|fab|fabulous|super|splendid|content)\\b");
	private static final Set<String> NON_ANSWERS = Set.of(
			"idk", "dunno", "i dunno", "i dont know", "dont know", "not sure", "im not sure", "no idea", "nothing", "none", "no", "nah", "nope",
			"not really", "whatever", "anything", "everything", "hmm", "um", "uh", "pass", "skip", "never mind", "nevermind", "doesnt matter",
			"it doesnt matter", "i dont care", "dont care", "no clue", "who knows", "good question", "not telling", "secret", "its a secret");
	private static final Set<String> NOT_A_NAME = Set.of(
			"yes", "yeah", "sure", "ok", "okay", "fine", "hi", "hello", "hey", "thanks", "you", "me", "i", "it", "that", "this", "what",
			"why", "how", "lol", "haha", "the", "a", "an", "my", "name", "is");
	/** Openings people put in front of the actual answer: "probably diamonds", "has to be the ocean". */
	private static final List<String> THING_LEADS = List.of(
			"i really like", "i really love", "i like", "i love", "i enjoy", "i adore", "my favourite thing is", "my favorite thing is",
			"my favourite is", "my favorite is", "my fav is", "favourite is", "favorite is", "it has to be", "it would be", "it would have to be",
			"has to be", "would have to be", "gotta be", "got to be", "id say", "i would say", "i think", "i guess", "probably", "maybe",
			"definitely", "easily", "easy", "oh", "hmm", "um", "uh", "well", "honestly", "i hate", "i really hate", "i cant stand", "i dislike",
			"i dont like", "im scared of", "i am scared of", "im afraid of", "i am afraid of", "im terrified of", "id go with", "i go with",
			"for sure", "always", "its", "it is", "oh definitely", "probably just", "just");
	private static final List<String> THING_TAILS = List.of(
			"a lot", "so much", "very much", "too", "the most", "most", "lol", "haha", "i guess", "probably", "honestly", "obviously",
			"of course", "for sure", "i think", "definitely", "really", "always", "id say", "i suppose");
	/** Words that make a "thing" about the companion or the moment, not a fact: "I like you", "I love it here". */
	private static final Set<String> NOT_A_THING = Set.of(
			"you", "u", "ya", "yall", "youre", "your", "yours", "yourself", "me", "myself", "us", "it", "this", "that", "these", "those", "here",
			"there", "them", "him", "her", "everything", "nothing", "everyone", "anyone", "anything", "something", "when", "how", "what", "if");
	/** "Call me later", "call me when you're done": not names. */
	private static final Set<String> NOT_LATER = Set.of("later", "back", "tomorrow", "sometime", "maybe", "when", "if", "anytime", "soon", "crazy");
	private static final List<String> NAME_LEADS = List.of(
			"you can call me", "you could call me", "just call me", "please call me", "call me", "my name is", "my names", "my friends call me",
			"friends call me", "people call me", "everyone calls me", "i go by", "i am called", "im called", "its", "it is", "im", "i am", "just",
			"um", "uh", "hmm", "oh", "well");

	private Answers() {
	}

	public static Sentiment sentiment(String text) {
		String c = String.join(" ", CommandParser.tokenize(text));
		if (NOT_BAD.matcher(c).find()) {
			return Sentiment.GOOD;
		}
		if (BAD.matcher(c).find()) {
			return Sentiment.BAD;
		}
		if (GOOD.matcher(c).find()) {
			return Sentiment.GOOD;
		}
		return Sentiment.NEUTRAL;
	}

	/** "Dunno", "nothing", "not telling": a reply that answers nothing. */
	public static boolean isNonAnswer(String text) {
		return NON_ANSWERS.contains(String.join(" ", CommandParser.tokenize(text)));
	}

	/** The thing in an answer like "probably diamonds" or "has to be the ocean", if it is short enough to be one. */
	public static Optional<String> thing(String text) {
		String c = String.join(" ", CommandParser.tokenize(text));
		if (c.isEmpty() || NON_ANSWERS.contains(c)) {
			return Optional.empty();
		}
		return cleanThing(stripLeads(c, THING_LEADS));
	}

	/**
	 * Trims "a lot", "so much" and the like off a thing, and rejects it if it's really a sentence or
	 * about the companion or the moment ("you", "it here").
	 */
	static Optional<String> cleanThing(String tokens) {
		String c = stripTails(tokens, THING_TAILS);
		if (c.isEmpty() || NON_ANSWERS.contains(c) || c.length() > MAX_THING_LENGTH || c.split(" ").length > MAX_THING_WORDS
				|| Arrays.stream(c.split(" ")).anyMatch(NOT_A_THING::contains)) {
			return Optional.empty();
		}
		return Optional.of(c);
	}

	/** A name from an answer like "Captain" or "just call me the boss", capitalised: "The Boss". */
	public static Optional<String> nickname(String text) {
		String c = String.join(" ", CommandParser.tokenize(text));
		if (NON_ANSWERS.contains(c)) {
			return Optional.empty();
		}
		c = stripLeads(c, NAME_LEADS);
		c = stripTails(c, List.of("please", "thanks", "thank you", "i guess", "lol", "haha"));
		String[] words = c.isEmpty() ? new String[0] : c.split(" ");
		if (words.length == 0 || words.length > 2 || NON_ANSWERS.contains(c)) {
			return Optional.empty();
		}
		if (Arrays.stream(words).allMatch(NOT_A_NAME::contains) || Arrays.stream(words).anyMatch(word -> word.length() > 12 || NOT_LATER.contains(word))) {
			return Optional.empty();
		}
		return Optional.of(Arrays.stream(words)
				.map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
				.collect(Collectors.joining(" ")));
	}

	private static String stripLeads(String text, List<String> leads) {
		boolean changed = true;
		while (changed) {
			changed = false;
			for (String lead : leads) {
				if (text.startsWith(lead + " ")) {
					text = text.substring(lead.length() + 1);
					changed = true;
					break;
				}
			}
		}
		return text;
	}

	private static String stripTails(String text, List<String> tails) {
		boolean changed = true;
		while (changed) {
			changed = false;
			for (String tail : tails) {
				if (text.endsWith(" " + tail)) {
					text = text.substring(0, text.length() - tail.length() - 1);
					changed = true;
					break;
				}
			}
		}
		return text;
	}
}

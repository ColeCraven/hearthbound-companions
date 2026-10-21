package io.github.tekless.hearthbound.core.intent;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads plain chat that isn't addressed to anyone ("build a house", "let's build a house", "we need a farm",
 * "can someone get wood", "let's set up camp here") as a job for whoever is around. Only real requests count:
 * "I built a house yesterday", "I love building houses" and "we don't need a farm" are left alone, and so is
 * anything too vague to act on ("build something"), which the normal one-to-one path asks about instead.
 *
 * <p>Pure Java, so it is covered by plain JUnit tests.
 */
public final class TeamRequest {
	/** The jobs a group can pick up from plain chat. */
	public enum Kind {
		HOUSE, SHELTER, WALL, BRIDGE, CAMP, FARM, STOREHOUSE, GATHER;

		/** Can several builders share this one between them? */
		public boolean sharedBuild() {
			return this == HOUSE || this == SHELTER || this == WALL || this == STOREHOUSE;
		}
	}

	/**
	 * @param kind  what to do
	 * @param item  for a gather, what to get ("logs", "cobblestone"); otherwise the thing named ("house")
	 * @param count for a gather, how many (null when not said)
	 */
	public record Job(Kind kind, String item, Integer count) {
	}

	private static final Pattern STATEMENT = Pattern.compile(
			"\\b(yesterday|last (night|time|week|year)|earlier today|the other day|used to|back home|once upon)\\b"
					+ "|^(i|we|you|he|she|they|someone|somebody)( just| already| finally| once)? (built|made|finished|gathered|got|had|did|planted|chopped|collected|set up|put up|found|saw|have built|have made|have got)\\b"
					+ "|^(im|i am|i was|i will|ill|i might|i may|we were|we will|well|im going to|im gonna|i was going to)\\b"
					+ "|^(do|does|did|have|has|is|are|was|were|isnt|arent|wasnt) (we|you|they|i|it|there|that|this)\\b"
					+ "|\\b(dont|do not|doesnt|no) (need|want|have to)\\b|\\bno need\\b|\\bnever mind\\b|\\bnevermind\\b");
	/** "I love building houses" is about the player, not a job. */
	private static final Pattern FEELING = Pattern.compile("^i (really |also |just |kind of |kinda )?(like|love|enjoy|adore|hate|dislike|miss)\\b");
	private static final Pattern FILLERS = Pattern.compile("^((please|pls|ok|okay|right|alright|so|hey|now|then|and|guys|team|folks|well|oh) )+");
	private static final Pattern LEAD_IN = Pattern.compile(
			"^(can someone|could someone|can somebody|could somebody|would someone|will someone|someone|somebody|anyone|can anyone|could anyone|anybody"
					+ "|lets|let us|can we|could we|shall we|should we|we should|we could|we gotta|we have to|we must|we ought to|time to|how about we|why dont we|what about we"
					+ "|i need you to|i want you to|id like you to|i would like you to|can you|could you|would you|will you"
					+ "|go and|go)( please)?\\s+");
	private static final Pattern NEED = Pattern.compile("^(we|i) (really |also |still )?(need|want|could use|would like|d like)( to (build|make|have|get|set up|put up))?"
			+ "( (a|an|some|the|more|another|new|our own|my own|a new|a few))? (?<thing>.+)$");
	private static final Pattern BUILD = Pattern.compile("^(build|make|put up|set up|construct|raise|knock up|throw up|start|lay out|plant|create|pitch)"
			+ "( (us|me|ourselves))?( (a|an|some|the|our|another|new|a new|a little|a small|a proper))? (?<thing>.+)$");
	private static final Pattern CAMP_HERE = Pattern.compile("^(camp|make camp|set up camp|pitch camp|pitch a tent|camp (here|out|for the night))\\b.*");
	private static final Pattern FILLER_TAIL = Pattern.compile("( (here|there|now|please|for us|for me|together|over here|right here|for the night|for tonight|nearby|somewhere|then|guys|team|everyone))+$");

	private TeamRequest() {
	}

	/** The job asked for, or empty when this isn't a plain request for one. */
	public static Optional<Job> parse(String raw, CommandParser parser) {
		String c = normalise(raw);
		if (c.isEmpty() || STATEMENT.matcher(c).find() || FEELING.matcher(c).find()) {
			return Optional.empty();
		}
		String rest = c;
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Pattern pattern : new Pattern[] {FILLERS, LEAD_IN}) {
				Matcher lead = pattern.matcher(rest);
				if (lead.find() && lead.end() < rest.length()) {
					rest = rest.substring(lead.end());
					changed = true;
				}
			}
		}
		if (CAMP_HERE.matcher(rest).matches()) {
			return Optional.of(new Job(Kind.CAMP, "camp", null));
		}
		Matcher need = NEED.matcher(rest);
		if (need.matches()) {
			Optional<Kind> kind = structure(need.group("thing"));
			if (kind.isPresent()) {
				return Optional.of(new Job(kind.get(), thingWord(need.group("thing")), null));
			}
		}
		Matcher build = BUILD.matcher(rest);
		if (build.matches()) {
			Optional<Kind> kind = structure(build.group("thing"));
			return kind.map(k -> new Job(k, thingWord(build.group("thing")), null));
		}
		return gather(rest, parser);
	}

	/** "get wood", "we need some stone", "can someone get 16 oak logs": one gather job, for one of them. */
	private static Optional<Job> gather(String rest, CommandParser parser) {
		if (!(parser.parse(rest) instanceof ParseResult.Command command) || command.intent().type() != IntentType.GATHER) {
			return Optional.empty();
		}
		String item = command.intent().item();
		if (item == null || item.isBlank() || java.util.Set.of("that", "this", "it", "them", "those", "these").contains(item)) {
			return Optional.empty(); // pointing at something: the one-to-one path works out what
		}
		return Optional.of(new Job(Kind.GATHER, item, command.intent().count()));
	}

	/** The structure a phrase names, if any we can build. Vague phrases ("something", "this") name nothing. */
	static Optional<Kind> structure(String phrase) {
		String p = FILLER_TAIL.matcher(phrase.trim()).replaceAll("").trim();
		if (p.matches(".*\\b(storehouse|storeroom|store room|pantry|larder|kitchen|cookhouse|granary)s?\\b.*")) {
			return Optional.of(Kind.STOREHOUSE);
		}
		if (p.matches(".*\\b(farm|farmland|field|crop field|crops|vegetable patch|wheat field)s?\\b.*")) {
			return Optional.of(Kind.FARM);
		}
		if (p.matches(".*\\b(camp|campsite|campfire|camp fire)s?\\b.*")) {
			return Optional.of(Kind.CAMP);
		}
		if (p.matches(".*\\b(house|home|cottage|cabin|place to live|place to sleep)s?\\b.*")) {
			return Optional.of(Kind.HOUSE);
		}
		if (p.matches(".*\\b(shelter|hut|shack|tent|hideout|base|bunker)s?\\b.*")) {
			return Optional.of(Kind.SHELTER);
		}
		if (p.matches(".*\\b(wall|barrier|barricade|fence)s?\\b.*")) {
			return Optional.of(Kind.WALL);
		}
		if (p.matches(".*\\b(bridge|walkway)s?\\b.*")) {
			return Optional.of(Kind.BRIDGE);
		}
		return Optional.empty();
	}

	private static String thingWord(String phrase) {
		return FILLER_TAIL.matcher(phrase.trim()).replaceAll("").trim();
	}

	static String normalise(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.toLowerCase(java.util.Locale.ROOT).replace('’', '\'').replace("'", "").replaceAll("[^a-z0-9 ]", " ")
				.replaceAll("\\s+", " ").trim();
	}
}

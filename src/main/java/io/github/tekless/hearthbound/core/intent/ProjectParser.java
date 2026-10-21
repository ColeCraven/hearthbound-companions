package io.github.tekless.hearthbound.core.intent;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads a big request ("everyone should have their own house", "build a farm and start harvesting food",
 * "a building for us to store and cook food") into the few projects companions can really carry out:
 * houses, a farm, a storehouse with a kitchen. Anything else is left to the normal order parser.
 */
public final class ProjectParser {
	/**
	 * @param houses     build houses (one each, or shared when blocks are short)
	 * @param farm       lay out a farm plot and work it
	 * @param storehouse build a small building with a chest and a furnace
	 * @param collectors collectors should gather materials and pass them on
	 * @param builders   builders are mentioned as helpers
	 * @param share      sharing a house was mentioned
	 */
	public record Plan(boolean houses, boolean farm, boolean storehouse, boolean collectors, boolean builders, boolean share) {
		public boolean any() {
			return houses || farm || storehouse;
		}
	}

	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
	private static final Pattern HOUSES = Pattern.compile(".*(\\b(?:own|their|a|some|new|more|the)\\s+(?:\\w+\\s+)?(?:houses?|homes?|cottages?|cabins?)\\b"
			+ "|\\b(?:build|building|make|put up|construct|raise)\\s+(?:\\w+\\s+){0,3}(?:houses|homes|cottages|cabins)\\b"
			+ "|\\bexpand\\s+(?:\\w+\\s+){0,2}(?:village|town|settlement)\\b).*", FLAGS);
	private static final Pattern FARM = Pattern.compile(".*(\\b(?:build|make|lay out|set up|start|dig|create|plant)\\s+(?:\\w+\\s+){0,2}(?:farm|farmland|crop field|field|garden|plot)\\b"
			+ "|\\b(?:start|begin)\\s+(?:harvesting|farming|growing|planting)\\b|\\bgrow\\s+(?:some\\s+)?(?:food|crops|wheat)\\b).*", FLAGS);
	private static final Pattern STOREHOUSE = Pattern.compile(".*(\\b(?:storehouse|storeroom|store room|pantry|kitchen|granary|larder|cookhouse)\\b"
			+ "|\\b(?:building|house|hut|room|place)\\s+(?:\\w+\\s+){0,3}(?:to|for)\\s+(?:\\w+\\s+){0,2}(?:store|storing|cook)"
			+ "|\\bstore\\s+(?:and|or)\\s+cook\\b).*", FLAGS);
	private static final Pattern COLLECTORS = Pattern.compile(".*\\b(?:collectors?|gatherers?|gather(?:ing)?\\s+(?:the\\s+)?(?:materials|supplies|resources))\\b.*", FLAGS);
	private static final Pattern BUILDERS = Pattern.compile(".*\\bbuilders?\\b.*", FLAGS);
	private static final Pattern SHARE = Pattern.compile(".*\\b(?:share|sharing|shared)\\b.*", FLAGS);

	private ProjectParser() {
	}

	/** The projects asked for, or empty when this isn't a project request. */
	public static Optional<Plan> parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		String text = raw.toLowerCase(Locale.ROOT).replace('\n', ' ');
		boolean storehouse = STOREHOUSE.matcher(text).matches();
		boolean farm = FARM.matcher(text).matches();
		// "a building for us to store and cook food" is a storehouse, not a house for everyone.
		boolean houses = HOUSES.matcher(text).matches() && !(storehouse && !text.contains("own house") && !text.contains("houses"));
		Plan plan = new Plan(houses, farm, storehouse, COLLECTORS.matcher(text).matches(), BUILDERS.matcher(text).matches(), SHARE.matcher(text).matches());
		return plan.any() ? Optional.of(plan) : Optional.empty();
	}
}

package io.github.tekless.hearthbound.core.life;

import java.util.Random;
import java.util.Set;

/**
 * How two companions who live together get on. Time spent together (meals, evenings, a shift side by side)
 * nudges it up; a few personalities rub each other up the wrong way, so for them it can drift down instead.
 * The score runs from -50 to 100, and only ever moves a point at a time.
 */
public final class Bonds {
	public static final int FRIENDS = 30;
	public static final int RIVALS = -10;

	/** Pairs that clash: a rogue and a guardian, a sellsword and a naturalist, a mystic and a farmhand. */
	private static final Set<String> CLASHES = Set.of("guardian|rogue", "naturalist|sellsword", "farmhand|mystic");

	private Bonds() {
	}

	public static boolean clash(String archetypeA, String archetypeB) {
		String pair = archetypeA.compareTo(archetypeB) < 0 ? archetypeA + "|" + archetypeB : archetypeB + "|" + archetypeA;
		return CLASHES.contains(pair);
	}

	/**
	 * The change after some time together: +1 as a rule, but for a clashing pair it is a coin toss that falls
	 * slightly more often on -1, so some of them become rivals and some become unlikely friends.
	 */
	public static int afterTimeTogether(String archetypeA, String archetypeB, Random random) {
		if (clash(archetypeA, archetypeB)) {
			return random.nextInt(100) < 55 ? -1 : 1;
		}
		return 1;
	}

	/** "friends", "rivals" or "neighbours". */
	public static String describe(int score) {
		return score >= FRIENDS ? "friends" : score <= RIVALS ? "rivals" : "neighbours";
	}
}

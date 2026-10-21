package io.github.tekless.hearthbound.core.life;

import java.util.Random;

/**
 * How a crop gets on while nobody is around to see it, worked out honestly instead of block by block.
 * In the game a crop on wet farmland grows a stage roughly every five or six thousand ticks (a quarter of a
 * day), and about half as fast on dry ground. A ripe crop is harvested (when someone is there to do it and
 * there is room to store the harvest), replanted, and grows again from the start.
 */
public final class CropGrowth {
	/** Average ticks per growth stage on wet and on dry farmland. */
	static final double WET_TICKS = 5500;
	static final double DRY_TICKS = 11000;

	/**
	 * The outcome for one crop.
	 *
	 * @param age       its growth stage now
	 * @param harvested how many times it was harvested and replanted
	 */
	public record Result(int age, int harvested) {
	}

	private CropGrowth() {
	}

	/**
	 * @param age          growth stage at the start
	 * @param maxAge       the ripe stage
	 * @param elapsed      ticks that passed
	 * @param wet          on hydrated farmland
	 * @param maxHarvests  how many harvests are allowed for this crop (0 when nobody may harvest it)
	 */
	public static Result simulate(int age, int maxAge, long elapsed, boolean wet, int maxHarvests, Random random) {
		if (elapsed <= 0 || maxAge <= 0) {
			return new Result(Math.min(age, maxAge), 0);
		}
		double expected = elapsed / (wet ? WET_TICKS : DRY_TICKS);
		// Some crops are luckier than others: a spread of about a quarter either way.
		double spread = expected * (0.75 + random.nextDouble() * 0.5);
		int stages = (int) Math.floor(spread + random.nextDouble());
		int total = age + stages;
		int harvested = 0;
		while (total >= maxAge && harvested < maxHarvests) {
			harvested++;
			total -= maxAge; // replanted at stage 0, and what time was left keeps it growing
		}
		return new Result(Math.min(total, maxAge), harvested);
	}

	/**
	 * Seeds or extra produce from one ripe crop, the way the game's own drops work: one guaranteed, plus three
	 * tries at a little over even odds.
	 */
	public static int bonusDrops(Random random) {
		int count = 0;
		for (int i = 0; i < 3; i++) {
			if (random.nextDouble() < 0.5714) {
				count++;
			}
		}
		return count;
	}
}

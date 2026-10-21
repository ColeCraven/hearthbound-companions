package io.github.tekless.hearthbound.core.life;

/**
 * A companion's day, worked out from the time of day alone (no world access, so it is plain JUnit
 * material). Day time follows the game: 0 is six in the morning, 6000 is noon, 12000 is six in the
 * evening and 18000 is midnight.
 *
 * <p>The shape is the same for everyone (up at dawn, work, a midday meal, work or leisure, the
 * evening together, bed), and {@link Traits} move it around a little per personality and per person,
 * so a hub doesn't wake up as one.
 */
public final class Schedule {
	public static final int DAY = 24000;
	/** How long getting up takes, from the moment they wake. */
	static final int WAKE_LENGTH = 700;
	static final int MEAL_START = 5600;
	static final int MEAL_LENGTH = 1200;
	/** When people drift over to the meeting spot. */
	static final int SOCIAL_START = 11000;
	/** Dusk: the fire gets lit. */
	public static final int DUSK = 12000;
	/** After dark: the time for stories round the fire. */
	public static final int NIGHT = 13000;
	static final int BEDTIME = 13800;
	/** A full moon keeps everyone up for the festival. */
	static final int FESTIVAL_EXTRA = 1500;

	/**
	 * What else is going on.
	 *
	 * @param raining    rain or snow where they live
	 * @param fullMoon   tonight is a full moon
	 * @param hunger     0 (just ate) to 100 (starving)
	 * @param tiredness  0 (rested) to 100 (dead on their feet)
	 * @param loneliness 0 (plenty of company) to 100 (nobody to talk to for ages)
	 */
	public record Conditions(boolean raining, boolean fullMoon, int hunger, int tiredness, int loneliness) {
		public static final Conditions CALM = new Conditions(false, false, 0, 0, 0);
	}

	/**
	 * Per-companion variation: when they get up and turn in, when they eat, and how often an afternoon is
	 * spent working or taking it easy.
	 *
	 * @param wakeOffset    ticks after (or, negative, before) six in the morning
	 * @param bedOffset     ticks after (or before) the usual bedtime
	 * @param mealOffset    ticks after (or before) the usual midday meal
	 * @param leisureChance percent of afternoons spent on leisure instead of work
	 * @param seed          for the day-to-day choices
	 */
	public record Traits(int wakeOffset, int bedOffset, int mealOffset, int leisureChance, long seed) {
		/** Traits from a personality ({@code farmhand}, {@code mystic}...) and a stable seed (their UUID bits). */
		public static Traits of(String archetypeId, long seed) {
			int wake;
			int bed;
			int leisure;
			switch (archetypeId) {
				case "farmhand" -> { // up with the hens, in bed early
					wake = -900;
					bed = -600;
					leisure = 25;
				}
				case "guardian" -> {
					wake = -500;
					bed = 0;
					leisure = 30;
				}
				case "sellsword" -> {
					wake = -300;
					bed = 300;
					leisure = 40;
				}
				case "rogue" -> { // late to bed, late to rise
					wake = 700;
					bed = 900;
					leisure = 60;
				}
				case "mystic" -> { // a night owl
					wake = 1200;
					bed = 2200;
					leisure = 50;
				}
				default -> {
					wake = 0;
					bed = 200;
					leisure = 45;
				}
			}
			java.util.Random random = new java.util.Random(seed);
			return new Traits(wake + random.nextInt(801) - 400, bed + random.nextInt(1001) - 500, random.nextInt(601) - 300,
					leisure, seed);
		}
	}

	private Schedule() {
	}

	/**
	 * What they should be doing now.
	 *
	 * @param dayTime the time of day, 0 to 23999
	 * @param day     which day it is (for choices that change from day to day)
	 */
	public static Activity at(long dayTime, long day, Traits traits, Conditions conditions) {
		Activity planned = planned(dayTime, day, traits, conditions);
		if (planned == Activity.SLEEP) {
			return planned;
		}
		if (conditions.raining()) {
			return Activity.SHELTER;
		}
		if (conditions.hunger() >= 80 && (planned == Activity.WORK || planned == Activity.LEISURE)) {
			return Activity.MEAL;
		}
		if (conditions.loneliness() >= 70 && planned == Activity.LEISURE) {
			return Activity.SOCIAL;
		}
		return planned;
	}

	/** The plan before weather and needs get a say. */
	static Activity planned(long dayTime, long day, Traits traits, Conditions conditions) {
		int t = mod(dayTime);
		int wake = mod(traits.wakeOffset());
		int bed = bedtime(traits, conditions);
		int awake = mod(bed - wake);
		int since = mod(t - wake);
		if (since >= awake) {
			return Activity.SLEEP;
		}
		if (since < WAKE_LENGTH) {
			return Activity.WAKE;
		}
		int mealStart = mod(MEAL_START + traits.mealOffset() - wake);
		if (since < mealStart) {
			return Activity.WORK;
		}
		if (since < mealStart + MEAL_LENGTH) {
			return Activity.MEAL;
		}
		if (since < mod(SOCIAL_START - wake)) {
			return leisureDay(traits, day) ? Activity.LEISURE : Activity.WORK;
		}
		return Activity.SOCIAL;
	}

	/** Bedtime for this companion tonight: later at a full moon, earlier when worn out. */
	public static int bedtime(Traits traits, Conditions conditions) {
		int bed = BEDTIME + traits.bedOffset();
		if (conditions.fullMoon()) {
			bed += FESTIVAL_EXTRA;
		}
		if (conditions.tiredness() >= 85) {
			bed -= 1200;
		}
		return Math.max(SOCIAL_START + 300, Math.min(DAY - 1, bed));
	}

	/** When they get up, 0 to 23999. */
	public static int wakeTime(Traits traits) {
		return mod(traits.wakeOffset());
	}

	/** Whether this afternoon is a day off. */
	static boolean leisureDay(Traits traits, long day) {
		long mixed = traits.seed() * 6364136223846793005L + day * 1442695040888963407L;
		return Math.floorMod(mixed >>> 33, 100) < traits.leisureChance();
	}

	/** Phase 0 is a full moon, as in the game: the phase moves on once a day through eight. */
	public static boolean fullMoon(long clockTime) {
		return Math.floorMod(clockTime / DAY, 8) == 0;
	}

	static int mod(long value) {
		return (int) Math.floorMod(value, (long) DAY);
	}
}

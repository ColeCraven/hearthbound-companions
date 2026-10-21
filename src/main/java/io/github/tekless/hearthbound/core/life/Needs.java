package io.github.tekless.hearthbound.core.life;

/**
 * Hunger, rest and company for a companion who lives somewhere. They creep up on their own and are put
 * right by eating, sleeping and spending time with people. They steer the day ({@link Schedule}) and how
 * chatty someone is, and that's all: nobody is harmed by an empty stomach.
 *
 * <p>All three run from 0 (fine) to 100. A game day is twenty minutes, so the rates are per second of
 * play: hunger fills in about half a day, tiredness over a full waking day.
 */
public final class Needs {
	public static final int MAX = 100;

	private int hunger;
	private int tiredness;
	private int loneliness;
	private int hungerParts;
	private int restParts;
	private int companyParts;

	public Needs() {
	}

	public Needs(int hunger, int tiredness, int loneliness) {
		this.hunger = clamp(hunger);
		this.tiredness = clamp(tiredness);
		this.loneliness = clamp(loneliness);
	}

	/**
	 * Moves the needs on.
	 *
	 * @param activity what they were doing
	 * @param seconds  how long for
	 * @param company  someone (a companion or a player) was close by
	 */
	public void pass(Activity activity, int seconds, boolean company) {
		hungerParts += seconds;
		while (hungerParts >= 6) {
			hungerParts -= 6;
			hunger = clamp(hunger + 1);
		}
		if (activity == Activity.SLEEP) {
			restParts += seconds;
			while (restParts >= 3) {
				restParts -= 3;
				tiredness = clamp(tiredness - 1);
			}
		} else {
			restParts += seconds;
			int every = activity == Activity.WORK ? 7 : 10;
			while (restParts >= every) {
				restParts -= every;
				tiredness = clamp(tiredness + 1);
			}
		}
		if (company) {
			companyParts = 0;
			loneliness = clamp(loneliness - Math.max(1, seconds));
		} else if (activity != Activity.SLEEP) {
			companyParts += seconds;
			while (companyParts >= 8) {
				companyParts -= 8;
				loneliness = clamp(loneliness + 1);
			}
		}
	}

	/** Takes the values of another (loading a save). */
	public void copyFrom(Needs other) {
		hunger = other.hunger;
		tiredness = other.tiredness;
		loneliness = other.loneliness;
	}

	/** A proper meal. */
	public void eat() {
		hunger = 0;
		hungerParts = 0;
	}

	/** A snack takes the edge off. */
	public void snack() {
		hunger = clamp(hunger - 40);
	}

	public int hunger() {
		return hunger;
	}

	public int tiredness() {
		return tiredness;
	}

	public int loneliness() {
		return loneliness;
	}

	/** The need that is worst right now, if any is pressing: "hungry", "tired", "lonely", or null. */
	public String pressing() {
		int worst = Math.max(hunger, Math.max(tiredness, loneliness));
		if (worst < 70) {
			return null;
		}
		if (worst == hunger) {
			return "hungry";
		}
		return worst == tiredness ? "tired" : "lonely";
	}

	/** How chatty they feel: lonely people talk more, tired and hungry ones less. Multiplies the chattiness setting. */
	public double chattiness() {
		double factor = 1.0;
		if (loneliness >= 60) {
			factor *= 1.5;
		}
		if (tiredness >= 75) {
			factor *= 0.5;
		}
		if (hunger >= 75) {
			factor *= 0.7;
		}
		return factor;
	}

	/** One word for their mood, for status replies: "cheerful", "fine", "a bit low". */
	public String mood() {
		int total = hunger + tiredness + loneliness;
		return total < 90 ? "cheerful" : total < 180 ? "fine" : "a bit low";
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(MAX, value));
	}
}

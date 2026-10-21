package io.github.tekless.hearthbound.core.task;

/**
 * Decides how a following companion moves, given how far away its owner is. Two-step catch-up
 * (hurry, then teleport) as used by popular follower overhauls; a dead band with hysteresis keeps
 * companions from stuttering when the player shuffles around.
 */
public final class FollowPolicy {
	/** Beyond this the companion hurries. */
	public static final double HURRY_DISTANCE = 10.0;
	/** Beyond this (or in another dimension) the companion teleports to a safe spot near the owner. */
	public static final double TELEPORT_DISTANCE = 32.0;
	/** Vertical gap that counts as separated even when the horizontal distance is short (cliffs, shafts). */
	public static final double TELEPORT_VERTICAL = 16.0;

	public enum Action {
		/** Within the comfortable band: stay put, look at the owner. */
		HOLD,
		WALK,
		HURRY,
		TELEPORT
	}

	private final double min;
	private final double max;

	public FollowPolicy(double min, double max) {
		if (min <= 0 || max < min) {
			throw new IllegalArgumentException("bad follow band " + min + ".." + max);
		}
		this.min = min;
		this.max = max;
	}

	/**
	 * @param horizontal     horizontal distance to the owner in blocks
	 * @param vertical       absolute height difference in blocks
	 * @param sameDimension  whether owner and companion are in the same dimension
	 * @param currentlyMoving whether the companion is already walking towards the owner
	 * @param stuck          whether the companion has made no progress for a while
	 */
	public Action decide(double horizontal, double vertical, boolean sameDimension, boolean currentlyMoving, boolean stuck) {
		if (!sameDimension || horizontal > TELEPORT_DISTANCE || vertical > TELEPORT_VERTICAL) {
			return Action.TELEPORT;
		}
		if (stuck && horizontal > max + 4) {
			return Action.TELEPORT;
		}
		if (horizontal > HURRY_DISTANCE) {
			return Action.HURRY;
		}
		if (horizontal > max) {
			return Action.WALK;
		}
		// Hysteresis: once walking, keep going until comfortably close.
		if (currentlyMoving && horizontal > min) {
			return Action.WALK;
		}
		return Action.HOLD;
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}
}

package io.github.tekless.hearthbound.core.task;

/**
 * Notices when a companion that is trying to move has barely moved for a while (walking into a
 * wall, circling a fence, pinned in water). Callers decide how to recover: re-path, hop, teleport,
 * or give up and explain.
 */
public final class StuckDetector {
	private final int windowTicks;
	private final double minProgress;

	private double anchorX;
	private double anchorZ;
	private int ticksSinceAnchor;
	private boolean hasAnchor;
	private int stuckEvents;

	public StuckDetector(int windowTicks, double minProgress) {
		this.windowTicks = windowTicks;
		this.minProgress = minProgress;
	}

	public StuckDetector() {
		this(60, 0.75);
	}

	/**
	 * Feed one tick. Returns true once per window in which the companion wanted to move but covered
	 * less than the minimum horizontal distance.
	 */
	public boolean update(double x, double z, boolean tryingToMove) {
		if (!tryingToMove) {
			reset();
			return false;
		}
		if (!hasAnchor) {
			anchor(x, z);
			return false;
		}
		ticksSinceAnchor++;
		double dx = x - anchorX;
		double dz = z - anchorZ;
		if (dx * dx + dz * dz >= minProgress * minProgress) {
			anchor(x, z);
			stuckEvents = 0;
			return false;
		}
		if (ticksSinceAnchor >= windowTicks) {
			anchor(x, z);
			stuckEvents++;
			return true;
		}
		return false;
	}

	/** Consecutive stuck windows; useful for escalating recovery. */
	public int consecutiveStuck() {
		return stuckEvents;
	}

	public void reset() {
		hasAnchor = false;
		ticksSinceAnchor = 0;
		stuckEvents = 0;
	}

	private void anchor(double x, double z) {
		anchorX = x;
		anchorZ = z;
		ticksSinceAnchor = 0;
		hasAnchor = true;
	}
}

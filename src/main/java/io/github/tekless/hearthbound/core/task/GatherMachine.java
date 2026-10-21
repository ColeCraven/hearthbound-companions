package io.github.tekless.hearthbound.core.task;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The "gather N items and bring them back" order as an explicit state machine.
 *
 * <pre>
 * SEARCHING -> APPROACHING -> BREAKING -> SEARCHING ... -> RETURNING -> (delivered) SUCCEEDED
 *                                                      \-> (partial)   FAILED with reason
 * </pre>
 *
 * The order only {@link TaskStatus#SUCCEEDED succeeds} once the full amount has been handed to
 * the owner. Every way it can stop early produces a concrete {@link FailureReason}, and any
 * items already collected are still delivered.
 */
public final class GatherMachine<T> {
	/** Ticks to reach one target before giving up on it. */
	static final int APPROACH_TIMEOUT = 400;
	/** Ticks spent breaking one block before giving up on it. */
	static final int BREAK_TIMEOUT = 600;
	/** Ticks to walk back to the owner before giving up. */
	static final int RETURN_TIMEOUT = 2400;
	/** Ticks the return trip may be blocked before failing as stuck. */
	static final int RETURN_STUCK_LIMIT = 200;

	public enum Phase {
		SEARCHING,
		APPROACHING,
		BREAKING,
		RETURNING,
		FINISHED
	}

	/** Things worth telling the player about. Facts come from here, never from dialogue. */
	public sealed interface Event {
		record HalfWay(int have, int goal) implements Event {
		}

		record CollectedAll(int goal) implements Event {
		}

		record Delivered(int count) implements Event {
		}

		/** Stopped early; {@code delivered} items were still handed over. */
		record Partial(int delivered, int goal, FailureReason reason, String detail) implements Event {
		}

		record Failed(FailureReason reason, String detail) implements Event {
		}
	}

	private final GatherWorld<T> world;
	private final int goal;
	private final int maxTicks;
	private final Set<T> excluded = new HashSet<>();
	private final List<Event> events = new ArrayList<>();

	private Phase phase = Phase.SEARCHING;
	private TaskStatus status = TaskStatus.RUNNING;
	private T target;
	private int phaseTicks;
	private int totalTicks;
	private int returnBlockedTicks;
	private int unreachable;
	private boolean halfAnnounced;
	private FailureReason stopReason;
	private String stopDetail;
	private FailureReason failure;
	private int delivered;

	public GatherMachine(GatherWorld<T> world, int goal) {
		if (goal <= 0) {
			throw new IllegalArgumentException("goal must be positive");
		}
		this.world = world;
		this.goal = goal;
		// Generous overall budget: 30 s base + 15 s per item.
		this.maxTicks = 600 + goal * 300;
	}

	public void tick() {
		if (status.isFinished()) {
			return;
		}
		totalTicks++;
		if (totalTicks > maxTicks && phase != Phase.RETURNING) {
			stopCollecting(FailureReason.TIMED_OUT, null);
			return;
		}
		switch (phase) {
			case SEARCHING -> search();
			case APPROACHING -> approach();
			case BREAKING -> breaking();
			case RETURNING -> returning();
			case FINISHED -> {
			}
		}
	}

	public void cancel() {
		if (!status.isFinished()) {
			status = TaskStatus.CANCELLED;
			phase = Phase.FINISHED;
			target = null;
		}
	}

	private void search() {
		if (world.collected() >= goal) {
			events.add(new Event.CollectedAll(goal));
			startReturn(null, null);
			return;
		}
		if (world.inventoryFull()) {
			stopCollecting(FailureReason.INVENTORY_FULL, null);
			return;
		}
		Optional<T> next = world.findTarget(excluded);
		if (next.isEmpty()) {
			stopCollecting(unreachable > 0 ? FailureReason.UNREACHABLE : FailureReason.NONE_FOUND, null);
			return;
		}
		T candidate = next.get();
		Optional<String> tool = world.missingTool(candidate);
		if (tool.isPresent()) {
			stopCollecting(FailureReason.NEEDS_TOOL, tool.get());
			return;
		}
		target = candidate;
		enter(Phase.APPROACHING);
	}

	private void approach() {
		if (++phaseTicks > APPROACH_TIMEOUT) {
			skipTarget();
			return;
		}
		switch (world.approach(target)) {
			case ARRIVED -> enter(Phase.BREAKING);
			case MOVING -> {
			}
			case UNREACHABLE, STUCK -> skipTarget();
		}
	}

	private void breaking() {
		if (++phaseTicks > BREAK_TIMEOUT) {
			skipTarget();
			return;
		}
		switch (world.breakStep(target)) {
			case IN_PROGRESS -> {
			}
			case DONE -> {
				target = null;
				int have = world.collected();
				if (!halfAnnounced && goal >= 6 && have * 2 >= goal && have < goal) {
					halfAnnounced = true;
					events.add(new Event.HalfWay(have, goal));
				}
				enter(Phase.SEARCHING);
			}
			case GONE -> {
				target = null;
				enter(Phase.SEARCHING);
			}
		}
	}

	private void returning() {
		if (!world.ownerAvailable()) {
			fail(FailureReason.OWNER_GONE, null);
			return;
		}
		if (++phaseTicks > RETURN_TIMEOUT) {
			fail(FailureReason.STUCK, null);
			return;
		}
		switch (world.returnToOwner()) {
			case ARRIVED -> handOver();
			case MOVING -> returnBlockedTicks = 0;
			case UNREACHABLE, STUCK -> {
				if (++returnBlockedTicks > RETURN_STUCK_LIMIT) {
					fail(FailureReason.STUCK, null);
				}
			}
		}
	}

	private void handOver() {
		int have = Math.min(world.collected(), goal);
		delivered = have > 0 ? world.deliver(have) : 0;
		if (stopReason == null && delivered >= goal) {
			events.add(new Event.Delivered(delivered));
			status = TaskStatus.SUCCEEDED;
		} else {
			FailureReason reason = stopReason != null ? stopReason : FailureReason.INVENTORY_FULL;
			events.add(new Event.Partial(delivered, goal, reason, stopDetail));
			failure = reason;
			status = TaskStatus.FAILED;
		}
		phase = Phase.FINISHED;
	}

	/** Stops looking for more; brings back what was found, or fails if nothing was. */
	private void stopCollecting(FailureReason reason, String detail) {
		if (world.collected() > 0) {
			startReturn(reason, detail);
		} else {
			fail(reason, detail);
		}
	}

	private void startReturn(FailureReason reason, String detail) {
		stopReason = reason;
		stopDetail = detail;
		target = null;
		enter(Phase.RETURNING);
	}

	private void skipTarget() {
		excluded.add(target);
		unreachable++;
		target = null;
		enter(Phase.SEARCHING);
	}

	private void fail(FailureReason reason, String detail) {
		failure = reason;
		status = TaskStatus.FAILED;
		phase = Phase.FINISHED;
		target = null;
		events.add(new Event.Failed(reason, detail));
	}

	private void enter(Phase next) {
		phase = next;
		phaseTicks = 0;
		returnBlockedTicks = 0;
	}

	/** Returns and clears the events produced since the last call. */
	public List<Event> drainEvents() {
		List<Event> copy = List.copyOf(events);
		events.clear();
		return copy;
	}

	public TaskStatus status() {
		return status;
	}

	public Phase phase() {
		return phase;
	}

	public FailureReason failure() {
		return failure;
	}

	public int goal() {
		return goal;
	}

	public int delivered() {
		return delivered;
	}

	public T currentTarget() {
		return target;
	}
}

package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;

/**
 * One thing a companion has been told to do. Orders are deterministic game code: they move the
 * companion, change the world, and report progress. Dialogue never decides an order's outcome.
 */
public interface CompanionOrder {
	Kind kind();

	/** Called every tick the companion is free to work on the order (not fighting, not downed). */
	void tick(CompanionEntity companion);

	/** Called when combat or another behaviour takes over movement for a while. */
	default void pause(CompanionEntity companion) {
		companion.getNavigation().stop();
	}

	/** Stops the order for good. */
	void cancel(CompanionEntity companion);

	TaskStatus status();

	/** Short description for status replies and the HUD, e.g. "gathering oak logs (4/16)". */
	String describe();

	/** What's holding the job up right now, in a few words ("short of 3 oak planks"), or null if nothing is. */
	default @org.jspecify.annotations.Nullable String blocker() {
		return null;
	}

	enum Kind {
		FOLLOW,
		WAIT,
		COME,
		GATHER,
		GIVE,
		ATTACK,
		MINE,
		BUILD,
		HOME,
		PROJECT,
		/** Putting things into, or taking them out of, a chest the player pointed at. */
		STORE,
		/** Smelting or crafting something on request. */
		CRAFT;

		/** Follow and wait are standing modes; the others are tasks that end. */
		public boolean isMode() {
			return this == FOLLOW || this == WAIT || this == HOME;
		}
	}
}

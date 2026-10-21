package io.github.tekless.hearthbound.core.task;

import java.util.Optional;
import java.util.Set;

/**
 * Everything {@link GatherMachine} needs from the game world. The real implementation talks to
 * Minecraft; tests use a small fake, so the decision logic is tested without starting a game.
 *
 * @param <T> how a target is identified (a block position in the game)
 */
public interface GatherWorld<T> {
	/** Nearest matching target within range that isn't excluded, if any. */
	Optional<T> findTarget(Set<T> excluded);

	/** Required tool that the companion lacks for this target, or empty if it can break it. */
	Optional<String> missingTool(T target);

	/** Moves towards a spot from which the target can be broken. */
	Move approach(T target);

	/** Advances breaking the target by one tick. */
	Break breakStep(T target);

	/** How many of the requested items the companion has collected since the order began. */
	int collected();

	boolean inventoryFull();

	/** Whether the owner can still be reached (online, same dimension). */
	boolean ownerAvailable();

	/** Moves towards the owner to hand over the items. */
	Move returnToOwner();

	/** Hands over up to {@code max} collected items; returns how many were given. */
	int deliver(int max);

	enum Move {
		ARRIVED,
		MOVING,
		UNREACHABLE,
		STUCK
	}

	enum Break {
		IN_PROGRESS,
		DONE,
		/** The block changed or vanished (someone else broke it). */
		GONE
	}
}

package io.github.tekless.hearthbound.world;

import io.github.tekless.hearthbound.life.HomeAreas;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Where farmers may put food: the chests of storehouses companions built themselves, and chests a player marked
 * as storage ({@code /companion setplace storage}) or listed in the hub's config. A chest a player placed and
 * didn't mark is never touched. Saved with the world (see {@link HomeAreas}).
 */
public final class Storehouses {
	private Storehouses() {
	}

	public static void register(ServerLevel level, BlockPos chest) {
		HomeAreas.get(level.getServer()).registerStore(level, chest);
	}

	/** The nearest storage chest within {@code range} blocks that is still a chest, or null. */
	public static BlockPos nearest(ServerLevel level, BlockPos from, double range) {
		return HomeAreas.get(level.getServer()).nearestStorage(level, from, range);
	}

	/** Nothing to forget any more: storehouses are saved with the world. */
	public static void reset() {
	}
}

package io.github.tekless.hearthbound.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * The chests of storehouses that companions built themselves. Farmers only ever put food into these;
 * a chest a player placed is never touched. Remembered for as long as the server runs.
 */
public final class Storehouses {
	private record Store(String dimension, BlockPos pos) {
	}

	private static final List<Store> STORES = new ArrayList<>();

	private Storehouses() {
	}

	public static void register(ServerLevel level, BlockPos chest) {
		STORES.add(new Store(level.dimension().identifier().toString(), chest.immutable()));
	}

	/** The nearest storehouse chest within {@code range} blocks that is still a chest, or null. */
	public static BlockPos nearest(ServerLevel level, BlockPos from, double range) {
		String dimension = level.dimension().identifier().toString();
		BlockPos best = null;
		double bestDistance = range * range;
		for (Store store : STORES) {
			if (!store.dimension().equals(dimension) || !level.isLoaded(store.pos()) || !level.getBlockState(store.pos()).is(Blocks.CHEST)) {
				continue;
			}
			double distance = store.pos().distSqr(from);
			if (distance <= bestDistance) {
				best = store.pos();
				bestDistance = distance;
			}
		}
		return best;
	}

	public static void reset() {
		STORES.clear();
	}
}

package io.github.tekless.hearthbound.life;

import io.github.tekless.hearthbound.config.HearthboundConfig;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/** A box of blocks (inclusive corners): a farm, a storage barn, a kitchen. */
public record Zone(BlockPos low, BlockPos high) {
	public static Zone of(BlockPos a, BlockPos b) {
		return new Zone(new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
				new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
	}

	/** A box from the config, or null if it isn't filled in properly. */
	public static @Nullable Zone of(HearthboundConfig.@Nullable Box box) {
		if (box == null || !box.valid()) {
			return null;
		}
		int[] low = box.low();
		int[] high = box.high();
		return new Zone(new BlockPos(low[0], low[1], low[2]), new BlockPos(high[0], high[1], high[2]));
	}

	public boolean contains(BlockPos pos) {
		return pos.getX() >= low.getX() && pos.getX() <= high.getX() && pos.getY() >= low.getY() && pos.getY() <= high.getY()
				&& pos.getZ() >= low.getZ() && pos.getZ() <= high.getZ();
	}

	public BlockPos centre() {
		return new BlockPos((low.getX() + high.getX()) / 2, low.getY(), (low.getZ() + high.getZ()) / 2);
	}

	/** How many blocks it holds. */
	public long volume() {
		return (long) (high.getX() - low.getX() + 1) * (high.getY() - low.getY() + 1) * (high.getZ() - low.getZ() + 1);
	}

	public Iterable<BlockPos> blocks() {
		return BlockPos.betweenClosed(low, high);
	}

	/** The same box with a little room around it (for "near the farm"). */
	public Zone grow(int by) {
		return new Zone(low.offset(-by, -by, -by), high.offset(by, by, by));
	}
}

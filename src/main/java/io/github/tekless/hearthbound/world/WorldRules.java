package io.github.tekless.hearthbound.world;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.config.HearthboundConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where companions may break blocks on a shared server: never inside the hub or other protected
 * areas, and never right next to something a player built.
 */
public final class WorldRules {
	private WorldRules() {
	}

	/** True if the position is inside the hub's protected radius or a configured protected area. */
	public static boolean isProtected(ServerLevel level, BlockPos pos) {
		HearthboundConfig.Server server = Hearthbound.config().server;
		String dimension = level.dimension().identifier().toString();
		HearthboundConfig.Hub hub = server.hub;
		if (hub.enabled && hub.protectRadius > 0 && dimension.equals(hub.dimension)
				&& within(pos, hub.x, hub.z, hub.protectRadius)) {
			return true;
		}
		for (HearthboundConfig.Area area : server.protectedAreas) {
			if (area != null && dimension.equals(area.dimension) && within(pos, area.x, area.z, area.radius)) {
				return true;
			}
		}
		return false;
	}

	/** May a companion break this block? Not in protected areas, not beside player-made blocks. */
	public static boolean mayBreak(ServerLevel level, BlockPos pos) {
		if (isProtected(level, pos)) {
			return false;
		}
		int radius = Math.min(8, Hearthbound.config().server.buildGuardRadius);
		if (radius <= 0) {
			return true;
		}
		for (BlockPos near : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius), pos.offset(radius, radius, radius))) {
			if (level.isLoaded(near) && isManMade(level.getBlockState(near))) {
				return false;
			}
		}
		return true;
	}

	/** Blocks that (almost) only appear because someone built something. */
	public static boolean isManMade(BlockState state) {
		if (state.isAir()) {
			return false;
		}
		return state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_SLABS)
				|| state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES)
				|| state.is(BlockTags.BEDS) || state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.BANNERS)
				|| state.is(BlockTags.ALL_SIGNS) || state.is(BlockTags.CANDLES) || state.is(BlockTags.SHULKER_BOXES)
				|| state.is(BlockTags.STAIRS) || state.is(BlockTags.SLABS) || state.is(BlockTags.WALLS)
				|| state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE) || state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.FURNACE)
				|| state.is(Blocks.CHEST) || state.is(Blocks.BARREL) || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
				|| state.is(Blocks.LANTERN) || state.is(Blocks.BRICKS) || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.LADDER)
				|| state.is(Blocks.BOOKSHELF) || state.is(Blocks.SMOOTH_STONE) || state.is(Blocks.POLISHED_ANDESITE)
				|| state.is(Blocks.POLISHED_DEEPSLATE) || state.is(Blocks.DEEPSLATE_BRICKS) || state.is(Blocks.TERRACOTTA)
				|| state.is(Blocks.QUARTZ_BLOCK) || state.is(Blocks.FARMLAND);
	}

	private static boolean within(BlockPos pos, int x, int z, int radius) {
		long dx = pos.getX() - x;
		long dz = pos.getZ() - z;
		return dx * dx + dz * dz <= (long) radius * radius;
	}
}

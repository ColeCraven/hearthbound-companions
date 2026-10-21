package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.persona.Role;
import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.world.Storehouses;
import io.github.tekless.hearthbound.world.WorldRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A farmer's project: till a plot beside water, sow what they carry, then keep it for a few minutes -
 * harvest what is ripe, replant, and put the food into a storehouse chest that companions built (never
 * a chest a player placed). A plot is shared: each farmer works every n-th tile.
 */
public final class FarmOrder implements CompanionOrder {
	/** How long a farmer tends the plot before the project is done. */
	private static final int TEND_TICKS = 4800;
	private static final int TILE_PATIENCE = 400;
	private static final int REACH_SQ = 9;

	/** The tiles to till (ground blocks level with the water) and the water that keeps them wet. */
	public static final class Plot {
		private final List<BlockPos> tiles;
		private final BlockPos water;
		private final boolean placeWater;

		public Plot(List<BlockPos> tiles, BlockPos water, boolean placeWater) {
			this.tiles = tiles;
			this.water = water;
			this.placeWater = placeWater;
		}

		public int size() {
			return tiles.size();
		}

		public BlockPos water() {
			return water;
		}

		public boolean placesWater() {
			return placeWater;
		}
	}

	private enum Phase {
		PREP, SOW, TEND
	}

	private final Plot plot;
	private final int index;
	private final int farmers;
	private final List<BlockPos> mine = new ArrayList<>();
	private final Runnable onEnd;
	private BlockWork work;
	private Phase phase = Phase.PREP;
	private TaskStatus status = TaskStatus.RUNNING;
	private int sowAt;
	private int sown;
	private int harvested;
	private int stored;
	private int tendTicks;
	private int cooldown;
	private int ticksOnTarget;
	private BlockPos target;
	private boolean noSeedsSaid;

	public FarmOrder(Plot plot, int index, int farmers, Runnable onEnd) {
		this.plot = plot;
		this.index = index;
		this.farmers = Math.max(1, farmers);
		this.onEnd = onEnd;
	}

	@Override
	public Kind kind() {
		return Kind.PROJECT;
	}

	// ------------------------------------------------------------------ planning

	/** A plot beside existing water, or around a water source placed from a bucket; null if there is no spot. */
	public static Plot plan(ServerLevel level, BlockPos centre, boolean hasBucket) {
		List<BlockPos> waters = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-16, -4, -16), centre.offset(16, 4, 16))) {
			if (level.hasChunkAt(pos) && level.getFluidState(pos).isSource() && level.getFluidState(pos).is(FluidTags.WATER)
					&& level.getBlockState(pos.above()).isAir()) {
				waters.add(pos.immutable());
			}
		}
		waters.sort(Comparator.comparingDouble(p -> p.distSqr(centre)));
		BlockPos bestWater = null;
		List<BlockPos> best = List.of();
		for (int i = 0; i < Math.min(8, waters.size()); i++) {
			List<BlockPos> tiles = tilesAround(level, waters.get(i));
			if (tiles.size() > best.size()) {
				best = tiles;
				bestWater = waters.get(i);
			}
		}
		if (bestWater != null && best.size() >= 4) {
			return new Plot(best, bestWater, false);
		}
		if (hasBucket) {
			for (int ring = 3; ring <= 12; ring += 3) {
				for (int[] d : new int[][] {{ring, 0}, {-ring, 0}, {0, ring}, {0, -ring}, {ring, ring}, {-ring, -ring}, {ring, -ring}, {-ring, ring}}) {
					BlockPos column = centre.offset(d[0], 0, d[1]);
					if (!level.hasChunkAt(column)) {
						continue;
					}
					BlockPos spot = new BlockPos(column.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
							column.getX(), column.getZ()) - 1, column.getZ());
					if (isGround(level.getBlockState(spot))) {
						List<BlockPos> tiles = tilesAround(level, spot);
						if (tiles.size() >= 10) {
							return new Plot(tiles, spot, true);
						}
					}
				}
			}
		}
		return null;
	}

	/** True if there is open water within reach of the player, for telling "no water" from "no room". */
	public static boolean waterNear(ServerLevel level, BlockPos centre) {
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-16, -4, -16), centre.offset(16, 4, 16))) {
			if (level.hasChunkAt(pos) && level.getFluidState(pos).isSource() && level.getFluidState(pos).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	private static List<BlockPos> tilesAround(ServerLevel level, BlockPos water) {
		List<BlockPos> tiles = new ArrayList<>();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				BlockPos pos = water.offset(dx, 0, dz);
				if (level.hasChunkAt(pos) && isGround(level.getBlockState(pos)) && Blueprint.isOpen(level.getBlockState(pos.above()))
						&& level.getFluidState(pos.above()).isEmpty() && !WorldRules.isProtected(level, pos) && !nearManMade(level, pos)) {
					tiles.add(pos);
				}
			}
		}
		tiles.sort(Comparator.comparingDouble(p -> p.distSqr(water)));
		return tiles.size() > 20 ? new ArrayList<>(tiles.subList(0, 20)) : tiles;
	}

	private static boolean isGround(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.FARMLAND);
	}

	private static boolean nearManMade(ServerLevel level, BlockPos pos) {
		for (BlockPos near : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 2, 2))) {
			BlockState state = level.getBlockState(near);
			if (!state.is(Blocks.FARMLAND) && WorldRules.isManMade(state)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ the job

	@Override
	public void tick(CompanionEntity c) {
		if (status.isFinished()) {
			return;
		}
		ServerLevel level = (ServerLevel) c.level();
		if (cooldown > 0) {
			cooldown--;
		}
		switch (phase) {
			case PREP -> prepare(c, level);
			case SOW -> sow(c, level);
			case TEND -> tend(c, level);
		}
	}

	private void prepare(CompanionEntity c, ServerLevel level) {
		Crafting.planksFromLogs(c);
		if (c.countCarried(s -> s.is(ItemTags.HOES)) == 0 && c.getMainHandItem().is(ItemTags.HOES) == false && !Crafting.hoe(c)) {
			c.say("project.blocked.hoe", Map.of());
			finish(TaskStatus.FAILED);
			return;
		}
		if (seedFor(c) == null) {
			c.say("project.blocked.seeds", Map.of());
			finish(TaskStatus.FAILED);
			return;
		}
		if (plot.placeWater && !level.getFluidState(plot.water).is(FluidTags.WATER)) {
			// One farmer brings the water; the rest wait a little for it.
			if (index == 0) {
				if (!goNear(c, plot.water, 3.0)) {
					return;
				}
				ItemStack bucket = null;
				for (ItemStack stack : c.inventory().getItems()) {
					if (stack.is(Items.WATER_BUCKET)) {
						bucket = stack;
						break;
					}
				}
				if (bucket == null) {
					c.say("project.blocked.water", Map.of());
					finish(TaskStatus.FAILED);
					return;
				}
				level.setBlockAndUpdate(plot.water, Blocks.WATER.defaultBlockState());
				bucket.shrink(1);
				c.storeOrDrop(new ItemStack(Items.BUCKET));
			} else if (tendTicks++ < 400) {
				return;
			}
		}
		tendTicks = 0;
		for (int i = 0; i < plot.tiles.size(); i++) {
			if (i % farmers == index) {
				mine.add(plot.tiles.get(i));
			}
		}
		phase = Phase.SOW;
	}

	private void sow(CompanionEntity c, ServerLevel level) {
		if (sowAt >= mine.size()) {
			c.say("project.farm.planted", Map.of("count", Integer.toString(sown)));
			phase = Phase.TEND;
			tendTicks = 0;
			return;
		}
		BlockPos tile = mine.get(sowAt);
		if (!tileUsable(level, tile)) {
			sowAt++;
			return;
		}
		if (!reach(c, tile) || cooldown > 0) {
			return;
		}
		Block crop = cropFor(seedFor(c));
		if (crop == null) {
			if (!noSeedsSaid) {
				noSeedsSaid = true;
				c.say("project.blocked.seeds", Map.of());
			}
			sowAt = mine.size();
			return;
		}
		c.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.setBlockAndUpdate(tile, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
		if (plant(c, level, tile)) {
			sown++;
		}
		sowAt++;
		target = null;
		cooldown = 8;
	}

	private boolean tileUsable(ServerLevel level, BlockPos tile) {
		BlockState ground = level.getBlockState(tile);
		return (isGround(ground)) && (Blueprint.isOpen(level.getBlockState(tile.above())) || level.getBlockState(tile.above()).getBlock() instanceof CropBlock);
	}

	private void tend(CompanionEntity c, ServerLevel level) {
		if (++tendTicks > TEND_TICKS) {
			deposit(c, level, true);
			if (harvested > 0) {
				c.say("project.farm.harvest", Map.of("count", Integer.toString(harvested), "item", "crops"));
			}
			finish(TaskStatus.SUCCEEDED);
			return;
		}
		if (work != null && target != null) {
			BlockPos pos = target;
			if (!(level.getBlockState(pos).getBlock() instanceof CropBlock crop && crop.isMaxAge(level.getBlockState(pos)))) {
				work.clear();
				target = null;
				return;
			}
			if (!reach(c, pos)) {
				return;
			}
			BlockWork.Result result = work.step(pos);
			if (result == BlockWork.Result.DONE) {
				harvested++;
				plant(c, level, pos.below());
				target = null;
			} else if (result == BlockWork.Result.GONE) {
				target = null;
			}
			return;
		}
		if (cooldown > 0 || c.tickCount % 10 != 0) {
			return;
		}
		cooldown = 10;
		deposit(c, level, false);
		for (BlockPos tile : mine) {
			BlockPos above = tile.above();
			BlockState state = level.getBlockState(above);
			if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
				target = above;
				ticksOnTarget = 0;
				work = new BlockWork(c, Role.Task.FARM);
				return;
			}
			if (level.getBlockState(tile).is(Blocks.FARMLAND) && state.isAir() && seedFor(c) != null && reach(c, tile)) {
				plant(c, level, tile);
				return;
			}
		}
	}

	/** Plants the best seed the farmer carries on top of this farmland; false if there is none or the spot is taken. */
	private boolean plant(CompanionEntity c, ServerLevel level, BlockPos farmland) {
		Item seed = seedFor(c);
		Block crop = cropFor(seed);
		if (crop == null || !level.getBlockState(farmland).is(Blocks.FARMLAND) || !Blueprint.isOpen(level.getBlockState(farmland.above()))) {
			return false;
		}
		if (c.inventory().removeItemType(seed, 1).isEmpty()) {
			return false;
		}
		level.setBlockAndUpdate(farmland.above(), crop.defaultBlockState());
		return true;
	}

	private static Item seedFor(CompanionEntity c) {
		for (Item item : List.of(Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT_SEEDS)) {
			if (c.countCarried(s -> s.is(item)) > 0) {
				return item;
			}
		}
		return null;
	}

	private static Block cropFor(Item seed) {
		if (seed == Items.WHEAT_SEEDS) {
			return Blocks.WHEAT;
		}
		if (seed == Items.CARROT) {
			return Blocks.CARROTS;
		}
		if (seed == Items.POTATO) {
			return Blocks.POTATOES;
		}
		return seed == Items.BEETROOT_SEEDS ? Blocks.BEETROOTS : null;
	}

	/** Puts surplus food into a storehouse chest that companions built, when one is close enough. */
	private void deposit(CompanionEntity c, ServerLevel level, boolean all) {
		BlockPos chestPos = Storehouses.nearest(level, c.blockPosition(), 64);
		if (chestPos == null) {
			return;
		}
		int carried = foodToStore(c);
		if (carried < (all ? 1 : 8)) {
			return;
		}
		if (!(level.getBlockEntity(chestPos) instanceof Container chest)) {
			return;
		}
		if (c.distanceToSqr(Vec3.atCenterOf(chestPos)) > 9.0) {
			if (all) {
				c.teleportTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 1.5);
			} else {
				c.getNavigation().moveTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5, 1.0);
				return;
			}
		}
		int moved = 0;
		for (ItemStack stack : c.inventory().getItems()) {
			if (stack.isEmpty() || !isFood(stack)) {
				continue;
			}
			int keep = stack.is(Items.CARROT) || stack.is(Items.POTATO) ? 4 : 0;
			int give = Math.min(stack.getCount(), Math.max(0, c.countCarried(s -> s.is(stack.getItem())) - keep));
			for (int slot = 0; slot < chest.getContainerSize() && give > 0; slot++) {
				ItemStack there = chest.getItem(slot);
				if (there.isEmpty()) {
					chest.setItem(slot, new ItemStack(stack.getItem(), give));
					stack.shrink(give);
					moved += give;
					give = 0;
				} else if (there.is(stack.getItem()) && there.getCount() < there.getMaxStackSize()) {
					int add = Math.min(give, there.getMaxStackSize() - there.getCount());
					there.grow(add);
					stack.shrink(add);
					moved += add;
					give -= add;
				}
			}
		}
		if (moved > 0) {
			chest.setChanged();
			stored += moved;
			c.say("project.farm.stored", Map.of("count", Integer.toString(moved)));
		}
	}

	private static boolean isFood(ItemStack stack) {
		return stack.is(Items.WHEAT) || stack.is(Items.BEETROOT) || stack.is(Items.CARROT) || stack.is(Items.POTATO);
	}

	private static int foodToStore(CompanionEntity c) {
		return c.countCarried(s -> s.is(Items.WHEAT) || s.is(Items.BEETROOT)) + Math.max(0, c.countCarried(s -> s.is(Items.CARROT)) - 4)
				+ Math.max(0, c.countCarried(s -> s.is(Items.POTATO)) - 4);
	}

	/** Walks until within reach of the block; true once there. Gives up on a tile that takes too long. */
	private boolean reach(CompanionEntity c, BlockPos pos) {
		if (!pos.equals(target) && phase == Phase.SOW) {
			target = pos;
			ticksOnTarget = 0;
		}
		if (++ticksOnTarget > TILE_PATIENCE && phase == Phase.SOW) {
			sowAt++;
			ticksOnTarget = 0;
			return false;
		}
		return goNear(c, pos, Math.sqrt(REACH_SQ));
	}

	private boolean goNear(CompanionEntity c, BlockPos pos, double distance) {
		c.getLookControl().setLookAt(Vec3.atCenterOf(pos));
		if (c.distanceToSqr(Vec3.atCenterOf(pos)) <= distance * distance) {
			c.getNavigation().stop();
			return true;
		}
		if (c.tickCount % 20 == 0 || c.getNavigation().isDone()) {
			c.getNavigation().moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1.0);
		}
		return false;
	}

	private void finish(TaskStatus result) {
		if (!status.isFinished()) {
			status = result;
			if (onEnd != null) {
				onEnd.run();
			}
		}
	}

	@Override
	public void cancel(CompanionEntity companion) {
		if (work != null) {
			work.clear();
		}
		companion.getNavigation().stop();
		finish(TaskStatus.CANCELLED);
	}

	@Override
	public TaskStatus status() {
		return status;
	}

	/** How many tiles have been sown so far (for status and tests). */
	public int sown() {
		return sown;
	}

	public int harvested() {
		return harvested;
	}

	public int stored() {
		return stored;
	}

	@Override
	public String describe() {
		return switch (phase) {
			case PREP -> "getting the farm ready";
			case SOW -> "planting the farm (" + sowAt + "/" + mine.size() + ")";
			case TEND -> "tending the farm";
		};
	}
}

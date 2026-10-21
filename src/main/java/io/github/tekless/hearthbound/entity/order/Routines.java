package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.core.persona.Role;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.Pastimes;
import io.github.tekless.hearthbound.world.WorldRules;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The jobs a companion picks for itself while following you and you stand still, when
 * {@code /companion autonomy on} is set: collectors gather logs and surface stone, farmers harvest
 * ripe crops and replant, guardians hunt hostile mobs close to you, builders light dark spots.
 *
 * <p>They are only ever offered as pastimes, so they stop the moment you move or give an order.
 * Limits: a handful of blocks a minute, never inside protected areas or beside things players built
 * (except ripe crops, which are the point), never from chests, and never when the pack is nearly full.
 */
public final class Routines {
	private static final int RANGE = 10;
	private static final int PER_MINUTE = 10;
	private static final int MAX_TICKS = 400;
	private static final Map<UUID, Deque<Long>> WORK = new HashMap<>();

	private Routines() {
	}

	public static boolean enabled() {
		return Hearthbound.config().routines;
	}

	/** A job that suits this companion's role and the surroundings, or nothing. */
	public static Optional<Pastimes.Pastime> pick(CompanionEntity companion, ServerPlayer owner) {
		if (!enabled() || !(companion.level() instanceof ServerLevel level) || !allowed(companion, level)) {
			return Optional.empty();
		}
		return switch (companion.role()) {
			case COLLECTOR -> Forage.find(level, companion, owner).map(pos -> (Pastimes.Pastime) new Forage(pos));
			case FARMER -> Harvest.find(level, companion, owner).map(pos -> (Pastimes.Pastime) new Harvest(pos));
			case GUARDIAN -> hostileNear(level, companion, owner).map(mob -> (Pastimes.Pastime) new Hunt(mob));
			case BUILDER -> Torch.find(level, companion, owner).map(pos -> (Pastimes.Pastime) new Torch(pos));
		};
	}

	private static boolean allowed(CompanionEntity companion, ServerLevel level) {
		if (companion.distanceToSqr(companion.ownerPlayer() == null ? companion.position() : companion.ownerPlayer().position()) > 20 * 20) {
			return false;
		}
		int free = 0;
		for (ItemStack stack : companion.inventory().getItems()) {
			if (stack.isEmpty()) {
				free++;
			}
		}
		return free >= 4 && withinCap(companion, level);
	}

	private static boolean withinCap(CompanionEntity companion, ServerLevel level) {
		Deque<Long> recent = WORK.computeIfAbsent(companion.getUUID(), id -> new ArrayDeque<>());
		long now = level.getGameTime();
		while (!recent.isEmpty() && now - recent.peekFirst() > 1200) {
			recent.removeFirst();
		}
		return recent.size() < PER_MINUTE;
	}

	private static void record(CompanionEntity companion, ServerLevel level) {
		WORK.computeIfAbsent(companion.getUUID(), id -> new ArrayDeque<>()).addLast(level.getGameTime());
	}

	/** Nearest position (to the companion) inside the area around the owner that passes {@code test}. */
	private static Optional<BlockPos> nearest(ServerLevel level, CompanionEntity companion, ServerPlayer owner, java.util.function.Predicate<BlockPos> test) {
		BlockPos center = owner.blockPosition();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RANGE, -4, -RANGE), center.offset(RANGE, 4, RANGE))) {
			if (!level.isLoaded(pos) || WorldRules.isProtected(level, pos)) {
				continue;
			}
			double distance = companion.distanceToSqr(Vec3.atCenterOf(pos));
			if (distance < bestDistance && test.test(pos)) {
				best = pos.immutable();
				bestDistance = distance;
			}
		}
		return Optional.ofNullable(best);
	}

	private static boolean approach(CompanionEntity c, BlockPos pos, int ticks) {
		c.getLookControl().setLookAt(Vec3.atCenterOf(pos));
		if (c.distanceToSqr(Vec3.atCenterOf(pos)) > 6.0) {
			if (ticks % 20 == 1) {
				c.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.9);
			}
			return false;
		}
		return true;
	}

	private static Optional<LivingEntity> hostileNear(ServerLevel level, CompanionEntity companion, ServerPlayer owner) {
		List<LivingEntity> mobs = level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(16),
				e -> e instanceof Enemy && e.isAlive() && companion.distanceToSqr(e) < 24 * 24);
		return mobs.stream().min(java.util.Comparator.comparingDouble(companion::distanceToSqr));
	}

	/** Chop a tree log or pick up exposed surface stone. */
	static final class Forage implements Pastimes.Pastime {
		private final BlockPos pos;
		private BlockWork worker;
		private int ticks;

		Forage(BlockPos pos) {
			this.pos = pos;
		}

		static Optional<BlockPos> find(ServerLevel level, CompanionEntity c, ServerPlayer owner) {
			Optional<BlockPos> log = nearest(level, c, owner, pos -> level.getBlockState(pos).is(BlockTags.LOGS) && WorldRules.mayBreak(level, pos));
			if (log.isPresent()) {
				return log;
			}
			return nearest(level, c, owner, pos -> level.getBlockState(pos).is(Blocks.STONE) && exposed(level, pos) && WorldRules.mayBreak(level, pos));
		}

		private static boolean exposed(ServerLevel level, BlockPos pos) {
			for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
				if (level.getBlockState(pos.relative(direction)).isAir()) {
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean tick(CompanionEntity c, ServerPlayer owner) {
			ServerLevel level = (ServerLevel) c.level();
			if (++ticks > MAX_TICKS || level.getBlockState(pos).isAir() || WorldRules.isProtected(level, pos)) {
				return false;
			}
			if (ticks == 1) {
				c.sayMaybe("routine.gather", Map.of("item", itemName(level.getBlockState(pos))), 0.6);
			}
			if (!approach(c, pos, ticks)) {
				return true;
			}
			if (worker == null) {
				worker = new BlockWork(c, Role.Task.GATHER);
			}
			BlockWork.Result result = worker.step(pos);
			if (result == BlockWork.Result.DONE) {
				record(c, level);
				return false;
			}
			return result == BlockWork.Result.IN_PROGRESS;
		}

		@Override
		public void stop(CompanionEntity c) {
			if (worker != null) {
				worker.clear();
			}
			c.restoreWeapon();
			c.getNavigation().stop();
		}
	}

	/** Harvest a ripe crop and put a seed back. */
	static final class Harvest implements Pastimes.Pastime {
		private final BlockPos pos;
		private BlockWork worker;
		private int ticks;

		Harvest(BlockPos pos) {
			this.pos = pos;
		}

		static Optional<BlockPos> find(ServerLevel level, CompanionEntity c, ServerPlayer owner) {
			return nearest(level, c, owner, pos -> level.getBlockState(pos).getBlock() instanceof CropBlock crop && crop.isMaxAge(level.getBlockState(pos)));
		}

		private static Item seedFor(Block block) {
			if (block == Blocks.WHEAT) {
				return Items.WHEAT_SEEDS;
			}
			if (block == Blocks.CARROTS) {
				return Items.CARROT;
			}
			if (block == Blocks.POTATOES) {
				return Items.POTATO;
			}
			if (block == Blocks.BEETROOTS) {
				return Items.BEETROOT_SEEDS;
			}
			return null;
		}

		@Override
		public boolean tick(CompanionEntity c, ServerPlayer owner) {
			ServerLevel level = (ServerLevel) c.level();
			BlockState state = level.getBlockState(pos);
			if (++ticks > MAX_TICKS || !(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
				return false;
			}
			if (ticks == 1) {
				c.sayMaybe("routine.farm", Map.of("item", itemName(state)), 0.6);
			}
			if (!approach(c, pos, ticks)) {
				return true;
			}
			if (worker == null) {
				worker = new BlockWork(c, Role.Task.FARM);
			}
			BlockWork.Result result = worker.step(pos);
			if (result == BlockWork.Result.DONE) {
				Item seed = seedFor(crop);
				if (seed != null && level.getBlockState(pos.below()).is(Blocks.FARMLAND) && c.inventory().removeItemType(seed, 1).getCount() > 0) {
					level.setBlock(pos, crop.getStateForAge(0), 3);
				}
				record(c, level);
				return false;
			}
			return result == BlockWork.Result.IN_PROGRESS;
		}

		@Override
		public void stop(CompanionEntity c) {
			if (worker != null) {
				worker.clear();
			}
			c.restoreWeapon();
			c.getNavigation().stop();
		}
	}

	/** Go after a hostile mob that is close to the player. */
	static final class Hunt implements Pastimes.Pastime {
		private final LivingEntity mob;
		private int ticks;

		Hunt(LivingEntity mob) {
			this.mob = mob;
		}

		@Override
		public boolean tick(CompanionEntity c, ServerPlayer owner) {
			if (!mob.isAlive() || ++ticks > 260 || c.isDowned()) {
				return false;
			}
			if (ticks == 1) {
				c.sayMaybe("routine.hunt", Map.of("mob", mob.getName().getString().toLowerCase(Locale.ROOT)), 0.7);
			}
			if (c.getTarget() != mob) {
				c.setTarget(mob);
			}
			return true;
		}

		@Override
		public void stop(CompanionEntity c) {
			if (c.getTarget() == mob) {
				c.setTarget(null);
			}
			c.getNavigation().stop();
		}
	}

	/** Put a torch down in a dark spot near the player. */
	static final class Torch implements Pastimes.Pastime {
		private final BlockPos pos;
		private int ticks;

		Torch(BlockPos pos) {
			this.pos = pos;
		}

		static Optional<BlockPos> find(ServerLevel level, CompanionEntity c, ServerPlayer owner) {
			if (c.countCarried(s -> s.is(Items.TORCH)) == 0) {
				return Optional.empty();
			}
			return nearest(level, c, owner, pos -> level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
					&& level.getMaxLocalRawBrightness(pos) <= 3 && Blocks.TORCH.defaultBlockState().canSurvive(level, pos));
		}

		@Override
		public boolean tick(CompanionEntity c, ServerPlayer owner) {
			ServerLevel level = (ServerLevel) c.level();
			if (++ticks > MAX_TICKS || !level.getBlockState(pos).isAir() || level.getMaxLocalRawBrightness(pos) > 3) {
				return false;
			}
			if (!approach(c, pos, ticks)) {
				return true;
			}
			if (c.inventory().removeItemType(Items.TORCH, 1).getCount() == 0) {
				return false;
			}
			c.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.setBlock(pos, Blocks.TORCH.defaultBlockState(), 3);
			c.sayMaybe("routine.torch", Map.of(), 0.5);
			record(c, level);
			return false;
		}
	}

	private static String itemName(BlockState state) {
		return new ItemStack(state.getBlock().asItem()).getHoverName().getString().toLowerCase(Locale.ROOT);
	}

	/** Used by tests: forget the per-minute allowance. */
	public static void resetAllowance() {
		WORK.clear();
	}
}

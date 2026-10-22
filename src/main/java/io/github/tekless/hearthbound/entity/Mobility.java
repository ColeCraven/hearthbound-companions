package io.github.tekless.hearthbound.entity;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.entity.order.BuildOrder;
import io.github.tekless.hearthbound.world.WorldRules;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Getting to someone when walking won't do it: up a ladder or vines, up out of a hole or a cliff by putting blocks
 * from the pack under their feet, across a gap of one to three blocks by bridging it, and swimming after them. Used
 * while following and while coming over. Blocks are only ever placed into empty space, never inside a protected area,
 * and a bridge is never built beside something a player made. When none of it helps, the caller teleports.
 */
public final class Mobility {
	private static final int MAX_PILLAR = 12;
	private static final int MAX_GAP = 3;

	private enum Mode {
		NONE, CLIMB, PILLAR, BRIDGE
	}

	private Mode mode = Mode.NONE;
	private int ticks;
	private @Nullable BlockPos ladder;
	private boolean onLadder;
	private @Nullable BlockPos base;
	private boolean waitingToPlace;
	private int pillared;
	private @Nullable BlockPos bridgeFrom;
	private @Nullable Direction bridgeDir;
	private int bridgeGaps;
	private int bridged;
	private int pathCheck;
	private boolean unreachable;
	private boolean waterMalusOff;
	private int cooldown;

	/** What they're doing right now, for status lines ("climbing"), or null. */
	public @Nullable String activity() {
		return switch (mode) {
			case CLIMB -> "climbing up to you";
			case PILLAR -> "building their way up";
			case BRIDGE -> "bridging a gap";
			default -> null;
		};
	}

	public boolean busy() {
		return mode != Mode.NONE;
	}

	/** Called when nothing needs reaching: the usual dislike of water comes back once they're out of it. */
	public void settle(CompanionEntity c) {
		if (waterMalusOff && !c.isInWater() && mode == Mode.NONE) {
			waterMalusOff = false;
			c.setPathfindingMalus(PathType.WATER, 4.0F);
		}
	}

	public void reset(CompanionEntity c) {
		mode = Mode.NONE;
		ladder = null;
		base = null;
		waitingToPlace = false;
		if (waterMalusOff) {
			waterMalusOff = false;
			c.setPathfindingMalus(PathType.WATER, 4.0F);
		}
	}

	/**
	 * One tick of trying to reach {@code target}. Swimming help is always on; the rest starts when the companion is stuck,
	 * or can't find a way to someone above them.
	 *
	 * @return true while it is moving the companion itself (the caller should leave the legs alone this tick)
	 */
	public boolean assist(CompanionEntity c, Entity target, boolean stuck) {
		if (!(c.level() instanceof ServerLevel level) || target.level() != c.level()) {
			return false;
		}
		swim(c, target);
		if (cooldown > 0) {
			cooldown--;
		}
		switch (mode) {
			case CLIMB -> {
				return climb(c, target);
			}
			case PILLAR -> {
				return pillar(c, level, target);
			}
			case BRIDGE -> {
				return bridge(c, level);
			}
			default -> {
			}
		}
		double dy = target.getY() - c.getY();
		double horizontal = Math.sqrt(Math.pow(target.getX() - c.getX(), 2) + Math.pow(target.getZ() - c.getZ(), 2));
		boolean blocked = stuck || (dy >= 1.5 && cantReach(c, target));
		if (!blocked || cooldown > 0 || c.isInWater()) {
			return false;
		}
		if (dy >= 1.5 && horizontal <= 14) {
			BlockPos found = findClimbable(level, c, target);
			if (found != null) {
				start(Mode.CLIMB);
				ladder = found;
				onLadder = false;
				return true;
			}
			if (horizontal <= 7 && canPillar(level, c)) {
				start(Mode.PILLAR);
				pillared = 0;
				base = null;
				c.sayMaybe("move.pillar", Map.of(), 0.6);
				return true;
			}
		}
		if (Math.abs(dy) < 1.5 && horizontal >= 2) {
			if (findGap(level, c, target)) {
				start(Mode.BRIDGE);
				bridged = 0;
				c.sayMaybe("move.bridge", Map.of(), 0.6);
				return true;
			}
		}
		cooldown = 40; // nothing to try here; let the caller catch up another way
		return false;
	}

	private void start(Mode next) {
		mode = next;
		ticks = 0;
	}

	private void stop() {
		mode = Mode.NONE;
		cooldown = 20;
	}

	/** Every second or so: is there a walkable way to them at all? */
	private boolean cantReach(CompanionEntity c, Entity target) {
		if (--pathCheck > 0) {
			return unreachable;
		}
		pathCheck = 20;
		Path path = c.getNavigation().createPath(target, 0);
		unreachable = path == null || !path.canReach();
		return unreachable;
	}

	// ------------------------------------------------------------------ swimming

	/** In the water: straight through it after them, a bit quicker than drifting, head kept up by the float goal. */
	private void swim(CompanionEntity c, Entity target) {
		boolean water = c.isInWater() || target.isInWater();
		if (water != waterMalusOff) {
			waterMalusOff = water;
			c.setPathfindingMalus(PathType.WATER, water ? 0.0F : 4.0F);
		}
		if (c.isInWater() && c.distanceTo(target) > 2.5) {
			Vec3 to = target.position().subtract(c.position()).multiply(1, 0, 1);
			if (to.lengthSqr() > 0.01) {
				Vec3 push = to.normalize().scale(0.025);
				c.setDeltaMovement(c.getDeltaMovement().add(push.x, target.getY() > c.getY() + 0.3 ? 0.01 : 0, push.z));
			}
		}
	}

	// ------------------------------------------------------------------ ladders and vines

	/** A ladder or vines within reach that goes up a good way towards them. */
	private static @Nullable BlockPos findClimbable(ServerLevel level, CompanionEntity c, Entity target) {
		BlockPos feet = c.blockPosition();
		BlockPos best = null;
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-8, -1, -8), feet.offset(8, 1, 8))) {
			if (!level.getBlockState(pos).is(BlockTags.CLIMBABLE)) {
				continue;
			}
			int top = pos.getY();
			while (top - pos.getY() < 32 && level.getBlockState(new BlockPos(pos.getX(), top + 1, pos.getZ())).is(BlockTags.CLIMBABLE)) {
				top++;
			}
			if (top >= feet.getY() + 1 && top + 2 >= target.getBlockY() && (best == null || pos.distSqr(feet) < best.distSqr(feet))) {
				best = pos.immutable();
			}
		}
		return best;
	}

	private boolean climb(CompanionEntity c, Entity target) {
		if (ladder == null || ++ticks > 300) {
			stop();
			return false;
		}
		if (!onLadder) {
			if (c.onClimbable() || Math.abs(c.getX() - (ladder.getX() + 0.5)) < 0.7 && Math.abs(c.getZ() - (ladder.getZ() + 0.5)) < 0.7) {
				onLadder = true;
				c.getNavigation().stop();
			} else {
				if (ticks % 20 == 1 || c.getNavigation().isDone()) {
					c.getNavigation().moveTo(ladder.getX() + 0.5, ladder.getY(), ladder.getZ() + 0.5, 1.0);
				}
				return true;
			}
		}
		BlockPos feet = c.blockPosition();
		boolean moreAbove = c.level().getBlockState(feet.above()).is(BlockTags.CLIMBABLE) || c.level().getBlockState(feet).is(BlockTags.CLIMBABLE);
		if (c.getY() >= target.getY() - 0.2 && !c.onClimbable()) {
			stop();
			return false;
		}
		if (!c.onClimbable() && !moreAbove && ticks > 20) {
			stop(); // off the top
			return false;
		}
		// Up the ladder; once at the top, step off towards them.
		Vec3 aim = moreAbove && c.getY() < target.getY() - 0.2 ? Vec3.atBottomCenterOf(ladder).add(0, c.getY() - ladder.getY() + 1, 0)
				: new Vec3(target.getX(), c.getY() + 1, target.getZ());
		c.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, 0.8);
		c.getJumpControl().jump();
		return true;
	}

	// ------------------------------------------------------------------ pillaring up

	private static boolean canPillar(ServerLevel level, CompanionEntity c) {
		BlockPos feet = c.blockPosition();
		return c.onGround() && material(c) != null && open(level.getBlockState(feet.above(2))) && !WorldRules.isProtected(level, feet);
	}

	private boolean pillar(CompanionEntity c, ServerLevel level, Entity target) {
		if (++ticks > 60 + pillared * 30 || pillared >= MAX_PILLAR || c.isInWater()) {
			stop();
			return false;
		}
		c.getNavigation().stop();
		c.setDeltaMovement(0, c.getDeltaMovement().y, 0);
		if (c.getY() >= target.getY() - 0.2 && c.onGround()) {
			stop();
			return false;
		}
		if (c.onGround() && !waitingToPlace) {
			base = c.blockPosition();
			if (!open(level.getBlockState(base.above(2))) || WorldRules.isProtected(level, base) || material(c) == null) {
				stop();
				return false;
			}
			c.snapTo(base.getX() + 0.5, c.getY(), base.getZ() + 0.5, c.getYRot(), c.getXRot());
			c.getJumpControl().jump();
			waitingToPlace = true;
			return true;
		}
		if (waitingToPlace && base != null && c.getY() >= base.getY() + 1.05) {
			waitingToPlace = false;
			ItemStack stack = material(c);
			if (stack == null || !open(level.getBlockState(base)) || !level.getFluidState(base).isEmpty() && level.getFluidState(base).is(FluidTags.LAVA)) {
				stop();
				return false;
			}
			if (!level.getEntities(c, new AABB(base)).isEmpty()) {
				return true;
			}
			place(c, level, base, stack);
			pillared++;
		} else if (waitingToPlace && c.onGround() && ticks % 10 == 0) {
			waitingToPlace = false; // the jump didn't take; try again
		}
		return true;
	}

	// ------------------------------------------------------------------ bridging small gaps

	/** A gap of one to three blocks straight ahead towards them, with ground on the far side, that they may bridge. */
	private boolean findGap(ServerLevel level, CompanionEntity c, Entity target) {
		if (!c.onGround()) {
			return false;
		}
		double dx = target.getX() - c.getX();
		double dz = target.getZ() - c.getZ();
		Direction dir = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		BlockPos feet = c.blockPosition();
		// Standing a step short of the edge is fine: the blocks are still in reach.
		int start = !open(level.getBlockState(feet.relative(dir, 1).below())) && open(level.getBlockState(feet.relative(dir, 2).below())) ? 2 : 1;
		int gaps = 0;
		for (int i = start; i <= start + MAX_GAP; i++) {
			BlockPos floor = feet.relative(dir, i).below();
			if (open(level.getBlockState(floor))) {
				gaps++;
			} else {
				break;
			}
		}
		if (gaps < 1 || gaps > MAX_GAP) {
			return false;
		}
		BlockPos far = feet.relative(dir, start + gaps);
		if (open(level.getBlockState(far.below())) || !open(level.getBlockState(far)) || !open(level.getBlockState(far.above()))) {
			return false;
		}
		int have = 0;
		for (ItemStack stack : c.inventory().getItems()) {
			if (BuildOrder.isBuildingBlock(stack)) {
				have += stack.getCount();
			}
		}
		if (have < gaps) {
			return false;
		}
		for (int i = start; i < start + gaps; i++) {
			BlockPos spot = feet.relative(dir, i).below();
			if (!mayBuildAt(level, spot) || level.getFluidState(spot).is(FluidTags.LAVA) || level.getFluidState(spot.below()).is(FluidTags.LAVA)
					|| !open(level.getBlockState(spot.above())) || !open(level.getBlockState(spot.above(2)))) {
				return false;
			}
		}
		bridgeFrom = feet.relative(dir, start - 1);
		bridgeDir = dir;
		bridgeGaps = gaps;
		return true;
	}

	/** Not inside a protected area, and not beside anything a player built (the same guard gathering keeps to). */
	public static boolean mayBuildAt(ServerLevel level, BlockPos pos) {
		if (WorldRules.isProtected(level, pos)) {
			return false;
		}
		int radius = Math.min(8, Hearthbound.config().server.buildGuardRadius);
		for (BlockPos near : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius), pos.offset(radius, radius, radius))) {
			if (level.isLoaded(near) && WorldRules.isManMade(level.getBlockState(near))) {
				return false;
			}
		}
		return true;
	}

	private boolean bridge(CompanionEntity c, ServerLevel level) {
		if (bridgeFrom == null || bridgeDir == null || ++ticks > 200) {
			stop();
			return false;
		}
		c.getNavigation().stop();
		if (bridged >= bridgeGaps) {
			stop();
			return false;
		}
		if (ticks % 5 != 0) {
			return true;
		}
		BlockPos spot = bridgeFrom.relative(bridgeDir, bridged + 1).below();
		c.getLookControl().setLookAt(Vec3.atCenterOf(spot));
		if (c.getEyePosition().distanceTo(Vec3.atCenterOf(spot)) > 5.0) {
			stop();
			return false;
		}
		ItemStack stack = material(c);
		if (stack == null) {
			stop();
			return false;
		}
		if (open(level.getBlockState(spot))) {
			place(c, level, spot, stack);
		}
		bridged++;
		return true;
	}

	// ------------------------------------------------------------------ shared

	private static @Nullable ItemStack material(CompanionEntity c) {
		ItemStack best = null;
		for (ItemStack stack : c.inventory().getItems()) {
			if (BuildOrder.isBuildingBlock(stack) && (best == null || stack.getCount() > best.getCount())) {
				best = stack;
			}
		}
		return best;
	}

	private static void place(CompanionEntity c, ServerLevel level, BlockPos pos, ItemStack stack) {
		BlockState state = ((BlockItem) stack.getItem()).getBlock().defaultBlockState();
		level.setBlockAndUpdate(pos, state);
		level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
		c.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		stack.shrink(1);
	}

	private static boolean open(BlockState state) {
		return state.isAir() || state.canBeReplaced();
	}
}

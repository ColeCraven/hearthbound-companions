package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.task.GatherMachine;
import io.github.tekless.hearthbound.core.task.GatherWorld;
import io.github.tekless.hearthbound.core.task.StuckDetector;
import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.world.GatherSpec;
import io.github.tekless.hearthbound.world.WorldRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Gather order: the Minecraft side of {@link GatherMachine}. Finds exposed natural blocks, walks
 * to them, breaks them at a believable speed with the best tool the companion carries, collects
 * the drops, and brings the requested amount back to the owner.
 */
public final class GatherOrder implements CompanionOrder, GatherWorld<BlockPos> {
	private static final int SEARCH_RADIUS = 24;
	private static final int SEARCH_DOWN = 10;
	private static final int SEARCH_UP = 12;
	private static final double REACH = 4.5;

	private final CompanionEntity companion;
	private final GatherSpec spec;
	private final GatherMachine<BlockPos> machine;
	private final int baseline;
	private final StuckDetector stuck = new StuckDetector(80, 0.6);

	private BlockPos pathTarget;
	private int repathCooldown;
	private BlockPos breakingPos;
	private float breakProgress;
	private int lastCrackStage = -1;
	private int swingCooldown;

	public GatherOrder(CompanionEntity companion, GatherSpec spec, int goal) {
		this(companion, spec, goal, -1);
	}

	/** @param baseline items of this kind carried before the order began, or -1 to count now */
	public GatherOrder(CompanionEntity companion, GatherSpec spec, int goal, int baseline) {
		this.companion = companion;
		this.spec = spec;
		this.baseline = baseline >= 0 ? baseline : companion.countCarried(spec.counts());
		this.machine = new GatherMachine<>(this, goal);
	}

	public GatherSpec spec() {
		return spec;
	}

	public int goal() {
		return machine.goal();
	}

	public int baseline() {
		return baseline;
	}

	@Override
	public Kind kind() {
		return Kind.GATHER;
	}

	@Override
	public void tick(CompanionEntity c) {
		machine.tick();
		for (GatherMachine.Event event : machine.drainEvents()) {
			report(event);
		}
	}

	private void report(GatherMachine.Event event) {
		Map<String, String> vars = Map.of(
				"item", spec.displayName(),
				"count", Integer.toString(machine.goal()),
				"have", Integer.toString(Math.max(collected(), 0)));
		switch (event) {
			case GatherMachine.Event.HalfWay halfWay -> companion.say("progress.gather",
					Map.of("item", spec.displayName(), "count", Integer.toString(halfWay.goal()), "have", Integer.toString(halfWay.have())));
			case GatherMachine.Event.CollectedAll all -> companion.say("done.gather", vars);
			case GatherMachine.Event.Delivered delivered -> {
				companion.say("done.delivered", Map.of("item", spec.displayName(), "count", Integer.toString(delivered.count())));
				companion.memory().addEvent("gathered " + delivered.count() + " " + spec.displayName() + " for {player}");
			}
			case GatherMachine.Event.Partial partial -> companion.say(
					partial.reason() == io.github.tekless.hearthbound.core.task.FailureReason.INVENTORY_FULL ? "fail.inventory_full" : "partial.gather",
					Map.of("item", spec.displayName(), "count", Integer.toString(partial.goal()), "have", Integer.toString(partial.delivered())));
			case GatherMachine.Event.Failed failed -> {
				String tool = failed.detail() == null ? "better tool" : failed.detail();
				companion.say(failed.reason().lineKey(), Map.of("item", spec.displayName(), "tool", tool, "count", Integer.toString(machine.goal()),
						"have", Integer.toString(Math.max(collected(), 0))));
			}
		}
	}

	@Override
	public void pause(CompanionEntity c) {
		clearBreaking();
		pathTarget = null;
		stuck.reset();
		CompanionOrder.super.pause(c);
	}

	@Override
	public void cancel(CompanionEntity c) {
		machine.cancel();
		clearBreaking();
		c.getNavigation().stop();
	}

	@Override
	public TaskStatus status() {
		return machine.status();
	}

	@Override
	public String describe() {
		return "gathering " + spec.displayName() + " (" + Math.max(collected(), 0) + "/" + machine.goal() + ")";
	}

	// ---------------------------------------------------------------- GatherWorld

	@Override
	public Optional<BlockPos> findTarget(Set<BlockPos> excluded) {
		ServerLevel level = (ServerLevel) companion.level();
		BlockPos origin = companion.blockPosition();
		List<BlockPos> found = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// Search in growing rings so nearby blocks are found cheaply.
		for (int radius : new int[] {8, 16, SEARCH_RADIUS}) {
			found.clear();
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					for (int dy = -SEARCH_DOWN; dy <= SEARCH_UP; dy++) {
						pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
						if (!level.isLoaded(pos)) {
							continue;
						}
						BlockState state = level.getBlockState(pos);
						if (!spec.blocks().test(state) || excluded.contains(pos)) {
							continue;
						}
						BlockPos candidate = pos.immutable();
						if (isExposed(level, candidate) && (!spec.treesOnly() || isNaturalTree(level, candidate))) {
							found.add(candidate);
						}
					}
				}
			}
			if (!found.isEmpty()) {
				found.sort(Comparator.comparingDouble(p -> p.distSqr(origin) + Math.max(0, p.getY() - origin.getY()) * 2.0));
				// Shared worlds: skip anything in a protected area or right next to someone's build.
				int checked = 0;
				for (BlockPos candidate : found) {
					if (WorldRules.mayBreak(level, candidate)) {
						return Optional.of(candidate);
					}
					excluded.add(candidate);
					if (++checked >= 48) {
						break;
					}
				}
			}
		}
		return Optional.empty();
	}

	@Override
	public Optional<String> missingTool(BlockPos target) {
		BlockState state = companion.level().getBlockState(target);
		if (!state.requiresCorrectToolForDrops()) {
			return Optional.empty();
		}
		if (companion.bestToolFor(state).map(stack -> stack.isCorrectToolForDrops(state)).orElse(false)) {
			return Optional.empty();
		}
		return Optional.of(toolName(state));
	}

	@Override
	public Move approach(BlockPos target) {
		if (!spec.blocks().test(companion.level().getBlockState(target))) {
			return Move.ARRIVED; // breakStep will report GONE
		}
		if (canReach(target)) {
			companion.getNavigation().stop();
			pathTarget = null;
			stuck.reset();
			return Move.ARRIVED;
		}
		if (!target.equals(pathTarget) || --repathCooldown <= 0 || companion.getNavigation().isDone()) {
			if (target.equals(pathTarget) && companion.getNavigation().isDone()) {
				// Arrived where the path ended but still out of reach.
				pathTarget = null;
				return Move.UNREACHABLE;
			}
			Path path = companion.getNavigation().createPath(target, 1);
			if (path == null) {
				return Move.UNREACHABLE;
			}
			if (!path.canReach() && path.getEndNode() != null
					&& Vec3.atCenterOf(target).distanceTo(new Vec3(path.getEndNode().x + 0.5, path.getEndNode().y + 1.6, path.getEndNode().z + 0.5)) > REACH) {
				return Move.UNREACHABLE;
			}
			companion.getNavigation().moveTo(path, 1.0);
			pathTarget = target.immutable();
			repathCooldown = 40;
		}
		companion.getLookControl().setLookAt(Vec3.atCenterOf(target));
		if (stuck.update(companion.getX(), companion.getZ(), true) && stuck.consecutiveStuck() >= 2) {
			pathTarget = null;
			return Move.STUCK;
		}
		return Move.MOVING;
	}

	@Override
	public Break breakStep(BlockPos target) {
		ServerLevel level = (ServerLevel) companion.level();
		BlockState state = level.getBlockState(target);
		if (!spec.blocks().test(state)) {
			clearBreaking();
			return Break.GONE;
		}
		if (!target.equals(breakingPos)) {
			clearBreaking();
			breakingPos = target.immutable();
			companion.holdBestToolFor(state);
		}
		companion.getLookControl().setLookAt(Vec3.atCenterOf(target));
		float hardness = state.getDestroySpeed(level, target);
		if (hardness < 0) {
			clearBreaking();
			return Break.GONE;
		}
		ItemStack tool = companion.getMainHandItem();
		boolean correct = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
		float speed = Math.max(1.0F, tool.getDestroySpeed(state));
		breakProgress += hardness == 0 ? 1.0F : speed / hardness / (correct ? 30.0F : 100.0F);
		if (--swingCooldown <= 0) {
			swingCooldown = 5;
			companion.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		}
		int stage = Math.min(9, (int) (breakProgress * 10));
		if (stage != lastCrackStage) {
			lastCrackStage = stage;
			level.destroyBlockProgress(companion.getId(), target, stage);
		}
		if (breakProgress < 1.0F) {
			return Break.IN_PROGRESS;
		}
		BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(target) : null;
		List<ItemStack> drops = Block.getDrops(state, level, target, blockEntity, companion, tool);
		level.destroyBlock(target, false, companion);
		if (!tool.isEmpty() && tool.isDamageableItem()) {
			tool.hurtAndBreak(1, companion, EquipmentSlot.MAINHAND);
		}
		for (ItemStack drop : drops) {
			companion.storeOrDrop(drop);
		}
		clearBreaking();
		return Break.DONE;
	}

	@Override
	public int collected() {
		return companion.countCarried(spec.counts()) - baseline;
	}

	@Override
	public boolean inventoryFull() {
		return !companion.hasRoomFor(spec.counts());
	}

	@Override
	public boolean ownerAvailable() {
		ServerPlayer owner = companion.ownerPlayer();
		return owner != null && owner.isAlive();
	}

	@Override
	public Move returnToOwner() {
		ServerPlayer owner = companion.ownerPlayer();
		if (owner == null) {
			return Move.UNREACHABLE;
		}
		if (owner.level() != companion.level() || companion.distanceTo(owner) > 32) {
			companion.requestCatchUp(owner);
			return Move.MOVING;
		}
		if (companion.distanceTo(owner) <= 3.0) {
			companion.getNavigation().stop();
			return Move.ARRIVED;
		}
		if (--repathCooldown <= 0 || companion.getNavigation().isDone()) {
			repathCooldown = 10;
			companion.getNavigation().moveTo(owner, 1.2);
		}
		if (stuck.update(companion.getX(), companion.getZ(), true) && stuck.consecutiveStuck() >= 2) {
			companion.requestCatchUp(owner);
		}
		return Move.MOVING;
	}

	@Override
	public int deliver(int max) {
		ServerPlayer owner = companion.ownerPlayer();
		if (owner == null) {
			return 0;
		}
		int given = companion.giveTo(owner, spec.counts(), max);
		if (given > 0) {
			companion.level().playSound(null, owner.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.2F);
		}
		companion.restoreWeapon();
		return given;
	}

	// ---------------------------------------------------------------- helpers

	private boolean canReach(BlockPos target) {
		return companion.getEyePosition().distanceTo(Vec3.atCenterOf(target)) <= REACH;
	}

	private void clearBreaking() {
		if (breakingPos != null && companion.level() instanceof ServerLevel level) {
			level.destroyBlockProgress(companion.getId(), breakingPos, -1);
		}
		breakingPos = null;
		breakProgress = 0;
		lastCrackStage = -1;
	}

	/** At least one face touches air or a non-solid block, so the companion isn't mining through walls. */
	private static boolean isExposed(ServerLevel level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockState neighbour = level.getBlockState(pos.relative(direction));
			if (neighbour.isAir() || !neighbour.isCollisionShapeFullBlock(level, pos.relative(direction))) {
				return true;
			}
		}
		return false;
	}

	/** Logs count as a tree only if natural (non-persistent) leaves are close by - not a log cabin. */
	private static boolean isNaturalTree(ServerLevel level, BlockPos log) {
		for (BlockPos p : BlockPos.betweenClosed(log.offset(-3, -1, -3), log.offset(3, 4, 3))) {
			BlockState state = level.getBlockState(p);
			if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)) {
				return true;
			}
		}
		return false;
	}

	private static String toolName(BlockState state) {
		String tier = state.is(BlockTags.NEEDS_DIAMOND_TOOL) ? "diamond " : state.is(BlockTags.NEEDS_IRON_TOOL) ? "iron "
				: state.is(BlockTags.NEEDS_STONE_TOOL) ? "stone " : "";
		if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			return tier + "shovel";
		}
		if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
			return tier + "axe";
		}
		if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
			return tier + "hoe";
		}
		return tier + "pickaxe";
	}
}

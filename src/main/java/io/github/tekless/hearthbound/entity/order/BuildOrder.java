package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a {@link Blueprint} one block at a time from blocks in the companion's pack (planks,
 * cobblestone, logs, bricks...), using the most plentiful kind first. A door goes in the doorway
 * if they carry one, and a torch inside a shelter.
 */
public final class BuildOrder implements CompanionOrder {
	private static final double REACH = 6.0;
	private static final int PLACE_EVERY = 4;

	private final CompanionEntity companion;
	private final Blueprint blueprint;
	private final Direction facing;
	private final BlockPos workSpot;
	private final List<BlockPos> queue;
	private final Map<BlockPos, Integer> blockedFor = new HashMap<>();
	private int cooldown;
	private int placed;
	private int repathCooldown;
	private int stuckTicks;
	private boolean finishing;
	private TaskStatus status = TaskStatus.RUNNING;

	public BuildOrder(CompanionEntity companion, Blueprint blueprint, BlockPos playerFeet, Direction facing, ServerLevel level) {
		this.companion = companion;
		this.blueprint = blueprint;
		this.facing = facing;
		this.workSpot = switch (blueprint.kind()) {
			case SHELTER -> playerFeet.relative(facing, 2); // the doorway, which stays open until the end
			case WALL -> playerFeet.relative(facing, 1);
			case BRIDGE -> playerFeet;
		};
		List<BlockPos> open = new ArrayList<>(blueprint.openSpots(level));
		// Bottom layer first, then up; along a bridge from the near end.
		open.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingDouble(p -> p.distSqr(playerFeet)));
		this.queue = open;
	}

	/** Building blocks the companion carries: full, solid, ordinary blocks (no ores, no valuables, no sand). */
	public static int countBuildingBlocks(CompanionEntity companion) {
		int total = 0;
		for (ItemStack stack : companion.inventory().getItems()) {
			if (isBuildingBlock(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public static boolean isBuildingBlock(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
			return false;
		}
		Block block = blockItem.getBlock();
		BlockState state = block.defaultBlockState();
		if (block instanceof FallingBlock || state.hasBlockEntity() || !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
			return false;
		}
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		return !path.equals("light") && !path.matches(".*(ore|diamond|emerald|gold|iron|netherite|lapis|redstone|copper|coal|amethyst|ancient_debris"
				+ "|tnt|slime|honey|ice|sponge|spawner|beacon|lodestone|respawn|bedrock|barrier|command|structure|jigsaw|infested|budding|crying|reinforced).*");
	}

	@Override
	public Kind kind() {
		return Kind.BUILD;
	}

	@Override
	public void tick(CompanionEntity c) {
		if (status.isFinished()) {
			return;
		}
		ServerLevel level = (ServerLevel) c.level();
		BlockPos next = queue.isEmpty() ? null : queue.get(0);
		if (next == null) {
			if (!finishing) {
				finishing = true;
				finishTouches(level);
			}
			status = TaskStatus.SUCCEEDED;
			c.say("done.build", Map.of("thing", blueprint.kind().label()));
			c.memory().addEvent("I built " + blueprint.kind().label() + " with {player}");
			return;
		}
		if (!Blueprint.isOpen(level.getBlockState(next))) {
			queue.remove(0); // something's already there; leave it be
			return;
		}
		BlockPos stand = blueprint.kind() == Blueprint.Kind.BRIDGE && placed > 0 ? lastPlacedBridge().above() : workSpot;
		if (c.getEyePosition().distanceTo(Vec3.atCenterOf(next)) > REACH) {
			walkTo(stand);
			return;
		}
		stuckTicks = 0;
		c.getLookControl().setLookAt(Vec3.atCenterOf(next));
		if (--cooldown > 0) {
			return;
		}
		cooldown = PLACE_EVERY;
		if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(next)).isEmpty()) {
			// Someone's standing there: try the rest first, give up on this spot after a while.
			int waited = blockedFor.merge(next, 1, Integer::sum);
			queue.remove(0);
			if (waited < 20) {
				queue.add(next);
			}
			return;
		}
		ItemStack material = pickMaterial();
		if (material == null) {
			status = TaskStatus.FAILED;
			c.say("fail.build_materials", Map.of("thing", blueprint.kind().label(), "need", Integer.toString(queue.size()), "have", "0"));
			return;
		}
		BlockState state = ((BlockItem) material.getItem()).getBlock().defaultBlockState();
		level.setBlockAndUpdate(next, state);
		level.playSound(null, next, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
		c.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		material.shrink(1);
		queue.remove(0);
		placed++;
	}

	/** The kind of block the companion has most of, so the build is one material where possible. */
	private ItemStack pickMaterial() {
		Map<net.minecraft.world.item.Item, Integer> totals = new HashMap<>();
		for (ItemStack stack : companion.inventory().getItems()) {
			if (isBuildingBlock(stack)) {
				totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		net.minecraft.world.item.Item best = totals.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
		if (best == null) {
			return null;
		}
		for (ItemStack stack : companion.inventory().getItems()) {
			if (stack.is(best) && !stack.isEmpty()) {
				return stack;
			}
		}
		return null;
	}

	private BlockPos lastPlacedBridge() {
		BlockPos at = workSpot.below();
		for (Blueprint.Spot spot : blueprint.spots()) {
			if (!Blueprint.isOpen(companion.level().getBlockState(spot.pos()))) {
				at = spot.pos();
			} else {
				break;
			}
		}
		return at;
	}

	/** A door in a shelter's doorway and a torch inside, if the companion has them. */
	private void finishTouches(ServerLevel level) {
		if (blueprint.kind() != Blueprint.Kind.SHELTER) {
			return;
		}
		List<BlockPos> doorway = blueprint.spots().stream().filter(Blueprint.Spot::doorway).map(Blueprint.Spot::pos)
				.sorted(Comparator.comparingInt(BlockPos::getY)).toList();
		ItemStack door = take(stack -> stack.getItem() instanceof BlockItem item && item.getBlock() instanceof DoorBlock);
		if (door != null && doorway.size() == 2 && Blueprint.isOpen(level.getBlockState(doorway.get(0))) && Blueprint.isOpen(level.getBlockState(doorway.get(1)))) {
			Block block = ((BlockItem) door.getItem()).getBlock();
			BlockState lower = block.defaultBlockState().setValue(DoorBlock.FACING, facing.getOpposite()).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
			level.setBlockAndUpdate(doorway.get(0), lower);
			level.setBlockAndUpdate(doorway.get(1), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
			door.shrink(1);
		}
		BlockPos inside = workSpot.relative(facing, 2);
		if (level.getBlockState(inside).isAir() && Blocks.TORCH.defaultBlockState().canSurvive(level, inside)) {
			ItemStack torch = take(stack -> stack.is(Items.TORCH));
			if (torch != null) {
				level.setBlockAndUpdate(inside, Blocks.TORCH.defaultBlockState());
				torch.shrink(1);
			}
		}
	}

	private ItemStack take(java.util.function.Predicate<ItemStack> filter) {
		for (ItemStack stack : companion.inventory().getItems()) {
			if (!stack.isEmpty() && filter.test(stack)) {
				return stack;
			}
		}
		return null;
	}

	private void walkTo(BlockPos spot) {
		if (--repathCooldown <= 0 || companion.getNavigation().isDone()) {
			repathCooldown = 20;
			companion.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
		}
		if (++stuckTicks > 400) {
			status = TaskStatus.FAILED;
			companion.say("fail.stuck", Map.of());
		}
	}

	@Override
	public void cancel(CompanionEntity c) {
		status = TaskStatus.CANCELLED;
		c.getNavigation().stop();
	}

	@Override
	public TaskStatus status() {
		return status;
	}

	@Override
	public String describe() {
		return "building " + blueprint.kind().label() + " (" + placed + "/" + (placed + queue.size()) + " blocks)";
	}

	public Blueprint blueprint() {
		return blueprint;
	}
}

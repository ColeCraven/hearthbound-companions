package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.world.WorldRules;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Digging: a 1x2 tunnel straight ahead, a staircase down, or a mining trip (stairs, then a
 * tunnel). Ores showing in the walls get mined out as they're found, torches go down every few
 * steps if the companion carries some, and it stops - and says why - before breaking into lava,
 * water or a drop, at the bottom of the world, or where blocks mustn't be broken.
 */
public final class MineOrder implements CompanionOrder {
	public enum Shape { TUNNEL, STAIRS }

	/** One stretch of digging: a shape and how many steps of it. */
	public record Leg(Shape shape, int length) {
	}

	private static final double REACH = 4.5;
	private static final int TORCH_EVERY = 8;
	private static final int MAX_VEIN = 24;

	private final CompanionEntity companion;
	private final Direction direction;
	private final List<Leg> legs;
	private final BlockWork work;
	private final Deque<BlockPos> veins = new ArrayDeque<>();
	private final Set<BlockPos> veinSeen = new HashSet<>();
	private final Map<String, Integer> found = new LinkedHashMap<>();

	private int legIndex;
	private int step;
	private BlockPos feet;
	private int veinMined;
	private int stepsDone;
	private TaskStatus status = TaskStatus.RUNNING;
	private int repathCooldown;
	private int stuckTicks;

	/**
	 * @param start     where the first step's feet go (usually the block in front of the player)
	 * @param direction which way to dig (horizontal)
	 */
	public MineOrder(CompanionEntity companion, BlockPos start, Direction direction, List<Leg> legs) {
		this.companion = companion;
		this.direction = direction;
		this.legs = List.copyOf(legs);
		this.work = new BlockWork(companion, io.github.tekless.hearthbound.core.persona.Role.Task.DIG);
		this.feet = start.immutable();
	}

	@Override
	public Kind kind() {
		return Kind.MINE;
	}

	@Override
	public void tick(CompanionEntity c) {
		if (status.isFinished()) {
			return;
		}
		ServerLevel level = (ServerLevel) c.level();
		if (!veins.isEmpty()) {
			mineVein(level);
			return;
		}
		if (legIndex >= legs.size()) {
			finish(TaskStatus.SUCCEEDED, "done.mine");
			return;
		}
		Leg leg = legs.get(legIndex);
		if (step >= leg.length()) {
			legIndex++;
			step = 0;
			return;
		}
		if (feet.getY() <= level.getMinY() + 6) {
			finish(TaskStatus.FAILED, "fail.mine_bottom");
			return;
		}
		List<BlockPos> blocks = blocksFor(leg.shape(), feet);
		for (BlockPos pos : blocks) {
			BlockState state = level.getBlockState(pos);
			if (passable(state)) {
				continue;
			}
			String danger = dangerNear(level, pos);
			if (danger != null) {
				finish(TaskStatus.FAILED, "fail.mine_hazard", Map.of("danger", danger));
				return;
			}
			if (!WorldRules.mayBreak(level, pos)) {
				finish(TaskStatus.FAILED, "fail.protected");
				return;
			}
			if (state.getDestroySpeed(level, pos) < 0) {
				finish(TaskStatus.FAILED, "fail.mine_bottom");
				return;
			}
			if (state.requiresCorrectToolForDrops() && !c.bestToolFor(state).map(t -> t.isCorrectToolForDrops(state)).orElse(false)) {
				finish(TaskStatus.FAILED, "fail.needs_tool", Map.of("tool", "pickaxe", "item", blockName(state)));
				return;
			}
			if (!c.hasRoomFor(stack -> true)) {
				finish(TaskStatus.FAILED, "fail.mine_full");
				return;
			}
			if (!inReach(pos)) {
				walkTo(standingSpot());
				return;
			}
			c.getNavigation().stop();
			if (work.step(pos) == BlockWork.Result.DONE && isOre(state)) {
				count(state);
			}
			return;
		}
		// The whole step is open: check what we broke into, look for ore, light it, move on.
		BlockState floor = level.getBlockState(feet.below());
		if (!floor.getFluidState().isEmpty() || floor.isAir()) {
			finish(TaskStatus.FAILED, floor.getFluidState().isEmpty() ? "fail.mine_cave" : "fail.mine_hazard",
					Map.of("danger", floor.getFluidState().is(FluidTags.LAVA) ? "lava" : "water"));
			return;
		}
		for (BlockPos pos : blocks) {
			queueOres(level, pos);
		}
		stepsDone++;
		if (stepsDone % TORCH_EVERY == 0) {
			placeTorch(level, feet);
		}
		step++;
		feet = nextFeet(leg.shape(), feet);
	}

	private void mineVein(ServerLevel level) {
		BlockPos pos = veins.peekFirst();
		BlockState state = level.getBlockState(pos);
		if (!isOre(state) || !WorldRules.mayBreak(level, pos) || dangerNear(level, pos) != null
				|| (state.requiresCorrectToolForDrops() && !companion.bestToolFor(state).map(t -> t.isCorrectToolForDrops(state)).orElse(false))) {
			veins.pollFirst(); // gone, off limits, unsafe or too hard for our pick: leave it
			work.clear();
			return;
		}
		if (!inReach(pos)) {
			if (companion.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > REACH + 2) {
				veins.pollFirst(); // out of the way of the tunnel; not worth a detour
				return;
			}
			walkTo(standingSpot());
			return;
		}
		if (work.step(pos) == BlockWork.Result.DONE) {
			veins.pollFirst();
			count(state);
			queueOres(level, pos);
		}
	}

	private void queueOres(ServerLevel level, BlockPos around) {
		for (Direction d : Direction.values()) {
			BlockPos next = around.relative(d);
			if (veinMined < MAX_VEIN && !veinSeen.contains(next) && isOre(level.getBlockState(next))) {
				veinSeen.add(next.immutable());
				veins.addLast(next.immutable());
				veinMined++;
			}
		}
	}

	private void count(BlockState state) {
		String name = blockName(state).replace(" ore", "").replace("deepslate ", "").replace("nether ", "");
		found.merge(name, 1, Integer::sum);
	}

	private void placeTorch(ServerLevel level, BlockPos at) {
		if (!level.getBlockState(at).isAir() || !Blocks.TORCH.defaultBlockState().canSurvive(level, at)) {
			return;
		}
		for (int i = 0; i < companion.inventory().getContainerSize(); i++) {
			ItemStack stack = companion.inventory().getItem(i);
			if (stack.is(Items.TORCH)) {
				stack.shrink(1);
				level.setBlockAndUpdate(at, Blocks.TORCH.defaultBlockState());
				return;
			}
		}
	}

	private List<BlockPos> blocksFor(Shape shape, BlockPos at) {
		List<BlockPos> blocks = new ArrayList<>();
		if (shape == Shape.STAIRS) {
			blocks.add(at.above(2)); // headroom for stepping down
		}
		blocks.add(at.above());
		blocks.add(at);
		return blocks;
	}

	private BlockPos nextFeet(Shape shape, BlockPos at) {
		BlockPos ahead = at.relative(direction);
		return shape == Shape.STAIRS ? ahead.below() : ahead;
	}

	/** Where to stand to work on the current step: the step behind it. */
	private BlockPos standingSpot() {
		BlockPos back = feet.relative(direction.getOpposite());
		Leg leg = legIndex < legs.size() ? legs.get(legIndex) : null;
		return leg != null && leg.shape() == Shape.STAIRS && step > 0 ? back.above() : back;
	}

	private void walkTo(BlockPos spot) {
		if (--repathCooldown <= 0 || companion.getNavigation().isDone()) {
			repathCooldown = 20;
			if (!companion.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0)) {
				stuckTicks += 20;
			}
		}
		if (++stuckTicks > 400) {
			finish(TaskStatus.FAILED, "fail.stuck");
		}
	}

	private boolean inReach(BlockPos pos) {
		boolean close = companion.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= REACH;
		if (close) {
			stuckTicks = 0;
		}
		return close;
	}

	private static boolean passable(BlockState state) {
		return state.isAir() || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || (state.canBeReplaced() && state.getFluidState().isEmpty());
	}

	/** "lava" or "water" if breaking this block would let it flow into the tunnel, else null. */
	private static String dangerNear(ServerLevel level, BlockPos pos) {
		for (Direction d : Direction.values()) {
			var fluid = level.getFluidState(pos.relative(d));
			if (fluid.is(FluidTags.LAVA)) {
				return "lava";
			}
			if (fluid.is(FluidTags.WATER)) {
				return "water";
			}
		}
		return level.getFluidState(pos).isEmpty() ? null : "water";
	}

	static boolean isOre(BlockState state) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return path.endsWith("_ore") || path.equals("ancient_debris");
	}

	private static String blockName(BlockState state) {
		return state.getBlock().getName().getString().toLowerCase(Locale.ROOT);
	}

	private void finish(TaskStatus result, String lineKey) {
		finish(result, lineKey, Map.of());
	}

	private void finish(TaskStatus result, String lineKey, Map<String, String> extra) {
		status = result;
		work.clear();
		companion.getNavigation().stop();
		Map<String, String> vars = new java.util.HashMap<>(extra);
		vars.put("ores", foundSummary());
		vars.put("count", Integer.toString(stepsDone));
		vars.putIfAbsent("item", "stone");
		vars.putIfAbsent("tool", "pickaxe");
		vars.putIfAbsent("have", "0");
		companion.say(lineKey, vars);
		if (!found.isEmpty()) {
			companion.memory().addEvent("I went mining with {player} and found " + foundSummary());
		}
	}

	/** "5 coal and 2 iron", or "nothing worth keeping". */
	public String foundSummary() {
		if (found.isEmpty()) {
			return "nothing worth keeping";
		}
		List<String> parts = found.entrySet().stream().map(e -> e.getValue() + " " + e.getKey()).collect(Collectors.toList());
		if (parts.size() == 1) {
			return parts.get(0);
		}
		return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
	}

	@Override
	public void pause(CompanionEntity c) {
		work.clear();
		CompanionOrder.super.pause(c);
	}

	@Override
	public void cancel(CompanionEntity c) {
		status = TaskStatus.CANCELLED;
		work.clear();
		c.getNavigation().stop();
	}

	@Override
	public TaskStatus status() {
		return status;
	}

	@Override
	public String describe() {
		Leg leg = legIndex < legs.size() ? legs.get(legIndex) : null;
		String what = leg == null || leg.shape() == Shape.TUNNEL ? "digging a tunnel" : "digging down";
		return what + " (" + stepsDone + " blocks" + (found.isEmpty() ? "" : ", found " + foundSummary()) + ")";
	}

	public int stepsDone() {
		return stepsDone;
	}
}

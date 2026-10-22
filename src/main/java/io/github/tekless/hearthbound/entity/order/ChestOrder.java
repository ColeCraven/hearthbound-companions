package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Putting things from the pack into a chest, or taking them out of it, on the player's say-so: only the chest they
 * were looking at when they asked, and only while they stay close to it. If they walk off, the job stops.
 */
public final class ChestOrder implements CompanionOrder {
	public enum Mode {
		STORE, TAKE
	}

	/** How close the player has to stay to the chest. */
	private static final double PLAYER_RANGE = 10;

	private final Mode mode;
	private final BlockPos chest;
	private final UUID player;
	private final Predicate<ItemStack> filter;
	private final @Nullable Predicate<ItemStack> looser;
	private final String name;
	private final int max;
	private int ticks;
	private int repath;
	private TaskStatus status = TaskStatus.RUNNING;

	/**
	 * @param filter what to move
	 * @param looser a broader match to fall back on when nothing fits {@code filter} ("iron" for iron ingots, not just raw iron)
	 */
	public ChestOrder(Mode mode, BlockPos chest, ServerPlayer player, Predicate<ItemStack> filter, @Nullable Predicate<ItemStack> looser, String name, int max) {
		this.mode = mode;
		this.chest = chest.immutable();
		this.player = player.getUUID();
		this.filter = filter;
		this.looser = looser;
		this.name = name;
		this.max = max;
	}

	/** The chest (or barrel) at a spot, both halves of a double chest together, or null. */
	public static @Nullable Container containerAt(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock block) {
			return ChestBlock.getContainer(block, state, level, pos, true);
		}
		BlockEntity entity = level.getBlockEntity(pos);
		return entity instanceof Container container && state.is(net.minecraft.world.level.block.Blocks.BARREL) ? container : null;
	}

	@Override
	public Kind kind() {
		return Kind.STORE;
	}

	@Override
	public void tick(CompanionEntity c) {
		if (status.isFinished()) {
			return;
		}
		ServerLevel level = (ServerLevel) c.level();
		ServerPlayer asker = level.getServer().getPlayerList().getPlayer(player);
		if (asker == null || asker.level() != level || asker.position().distanceTo(Vec3.atCenterOf(chest)) > PLAYER_RANGE) {
			status = TaskStatus.FAILED;
			c.say("chest.left", Map.of());
			return;
		}
		Container container = containerAt(level, chest);
		if (container == null) {
			status = TaskStatus.FAILED;
			c.say("chest.none", Map.of());
			return;
		}
		if (c.getEyePosition().distanceTo(Vec3.atCenterOf(chest)) > 4.0) {
			if (--repath <= 0 || c.getNavigation().isDone()) {
				repath = 20;
				c.getNavigation().moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 1.1);
			}
			if (++ticks > 400) {
				status = TaskStatus.FAILED;
				c.say("fail.stuck", Map.of());
			}
			return;
		}
		c.getNavigation().stop();
		c.getLookControl().setLookAt(Vec3.atCenterOf(chest));
		c.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		Predicate<ItemStack> what = filter;
		if (looser != null && !anyMatch(mode == Mode.STORE ? c.inventory() : container, filter)) {
			what = looser;
		}
		Map<String, String> vars;
		if (mode == Mode.STORE) {
			int moved = 0;
			boolean full = false;
			for (int slot = 0; slot < c.inventory().getContainerSize() && moved < max; slot++) {
				ItemStack stack = c.inventory().getItem(slot);
				if (stack.isEmpty() || !what.test(stack)) {
					continue;
				}
				int want = Math.min(stack.getCount(), max - moved);
				int in = insert(container, stack.copyWithCount(want));
				stack.shrink(in);
				moved += in;
				if (in < want) {
					full = true;
					break;
				}
			}
			container.setChanged();
			vars = Map.of("count", Integer.toString(moved), "item", name, "have", Integer.toString(moved));
			c.say(moved == 0 ? (full ? "chest.full" : "fail.give_missing") : full ? "chest.full" : "chest.stored", vars);
			status = moved > 0 ? TaskStatus.SUCCEEDED : TaskStatus.FAILED;
		} else {
			int moved = 0;
			boolean packFull = false;
			for (int slot = 0; slot < container.getContainerSize() && moved < max; slot++) {
				ItemStack stack = container.getItem(slot);
				if (stack.isEmpty() || !what.test(stack)) {
					continue;
				}
				int want = Math.min(stack.getCount(), max - moved);
				ItemStack rest = c.inventory().addItem(stack.copyWithCount(want));
				int taken = want - rest.getCount();
				stack.shrink(taken);
				moved += taken;
				if (!rest.isEmpty()) {
					packFull = true;
					break;
				}
			}
			container.setChanged();
			vars = Map.of("count", Integer.toString(moved), "item", name, "have", Integer.toString(moved));
			c.say(moved == 0 ? (packFull ? "fail.inventory_full" : "chest.none_of") : "chest.taken", vars);
			status = moved > 0 ? TaskStatus.SUCCEEDED : TaskStatus.FAILED;
		}
	}

	private static boolean anyMatch(Container container, Predicate<ItemStack> filter) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty() && filter.test(stack)) {
				return true;
			}
		}
		return false;
	}

	/** Puts as much of the stack as fits into the container: onto matching stacks first, then empty slots. Returns how many went in. */
	static int insert(Container container, ItemStack stack) {
		int left = stack.getCount();
		for (int pass = 0; pass < 2 && left > 0; pass++) {
			for (int slot = 0; slot < container.getContainerSize() && left > 0; slot++) {
				ItemStack inside = container.getItem(slot);
				if (pass == 0 && !inside.isEmpty() && ItemStack.isSameItemSameComponents(inside, stack)) {
					int room = Math.min(container.getMaxStackSize(inside), inside.getMaxStackSize()) - inside.getCount();
					int put = Math.min(room, left);
					if (put > 0) {
						inside.grow(put);
						left -= put;
					}
				} else if (pass == 1 && inside.isEmpty() && container.canPlaceItem(slot, stack)) {
					int put = Math.min(left, Math.min(container.getMaxStackSize(stack), stack.getMaxStackSize()));
					container.setItem(slot, stack.copyWithCount(put));
					left -= put;
				}
			}
		}
		return stack.getCount() - left;
	}

	@Override
	public void cancel(CompanionEntity companion) {
		if (!status.isFinished()) {
			status = TaskStatus.CANCELLED;
		}
		companion.getNavigation().stop();
	}

	@Override
	public TaskStatus status() {
		return status;
	}

	@Override
	public String describe() {
		return (mode == Mode.STORE ? "putting " + name + " in your chest" : "taking " + name + " from your chest");
	}
}

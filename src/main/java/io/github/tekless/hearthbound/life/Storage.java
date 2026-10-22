package io.github.tekless.hearthbound.life;

import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.Pastimes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Putting the harvest away. Only ever into a storage chest of the home (marked by a player, set in the config,
 * or a storehouse companions built), never any other chest, and only farm produce.
 */
public final class Storage {
	/** Farm produce that goes into storage. Seeds and a few of each planting crop are kept back for replanting. */
	static final Set<Item> PRODUCE = Set.of(Items.WHEAT, Items.BEETROOT, Items.CARROT, Items.POTATO, Items.POISONOUS_POTATO,
			Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS);
	private static final Map<Item, Integer> KEEP = Map.of(Items.CARROT, 4, Items.POTATO, 4, Items.WHEAT_SEEDS, 16, Items.BEETROOT_SEEDS, 8);

	private Storage() {
	}

	/** Produce in the pack beyond what is kept back for replanting. */
	static int surplus(CompanionEntity companion) {
		int total = 0;
		for (Item item : PRODUCE) {
			total += Math.max(0, companion.countCarried(s -> s.is(item)) - KEEP.getOrDefault(item, 0));
		}
		return total;
	}

	/** How many items like this are in these chests, counting only the loaded ones. */
	static int countIn(ServerLevel level, List<BlockPos> chests, java.util.function.Predicate<net.minecraft.world.item.ItemStack> what) {
		int total = 0;
		for (BlockPos pos : chests) {
			if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof Container container) {
				for (int slot = 0; slot < container.getContainerSize(); slot++) {
					net.minecraft.world.item.ItemStack stack = container.getItem(slot);
					if (!stack.isEmpty() && what.test(stack)) {
						total += stack.getCount();
					}
				}
			}
		}
		return total;
	}

	/** How many of this item fit into the chest. */
	static int room(Container chest, Item item) {
		int room = 0;
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack there = chest.getItem(slot);
			if (there.isEmpty()) {
				room += item.getDefaultMaxStackSize();
			} else if (there.is(item)) {
				room += Math.max(0, there.getMaxStackSize() - there.getCount());
			}
		}
		return room;
	}

	/** Puts up to {@code count} of the item into the chest; returns how many went in. */
	static int insert(Container chest, Item item, int count) {
		int moved = 0;
		for (int slot = 0; slot < chest.getContainerSize() && moved < count; slot++) {
			ItemStack there = chest.getItem(slot);
			if (!there.isEmpty() && there.is(item) && there.getCount() < there.getMaxStackSize()) {
				int add = Math.min(count - moved, there.getMaxStackSize() - there.getCount());
				there.grow(add);
				moved += add;
			}
		}
		for (int slot = 0; slot < chest.getContainerSize() && moved < count; slot++) {
			if (chest.getItem(slot).isEmpty()) {
				int add = Math.min(count - moved, item.getDefaultMaxStackSize());
				chest.setItem(slot, new ItemStack(item, add));
				moved += add;
			}
		}
		if (moved > 0) {
			chest.setChanged();
		}
		return moved;
	}

	static @Nullable Container chestAt(ServerLevel level, BlockPos pos) {
		return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof Container chest ? chest : null;
	}

	/** The storage chest closest to the companion, or null when the home has none. */
	static @Nullable BlockPos nearestChest(CompanionEntity companion, List<BlockPos> storage) {
		BlockPos best = null;
		for (BlockPos pos : storage) {
			if (best == null || pos.distSqr(companion.blockPosition()) < best.distSqr(companion.blockPosition())) {
				best = pos;
			}
		}
		return best;
	}

	/** Walk to a storage chest and put the surplus produce in. */
	static final class Deposit implements Pastimes.Pastime {
		private final BlockPos chest;
		private int ticks;

		Deposit(BlockPos chest) {
			this.chest = chest;
		}

		@Override
		public boolean tick(CompanionEntity c, @Nullable ServerPlayer owner) {
			ServerLevel level = (ServerLevel) c.level();
			if (++ticks > 600) {
				return false;
			}
			c.getLookControl().setLookAt(Vec3.atCenterOf(chest));
			if (c.distanceToSqr(Vec3.atCenterOf(chest)) > 9.0) {
				if (ticks % 20 == 1) {
					c.getNavigation().moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 0.8);
				}
				return true;
			}
			Container container = chestAt(level, chest);
			if (container == null) {
				return false;
			}
			int moved = 0;
			for (Item item : PRODUCE) {
				int spare = Math.max(0, c.countCarried(s -> s.is(item)) - KEEP.getOrDefault(item, 0));
				int put = insert(container, item, Math.min(spare, room(container, item)));
				if (put > 0) {
					c.inventory().removeItemType(item, put);
					moved += put;
				}
			}
			c.getNavigation().stop();
			c.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			if (moved > 0) {
				c.sayMaybe("life.stored", Map.of("count", Integer.toString(moved)), 0.4);
			}
			return false;
		}
	}
}

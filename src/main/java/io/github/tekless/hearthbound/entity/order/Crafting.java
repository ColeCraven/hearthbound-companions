package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Simple, honest crafting from the companion's own pack, with no crafting table and nothing made up:
 * logs to planks, planks to sticks, planks to a chest, planks to doors, cobblestone to a furnace,
 * coal and a stick to torches, planks and sticks to a wooden hoe. Each recipe only runs if the items
 * are really carried, and uses them up.
 */
public final class Crafting {
	private Crafting() {
	}

	/** Turns every log in the pack into planks of the same wood. Returns how many planks were made. */
	public static int planksFromLogs(CompanionEntity c) {
		int made = 0;
		for (ItemStack stack : c.inventory().getItems()) {
			if (stack.isEmpty() || !stack.is(ItemTags.LOGS)) {
				continue;
			}
			Item planks = planksFor(stack.getItem());
			if (planks == null) {
				continue;
			}
			int logs = stack.getCount();
			stack.setCount(0);
			c.storeOrDrop(new ItemStack(planks, logs * 4));
			made += logs * 4;
		}
		return made;
	}

	/** Planks of one kind into sticks (2 planks make 4 sticks). */
	public static boolean sticks(CompanionEntity c) {
		return convert(c, s -> s.is(ItemTags.PLANKS), 2, new ItemStack(Items.STICK, 4));
	}

	public static boolean chest(CompanionEntity c) {
		return convert(c, s -> s.is(ItemTags.PLANKS), 8, new ItemStack(Items.CHEST));
	}

	/** Three doors from six planks of the same wood. */
	public static boolean doors(CompanionEntity c) {
		Item planks = mostPlentiful(c, s -> s.is(ItemTags.PLANKS), 6);
		if (planks == null) {
			return false;
		}
		Item door = doorFor(planks);
		return door != null && convert(c, s -> s.is(planks), 6, new ItemStack(door, 3));
	}

	public static boolean furnace(CompanionEntity c) {
		return convert(c, s -> s.is(Items.COBBLESTONE), 8, new ItemStack(Items.FURNACE));
	}

	/** Four torches from a piece of coal (or charcoal) and a stick, making the stick first if only planks are carried. */
	public static boolean torches(CompanionEntity c) {
		if (c.countCarried(s -> s.is(Items.COAL) || s.is(Items.CHARCOAL)) == 0) {
			return false;
		}
		if (c.countCarried(s -> s.is(Items.STICK)) == 0 && !sticks(c)) {
			return false;
		}
		return convert(c, s -> s.is(Items.STICK), 1, ItemStack.EMPTY)
				&& convert(c, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL), 1, new ItemStack(Items.TORCH, 4));
	}

	/** A wooden hoe from two planks and two sticks (sticks are made from planks when needed). */
	public static boolean hoe(CompanionEntity c) {
		if (c.countCarried(s -> s.is(ItemTags.PLANKS)) < 2 || (c.countCarried(s -> s.is(Items.STICK)) < 2 && !sticks(c))) {
			return false;
		}
		if (c.countCarried(s -> s.is(Items.STICK)) < 2) {
			return false;
		}
		return convert(c, s -> s.is(ItemTags.PLANKS), 2, ItemStack.EMPTY) && convert(c, s -> s.is(Items.STICK), 2, new ItemStack(Items.WOODEN_HOE));
	}

	/** Uses up {@code amount} matching items and adds {@code result}; does nothing unless they are all there. */
	private static boolean convert(CompanionEntity c, Predicate<ItemStack> input, int amount, ItemStack result) {
		if (c.countCarried(input) < amount) {
			return false;
		}
		int left = amount;
		for (ItemStack stack : c.inventory().getItems()) {
			if (left > 0 && !stack.isEmpty() && input.test(stack)) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		if (!result.isEmpty()) {
			c.storeOrDrop(result);
		}
		return true;
	}

	private static Item mostPlentiful(CompanionEntity c, Predicate<ItemStack> filter, int atLeast) {
		Item best = null;
		int bestCount = atLeast - 1;
		for (ItemStack stack : c.inventory().getItems()) {
			if (!stack.isEmpty() && filter.test(stack)) {
				int count = c.countCarried(s -> s.is(stack.getItem()));
				if (count > bestCount) {
					best = stack.getItem();
					bestCount = count;
				}
			}
		}
		return best;
	}

	private static Item planksFor(Item log) {
		String path = BuiltInRegistries.ITEM.getKey(log).getPath().replace("stripped_", "").replace("_log", "_planks")
				.replace("_wood", "_planks").replace("_stem", "_planks").replace("_hyphae", "_planks");
		return lookup(path);
	}

	private static Item doorFor(Item planks) {
		return lookup(BuiltInRegistries.ITEM.getKey(planks).getPath().replace("_planks", "_door"));
	}

	private static Item lookup(String path) {
		Identifier id = Identifier.tryParse("minecraft:" + path);
		return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR).orElse(null);
	}
}

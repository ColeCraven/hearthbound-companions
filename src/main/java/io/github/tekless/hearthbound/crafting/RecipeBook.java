package io.github.tekless.hearthbound.crafting;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * The game's own crafting and smelting recipes, as companions use them: the same recipes a player has, with the
 * items really taken from their pack. Nothing is made unless the recipe matches what they put in the grid, and
 * anything a recipe leaves behind (an empty bucket) goes back in the pack. Recipes bigger than two by two need a
 * crafting table, as they would for you.
 */
public final class RecipeBook {
	/** One way to craft something: the grid (left to right, top to bottom) and what comes out. */
	public record CraftOption(CraftingRecipe recipe, int width, int height, List<Optional<Ingredient>> slots, Item result, int count) {
		public boolean needsTable() {
			return width > 2 || height > 2;
		}

		/** The ingredients the grid holds, one entry per filled slot. */
		public List<Ingredient> ingredients() {
			return slots.stream().flatMap(Optional::stream).toList();
		}
	}

	/** One way to smelt something in a furnace. */
	public record SmeltOption(Ingredient input, Item result, int count) {
	}

	private static Map<Item, List<CraftOption>> crafting = Map.of();
	private static Map<Item, List<SmeltOption>> smelting = Map.of();
	private static RecipeManager indexed;

	private RecipeBook() {
	}

	/** Every crafting recipe that makes this item, simplest first (fewest ingredients). */
	public static List<CraftOption> craftingFor(ServerLevel level, Item item) {
		index(level);
		return crafting.getOrDefault(item, List.of());
	}

	public static List<SmeltOption> smeltingFor(ServerLevel level, Item item) {
		index(level);
		return smelting.getOrDefault(item, List.of());
	}

	/** What smelting this gives, if anything ("raw iron" gives an iron ingot). */
	public static Optional<SmeltOption> smeltingOf(ServerLevel level, ItemStack input) {
		index(level);
		for (List<SmeltOption> options : smelting.values()) {
			for (SmeltOption option : options) {
				if (option.input().test(input)) {
					return Optional.of(option);
				}
			}
		}
		return Optional.empty();
	}

	/** Every item some crafting recipe makes. */
	public static java.util.Set<Item> craftable(ServerLevel level) {
		index(level);
		return crafting.keySet();
	}

	private static synchronized void index(ServerLevel level) {
		RecipeManager manager = level.recipeAccess();
		if (manager == indexed) {
			return;
		}
		Map<Item, List<CraftOption>> crafts = new HashMap<>();
		Map<Item, List<SmeltOption>> smelts = new HashMap<>();
		for (RecipeHolder<?> holder : manager.getRecipes()) {
			Recipe<?> recipe = holder.value();
			try {
				if (recipe instanceof ShapedRecipe shaped) {
					add(crafts, shaped, shaped.getWidth(), shaped.getHeight(), shaped.getIngredients());
				} else if (recipe instanceof ShapelessRecipe shapeless) {
					List<Optional<Ingredient>> slots = shapeless.placementInfo().ingredients().stream().map(Optional::of).toList();
					int side = slots.size() <= 4 ? 2 : 3;
					List<Optional<Ingredient>> grid = new ArrayList<>(slots);
					while (grid.size() < side * side) {
						grid.add(Optional.empty());
					}
					add(crafts, shapeless, side, side, grid);
				} else if (recipe instanceof net.minecraft.world.item.crafting.SmeltingRecipe cooking) {
					List<Ingredient> inputs = cooking.placementInfo().ingredients();
					if (inputs.isEmpty()) {
						continue;
					}
					ItemStack sample = sample(inputs.get(0));
					ItemStack out = cooking.assemble(new SingleRecipeInput(sample));
					if (!out.isEmpty()) {
						smelts.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(new SmeltOption(inputs.get(0), out.getItem(), out.getCount()));
					}
				}
			} catch (RuntimeException e) {
				Hearthbound.LOGGER.debug("Skipped recipe {}: {}", holder.id(), e.getMessage());
			}
		}
		for (List<CraftOption> options : crafts.values()) {
			options.sort(Comparator.comparingInt((CraftOption o) -> o.ingredients().size()));
		}
		crafting = crafts;
		smelting = smelts;
		indexed = manager;
	}

	private static void add(Map<Item, List<CraftOption>> into, CraftingRecipe recipe, int width, int height, List<Optional<Ingredient>> slots) {
		if (slots.size() != width * height || slots.stream().allMatch(Optional::isEmpty)) {
			return;
		}
		List<ItemStack> grid = new ArrayList<>();
		for (Optional<Ingredient> slot : slots) {
			grid.add(slot.map(RecipeBook::sample).orElse(ItemStack.EMPTY));
		}
		ItemStack out = recipe.assemble(CraftingInput.of(width, height, grid));
		if (!out.isEmpty() && out.getItem() != Items.AIR) {
			into.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(new CraftOption(recipe, width, height, slots, out.getItem(), out.getCount()));
		}
	}

	private static ItemStack sample(Ingredient ingredient) {
		return ingredient.items().findFirst().map(holder -> new ItemStack(holder.value())).orElse(ItemStack.EMPTY);
	}

	/** The items an ingredient takes (oak planks, or any planks for a chest). */
	public static List<Item> accepted(Ingredient ingredient) {
		return ingredient.items().map(holder -> holder.value()).toList();
	}

	/**
	 * Crafts once from the companion's pack. Picks, for every slot, the matching item they carry most of, checks the
	 * recipe really matches that grid, then takes the items and adds what it makes.
	 *
	 * @return false (and nothing changes) when they don't have what it takes
	 */
	public static boolean craftOnce(CompanionEntity companion, CraftOption option, ServerLevel level) {
		List<ItemStack> pack = companion.inventory().getItems();
		Map<ItemStack, Integer> used = new IdentityHashMap<>();
		List<ItemStack> grid = new ArrayList<>();
		for (Optional<Ingredient> slot : option.slots()) {
			if (slot.isEmpty()) {
				grid.add(ItemStack.EMPTY);
				continue;
			}
			ItemStack pick = null;
			int best = 0;
			for (ItemStack stack : pack) {
				int free = stack.getCount() - used.getOrDefault(stack, 0);
				if (!stack.isEmpty() && free > 0 && slot.get().test(stack)) {
					int total = companion.countCarried(s -> s.is(stack.getItem()));
					if (pick == null || total > best) {
						pick = stack;
						best = total;
					}
				}
			}
			if (pick == null) {
				return false;
			}
			used.merge(pick, 1, Integer::sum);
			grid.add(pick.copyWithCount(1));
		}
		CraftingInput input = CraftingInput.of(option.width(), option.height(), grid);
		if (!option.recipe().matches(input, level)) {
			return false;
		}
		ItemStack result = option.recipe().assemble(input);
		NonNullList<ItemStack> leftovers = option.recipe().getRemainingItems(input);
		used.forEach(ItemStack::shrink);
		companion.storeOrDrop(result);
		for (ItemStack leftover : leftovers) {
			if (!leftover.isEmpty()) {
				companion.storeOrDrop(leftover);
			}
		}
		return true;
	}

	/** Crafts {@code item} up to {@code times} times with the first recipe that works. Returns how many crafts happened. */
	public static int craft(CompanionEntity companion, ServerLevel level, Item item, int times, boolean haveTable) {
		int done = 0;
		for (CraftOption option : craftingFor(level, item)) {
			if (option.needsTable() && !haveTable) {
				continue;
			}
			while (done < times && craftOnce(companion, option, level)) {
				done++;
			}
			if (done >= times) {
				break;
			}
		}
		return done;
	}
}

package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.crafting.RecipeBook;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Smelting and crafting on request: "smelt this iron", "craft me a pickaxe". Smelting uses a furnace nearby (or one
 * they put down and pick up after) and real furnace time; crafting uses the game's recipes, a crafting table for
 * anything bigger than two by two, and only what's in the pack. When something's missing they say what, before
 * going anywhere.
 */
public final class MakeOrders {
	private MakeOrders() {
	}

	/** What the request came to: started (with the line to say), or refused (with the reason). */
	public record Result(boolean accepted, String lineKey, Map<String, String> vars) {
	}

	private static final Set<String> FILLER = Set.of("this", "that", "these", "those", "the", "my", "your", "some", "a", "an", "of", "it", "them", "all",
			"me", "us", "for", "please", "up", "down", "here", "there", "now");

	/** The words of an item phrase that say what it is ("this iron" is "iron"). */
	static List<String> words(String phrase) {
		List<String> words = new ArrayList<>();
		if (phrase == null) {
			return words;
		}
		for (String word : phrase.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+")) {
			if (!word.isEmpty() && !FILLER.contains(word)) {
				words.add(singular(word));
			}
		}
		return words;
	}

	private static String singular(String word) {
		if (word.endsWith("ies") && word.length() > 4) {
			return word.substring(0, word.length() - 3) + "y";
		}
		if (word.endsWith("ches") || word.endsWith("shes")) {
			return word.substring(0, word.length() - 2);
		}
		if (word.endsWith("s") && !word.endsWith("ss") && word.length() > 3) {
			return word.substring(0, word.length() - 1);
		}
		return word;
	}

	private static boolean names(Item item, List<String> words) {
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();
		for (String word : words) {
			if (!path.contains(word)) {
				return false;
			}
		}
		return !words.isEmpty();
	}

	// ------------------------------------------------------------------ smelting

	/** "Smelt this iron": what in the pack smelts, matching the words (all of it, or {@code count}), in a furnace. */
	public static Result smelt(ServerPlayer player, CompanionEntity c, String phrase, Integer count) {
		ServerLevel level = (ServerLevel) c.level();
		List<String> words = words(phrase);
		Item input = null;
		int total = 0;
		for (ItemStack stack : c.inventory().getItems()) {
			if (stack.isEmpty()) {
				continue;
			}
			Optional<RecipeBook.SmeltOption> option = RecipeBook.smeltingOf(level, stack);
			if (option.isPresent() && (words.isEmpty() || names(stack.getItem(), words) || names(option.get().result(), words))) {
				int have = c.countCarried(s -> s.is(stack.getItem()));
				if (have > total) {
					input = stack.getItem();
					total = have;
				}
			}
		}
		String asked = phrase == null || phrase.isBlank() ? "anything" : String.join(" ", words);
		if (input == null) {
			ItemStack held = player.getMainHandItem();
			Optional<RecipeBook.SmeltOption> option = held.isEmpty() ? Optional.empty() : RecipeBook.smeltingOf(level, held);
			if (option.isPresent() && (words.isEmpty() || names(held.getItem(), words) || names(option.get().result(), words))) {
				return new Result(false, "smelt.hand_over", Map.of("item", Supply.name(held.getItem(), 2)));
			}
			return new Result(false, "smelt.nothing", Map.of("item", asked));
		}
		Item from = input;
		Item result = RecipeBook.smeltingOf(level, new ItemStack(from)).orElseThrow().result();
		int amount = Math.min(64, count == null ? total : Math.min(count, total));
		boolean fuel = c.inventory().getItems().stream().anyMatch(s -> !s.isEmpty() && !s.is(from) && Supply.isFuel(s));
		if (!fuel) {
			return new Result(false, "smelt.no_fuel", Map.of());
		}
		boolean furnace = Supply.Stations.find(level, c.blockPosition(), 12, s -> s.is(Blocks.FURNACE)) != null
				|| c.countCarried(s -> s.is(Items.FURNACE)) > 0 || c.countCarried(s -> s.is(Items.COBBLESTONE)) >= 8;
		if (!furnace) {
			return new Result(false, "smelt.no_furnace", Map.of());
		}
		Supply.Workshop workshop = new Supply.Workshop(pos -> false);
		Supply.SmeltStep smelting = new Supply.SmeltStep(result, amount, workshop, Set.of(from));
		String resultName = Supply.name(result, amount);
		ProjectOrder.Step report = companion -> {
			companion.say("smelt.done", Map.of("count", Integer.toString(smelting.collected()), "item", Supply.name(result, smelting.collected())));
			return ProjectOrder.State.DONE;
		};
		ProjectOrder order = new ProjectOrder(CompanionOrder.Kind.CRAFT, "smelting " + Supply.name(from, amount), List.of(smelting, report),
				() -> workshop.tidy(c));
		c.setOrder(order);
		return new Result(true, "smelt.ack", Map.of("count", Integer.toString(amount), "item", Supply.name(from, amount), "thing", resultName));
	}

	// ------------------------------------------------------------------ crafting

	/** Things made of better stuff come first when someone just asks for "a pickaxe". */
	private static int tier(Item item) {
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();
		return path.startsWith("netherite") ? 6 : path.startsWith("diamond") ? 5 : path.startsWith("iron") ? 4 : path.startsWith("copper") ? 3
				: path.startsWith("stone") ? 2 : path.startsWith("golden") ? 1 : 0;
	}

	/** The items a request could mean: the exact one if it names one, else every craftable item of that kind, best first. */
	static List<Item> candidates(ServerLevel level, String phrase) {
		List<String> words = words(phrase);
		if (words.isEmpty()) {
			return List.of();
		}
		Set<Item> craftable = RecipeBook.craftable(level);
		Identifier exact = Identifier.tryParse("minecraft:" + String.join("_", words));
		if (exact != null) {
			Item item = BuiltInRegistries.ITEM.getOptional(exact).orElse(Items.AIR);
			if (item != Items.AIR && craftable.contains(item)) {
				return List.of(item);
			}
		}
		String kind = words.get(words.size() - 1);
		List<Item> found = new ArrayList<>();
		for (Item item : craftable) {
			String path = BuiltInRegistries.ITEM.getKey(item).getPath();
			if ((path.equals(kind) || path.endsWith("_" + kind)) && names(item, words)) {
				found.add(item);
			}
		}
		found.sort(Comparator.comparingInt(MakeOrders::tier).reversed().thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).getPath()));
		return found;
	}

	/** "Craft me a pickaxe": the best of what was asked that the pack can make, crafted for real, then handed over. */
	public static Result craft(ServerPlayer player, CompanionEntity c, String phrase, Integer count) {
		ServerLevel level = (ServerLevel) c.level();
		List<Item> options = candidates(level, phrase);
		String asked = phrase == null || phrase.isBlank() ? "that" : String.join(" ", words(phrase));
		if (options.isEmpty()) {
			return new Result(false, "craft.unknown", Map.of("item", asked));
		}
		Map<Item, Integer> carried = Supply.carried(c);
		Item chosen = null;
		Map<Item, Integer> wanted = null;
		Map<Item, Integer> fewestMissing = null;
		Item fewestFor = null;
		for (Item option : options) {
			int batch = RecipeBook.craftingFor(level, option).stream().mapToInt(RecipeBook.CraftOption::count).min().orElse(1);
			Map<Item, Integer> want = new LinkedHashMap<>();
			want.put(option, Math.max(1, Math.min(64, count == null ? batch : count)));
			Supply.Plan plan = Supply.plan(level, carried, want, false);
			if (plan.possible()) {
				chosen = option;
				wanted = want;
				break;
			}
			int missing = plan.missing().values().stream().mapToInt(Integer::intValue).sum();
			if (fewestMissing == null || missing < fewestMissing.values().stream().mapToInt(Integer::intValue).sum()) {
				fewestMissing = plan.missing();
				fewestFor = option;
			}
		}
		if (chosen == null) {
			return new Result(false, "craft.missing", Map.of("item", Supply.name(fewestFor, 1), "missing", Supply.describe(fewestMissing)));
		}
		Item item = chosen;
		int amount = wanted.get(item);
		Map<Item, Integer> want = wanted;
		Supply.Workshop workshop = new Supply.Workshop(pos -> false);
		int before = c.countCarried(s -> s.is(item));
		Map<Item, Integer> target = new LinkedHashMap<>();
		target.put(item, before + amount);
		UUID asker = player.getUUID();
		ProjectOrder.Step handOver = companion -> {
			int made = companion.countCarried(s -> s.is(item)) - before;
			ServerPlayer to = ((ServerLevel) companion.level()).getServer().getPlayerList().getPlayer(asker);
			if (made <= 0) {
				return ProjectOrder.State.FAILED;
			}
			if (to != null && to.level() == companion.level() && companion.distanceTo(to) <= 6) {
				int given = companion.giveTo(to, s -> s.is(item), made);
				companion.say("craft.done", Map.of("count", Integer.toString(given), "item", Supply.name(item, given)));
			} else {
				companion.say("craft.done_kept", Map.of("count", Integer.toString(made), "item", Supply.name(item, made)));
			}
			return ProjectOrder.State.DONE;
		};
		String thing = Supply.name(item, amount);
		Supply.Step making = new Supply.Step(() -> target, workshop, thing).withoutGathering();
		ProjectOrder order = new ProjectOrder(CompanionOrder.Kind.CRAFT, "crafting " + thing, List.of(making, handOver), () -> workshop.tidy(c));
		c.setOrder(order);
		return new Result(true, "craft.ack", Map.of("count", Integer.toString(amount), "item", thing));
	}
}

package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.dialogue.English;
import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.crafting.RecipeBook;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.world.GatherCatalog;
import io.github.tekless.hearthbound.world.GatherSpec;
import io.github.tekless.hearthbound.world.WorldRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Getting what a job needs, honestly: what's in the pack first, then natural blocks gathered nearby, then the game's
 * real crafting recipes (at a crafting table for anything bigger than two by two) and furnace smelting. The plan is
 * made again after every step, so whatever really turned up is what counts. If something can't be had at all (wool
 * for a bed, say), they say what's missing before doing anything.
 */
public final class Supply {
	/** Natural blocks that don't drop themselves, so gathering them never gives you one. */
	private static final Set<Item> NOT_BY_GATHERING = Set.of(Items.GRASS_BLOCK, Items.MYCELIUM, Items.PODZOL, Items.STONE, Items.DEEPSLATE,
			Items.ICE, Items.PACKED_ICE, Items.GLOWSTONE, Items.CLAY, Items.SNOW_BLOCK, Items.MELON, Items.MOSS_BLOCK, Items.AMETHYST_CLUSTER,
			Items.NETHER_QUARTZ_ORE, Items.BAMBOO, Items.KELP);

	private Supply() {
	}

	/** One thing to do towards the materials. */
	public sealed interface Action {
	}

	public record Gather(GatherSpec spec, Item item, int count) implements Action {
	}

	public record Craft(Item item, int times, boolean table) implements Action {
	}

	public record Smelt(Item item, int count) implements Action {
	}

	/** What to do, in order, and what can't be had at all. */
	public record Plan(List<Action> actions, Map<Item, Integer> missing) {
		public boolean possible() {
			return missing.isEmpty();
		}
	}

	/** What the companion's pack holds, item by item. */
	public static Map<Item, Integer> carried(CompanionEntity companion) {
		Map<Item, Integer> have = new HashMap<>();
		for (ItemStack stack : companion.inventory().getItems()) {
			if (!stack.isEmpty()) {
				have.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return have;
	}

	/** What is still short of {@code wanted}, given what is carried. */
	public static Map<Item, Integer> shortfall(Map<Item, Integer> carried, Map<Item, Integer> wanted) {
		Map<Item, Integer> missing = new LinkedHashMap<>();
		wanted.forEach((item, count) -> {
			int short_ = count - carried.getOrDefault(item, 0);
			if (short_ > 0) {
				missing.put(item, short_);
			}
		});
		return missing;
	}

	/** Works out how to get everything in {@code wanted} from what is carried. */
	public static Plan plan(ServerLevel level, Map<Item, Integer> carried, Map<Item, Integer> wanted) {
		Planner planner = new Planner(level, new HashMap<>(carried));
		// Take everything wanted from the pack first, so nothing wanted is used up making something else.
		Map<Item, Integer> stillWanted = new LinkedHashMap<>();
		wanted.forEach((item, count) -> {
			int take = Math.min(count, planner.have.getOrDefault(item, 0));
			planner.have.merge(item, -take, Integer::sum);
			if (count - take > 0) {
				stillWanted.put(item, count - take);
			}
		});
		stillWanted.forEach((item, count) -> planner.obtain(item, count, 0));
		return new Plan(planner.actions, planner.missing);
	}

	/** "12 glass and 4 oak stairs". */
	public static String describe(Map<Item, Integer> items) {
		List<String> parts = new ArrayList<>();
		items.forEach((item, count) -> parts.add(count + " " + name(item, count)));
		return parts.isEmpty() ? "nothing" : English.list(parts);
	}

	public static String name(Item item, int count) {
		String name = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
		if (name.contains(".")) {
			name = BuiltInRegistries.ITEM.getKey(item).getPath().replace('_', ' ');
		}
		if (count != 1 && !name.endsWith("s") && !name.endsWith("glass") && !name.endsWith("stone") && !name.endsWith("dirt")
				&& !name.endsWith("sand") && !name.endsWith("wool") && !name.endsWith("terracotta") && !name.endsWith("gravel")) {
			name = name.endsWith("ch") || name.endsWith("sh") ? name + "es" : name + "s";
		}
		return name;
	}

	/** The gather spec that brings this very item home, if it's a natural block that drops itself. */
	public static Optional<GatherSpec> gatherable(Item item) {
		if (NOT_BY_GATHERING.contains(item)) {
			return Optional.empty();
		}
		String phrase = BuiltInRegistries.ITEM.getKey(item).getPath().replace('_', ' ');
		if (GatherCatalog.resolve(phrase) instanceof GatherCatalog.Resolution.Gatherable gatherable && gatherable.spec().counts().test(new ItemStack(item))) {
			return Optional.of(gatherable.spec());
		}
		return Optional.empty();
	}

	/** Fuel a furnace takes, from the usual things anyone would burn. */
	public static boolean isFuel(ItemStack stack) {
		return stack.is(ItemTags.COALS) || stack.is(ItemTags.LOGS_THAT_BURN) || stack.is(ItemTags.PLANKS) || stack.is(Items.STICK)
				|| stack.is(Items.COAL_BLOCK) || stack.is(Items.BLAZE_ROD) || stack.is(Items.DRIED_KELP_BLOCK);
	}

	/** How many items one of this fuel smelts. */
	static double smeltsPer(ItemStack fuel) {
		if (fuel.is(Items.COAL_BLOCK)) {
			return 80;
		}
		if (fuel.is(ItemTags.COALS) || fuel.is(Items.BLAZE_ROD)) {
			return fuel.is(Items.BLAZE_ROD) ? 12 : 8;
		}
		if (fuel.is(Items.DRIED_KELP_BLOCK)) {
			return 20;
		}
		return fuel.is(Items.STICK) ? 0.5 : 1.5;
	}

	private static final class Planner {
		final ServerLevel level;
		final Map<Item, Integer> have;
		final List<Action> actions = new ArrayList<>();
		final Map<Item, Integer> missing = new LinkedHashMap<>();
		final Set<Item> visiting = new HashSet<>();

		Planner(ServerLevel level, Map<Item, Integer> have) {
			this.level = level;
			this.have = have;
		}

		boolean obtain(Item item, int amount, int depth) {
			int take = Math.min(amount, have.getOrDefault(item, 0));
			have.merge(item, -take, Integer::sum);
			amount -= take;
			if (amount <= 0) {
				return true;
			}
			if (depth > 4 || visiting.contains(item)) {
				missing.merge(item, amount, Integer::sum);
				return false;
			}
			visiting.add(item);
			try {
				Optional<GatherSpec> spec = gatherable(item);
				if (spec.isPresent()) {
					actions.add(new Gather(spec.get(), item, amount));
					return true;
				}
				for (RecipeBook.CraftOption option : RecipeBook.craftingFor(level, item)) {
					int times = (amount + option.count() - 1) / option.count();
					Snapshot snapshot = snapshot();
					boolean ok = true;
					for (net.minecraft.world.item.crafting.Ingredient ingredient : option.ingredients()) {
						Item pick = pick(ingredient);
						if (pick == null || !obtain(pick, times, depth + 1)) {
							ok = false;
							break;
						}
					}
					if (ok) {
						actions.add(new Craft(item, times, option.needsTable()));
						have.merge(item, times * option.count() - amount, Integer::sum);
						return true;
					}
					restore(snapshot);
				}
				for (RecipeBook.SmeltOption option : RecipeBook.smeltingFor(level, item)) {
					Snapshot snapshot = snapshot();
					Item input = pick(option.input());
					if (input != null && obtain(input, amount, depth + 1) && fuelFor(amount)) {
						actions.add(new Smelt(item, amount));
						return true;
					}
					restore(snapshot);
				}
				missing.merge(item, amount, Integer::sum);
				return false;
			} finally {
				visiting.remove(item);
			}
		}

		/** Fuel for a smelt: coal or wood already carried, else logs to gather and burn. */
		private boolean fuelFor(int items) {
			for (Map.Entry<Item, Integer> entry : have.entrySet()) {
				ItemStack sample = new ItemStack(entry.getKey());
				if (entry.getValue() > 0 && isFuel(sample)) {
					int needed = (int) Math.ceil(items / smeltsPer(sample));
					if (entry.getValue() >= needed) {
						have.merge(entry.getKey(), -needed, Integer::sum);
						return true;
					}
				}
			}
			if (!(GatherCatalog.resolve("logs") instanceof GatherCatalog.Resolution.Gatherable logs)) {
				return false;
			}
			actions.add(new Gather(logs.spec(), Items.OAK_LOG, (int) Math.ceil(items / 1.5)));
			return true;
		}

		/** Which item to use for an ingredient: the one carried most, else one that can be gathered, else one that can be made. */
		private @Nullable Item pick(net.minecraft.world.item.crafting.Ingredient ingredient) {
			List<Item> options = RecipeBook.accepted(ingredient);
			Item best = null;
			int bestCount = 0;
			for (Item option : options) {
				int count = have.getOrDefault(option, 0);
				if (count > bestCount) {
					best = option;
					bestCount = count;
				}
			}
			if (best != null) {
				return best;
			}
			for (Item option : options) {
				if (gatherable(option).isPresent()) {
					return option;
				}
			}
			for (Item option : options) {
				if (!RecipeBook.craftingFor(level, option).isEmpty() || !RecipeBook.smeltingFor(level, option).isEmpty()) {
					return option;
				}
			}
			return options.isEmpty() ? null : options.get(0);
		}

		private record Snapshot(Map<Item, Integer> have, int actions, Map<Item, Integer> missing) {
		}

		private Snapshot snapshot() {
			return new Snapshot(new HashMap<>(have), actions.size(), new LinkedHashMap<>(missing));
		}

		private void restore(Snapshot snapshot) {
			have.clear();
			have.putAll(snapshot.have());
			while (actions.size() > snapshot.actions()) {
				actions.remove(actions.size() - 1);
			}
			missing.clear();
			missing.putAll(snapshot.missing());
		}
	}

	// ------------------------------------------------------------------ the work itself

	/** The crafting table and furnace a job uses, and whether the companion put them down (so they pick them up after). */
	public static final class Workshop {
		@Nullable BlockPos table;
		@Nullable BlockPos furnace;
		boolean placedTable;
		boolean placedFurnace;
		/** Spots that must stay free (where a build goes). */
		final Predicate<BlockPos> reserved;

		public Workshop(Predicate<BlockPos> reserved) {
			this.reserved = reserved;
		}

		/** Picks up whatever the companion put down for the job, if it's still there. */
		public void tidy(CompanionEntity c) {
			ServerLevel level = (ServerLevel) c.level();
			if (placedTable && table != null && level.getBlockState(table).is(Blocks.CRAFTING_TABLE)) {
				level.removeBlock(table, false);
				c.storeOrDrop(new ItemStack(Items.CRAFTING_TABLE));
			}
			if (placedFurnace && furnace != null && level.getBlockState(furnace).is(Blocks.FURNACE)) {
				if (level.getBlockEntity(furnace) instanceof AbstractFurnaceBlockEntity entity) {
					for (int slot = 0; slot < entity.getContainerSize(); slot++) {
						ItemStack left = entity.removeItemNoUpdate(slot);
						if (!left.isEmpty()) {
							c.storeOrDrop(left);
						}
					}
				}
				level.removeBlock(furnace, false);
				c.storeOrDrop(new ItemStack(Items.FURNACE));
			}
			placedTable = false;
			placedFurnace = false;
		}
	}

	/**
	 * Gets everything {@code wanted} asks for into the pack, a step at a time, saying what they're doing. Fails, after
	 * saying what's missing, when something can't be had or a step keeps going wrong.
	 */
	public static final class Step implements ProjectOrder.Step {
		private final Supplier<Map<Item, Integer>> wanted;
		private final Workshop workshop;
		private final String thing;
		private ProjectOrder.@Nullable Step current;
		private int steps;
		private int failures;
		private final Set<String> announced = new HashSet<>();
		private Map<Item, Integer> lastMissing = Map.of();

		/** @param thing what it's all for, for what they say ("your cottage", "a pickaxe") */
		public Step(Supplier<Map<Item, Integer>> wanted, Workshop workshop, String thing) {
			this.wanted = wanted;
			this.workshop = workshop;
			this.thing = thing;
		}

		/** What couldn't be had, when the step failed. */
		public Map<Item, Integer> missing() {
			return lastMissing;
		}

		@Override
		public ProjectOrder.State tick(CompanionEntity c) {
			if (current != null) {
				ProjectOrder.State state = current.tick(c);
				if (state == ProjectOrder.State.RUNNING) {
					return state;
				}
				if (state == ProjectOrder.State.FAILED) {
					failures++;
				}
				current = null;
			}
			ServerLevel level = (ServerLevel) c.level();
			Map<Item, Integer> want = wanted.get();
			Plan plan = plan(level, carried(c), want);
			if (plan.actions().isEmpty() && plan.possible()) {
				return ProjectOrder.State.DONE;
			}
			if (!plan.possible() || failures >= 3 || steps >= 16) {
				lastMissing = plan.possible() ? shortfall(carried(c), want) : plan.missing();
				if (lastMissing.isEmpty()) {
					lastMissing = shortfall(carried(c), want);
				}
				c.say("supply.blocked", Map.of("thing", thing, "missing", Supply.describe(lastMissing)));
				return ProjectOrder.State.FAILED;
			}
			Action action = plan.actions().get(0);
			steps++;
			current = switch (action) {
				case Gather gather -> {
					announce(c, "supply.gather", gather.count(), gather.spec().displayName());
					yield new GatherStep(new GatherOrder(c, gather.spec(), Math.min(64, gather.count())).keepForSelf());
				}
				case Craft craft -> {
					announce(c, "supply.craft", craft.times(), name(craft.item(), 2));
					yield new CraftStep(craft.item(), craft.times(), craft.table(), workshop);
				}
				case Smelt smelt -> {
					announce(c, "supply.smelt", smelt.count(), name(smelt.item(), smelt.count()));
					yield new SmeltStep(smelt.item(), smelt.count(), workshop, want.keySet());
				}
			};
			return ProjectOrder.State.RUNNING;
		}

		private void announce(CompanionEntity c, String key, int count, String item) {
			if (announced.add(key + item)) {
				c.say(key, Map.of("count", Integer.toString(count), "item", item, "thing", thing));
			}
		}

		@Override
		public void pause(CompanionEntity c) {
			if (current != null) {
				current.pause(c);
			}
			ProjectOrder.Step.super.pause(c);
		}

		@Override
		public void stop(CompanionEntity c) {
			if (current != null) {
				current.stop(c);
			}
			workshop.tidy(c);
		}

		@Override
		public String describe() {
			return current != null ? current.describe() : "working out materials";
		}
	}

	/** A gather run whose haul stays in the pack. */
	static final class GatherStep implements ProjectOrder.Step {
		private final GatherOrder order;

		GatherStep(GatherOrder order) {
			this.order = order;
		}

		@Override
		public ProjectOrder.State tick(CompanionEntity c) {
			order.tick(c);
			if (!order.status().isFinished()) {
				return ProjectOrder.State.RUNNING;
			}
			return order.status() == TaskStatus.SUCCEEDED ? ProjectOrder.State.DONE : ProjectOrder.State.FAILED;
		}

		@Override
		public void pause(CompanionEntity c) {
			order.pause(c);
		}

		@Override
		public void stop(CompanionEntity c) {
			order.cancel(c);
		}

		@Override
		public String describe() {
			return "gathering " + order.spec().displayName();
		}
	}

	/** Walks to a block and stays within reach of it. True once there. Gives up after a while. */
	private static final class Approach {
		private int ticks;
		private int repath;

		boolean near(CompanionEntity c, BlockPos pos) {
			if (c.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= 4.0) {
				c.getNavigation().stop();
				c.getLookControl().setLookAt(Vec3.atCenterOf(pos));
				ticks = 0;
				return true;
			}
			ticks++;
			if (--repath <= 0 || c.getNavigation().isDone()) {
				repath = 20;
				c.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
			}
			return false;
		}

		boolean gaveUp() {
			return ticks > 400;
		}
	}

	/** Uses a crafting table nearby, or puts one down (crafting it from planks if need be), then crafts. */
	static final class CraftStep implements ProjectOrder.Step {
		private final Item item;
		private final int times;
		private final boolean table;
		private final Workshop workshop;
		private final Approach approach = new Approach();

		CraftStep(Item item, int times, boolean table, Workshop workshop) {
			this.item = item;
			this.times = times;
			this.table = table;
			this.workshop = workshop;
		}

		@Override
		public ProjectOrder.State tick(CompanionEntity c) {
			ServerLevel level = (ServerLevel) c.level();
			if (table) {
				if (workshop.table == null || !level.getBlockState(workshop.table).is(Blocks.CRAFTING_TABLE)) {
					workshop.table = Stations.find(level, c.blockPosition(), 12, s -> s.is(Blocks.CRAFTING_TABLE));
					workshop.placedTable = false;
				}
				if (workshop.table == null) {
					if (c.countCarried(s -> s.is(Items.CRAFTING_TABLE)) == 0) {
						if (c.countCarried(s -> s.is(ItemTags.PLANKS)) < 4) {
							Crafting.planksFromLogs(c);
						}
						RecipeBook.craft(c, level, Items.CRAFTING_TABLE, 1, false);
					}
					workshop.table = Stations.placeFromPack(level, c, Items.CRAFTING_TABLE, Blocks.CRAFTING_TABLE, workshop.reserved);
					workshop.placedTable = workshop.table != null;
					if (workshop.table == null) {
						c.say("craft.no_table", Map.of());
						return ProjectOrder.State.FAILED;
					}
				}
				if (!approach.near(c, workshop.table)) {
					return approach.gaveUp() ? ProjectOrder.State.FAILED : ProjectOrder.State.RUNNING;
				}
			}
			int done = RecipeBook.craft(c, level, item, times, table);
			if (done > 0) {
				c.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				level.playSound(null, c.blockPosition(), Blocks.CRAFTING_TABLE.defaultBlockState().getSoundType().getHitSound(), SoundSource.BLOCKS, 0.6F, 1.2F);
			}
			return done >= times ? ProjectOrder.State.DONE : ProjectOrder.State.FAILED;
		}

		@Override
		public String describe() {
			return "crafting " + name(item, 2);
		}
	}

	/**
	 * Smelts in a furnace nearby, or one they put down (made from cobblestone if they have to). Fuel goes in one piece at a
	 * time while it's needed, the input goes in, and what comes out goes into the pack. Real furnace time, about ten
	 * seconds an item.
	 */
	public static final class SmeltStep implements ProjectOrder.Step {
		private final Item item;
		private final int count;
		private final Workshop workshop;
		private final Set<Item> keep;
		private final Approach approach = new Approach();
		private int collected;
		private int waited;
		private boolean loaded;

		/** @param keep items the job needs for itself, never burnt as fuel */
		public SmeltStep(Item item, int count, Workshop workshop, Set<Item> keep) {
			this.item = item;
			this.count = count;
			this.workshop = workshop;
			this.keep = keep;
		}

		public int collected() {
			return collected;
		}

		@Override
		public ProjectOrder.State tick(CompanionEntity c) {
			ServerLevel level = (ServerLevel) c.level();
			if (workshop.furnace == null || !(level.getBlockEntity(workshop.furnace) instanceof AbstractFurnaceBlockEntity)) {
				workshop.furnace = Stations.find(level, c.blockPosition(), 12, s -> s.is(Blocks.FURNACE)
						&& level.getBlockEntity(Stations.lastFound()) instanceof AbstractFurnaceBlockEntity f && free(f));
				workshop.placedFurnace = false;
				if (workshop.furnace == null) {
					if (c.countCarried(s -> s.is(Items.FURNACE)) == 0 && !Crafting.furnace(c)) {
						c.say("smelt.no_furnace", Map.of());
						return ProjectOrder.State.FAILED;
					}
					workshop.furnace = Stations.placeFromPack(level, c, Items.FURNACE, Blocks.FURNACE, workshop.reserved);
					workshop.placedFurnace = workshop.furnace != null;
					if (workshop.furnace == null) {
						c.say("smelt.no_furnace", Map.of());
						return ProjectOrder.State.FAILED;
					}
				}
			}
			if (!approach.near(c, workshop.furnace)) {
				return approach.gaveUp() ? ProjectOrder.State.FAILED : ProjectOrder.State.RUNNING;
			}
			if (!(level.getBlockEntity(workshop.furnace) instanceof AbstractFurnaceBlockEntity furnace)) {
				return ProjectOrder.State.FAILED;
			}
			// Take out whatever is done.
			ItemStack out = furnace.getItem(2);
			if (!out.isEmpty() && out.is(item)) {
				ItemStack taken = furnace.removeItem(2, out.getCount());
				collected += taken.getCount();
				c.storeOrDrop(taken);
				furnace.setChanged();
			}
			if (collected >= count) {
				return ProjectOrder.State.DONE;
			}
			// Load the input once.
			if (!loaded) {
				loaded = true;
				int put = 0;
				for (ItemStack stack : c.inventory().getItems()) {
					if (put < count - collected && !stack.isEmpty() && smeltsInto(level, stack)) {
						ItemStack inside = furnace.getItem(0);
						if (!inside.isEmpty() && !ItemStack.isSameItemSameComponents(inside, stack)) {
							continue;
						}
						int take = Math.min(count - collected - put, Math.min(stack.getCount(), stack.getMaxStackSize() - inside.getCount()));
						if (take <= 0) {
							continue;
						}
						ItemStack moved = stack.split(take);
						furnace.setItem(0, inside.isEmpty() ? moved : inside.copyWithCount(inside.getCount() + moved.getCount()));
						put += take;
					}
				}
				furnace.setChanged();
				if (put == 0 && furnace.getItem(0).isEmpty()) {
					return ProjectOrder.State.FAILED;
				}
			}
			// Keep it burning while there's something in it.
			BlockState state = level.getBlockState(workshop.furnace);
			boolean lit = state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT);
			if (!lit && furnace.getItem(1).isEmpty() && !furnace.getItem(0).isEmpty()) {
				ItemStack fuel = null;
				for (ItemStack stack : c.inventory().getItems()) {
					if (!stack.isEmpty() && isFuel(stack) && !keep.contains(stack.getItem()) && furnace.canPlaceItem(1, stack)) {
						fuel = stack;
						break;
					}
				}
				if (fuel == null) {
					c.say("smelt.no_fuel", Map.of());
					return ProjectOrder.State.FAILED;
				}
				furnace.setItem(1, fuel.split(1));
				furnace.setChanged();
				c.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			}
			if (furnace.getItem(0).isEmpty() && furnace.getItem(2).isEmpty()) {
				return collected > 0 ? ProjectOrder.State.DONE : ProjectOrder.State.FAILED;
			}
			if (++waited > 400 + count * 260) {
				return ProjectOrder.State.FAILED;
			}
			return ProjectOrder.State.RUNNING;
		}

		private boolean smeltsInto(ServerLevel level, ItemStack stack) {
			return RecipeBook.smeltingOf(level, stack).map(option -> option.result() == item).orElse(false);
		}

		private static boolean free(AbstractFurnaceBlockEntity furnace) {
			return furnace.getItem(0).isEmpty() && furnace.getItem(2).isEmpty();
		}

		@Override
		public String describe() {
			return "smelting " + name(item, 2) + " (" + collected + "/" + count + ")";
		}
	}

	/** Finding and putting down work blocks. */
	static final class Stations {
		private static BlockPos found = BlockPos.ZERO;

		private Stations() {
		}

		/** The position being checked by the predicate passed to {@link #find} (so it can look at the block entity). */
		static BlockPos lastFound() {
			return found;
		}

		static @Nullable BlockPos find(ServerLevel level, BlockPos from, int radius, Predicate<BlockState> what) {
			BlockPos best = null;
			for (BlockPos pos : BlockPos.betweenClosed(from.offset(-radius, -4, -radius), from.offset(radius, 4, radius))) {
				found = pos;
				if (what.test(level.getBlockState(pos)) && (best == null || pos.distSqr(from) < best.distSqr(from))) {
					best = pos.immutable();
				}
			}
			return best;
		}

		/** Puts a block down from the pack on a free spot beside the companion. Returns where, or null. */
		static @Nullable BlockPos placeFromPack(ServerLevel level, CompanionEntity c, Item item, Block block, Predicate<BlockPos> reserved) {
			ItemStack stack = null;
			for (ItemStack s : c.inventory().getItems()) {
				if (s.is(item)) {
					stack = s;
					break;
				}
			}
			if (stack == null) {
				return null;
			}
			BlockPos feet = c.blockPosition();
			for (int r = 1; r <= 3; r++) {
				for (Direction direction : Direction.Plane.HORIZONTAL) {
					BlockPos pos = feet.relative(direction, r);
					for (int dy = 0; dy >= -1; dy--) {
						BlockPos spot = pos.above(dy);
						BlockState here = level.getBlockState(spot);
						if ((here.isAir() || here.canBeReplaced()) && level.getFluidState(spot).isEmpty() && !reserved.test(spot)
								&& level.getBlockState(spot.below()).isSolidRender() && !WorldRules.isProtected(level, spot)
								&& level.getEntities(null, new net.minecraft.world.phys.AABB(spot)).isEmpty()) {
							BlockState placed = block.defaultBlockState();
							if (placed.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
								placed = placed.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, direction.getOpposite());
							}
							level.setBlockAndUpdate(spot, placed);
							level.playSound(null, spot, placed.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
							c.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
							stack.shrink(1);
							return spot.immutable();
						}
					}
				}
			}
			return null;
		}
	}
}

package io.github.tekless.hearthbound.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns what the player asked for ("oak logs", "iron", "a few flowers") into a {@link GatherSpec},
 * or explains why it can't be gathered. Only naturally occurring blocks are allowed, so a
 * companion asked for "planks" or "glass" won't dismantle your house.
 */
public final class GatherCatalog {
	public sealed interface Resolution {
		record Gatherable(GatherSpec spec) implements Resolution {
		}

		/** A real item, but not something you can collect by breaking natural blocks. */
		record NotGatherable(String displayName) implements Resolution {
		}

		record Unknown(String phrase) implements Resolution {
		}
	}

	private static final Map<String, GatherSpec> ALIASES = new LinkedHashMap<>();

	static {
		GatherSpec logs = new GatherSpec("logs", "logs", s -> s.is(BlockTags.LOGS), i -> i.is(ItemTags.LOGS), 16, true);
		alias(logs, "log", "logs", "wood", "woods", "tree", "trees", "timber", "lumber", "wood logs");
		GatherSpec cobble = blocks("cobblestone", "cobblestone", Items.COBBLESTONE, 32, Blocks.STONE, Blocks.COBBLESTONE);
		alias(cobble, "stone", "stones", "cobble", "cobblestone", "cobblestones", "rock", "rocks");
		alias(blocks("dirt", "dirt", Items.DIRT, 32, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT),
				"dirt", "soil", "earth");
		alias(blocks("sand", "sand", Items.SAND, 32, Blocks.SAND), "sand");
		alias(blocks("red_sand", "red sand", Items.RED_SAND, 32, Blocks.RED_SAND), "red sand");
		alias(blocks("gravel", "gravel", Items.GRAVEL, 32, Blocks.GRAVEL), "gravel");
		alias(blocks("clay", "clay", Items.CLAY_BALL, 16, Blocks.CLAY), "clay", "clay balls", "clay ball");
		alias(ore("coal", "coal", Items.COAL, BlockItemTags.COAL_ORES.block()), "coal", "coal ore", "coal ores");
		alias(ore("raw_iron", "raw iron", Items.RAW_IRON, BlockTags.IRON_ORES), "iron", "iron ore", "iron ores", "raw iron");
		alias(ore("raw_copper", "raw copper", Items.RAW_COPPER, BlockTags.COPPER_ORES), "copper", "copper ore", "copper ores", "raw copper");
		alias(ore("raw_gold", "raw gold", Items.RAW_GOLD, BlockTags.GOLD_ORES), "gold", "gold ore", "gold ores", "raw gold");
		alias(ore("diamond", "diamonds", Items.DIAMOND, BlockItemTags.DIAMOND_ORES.block()), "diamond", "diamonds", "diamond ore", "diamond ores");
		alias(ore("redstone", "redstone", Items.REDSTONE, BlockItemTags.REDSTONE_ORES.block()), "redstone", "redstone dust", "redstone ore");
		alias(ore("lapis", "lapis lazuli", Items.LAPIS_LAZULI, BlockItemTags.LAPIS_ORES.block()), "lapis", "lapis lazuli", "lapis ore");
		alias(ore("emerald", "emeralds", Items.EMERALD, BlockItemTags.EMERALD_ORES.block()), "emerald", "emeralds", "emerald ore");
		alias(new GatherSpec("flowers", "flowers", s -> s.is(BlockTags.SMALL_FLOWERS), i -> i.is(BlockItemTags.SMALL_FLOWERS.item()), 8, false),
				"flower", "flowers");
		alias(blocks("sugar_cane", "sugar cane", Items.SUGAR_CANE, 16, Blocks.SUGAR_CANE), "sugar cane", "sugarcane", "cane", "reeds");
		alias(blocks("pumpkin", "pumpkins", Items.PUMPKIN, 4, Blocks.PUMPKIN), "pumpkin", "pumpkins");
		alias(blocks("melon", "melon slices", Items.MELON_SLICE, 9, Blocks.MELON), "melon", "melons", "melon slice", "melon slices");
		alias(new GatherSpec("mushrooms", "mushrooms", s -> s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.RED_MUSHROOM),
				i -> i.is(Items.BROWN_MUSHROOM) || i.is(Items.RED_MUSHROOM), 8, false), "mushroom", "mushrooms");
		alias(blocks("cactus", "cactus", Items.CACTUS, 8, Blocks.CACTUS), "cactus", "cacti");
		alias(blocks("snowball", "snowballs", Items.SNOWBALL, 16, Blocks.SNOW_BLOCK, Blocks.SNOW), "snow", "snowball", "snowballs");
		alias(blocks("netherrack", "netherrack", Items.NETHERRACK, 32, Blocks.NETHERRACK), "netherrack");
		alias(blocks("cobbled_deepslate", "cobbled deepslate", Items.COBBLED_DEEPSLATE, 32, Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE),
				"deepslate", "cobbled deepslate");
		alias(blocks("seeds", "wheat seeds", Items.WHEAT_SEEDS, 8, Blocks.SHORT_GRASS, Blocks.TALL_GRASS), "seeds", "seed", "wheat seeds");
	}

	/** Natural blocks that can also be requested by their own name ("granite", "moss block"...). */
	private static final List<Predicate<BlockState>> NATURAL = List.of(
			s -> s.is(BlockTags.BASE_STONE_OVERWORLD),
			s -> s.is(BlockTags.BASE_STONE_NETHER),
			s -> s.is(BlockTags.DIRT),
			s -> s.is(BlockTags.SAND),
			s -> s.is(BlockTags.LOGS),
			s -> s.is(BlockTags.SMALL_FLOWERS),
			s -> s.is(Blocks.CALCITE) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.MUD) || s.is(Blocks.END_STONE)
					|| s.is(Blocks.SOUL_SAND) || s.is(Blocks.SOUL_SOIL) || s.is(Blocks.OBSIDIAN) || s.is(Blocks.ICE)
					|| s.is(Blocks.PACKED_ICE) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.AMETHYST_CLUSTER) || s.is(Blocks.DRIPSTONE_BLOCK)
					|| s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.TERRACOTTA) || s.is(Blocks.SNOW_BLOCK)
					|| s.is(Blocks.NETHER_QUARTZ_ORE) || s.is(Blocks.GLOWSTONE) || s.is(Blocks.BAMBOO) || s.is(Blocks.KELP_PLANT));

	private GatherCatalog() {
	}

	public static Resolution resolve(String phrase) {
		if (phrase == null || phrase.isBlank()) {
			return new Resolution.Unknown("");
		}
		String key = normalise(phrase);
		GatherSpec alias = ALIASES.get(key);
		if (alias != null) {
			return new Resolution.Gatherable(alias);
		}
		// Species-specific logs and stems: "oak logs", "birch wood", "crimson stems".
		for (String suffix : List.of(" logs", " log", " wood", " stems", " stem")) {
			if (key.endsWith(suffix)) {
				String species = key.substring(0, key.length() - suffix.length()).trim().replace(' ', '_');
				Optional<Item> item = itemById(species + (suffix.contains("stem") ? "_stem" : "_log"));
				if (item.isPresent() && item.get() instanceof BlockItem blockItem) {
					Block block = blockItem.getBlock();
					Item drop = item.get();
					String name = displayName(drop, true);
					return new Resolution.Gatherable(new GatherSpec(id(drop), name, s -> s.is(block), i -> i.is(drop), 16, true));
				}
			}
		}
		Optional<Item> item = findItem(key);
		if (item.isEmpty()) {
			return new Resolution.Unknown(phrase);
		}
		Item found = item.get();
		if (found instanceof BlockItem blockItem && isNatural(blockItem.getBlock().defaultBlockState())) {
			Block block = blockItem.getBlock();
			boolean logs = block.defaultBlockState().is(BlockTags.LOGS);
			return new Resolution.Gatherable(new GatherSpec(id(found), displayName(found, true),
					s -> s.is(block), i -> i.is(found), logs ? 16 : 32, logs));
		}
		return new Resolution.NotGatherable(displayName(found, true));
	}

	/** Looks a saved spec key back up (aliases first, then item ids). */
	public static Optional<GatherSpec> byKey(String key) {
		for (GatherSpec spec : ALIASES.values()) {
			if (spec.key().equals(key)) {
				return Optional.of(spec);
			}
		}
		Identifier id = Identifier.tryParse(key);
		if (id == null) {
			return Optional.empty();
		}
		Resolution resolution = resolve(id.getPath().replace('_', ' '));
		return resolution instanceof Resolution.Gatherable g ? Optional.of(g.spec()) : Optional.empty();
	}

	public static boolean isNatural(BlockState state) {
		for (Predicate<BlockState> test : NATURAL) {
			if (test.test(state)) {
				return true;
			}
		}
		return false;
	}

	/** Every alias the catalog knows; used by tests and for suggestions. */
	public static List<String> aliases() {
		return new ArrayList<>(ALIASES.keySet());
	}

	private static Optional<Item> findItem(String key) {
		String underscored = key.replace(' ', '_');
		List<String> candidates = new ArrayList<>();
		candidates.add(underscored);
		if (underscored.endsWith("ies")) {
			candidates.add(underscored.substring(0, underscored.length() - 3) + "y");
		}
		if (underscored.endsWith("es")) {
			candidates.add(underscored.substring(0, underscored.length() - 2));
		}
		if (underscored.endsWith("s")) {
			candidates.add(underscored.substring(0, underscored.length() - 1));
		}
		for (String candidate : candidates) {
			Optional<Item> item = itemById(candidate);
			if (item.isPresent()) {
				return item;
			}
		}
		return Optional.empty();
	}

	private static Optional<Item> itemById(String path) {
		Identifier id = Identifier.tryParse(path.contains(":") ? path : "minecraft:" + path);
		if (id == null) {
			return Optional.empty();
		}
		return BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR);
	}

	private static String normalise(String phrase) {
		return phrase.toLowerCase(Locale.ROOT).replace('_', ' ').replaceAll("[^a-z0-9 :]", "").replaceAll("\\s+", " ").trim();
	}

	private static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	private static String displayName(Item item, boolean plural) {
		String name = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
		if (name.contains(".")) {
			// Language not loaded (e.g. in tests): fall back to the registry path.
			name = BuiltInRegistries.ITEM.getKey(item).getPath().replace('_', ' ');
		}
		if (plural && !name.endsWith("s") && !name.endsWith("dirt") && !name.endsWith("sand") && !name.endsWith("gravel")
				&& !name.endsWith("stone") && !name.endsWith("rack") && !name.endsWith("clay")) {
			name = name.endsWith("y") && !name.endsWith("ey") ? name.substring(0, name.length() - 1) + "ies" : name + "s";
		}
		return name;
	}

	private static GatherSpec blocks(String key, String name, Item drop, int defaultCount, Block... blocks) {
		return new GatherSpec(key, name, s -> {
			for (Block block : blocks) {
				if (s.is(block)) {
					return true;
				}
			}
			return false;
		}, i -> i.is(drop), defaultCount, false);
	}

	private static GatherSpec ore(String key, String name, Item drop, TagKey<Block> ores) {
		return new GatherSpec(key, name, s -> s.is(ores), i -> i.is(drop), key.equals("diamond") || key.equals("emerald") ? 3 : 8, false);
	}

	private static void alias(GatherSpec spec, String... names) {
		for (String name : names) {
			ALIASES.put(name, spec);
		}
	}
}

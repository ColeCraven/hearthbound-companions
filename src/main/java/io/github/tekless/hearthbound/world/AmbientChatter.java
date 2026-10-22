package io.github.tekless.hearthbound.world;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.core.intent.QuestionKind;
import io.github.tekless.hearthbound.core.memory.PlayerFacts;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.network.HearthboundNetwork;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Unprompted comments and warnings, driven by things that are really happening near the player.
 * Each companion comments on what its archetype cares about, with cooldowns so it never chatters
 * over itself; warnings (creeper, mob behind you, low health, lava) skip most of the cooldown.
 * Runs once a second per player, from the server tick.
 */
public final class AmbientChatter {
	private static final Map<CompanionEntity, State> STATES = new WeakHashMap<>();
	/** Replies from one companion to another, said a couple of seconds later. */
	private static final java.util.List<Banter> PENDING = new java.util.ArrayList<>();

	private record Banter(CompanionEntity speaker, String other, long dueTick) {
	}

	private static final class State {
		int cooldown = 200;
		int warningCooldown;
		String lastBiome;
		boolean wasNight;
		boolean wasRaining;
		boolean wasUnderground;
		boolean warnedLowHealth;
		boolean suggestedEat;
		boolean fedHurt;
		long shelterDay = -1;
		long lastCarry = -100000;
		long lastQuestion = -100000;
		long lastDislike = -100000;
		long flowerDay = -1;
		/** Likes already brought up, and when, so "you said you loved the ocean" isn't said at every beach. */
		final Map<String, Long> recalled = new HashMap<>();
	}

	/** Food a friend wouldn't hand you. */
	private static final Set<Item> BAD_FOOD = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH,
			Items.SUSPICIOUS_STEW, Items.CHORUS_FRUIT);

	private AmbientChatter() {
	}

	/** Called once a second (every 20 ticks) for each online player. */
	public static void tick(ServerPlayer player) {
		HearthboundNetwork.sendPartyStatus(player);
		double chatter = Hearthbound.config().ambientChatter;
		long now = player.level().getGameTime();
		PENDING.removeIf(banter -> {
			if (now < banter.dueTick()) {
				return false;
			}
			if (banter.speaker().isAlive() && !banter.speaker().isDowned()) {
				banter.speaker().say("banter.reply", Map.of("other", banter.other()));
			}
			return true;
		});
		Exchanges.tick(now);
		boolean someoneSpoke = false;
		java.util.List<CompanionEntity> party = Conversations.party(player);
		for (CompanionEntity companion : party) {
			State state = STATES.computeIfAbsent(companion, c -> initialState(c));
			state.cooldown -= 20;
			state.warningCooldown -= 20;
			if (companion.isDowned() || someoneSpoke) {
				continue;
			}
			// Warnings and food for someone in need always get through; everything else waits for a pause and respects chattiness.
			if (feed(player, companion, state) || warn(player, companion, state)) {
				someoneSpoke = true;
			} else if (chatter > 0 && !Conversations.isChatting(player)
					&& (suggest(player, companion, state) || comment(player, companion, state, chatter))) {
				someoneSpoke = true;
				maybeBanter(companion, party, now, chatter);
			}
		}
		if (!someoneSpoke) {
			Exchanges.maybeStartFor(player, party, chatter);
		}
	}

	/** Another nearby companion sometimes answers what was just said. */
	private static void maybeBanter(CompanionEntity speaker, java.util.List<CompanionEntity> party, long now, double chatter) {
		java.util.List<CompanionEntity> listeners = party.stream()
				.filter(c -> c != speaker && !c.isDowned() && c.distanceTo(speaker) < 16).toList();
		if (listeners.isEmpty() || speaker.getRandom().nextDouble() > 0.35 * Math.min(2.0, chatter)) {
			return;
		}
		CompanionEntity listener = listeners.get(speaker.getRandom().nextInt(listeners.size()));
		PENDING.add(new Banter(listener, speaker.companionName(), now + 40 + speaker.getRandom().nextInt(40)));
	}

	/**
	 * Food handed over when the player is starving, or badly hurt and could eat, if this companion carries some and is
	 * close by. Once per bad patch, whatever the chattiness: it's help, not chatter.
	 */
	private static boolean feed(ServerPlayer player, CompanionEntity companion, State state) {
		int food = player.getFoodData().getFoodLevel();
		if (food > 12) {
			state.suggestedEat = false;
		}
		if (player.getHealth() > 12) {
			state.fedHurt = false;
		}
		boolean starving = food <= 6 && !state.suggestedEat;
		boolean hurt = player.getHealth() <= 6 && player.getFoodData().needsFood() && !state.fedHurt;
		if ((!starving && !hurt) || companion.distanceTo(player) > 6) {
			return false;
		}
		Optional<ItemStack> snack = bestFood(companion);
		if (snack.isEmpty()) {
			return false;
		}
		Item item = snack.get().getItem();
		String name = snack.get().getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
		int given = companion.giveTo(player, stack -> stack.is(item), hurt && !starving ? 2 : 3);
		if (given <= 0) {
			return false;
		}
		state.suggestedEat |= starving;
		state.fedHurt |= hurt;
		return speak(companion, state, "gift.food", Map.of("item", name), true);
	}

	/** Practical suggestions: eat, find shelter, hand over some of that load. Never acted on alone. */
	private static boolean suggest(ServerPlayer player, CompanionEntity companion, State state) {
		if (state.cooldown > 0 && state.warningCooldown > 0) {
			return false;
		}
		int food = player.getFoodData().getFoodLevel();
		if (food <= 6 && !state.suggestedEat) {
			state.suggestedEat = true; // feed() already handed over anything they had
			return speak(companion, state, "suggest.eat", Map.of(), true);
		}
		ServerLevel level = (ServerLevel) companion.level();
		long time = level.getOverworldClockTime() % 24000;
		long day = level.getOverworldClockTime() / 24000;
		if (time >= 11800 && time < 12600 && state.shelterDay != day && level.canSeeSky(player.blockPosition())
				&& companion.archetype() != Archetype.MYSTIC) {
			state.shelterDay = day;
			return speak(companion, state, "suggest.shelter", Map.of(), true);
		}
		long now = level.getGameTime();
		if (player.getInventory().getFreeSlot() < 0 && now - state.lastCarry > 12000) {
			state.lastCarry = now;
			return speak(companion, state, "suggest.carry", Map.of(), true);
		}
		return state.cooldown <= 0 && giveFlower(player, companion, state, day);
	}

	/** Once a day at most, a companion who likes you hands over a flower they picked on the way. */
	private static boolean giveFlower(ServerPlayer player, CompanionEntity companion, State state, long day) {
		int affinity = companion.memory().affinity();
		boolean fond = affinity >= 40 || (affinity >= 10 && (companion.archetype() == Archetype.NATURALIST || companion.archetype() == Archetype.FARMHAND));
		if (!fond || state.flowerDay == day || companion.distanceTo(player) > 5 || companion.getRandom().nextInt(30) != 0) {
			return false;
		}
		for (ItemStack stack : companion.inventory().getItems()) {
			if (stack.is(net.minecraft.tags.BlockItemTags.SMALL_FLOWERS.item())) {
				Item flower = stack.getItem();
				String name = stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
				if (companion.giveTo(player, s -> s.is(flower), 1) > 0) {
					state.flowerDay = day;
					companion.wave();
					return speak(companion, state, "gift.flower", Map.of("item", name), false);
				}
			}
		}
		return false;
	}

	private static Optional<ItemStack> bestFood(CompanionEntity companion) {
		ItemStack best = null;
		int bestNutrition = 0;
		for (ItemStack stack : companion.inventory().getItems()) {
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (food != null && !BAD_FOOD.contains(stack.getItem()) && food.nutrition() > bestNutrition) {
				best = stack;
				bestNutrition = food.nutrition();
			}
		}
		return Optional.ofNullable(best);
	}

	private static State initialState(CompanionEntity companion) {
		State state = new State();
		state.lastBiome = Conversations.biomeName(companion);
		state.wasNight = companion.level().isDarkOutside();
		state.wasRaining = companion.level().isRaining();
		state.wasUnderground = !companion.level().canSeeSky(companion.blockPosition());
		return state;
	}

	/** Urgent, always-useful warnings. */
	private static boolean warn(ServerPlayer player, CompanionEntity companion, State state) {
		if (state.warningCooldown > 0 || companion.getTarget() != null) {
			return false;
		}
		ServerLevel level = (ServerLevel) companion.level();
		for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(10), e -> e instanceof Enemy && e.isAlive())) {
			if (mob instanceof Creeper creeper && creeper.distanceTo(player) < 6) {
				return speak(companion, state, "combat.creeper", Map.of(), true);
			}
			if (mob instanceof Mob hostile && hostile.getTarget() == player && isBehind(player, mob)) {
				return speak(companion, state, "callout.hostile_behind", Map.of("mob", mob.getName().getString().toLowerCase()), true);
			}
			long now = level.getGameTime();
			if (now - state.lastDislike > 6000) {
				String name = mob.getName().getString().toLowerCase();
				Optional<String> hated = facts(player, companion).flatMap(f -> f.dislikedMatch(name));
				if (hated.isPresent()) {
					state.lastDislike = now;
					return speak(companion, state, "recall.disliked", Map.of("thing", hated.get(), "mob", name), true);
				}
			}
		}
		if (player.getHealth() <= 6 && !state.warnedLowHealth) {
			state.warnedLowHealth = true;
			return speak(companion, state, "combat.player_low_health", Map.of(), true);
		}
		if (player.getHealth() > 12) {
			state.warnedLowHealth = false;
		}
		if (companion.archetype().interests().contains(Archetype.Interest.DANGER) || companion.archetype() == Archetype.GUARDIAN) {
			BlockPos feet = player.blockPosition();
			for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-3, -2, -3), feet.offset(3, 0, 3))) {
				if (level.getFluidState(pos).is(FluidTags.LAVA)) {
					return speak(companion, state, "callout.lava", Map.of(), true);
				}
			}
		}
		return false;
	}

	/** Flavour: weather, time, places, and things the archetype is interested in. */
	private static boolean comment(ServerPlayer player, CompanionEntity companion, State state, double chatter) {
		ServerLevel level = (ServerLevel) companion.level();
		boolean night = level.isDarkOutside();
		boolean raining = level.isRaining();
		boolean underground = !level.canSeeSky(companion.blockPosition()) && companion.getY() < level.getSeaLevel() - 4;
		String biome = Conversations.biomeName(companion);
		String key = null;
		Map<String, String> vars = Map.of();
		if (night != state.wasNight) {
			key = night ? "ambient.night" : "ambient.dawn";
		} else if (raining && !state.wasRaining) {
			key = level.isThundering() ? "ambient.thunder" : "ambient.rain";
		} else if (underground && !state.wasUnderground) {
			key = "ambient.cave";
		} else if (!biome.equals(state.lastBiome)) {
			Optional<String> loved = recall(player, companion, state, biome);
			if (loved.isPresent()) {
				key = "recall.liked";
				vars = Map.of("thing", loved.get());
			} else {
				key = biomeKey(level, companion);
			}
		}
		state.wasNight = night;
		state.wasRaining = raining;
		state.wasUnderground = underground;
		state.lastBiome = biome;
		if (key == null && state.cooldown <= 0) {
			String spotted = spotInteresting(level, companion);
			if (spotted != null) {
				key = spotted.startsWith("ore:") ? "callout.ore" : "callout.chest";
				String item = spotted.startsWith("ore:") ? spotted.substring(4) : "chest";
				vars = Map.of("item", item);
				Optional<String> loved = recall(player, companion, state, item);
				if (loved.isPresent()) {
					key = "recall.liked_find";
					vars = Map.of("item", item, "thing", loved.get());
				}
			} else if (companion.getRandom().nextInt(4) == 0) {
				if (companion.getRandom().nextInt(5) < 2 && askSomething(player, companion, state)) {
					return true;
				}
				key = "ambient.idle";
			}
		}
		if (key == null || state.cooldown > 0) {
			return false;
		}
		boolean spoke = speak(companion, state, key, vars, false);
		if (spoke) {
			Archetype.Chattiness chattiness = companion.archetype().chattiness();
			int base = chattiness.minTicks() + companion.getRandom().nextInt(chattiness.maxTicks() - chattiness.minTicks());
			state.cooldown = (int) (base / Math.max(0.25, chatter));
		}
		return spoke;
	}

	/**
	 * In a quiet moment, asks the player something about themselves, and remembers the answer
	 * (see Conversations#answer). Only one-on-one, never mid-conversation or mid-task.
	 */
	private static boolean askSomething(ServerPlayer player, CompanionEntity companion, State state) {
		long now = companion.level().getGameTime();
		if (now - state.lastQuestion < 6000 || !companion.isOwnedBy(player) || companion.distanceTo(player) > 10 || companion.getTarget() != null
				|| !companion.order().kind().isMode() || player.isSleeping() || Conversations.isChatting(player) || !Conversations.aloneWithCompanions(player)) {
			return false;
		}
		PlayerFacts facts = companion.factsAbout(player);
		java.util.List<QuestionKind> options = new java.util.ArrayList<>(java.util.List.of(QuestionKind.HOW, QuestionKind.OPEN));
		if (facts.nickname().isEmpty() && companion.memory().affinity() >= 5) {
			options.add(QuestionKind.NICKNAME);
			options.add(QuestionKind.NICKNAME);
		}
		if (facts.likes().size() < 3) {
			options.add(QuestionKind.LIKE);
			options.add(QuestionKind.LIKE);
		}
		if (facts.dislikes().size() < 2) {
			options.add(QuestionKind.DISLIKE);
		}
		QuestionKind kind = options.get(companion.getRandom().nextInt(options.size()));
		state.lastQuestion = now;
		companion.ask(player, kind, "ask." + kind.name().toLowerCase(java.util.Locale.ROOT), Map.of());
		Conversations.beginChat(player, companion);
		Archetype.Chattiness chattiness = companion.archetype().chattiness();
		state.cooldown = Math.max(state.cooldown, chattiness.minTicks());
		return true;
	}

	/** Something the player said they love, if it's right here (and hasn't been mentioned in the last day). */
	private static Optional<String> recall(ServerPlayer player, CompanionEntity companion, State state, String worldThing) {
		long now = companion.level().getGameTime();
		Optional<String> loved = facts(player, companion).flatMap(f -> f.likedMatch(worldThing));
		if (loved.isEmpty() || now - state.recalled.getOrDefault(loved.get(), -100000L) < 24000) {
			return Optional.empty();
		}
		state.recalled.put(loved.get(), now);
		return loved;
	}

	private static Optional<PlayerFacts> facts(ServerPlayer player, CompanionEntity companion) {
		return companion.isOwnedBy(player) ? companion.memory().knownFacts(player.getUUID().toString()) : Optional.empty();
	}

	private static String biomeKey(ServerLevel level, CompanionEntity companion) {
		String dimension = level.dimension().identifier().getPath();
		if (dimension.equals("the_nether")) {
			return "ambient.biome.nether";
		}
		if (dimension.equals("the_end")) {
			return "ambient.biome.end";
		}
		String biome = Conversations.biomeName(companion);
		if (biome.contains("forest") || biome.contains("taiga") || biome.contains("grove") || biome.contains("birch")) {
			return "ambient.biome.forest";
		}
		if (biome.contains("desert") || biome.contains("badlands")) {
			return "ambient.biome.desert";
		}
		if (biome.contains("snow") || biome.contains("frozen") || biome.contains("ice")) {
			return "ambient.biome.snow";
		}
		if (biome.contains("ocean") || biome.contains("beach") || biome.contains("river")) {
			return "ambient.biome.ocean";
		}
		if (biome.contains("jungle") || biome.contains("bamboo")) {
			return "ambient.biome.jungle";
		}
		if (biome.contains("swamp") || biome.contains("mangrove")) {
			return "ambient.biome.swamp";
		}
		if (biome.contains("peak") || biome.contains("hill") || biome.contains("mountain") || biome.contains("slope")) {
			return "ambient.biome.mountain";
		}
		if (biome.contains("plains") || biome.contains("meadow") || biome.contains("savanna")) {
			return "ambient.biome.plains";
		}
		return null;
	}

	/** Exposed ores or chests the archetype cares about, within a few blocks. */
	private static String spotInteresting(ServerLevel level, CompanionEntity companion) {
		var interests = companion.archetype().interests();
		boolean ores = interests.contains(Archetype.Interest.ORES) || interests.contains(Archetype.Interest.LOOT);
		boolean chests = interests.contains(Archetype.Interest.CHESTS) || interests.contains(Archetype.Interest.LOOT);
		if (!ores && !chests) {
			return null;
		}
		BlockPos origin = companion.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-6, -3, -6), origin.offset(6, 3, 6))) {
			BlockState state = level.getBlockState(pos);
			if (ores && (state.is(net.minecraft.tags.BlockItemTags.DIAMOND_ORES.block()) || state.is(BlockTags.GOLD_ORES) || state.is(net.minecraft.tags.BlockItemTags.EMERALD_ORES.block())
					|| state.is(BlockTags.IRON_ORES)) && level.getBlockState(pos.above()).isAir()) {
				return "ore:" + state.getBlock().getName().getString().toLowerCase();
			}
			if (chests && (state.is(Blocks.CHEST) || state.is(Blocks.BARREL)) && !companion.level().getBlockState(pos).isAir()) {
				return "chest";
			}
		}
		return null;
	}

	private static boolean isBehind(ServerPlayer player, LivingEntity mob) {
		Vec3 look = player.getViewVector(1.0F).multiply(1, 0, 1).normalize();
		Vec3 toMob = mob.position().subtract(player.position()).multiply(1, 0, 1).normalize();
		return look.dot(toMob) < -0.3;
	}

	private static boolean speak(CompanionEntity companion, State state, String key, Map<String, String> vars, boolean warning) {
		companion.say(key, vars);
		if (warning) {
			state.warningCooldown = 200;
			state.cooldown = Math.max(state.cooldown, 200);
		}
		return true;
	}
}

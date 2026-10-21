package io.github.tekless.hearthbound.world;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.network.HearthboundNetwork;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
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

	private static final class State {
		int cooldown = 200;
		int warningCooldown;
		String lastBiome;
		boolean wasNight;
		boolean wasRaining;
		boolean wasUnderground;
		boolean warnedLowHealth;
	}

	private AmbientChatter() {
	}

	/** Called once a second (every 20 ticks) for each online player. */
	public static void tick(ServerPlayer player) {
		HearthboundNetwork.sendPartyStatus(player);
		double chatter = Hearthbound.config().ambientChatter;
		boolean someoneSpoke = false;
		for (CompanionEntity companion : Conversations.party(player)) {
			State state = STATES.computeIfAbsent(companion, c -> initialState(c));
			state.cooldown -= 20;
			state.warningCooldown -= 20;
			if (companion.isDowned() || chatter <= 0 || someoneSpoke) {
				continue;
			}
			someoneSpoke = warn(player, companion, state) || comment(player, companion, state, chatter);
		}
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
			key = biomeKey(level, companion);
		}
		state.wasNight = night;
		state.wasRaining = raining;
		state.wasUnderground = underground;
		state.lastBiome = biome;
		if (key == null && state.cooldown <= 0) {
			String spotted = spotInteresting(level, companion);
			if (spotted != null) {
				key = spotted.startsWith("ore:") ? "callout.ore" : "callout.chest";
				vars = Map.of("item", spotted.startsWith("ore:") ? spotted.substring(4) : "chest");
			} else if (companion.getRandom().nextInt(4) == 0) {
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

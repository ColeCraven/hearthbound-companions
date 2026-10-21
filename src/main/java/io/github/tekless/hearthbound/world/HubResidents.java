package io.github.tekless.hearthbound.world;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.config.HearthboundConfig;
import io.github.tekless.hearthbound.core.persona.CastMember;
import io.github.tekless.hearthbound.dialogue.Texts;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.order.HomeOrder;
import io.github.tekless.hearthbound.registry.ModEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;

/**
 * The cast living at the server's spawn hub. Anyone listed in {@code server.hub.residents} who
 * doesn't exist yet is spawned there once; after that they're ordinary companions who happen to
 * call the hub home: they stand around and chat there, follow whoever recruits them, and make
 * their own way back when dismissed.
 */
public final class HubResidents {
	private static final double GREET_RANGE = 5.0;
	private static final long GREET_COOLDOWN = 2400;
	private static final Map<UUID, Long> LAST_GREETED = new HashMap<>();

	private HubResidents() {
	}

	public static boolean enabled() {
		return Hearthbound.config().server.hub.enabled;
	}

	public static Optional<BlockPos> hubPos() {
		HearthboundConfig.Hub hub = Hearthbound.config().server.hub;
		return hub.enabled ? Optional.of(new BlockPos(hub.x, hub.y, hub.z)) : Optional.empty();
	}

	public static Optional<ServerLevel> hubLevel(MinecraftServer server) {
		Identifier id = Identifier.tryParse(Hearthbound.config().server.hub.dimension);
		if (!enabled() || id == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(server.getLevel(ResourceKey.create(Registries.DIMENSION, id)));
	}

	/** The cast members configured to live at the hub. */
	public static List<CastMember> residents() {
		List<String> names = Hearthbound.config().server.hub.residents;
		List<CastMember> found = new ArrayList<>();
		for (String name : names) {
			if (name.equalsIgnoreCase("all")) {
				return List.of(CastMember.values());
			}
			CastMember.byName(name).ifPresentOrElse(member -> {
				if (!found.contains(member)) {
					found.add(member);
				}
			}, () -> Hearthbound.LOGGER.warn("server.hub.residents: no cast member called '{}'", name));
		}
		return found;
	}

	/** Runs once a second from the server tick. */
	public static void tick(MinecraftServer server) {
		if (!enabled()) {
			return;
		}
		Optional<ServerLevel> found = hubLevel(server);
		if (found.isEmpty()) {
			return;
		}
		ServerLevel level = found.get();
		BlockPos hub = hubPos().orElseThrow();
		if (server.getTickCount() % 200 == 0 && level.isLoaded(hub)) {
			spawnMissing(level, hub);
		}
		greetVisitors(level, hub);
	}

	/** Spawns every resident who doesn't exist anywhere in the world yet. Returns how many. */
	public static int spawnMissing(ServerLevel level, BlockPos hub) {
		CompanionRoster roster = CompanionRoster.get(level.getServer());
		List<CastMember> missing = new ArrayList<>();
		for (CastMember member : residents()) {
			if (!roster.castExists(member)) {
				missing.add(member);
			}
		}
		int index = 0;
		for (CastMember member : missing) {
			CompanionEntity companion = ModEntities.COMPANION.create(level, EntitySpawnReason.EVENT);
			if (companion == null) {
				continue;
			}
			BlockPos spot = findSpot(level, companion, hub, index++, missing.size());
			companion.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
			companion.initIdentity(member);
			companion.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
			companion.giveStarterGear();
			companion.makeHubResident(hub);
			level.addFreshEntity(companion);
			roster.noteLoaded(companion);
			Hearthbound.LOGGER.info("{} moved into the hub at {}", member.displayName(), spot.toShortString());
		}
		return missing.size();
	}

	/** Puts a dismissed resident back at the hub (from wherever they are, any dimension). */
	public static void sendHome(CompanionEntity companion) {
		if (!(companion.level() instanceof ServerLevel current)) {
			return;
		}
		Optional<ServerLevel> found = hubLevel(current.getServer());
		Optional<BlockPos> hub = hubPos();
		if (found.isEmpty() || hub.isEmpty()) {
			return;
		}
		ServerLevel level = found.get();
		boolean far = level != current || companion.blockPosition().distSqr(hub.get()) > 48 * 48;
		if (far) {
			level.getChunkAt(hub.get()); // make sure the hub is loaded before arriving
			BlockPos spot = findSpot(level, companion, hub.get(), companion.getRandom().nextInt(12), 12);
			current.sendParticles(ParticleTypes.POOF, companion.getX(), companion.getY() + 1, companion.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			companion.getNavigation().stop();
			if (level == current) {
				companion.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, companion.getYRot(), 0);
			} else {
				companion.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, Set.of(), companion.getYRot(), 0, false);
			}
		}
		companion.setOrder(new HomeOrder(hub.get()));
	}

	/** One friendly hello (and a first-visit tip) when someone walks up to the residents. */
	private static void greetVisitors(ServerLevel level, BlockPos hub) {
		HearthboundConfig.Hub config = Hearthbound.config().server.hub;
		long now = level.getGameTime();
		CompanionRoster roster = CompanionRoster.get(level.getServer());
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.blockPosition().distSqr(hub) > (long) (config.wanderRadius + 16) * (config.wanderRadius + 16)) {
				continue;
			}
			CompanionEntity nearest = null;
			double best = GREET_RANGE * GREET_RANGE;
			for (CompanionEntity companion : CompanionTracker.near(player, GREET_RANGE)) {
				double d = companion.distanceToSqr(player);
				if (!companion.isRecruited() && companion.isHubResident() && !companion.isDowned() && d < best) {
					nearest = companion;
					best = d;
				}
			}
			if (nearest == null) {
				continue;
			}
			if (config.welcomeTip && !roster.welcomed(player.getUUID())) {
				roster.markWelcomed(player.getUUID());
				player.sendSystemMessage(Texts.tr("message.hearthbound.welcome_tip", nearest.companionName().split(" ")[0])
						.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
			Long last = LAST_GREETED.get(player.getUUID());
			if (last == null || now - last > GREET_COOLDOWN) {
				LAST_GREETED.put(player.getUUID(), now);
				nearest.getLookControl().setLookAt(player);
				nearest.noteSpeaker(player);
				nearest.say("greet.stranger", Map.of("player", player.getName().getString()));
			}
		}
	}

	/**
	 * A standing spot on a ring around the hub centre. If that direction is blocked (a bench, a
	 * stall), nearby directions and distances are tried; the centre itself is the very last resort,
	 * since it's often a fountain or a campfire.
	 */
	private static BlockPos findSpot(ServerLevel level, CompanionEntity companion, BlockPos hub, int index, int count) {
		int radius = Math.max(2, Math.min(6, Hearthbound.config().server.hub.wanderRadius / 2));
		double angle = (Math.PI * 2 * index) / Math.max(1, count);
		for (int step = 0; step < 16; step++) {
			// 0, +1, -1, +2, -2 ... sixteenths of a turn away from the preferred direction
			double a = angle + Math.PI / 8 * ((step + 1) / 2) * (step % 2 == 0 ? 1 : -1);
			for (int r : new int[] {radius, radius + 1, radius - 1, radius + 2, radius - 2}) {
				if (r < 2) {
					continue;
				}
				BlockPos found = standableColumn(level, hub.getX() + (int) Math.round(Math.cos(a) * r),
						hub.getY(), hub.getZ() + (int) Math.round(Math.sin(a) * r));
				if (found != null) {
					return found;
				}
			}
		}
		return hub;
	}

	private static BlockPos standableColumn(ServerLevel level, int x, int y, int z) {
		for (int dy = 3; dy >= -3; dy--) {
			BlockPos pos = new BlockPos(x, y + dy, z);
			if (standable(level, pos)) {
				return pos;
			}
		}
		return null;
	}

	private static boolean standable(Level level, BlockPos pos) {
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
				&& level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
				&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
				&& level.getFluidState(pos).isEmpty();
	}

	/** Lower-case resident ids, for commands and messages. */
	public static List<String> residentIds() {
		return residents().stream().map(member -> member.id().toLowerCase(Locale.ROOT)).toList();
	}
}

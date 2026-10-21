package io.github.tekless.hearthbound.world;

import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Every companion currently loaded on the server. Lookups like "this player's party" or "who is
 * called Hazel near me" walk this short list instead of scanning big boxes of the world, which
 * keeps things cheap with many players online.
 */
public final class CompanionTracker {
	private static final Set<CompanionEntity> LOADED = new LinkedHashSet<>();

	private CompanionTracker() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof CompanionEntity companion) {
				LOADED.add(companion);
				CompanionRoster.get(level.getServer()).noteLoaded(companion);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof CompanionEntity companion) {
				LOADED.remove(companion);
				Entity.RemovalReason reason = companion.getRemovalReason();
				if (reason != null && reason.shouldDestroy()) {
					CompanionRoster.get(level.getServer()).forget(companion.getUUID());
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOADED.clear());
	}

	/** Loaded, living companions (a copy, safe to iterate while companions move between levels). */
	public static List<CompanionEntity> all() {
		List<CompanionEntity> alive = new ArrayList<>(LOADED.size());
		for (CompanionEntity companion : LOADED) {
			if (companion.isAlive() && !companion.isRemoved()) {
				alive.add(companion);
			}
		}
		return alive;
	}

	/** Loaded companions in the player's dimension within {@code range} blocks. */
	public static List<CompanionEntity> near(ServerPlayer player, double range) {
		List<CompanionEntity> found = new ArrayList<>();
		double rangeSq = range * range;
		for (CompanionEntity companion : LOADED) {
			if (companion.isAlive() && companion.level() == player.level() && companion.distanceToSqr(player) <= rangeSq) {
				found.add(companion);
			}
		}
		return found;
	}

	/** The player's loaded companions in any dimension. */
	public static List<CompanionEntity> ownedBy(ServerPlayer player) {
		List<CompanionEntity> found = new ArrayList<>();
		for (CompanionEntity companion : LOADED) {
			if (companion.isAlive() && companion.isOwnedBy(player)) {
				found.add(companion);
			}
		}
		return found;
	}

	public static Set<CompanionEntity> view() {
		return Collections.unmodifiableSet(LOADED);
	}
}

package io.github.tekless.hearthbound.world;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.config.HearthboundConfig;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Obey mode: every companion in the world takes orders from you, whether they travel with nobody, with you,
 * or with another player (an admin override). Companions who aren't with anyone only join you if you ask
 * them to, and everyone goes back to what they were doing when the job ends.
 *
 * <p>Only the host of a single-player world and server operators (permission level 2 or more) can use it.
 * It is on by default for the host; on a server each operator turns it on for themselves, and it stops
 * working for anyone who loses operator rights.
 */
public final class Obey {
	/** A test server is never single-player, so tests name their "hosts" here. */
	private static final java.util.Set<UUID> TEST_HOSTS = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private Obey() {
	}

	/** The host of a single-player world (the first player in). */
	public static boolean isHost(ServerPlayer player) {
		if (TEST_HOSTS.contains(player.getUUID())) {
			return true;
		}
		MinecraftServer server = player.level().getServer();
		return server.isSingleplayer() && server.getPlayerList().getPlayers().indexOf(player) == 0;
	}

	/** The single-player host, or an operator (permission level 2+). Nobody else can use obey mode. */
	public static boolean mayUse(ServerPlayer player) {
		return isHost(player) || Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
	}

	/** Whether this player's orders reach every companion, not only their own. */
	public static boolean active(ServerPlayer player) {
		HearthboundConfig config = Hearthbound.config();
		if (isHost(player)) {
			return config.obeyAll;
		}
		return config.obeyPlayers.contains(player.getUUID().toString()) && mayUse(player);
	}

	/** Turns obey mode on or off for this player and saves it. Returns false if they aren't allowed to. */
	public static boolean set(ServerPlayer player, boolean on) {
		return set(player, on, true);
	}

	/** @param save write the config file too (tests switch it off so they leave nothing behind) */
	public static boolean set(ServerPlayer player, boolean on, boolean save) {
		if (on && !mayUse(player)) {
			return false;
		}
		HearthboundConfig config = Hearthbound.config();
		if (isHost(player)) {
			config.obeyAll = on;
		} else if (on) {
			if (!config.obeyPlayers.contains(player.getUUID().toString())) {
				config.obeyPlayers.add(player.getUUID().toString());
			}
		} else {
			config.obeyPlayers.remove(player.getUUID().toString());
		}
		if (save) {
			config.save();
		}
		return true;
	}

	/** Used by tests: treat this player as a single-player host. */
	public static void addTestHost(UUID player) {
		TEST_HOSTS.add(player);
	}

	public static void removeTestHost(UUID player) {
		TEST_HOSTS.remove(player);
	}
}

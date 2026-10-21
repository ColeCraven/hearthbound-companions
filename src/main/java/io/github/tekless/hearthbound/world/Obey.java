package io.github.tekless.hearthbound.world;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.config.HearthboundConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Obey mode: every companion in the world takes orders from you, recruited or not. They don't become
 * followers unless asked, and go back to what they were doing when the job ends. On by default for the
 * host of a single-player world; on a dedicated server it is off, op only, and only for the op who set it.
 */
public final class Obey {
	private Obey() {
	}

	/** The host of a single-player world (the first player in). */
	public static boolean isHost(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		return server.isSingleplayer() && server.getPlayerList().getPlayers().indexOf(player) == 0;
	}

	/** Whether this player's orders reach companions that aren't theirs. */
	public static boolean active(ServerPlayer player) {
		HearthboundConfig config = Hearthbound.config();
		if (isHost(player)) {
			return config.obeyAll;
		}
		return config.obeyPlayers.contains(player.getUUID().toString());
	}

	public static void set(ServerPlayer player, boolean on) {
		set(player, on, true);
	}

	/** @param save write the config file too (tests switch it off so they leave nothing behind) */
	public static void set(ServerPlayer player, boolean on, boolean save) {
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
	}
}

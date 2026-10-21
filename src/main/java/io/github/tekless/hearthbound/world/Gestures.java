package io.github.tekless.hearthbound.world;

import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Crouch twice, quickly, while looking at a companion and they crouch back: the way players have
 * always said hello to each other. Watches each player's sneak key every server tick.
 */
public final class Gestures {
	/** Two crouches this close together (in ticks) count as a hello. */
	private static final int DOUBLE_TAP = 12;
	private static final double RANGE = 8;
	private static final Map<UUID, Sneak> SNEAKS = new HashMap<>();

	private static final class Sneak {
		boolean down;
		long lastPress = -100;
		long lastHello = -100;
	}

	private Gestures() {
	}

	public static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Sneak sneak = SNEAKS.computeIfAbsent(player.getUUID(), id -> new Sneak());
			boolean down = player.isShiftKeyDown();
			if (down && !sneak.down) {
				if (now - sneak.lastPress <= DOUBLE_TAP && now - sneak.lastHello > 30) {
					CompanionEntity companion = Conversations.lookedAt(player, RANGE);
					if (companion != null) {
						sneak.lastHello = now;
						companion.crouchBack(player);
					}
				}
				sneak.lastPress = now;
			}
			sneak.down = down;
		}
	}

	public static void reset() {
		SNEAKS.clear();
	}
}

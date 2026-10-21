package io.github.tekless.hearthbound.world;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.core.intent.QuestionKind;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * Companions noticing what happens to their player: an advancement, a level milestone, diamonds,
 * going to bed and waking up, dying and coming back. The closest companion in the party speaks,
 * and the bigger moments go into their memory so they can bring them up later.
 */
public final class Moments {
	private static final double RANGE = 24;
	private static final int[] LEVEL_MILESTONES = {5, 10, 15, 20, 25, 30, 40, 50, 75, 100};
	private static final Map<UUID, Watch> WATCHES = new HashMap<>();
	/** Advancement reactions wait a moment, so they come after the game's own announcement. */
	private static final List<Pending> PENDING = new ArrayList<>();

	private record Pending(UUID player, String key, Map<String, String> vars, String event, long dueTick) {
	}

	private static final class Watch {
		int level = -1;
		int diamonds = -1;
		long lastReaction = -1000;
		long lastDiamonds = -100000;
		long respawnedAt = -1;
	}

	private Moments() {
	}

	/** Once a second for each online player. */
	public static void tick(ServerPlayer player) {
		long now = player.level().getGameTime();
		Watch watch = watch(player);
		PENDING.removeIf(pending -> {
			if (now - pending.dueTick() > 600) {
				return true; // that player logged off before we got to it
			}
			if (!pending.player().equals(player.getUUID()) || now < pending.dueTick()) {
				return false;
			}
			CompanionEntity speaker = react(player, watch, pending.key(), pending.vars(), RANGE, false);
			if (speaker != null) {
				speaker.hop();
				if (pending.event() != null) {
					speaker.memory().addEvent(pending.event());
				}
			}
			return true;
		});

		int level = player.experienceLevel;
		if (watch.level >= 0 && level > watch.level && crossesMilestone(watch.level, level)) {
			CompanionEntity speaker = react(player, watch, "react.level", Map.of("level", Integer.toString(level)), RANGE, false);
			if (speaker != null) {
				speaker.hop();
			}
		}
		watch.level = level;

		int diamonds = player.getInventory().countItem(Items.DIAMOND);
		if (watch.diamonds >= 0 && diamonds > watch.diamonds && now - watch.lastDiamonds > 6000) {
			watch.lastDiamonds = now;
			CompanionEntity speaker = react(player, watch, "react.diamonds", Map.of(), RANGE, false);
			if (speaker != null) {
				speaker.hop();
			}
		}
		watch.diamonds = diamonds;

		if (watch.respawnedAt >= 0) {
			if (now - watch.respawnedAt > 6000) {
				watch.respawnedAt = -1;
			} else {
				CompanionEntity speaker = react(player, watch, "react.respawn", Map.of(), 12, true);
				if (speaker != null) {
					watch.respawnedAt = -1;
					speaker.wave();
					speaker.memory().addEvent("I thought I'd lost {player} for good");
				}
			}
		}
	}

	/** Called (from a mixin) the moment a player completes an advancement. */
	public static void advancementEarned(ServerPlayer player, AdvancementHolder holder) {
		holder.value().display().ifPresent(display -> {
			if (!display.announceToChat()) {
				return;
			}
			String title = display.title().getString();
			boolean big = display.type() == AdvancementType.CHALLENGE || display.type() == AdvancementType.GOAL;
			PENDING.add(new Pending(player.getUUID(), display.type() == AdvancementType.CHALLENGE ? "react.challenge" : "react.advancement",
					Map.of("advancement", "[" + title + "]"), big ? "I watched {player} earn \"" + title + "\"" : null, player.level().getGameTime() + 30));
		});
	}

	public static void playerDied(ServerPlayer player) {
		react(player, watch(player), "react.player_died", Map.of(), 48, true);
	}

	public static void respawned(ServerPlayer player) {
		watch(player).respawnedAt = player.level().getGameTime();
	}

	public static void wentToBed(ServerPlayer player) {
		react(player, watch(player), "react.sleep", Map.of(), 16, false);
	}

	/** Woken up: in the morning that's a fresh start worth a "sleep well?" */
	public static void wokeUp(ServerPlayer player) {
		if (player.level().isDarkOutside()) {
			return;
		}
		CompanionEntity speaker = closest(player, 16);
		Watch watch = watch(player);
		if (speaker != null && Hearthbound.config().ambientChatter > 0 && player.level().getGameTime() - watch.lastReaction > 100) {
			watch.lastReaction = player.level().getGameTime();
			speaker.ask(player, QuestionKind.HOW, "react.wake", Map.of());
		}
	}

	public static void reset() {
		WATCHES.clear();
		PENDING.clear();
	}

	private static Watch watch(ServerPlayer player) {
		return WATCHES.computeIfAbsent(player.getUUID(), id -> new Watch());
	}

	/**
	 * The closest companion in the party says something about the moment. Reactions are spaced out
	 * by a few seconds unless {@code urgent}, and turned off with the rest of the chatter.
	 */
	private static CompanionEntity react(ServerPlayer player, Watch watch, String key, Map<String, String> vars, double range, boolean urgent) {
		long now = player.level().getGameTime();
		if (!urgent && (now - watch.lastReaction < 100 || Hearthbound.config().ambientChatter <= 0)) {
			return null;
		}
		CompanionEntity speaker = closest(player, range);
		if (speaker == null) {
			return null;
		}
		watch.lastReaction = now;
		speaker.noteSpeaker(player);
		speaker.say(key, vars);
		return speaker;
	}

	private static CompanionEntity closest(ServerPlayer player, double range) {
		return Conversations.party(player).stream()
				.filter(c -> !c.isDowned() && c.distanceTo(player) <= range)
				.min(Comparator.comparingDouble(c -> c.distanceToSqr(player)))
				.orElse(null);
	}

	static boolean crossesMilestone(int from, int to) {
		for (int milestone : LEVEL_MILESTONES) {
			if (from < milestone && to >= milestone) {
				return true;
			}
		}
		return false;
	}
}

package io.github.tekless.hearthbound.world;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Short chats between companions: two to four lines, taking turns, each in their own voice. They
 * start when several companions travel together and the player isn't talking, or when two hub
 * residents meet while wandering. They are only ever spoken lines; nothing here is read as an order.
 */
public final class Exchanges {
	public enum Topic {
		WEATHER, ROAD, FOOD, TEASING,
		/** Daily life at home: a meal together, a story by the fire, the full-moon festival. */
		MEAL, STORY, FESTIVAL,
		/** Two who get on well, two who don't, and the players they know. */
		FRIENDS, RIVALS, PEOPLE;

		String key(String step) {
			return "exchange." + name().toLowerCase(java.util.Locale.ROOT) + "." + step;
		}
	}

	/** One exchange in progress; cancelled as soon as the player starts talking. */
	private static final class Chat {
		final ServerPlayer witness;
		boolean cancelled;

		Chat(ServerPlayer witness) {
			this.witness = witness;
		}
	}

	private record Step(Chat chat, CompanionEntity speaker, String key, Map<String, String> vars, long due) {
	}

	private static final List<Step> QUEUE = new ArrayList<>();
	private static final Map<UUID, Long> LAST_FOR_PLAYER = new HashMap<>();
	private static final Map<String, Long> LAST_FOR_PAIR = new HashMap<>();
	/** Minimum gap between exchanges for one player, at normal chattiness. */
	private static final long PLAYER_GAP = 4800;
	private static final long PAIR_GAP = 6000;

	private Exchanges() {
	}

	/** Speaks any lines that have come due. Safe to call from several places each second. */
	public static void tick(long now) {
		if (QUEUE.isEmpty()) {
			return;
		}
		List<Step> due = new ArrayList<>();
		for (Step step : QUEUE) {
			if (now >= step.due()) {
				due.add(step);
			}
		}
		QUEUE.removeAll(due);
		for (Step step : due) {
			if (step.chat().cancelled) {
				continue;
			}
			ServerPlayer witness = step.chat().witness;
			if (witness != null && Conversations.isChatting(witness)) {
				step.chat().cancelled = true; // the player started talking: let it drop
				continue;
			}
			if (step.speaker().isAlive() && !step.speaker().isDowned() && step.speaker().getTarget() == null) {
				step.speaker().say(step.key(), step.vars());
			}
		}
	}

	/** Maybe starts a chat between two companions travelling with the player. Returns true if one began. */
	public static boolean maybeStartFor(ServerPlayer player, List<CompanionEntity> party, double chatter) {
		long now = player.level().getGameTime();
		if (chatter <= 0 || party.size() < 2 || !QUEUE.isEmpty() || Conversations.isChatting(player) || player.isSleeping()
				|| now - LAST_FOR_PLAYER.getOrDefault(player.getUUID(), -100000L) < PLAYER_GAP / Math.max(0.25, chatter)
				|| player.getRandom().nextInt(40) != 0) {
			return false;
		}
		List<CompanionEntity> ready = party.stream().filter(Exchanges::free).toList();
		if (ready.size() < 2) {
			return false;
		}
		CompanionEntity a = ready.get(player.getRandom().nextInt(ready.size()));
		List<CompanionEntity> others = ready.stream().filter(c -> c != a && c.distanceTo(a) < 12).toList();
		if (others.isEmpty()) {
			return false;
		}
		CompanionEntity b = others.get(player.getRandom().nextInt(others.size()));
		LAST_FOR_PLAYER.put(player.getUUID(), now);
		start(player, a, b, pickTopic(a, player.getRandom().nextInt(100)));
		return true;
	}

	/** Hub residents who meet while wandering sometimes stop to chat, if someone is around to hear. */
	public static void hubTick(MinecraftServer server) {
		if (Hearthbound.config().ambientChatter <= 0) {
			return;
		}
		List<CompanionEntity> residents = CompanionTracker.all().stream()
				.filter(c -> !c.isRecruited() && free(c)).toList();
		for (int i = 0; i < residents.size(); i++) {
			for (int j = i + 1; j < residents.size(); j++) {
				CompanionEntity a = residents.get(i);
				CompanionEntity b = residents.get(j);
				if (a.level() != b.level() || a.distanceTo(b) > 5 || a.getRandom().nextInt(25) != 0) {
					continue;
				}
				ServerPlayer witness = (ServerPlayer) a.level().getNearestPlayer(a, 20);
				long now = a.level().getGameTime();
				String pair = a.getUUID().compareTo(b.getUUID()) < 0 ? a.getUUID() + "|" + b.getUUID() : b.getUUID() + "|" + a.getUUID();
				if (witness == null || now - LAST_FOR_PAIR.getOrDefault(pair, -100000L) < PAIR_GAP / Math.max(0.25, Hearthbound.config().ambientChatter)) {
					continue;
				}
				LAST_FOR_PAIR.put(pair, now);
				start(witness, a, b, pickNeighbourTopic(server, a, b, a.getRandom().nextInt(100)));
			}
		}
	}

	private static boolean free(CompanionEntity c) {
		return c.isAlive() && !c.isDowned() && c.getTarget() == null && c.order().kind().isMode();
	}

	/** Neighbours talk about each other (friends warmly, rivals less so), the players they know, or the usual. */
	static Topic pickNeighbourTopic(MinecraftServer server, CompanionEntity a, CompanionEntity b, int roll) {
		int bond = io.github.tekless.hearthbound.life.HomeAreas.get(server).bond(a.getUUID(), b.getUUID());
		if (bond >= io.github.tekless.hearthbound.core.life.Bonds.FRIENDS && roll < 35) {
			return Topic.FRIENDS;
		}
		if (bond <= io.github.tekless.hearthbound.core.life.Bonds.RIVALS && roll < 35) {
			return Topic.RIVALS;
		}
		if (roll >= 80 && knownPlayer(server, a) != null) {
			return Topic.PEOPLE;
		}
		return pickTopic(a, roll);
	}

	/** A player this companion knows by name (they told them what to call them, or they're online), or null. */
	static String knownPlayer(MinecraftServer server, CompanionEntity companion) {
		for (Map.Entry<String, io.github.tekless.hearthbound.core.memory.PlayerFacts> known : companion.memory().allFacts().entrySet()) {
			java.util.Optional<String> nick = known.getValue().nickname();
			if (nick.isPresent()) {
				return nick.get();
			}
			try {
				ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(known.getKey()));
				if (online != null) {
					return online.getName().getString();
				}
			} catch (IllegalArgumentException e) {
				// not a player id
			}
		}
		return null;
	}

	/** Weather talk when it is raining or dark, otherwise the road, food, or a little teasing. */
	static Topic pickTopic(CompanionEntity companion, int roll) {
		if ((companion.level().isRaining() || companion.level().isDarkOutside()) && roll < 60) {
			return Topic.WEATHER;
		}
		return switch (roll % 3) {
			case 0 -> Topic.ROAD;
			case 1 -> Topic.FOOD;
			default -> Topic.TEASING;
		};
	}

	/** Queues a chat: opening line, reply, a closing line (and sometimes a last word). */
	public static void start(ServerPlayer witness, CompanionEntity a, CompanionEntity b, Topic topic) {
		long now = a.level().getGameTime();
		String thing = switch (topic) {
			case WEATHER -> a.level().isRaining() ? "the rain" : "the dark";
			case ROAD -> Conversations.biomeName(a);
			case FOOD, MEAL -> "a proper meal";
			case TEASING, FRIENDS, RIVALS -> b.companionName().split(" ")[0];
			case STORY -> "the old days";
			case FESTIVAL -> "the full moon";
			case PEOPLE -> {
				String known = a.level() instanceof net.minecraft.server.level.ServerLevel level ? knownPlayer(level.getServer(), a) : null;
				yield known != null ? known : "our visitors";
			}
		};
		Map<String, String> toB = Map.of("other", a.companionName().split(" ")[0], "thing", thing);
		Map<String, String> toA = Map.of("other", b.companionName().split(" ")[0], "thing", thing);
		Chat chat = new Chat(witness);
		java.util.Random random = new java.util.Random(now ^ a.getUUID().getLeastSignificantBits());
		long at = now + 5;
		QUEUE.add(new Step(chat, a, topic.key("open"), toA, at));
		at += 50 + random.nextInt(50);
		QUEUE.add(new Step(chat, b, topic.key("reply"), toB, at));
		at += 50 + random.nextInt(50);
		if (random.nextInt(10) < 7) {
			QUEUE.add(new Step(chat, a, topic.key("close"), toA, at));
			at += 50 + random.nextInt(50);
		}
		if (random.nextInt(10) < 3) {
			QUEUE.add(new Step(chat, b, "banter.reply", toB, at));
		}
	}

	/** Forgets everything (the world closed). */
	public static void reset() {
		QUEUE.clear();
		LAST_FOR_PLAYER.clear();
		LAST_FOR_PAIR.clear();
	}
}

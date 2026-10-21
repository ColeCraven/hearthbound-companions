package io.github.tekless.hearthbound.dialogue;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.ai.AiDialogue;
import io.github.tekless.hearthbound.core.intent.Answers;
import io.github.tekless.hearthbound.core.intent.CommandParser;
import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.intent.NameMatch;
import io.github.tekless.hearthbound.core.intent.ParseResult;
import io.github.tekless.hearthbound.core.intent.QuestionKind;
import io.github.tekless.hearthbound.core.intent.TalkTopic;
import io.github.tekless.hearthbound.core.memory.PlayerFacts;
import io.github.tekless.hearthbound.core.persona.Role;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.order.AttackOrder;
import io.github.tekless.hearthbound.entity.order.Blueprint;
import io.github.tekless.hearthbound.entity.order.BuildOrder;
import io.github.tekless.hearthbound.entity.order.ComeOrder;
import io.github.tekless.hearthbound.entity.order.CompanionOrder;
import io.github.tekless.hearthbound.entity.order.FollowOrder;
import io.github.tekless.hearthbound.entity.order.GatherOrder;
import io.github.tekless.hearthbound.entity.order.GiveOrder;
import io.github.tekless.hearthbound.entity.order.MineOrder;
import io.github.tekless.hearthbound.entity.order.WaitOrder;
import io.github.tekless.hearthbound.world.CompanionRoster;
import io.github.tekless.hearthbound.world.CompanionTracker;
import io.github.tekless.hearthbound.world.GatherCatalog;
import io.github.tekless.hearthbound.world.WorldRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Entry point for everything the player says to companions. The flow is always:
 * parse -> validate against the real game -> act -> <i>then</i> reply with the real outcome.
 */
public final class Conversations {
	public static final int MAX_GATHER = 256;
	/** Longest tunnel or staircase in one order. */
	public static final int MAX_DIG = 64;
	private static final double ADDRESS_RANGE = 64;
	private static final Pattern AT_ADDRESS = Pattern.compile("^@(\\S+)\\s*(.*)$", Pattern.DOTALL);
	private static final Pattern NAME_ADDRESS = Pattern.compile("^([A-Za-z]+)\\s*[,:]\\s*(.+)$", Pattern.DOTALL);
	/** "@Honey" somewhere in the middle of a message, e.g. "hey @honey, how are you?". */
	private static final Pattern AT_MENTION = Pattern.compile("(?:^|\\s)@([A-Za-z]+)[,:;!?.]*");
	private static final CommandParser PARSER = new CommandParser(Conversations::isMobName);
	/** Three minutes of silence ends a conversation. */
	private static final long CHAT_TIMEOUT_TICKS = 3600;
	/** Walk (well) out of earshot and the conversation is over. */
	private static final double CHAT_RANGE = 64;
	/** With nobody else around, chat goes to the companion you're looking at, or your nearest party member. */
	private static final double LOOK_RANGE = 24;
	private static final double NEAREST_RANGE = 64;
	/** Strangers only answer plain chat when you're right next to them. */
	private static final double STRANGER_RANGE = 8;
	/** Companions further away than this walk over (or catch up) before starting a job at the player. */
	private static final double TRAVEL_FIRST = 24;
	/** "Other players nearby" for deciding whether plain chat might be meant for them instead. */
	private static final double COMPANY_RANGE = 24;
	private static final String BUILD_THINGS = "a shelter, a wall, a bridge or a camp";
	private static final Map<UUID, Chat> CHATS = new HashMap<>();
	/** The short thread of each conversation, so "why?" and "tell me more" have something to lean on. */
	private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
	/** A task a stranger was asked to do, waiting for their "yes, I'll join you first". */
	private static final Map<UUID, Intent> PENDING_JOIN = new HashMap<>();
	private static final java.util.Set<TalkTopic> REPLAYABLE = java.util.EnumSet.of(TalkTopic.HOW, TalkTopic.LIKES, TalkTopic.DISLIKES,
			TalkTopic.WHO, TalkTopic.STORY, TalkTopic.PLACE, TalkTopic.PLAN, TalkTopic.FOOD, TalkTopic.JOKE, TalkTopic.MEMORY, TalkTopic.WHERE);
	private static final java.util.Set<String> BIOME_WORDS = java.util.Set.of("forest", "desert", "jungle", "swamp", "ocean", "mountains",
			"mountain", "plains", "caves", "cave", "village", "nether", "taiga", "savanna", "badlands", "tundra");
	private static final java.util.Set<String> EXTRA_ITEM_WORDS = java.util.Set.of("iron", "gold", "copper", "wood", "ores");
	private static final java.util.Set<String> VAGUE_WORDS = java.util.Set.of("this", "that", "it", "something", "anything", "a", "an", "the",
			"thing", "things", "stuff", "one", "whatever", "here", "there", "nice", "cool", "small", "simple", "useful", "quick", "little",
			"for", "me", "us", "you", "like", "can", "want", "over", "some", "good", "else");
	private static final java.util.Set<String> HUNT_WORDS = java.util.Set.of("animals", "animal", "game", "food", "meat", "something to eat");

	/** What the conversation with one companion is about. */
	private static final class Context {
		TalkTopic topic;
		long tick;
		String subject;
		String lastItem;
	}

	private enum Group {
		/** The player's party: everyone with them, or close by. */
		PARTY,
		/** Every companion the player owns, wherever they are. */
		EVERYONE
	}

	private enum Reach {
		HERE, OTHER_DIMENSION, UNLOADED, NONE
	}
	private static final Map<UUID, Long> HINTED = new HashMap<>();
	/** Players who've had the "now try..." tip after recruiting someone this session. */
	private static final java.util.Set<UUID> RECRUIT_TIPS = new java.util.HashSet<>();

	/** Who a player is talking with, so they can keep chatting without @names. */
	private record Chat(CompanionEntity companion, long lastTick) {
	}

	private Conversations() {
	}

	/**
	 * Handles a chat line if it is meant for companions: addressed by name ("@Hazel follow me",
	 * "Hazel, wait here", "@all come here", "hey @hazel"), or plain chat while you're already
	 * talking to one. Returns true if a companion took it.
	 */
	public static boolean onChat(ServerPlayer player, String raw) {
		String text = raw.trim();
		String name;
		String body;
		Matcher at = AT_ADDRESS.matcher(text);
		Matcher named = NAME_ADDRESS.matcher(text);
		boolean fuzzy = true;
		if (at.matches()) {
			name = at.group(1);
			body = at.group(2);
		} else if (named.matches() && !nearbyNamed(player, named.group(1), false).isEmpty()) {
			fuzzy = false; // "Well, I think..." must not reach someone called Nell
			name = named.group(1);
			body = named.group(2);
		} else {
			// A mention further into the message only counts if a companion by that name is close by,
			// so "@Steve look at this" between players is left alone.
			Matcher mention = AT_MENTION.matcher(text);
			if (!mention.find() || (isGroupName(mention.group(1)) ? CompanionTracker.ownedBy(player).isEmpty() : reach(player, mention.group(1), true) == Reach.NONE)) {
				if (text.contains("@")) {
					return false; // talking to (or about) someone else
				}
				CompanionEntity partner = conversationPartner(player);
				if (partner == null) {
					return false;
				}
				handleMessage(player, partner, text);
				return true;
			}
			name = mention.group(1);
			body = (text.substring(0, mention.start()) + " " + text.substring(mention.end())).replaceAll("\\s+", " ").trim()
					.replaceAll("^[,:;]+\\s*|\\s*[,:;]+$", "");
			if (body.isEmpty()) {
				body = "hello";
			}
		}
		Group group = groupOf(name);
		if (group == Group.EVERYONE) {
			return sayToEveryone(player, body);
		}
		List<CompanionEntity> targets = group == Group.PARTY ? party(player) : nearbyNamed(player, name, fuzzy);
		if (targets.isEmpty()) {
			String key = group != null ? "message.hearthbound.no_party" : switch (reach(player, name, fuzzy)) {
				case OTHER_DIMENSION -> "message.hearthbound.other_dimension";
				case UNLOADED -> "message.hearthbound.not_loaded";
				default -> "message.hearthbound.no_such_companion";
			};
			player.sendSystemMessage(Texts.tr(key, name).withStyle(ChatFormatting.GRAY));
			return true;
		}
		for (CompanionEntity companion : targets) {
			handleMessage(player, companion, body, group == null);
		}
		if (group == Group.PARTY && targets.size() > 1) {
			report(player, targets, List.of());
		}
		return true;
	}

	/** {@code @everyone}: every companion the player has, wherever they are, with a note on who couldn't hear. */
	public static boolean sayToEveryone(ServerPlayer player, String body) {
		List<CompanionEntity> heard = new ArrayList<>();
		List<String> missed = new ArrayList<>();
		java.util.Set<UUID> loaded = new java.util.HashSet<>();
		for (CompanionEntity companion : CompanionTracker.ownedBy(player)) {
			loaded.add(companion.getUUID());
			if (companion.level() == player.level()) {
				heard.add(companion);
			} else {
				missed.add(shortName(companion) + " (another dimension)");
			}
		}
		for (Map.Entry<String, CompanionRoster.Entry> entry : CompanionRoster.get(player.level().getServer()).entries().entrySet()) {
			if (player.getUUID().toString().equals(entry.getValue().owner) && !loaded.stream().anyMatch(id -> id.toString().equals(entry.getKey()))) {
				missed.add(entry.getValue().name.split(" ")[0] + " (not loaded, too far away)");
			}
		}
		if (heard.isEmpty() && missed.isEmpty()) {
			player.sendSystemMessage(Texts.tr("message.hearthbound.no_party").withStyle(ChatFormatting.GRAY));
			return true;
		}
		for (CompanionEntity companion : heard) {
			handleMessage(player, companion, body, false);
		}
		report(player, heard, missed);
		return true;
	}

	/** One short grey line saying who heard a group message and who couldn't. */
	private static void report(ServerPlayer player, List<CompanionEntity> heard, List<String> missed) {
		if (!heard.isEmpty()) {
			player.sendSystemMessage(Texts.tr("message.hearthbound.heard_by", String.join(", ", heard.stream().map(Conversations::shortName).toList()))
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		if (!missed.isEmpty()) {
			player.sendSystemMessage(Texts.tr("message.hearthbound.unreachable", String.join(", ", missed)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	private static String shortName(CompanionEntity companion) {
		return companion.companionName().split(" ")[0];
	}

	private static Group groupOf(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		if (n.equals("everyone")) {
			return Group.EVERYONE;
		}
		return n.equals("all") || n.equals("party") || n.equals("team") || n.equals("companions") ? Group.PARTY : null;
	}

	/** Whether a named companion is here, loaded in another dimension, known but not loaded, or unknown. */
	private static Reach reach(ServerPlayer player, String name, boolean fuzzy) {
		if (!nearbyNamed(player, name, fuzzy).isEmpty()) {
			return Reach.HERE;
		}
		String wanted = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
		for (CompanionEntity companion : CompanionTracker.ownedBy(player)) {
			if (firstName(companion).equals(wanted)) {
				return Reach.OTHER_DIMENSION;
			}
		}
		for (CompanionRoster.Entry entry : CompanionRoster.get(player.level().getServer()).entries().values()) {
			if (player.getUUID().toString().equals(entry.owner) && entry.name.toLowerCase(Locale.ROOT).split(" ")[0].replaceAll("[^a-z]", "").equals(wanted)) {
				return Reach.UNLOADED;
			}
		}
		return Reach.NONE;
	}

	/**
	 * Who plain chat from this player goes to, if anyone: the companion they're in a conversation
	 * with (close by, talked to in the last minute). When other players are around, the player also
	 * has to be facing them, so chat with friends isn't taken over. With nobody else around, plain
	 * chat starts a conversation with the companion they're looking at, or the nearest one.
	 */
	private static CompanionEntity conversationPartner(ServerPlayer player) {
		long now = player.level().getGameTime();
		boolean alone = aloneWithCompanions(player);
		Chat chat = CHATS.get(player.getUUID());
		if (chat != null) {
			CompanionEntity companion = chat.companion();
			boolean still = companion.isAlive() && !companion.isRemoved() && companion.level() == player.level()
					&& companion.distanceTo(player) <= CHAT_RANGE && now - chat.lastTick() <= CHAT_TIMEOUT_TICKS;
			if (!still) {
				CHATS.remove(player.getUUID());
			} else if (alone || isFacing(player, companion)) {
				return companion;
			}
		}
		if (!alone) {
			return null;
		}
		CompanionEntity looked = lookedAt(player, LOOK_RANGE);
		if (looked != null) {
			return looked;
		}
		// Your own party first (however far they've wandered); a stranger only if you're right beside them.
		CompanionEntity mine = CompanionTracker.near(player, NEAREST_RANGE).stream().filter(c -> c.isOwnedBy(player))
				.min(Comparator.comparingDouble(c -> c.distanceToSqr(player))).orElse(null);
		if (mine != null) {
			return mine;
		}
		return CompanionTracker.near(player, STRANGER_RANGE).stream()
				.min(Comparator.comparingDouble(c -> c.distanceToSqr(player))).orElse(null);
	}

	/** Starts (or refreshes) a conversation, e.g. after a right-click hello. */
	public static void beginChat(ServerPlayer player, CompanionEntity companion) {
		Chat previous = CHATS.put(player.getUUID(), new Chat(companion, player.level().getGameTime()));
		if (previous == null || previous.companion() != companion) {
			long now = player.level().getGameTime();
			Long last = HINTED.get(player.getUUID());
			if (last == null || now - last > 12000) {
				HINTED.put(player.getUUID(), now);
				player.sendSystemMessage(Texts.tr("message.hearthbound.chatting", companion.companionName().split(" ")[0])
						.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		}
	}

	public static void endChat(ServerPlayer player) {
		CHATS.remove(player.getUUID());
	}

	/** Forgets every conversation (the world closed). */
	public static void reset() {
		CHATS.clear();
		CONTEXTS.clear();
		PENDING_JOIN.clear();
		HINTED.clear();
		RECRUIT_TIPS.clear();
	}

	/** True if the player has been talking with a companion in the last half minute. */
	public static boolean isChatting(ServerPlayer player) {
		Chat chat = CHATS.get(player.getUUID());
		return chat != null && player.level().getGameTime() - chat.lastTick() < 600;
	}

	/** No other players within earshot, so plain chat can only be meant for companions. */
	public static boolean aloneWithCompanions(ServerPlayer player) {
		for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
			if (other != player && !other.isSpectator() && other.level() == player.level() && other.distanceTo(player) < COMPANY_RANGE) {
				return false;
			}
		}
		return true;
	}

	private static boolean isFacing(ServerPlayer player, CompanionEntity companion) {
		Vec3 look = player.getViewVector(1.0F).multiply(1, 0, 1).normalize();
		Vec3 toThem = companion.position().subtract(player.position()).multiply(1, 0, 1).normalize();
		return look.dot(toThem) > 0.7; // within about 45 degrees
	}

	/** The companion under the player's crosshair, within {@code range} blocks, or null. */
	public static CompanionEntity lookedAt(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(range));
		AABB area = player.getBoundingBox().inflate(range);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, area, e -> e instanceof CompanionEntity && e.isAlive(), range * range);
		return hit != null && hit.getEntity() instanceof CompanionEntity companion ? companion : null;
	}

	private static boolean isGroupName(String name) {
		return groupOf(name) != null;
	}

	/** Companions within earshot with that name (case-insensitive, first name, exact only). */
	public static List<CompanionEntity> nearbyNamed(ServerPlayer player, String name) {
		return nearbyNamed(player, name, false);
	}

	/**
	 * Companions within earshot with that name. With {@code fuzzy}, a short form or a typo also
	 * counts ("@ros", "@rsoie" for Honey) as long as it fits only one name and isn't a player's name.
	 */
	public static List<CompanionEntity> nearbyNamed(ServerPlayer player, String name, boolean fuzzy) {
		String wanted = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
		if (wanted.isEmpty()) {
			return List.of();
		}
		List<CompanionEntity> exact = new ArrayList<>();
		List<CompanionEntity> close = new ArrayList<>();
		for (CompanionEntity companion : nearby(player)) {
			String first = firstName(companion);
			if (first.equals(wanted)) {
				exact.add(companion);
			} else if (fuzzy && NameMatch.closeEnough(wanted, first)) {
				close.add(companion);
			}
		}
		List<CompanionEntity> found = exact;
		if (found.isEmpty() && !close.isEmpty() && close.stream().map(Conversations::firstName).distinct().count() == 1
				&& player.level().getServer().getPlayerList().getPlayers().stream().noneMatch(p -> p.getName().getString().equalsIgnoreCase(wanted))) {
			found = close;
		}
		// If several share a name, prefer the player's own companions, then the closest.
		found.sort(Comparator.comparing((CompanionEntity c) -> !c.isOwnedBy(player)).thenComparingDouble(c -> c.distanceToSqr(player)));
		return found.isEmpty() ? found : List.of(found.get(0));
	}

	private static String firstName(CompanionEntity companion) {
		return companion.companionName().toLowerCase(Locale.ROOT).split(" ")[0].replaceAll("[^a-z]", "");
	}

	/** The player's recruited companions in the same dimension who are following or close by. */
	public static List<CompanionEntity> party(ServerPlayer player) {
		List<CompanionEntity> party = new ArrayList<>();
		for (CompanionEntity companion : CompanionTracker.ownedBy(player)) {
			if (companion.level() == player.level()
					&& (companion.baseMode() == CompanionOrder.Kind.FOLLOW || companion.distanceTo(player) <= ADDRESS_RANGE)) {
				party.add(companion);
			}
		}
		return party;
	}

	/** Every loaded companion in the player's dimension, however far away. */
	private static List<CompanionEntity> nearby(ServerPlayer player) {
		return CompanionTracker.near(player, 3.0E7);
	}

	/** Counts every companion travelling with the player, including ones in unloaded chunks. */
	public static int partySize(ServerPlayer player) {
		int loaded = CompanionTracker.ownedBy(player).size();
		return Math.max(loaded, CompanionRoster.get(player.level().getServer()).ownedBy(player.getUUID()));
	}

	/** One message to one companion (and the start of a conversation with them). */
	public static void handleMessage(ServerPlayer player, CompanionEntity companion, String text) {
		handleMessage(player, companion, text, true);
	}

	private static void handleMessage(ServerPlayer player, CompanionEntity companion, String text, boolean converse) {
		if (converse) {
			beginChat(player, companion);
		}
		CompanionEntity.AskedQuestion asked = companion.takeQuestion(player);
		companion.noteSpeaker(player);
		companion.memory().talkingWith(player.getUUID().toString());
		companion.memory().addPlayerLine(text);
		companion.getLookControl().setLookAt(player);
		ParseResult result = PARSER.parse(text, List.of(companion.companionName()));
		if ((result instanceof ParseResult.Talk talk && talk.topic() == TalkTopic.BYE)
				|| (result instanceof ParseResult.Command command && command.intent().type() == IntentType.DISMISS)) {
			endChat(player);
		}
		if (asked != null && answer(player, companion, asked, text, result)) {
			return;
		}
		switch (result) {
			case ParseResult.Command command -> orderFromChat(player, companion, command.intent());
			case ParseResult.Share share -> share(player, companion, share, text);
			case ParseResult.Talk talk -> converse(player, companion, text, talk.topic());
			case ParseResult.Unclear unclear -> {
				if (unclear.reason() == ParseResult.UnclearReason.GATHER_WHAT) {
					companion.ask(player, QuestionKind.GATHER_WHAT, "unclear.gather_what", Map.of());
				} else {
					companion.say("unclear.generic", Map.of());
				}
			}
			case ParseResult.Unsupported unsupported -> companion.say("fail.unsupported", Map.of("action", unsupported.action()));
			case ParseResult.Unrecognised unrecognised -> converse(player, companion, text, TalkTopic.GENERIC);
		}
	}

	private static Context context(CompanionEntity companion) {
		return CONTEXTS.computeIfAbsent(companion.getUUID(), id -> new Context());
	}

	/** Small talk: the AI model if one is set up, else a line about what was actually mentioned, else the topic's lines. */
	private static void converse(ServerPlayer player, CompanionEntity companion, String text, TalkTopic topic) {
		if (AiDialogue.tryConverse(player, companion, text, null)) {
			return;
		}
		if (topic == TalkTopic.GENERIC || topic == TalkTopic.QUESTION) {
			String[] mention = findMention(text);
			if (mention != null) {
				context(companion).subject = mention[1];
				companion.say(mention[0], Map.of("thing", mention[1]));
				return;
			}
		}
		talk(player, companion, topic);
	}

	/** A creature, item or place the player brought up, so the reply can be about it: {key, thing} or null. */
	private static String[] findMention(String text) {
		String[] words = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").trim().split("\\s+");
		for (int i = 0; i < words.length; i++) {
			if (i + 1 < words.length && isMobName(words[i] + " " + words[i + 1])) {
				return new String[] {"talk.mention.mob", words[i] + " " + words[i + 1]};
			}
			if (words[i].length() >= 3 && isMobName(words[i])) {
				return new String[] {"talk.mention.mob", words[i]};
			}
		}
		for (String word : words) {
			if (word.length() < 4) {
				continue;
			}
			String single = CommandParserAccess.singular(word);
			boolean item = EXTRA_ITEM_WORDS.contains(word) || BIOME_WORDS.contains(word);
			if (!item) {
				Identifier key = Identifier.tryParse("minecraft:" + single);
				item = key != null && BuiltInRegistries.ITEM.containsKey(key);
			}
			if (item) {
				return new String[] {"talk.mention.thing", word};
			}
		}
		return null;
	}

	private static boolean isTask(IntentType type) {
		return type == IntentType.GATHER || type == IntentType.BUILD || type == IntentType.MINE || type == IntentType.ATTACK;
	}

	private static boolean isDemonstrative(String phrase) {
		return phrase != null && java.util.Set.of("that", "this", "it", "those", "these", "them").contains(phrase.toLowerCase(Locale.ROOT).trim());
	}

	/** "build this", "build something": no real subject, so we ask rather than guess. */
	private static boolean isVague(String phrase) {
		if (phrase == null || phrase.isBlank()) {
			return true;
		}
		for (String word : phrase.toLowerCase(Locale.ROOT).split("\\s+")) {
			if (!VAGUE_WORDS.contains(word)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * An order typed in chat. "This" and "that" are worked out from what the player is looking at (or the
	 * last thing they were working on); when that doesn't settle it, the companion asks one short question,
	 * and the next message is taken as the answer.
	 */
	private static void orderFromChat(ServerPlayer player, CompanionEntity companion, Intent intent) {
		if (!companion.isRecruited() && isTask(intent.type()) && !companion.isDowned()) {
			PENDING_JOIN.put(companion.getUUID(), intent);
			companion.ask(player, QuestionKind.JOIN_FIRST, "ask.join_first", Map.of());
			return;
		}
		Context ctx = context(companion);
		Intent resolved = intent;
		if (intent.type() == IntentType.GATHER && isDemonstrative(intent.item())) {
			String pointed = lookedAtBlockPhrase(player);
			if (pointed == null) {
				pointed = ctx.lastItem;
			}
			if (pointed == null) {
				companion.ask(player, QuestionKind.GATHER_WHAT, "unclear.gather_what", Map.of());
				return;
			}
			resolved = intent.withItem(pointed);
		} else if (intent.type() == IntentType.BUILD && Blueprint.Kind.byPhrase(intent.item()).isEmpty() && isVague(intent.item())) {
			companion.ask(player, QuestionKind.BUILD_WHAT, "ask.build_what", Map.of("things", BUILD_THINGS));
			return;
		}
		Outcome outcome = apply(player, companion, resolved);
		if (outcome.accepted() && resolved.type() == IntentType.GATHER && resolved.item() != null) {
			ctx.lastItem = resolved.item();
		}
		if (outcome.lineKey() != null) {
			if ("fail.no_target".equals(outcome.lineKey())) {
				companion.ask(player, QuestionKind.TARGET_WHAT, outcome.lineKey(), outcome.vars());
			} else {
				companion.say(outcome.lineKey(), outcome.vars());
			}
		}
		if (outcome.accepted() && "ack.come".equals(outcome.lineKey()) == false) {
			roleRemark(companion, resolved);
		}
	}

	/** Now and then, a word on whether this is the sort of job they're good at (or not). */
	private static void roleRemark(CompanionEntity companion, Intent intent) {
		Role.Task task = switch (intent.type()) {
			case GATHER -> Role.Task.GATHER;
			case BUILD -> Role.Task.BUILD;
			case MINE -> Role.Task.DIG;
			case ATTACK -> Role.Task.COMBAT;
			default -> null;
		};
		if (task == null || companion.getRandom().nextInt(3) != 0) {
			return;
		}
		Role role = companion.role();
		if (role.isStrongAt(task)) {
			companion.say("role.strong", Map.of("task", task.verb()));
		} else if (role.isWeakAt(task)) {
			companion.say("role.weak", Map.of("task", task.verb()));
		}
	}

	/** The block under the crosshair as an item phrase ("oak log", "iron ore"), or null if there isn't one. */
	private static String lookedAtBlockPhrase(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(24));
		net.minecraft.world.phys.BlockHitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(eye, end,
				net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			return null;
		}
		net.minecraft.world.item.Item item = player.level().getBlockState(hit.getBlockPos()).getBlock().asItem();
		if (item == net.minecraft.world.item.Items.AIR) {
			return null;
		}
		String phrase = BuiltInRegistries.ITEM.getKey(item).getPath().replace('_', ' ');
		// Pointing at something that can't be collected (bedrock, a barrier) is the same as pointing at nothing.
		return GatherCatalog.resolve(phrase) instanceof GatherCatalog.Resolution.Gatherable ? phrase : null;
	}

	/** Reads the reply to a task question ("A shelter, a wall or a bridge?" / "Gather what?" / "Attack what?" / "Want me to join first?"). */
	private static boolean answerTask(ServerPlayer player, CompanionEntity companion, CompanionEntity.AskedQuestion asked, String text, ParseResult result) {
		switch (asked.kind()) {
			case BUILD_WHAT -> {
				if (Blueprint.Kind.byPhrase(text).isPresent()) {
					orderFromChat(player, companion, Intent.build(text));
					return true;
				}
			}
			case GATHER_WHAT -> {
				if (PARSER.parse("gather " + text) instanceof ParseResult.Command command && command.intent().type() == IntentType.GATHER
						&& command.intent().item() != null && !isDemonstrative(command.intent().item())) {
					orderFromChat(player, companion, command.intent());
					return true;
				}
			}
			case TARGET_WHAT -> {
				if (PARSER.parse("attack " + text) instanceof ParseResult.Command command && command.intent().type() == IntentType.ATTACK
						&& command.intent().target() != null) {
					orderFromChat(player, companion, command.intent());
					return true;
				}
			}
			case JOIN_FIRST -> {
				Intent pending = PENDING_JOIN.remove(companion.getUUID());
				TalkTopic topic = result instanceof ParseResult.Talk talk ? talk.topic() : null;
				if (pending != null && topic == TalkTopic.YES) {
					Outcome joined = recruit(player, companion);
					companion.say(joined.lineKey(), joined.vars());
					if (joined.accepted()) {
						orderFromChat(player, companion, pending);
					}
					return true;
				}
				if (pending != null && topic == TalkTopic.NO) {
					companion.say("ask.skip", Map.of());
					return true;
				}
			}
			default -> {
			}
		}
		return false;
	}

	/** Small talk that can't be the answer to a "what" question: "haha" is not someone's favourite thing. */
	private static final java.util.Set<TalkTopic> NOT_AN_ANSWER = java.util.EnumSet.of(TalkTopic.HELLO, TalkTopic.THANKS, TalkTopic.LAUGH,
			TalkTopic.YES, TalkTopic.BYE, TalkTopic.INSULT, TalkTopic.COMPLIMENT, TalkTopic.FONDNESS, TalkTopic.HOW, TalkTopic.WHO, TalkTopic.HELP,
			TalkTopic.BOND, TalkTopic.ABOUT_ME, TalkTopic.JOKE, TalkTopic.MEMORY, TalkTopic.SAD, TalkTopic.WHERE);

	/**
	 * Reads a reply to something the companion asked. Orders and statements still go the normal way;
	 * otherwise a name, a like or a dislike is remembered, and a mood gets a fitting reply.
	 *
	 * @return true if the reply was taken as an answer
	 */
	private static boolean answer(ServerPlayer player, CompanionEntity companion, CompanionEntity.AskedQuestion asked, String text, ParseResult result) {
		if (result instanceof ParseResult.Command || result instanceof ParseResult.Share) {
			return false;
		}
		if (answerTask(player, companion, asked, text, result)) {
			return true;
		}
		if (asked.kind() == QuestionKind.BUILD_WHAT || asked.kind() == QuestionKind.GATHER_WHAT || asked.kind() == QuestionKind.TARGET_WHAT
				|| asked.kind() == QuestionKind.JOIN_FIRST) {
			return false; // they changed the subject; treat it as a fresh message
		}
		TalkTopic topic = result instanceof ParseResult.Talk talk ? talk.topic() : null;
		boolean couldBeAThing = topic == null || !NOT_AN_ANSWER.contains(topic);
		boolean declined = Answers.isNonAnswer(text) || topic == TalkTopic.NO;
		PlayerFacts facts = companion.factsAbout(player);
		String key;
		Map<String, String> vars = Map.of();
		switch (asked.kind()) {
			case NICKNAME -> {
				Optional<String> name = Answers.nickname(text);
				if (declined) {
					// Their own name is fine by them, so don't ask again.
					facts.setNickname(player.getName().getString());
					key = "ask.nickname.skip";
				} else if (name.isPresent() && couldBeAThing) {
					facts.setNickname(name.get());
					key = "fact.nickname";
					vars = Map.of("nick", name.get());
				} else {
					return false;
				}
			}
			case LIKE, DISLIKE -> {
				Optional<String> thing = Answers.thing(text);
				if (declined) {
					key = "ask.skip";
				} else if (thing.isPresent() && couldBeAThing) {
					if (asked.kind() == QuestionKind.LIKE) {
						facts.like(thing.get());
						key = "fact.like";
					} else {
						facts.dislike(thing.get());
						key = "fact.dislike";
					}
					vars = Map.of("thing", thing.get());
				} else {
					return false;
				}
			}
			case HOW -> {
				Answers.Sentiment mood = Answers.sentiment(text);
				if (mood == Answers.Sentiment.NEUTRAL) {
					return false;
				}
				key = mood == Answers.Sentiment.GOOD ? "talk.answer.good" : "talk.answer.bad";
			}
			default -> {
				if (topic != null && topic != TalkTopic.QUESTION && topic != TalkTopic.GENERIC) {
					return false;
				}
				key = "talk.answer";
			}
		}
		if (!AiDialogue.tryConverse(player, companion, text, null)) {
			companion.say(key, vars);
		}
		return true;
	}

	/** Remembers what the player just told us about themselves, then says so. */
	private static void share(ServerPlayer player, CompanionEntity companion, ParseResult.Share share, String text) {
		PlayerFacts facts = companion.factsAbout(player);
		String key;
		Map<String, String> vars;
		switch (share.kind()) {
			case NICKNAME -> {
				facts.setNickname(share.value());
				key = "fact.nickname";
				vars = Map.of("nick", share.value());
			}
			case LIKE -> {
				facts.like(share.value());
				key = "fact.like";
				vars = Map.of("thing", share.value());
			}
			case DISLIKE -> {
				facts.dislike(share.value());
				key = "fact.dislike";
				vars = Map.of("thing", share.value());
			}
			default -> {
				facts.forgetNickname();
				key = "fact.forget";
				vars = Map.of("player", companion.callName(player));
			}
		}
		if (!AiDialogue.tryConverse(player, companion, text, null)) {
			companion.say(key, vars);
		}
	}

	/**
	 * Validates and carries out an intent, then replies with what actually happened. Used for
	 * parsed chat, buttons, commands and AI proposals alike.
	 *
	 * @return true if the intent was accepted and started
	 */
	public static boolean execute(ServerPlayer player, CompanionEntity companion, Intent intent) {
		Outcome outcome = apply(player, companion, intent);
		if (outcome.lineKey() != null) {
			companion.say(outcome.lineKey(), outcome.vars());
		}
		return outcome.accepted();
	}

	/** The validated result of an intent: which line to say and whether anything started. */
	public record Outcome(boolean accepted, String lineKey, Map<String, String> vars) {
		static Outcome yes(String key, Map<String, String> vars) {
			return new Outcome(true, key, vars);
		}

		static Outcome no(String key, Map<String, String> vars) {
			return new Outcome(false, key, vars);
		}
	}

	/** Validates and performs an intent without speaking (so AI replies can be checked first). */
	public static Outcome apply(ServerPlayer player, CompanionEntity companion, Intent intent) {
		IntentType type = intent.type();
		if (companion.isRecruited() && !companion.isOwnedBy(player)) {
			String owner = companion.ownerName() != null ? companion.ownerName() : "someone else";
			return Outcome.no("recruit.taken", Map.of("owner", owner));
		}
		if (!companion.isRecruited()) {
			return switch (type) {
				case RECRUIT, FOLLOW, COME -> recruit(player, companion);
				case DISMISS -> Outcome.no("dismiss.not_recruited", Map.of());
				case STATUS -> Outcome.yes("status.idle", Map.of());
				default -> Outcome.no("not_recruited", Map.of());
			};
		}
		if (companion.isDowned() && type != IntentType.STATUS && type != IntentType.DISMISS) {
			return Outcome.no("fail.downed", Map.of());
		}
		CompanionOrder.Kind current = companion.order().kind();
		if ((type == IntentType.GATHER || type == IntentType.BUILD || type == IntentType.MINE)
				&& companion.level() == player.level() && companion.distanceTo(player) > TRAVEL_FIRST) {
			// Too far away to work at the player's spot: come over first, then start (ComeOrder teleports if it must).
			companion.setOrder(new ComeOrder(arrived -> {
				ServerPlayer owner = arrived.ownerPlayer();
				if (owner != null) {
					Outcome outcome = apply(owner, arrived, intent);
					if (outcome.lineKey() != null) {
						arrived.say(outcome.lineKey(), outcome.vars());
					}
				}
			}));
			return Outcome.yes("ack.come", Map.of());
		}
		return switch (type) {
			case FOLLOW -> {
				if (current == CompanionOrder.Kind.FOLLOW) {
					yield Outcome.yes("ack.follow.already", Map.of());
				}
				companion.setOrder(new FollowOrder());
				yield Outcome.yes("ack.follow", Map.of());
			}
			case WAIT -> {
				if (current == CompanionOrder.Kind.WAIT) {
					yield Outcome.yes("ack.wait.already", Map.of());
				}
				companion.setOrder(new WaitOrder(companion.blockPosition()));
				yield Outcome.yes("ack.wait", Map.of());
			}
			case COME -> {
				companion.setOrder(new ComeOrder());
				yield Outcome.yes("ack.come", Map.of());
			}
			case CANCEL -> {
				Optional<String> cancelled = companion.cancelTask();
				if (cancelled.isPresent()) {
					yield Outcome.yes("ack.cancel", Map.of("task", cancelled.get()));
				}
				if (current == CompanionOrder.Kind.FOLLOW) {
					companion.setOrder(new WaitOrder(companion.blockPosition()));
					yield Outcome.yes("ack.wait", Map.of());
				}
				yield Outcome.no("ack.cancel.nothing", Map.of());
			}
			case GATHER -> gather(companion, intent);
			case BUILD -> build(player, companion, intent);
			case MINE -> mine(player, companion, intent);
			case GIVE -> give(player, companion, intent);
			case ATTACK -> attack(player, companion, intent);
			case STATUS -> status(companion);
			case INVENTORY -> {
				if (companion.distanceTo(player) > 16) {
					companion.setOrder(new ComeOrder());
					yield Outcome.yes("ack.come", Map.of());
				}
				companion.openInventory(player);
				yield Outcome.yes("talk.hello", Map.of());
			}
			case RECRUIT -> Outcome.no("recruit.already", Map.of());
			case DISMISS -> {
				companion.dismiss();
				yield Outcome.yes("dismiss.accept", Map.of());
			}
		};
	}

	private static Outcome recruit(ServerPlayer player, CompanionEntity companion) {
		int limit = Hearthbound.config().partyLimit();
		int size = partySize(player);
		if (size >= limit) {
			return Outcome.no("recruit.party_full", Map.of("count", Integer.toString(size)));
		}
		companion.recruit(player);
		companion.wave();
		if (RECRUIT_TIPS.add(player.getUUID())) {
			String name = companion.companionName().split(" ")[0];
			player.level().getServer().execute(() -> player.sendSystemMessage(
					Texts.tr("message.hearthbound.recruited_tip", name).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
		}
		return Outcome.yes("recruit.accept", Map.of());
	}

	private static Outcome gather(CompanionEntity companion, Intent intent) {
		GatherCatalog.Resolution resolution = GatherCatalog.resolve(intent.item());
		return switch (resolution) {
			case GatherCatalog.Resolution.Unknown unknown -> Outcome.no("fail.unknown_item", Map.of("item", clip(unknown.phrase())));
			case GatherCatalog.Resolution.NotGatherable not -> Outcome.no("fail.not_gatherable", Map.of("item", not.displayName()));
			case GatherCatalog.Resolution.Gatherable gatherable -> {
				if (WorldRules.isProtected((ServerLevel) companion.level(), companion.blockPosition())) {
					yield Outcome.no("fail.protected", Map.of());
				}
				int count = intent.count() == null ? gatherable.spec().defaultCount() : intent.count();
				if (count < 1 || count > MAX_GATHER) {
					yield Outcome.no("fail.count_invalid", Map.of("max", Integer.toString(MAX_GATHER)));
				}
				companion.setOrder(new GatherOrder(companion, gatherable.spec(), count));
				yield Outcome.yes("ack.gather", Map.of("count", Integer.toString(count), "item", gatherable.spec().displayName()));
			}
		};
	}

	/** Lays the structure out in front of the player and checks site and materials before agreeing. */
	private static Outcome build(ServerPlayer player, CompanionEntity companion, Intent intent) {
		Optional<Blueprint.Kind> kind = Blueprint.Kind.byPhrase(intent.item());
		if (kind.isEmpty()) {
			return Outcome.no("fail.build_unknown", Map.of("things", BUILD_THINGS));
		}
		String thing = kind.get().label();
		ServerLevel level = (ServerLevel) player.level();
		if (companion.level() != level) {
			return Outcome.no("fail.owner_gone", Map.of());
		}
		Direction facing = player.getDirection();
		BlockPos feet = player.blockPosition();
		Blueprint blueprint = Blueprint.plan(kind.get(), feet, facing, level);
		for (Blueprint.Spot spot : blueprint.spots()) {
			if (WorldRules.isProtected(level, spot.pos())) {
				return Outcome.no("fail.protected", Map.of());
			}
		}
		Optional<String> problem = blueprint.siteProblem(level);
		if (problem.isPresent()) {
			String why = switch (problem.get()) {
				case "uneven" -> "the ground's too uneven";
				case "blocked" -> "there's too much in the way";
				case "lava" -> "there's lava";
				default -> "there's nothing to bridge here";
			};
			return Outcome.no("fail.build_site", Map.of("thing", thing, "why", why));
		}
		if (kind.get() == Blueprint.Kind.CAMP) {
			if (WorldRules.isProtected(level, blueprint.centre())) {
				return Outcome.no("fail.protected", Map.of());
			}
			if (companion.countCarried(s -> s.is(net.minecraft.world.item.Items.CAMPFIRE) || s.is(net.minecraft.world.item.Items.SOUL_CAMPFIRE)) == 0) {
				return Outcome.no("fail.camp_campfire", Map.of());
			}
		}
		int need = blueprint.openSpots(level).size();
		int have = BuildOrder.countBuildingBlocks(companion);
		if (have < need) {
			return Outcome.no("fail.build_materials", Map.of("thing", thing, "need", Integer.toString(need), "have", Integer.toString(have)));
		}
		companion.setOrder(new BuildOrder(companion, blueprint, feet, facing, level));
		return Outcome.yes("ack.build", Map.of("thing", thing));
	}

	/** Digging starts in front of the player, the way they face. Needs a pickaxe and somewhere allowed. */
	private static Outcome mine(ServerPlayer player, CompanionEntity companion, Intent intent) {
		ServerLevel level = (ServerLevel) player.level();
		if (companion.level() != level) {
			return Outcome.no("fail.owner_gone", Map.of());
		}
		boolean pickaxe = companion.getMainHandItem().is(ItemTags.PICKAXES)
				|| companion.inventory().getItems().stream().anyMatch(stack -> stack.is(ItemTags.PICKAXES));
		if (!pickaxe) {
			return Outcome.no("fail.needs_tool", Map.of("tool", "pickaxe", "item", "stone"));
		}
		Direction facing = player.getDirection();
		BlockPos start = player.blockPosition().relative(facing);
		if (WorldRules.isProtected(level, start)) {
			return Outcome.no("fail.protected", Map.of());
		}
		String shape = intent.target() == null ? "trip" : intent.target();
		int length = intent.count() == null ? 0 : Math.max(1, Math.min(MAX_DIG, intent.count()));
		List<MineOrder.Leg> legs = switch (shape) {
			case "down" -> List.of(new MineOrder.Leg(MineOrder.Shape.STAIRS, length > 0 ? length : 16));
			case "tunnel" -> List.of(new MineOrder.Leg(MineOrder.Shape.TUNNEL, length > 0 ? length : 24));
			default -> List.of(new MineOrder.Leg(MineOrder.Shape.STAIRS, length > 0 ? length : 16), new MineOrder.Leg(MineOrder.Shape.TUNNEL, 24));
		};
		companion.setOrder(new MineOrder(companion, start, facing, legs));
		return Outcome.yes("ack.mine", Map.of());
	}

	private static Outcome give(ServerPlayer player, CompanionEntity companion, Intent intent) {
		Predicate<ItemStack> filter;
		String name;
		if (intent.item() == null) {
			if (!companion.carriesAnything()) {
				return Outcome.no("ack.give.nothing", Map.of());
			}
			filter = stack -> true;
			name = "everything";
		} else {
			GatherCatalog.Resolution resolution = GatherCatalog.resolve(intent.item());
			if (resolution instanceof GatherCatalog.Resolution.Gatherable gatherable) {
				filter = gatherable.spec().counts();
				name = gatherable.spec().displayName();
			} else {
				String wanted = intent.item().toLowerCase(Locale.ROOT);
				filter = stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(CommandParserAccess.singular(wanted));
				name = wanted;
			}
			if (companion.countCarried(filter) == 0) {
				return Outcome.no("fail.give_missing", Map.of("item", clip(name)));
			}
		}
		int max = intent.count() == null ? Integer.MAX_VALUE : intent.count();
		companion.setOrder(new GiveOrder(filter, name, max));
		if (companion.distanceTo(player) > 3) {
			return Outcome.yes("ack.come", Map.of());
		}
		// Close enough: the order hands items over on its first tick and says so then.
		return new Outcome(true, null, Map.of());
	}

	private static Outcome attack(ServerPlayer player, CompanionEntity companion, Intent intent) {
		LivingEntity target = findTarget(player, intent.target());
		if (target == null) {
			return Outcome.no("fail.no_target", Map.of());
		}
		String name = target.getName().getString().toLowerCase(Locale.ROOT);
		if (target == player || target instanceof CompanionEntity || target instanceof Player) {
			return Outcome.no("fail.refuse_target", Map.of("mob", name));
		}
		// Anyone can fight; nobody hurts villagers, golems or pets.
		boolean protectedTarget = target instanceof AbstractVillager || target instanceof IronGolem
				|| (target instanceof TamableAnimal tame && tame.isTame());
		if (protectedTarget) {
			return Outcome.no("fail.refuse_target", Map.of("mob", name));
		}
		// Never someone else's pet or mount, whatever the personality.
		if (target instanceof TamableAnimal tame && tame.isTame() && !tame.isOwnedBy(player)) {
			return Outcome.no("fail.refuse_target", Map.of("mob", name));
		}
		companion.setOrder(new AttackOrder(target, name));
		companion.setTarget(target);
		return Outcome.yes("ack.attack", Map.of("mob", name));
	}

	private static Outcome status(CompanionEntity companion) {
		if (companion.isDowned()) {
			return Outcome.yes("status.downed", Map.of());
		}
		CompanionOrder order = companion.order();
		return switch (order.kind()) {
			case FOLLOW -> Outcome.yes("status.follow", Map.of());
			case WAIT -> Outcome.yes("status.wait", Map.of());
			case GATHER -> {
				GatherOrder gather = (GatherOrder) order;
				yield Outcome.yes("status.gather", Map.of("item", gather.spec().displayName(),
						"have", Integer.toString(Math.max(0, gather.collected())), "count", Integer.toString(gather.goal())));
			}
			case MINE -> Outcome.yes("status.mine", Map.of("count", Integer.toString(((MineOrder) order).stepsDone())));
			case BUILD -> Outcome.yes("status.build", Map.of("thing", ((BuildOrder) order).blueprint().kind().label()));
			default -> Outcome.yes("status.idle", Map.of());
		};
	}

	/** Looked-at creature, a named one near the player, or the nearest hostile. */
	private static LivingEntity findTarget(ServerPlayer player, String phrase) {
		AABB area = player.getBoundingBox().inflate(24);
		if (phrase == null) {
			Vec3 eye = player.getEyePosition();
			Vec3 end = eye.add(player.getViewVector(1.0F).scale(24));
			EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, area,
					e -> e instanceof LivingEntity && !(e instanceof CompanionEntity) && e.isPickable(), 24 * 24);
			if (hit != null && hit.getEntity() instanceof LivingEntity living) {
				return living;
			}
			return player.level().getEntitiesOfClass(LivingEntity.class, area, e -> e instanceof Enemy && e.isAlive()).stream()
					.min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
		}
		String wanted = CommandParserAccess.singular(phrase.toLowerCase(Locale.ROOT));
		if (HUNT_WORDS.contains(phrase.toLowerCase(Locale.ROOT))) {
			return player.level().getEntitiesOfClass(Animal.class, area, e -> e.isAlive() && !(e instanceof TamableAnimal tame && tame.isTame()))
					.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
		}
		return player.level().getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && !(e instanceof CompanionEntity) && e != player
						&& e.getType().getDescription().getString().toLowerCase(Locale.ROOT).equals(wanted))
				.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
	}

	/** Answers small talk with scripted lines (used when no AI model is configured or it fails). */
	public static void talk(ServerPlayer player, CompanionEntity companion, TalkTopic topic) {
		String biome = biomeName(companion);
		Context ctx = context(companion);
		long now = companion.level().getGameTime();
		// "and you?" and "tell me more" lean on what we were just talking about.
		if ((topic == TalkTopic.MORE || topic == TalkTopic.AND_YOU) && ctx.topic != null && now - ctx.tick < 2400) {
			topic = ctx.topic;
		} else if (REPLAYABLE.contains(topic)) {
			ctx.topic = topic;
			ctx.tick = now;
		}
		List<String> events = companion.memory().events();
		// Shared events belong to whoever the companion travels with.
		if (topic == TalkTopic.MEMORY && (events.isEmpty() || !companion.isOwnedBy(player))) {
			topic = TalkTopic.GENERIC;
		}
		// Events are kept as "I joined {player} on the road"; said to that player, it's "you".
		String event = events.isEmpty() ? "we set out" : events.get(companion.getRandom().nextInt(events.size())).replace("{player}", "you");
		String key = topic.lineKey();
		Map<String, String> vars = new HashMap<>(Map.of("biome", biome, "event", event, "player", companion.callName(player)));
		if (topic == TalkTopic.ABOUT_ME) {
			Optional<PlayerFacts> known = companion.memory().knownFacts(player.getUUID().toString());
			if (known.isPresent()) {
				vars.put("facts", known.get().summary());
			} else {
				key = "talk.about_me.none";
			}
		} else if (topic == TalkTopic.BOND) {
			int affinity = companion.memory().affinity();
			key = !companion.isOwnedBy(player) ? "talk.bond.stranger" : affinity >= 25 ? "talk.bond.close" : affinity <= -20 ? "talk.bond.cold" : "talk.bond";
		}
		companion.say(key, vars);
		switch (topic) {
			case HELLO, BYE -> companion.wave();
			case LAUGH, COMPLIMENT, FONDNESS -> companion.hop();
			default -> {
			}
		}
		if (topic == TalkTopic.HELP) {
			player.sendSystemMessage(Texts.tr("message.hearthbound.help_hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	public static String biomeName(Entity entity) {
		return entity.level().getBiome(entity.blockPosition()).unwrapKey()
				.map(key -> key.identifier().getPath().replace('_', ' '))
				.orElse("these lands");
	}

	static boolean isMobName(String phrase) {
		String id = phrase.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
		for (String candidate : List.of(id, CommandParserAccess.singular(id))) {
			Identifier key = Identifier.tryParse("minecraft:" + candidate);
			if (key != null && BuiltInRegistries.ENTITY_TYPE.getOptional(key)
					.filter(type -> type.getCategory() != MobCategory.MISC).isPresent()) {
				return true;
			}
		}
		return false;
	}

	private static String clip(String text) {
		return text.length() > 40 ? text.substring(0, 40) + "..." : text;
	}

	/** Small helper so this class can share the parser's plural handling. */
	private static final class CommandParserAccess {
		static String singular(String phrase) {
			if (phrase.endsWith("ies") && phrase.length() > 4) {
				return phrase.substring(0, phrase.length() - 3) + "y";
			}
			if (phrase.endsWith("s") && !phrase.endsWith("ss") && phrase.length() > 3) {
				return phrase.substring(0, phrase.length() - 1);
			}
			return phrase;
		}
	}
}

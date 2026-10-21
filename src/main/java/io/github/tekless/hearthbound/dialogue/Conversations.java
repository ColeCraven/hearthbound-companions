package io.github.tekless.hearthbound.dialogue;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.ai.AiDialogue;
import io.github.tekless.hearthbound.core.intent.CommandParser;
import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.intent.ParseResult;
import io.github.tekless.hearthbound.core.intent.TalkTopic;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.order.AttackOrder;
import io.github.tekless.hearthbound.entity.order.ComeOrder;
import io.github.tekless.hearthbound.entity.order.CompanionOrder;
import io.github.tekless.hearthbound.entity.order.FollowOrder;
import io.github.tekless.hearthbound.entity.order.GatherOrder;
import io.github.tekless.hearthbound.entity.order.GiveOrder;
import io.github.tekless.hearthbound.entity.order.WaitOrder;
import io.github.tekless.hearthbound.world.CompanionRoster;
import io.github.tekless.hearthbound.world.CompanionTracker;
import io.github.tekless.hearthbound.world.GatherCatalog;
import io.github.tekless.hearthbound.world.WorldRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
	private static final double ADDRESS_RANGE = 64;
	private static final Pattern AT_ADDRESS = Pattern.compile("^@(\\S+)\\s*(.*)$", Pattern.DOTALL);
	private static final Pattern NAME_ADDRESS = Pattern.compile("^([A-Za-z]+)\\s*[,:]\\s*(.+)$", Pattern.DOTALL);
	/** "@Rosie" somewhere in the middle of a message, e.g. "hey @rosie, how are you?". */
	private static final Pattern AT_MENTION = Pattern.compile("(?:^|\\s)@([A-Za-z]+)[,:;!?.]*");
	private static final CommandParser PARSER = new CommandParser(Conversations::isMobName);

	private Conversations() {
	}

	/**
	 * Handles a chat line if it is addressed to companions ("@Hazel follow me", "Hazel, wait here",
	 * "@all come here"). Returns true if it was.
	 */
	public static boolean onChat(ServerPlayer player, String raw) {
		String text = raw.trim();
		String name;
		String body;
		Matcher at = AT_ADDRESS.matcher(text);
		Matcher named = NAME_ADDRESS.matcher(text);
		if (at.matches()) {
			name = at.group(1);
			body = at.group(2);
		} else if (named.matches() && !nearbyNamed(player, named.group(1)).isEmpty()) {
			name = named.group(1);
			body = named.group(2);
		} else {
			// A mention further into the message only counts if a companion by that name is close by,
			// so "@Steve look at this" between players is left alone.
			Matcher mention = AT_MENTION.matcher(text);
			if (!mention.find() || (isGroupName(mention.group(1)) ? party(player).isEmpty() : nearbyNamed(player, mention.group(1)).isEmpty())) {
				return false;
			}
			name = mention.group(1);
			body = (text.substring(0, mention.start()) + " " + text.substring(mention.end())).replaceAll("\\s+", " ").trim()
					.replaceAll("^[,:;]+\\s*|\\s*[,:;]+$", "");
			if (body.isEmpty()) {
				body = "hello";
			}
		}
		List<CompanionEntity> targets = isGroupName(name) ? party(player) : nearbyNamed(player, name);
		if (targets.isEmpty()) {
			player.sendSystemMessage(Texts.tr(isGroupName(name) ? "message.hearthbound.no_party" : "message.hearthbound.no_such_companion", name)
					.withStyle(ChatFormatting.GRAY));
			return true;
		}
		for (CompanionEntity companion : targets) {
			handleMessage(player, companion, body);
		}
		return true;
	}

	private static boolean isGroupName(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		return n.equals("all") || n.equals("everyone") || n.equals("party") || n.equals("team") || n.equals("companions");
	}

	/** Companions within earshot with that name (case-insensitive, first name). */
	public static List<CompanionEntity> nearbyNamed(ServerPlayer player, String name) {
		String wanted = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
		List<CompanionEntity> found = new ArrayList<>();
		for (CompanionEntity companion : nearby(player)) {
			String first = companion.companionName().toLowerCase(Locale.ROOT).split(" ")[0].replaceAll("[^a-z]", "");
			if (!wanted.isEmpty() && first.equals(wanted)) {
				found.add(companion);
			}
		}
		// If several share a name, prefer the player's own companions, then the closest.
		found.sort(Comparator.comparing((CompanionEntity c) -> !c.isOwnedBy(player)).thenComparingDouble(c -> c.distanceToSqr(player)));
		return found.isEmpty() ? found : List.of(found.get(0));
	}

	/** The player's recruited companions in the same dimension. */
	public static List<CompanionEntity> party(ServerPlayer player) {
		List<CompanionEntity> party = new ArrayList<>();
		for (CompanionEntity companion : CompanionTracker.ownedBy(player)) {
			if (companion.level() == player.level()) {
				party.add(companion);
			}
		}
		return party;
	}

	private static List<CompanionEntity> nearby(ServerPlayer player) {
		return CompanionTracker.near(player, ADDRESS_RANGE);
	}

	/** Counts every companion travelling with the player, including ones in unloaded chunks. */
	public static int partySize(ServerPlayer player) {
		int loaded = CompanionTracker.ownedBy(player).size();
		return Math.max(loaded, CompanionRoster.get(player.level().getServer()).ownedBy(player.getUUID()));
	}

	/** One message to one companion. */
	public static void handleMessage(ServerPlayer player, CompanionEntity companion, String text) {
		companion.noteSpeaker(player);
		companion.memory().talkingWith(player.getUUID().toString());
		companion.memory().addPlayerLine(text);
		companion.getLookControl().setLookAt(player);
		ParseResult result = PARSER.parse(text, List.of(companion.companionName()));
		switch (result) {
			case ParseResult.Command command -> execute(player, companion, command.intent());
			case ParseResult.Talk talk -> {
				if (!AiDialogue.tryConverse(player, companion, text, null)) {
					talk(player, companion, talk.topic());
				}
			}
			case ParseResult.Unclear unclear -> companion.say(
					unclear.reason() == ParseResult.UnclearReason.GATHER_WHAT ? "unclear.gather_what" : "unclear.generic", Map.of());
			case ParseResult.Unsupported unsupported -> companion.say("fail.unsupported", Map.of("action", unsupported.action()));
			case ParseResult.Unrecognised unrecognised -> {
				if (!AiDialogue.tryConverse(player, companion, text, null)) {
					talk(player, companion, TalkTopic.GENERIC);
				}
			}
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
		Archetype archetype = companion.archetype();
		boolean protectedTarget = target instanceof AbstractVillager || target instanceof IronGolem
				|| (target instanceof TamableAnimal tame && tame.isTame());
		if (archetype.refuses(Archetype.TargetRefusal.VILLAGERS_AND_PETS) && protectedTarget) {
			return Outcome.no("fail.refuse_target", Map.of("mob", name));
		}
		if (archetype.refuses(Archetype.TargetRefusal.FARM_ANIMALS) && target instanceof Animal) {
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
		return player.level().getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && !(e instanceof CompanionEntity) && e != player
						&& e.getType().getDescription().getString().toLowerCase(Locale.ROOT).equals(wanted))
				.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
	}

	/** Answers small talk with scripted lines (used when no AI model is configured or it fails). */
	public static void talk(ServerPlayer player, CompanionEntity companion, TalkTopic topic) {
		String biome = biomeName(companion);
		List<String> events = companion.memory().events();
		if (topic == TalkTopic.MEMORY && events.isEmpty()) {
			topic = TalkTopic.GENERIC;
		}
		String event = events.isEmpty() ? "the road" : events.get(companion.getRandom().nextInt(events.size()))
				.replace("{player}", player.getName().getString());
		companion.say(topic.lineKey(), Map.of("biome", biome, "event", event, "player", player.getName().getString()));
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

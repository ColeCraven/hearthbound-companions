package io.github.tekless.hearthbound.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.dialogue.Texts;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.core.persona.CastMember;
import java.util.stream.Stream;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import io.github.tekless.hearthbound.registry.ModEntities;
import io.github.tekless.hearthbound.world.CompanionRoster;
import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.config.HearthboundConfig;
import io.github.tekless.hearthbound.world.CompanionTracker;
import io.github.tekless.hearthbound.world.HubResidents;
import io.github.tekless.hearthbound.world.Homes;
import io.github.tekless.hearthbound.world.Obey;
import io.github.tekless.hearthbound.life.HomeAreas;
import io.github.tekless.hearthbound.life.Places;
import io.github.tekless.hearthbound.life.Zone;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * {@code /companion ...} - a keyboard-friendly way to do everything the chat and UI can do, plus
 * {@code summon} for testing. Orders go through the same validation as chat.
 */
public final class CompanionCommands {
	private static final SuggestionProvider<CommandSourceStack> NEARBY_NAMES = (context, builder) -> {
		ServerPlayer player = context.getSource().getPlayer();
		if (player != null) {
			CompanionTracker.near(player, 64).forEach(c -> builder.suggest(c.companionName().split(" ")[0]));
		}
		return builder.buildFuture();
	};

	/** A name, or party, everyone, or a role. */
	private static final SuggestionProvider<CommandSourceStack> WHO = (context, builder) -> {
		ServerPlayer player = context.getSource().getPlayer();
		if (player != null) {
			CompanionTracker.near(player, 64).forEach(c -> builder.suggest(c.companionName().split(" ")[0]));
		}
		return SharedSuggestionProvider.suggest(List.of("party", "everyone", "builders", "farmers", "collectors", "guardians"), builder);
	};

	private CompanionCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var root = Commands.literal("companion");
		root.then(Commands.literal("summon")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> summon(ctx, null, null, null, null))
				.then(Commands.argument("archetype", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(Stream.concat(Arrays.stream(CastMember.values()).map(CastMember::id),
								Arrays.stream(Archetype.values()).map(Archetype::id)), b))
						.executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "archetype"), null, null, null))
						.then(Commands.argument("name", StringArgumentType.word())
								.executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "archetype"), StringArgumentType.getString(ctx, "name"), null, null))
								.then(Commands.argument("skin", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(CompanionSkin.values()).map(CompanionSkin::id), b))
										.executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "archetype"), StringArgumentType.getString(ctx, "name"),
												StringArgumentType.getString(ctx, "skin"), null))
										.then(Commands.argument("model", StringArgumentType.word())
												.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("wide", "slim"), b))
												.executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "archetype"), StringArgumentType.getString(ctx, "name"),
														StringArgumentType.getString(ctx, "skin"), StringArgumentType.getString(ctx, "model"))))))));

		root.then(Commands.literal("list").executes(CompanionCommands::list));
		root.then(Commands.literal("help").executes(CompanionCommands::help));
		root.executes(CompanionCommands::help);

		for (IntentType type : List.of(IntentType.RECRUIT, IntentType.DISMISS, IntentType.FOLLOW, IntentType.WAIT, IntentType.COME,
				IntentType.CANCEL, IntentType.STATUS, IntentType.GIVE, IntentType.INVENTORY)) {
			root.then(Commands.literal(type.id())
					.then(Commands.argument("name", StringArgumentType.word()).suggests(NEARBY_NAMES)
							.executes(ctx -> order(ctx, Intent.of(type)))));
		}
		root.then(Commands.literal("gather")
				.then(Commands.argument("name", StringArgumentType.word()).suggests(NEARBY_NAMES)
						.then(Commands.argument("count", IntegerArgumentType.integer(1, Conversations.MAX_GATHER))
								.then(Commands.argument("item", StringArgumentType.greedyString())
										.executes(ctx -> order(ctx, Intent.gather(StringArgumentType.getString(ctx, "item"),
												IntegerArgumentType.getInteger(ctx, "count"))))))));
		root.then(Commands.literal("say")
				.then(Commands.argument("name", StringArgumentType.word()).suggests(NEARBY_NAMES)
						.then(Commands.argument("message", StringArgumentType.greedyString())
								.executes(CompanionCommands::say))));
		root.then(Commands.literal("skin")
				.then(Commands.argument("name", StringArgumentType.word()).suggests(NEARBY_NAMES)
						.then(Commands.argument("skin", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(Stream.concat(Arrays.stream(CompanionSkin.values()).map(CompanionSkin::id),
										Arrays.stream(CastMember.values()).map(CastMember::id)), b))
								.executes(ctx -> skin(ctx, null))
								.then(Commands.argument("model", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("wide", "slim"), b))
										.executes(ctx -> skin(ctx, StringArgumentType.getString(ctx, "model")))))));
		root.then(Commands.literal("tell")
				.then(Commands.argument("who", StringArgumentType.word())
						.suggests((c, b) -> {
							ServerPlayer p = c.getSource().getPlayer();
							if (p != null) {
								CompanionTracker.ownedBy(p).forEach(x -> b.suggest(x.companionName().split(" ")[0]));
							}
							return SharedSuggestionProvider.suggest(List.of("party", "everyone"), b);
						})
						.then(Commands.argument("message", StringArgumentType.greedyString()).executes(CompanionCommands::tell))));
		root.then(Commands.literal("chattiness")
				.requires(CompanionCommands::mayConfigure)
				.executes(ctx -> chattiness(ctx, null))
				.then(Commands.argument("level", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(HearthboundConfig.CHATTINESS.keySet(), b))
						.executes(ctx -> chattiness(ctx, StringArgumentType.getString(ctx, "level")))));
		root.then(Commands.literal("autonomy")
				.requires(CompanionCommands::mayConfigure)
				.executes(ctx -> autonomy(ctx, null))
				.then(Commands.argument("mode", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("on", "off"), b))
						.executes(ctx -> autonomy(ctx, StringArgumentType.getString(ctx, "mode")))));
		root.then(Commands.literal("sethome")
				.then(Commands.argument("who", StringArgumentType.word()).suggests(WHO)
						.executes(ctx -> setHome(ctx, null))
						.then(Commands.literal("here").executes(ctx -> setHome(ctx, null)))
						.then(Commands.argument("pos", BlockPosArgument.blockPos())
								.executes(ctx -> setHome(ctx, BlockPosArgument.getBlockPos(ctx, "pos"))))));
		root.then(Commands.literal("home")
				.then(Commands.argument("who", StringArgumentType.word()).suggests(WHO)
						.executes(ctx -> goHome(ctx, false))
						.then(Commands.literal("teleport").executes(ctx -> goHome(ctx, true)))));
		root.then(Commands.literal("tphome")
				.then(Commands.argument("who", StringArgumentType.word()).suggests(WHO)
						.executes(ctx -> goHome(ctx, true))));
		for (String word : List.of("obey", "lead")) {
			root.then(Commands.literal(word)
					.requires(CompanionCommands::mayConfigure)
					.executes(ctx -> obey(ctx, null))
					.then(Commands.argument("mode", StringArgumentType.word())
							.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("on", "off"), b))
							.executes(ctx -> obey(ctx, StringArgumentType.getString(ctx, "mode")))));
		}
		root.then(Commands.literal("setplace")
				.requires(CompanionCommands::mayConfigure)
				.then(Commands.argument("place", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("bed", "commons", "kitchen", "farm", "storage", "patrol"), b))
						.executes(ctx -> setPlace(ctx, null))
						.then(Commands.argument("who", StringArgumentType.word()).suggests(WHO)
								.executes(ctx -> setPlace(ctx, StringArgumentType.getString(ctx, "who"))))));
		root.then(Commands.literal("places")
				.executes(ctx -> places(ctx, null))
				.then(Commands.argument("who", StringArgumentType.word()).suggests(NEARBY_NAMES)
						.executes(ctx -> places(ctx, StringArgumentType.getString(ctx, "who")))));
		root.then(Commands.literal("partylimit")
				.requires(CompanionCommands::mayConfigure)
				.executes(ctx -> partyLimit(ctx, null, null))
				.then(Commands.argument("limit", StringArgumentType.word())
						.suggests((c, b) -> {
							if (c.getSource().getServer() != null) {
								c.getSource().getServer().getPlayerList().getPlayers().forEach(p -> b.suggest(p.getName().getString()));
							}
							return SharedSuggestionProvider.suggest(List.of("3", "5", "unlimited"), b);
						})
						.executes(ctx -> partyLimit(ctx, null, StringArgumentType.getString(ctx, "limit")))
						.then(Commands.argument("playerLimit", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("3", "5", "unlimited", "default"), b))
								.executes(ctx -> partyLimit(ctx, StringArgumentType.getString(ctx, "limit"), StringArgumentType.getString(ctx, "playerLimit"))))));
		root.then(Commands.literal("hub")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("list").executes(CompanionCommands::hubList))
				.then(Commands.literal("spawn").executes(CompanionCommands::hubSpawn))
				.then(Commands.literal("home")
						.then(Commands.argument("name", StringArgumentType.word()).suggests(NEARBY_NAMES)
								.executes(CompanionCommands::hubHome))));
		BlueprintCommands.register(root);
		dispatcher.register(root);
	}

	/**
	 * The single-player host, an operator, or the server console. Commands that change things for everyone
	 * check this when they run, since in a LAN world {@link #mayConfigure} lets every player see them.
	 */
	private static boolean isAdmin(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player == null ? Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source) : Obey.mayUse(player);
	}

	/** In single-player the host can always change these; on a server it takes operator rights. */
	private static boolean mayConfigure(CommandSourceStack source) {
		// The command tree is also built for each joining player with no server on the source: show it then,
		// the real check happens again when the command runs.
		return source.getServer() == null || source.getServer().isSingleplayer() || Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)
				|| (source.getPlayer() != null && Obey.isHost(source.getPlayer()));
	}

	/** {@code /companion tell <name|party|everyone> <message>}: the same as typing @name message in chat. */
	private static int tell(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String who = StringArgumentType.getString(ctx, "who");
		Conversations.onChat(player, "@" + who + " " + StringArgumentType.getString(ctx, "message"));
		return 1;
	}

	private static int chattiness(CommandContext<CommandSourceStack> ctx, String level) {
		HearthboundConfig config = Hearthbound.config();
		if (level != null) {
			Double value = HearthboundConfig.CHATTINESS.get(level.toLowerCase(Locale.ROOT));
			if (value == null) {
				try {
					value = Math.max(0.0, Math.min(2.0, Double.parseDouble(level)));
				} catch (NumberFormatException e) {
					ctx.getSource().sendFailure(Component.literal("Pick off, low, normal or high (or a number from 0 to 2)."));
					return 0;
				}
			}
			config.ambientChatter = value;
			config.save();
		}
		String name = config.chattinessName();
		ctx.getSource().sendSuccess(() -> Component.literal("Chattiness is " + name + String.format(Locale.ROOT, " (%.1f). ", config.ambientChatter) + switch (name) {
			case "off" -> "No unprompted chatter. They still answer you, confirm orders and warn you about danger.";
			case "low" -> "Only now and then. Replies, confirmations and warnings are unchanged.";
			case "high" -> "They'll chat plenty, and talk to each other more.";
			default -> "The default: comments now and then, and the odd chat between them.";
		} + " It applies to every companion in the world. Options: off, low, normal, high."), false);
		return 1;
	}

	private static int autonomy(CommandContext<CommandSourceStack> ctx, String mode) {
		HearthboundConfig config = Hearthbound.config();
		if (mode != null) {
			if (!mode.equalsIgnoreCase("on") && !mode.equalsIgnoreCase("off")) {
				ctx.getSource().sendFailure(Component.literal("Say on or off."));
				return 0;
			}
			config.routines = mode.equalsIgnoreCase("on");
			config.save();
		}
		ctx.getSource().sendSuccess(() -> Component.literal(config.routines
				? "Autonomy is on for every companion in the world. While they follow you and you stand still, or while they pass the time around their own home, collectors gather logs and stone, farmers harvest and replant, guardians hunt hostile mobs and builders light dark spots. Your orders always come first, and they leave protected areas, your builds and your chests alone."
				: "Autonomy is off. Companions only do what you ask. Turn it on with /companion autonomy on."), false);
		return 1;
	}

	/** Lists every cast member the world knows about: at the hub, or travelling with someone. */
	private static int hubList(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		if (!HubResidents.enabled()) {
			source.sendFailure(Component.literal("The hub is off. Set server.hub in config/hearthbound.json and restart."));
			return 0;
		}
		CompanionRoster roster = CompanionRoster.get(source.getServer());
		for (CastMember member : HubResidents.residents()) {
			CompanionRoster.Entry entry = roster.entryForCast(member);
			String where = entry == null ? "not spawned yet" : entry.ownerName != null ? "travelling with " + entry.ownerName : "at the hub";
			source.sendSuccess(() -> Component.literal(member.displayName()).withStyle(CompanionEntity.nameColour(member.archetype().id()))
					.append(Component.literal(" - " + where).withStyle(ChatFormatting.GRAY)), false);
		}
		return 1;
	}

	/** Spawns any hub residents who don't exist yet, right now. */
	private static int hubSpawn(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		var level = HubResidents.hubLevel(source.getServer());
		var hub = HubResidents.hubPos();
		if (level.isEmpty() || hub.isEmpty()) {
			source.sendFailure(Component.literal("The hub is off. Set server.hub in config/hearthbound.json and restart."));
			return 0;
		}
		level.get().getChunkAt(hub.get());
		int spawned = HubResidents.spawnMissing(level.get(), hub.get());
		source.sendSuccess(() -> Component.literal(spawned == 0 ? "Everyone already lives at the hub (or is out travelling)."
				: spawned + " companion(s) moved into the hub."), true);
		return spawned;
	}

	/**
	 * Sends a companion back to the hub, ending their travels with whoever had them. Works from the console.
	 * If the world has a record of them but no body is loaded anywhere, they are respawned at the hub, since
	 * the caller asked for exactly that.
	 */
	private static int hubHome(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		if (!HubResidents.enabled()) {
			source.sendFailure(Component.literal("The hub is off. Set server.hub in config/hearthbound.json and restart."));
			return 0;
		}
		String wanted = name.toLowerCase(Locale.ROOT);
		CompanionEntity companion = CompanionTracker.all().stream()
				.filter(c -> c.companionName().toLowerCase(Locale.ROOT).split(" ")[0].equals(wanted))
				.min(java.util.Comparator.comparingDouble(c -> source.getPosition().distanceToSqr(c.position()))).orElse(null);
		if (companion != null) {
			HubResidents.hubPos().ifPresent(companion::makeHubResident);
			if (companion.isRecruited()) {
				companion.dismiss();
			}
			HubResidents.sendHome(companion);
			source.sendSuccess(() -> Component.literal(companion.companionName() + " is back at the hub."), true);
			return 1;
		}
		CastMember member = CastMember.byName(name).orElse(null);
		var level = HubResidents.hubLevel(source.getServer());
		var hub = HubResidents.hubPos();
		if (member == null || level.isEmpty() || hub.isEmpty()) {
			source.sendFailure(Component.literal("No companion called " + name + " is loaded. Only cast characters can be respawned."));
			return 0;
		}
		CompanionRoster roster = CompanionRoster.get(source.getServer());
		CompanionRoster.Entry entry = roster.entryForCast(member);
		level.get().getChunkAt(hub.get());
		roster.forgetCast(member);
		if (!HubResidents.spawnOne(level.get(), hub.get(), member, 0, 1)) {
			source.sendFailure(Component.literal("Could not spawn " + member.displayName() + "."));
			return 0;
		}
		String was = entry == null ? "was not in the world yet" : entry.ownerName != null ? "was recorded as travelling with " + entry.ownerName + " but no body is loaded" : "had no loaded body";
		source.sendSuccess(() -> Component.literal(member.displayName() + " " + was + ", so they have been respawned at the hub. "
				+ "If the original turns up later, remove one of them."), true);
		return 1;
	}

	/**
	 * {@code /companion obey [on|off]}: every companion in the world takes your orders, recruited by you, by nobody,
	 * or by another player. Only for the single-player host and operators.
	 */
	private static int obey(CommandContext<CommandSourceStack> ctx, String mode) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (!Obey.mayUse(player)) {
			ctx.getSource().sendFailure(Component.literal("Only the host of a single-player world or a server operator can use obey mode."));
			return 0;
		}
		if (mode != null) {
			if (!mode.equalsIgnoreCase("on") && !mode.equalsIgnoreCase("off")) {
				ctx.getSource().sendFailure(Component.literal("Say on or off."));
				return 0;
			}
			Obey.set(player, mode.equalsIgnoreCase("on"));
		}
		boolean on = Obey.active(player);
		ctx.getSource().sendSuccess(() -> Component.literal(on
				? "Obey mode is on. Every companion in the world takes your orders: ones with nobody, ones with you, and ones travelling with other players. "
						+ "Nobody changes sides: those with nobody only join you if you ask, and everyone goes back to what they were doing when the job is done."
				: "Obey mode is off. Companions only take orders from the people they travel with. Turn it on with /companion obey on."), false);
		return 1;
	}

	/**
	 * {@code /companion partylimit [player] [number|unlimited|default]}: how many companions someone can travel with.
	 * The host, operators and the console only. 0 or "unlimited" lifts the limit.
	 */
	private static int partyLimit(CommandContext<CommandSourceStack> ctx, String who, String value) {
		CommandSourceStack source = ctx.getSource();
		HearthboundConfig config = Hearthbound.config();
		if (value == null) {
			int global = config.partyLimit;
			source.sendSuccess(() -> Component.literal("The party limit is " + (global <= 0 ? "unlimited" : Integer.toString(global))
					+ (config.partyLimits.isEmpty() ? "." : ", with " + config.partyLimits.size() + " player(s) on their own limit.")
					+ " Change it with /companion partylimit <number|unlimited>, or for one player with /companion partylimit <player> <number|unlimited|default>."), false);
			return Math.max(0, global);
		}
		if (!isAdmin(source)) {
			source.sendFailure(Component.literal("Only the host of a single-player world or a server operator can change the party limit."));
			return 0;
		}
		if (who == null && parseLimit(value) == null) {
			// "/companion partylimit Steve": that player's limit.
			ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(value);
			if (target == null) {
				source.sendFailure(Component.literal("Give a number, \"unlimited\", or the name of a player who is online."));
				return 0;
			}
			Integer own = config.partyLimits.get(target.getUUID().toString());
			int limit = config.partyLimitFor(target.getUUID());
			source.sendSuccess(() -> Component.literal(target.getName().getString() + "'s party limit is " + (limit <= 0 ? "unlimited" : Integer.toString(limit))
					+ (own == null ? " (the world's)." : " (their own).")), false);
			return limit;
		}
		if (who == null) {
			int limit = parseLimit(value);
			config.partyLimit = limit;
			config.save();
			source.sendSuccess(() -> Component.literal("Party limit set to " + (limit <= 0 ? "unlimited" : Integer.toString(limit))
					+ " for everyone. Companions who are already with someone stay with them."), true);
			return 1;
		}
		ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(who);
		if (target == null) {
			source.sendFailure(Component.literal(who + " isn't online. Set a limit for a player while they're on the server."));
			return 0;
		}
		if (value.equalsIgnoreCase("default") || value.equalsIgnoreCase("reset")) {
			config.partyLimits.remove(target.getUUID().toString());
			config.save();
			source.sendSuccess(() -> Component.literal(target.getName().getString() + " is back on the world's party limit."), true);
			return 1;
		}
		Integer limit = parseLimit(value);
		if (limit == null) {
			source.sendFailure(Component.literal("Give a number, \"unlimited\" or \"default\"."));
			return 0;
		}
		config.partyLimits.put(target.getUUID().toString(), limit);
		config.save();
		source.sendSuccess(() -> Component.literal(target.getName().getString() + " can now travel with " + (limit <= 0 ? "any number of" : "up to " + limit) + " companions."), true);
		return 1;
	}

	/**
	 * {@code /companion setplace <bed|commons|kitchen|farm|storage|patrol> [name|role]}: marks the spot you stand on as
	 * one of the places of the home nearby (or the home of the named companion, or of someone with that role).
	 */
	private static int setPlace(CommandContext<CommandSourceStack> ctx, String who) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		if (!isAdmin(source)) {
			source.sendFailure(Component.literal("Only the host of a single-player world or a server operator can mark places."));
			return 0;
		}
		String place = StringArgumentType.getString(ctx, "place").toLowerCase(Locale.ROOT);
		ServerLevel level = (ServerLevel) player.level();
		HomeAreas areas = HomeAreas.get(source.getServer());
		List<CompanionEntity> chosen = who == null ? List.of() : residentsCalled(player, who);
		if (who != null && chosen.isEmpty()) {
			source.sendFailure(Component.literal("Nobody called " + who + " lives around here. Places are for companions who aren't travelling with anyone."));
			return 0;
		}
		HomeAreas.Area area = !chosen.isEmpty() ? areas.areaOf(chosen.get(0)) : nearbyArea(player, areas);
		if (area == null) {
			source.sendFailure(Component.literal("Nobody lives near here yet. Dismiss a companion here (or give one a home with /companion sethome), then mark places."));
			return 0;
		}
		BlockPos at = player.blockPosition();
		String what;
		switch (place) {
			case "bed" -> {
				BlockPos bed = bedNear(level, at);
				if (bed == null) {
					source.sendFailure(Component.literal("Stand on (or right next to) the bed you want to give them."));
					return 0;
				}
				CompanionEntity sleeper = !chosen.isEmpty() ? chosen.get(0) : areas.residents(area).stream()
						.filter(c -> !area.markedBeds.contains(c.getUUID().toString()))
						.min(java.util.Comparator.comparingDouble(c -> c.distanceToSqr(player))).orElse(null);
				if (sleeper == null) {
					source.sendFailure(Component.literal("Say whose bed it is: /companion setplace bed <name>."));
					return 0;
				}
				areas.markBed(area, sleeper, bed);
				what = "That bed is " + sleeper.companionName().split(" ")[0] + "'s now";
			}
			case "commons", "meeting", "square" -> {
				areas.markCommons(area, at);
				what = "This is where everyone gets together in the evening now (a campfire here gets lit at dusk)";
			}
			case "kitchen" -> {
				areas.markKitchen(area, at);
				what = "Meals happen here now";
			}
			case "farm" -> {
				java.util.List<BlockPos> farmland = new java.util.ArrayList<>();
				for (BlockPos pos : BlockPos.betweenClosed(at.offset(-12, -3, -12), at.offset(12, 2, 12))) {
					if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.FARMLAND)) {
						farmland.add(pos.immutable());
					}
				}
				Zone zone = farmland.isEmpty() ? Zone.of(at.offset(-4, -1, -4), at.offset(4, 1, 4)) : io.github.tekless.hearthbound.life.HomeAreas.farmAround(farmland, at);
				areas.markFarm(area, zone);
				int across = zone.high().getX() - zone.low().getX() + 1;
				int deep = zone.high().getZ() - zone.low().getZ() + 1;
				what = (farmland.isEmpty() ? "No farmland here yet, so a " + across + " by " + deep + " patch around you is the farm. Till it and plant it, and the farmers take it from there"
						: "The farm is the " + across + " by " + deep + " patch of farmland around you. The farmers look after the crops in it, and only those");
			}
			case "storage", "store", "chest" -> {
				BlockPos chest = chestNear(level, at);
				if (chest == null) {
					source.sendFailure(Component.literal("Stand right next to the chest (or barrel) you want them to use."));
					return 0;
				}
				areas.markStorage(area, chest);
				what = "The harvest goes into that chest now, and into no other chest";
			}
			case "patrol" -> {
				areas.addPatrol(area, at);
				what = "Added a patrol point for the guardians (" + area.patrol.size() + " now)";
			}
			default -> {
				source.sendFailure(Component.literal("Pick one of: bed, commons, kitchen, farm, storage, patrol."));
				return 0;
			}
		}
		List<String> names = areas.residents(area).stream().map(c -> c.companionName().split(" ")[0]).toList();
		String folk = names.isEmpty() ? "" : " (" + String.join(", ", names) + ")";
		source.sendSuccess(() -> Component.literal(what + "." + folk).withStyle(ChatFormatting.GRAY), true);
		return 1;
	}

	/** {@code /companion places [name]}: what the home nearby has, as the companions see it. */
	private static int places(CommandContext<CommandSourceStack> ctx, String who) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		HomeAreas areas = HomeAreas.get(source.getServer());
		List<CompanionEntity> chosen = who == null ? List.of() : residentsCalled(player, who);
		HomeAreas.Area area = !chosen.isEmpty() ? areas.areaOf(chosen.get(0)) : nearbyArea(player, areas);
		if (area == null) {
			source.sendFailure(Component.literal("Nobody lives near here yet."));
			return 0;
		}
		ServerLevel level = (ServerLevel) player.level();
		Places places = areas.places(level, area);
		List<String> lines = new java.util.ArrayList<>();
		lines.add("Meeting spot: " + places.commons().toShortString() + (places.commonsFire() ? " (a campfire, lit at dusk)" : ""));
		lines.add("Kitchen: " + places.kitchen().toShortString());
		lines.add("Farm: " + (places.farm() == null ? "none yet (till some farmland nearby, or /companion setplace farm)"
				: places.farm().low().toShortString() + " to " + places.farm().high().toShortString() + (places.farmMarked() ? " (marked)" : " (found)")));
		lines.add("Storage: " + (places.storage().isEmpty() ? "none (stand by a chest and /companion setplace storage)" : places.storage().size() + " chest(s)"));
		lines.add("Patrol points: " + places.patrol().size());
		for (CompanionEntity resident : areas.residents(area)) {
			BlockPos bed = areas.bedFor(level, area, resident);
			lines.add(resident.companionName().split(" ")[0] + ": " + resident.order().describe() + ", " + resident.needs().mood()
					+ (bed == null ? ", no bed yet" : ", sleeps at " + bed.toShortString()));
		}
		source.sendSuccess(() -> Component.literal("Home around " + area.centrePos().toShortString() + ":").withStyle(ChatFormatting.YELLOW), false);
		for (String line : lines) {
			source.sendSuccess(() -> Component.literal("  " + line).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	/** Companions not travelling with anyone, called this or of this role ("farmers"), nearest first. */
	private static List<CompanionEntity> residentsCalled(ServerPlayer player, String who) {
		io.github.tekless.hearthbound.core.intent.GroupAddress.Result role = io.github.tekless.hearthbound.core.intent.GroupAddress.parse("@" + who);
		String wanted = who.toLowerCase(Locale.ROOT);
		return CompanionTracker.near(player, 256).stream()
				.filter(c -> !c.isRecruited())
				.filter(c -> role != null ? c.role() == role.role() : c.companionName().toLowerCase(Locale.ROOT).split(" ")[0].equals(wanted))
				.sorted(java.util.Comparator.comparingDouble(c -> c.distanceToSqr(player))).toList();
	}

	/** The home nearest the player: an area within 48 blocks, or that of the nearest companion living within 32. */
	private static HomeAreas.Area nearbyArea(ServerPlayer player, HomeAreas areas) {
		HomeAreas.Area area = areas.nearest(player.level().dimension().identifier().toString(), player.blockPosition(), 48);
		if (area != null) {
			return area;
		}
		return CompanionTracker.near(player, 32).stream().filter(c -> !c.isRecruited())
				.min(java.util.Comparator.comparingDouble(c -> c.distanceToSqr(player))).map(areas::areaOf).orElse(null);
	}

	private static BlockPos bedNear(ServerLevel level, BlockPos at) {
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 1, 2))) {
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
				boolean head = state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD;
				return head ? pos.immutable() : pos.relative(state.getValue(net.minecraft.world.level.block.BedBlock.FACING)).immutable();
			}
		}
		return null;
	}

	private static BlockPos chestNear(ServerLevel level, BlockPos at) {
		BlockPos best = null;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 1, 2))) {
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
			boolean chest = state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock || state.is(net.minecraft.world.level.block.Blocks.BARREL);
			if (chest && (best == null || pos.distSqr(at) < best.distSqr(at))) {
				best = pos.immutable();
			}
		}
		return best;
	}

	/** A party limit from "5", "0", "unlimited", "none"... or null if it isn't one. */
	private static Integer parseLimit(String value) {
		String v = value.toLowerCase(Locale.ROOT);
		if (v.equals("unlimited") || v.equals("none") || v.equals("off") || v.equals("infinite") || v.equals("no-limit")) {
			return 0;
		}
		try {
			return Math.max(0, Math.min(1000, Integer.parseInt(v)));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** {@code /companion sethome <who> [here|x y z]}. */
	private static int setHome(CommandContext<CommandSourceStack> ctx, BlockPos at) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Conversations.Targets targets = Conversations.commandable(player, StringArgumentType.getString(ctx, "who"));
		BlockPos pos = at != null ? at : player.blockPosition();
		targets.loaded().forEach(c -> Homes.set(c, pos));
		reportTargets(ctx.getSource(), targets, "Home set to " + pos.toShortString() + " for ");
		return targets.loaded().size();
	}

	/** {@code /companion home <who> [teleport]} and {@code /companion tphome <who>}. */
	private static int goHome(CommandContext<CommandSourceStack> ctx, boolean teleport) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Conversations.Targets targets = Conversations.commandable(player, StringArgumentType.getString(ctx, "who"));
		List<CompanionEntity> went = new java.util.ArrayList<>();
		List<String> missed = new java.util.ArrayList<>(targets.missed());
		for (CompanionEntity companion : targets.loaded()) {
			if (Homes.send(companion, teleport)) {
				went.add(companion);
			} else {
				missed.add(companion.companionName().split(" ")[0] + " (no home yet; use /companion sethome)");
			}
		}
		if (!went.isEmpty()) {
			went.get(0).say(teleport ? "ack.home.teleport" : "ack.home", java.util.Map.of());
		}
		reportTargets(ctx.getSource(), new Conversations.Targets(went, missed), (teleport ? "Teleported home: " : "Heading home: "));
		return went.size();
	}

	private static void reportTargets(CommandSourceStack source, Conversations.Targets targets, String prefix) {
		if (!targets.loaded().isEmpty()) {
			String names = String.join(", ", targets.loaded().stream().map(c -> c.companionName().split(" ")[0]).toList());
			source.sendSuccess(() -> Component.literal(prefix + names + "."), false);
		}
		if (!targets.missed().isEmpty()) {
			source.sendFailure(Component.literal("Could not reach: " + String.join(", ", targets.missed()) + "."));
		}
	}

	private static int summon(CommandContext<CommandSourceStack> ctx, String archetypeId, String name, String skinId, String model)
			throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		CompanionEntity companion = ModEntities.COMPANION.create(level, EntitySpawnReason.COMMAND);
		if (companion == null) {
			return 0;
		}
		companion.snapTo(player.getX() + player.getLookAngle().x * 2, player.getY(), player.getZ() + player.getLookAngle().z * 2, player.getYRot() + 180, 0);
		// Pick who they are first, so the starting gear suits the character (a random one is rolled otherwise).
		CastMember castMember = archetypeId == null ? null : CastMember.byName(archetypeId).orElse(null);
		if (castMember != null) {
			companion.initIdentity(castMember);
		} else if (archetypeId != null) {
			Archetype archetype = Archetype.byId(archetypeId).orElse(null);
			if (archetype == null) {
				ctx.getSource().sendFailure(Component.literal("Unknown character or archetype. Try a name (" + String.join(", ",
						Arrays.stream(CastMember.values()).map(CastMember::displayName).toList()) + ") or an archetype ("
						+ String.join(", ", Arrays.stream(Archetype.values()).map(Archetype::id).toList()) + ")."));
				return 0;
			}
			String chosenName = name != null ? capitalise(name) : archetype.names().get(level.getRandom().nextInt(archetype.names().size()));
			CompanionSkin skin = skinId != null ? CompanionSkin.byId(skinId).orElse(CompanionSkin.STEVE)
					: CompanionSkin.values()[level.getRandom().nextInt(CompanionSkin.values().length)];
			boolean slim = model != null ? model.equalsIgnoreCase("slim") : level.getRandom().nextBoolean();
			companion.initIdentity(archetype, chosenName, skin, slim);
		}
		companion.finalizeSpawn(level, level.getCurrentDifficultyAt(companion.blockPosition()), EntitySpawnReason.COMMAND, null);
		if (archetypeId != null) {
			companion.giveStarterGear();
		}
		level.addFreshEntity(companion);
		ctx.getSource().sendSuccess(() -> Component.literal("Summoned " + companion.companionName() + " the "
				+ companion.archetype().displayName() + ". Say \"@" + companion.companionName() + " join me\" to recruit them."), false);
		return 1;
	}

	private static int help(CommandContext<CommandSourceStack> ctx) {
		for (Component line : HelpText.lines()) {
			ctx.getSource().sendSuccess(() -> line, false);
		}
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		List<CompanionEntity> party = Conversations.party(player);
		if (party.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Texts.tr("message.hearthbound.no_party").withStyle(ChatFormatting.GRAY), false);
			return 0;
		}
		for (CompanionEntity companion : party) {
			ctx.getSource().sendSuccess(() -> Component.literal(companion.companionName())
					.withStyle(CompanionEntity.nameColour(companion.archetype().id()))
					.append(Component.literal(" (" + companion.archetype().displayName() + ") - " + companion.order().describe()
							+ String.format(Locale.ROOT, ", %.0f/%.0f health", companion.getHealth(), companion.getMaxHealth()))
							.withStyle(ChatFormatting.GRAY)), false);
		}
		return party.size();
	}

	private static int order(CommandContext<CommandSourceStack> ctx, Intent intent) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CompanionEntity companion = find(ctx, player);
		if (companion == null) {
			return 0;
		}
		return Conversations.execute(player, companion, intent) ? 1 : 0;
	}

	private static int say(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CompanionEntity companion = find(ctx, player);
		if (companion == null) {
			return 0;
		}
		Conversations.handleMessage(player, companion, StringArgumentType.getString(ctx, "message"));
		return 1;
	}

	private static int skin(CommandContext<CommandSourceStack> ctx, String model) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CompanionEntity companion = find(ctx, player);
		if (companion == null) {
			return 0;
		}
		if (!companion.isOwnedBy(player) && !Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(ctx.getSource())) {
			ctx.getSource().sendFailure(Component.literal(companion.isRecruited() ? "That companion travels with someone else."
					: "Only your own companions change outfits for you. Recruit them first."));
			return 0;
		}
		String skinId = StringArgumentType.getString(ctx, "skin");
		int variant;
		CastMember outfit = CastMember.byName(skinId).orElse(null);
		if (outfit != null) {
			variant = outfit.skinVariant();
		} else {
			CompanionSkin skin = CompanionSkin.byId(skinId).orElse(null);
			if (skin == null) {
				ctx.getSource().sendFailure(Component.literal("Unknown skin. Try a default skin (" + String.join(", ",
						Arrays.stream(CompanionSkin.values()).map(CompanionSkin::id).toList()) + ") or a cast outfit ("
						+ String.join(", ", Arrays.stream(CastMember.values()).map(CastMember::id).toList()) + ")."));
				return 0;
			}
			variant = skin.encode(model != null && model.equalsIgnoreCase("slim"));
		}
		companion.setSkinVariant(variant);
		ctx.getSource().sendSuccess(() -> Component.literal(companion.companionName() + " now wears " + CompanionSkin.describe(variant) + "."), false);
		return 1;
	}

	private static CompanionEntity find(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		String name = StringArgumentType.getString(ctx, "name");
		List<CompanionEntity> found = Conversations.nearbyNamed(player, name);
		if (found.isEmpty()) {
			ctx.getSource().sendFailure(Texts.tr("message.hearthbound.no_such_companion", name));
			return null;
		}
		return found.get(0);
	}

	private static String capitalise(String name) {
		return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase(Locale.ROOT);
	}
}

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
import io.github.tekless.hearthbound.world.CompanionTracker;
import io.github.tekless.hearthbound.world.HubResidents;
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
		root.then(Commands.literal("hub")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("list").executes(CompanionCommands::hubList))
				.then(Commands.literal("spawn").executes(CompanionCommands::hubSpawn))
				.then(Commands.literal("home")
						.then(Commands.argument("name", StringArgumentType.word()).suggests(NEARBY_NAMES)
								.executes(CompanionCommands::hubHome))));
		dispatcher.register(root);
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

	/** Sends a companion back to the hub, ending their travels with whoever had them. */
	private static int hubHome(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CompanionEntity companion = find(ctx, player);
		if (companion == null) {
			return 0;
		}
		HubResidents.hubPos().ifPresent(companion::makeHubResident);
		if (companion.isRecruited()) {
			companion.dismiss();
		}
		HubResidents.sendHome(companion);
		ctx.getSource().sendSuccess(() -> Component.literal(companion.companionName() + " is back at the hub."), true);
		return 1;
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
		companion.finalizeSpawn(level, level.getCurrentDifficultyAt(companion.blockPosition()), EntitySpawnReason.COMMAND, null);
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
		level.addFreshEntity(companion);
		ctx.getSource().sendSuccess(() -> Component.literal("Summoned " + companion.companionName() + " the "
				+ companion.archetype().displayName() + ". Say \"@" + companion.companionName() + " join me\" to recruit them."), false);
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

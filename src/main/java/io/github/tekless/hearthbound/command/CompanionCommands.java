package io.github.tekless.hearthbound.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import io.github.tekless.hearthbound.registry.ModEntities;
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
import net.minecraft.world.phys.AABB;

/**
 * {@code /companion ...} - a keyboard-friendly way to do everything the chat and UI can do, plus
 * {@code summon} for testing. Orders go through the same validation as chat.
 */
public final class CompanionCommands {
	private static final SuggestionProvider<CommandSourceStack> NEARBY_NAMES = (context, builder) -> {
		ServerPlayer player = context.getSource().getPlayer();
		if (player != null) {
			AABB box = player.getBoundingBox().inflate(64);
			player.level().getEntitiesOfClass(CompanionEntity.class, box, CompanionEntity::isAlive)
					.forEach(c -> builder.suggest(c.companionName().split(" ")[0]));
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
						.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Archetype.values()).map(Archetype::id), b))
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
								.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(CompanionSkin.values()).map(CompanionSkin::id), b))
								.then(Commands.argument("model", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("wide", "slim"), b))
										.executes(CompanionCommands::skin)))));
		dispatcher.register(root);
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
		if (archetypeId != null) {
			Archetype archetype = Archetype.byId(archetypeId).orElse(null);
			if (archetype == null) {
				ctx.getSource().sendFailure(Component.literal("Unknown archetype. Try: " + String.join(", ",
						Arrays.stream(Archetype.values()).map(Archetype::id).toList())));
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
			ctx.getSource().sendSuccess(() -> Component.translatable("message.hearthbound.no_party").withStyle(ChatFormatting.GRAY), false);
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

	private static int skin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CompanionEntity companion = find(ctx, player);
		if (companion == null) {
			return 0;
		}
		if (companion.isRecruited() && !companion.isOwnedBy(player)) {
			ctx.getSource().sendFailure(Component.literal("That companion travels with someone else."));
			return 0;
		}
		CompanionSkin skin = CompanionSkin.byId(StringArgumentType.getString(ctx, "skin")).orElse(null);
		if (skin == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown skin. Try: " + String.join(", ",
					Arrays.stream(CompanionSkin.values()).map(CompanionSkin::id).toList())));
			return 0;
		}
		boolean slim = StringArgumentType.getString(ctx, "model").equalsIgnoreCase("slim");
		companion.setSkinVariant(skin.encode(slim));
		ctx.getSource().sendSuccess(() -> Component.literal(companion.companionName() + " now uses " + skin.id() + " (" + (slim ? "slim" : "wide") + ")."), false);
		return 1;
	}

	private static CompanionEntity find(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		String name = StringArgumentType.getString(ctx, "name");
		List<CompanionEntity> found = Conversations.nearbyNamed(player, name);
		if (found.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("message.hearthbound.no_such_companion", name));
			return null;
		}
		return found.get(0);
	}

	private static String capitalise(String name) {
		return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase(Locale.ROOT);
	}
}

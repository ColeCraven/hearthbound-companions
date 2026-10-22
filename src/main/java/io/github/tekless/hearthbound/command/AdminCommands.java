package io.github.tekless.hearthbound.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.ai.AiSetup;
import io.github.tekless.hearthbound.config.HearthboundConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

/**
 * Host and operator settings: one-click AI setup ({@code /companion ai ...}) and who can hurt companions
 * ({@code /companion damage ...}, {@code /companion godmode ...}). Everything here is saved in config/hearthbound.json.
 */
final class AdminCommands {
	private AdminCommands() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("ai")
				.requires(CompanionCommands::mayConfigure)
				.executes(AdminCommands::aiStatus)
				.then(Commands.literal("status").executes(AdminCommands::aiStatus))
				.then(Commands.literal("off").executes(AdminCommands::aiOff))
				.then(Commands.literal("setup")
						.executes(AdminCommands::ollamaList)
						.then(Commands.literal("ollama")
								.executes(AdminCommands::ollamaList)
								.then(Commands.argument("model", StringArgumentType.greedyString())
										.executes(ctx -> ollamaUse(ctx, StringArgumentType.getString(ctx, "model")))))
						.then(Commands.literal("openai")
								.executes(ctx -> cloud(ctx, "openai", null))
								.then(Commands.argument("model", StringArgumentType.greedyString())
										.executes(ctx -> cloud(ctx, "openai", StringArgumentType.getString(ctx, "model")))))
						.then(Commands.literal("anthropic")
								.executes(ctx -> cloud(ctx, "anthropic", null))
								.then(Commands.argument("model", StringArgumentType.greedyString())
										.executes(ctx -> cloud(ctx, "anthropic", StringArgumentType.getString(ctx, "model")))))));
		root.then(Commands.literal("damage")
				.requires(CompanionCommands::mayConfigure)
				.executes(AdminCommands::damageStatus)
				.then(Commands.literal("players")
						.then(Commands.literal("on").executes(ctx -> damage(ctx, "players", true)))
						.then(Commands.literal("off").executes(ctx -> damage(ctx, "players", false))))
				.then(Commands.literal("others")
						.then(Commands.literal("on").executes(ctx -> damage(ctx, "others", true)))
						.then(Commands.literal("off").executes(ctx -> damage(ctx, "others", false)))));
		root.then(Commands.literal("godmode")
				.requires(CompanionCommands::mayConfigure)
				.executes(AdminCommands::damageStatus)
				.then(Commands.literal("on").executes(ctx -> damage(ctx, "godmode", true)))
				.then(Commands.literal("off").executes(ctx -> damage(ctx, "godmode", false))));
	}

	private static boolean allowed(CommandContext<CommandSourceStack> ctx) {
		if (!CompanionCommands.isAdmin(ctx.getSource())) {
			ctx.getSource().sendFailure(Component.literal("Only the host or an operator can change that."));
			return false;
		}
		return true;
	}

	private static void send(CommandSourceStack source, List<String> lines) {
		for (String line : lines) {
			source.sendSuccess(() -> Component.literal(line), false);
		}
	}

	/** Lines that arrive later (a test reply), sent on the server thread. */
	private static void later(CommandSourceStack source, java.util.concurrent.CompletableFuture<List<String>> lines) {
		MinecraftServer server = source.getServer();
		lines.thenAccept(result -> server.execute(() -> send(source, result)));
	}

	// ------------------------------------------------------------------ AI

	private static int aiStatus(CommandContext<CommandSourceStack> ctx) {
		send(ctx.getSource(), AiSetup.status());
		return 1;
	}

	private static int aiOff(CommandContext<CommandSourceStack> ctx) {
		if (!allowed(ctx)) {
			return 0;
		}
		send(ctx.getSource(), AiSetup.off(ctx.getSource().getServer()));
		return 1;
	}

	/** Step one: what has the local Ollama got? Each model is a click away. */
	private static int ollamaList(CommandContext<CommandSourceStack> ctx) {
		if (!allowed(ctx)) {
			return 0;
		}
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		source.sendSuccess(() -> Component.literal("Looking for Ollama at " + AiSetup.OLLAMA + "..."), false);
		AiSetup.findOllamaModels().thenAccept(found -> server.execute(() -> {
			if (found.problem().isPresent()) {
				source.sendSuccess(() -> Component.literal(found.problem().get()), false);
				return;
			}
			MutableComponent line = Component.literal("Pick a model: ");
			for (String model : found.models()) {
				String command = "/companion ai setup ollama " + model;
				line.append(Component.literal("[" + model + "]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand(command))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(command)))));
				line.append(" ");
			}
			source.sendSuccess(() -> line, false);
			source.sendSuccess(() -> Component.literal("(or type /companion ai setup ollama <model>)").withStyle(ChatFormatting.GRAY), false);
		}));
		return 1;
	}

	private static int ollamaUse(CommandContext<CommandSourceStack> ctx, String model) {
		if (!allowed(ctx)) {
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Switching to Ollama " + model.trim() + " and asking for one test reply..."), false);
		later(ctx.getSource(), AiSetup.useOllama(ctx.getSource().getServer(), model));
		return 1;
	}

	private static int cloud(CommandContext<CommandSourceStack> ctx, String provider, String model) {
		if (!allowed(ctx)) {
			return 0;
		}
		AiSetup.Cloud result = AiSetup.cloud(ctx.getSource().getServer(), provider, model);
		send(ctx.getSource(), result.now());
		if (result.test() != null) {
			later(ctx.getSource(), result.test());
		}
		return 1;
	}

	// ------------------------------------------------------------------ damage

	private static int damageStatus(CommandContext<CommandSourceStack> ctx) {
		send(ctx.getSource(), damageLines());
		return 1;
	}

	static List<String> damageLines() {
		HearthboundConfig.Server server = Hearthbound.config().server;
		return List.of(
				"Players hurting companions: " + (server.playersCanHurt ? "on (your own hits only sting)" : "off") + "  /companion damage players on|off",
				"Other people's companions: " + (server.otherPlayersCanHurt ? "can be hurt" : "can't be hurt") + "  /companion damage others on|off",
				"God mode (companions take no damage at all): " + (server.godmode ? "on" : "off") + "  /companion godmode on|off",
				"Knocked down instead of killed: " + (Hearthbound.config().essentialCompanions ? "on" : "off") + " (essentialCompanions in config/hearthbound.json)");
	}

	private static int damage(CommandContext<CommandSourceStack> ctx, String what, boolean on) {
		if (!allowed(ctx)) {
			return 0;
		}
		HearthboundConfig config = Hearthbound.config();
		switch (what) {
			case "players" -> config.server.playersCanHurt = on;
			case "others" -> config.server.otherPlayersCanHurt = on;
			default -> config.server.godmode = on;
		}
		config.save();
		send(ctx.getSource(), damageLines());
		return 1;
	}
}

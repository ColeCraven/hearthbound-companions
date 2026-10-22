package io.github.tekless.hearthbound.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.tekless.hearthbound.blueprint.Blueprints;
import io.github.tekless.hearthbound.blueprint.SavedBuild;
import io.github.tekless.hearthbound.core.blueprint.Layout;
import io.github.tekless.hearthbound.entity.order.Supply;
import io.github.tekless.hearthbound.world.Obey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /companion blueprint corner1|corner2|save <name>|list|info <name>|delete <name>}: saving something you built
 * so companions can put it up again. Mark two opposite corners (look at the block, or stand on it), face the front of
 * the build the way you'd like to see it built, and save it with a name.
 */
final class BlueprintCommands {
	private static final SuggestionProvider<CommandSourceStack> NAMES = (context, builder) ->
			SharedSuggestionProvider.suggest(context.getSource().getServer() == null ? List.of() : Blueprints.names(context.getSource().getServer()), builder);

	private BlueprintCommands() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("blueprint")
				.executes(BlueprintCommands::list)
				.then(Commands.literal("corner1").executes(ctx -> corner(ctx, true)))
				.then(Commands.literal("corner2").executes(ctx -> corner(ctx, false)))
				.then(Commands.literal("save").then(Commands.argument("name", StringArgumentType.greedyString()).executes(BlueprintCommands::save)))
				.then(Commands.literal("list").executes(BlueprintCommands::list))
				.then(Commands.literal("info").then(Commands.argument("name", StringArgumentType.word()).suggests(NAMES).executes(BlueprintCommands::info)))
				.then(Commands.literal("delete").then(Commands.argument("name", StringArgumentType.word()).suggests(NAMES).executes(BlueprintCommands::delete))));
	}

	/** The block the player looks at (within 8 blocks), else the one they stand on. */
	static BlockPos target(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(8));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.BLOCK) {
			return hit.getBlockPos().immutable();
		}
		return player.blockPosition().below();
	}

	private static int corner(CommandContext<CommandSourceStack> ctx, boolean first) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		BlockPos pos = target(player);
		Blueprints.Corners corners = Blueprints.mark(player.getUUID(), player.level().dimension().identifier().toString(), pos, first);
		String size = "";
		if (corners.first() != null && corners.second() != null) {
			BlockPos a = corners.first();
			BlockPos b = corners.second();
			size = " The box is " + (Math.abs(a.getX() - b.getX()) + 1) + " by " + (Math.abs(a.getZ() - b.getZ()) + 1) + ", "
					+ (Math.abs(a.getY() - b.getY()) + 1) + " high. Face the front of it and use /companion blueprint save <name>.";
		} else {
			size = " Now mark the opposite corner with /companion blueprint " + (first ? "corner2" : "corner1") + ".";
		}
		String said = (first ? "First" : "Second") + " corner at " + pos.toShortString() + "." + size;
		ctx.getSource().sendSuccess(() -> Component.literal(said).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int save(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		Optional<String> name = Layout.cleanName(StringArgumentType.getString(ctx, "name"));
		if (name.isEmpty()) {
			source.sendFailure(Component.literal("Give it a name with letters or numbers in it, like \"cottage\"."));
			return 0;
		}
		Blueprints.Corners corners = Blueprints.corners(player.getUUID());
		if (corners.first() == null || corners.second() == null || !corners.dimension().equals(player.level().dimension().identifier().toString())) {
			source.sendFailure(Component.literal("Mark both corners first: look at one corner block and use /companion blueprint corner1, then the opposite one with corner2."));
			return 0;
		}
		BlockPos a = corners.first();
		BlockPos b = corners.second();
		Optional<String> tooBig = Layout.sizeProblem(Math.abs(a.getX() - b.getX()) + 1, Math.abs(a.getY() - b.getY()) + 1, Math.abs(a.getZ() - b.getZ()) + 1);
		if (tooBig.isPresent()) {
			source.sendFailure(Component.literal("That's " + tooBig.get() + ". Pick a smaller box."));
			return 0;
		}
		Optional<SavedBuild> existing = Blueprints.isOwn(source.getServer(), name.get()) ? Blueprints.load(source.getServer(), name.get()) : Optional.empty();
		if (existing.isPresent() && !existing.get().author().equals(player.getName().getString()) && !Obey.mayUse(player)) {
			source.sendFailure(Component.literal(existing.get().author() + " already saved a blueprint called " + Layout.spoken(name.get()) + ". Pick another name."));
			return 0;
		}
		SavedBuild build = SavedBuild.capture((ServerLevel) player.level(), a, b, player.getDirection(), name.get(), player.getName().getString());
		if (build.pieces().isEmpty()) {
			source.sendFailure(Component.literal("There's nothing in that box a companion could build: no blocks, or only things like water and grass."));
			return 0;
		}
		if (build.pieces().size() >= Layout.MAX_BLOCKS) {
			source.sendFailure(Component.literal("That's more than " + Layout.MAX_BLOCKS + " blocks. Pick a smaller box."));
			return 0;
		}
		if (!Blueprints.save(source.getServer(), build)) {
			source.sendFailure(Component.literal("Couldn't write the blueprint file. The server log says why."));
			return 0;
		}
		String spoken = Layout.spoken(build.name());
		source.sendSuccess(() -> Component.literal("Saved \"" + spoken + "\": " + describe(build) + ". Say \"@Name build my " + spoken
				+ " here\" or \"builders, build " + spoken + "\". It goes up in front of you, facing the way you face.").withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static String describe(SavedBuild build) {
		Map<Item, Integer> top = new LinkedHashMap<>();
		build.materials().entrySet().stream().sorted(Map.Entry.<Item, Integer>comparingByValue().reversed()).limit(4)
				.forEach(e -> top.put(e.getKey(), e.getValue()));
		int others = build.materials().size() - top.size();
		return build.size().getX() + " by " + build.size().getZ() + ", " + build.size().getY() + " high, " + build.pieces().size() + " blocks ("
				+ Supply.describe(top) + (others > 0 ? " and " + others + " other kind" + (others == 1 ? "" : "s") : "") + ")";
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		List<String> names = Blueprints.names(source.getServer());
		if (names.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No blueprints yet. Mark two corners of something you built with /companion blueprint corner1 and corner2, "
					+ "then /companion blueprint save <name>. Structure files (.nbt) in config/hearthbound/blueprints, or saved by a structure block, work too.")
					.withStyle(ChatFormatting.GRAY), false);
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Blueprints: " + String.join(", ", names.stream()
				.map(n -> Layout.spoken(n) + (Blueprints.isOwn(source.getServer(), n) ? "" : " (structure file)")).toList()) + ".").withStyle(ChatFormatting.GRAY), false);
		return names.size();
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		Optional<SavedBuild> build = Layout.cleanName(name).flatMap(n -> Blueprints.load(source.getServer(), n));
		if (build.isEmpty()) {
			source.sendFailure(Component.literal("No blueprint called " + name + ". /companion blueprint list shows them."));
			return 0;
		}
		SavedBuild b = build.get();
		source.sendSuccess(() -> Component.literal(Layout.spoken(b.name()) + ": " + describe(b) + (b.author().isEmpty() ? "" : ", saved by " + b.author())
				+ ". It needs " + Supply.describe(b.materials()) + ".").withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int delete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		String name = Layout.cleanName(StringArgumentType.getString(ctx, "name")).orElse("");
		Optional<SavedBuild> build = Blueprints.isOwn(source.getServer(), name) ? Blueprints.load(source.getServer(), name) : Optional.empty();
		if (build.isEmpty()) {
			source.sendFailure(Component.literal("No saved blueprint called " + name + ". Structure files are left alone; remove those by hand."));
			return 0;
		}
		ServerPlayer player = source.getPlayer();
		boolean allowed = player == null ? Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)
				: build.get().author().equals(player.getName().getString()) || Obey.mayUse(player);
		if (!allowed) {
			source.sendFailure(Component.literal("Only " + build.get().author() + " (or the host or an operator) can delete that one."));
			return 0;
		}
		Blueprints.delete(source.getServer(), name);
		source.sendSuccess(() -> Component.literal("Deleted the blueprint " + Layout.spoken(name) + ".").withStyle(ChatFormatting.GRAY), false);
		return 1;
	}
}

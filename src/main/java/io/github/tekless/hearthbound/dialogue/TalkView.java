package io.github.tekless.hearthbound.dialogue;

import io.github.tekless.hearthbound.blueprint.Blueprints;
import io.github.tekless.hearthbound.core.blueprint.Layout;
import io.github.tekless.hearthbound.core.memory.CompanionMemory;
import io.github.tekless.hearthbound.crafting.RecipeBook;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.order.ChestOrder;
import io.github.tekless.hearthbound.entity.order.CompanionOrder;
import io.github.tekless.hearthbound.network.TalkPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The server side of the conversation screen: what it shows about a companion (who they are, how they are, what they're
 * doing, what's been said), a few things worth saying right now, and what happens to a line typed into it (the same as
 * saying it in chat to that companion, without it going out to everyone).
 */
public final class TalkView {
	/** How close a companion who isn't yours has to be before the screen talks to them. */
	private static final double RANGE = 16;

	private TalkView() {
	}

	/** Yours, wherever they are in this dimension, or anyone else's (or no one's) close by. */
	public static boolean canTalk(ServerPlayer player, @Nullable Entity entity) {
		return entity instanceof CompanionEntity companion && companion.isAlive() && companion.level() == player.level()
				&& (companion.isOwnedBy(player) || companion.distanceTo(player) <= RANGE);
	}

	public static TalkPayloads.State state(ServerPlayer player, CompanionEntity companion) {
		String need = companion.needs().pressing();
		String task = companion.isDowned() ? "knocked down" : companion.isSleeping() ? "asleep" : companion.order().describe();
		TalkPayloads.Header header = new TalkPayloads.Header(companion.getId(), companion.companionName(), companion.archetype().id(),
				companion.role().label(), companion.needs().mood(), need == null ? "nothing pressing" : need, task);
		List<TalkPayloads.Line> lines = new ArrayList<>();
		for (CompanionMemory.Line line : companion.memory().lines()) {
			lines.add(new TalkPayloads.Line(line.fromPlayer(), clip(line.text(), 240)));
		}
		return new TalkPayloads.State(header, lines.size() > 32 ? lines.subList(lines.size() - 32, lines.size()) : lines, suggestions(player, companion));
	}

	/**
	 * A few things worth saying to them right now, as plain sentences the parser understands: join me, a chest you're
	 * looking at, something in their pack to smelt or hand over, a saved build, home at night.
	 */
	public static List<String> suggestions(ServerPlayer player, CompanionEntity companion) {
		List<String> out = new ArrayList<>();
		if (companion.ownerId() == null) {
			out.add("Will you join me?");
			return out;
		}
		if (!companion.isOwnedBy(player)) {
			return out;
		}
		ServerLevel level = (ServerLevel) companion.level();
		if (lookedAtContainer(player) != null) {
			out.add(companion.inventory().isEmpty() ? "Take everything out of that chest" : "Put everything in that chest");
		}
		Map<Item, Integer> carried = new HashMap<>();
		boolean food = false;
		String smelt = null;
		for (ItemStack stack : companion.inventory().getItems()) {
			if (stack.isEmpty()) {
				continue;
			}
			carried.merge(stack.getItem(), stack.getCount(), Integer::sum);
			food |= stack.has(DataComponents.FOOD);
			if (smelt == null && stack.getItem() != net.minecraft.world.item.Items.COBBLESTONE && RecipeBook.smeltingOf(level, stack).isPresent()) {
				smelt = words(stack.getItem());
			}
		}
		if (food && (player.getFoodData().getFoodLevel() < 14 || player.getHealth() < player.getMaxHealth() / 2)) {
			out.add("Give me some food");
		}
		if (smelt != null) {
			out.add("Smelt the " + smelt);
		}
		carried.entrySet().stream().max(Map.Entry.comparingByValue())
				.ifPresent(most -> out.add("Give me the " + words(most.getKey())));
		List<String> builds = Blueprints.names(level.getServer());
		if (!builds.isEmpty() && companion.order().kind() != CompanionOrder.Kind.PROJECT) {
			out.add("Build my " + Layout.spoken(builds.get(0)) + " here");
		}
		long time = level.getOverworldClockTime() % 24000;
		if (time > 12500 && time < 23000 && companion.home() != null) {
			out.add("Go home");
		}
		return out.size() > 4 ? out.subList(0, 4) : out;
	}

	/** A line from the screen: the same as saying it to them in chat. */
	public static boolean say(ServerPlayer player, CompanionEntity companion, String text) {
		String line = text == null ? "" : text.strip();
		if (line.isEmpty() || !canTalk(player, companion)) {
			return false;
		}
		Conversations.handleMessage(player, companion, clip(line, 256));
		return true;
	}

	private static @Nullable BlockPos lookedAtContainer(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(player.getViewVector(1.0F).scale(6)), ClipContext.Block.OUTLINE,
				ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK && ChestOrder.containerAt((ServerLevel) player.level(), hit.getBlockPos()) != null ? hit.getBlockPos() : null;
	}

	private static String words(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getPath().replace('_', ' ').toLowerCase(Locale.ROOT);
	}

	private static String clip(String text, int max) {
		return text.length() <= max ? text : text.substring(0, max);
	}
}

package io.github.tekless.hearthbound.life;

import io.github.tekless.hearthbound.core.persona.Role;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.order.Crafting;
import io.github.tekless.hearthbound.entity.order.FarmOrder;
import io.github.tekless.hearthbound.entity.order.GatherOrder;
import io.github.tekless.hearthbound.entity.order.ProjectOrder;
import io.github.tekless.hearthbound.entity.order.Projects;
import io.github.tekless.hearthbound.entity.order.Routines;
import io.github.tekless.hearthbound.world.GatherCatalog;
import io.github.tekless.hearthbound.world.WorldRules;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * What someone living in a brand-new world does about what isn't there yet, with autonomy on: a builder with no
 * roof over home puts up a small shelter (chopping wood for it if they must), and a farmer with seeds starts a small
 * plot by the nearest water (chopping a couple of logs for a hoe first, if they have to). Each is tried at most
 * every few days, so nobody keeps at something that can't work. When something can't be done, or autonomy is off,
 * they say what they'd need instead, at most once a day, and get on with something else.
 */
final class Initiative {
	private Initiative() {
	}

	/** A builder with nothing over their head and no beds around: a shelter of their own. True if they started one. */
	static boolean shelter(CompanionEntity c, ServerLevel level, HomeAreas.Area area, BlockPos home, long day) {
		if (c.role() != Role.BUILDER || area.isHub() || !area.found.beds().isEmpty() || !level.canSeeSky(home)
				|| WorldRules.isProtected(level, home) || coveredNear(level, home) || !c.tryEvery("own_shelter", day, 3)) {
			return false;
		}
		if (!Routines.enabled()) {
			mention(c, level, day, "a roof over our heads");
			return false;
		}
		ProjectOrder order = Projects.ownShelter(level, home);
		if (order == null) {
			mention(c, level, day, "a flat, clear bit of ground for a shelter");
			return false;
		}
		c.sayMaybe("life.self.shelter", Map.of(), 1.0);
		c.setOrder(order);
		return true;
	}

	/** A farmer with no farm: seeds, a hoe, water close by, then a small plot. True if they started on it. */
	static boolean farm(CompanionEntity c, ServerLevel level, Places places, BlockPos home, long day) {
		if (c.role() != Role.FARMER || places.farm() != null || !c.tryEvery("own_farm", day, 2)) {
			return false;
		}
		if (!Routines.enabled()) {
			mention(c, level, day, "a bit of farmland");
			return false;
		}
		if (c.countCarried(s -> s.is(Items.WHEAT_SEEDS) || s.is(Items.CARROT) || s.is(Items.POTATO) || s.is(Items.BEETROOT_SEEDS)) == 0) {
			mention(c, level, day, "some seeds");
			return false;
		}
		boolean hoe = c.countCarried(s -> s.is(ItemTags.HOES)) > 0 || c.getMainHandItem().is(ItemTags.HOES);
		if (!hoe) {
			Crafting.planksFromLogs(c);
			hoe = Crafting.hoe(c);
		}
		if (!hoe) {
			// Two logs make a hoe: go and get them, if there's a tree about.
			if (GatherCatalog.resolve("logs") instanceof GatherCatalog.Resolution.Gatherable logs && !WorldRules.isProtected(level, home)) {
				c.sayMaybe("life.self.tools", Map.of(), 1.0);
				c.setOrder(new GatherOrder(c, logs.spec(), 2).keepForSelf());
				return true;
			}
			mention(c, level, day, "a hoe");
			return false;
		}
		boolean bucket = c.countCarried(s -> s.is(Items.WATER_BUCKET)) > 0;
		FarmOrder.Plot plot = FarmOrder.plan(level, home, bucket);
		if (plot == null) {
			mention(c, level, day, FarmOrder.waterNear(level, home) ? "a flat bit of ground by the water for a farm" : "water close by, for a farm");
			return false;
		}
		c.sayMaybe("life.self.farm", Map.of(), 1.0);
		c.setOrder(new FarmOrder(plot, 0, 1, null));
		return true;
	}

	/** Bedtime with no bed: they say so (once a day) and doze somewhere dry. */
	static void noBed(CompanionEntity c, ServerLevel level, long day) {
		mention(c, level, day, "a bed");
	}

	/** Produce in the pack and no storage chest at home: they say so, and a hint says how to give them one. */
	static void noStorage(CompanionEntity c, ServerLevel level, long day) {
		if (mention(c, level, day, "a chest to put the harvest in")) {
			Player near = level.getNearestPlayer(c, 16.0);
			if (near instanceof ServerPlayer player) {
				player.sendSystemMessage(Component.literal("Tip: stand next to a chest and type /companion setplace storage to make it theirs.")
						.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		}
	}

	/** Says what they need, once a day per need, and only when someone is close enough to hear. True if it was said. */
	static boolean mention(CompanionEntity c, ServerLevel level, long day, String thing) {
		if (level.getNearestPlayer(c, 16.0) == null || !c.tryEvery("need:" + thing, day, 1)) {
			return false;
		}
		c.say("life.need", Map.of("thing", thing));
		return true;
	}

	/** Somewhere with a roof within a few blocks of home (a cave, an overhang, someone's hut). */
	private static boolean coveredNear(ServerLevel level, BlockPos home) {
		for (int r = 0; r <= 6; r += 3) {
			for (int i = 0; i < 8; i++) {
				double angle = Math.PI * 2 * i / 8;
				BlockPos spot = PlaceScan.standableNear(level, home.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r)), 3);
				if (spot != null && !level.canSeeSky(spot)) {
					return true;
				}
			}
		}
		return false;
	}
}

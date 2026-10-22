package io.github.tekless.hearthbound.life;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.core.life.Activity;
import io.github.tekless.hearthbound.core.life.Bonds;
import io.github.tekless.hearthbound.core.life.Schedule;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.order.HomeOrder;
import io.github.tekless.hearthbound.world.Exchanges;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Runs once a second for every home where somebody lives and is loaded: keeps the home's clock (so time spent
 * unloaded can be caught up honestly when people come back), lights the meeting fire at dusk and puts it out in
 * the morning, starts the group moments (a shared meal, a story by the fire, the full-moon festival), lets
 * friendships grow, and hands out the "while you were away" news.
 */
public final class LifeTicker {
	/** A gap this long (two minutes) with nobody home loaded means there's time to catch up. */
	static final long CATCH_UP_AFTER = 2400;
	private static final long MOMENT_GAP = 1800;
	private static final Map<String, Long> LAST_MOMENT = new HashMap<>();
	private static final Map<String, Long> LAST_BOND = new HashMap<>();

	private LifeTicker() {
	}

	public static void tick(MinecraftServer server) {
		HomeAreas areas = HomeAreas.get(server);
		Map<HomeAreas.Area, List<CompanionEntity>> live = new LinkedHashMap<>();
		for (CompanionEntity companion : io.github.tekless.hearthbound.world.CompanionTracker.all()) {
			if (DailyRoutine.enabledFor(companion)) {
				live.computeIfAbsent(areas.areaOf(companion), a -> new ArrayList<>()).add(companion);
			}
		}
		for (Map.Entry<HomeAreas.Area, List<CompanionEntity>> entry : live.entrySet()) {
			HomeAreas.Area area = entry.getKey();
			List<CompanionEntity> residents = entry.getValue();
			ServerLevel level = (ServerLevel) residents.get(0).level();
			long now = level.getGameTime();
			if (area.lastLive >= 0 && now - area.lastLive > CATCH_UP_AFTER) {
				CatchUp.run(level, areas, area, residents, now - area.lastLive, new Random(now ^ area.id.hashCode()));
			}
			areas.touch(area, now);
			Places places = areas.places(level, area);
			fire(level, areas, area, places, residents);
			moments(level, area, places, residents);
			bonds(level, areas, area, residents);
			deliverNews(level, area, areas);
		}
		if (server.getTickCount() % 1200 == 0 && !live.isEmpty()) {
			areas.markDirty(); // keep the homes' clocks on disk about once a minute
		}
		if (server.getTickCount() % 200 == 0) {
			areas.save();
		}
	}

	/** The meeting fire: lit at dusk by someone at the meeting spot, put out in the morning by whoever lit it. */
	private static void fire(ServerLevel level, HomeAreas areas, HomeAreas.Area area, Places places, List<CompanionEntity> residents) {
		if (!places.commonsFire() || !level.hasChunkAt(places.commons())) {
			return;
		}
		BlockState state = level.getBlockState(places.commons());
		if (!state.hasProperty(CampfireBlock.LIT)) {
			return;
		}
		long dayTime = Math.floorMod(residents.get(0).clockTime(), (long) Schedule.DAY);
		boolean lit = state.getValue(CampfireBlock.LIT);
		boolean evening = dayTime >= Schedule.DUSK && dayTime < 22000;
		if (!lit && evening) {
			CompanionEntity lighter = residents.stream().filter(c -> activity(c) == Activity.SOCIAL && c.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(places.commons())) < 36)
					.findFirst().orElse(null);
			if (lighter != null) {
				level.setBlockAndUpdate(places.commons(), state.setValue(CampfireBlock.LIT, true));
				lighter.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				area.litFire = true;
				areas.markDirty();
				if (level.getNearestPlayer(lighter, 16.0) != null) {
					lighter.sayMaybe("life.fire", Map.of(), 0.5);
				}
			}
		} else if (lit && area.litFire && dayTime >= 0 && dayTime < 3000) {
			CompanionEntity up = residents.stream().filter(c -> activity(c) != Activity.SLEEP && activity(c) != null).findFirst().orElse(null);
			if (up != null) {
				level.setBlockAndUpdate(places.commons(), state.setValue(CampfireBlock.LIT, false));
				area.litFire = false;
				areas.markDirty();
			}
		}
	}

	/** A shared meal, a story by the fire, the full-moon festival: started now and then when people are gathered and someone's there to hear. */
	private static void moments(ServerLevel level, HomeAreas.Area area, Places places, List<CompanionEntity> residents) {
		long now = level.getGameTime();
		double chatter = Hearthbound.config().ambientChatter;
		if (chatter <= 0 || now - LAST_MOMENT.getOrDefault(area.id, -MOMENT_GAP * 2) < MOMENT_GAP / Math.max(0.25, chatter)) {
			return;
		}
		List<ServerPlayer> listeners = HomeAreas.playersNear(level, places.commons(), 24);
		if (listeners.isEmpty()) {
			return;
		}
		long clock = residents.get(0).clockTime();
		long dayTime = Math.floorMod(clock, (long) Schedule.DAY);
		List<CompanionEntity> eating = gathered(residents, Activity.MEAL, places.kitchen(), 8);
		List<CompanionEntity> social = gathered(residents, Activity.SOCIAL, places.commons(), 8);
		Exchanges.Topic topic = null;
		List<CompanionEntity> group = List.of();
		if (eating.size() >= 2) {
			topic = Exchanges.Topic.MEAL;
			group = eating;
		} else if (social.size() >= 2 && Schedule.fullMoon(clock) && dayTime >= Schedule.DUSK) {
			topic = Exchanges.Topic.FESTIVAL;
			group = social;
		} else if (social.size() >= 2 && dayTime >= Schedule.NIGHT) {
			topic = Exchanges.Topic.STORY;
			group = social;
		}
		if (topic == null) {
			return;
		}
		LAST_MOMENT.put(area.id, now);
		Random random = new Random(now);
		CompanionEntity a = group.get(random.nextInt(group.size()));
		List<CompanionEntity> others = group.stream().filter(c -> c != a).toList();
		CompanionEntity b = others.get(random.nextInt(others.size()));
		Exchanges.start(listeners.get(0), a, b, topic);
		if (topic == Exchanges.Topic.FESTIVAL) {
			group.forEach(CompanionEntity::hop);
		}
	}

	/** Time together at meals and in the evening brings people closer (or, for a few, gets on their nerves). */
	private static void bonds(ServerLevel level, HomeAreas areas, HomeAreas.Area area, List<CompanionEntity> residents) {
		long now = level.getGameTime();
		if (now - LAST_BOND.getOrDefault(area.id, -600L) < 600 || residents.size() < 2) {
			return;
		}
		LAST_BOND.put(area.id, now);
		Random random = new Random(now ^ 0x5DEECE66DL);
		for (int i = 0; i < residents.size(); i++) {
			for (int j = i + 1; j < residents.size(); j++) {
				CompanionEntity a = residents.get(i);
				CompanionEntity b = residents.get(j);
				Activity together = activity(a);
				if (together != activity(b) || (together != Activity.MEAL && together != Activity.SOCIAL && together != Activity.WORK) || a.distanceTo(b) > 6) {
					continue;
				}
				areas.growBond(a.getUUID(), b.getUUID(), Bonds.afterTimeTogether(a.archetype().id(), b.archetype().id(), random));
			}
		}
	}

	/** Hands the "while you were away" news to the first player who comes by. */
	private static void deliverNews(ServerLevel level, HomeAreas.Area area, HomeAreas areas) {
		if (area.pendingSummary == null) {
			return;
		}
		List<ServerPlayer> near = HomeAreas.playersNear(level, area.centrePos(), 48);
		if (near.isEmpty()) {
			return;
		}
		Component news = Component.literal(area.pendingSummary).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
		near.forEach(player -> player.sendSystemMessage(news));
		area.pendingSummary = null;
		areas.markDirty();
	}

	private static List<CompanionEntity> gathered(List<CompanionEntity> residents, Activity activity, BlockPos at, double range) {
		return residents.stream().filter(c -> activity(c) == activity && !c.isSleeping() && c.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at)) < range * range)
				.toList();
	}

	static Activity activity(CompanionEntity companion) {
		return companion.order() instanceof HomeOrder home ? home.activity() : null;
	}

	/** Forgets the timers (the world closed). */
	public static void reset() {
		LAST_MOMENT.clear();
		LAST_BOND.clear();
		Initiative.reset();
	}
}

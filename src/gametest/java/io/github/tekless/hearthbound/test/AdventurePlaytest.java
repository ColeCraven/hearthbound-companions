package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.order.CompanionOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;

/**
 * A scripted playtest in a normally generated world (real terrain, mobs, day/night): follow across
 * terrain, gather from natural trees, fight at night, interrupt an order, travel to the Nether, and
 * finally save, quit and reload. Prints a report to the log ("[Playtest]") and fails on broken rules.
 */
@SuppressWarnings("UnstableApiUsage")
public class AdventurePlaytest implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();

	private record Snapshot(String name, String archetype, int skin, String order, String baseMode, int logs, boolean sword,
			int lines, double distance, String lastLine, boolean downed, float health, String dimension) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		List<Snapshot> beforeQuit;
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("hearthbound"))
				.create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p resistance 99999 4 true");
			server.runCommand("time set 1000");
			server.runCommand("execute at @p run summon hearthbound:companion ~2 ~ ~2 {Archetype:\"sellsword\",Skin:6,CustomName:\"Bram\",CustomNameVisible:1b}");
			server.runCommand("execute at @p run summon hearthbound:companion ~-2 ~ ~2 {Archetype:\"naturalist\",Skin:10,CustomName:\"Wren\",CustomNameVisible:1b}");
			context.waitTicks(10);
			say(server, "Bram", "join me");
			say(server, "Wren", "will you join me?");
			context.waitTicks(20);
			check(snapshots(server).stream().allMatch(s -> s.order().equals("FOLLOW")), "both recruited and following");

			// 1. Follow across natural terrain in hops.
			int teleports = 0;
			for (int hop = 0; hop < 6; hop++) {
				server.runCommand("execute at @p run spreadplayers ~10 ~4 0 2 false @p");
				context.waitTicks(100);
				for (Snapshot s : snapshots(server)) {
					report.add(String.format(Locale.ROOT, "hop %d: %s at %.1f blocks", hop, s.name(), s.distance()));
					if (s.distance() > 12) {
						teleports++;
					}
				}
			}
			check(snapshots(server).stream().allMatch(s -> s.distance() < 14), "followers kept up across terrain");
			context.takeScreenshot("playtest-terrain");
			report.add("followers further than 12 blocks after a hop: " + teleports + " of 12 checks");

			// 2. Gather from whatever trees the world has.
			int logsBefore = playerLogs(server);
			say(server, "Bram", "chop 4 logs");
			server.waitFor(s -> snapshot(s, "Bram").map(b -> !b.order().equals("GATHER")).orElse(true), 2400);
			Snapshot bram = snapshots(server).stream().filter(s -> s.name().equals("Bram")).findFirst().orElseThrow();
			int gained = playerLogs(server) - logsBefore;
			report.add("gather: last line '" + bram.lastLine() + "', player gained " + gained + " logs");
			check(!bram.lastLine().equals("done.delivered") || gained >= 4, "completion is only claimed when 4 logs were delivered");
			check(gained == 0 || bram.lastLine().equals("done.delivered") || bram.lastLine().equals("partial.gather")
					|| bram.lastLine().equals("fail.inventory_full"), "items handed over are always announced truthfully");
			context.takeScreenshot("playtest-gather");

			// 3. Night fight.
			server.runCommand("time set 14000");
			server.runCommand("execute at @p run summon zombie ~6 ~ ~");
			server.runCommand("execute at @p run summon zombie ~-6 ~ ~3");
			server.runCommand("execute at @p run summon zombie ~0 ~ ~-6");
			context.waitTicks(60);
			context.takeScreenshot("playtest-night-fight");
			context.waitTicks(400);
			int zombies = server.computeOnServer(s -> countZombies(s));
			for (Snapshot s : snapshots(server)) {
				report.add(String.format(Locale.ROOT, "after fight: %s health %.1f downed=%s", s.name(), s.health(), s.downed()));
			}
			report.add("zombies left after 23 s: " + zombies + " of 3");
			check(snapshots(server).size() == 2, "nobody died (essential companions)");
			server.runCommand("kill @e[type=minecraft:zombie]");
			server.runCommand("time set 1000");

			// 4. Interrupted order: change of mind straight away.
			say(server, "Wren", "gather 10 sand");
			say(server, "Wren", "never mind");
			say(server, "Wren", "what are you doing?");
			context.waitTicks(5);
			Snapshot wren = snapshots(server).stream().filter(s -> s.name().equals("Wren")).findFirst().orElseThrow();
			check(wren.order().equals("FOLLOW"), "cancelled order really stopped (" + wren.order() + ")");
			check(wren.lastLine().equals("status.follow"), "status reply matches reality (" + wren.lastLine() + ")");

			// 5. Nether trip: followers come along; a waiting companion stays.
			say(server, "Wren", "wait here");
			context.waitTicks(5);
			server.runCommand("execute in minecraft:the_nether run fill 0 120 0 10 120 10 minecraft:stone");
			server.runCommand("execute in minecraft:the_nether run fill 0 121 0 10 124 10 minecraft:air");
			server.runCommand("execute in minecraft:the_nether run tp @p 5 121 5");
			context.waitTicks(60);
			List<Snapshot> nether = snapshots(server);
			report.add("after portal-style travel: " + nether);
			check(nether.stream().anyMatch(s -> s.name().equals("Bram") && s.dimension().equals("the_nether")), "following Bram came to the Nether");
			check(nether.stream().anyMatch(s -> s.name().equals("Wren") && s.dimension().equals("overworld")), "waiting Wren stayed behind");
			context.takeScreenshot("playtest-nether");
			server.runCommand("execute in minecraft:overworld run spreadplayers 0 0 0 4 false @p");
			context.waitTicks(60);

			// 6. Prepare state to check after reloading.
			server.runCommand("item replace entity @e[type=hearthbound:companion,name=Bram] weapon.mainhand with minecraft:iron_sword");
			say(server, "Bram", "tell me a joke");
			context.waitTicks(20);
			beforeQuit = snapshots(server);
			save = world.getWorldSave();
		}

		try (TestSingleplayerContext reopened = save.open()) {
			reopened.getConnection().waitForChunksRender();
			TestServerContext server = reopened.getServer();
			context.waitTicks(40);
			List<Snapshot> afterLoad = snapshots(server);
			report.add("before quit: " + beforeQuit);
			report.add("after reload: " + afterLoad);
			for (Snapshot before : beforeQuit) {
				Snapshot after = afterLoad.stream().filter(s -> s.name().equals(before.name())).findFirst().orElse(null);
				check(after != null, before.name() + " still exists after reload");
				check(after.archetype().equals(before.archetype()) && after.skin() == before.skin(), before.name() + " kept personality and skin");
				check(after.baseMode().equals(before.baseMode()), before.name() + " kept follow/wait mode");
				check(after.sword() == before.sword(), before.name() + " kept equipment");
				check(after.lines() == before.lines(), before.name() + " kept conversation memory");
			}
			context.takeScreenshot("playtest-after-reload");
		}
		report.forEach(line -> System.out.println("[Playtest] " + line));
	}

	private static void say(TestServerContext server, String name, String message) {
		server.runCommand("execute as @p run companion say " + name + " " + message);
	}

	private void check(boolean condition, String what) {
		report.add((condition ? "PASS " : "FAIL ") + what);
		if (!condition) {
			report.forEach(line -> System.out.println("[Playtest] " + line));
			throw new AssertionError("Playtest check failed: " + what);
		}
	}

	private static int playerLogs(TestServerContext server) {
		return server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getInventory()
				.countItem(Items.OAK_LOG) + s.getPlayerList().getPlayers().get(0).getInventory().countItem(Items.BIRCH_LOG)
				+ s.getPlayerList().getPlayers().get(0).getInventory().countItem(Items.SPRUCE_LOG)
				+ s.getPlayerList().getPlayers().get(0).getInventory().countItem(Items.DARK_OAK_LOG)
				+ s.getPlayerList().getPlayers().get(0).getInventory().countItem(Items.JUNGLE_LOG)
				+ s.getPlayerList().getPlayers().get(0).getInventory().countItem(Items.ACACIA_LOG));
	}

	private static int countZombies(MinecraftServer server) {
		int count = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof Zombie && entity.isAlive()) {
					count++;
				}
			}
		}
		return count;
	}

	private static List<Snapshot> snapshots(TestServerContext server) {
		return server.computeOnServer(AdventurePlaytest::snapshotAll);
	}

	private static java.util.Optional<Snapshot> snapshot(MinecraftServer server, String name) {
		return snapshotAll(server).stream().filter(s -> s.name().equals(name)).findFirst();
	}

	private static List<Snapshot> snapshotAll(MinecraftServer server) {
		List<Snapshot> out = new ArrayList<>();
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof CompanionEntity c && c.isAlive()) {
					double distance = c.level() == player.level() ? c.distanceTo(player) : Double.POSITIVE_INFINITY;
					CompanionOrder order = c.order();
					out.add(new Snapshot(c.companionName(), c.archetype().id(), c.skinVariant(), order.kind().name(), c.baseMode().name(),
							c.countCarried(s -> true), c.getMainHandItem().is(Items.IRON_SWORD) || c.countCarried(s -> s.is(Items.IRON_SWORD)) > 0,
							c.memory().lines().size(), distance, c.lastLineKey(), c.isDowned(), c.getHealth(),
							level.dimension().identifier().getPath()));
				}
			}
		}
		out.sort(java.util.Comparator.comparing(Snapshot::name));
		return out;
	}
}

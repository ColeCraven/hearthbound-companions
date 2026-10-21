package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.entity.CompanionSkin;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Drives the real game client: renders every default skin in both arm widths, holds a short
 * conversation, and watches a companion gather wood, taking screenshots (saved under
 * build/run/clientGameTest/screenshots) for review and for the README.
 * Run with {@code ./gradlew runClientGameTest}; it opens a game window.
 */
@SuppressWarnings("UnstableApiUsage")
public class CompanionClientGameTest implements FabricClientGameTest {
	private static final String[] ARCHETYPES = {"sellsword", "naturalist", "rogue", "guardian", "farmhand", "mystic", "sellsword", "naturalist", "rogue"};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("tp @p 0 ~ 0 0 8");
			context.waitTicks(20);

			// The nine default skins, wide arms, then slim arms.
			for (boolean slim : new boolean[] {false, true}) {
				CompanionSkin[] skins = CompanionSkin.values();
				for (int i = 0; i < skins.length; i++) {
					summon(server, (i - 4) * 1.25, 5.5, skins[i].encode(slim), capitalise(skins[i].id()), ARCHETYPES[i]);
				}
				context.waitTicks(30);
				clearChat(context);
				context.takeScreenshot(slim ? "hearthbound-skins-slim" : "hearthbound-skins-wide");
				removeCompanions(server, context);
			}

			// Same skin, both models side by side: slim (3px) arms vs wide (4px) arms.
			summon(server, -0.7, 2.6, CompanionSkin.ALEX.encode(true), "Alex (slim)", "rogue");
			summon(server, 0.7, 2.6, CompanionSkin.ALEX.encode(false), "Alex (wide)", "rogue");
			context.waitTicks(30);
			clearChat(context);
			context.takeScreenshot("hearthbound-slim-vs-wide");
			removeCompanions(server, context);

			// A small party: recruit three companions and give orders through the normal chat path.
			server.runCommand("execute at @p run summon hearthbound:companion ~-1.5 ~ ~3 {Archetype:\"naturalist\",Skin:102,CustomName:\"Hazel\",CustomNameVisible:1b}");
			server.runCommand("execute at @p run summon hearthbound:companion ~0 ~ ~3.5 {Archetype:\"sellsword\",Skin:100,CustomName:\"Bram\",CustomNameVisible:1b}");
			server.runCommand("execute at @p run summon hearthbound:companion ~1.5 ~ ~3 {Archetype:\"rogue\",Skin:104,CustomName:\"Cass\",CustomNameVisible:1b}");
			context.waitTicks(10);
			server.runCommand("execute as @p run companion say Hazel hello! will you join me?");
			server.runCommand("execute as @p run companion say Bram join me");
			server.runCommand("execute as @p run companion say Cass would you like to join my party");
			context.waitTicks(30);
			server.runCommand("execute as @p run companion say Bram build me a castle");
			server.runCommand("execute as @p run companion say Hazel gather 20 diamonds");
			server.runCommand("execute as @p run companion say Cass wait here");
			context.waitTicks(60);
			context.takeScreenshot("hearthbound-party-conversation");

			// Gathering: grow a few trees and send Bram for wood.
			server.runCommand("execute at @p run place feature minecraft:oak ~-3 ~ ~9");
			server.runCommand("execute at @p run place feature minecraft:oak ~2 ~ ~11");
			server.runCommand("execute at @p run place feature minecraft:birch ~5 ~ ~8");
			context.waitTicks(10);
			server.runCommand("execute as @p run companion say Bram could you chop me 6 logs?");
			context.waitTicks(160);
			context.takeScreenshot("hearthbound-gathering");
			context.waitTicks(600);
			context.takeScreenshot("hearthbound-gathering-done");
		}
	}

	private static void summon(TestServerContext server, double x, double z, int skin, String name, String archetype) {
		server.runCommand(String.format(Locale.ROOT,
				"execute at @p run summon hearthbound:companion ~%.2f ~ ~%.2f {Archetype:\"%s\",Skin:%d,CustomName:\"%s\",CustomNameVisible:1b,NoAI:1b,Rotation:[180f,0f]}",
				x, z, archetype, skin, name));
	}

	/** Removes test companions without death animations or "I'm hurt" lines. */
	private static void removeCompanions(TestServerContext server, ClientGameTestContext context) {
		server.runCommand("tp @e[type=hearthbound:companion] ~ -200 ~");
		context.waitTicks(40);
	}

	private static void clearChat(ClientGameTestContext context) {
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
		context.waitTicks(2);
	}

	private static String capitalise(String text) {
		return Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}
}

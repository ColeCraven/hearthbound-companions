package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.entity.CompanionSkin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Drives the real game client: renders every default skin in both arm widths, then holds a short
 * conversation, taking screenshots (saved under build/run/clientGameTest/screenshots) for review
 * and for the README. Run with {@code ./gradlew runClientGameTest}; it opens a game window.
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
			server.runCommand("tp @p 0 ~ 0 0 12");
			context.waitTicks(20);

			// Two rows: the nine default skins with wide arms (front) and slim arms (back).
			CompanionSkin[] skins = CompanionSkin.values();
			for (int i = 0; i < skins.length; i++) {
				double x = (i - 4) * 1.15;
				summon(server, x, 4.5, skins[i].encode(false), capitalise(skins[i].id()), ARCHETYPES[i]);
				summon(server, x, 7.0, skins[i].encode(true), capitalise(skins[i].id()) + " (slim)", ARCHETYPES[i]);
			}
			context.waitTicks(40);
			context.takeScreenshot("hearthbound-default-skins");

			server.runCommand("kill @e[type=hearthbound:companion]");
			context.waitTicks(10);

			// A small party: recruit three companions and give orders through the normal chat path.
			server.runCommand("execute at @p run summon hearthbound:companion ~-1.5 ~ ~3 {Archetype:\"naturalist\",Skin:10,CustomName:\"Wren\",CustomNameVisible:1b}");
			server.runCommand("execute at @p run summon hearthbound:companion ~0 ~ ~3.5 {Archetype:\"sellsword\",Skin:6,CustomName:\"Bram\",CustomNameVisible:1b}");
			server.runCommand("execute at @p run summon hearthbound:companion ~1.5 ~ ~3 {Archetype:\"rogue\",Skin:13,CustomName:\"Cass\",CustomNameVisible:1b}");
			context.waitTicks(10);
			server.runCommand("execute as @p run companion say Wren hello! will you join me?");
			server.runCommand("execute as @p run companion say Bram join me");
			server.runCommand("execute as @p run companion say Cass would you like to join my party");
			context.waitTicks(30);
			server.runCommand("execute as @p run companion say Bram build me a castle");
			server.runCommand("execute as @p run companion say Wren gather 20 diamonds");
			server.runCommand("execute as @p run companion say Cass wait here");
			context.waitTicks(60);
			context.takeScreenshot("hearthbound-party-conversation");
		}
	}

	private static void summon(TestServerContext server, double x, double z, int skin, String name, String archetype) {
		server.runCommand(String.format(java.util.Locale.ROOT,
				"execute at @p run summon hearthbound:companion ~%.2f ~ ~%.2f {Archetype:\"%s\",Skin:%d,CustomName:\"%s\",CustomNameVisible:1b,NoAI:1b,Rotation:[180f,0f]}",
				x, z, archetype, skin, name));
	}

	private static String capitalise(String text) {
		return Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}
}

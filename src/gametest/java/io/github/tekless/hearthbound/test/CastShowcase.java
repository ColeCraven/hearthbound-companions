package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.core.persona.CastMember;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Renders the named cast in the real client for review: the README banner, front and back
 * line-ups, close-ups of each group, and a few of them walking (to check seams and outer layers in
 * motion).
 */
@SuppressWarnings("UnstableApiUsage")
public class CastShowcase implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("tp @p 0 ~ 0 0 6");
			context.waitTicks(20);

			CastMember[] cast = CastMember.values();
			// The README banner, like a team photo: the back row on a one-block step, no HUD.
			server.runCommand("execute at @p run fill ~-6 ~ ~5 ~6 ~ ~6 minecraft:grass_block");
			for (int i = 0; i < cast.length; i++) {
				boolean backRow = i >= 6;
				summon(server, cast[i], (2.5 - (i % 6)) * 1.15 + (backRow ? 0.3 : 0), backRow ? 1 : 0, backRow ? 5.5 : 3.9, 180, true);
			}
			context.waitTicks(30);
			clearChat(context);
			context.runOnClient(client -> client.gui.hud.toggle());
			context.waitTicks(2);
			context.takeScreenshot("cast-banner");
			context.runOnClient(client -> client.gui.hud.toggle());
			remove(server, context);
			server.runCommand("execute at @p run fill ~-6 ~ ~5 ~6 ~ ~6 minecraft:air");

			// Line-up: two rows of six, facing the camera, then facing away.
			for (boolean back : new boolean[] {false, true}) {
				for (int i = 0; i < cast.length; i++) {
					int row = i / 6;
					double x = (2.5 - (i % 6)) * 1.3;
					summon(server, cast[i], x, row == 0 ? 5.0 : 7.6, back ? 0 : 180, true);
				}
				context.waitTicks(30);
				clearChat(context);
				context.takeScreenshot(back ? "cast-lineup-back" : "cast-lineup-front");
				remove(server, context);
			}

			// Close-ups, four at a time.
			for (int group = 0; group < 3; group++) {
				for (int i = 0; i < 4; i++) {
					summon(server, cast[group * 4 + i], (1.5 - i) * 1.1, 2.6, 180, true);
				}
				context.waitTicks(30);
				clearChat(context);
				context.takeScreenshot("cast-closeup-" + (group + 1));
				remove(server, context);
			}

			// Walking: three recruited companions hurry back to the player from a distance.
			summon(server, CastMember.DAISY, 2, 14, 180, false);
			summon(server, CastMember.CASS, 0, 15, 180, false);
			summon(server, CastMember.CASPER, -2, 14, 180, false);
			context.waitTicks(5);
			for (String name : new String[] {"Daisy", "Cass", "Casper"}) {
				server.runCommand("execute as @p run companion recruit " + name);
			}
			context.waitTicks(22);
			clearChat(context);
			context.takeScreenshot("cast-walking");
		}
	}

	private static void summon(TestServerContext server, CastMember member, double x, double z, float yaw, boolean frozen) {
		summon(server, member, x, 0, z, yaw, frozen);
	}

	private static void summon(TestServerContext server, CastMember member, double x, double y, double z, float yaw, boolean frozen) {
		server.runCommand(String.format(Locale.ROOT,
				"execute at @p run summon hearthbound:companion ~%.2f ~%.2f ~%.2f {Archetype:\"%s\",Skin:%d,CustomName:\"%s\",CustomNameVisible:1b,%sRotation:[%.0ff,0f]}",
				x, y, z, member.archetype().id(), member.skinVariant(), member.displayName(), frozen ? "NoAI:1b," : "", yaw));
	}

	private static void remove(TestServerContext server, ClientGameTestContext context) {
		server.runCommand("tp @e[type=hearthbound:companion] ~ -200 ~");
		context.waitTicks(30);
	}

	private static void clearChat(ClientGameTestContext context) {
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
		context.waitTicks(2);
	}
}

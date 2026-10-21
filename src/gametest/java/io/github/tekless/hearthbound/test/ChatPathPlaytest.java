package io.github.tekless.hearthbound.test;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.tekless.hearthbound.client.HearthboundClient;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/**
 * Talks to a companion exactly like a player would: opens the chat box, types "@Bram join me" and
 * presses Enter; then uses the Talk key (G), which pre-fills "@Bram ", and asks for a status.
 */
@SuppressWarnings("UnstableApiUsage")
public class ChatPathPlaytest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("execute at @p run summon hearthbound:companion ~1 ~ ~3 {Archetype:\"sellsword\",Skin:100,CustomName:\"Bram\",CustomNameVisible:1b}");
			context.waitTicks(10);

			typeInChat(context, "@Bram join me");
			context.waitTicks(10);
			String afterRecruit = server.computeOnServer(ChatPathPlaytest::bramState);
			if (!afterRecruit.equals("recruited:recruit.accept")) {
				throw new AssertionError("Typing '@Bram join me' in chat should recruit Bram, got " + afterRecruit);
			}

			// The talk key opens chat with "@Bram " already typed (last companion spoken to).
			context.getInput().pressKey(HearthboundClient.talkKey());
			context.waitTicks(5);
			context.getInput().typeChars("what are you doing?");
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(10);
			String afterStatus = server.computeOnServer(ChatPathPlaytest::bramState);
			if (!afterStatus.equals("recruited:status.follow")) {
				throw new AssertionError("Talk key + 'what are you doing?' should get a follow status, got " + afterStatus);
			}
			context.takeScreenshot("hearthbound-chat-path");
			System.out.println("[Playtest] PASS chat path: '@Bram join me' recruited; talk key + 'what are you doing?' -> status.follow");
		}
	}

	private static void typeInChat(ClientGameTestContext context, String text) {
		context.getInput().pressKey(options -> options.keyChat);
		context.waitTicks(5);
		context.getInput().typeChars(text);
		context.getInput().pressKey(InputConstants.KEY_RETURN);
	}

	private static String bramState(MinecraftServer server) {
		for (var level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof CompanionEntity companion && companion.companionName().equals("Bram")) {
					return (companion.isRecruited() ? "recruited" : "stranger") + ":" + companion.lastLineKey();
				}
			}
		}
		return "missing";
	}
}

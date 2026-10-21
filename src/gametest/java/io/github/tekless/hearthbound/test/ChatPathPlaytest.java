package io.github.tekless.hearthbound.test;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.tekless.hearthbound.client.HearthboundClient;
import io.github.tekless.hearthbound.core.persona.CastMember;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/**
 * Talks to a companion exactly like a player would: opens the chat box, types "@Bram join me" and
 * presses Enter; then uses the Talk key (G), which pre-fills "@Bram ", and asks for a status.
 * After that, a free conversation where Rosie learns a nickname and a favourite thing and says
 * them back, and a companion building a shelter from the planks in their pack.
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

			// A free conversation: name her once, then just talk.
			server.runCommand("execute at @p run summon hearthbound:companion ~-1 ~ ~2.5 {Archetype:\"farmhand\",Skin:109,CustomName:\"Rosie\",CustomNameVisible:1b,Rotation:[180f,0f]}");
			context.waitTicks(10);
			context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
			String[][] lines = {
					{"@Rosie hi there!", "talk.hello"},
					{"how are you?", "talk.how"},
					{"call me Captain", "fact.nickname"},
					{"I love cherry trees", "fact.like"},
					{"what do you know about me?", "talk.about_me"},
			};
			for (String[] line : lines) {
				typeInChat(context, line[0]);
				context.waitTicks(25);
				String key = server.computeOnServer(s -> lastLine(s, "Rosie"));
				if (!key.equals(line[1])) {
					throw new AssertionError("'" + line[0] + "' should get a " + line[1] + " reply from Rosie, got " + key);
				}
			}
			context.takeScreenshot("hearthbound-free-chat");
			System.out.println("[Playtest] PASS free chat: one @Rosie, then four plain messages answered in character, and remembered");

			// Building: a guardian with a pack of planks, a door and a torch puts up a shelter in front of the player.
			// (Followers teleport back to you, so the earlier two are removed for good.)
			server.runCommand("kill @e[type=hearthbound:companion]");
			context.waitTicks(40);
			context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
			CastMember builder = CastMember.CONOBY;
			server.runCommand(String.format(Locale.ROOT, "execute at @p run summon hearthbound:companion ~1.5 ~ ~1 {Archetype:\"%s\",Skin:%d,CustomName:\"%s\","
					+ "CustomNameVisible:1b,Inventory:[{id:\"minecraft:oak_planks\",count:64},{id:\"minecraft:oak_planks\",count:32},"
					+ "{id:\"minecraft:oak_door\",count:1},{id:\"minecraft:torch\",count:4}]}",
					builder.archetype().id(), builder.skinVariant(), builder.displayName()));
			context.waitTicks(10);
			typeInChat(context, "@Conoby join me");
			context.waitTicks(20);
			typeInChat(context, "@Conoby build a shelter");
			server.waitFor(s -> lastLine(s, "Conoby").equals("done.build"), 2000);
			typeInChat(context, "@Conoby wait here");
			context.waitTicks(20);
			// A three-quarter view of the finished shelter, doorway side.
			server.runCommand("execute at @p run tp @p ~6 ~ ~-5 34 8");
			context.waitTicks(25);
			context.takeScreenshot("hearthbound-building");
			System.out.println("[Playtest] PASS building: a shelter from planks in the pack, door and torch included");
		}
	}

	private static String lastLine(MinecraftServer server, String name) {
		for (var level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof CompanionEntity companion && companion.companionName().equals(name)) {
					return companion.lastLineKey();
				}
			}
		}
		return "missing";
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

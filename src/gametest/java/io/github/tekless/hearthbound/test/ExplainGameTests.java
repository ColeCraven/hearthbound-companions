package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.dialogue.Explain;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import io.github.tekless.hearthbound.registry.ModEntities;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** {@code /companion explain}: what they heard, how it was read, who it was for, what came of it and what's in the way. */
public class ExplainGameTests {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static ServerPlayer player(GameTestHelper helper, double x, double z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(x, 1, z));
		player.snapTo(pos.x, pos.y, pos.z, 0, 0);
		return player;
	}

	private static CompanionEntity companion(GameTestHelper helper, Archetype archetype, String name, double x, double z) {
		CompanionEntity companion = helper.spawn(ModEntities.COMPANION, new Vec3(x, 1, z));
		companion.initIdentity(archetype, name, CompanionSkin.STEVE, false);
		return companion;
	}

	private static void remove(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static boolean has(List<String> lines, String start, String part) {
		return lines.stream().anyMatch(line -> line.startsWith(start) && line.contains(part));
	}

	@GameTest(maxTicks = 100, padding = 40)
	public void aRefusedCraftIsExplained(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 1.5);
		CompanionEntity ezra = companion(helper, Archetype.SELLSWORD, "Gtezr", 3.5, 4.5);
		Conversations.apply(owner, ezra, Intent.of(IntentType.RECRUIT));
		ezra.inventory().addItem(new ItemStack(Items.STICK, 1));
		helper.assertTrue(Conversations.onChat(owner, "@Gtezr craft me an iron sword"), "heard");
		List<String> lines = Explain.lines(ezra, owner);
		helper.assertTrue(has(lines, "Gtezr last heard", "craft me an iron sword"), "what was heard: " + lines);
		helper.assertTrue(has(lines, "Read as:", "craft, item \"iron sword\""), "how it was read: " + lines);
		helper.assertTrue(has(lines, "Meant for:", "Gtezr"), "who for: " + lines);
		helper.assertTrue(has(lines, "Result:", "refused: craft.missing") && has(lines, "Result:", "iron"), "what came of it: " + lines);
		helper.assertTrue(has(lines, "Last said:", ""), "and what they said back: " + lines);
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = 40)
	public void aPartyOrderNamesEveryoneItWasFor(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 1.5);
		CompanionEntity ann = companion(helper, Archetype.FARMHAND, "Gtann", 2.5, 4.5);
		CompanionEntity bo = companion(helper, Archetype.ROGUE, "Gtbob", 4.5, 4.5);
		Conversations.apply(owner, ann, Intent.of(IntentType.RECRUIT));
		Conversations.apply(owner, bo, Intent.of(IntentType.RECRUIT));
		helper.assertTrue(Conversations.onChat(owner, "@party wait here"), "heard");
		for (CompanionEntity companion : List.of(ann, bo)) {
			List<String> lines = Explain.lines(companion, owner);
			helper.assertTrue(has(lines, "Read as:", "order: wait"), "read as an order: " + lines);
			helper.assertTrue(has(lines, "Meant for:", "Gtann") && has(lines, "Meant for:", "Gtbob"), "both named: " + lines);
			helper.assertTrue(has(lines, "Result:", "accepted"), "and taken: " + lines);
			helper.assertTrue(has(lines, "Blockers:", "none"), "nothing in the way: " + lines);
		}
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = 40)
	public void blockersAreListedAndTheConsoleCanAsk(GameTestHelper helper) {
		floor(helper);
		CompanionEntity loner = companion(helper, Archetype.MYSTIC, "Gtlon", 3.5, 3.5);
		for (int slot = 0; slot < loner.inventory().getContainerSize(); slot++) {
			loner.inventory().setItem(slot, new ItemStack(Items.DIRT, 64));
		}
		loner.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1000.0F); // knocked down, not killed
		helper.assertTrue(loner.isDowned() && loner.isAlive(), "knocked down");
		List<String> blockers = Explain.blockers(loner, null);
		helper.assertTrue(blockers.stream().anyMatch(b -> b.startsWith("knocked down")), "downed: " + blockers);
		helper.assertTrue(blockers.stream().anyMatch(b -> b.startsWith("not with anyone")), "a stranger: " + blockers);
		helper.assertTrue(blockers.contains("pack full"), "full pack: " + blockers);
		helper.assertTrue(has(Explain.lines(loner, null), "Gtlon hasn't heard", ""), "nothing heard yet");
		CommandSourceStack console = helper.getLevel().getServer().createCommandSourceStack().withSuppressedOutput();
		try {
			int result = helper.getLevel().getServer().getCommands().getDispatcher().execute("companion explain Gtlon", console);
			helper.assertValueEqual(result, 1, "the console can ask too");
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new AssertionError("explain from the console: " + e.getMessage(), e);
		}
		helper.succeed();
	}
}

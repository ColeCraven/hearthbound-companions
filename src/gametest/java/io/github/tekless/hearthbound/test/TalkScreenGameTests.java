package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.dialogue.TalkView;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import io.github.tekless.hearthbound.entity.order.CompanionOrder;
import io.github.tekless.hearthbound.network.TalkPayloads;
import io.github.tekless.hearthbound.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The conversation screen's server side: what it shows, who it lets you talk to, and that a line from it does the
 * same as saying it in chat.
 */
public class TalkScreenGameTests {
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

	@GameTest(maxTicks = 100, padding = 40)
	public void aLineFromTheScreenIsAnOrderLikeChat(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 1.5);
		CompanionEntity wren = companion(helper, Archetype.NATURALIST, "Gtwrn", 3.5, 4.5);
		Conversations.apply(owner, wren, Intent.of(IntentType.RECRUIT));
		wren.inventory().addItem(new ItemStack(Items.OAK_LOG, 12));
		helper.assertTrue(TalkView.say(owner, wren, "  wait here  "), "sent");
		helper.assertValueEqual(wren.order().kind(), CompanionOrder.Kind.WAIT, "waiting, as if said in chat");
		TalkPayloads.State state = TalkView.state(owner, wren);
		helper.assertValueEqual(state.header().name(), "Gtwrn", "who");
		helper.assertValueEqual(state.header().role(), wren.role().label(), "their role");
		helper.assertTrue(!state.header().mood().isEmpty() && !state.header().need().isEmpty(), "mood and need");
		helper.assertValueEqual(state.header().task(), wren.order().describe(), "what they're doing");
		helper.assertTrue(state.lines().stream().anyMatch(line -> line.fromPlayer() && line.text().equals("wait here")), "what you said");
		helper.assertTrue(state.lines().stream().anyMatch(line -> !line.fromPlayer()), "and the answer");
		helper.assertTrue(state.suggestions().contains("Give me the oak log"), "something worth saying: " + state.suggestions());
		helper.assertFalse(TalkView.say(owner, wren, "   "), "nothing to say is nothing sent");
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = 40)
	public void onlyYoursOrSomeoneCloseCanBeTalkedTo(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper, 0.5, 0.5);
		CompanionEntity near = companion(helper, Archetype.ROGUE, "Gtnea", 3.5, 3.5);
		CompanionEntity far = companion(helper, Archetype.ROGUE, "Gtfar", 3.5, 30.5);
		helper.assertTrue(TalkView.canTalk(player, near), "a stranger close by");
		helper.assertFalse(TalkView.canTalk(player, far), "not one a long way off");
		helper.assertFalse(TalkView.say(player, far, "follow me"), "so nothing reaches them");
		helper.assertValueEqual(TalkView.state(player, near).suggestions(), java.util.List.of("Will you join me?"), "a stranger can be asked along");
		helper.assertFalse(TalkView.canTalk(player, player), "only companions");
		remove(helper, player);
		helper.succeed();
	}
}

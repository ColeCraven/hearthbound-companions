package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import io.github.tekless.hearthbound.entity.order.BuildOrder;
import io.github.tekless.hearthbound.entity.order.CompanionOrder;
import io.github.tekless.hearthbound.entity.order.GatherOrder;
import io.github.tekless.hearthbound.entity.order.Blueprint;
import io.github.tekless.hearthbound.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Orders typed in ordinary chat: the right companion hears them, "this" and "that" are worked out
 * from what the player is looking at, vague requests get one short question and the answer is used,
 * and tasks can be stopped or replaced at any time.
 */
public class ChatOrdersGameTests {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** A player standing at (x, 1, z) looking along +x (east) or +z (south). */
	private static ServerPlayer player(GameTestHelper helper, double x, double z, boolean east) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(x, 1, z));
		Vec3 ahead = helper.absoluteVec(new Vec3(x + (east ? 3 : 0), 1, z + (east ? 0 : 3)));
		float yaw = (float) Math.toDegrees(Math.atan2(-(ahead.x - pos.x), ahead.z - pos.z));
		player.snapTo(pos.x, pos.y, pos.z, yaw, 0);
		player.setYHeadRot(yaw);
		return player;
	}

	private static CompanionEntity companion(GameTestHelper helper, Archetype archetype, String name, double x, double z) {
		CompanionEntity companion = helper.spawn(ModEntities.COMPANION, new Vec3(x, 1, z));
		companion.initIdentity(archetype, name, CompanionSkin.STEVE, false);
		return companion;
	}

	private static CompanionEntity recruited(GameTestHelper helper, ServerPlayer owner, Archetype archetype, String name, double x, double z) {
		CompanionEntity companion = companion(helper, archetype, name, x, z);
		Conversations.apply(owner, companion, Intent.of(IntentType.RECRUIT));
		return companion;
	}

	private static void remove(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	@GameTest(maxTicks = 60)
	public void chatOrderReachesTheRightCompanion(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 0.5, false);
		CompanionEntity ada = recruited(helper, owner, Archetype.SELLSWORD, "Adaxx", 1.5, 3.5);
		CompanionEntity bo = recruited(helper, owner, Archetype.NATURALIST, "Boxx", 3.5, 3.5);
		CompanionEntity cy = recruited(helper, owner, Archetype.ROGUE, "Cyxx", 5.5, 3.5);
		helper.assertTrue(Conversations.onChat(owner, "@boxx wait here"), "addressed by name");
		helper.assertValueEqual(bo.order().kind(), CompanionOrder.Kind.WAIT, "Bo waits");
		helper.assertValueEqual(ada.order().kind(), CompanionOrder.Kind.FOLLOW, "Ada carries on following");
		helper.assertValueEqual(cy.order().kind(), CompanionOrder.Kind.FOLLOW, "Cy carries on following");
		// Then plain chat, no @, goes to the one you're talking with (Bo is straight ahead).
		helper.assertTrue(Conversations.onChat(owner, "follow me"), "plain chat reaches the conversation partner");
		helper.assertValueEqual(bo.order().kind(), CompanionOrder.Kind.FOLLOW, "Bo follows again");
		helper.assertValueEqual(ada.lastLineKey(), "", "Ada heard nothing");
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void buildThisAsksOneQuestionThenBuilds(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 0.5, false);
		CompanionEntity builder = recruited(helper, owner, Archetype.NATURALIST, "Lindxx", 3.5, 1.5);
		builder.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 64));
		builder.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 16));
		helper.assertTrue(Conversations.onChat(owner, "@lindxx build this"), "heard");
		helper.assertValueEqual(builder.lastLineKey(), "ask.build_what", "asks what to build");
		helper.assertFalse(builder.order().kind() == CompanionOrder.Kind.BUILD, "nothing started yet");
		helper.assertTrue(Conversations.onChat(owner, "a shelter please"), "the answer needs no @");
		helper.assertValueEqual(builder.order().kind(), CompanionOrder.Kind.BUILD, "building now");
		helper.assertValueEqual(((BuildOrder) builder.order()).blueprint().kind(), Blueprint.Kind.SHELTER, "a shelter");
		helper.assertValueEqual(builder.lastLineKey(), "ack.build", "acknowledged");
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void collectThatGathersTheBlockYouAreLookingAt(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(5, 2, 3, Blocks.OAK_LOG);
		ServerPlayer owner = player(helper, 1.5, 3.5, true);
		CompanionEntity ada = recruited(helper, owner, Archetype.GUARDIAN, "Adaxx", 1.5, 5.5);
		helper.assertTrue(Conversations.onChat(owner, "@adaxx collect that"), "heard");
		helper.assertValueEqual(ada.order().kind(), CompanionOrder.Kind.GATHER, "gathering");
		helper.assertTrue(((GatherOrder) ada.order()).spec().displayName().toLowerCase().contains("oak"), "the block's own wood, not just any logs");
		helper.assertValueEqual(ada.lastLineKey(), "ack.gather", "acknowledged");
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void collectThatWithNothingInSightAsksWhat(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 1.5, 3.5, true);
		CompanionEntity ada = recruited(helper, owner, Archetype.ROGUE, "Adaxx", 1.5, 5.5);
		helper.assertTrue(Conversations.onChat(owner, "@adaxx collect that"), "heard");
		helper.assertValueEqual(ada.lastLineKey(), "unclear.gather_what", "asks what");
		helper.assertTrue(Conversations.onChat(owner, "@adaxx 8 cobblestone"), "answer");
		helper.assertValueEqual(ada.order().kind(), CompanionOrder.Kind.GATHER, "gathering the answer");
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void stopAndReplaceTasks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 0.5, false);
		CompanionEntity ada = recruited(helper, owner, Archetype.FARMHAND, "Adaxx", 3.5, 2.5);
		helper.assertTrue(Conversations.onChat(owner, "@adaxx gather 6 logs"), "order given");
		CompanionOrder first = ada.order();
		helper.assertValueEqual(first.kind(), CompanionOrder.Kind.GATHER, "gathering");
		helper.assertTrue(Conversations.onChat(owner, "@adaxx stop"), "stop heard");
		helper.assertValueEqual(first.status(), TaskStatus.CANCELLED, "the task really stopped");
		helper.assertValueEqual(ada.lastLineKey(), "ack.cancel", "says what it dropped");
		helper.assertTrue(Conversations.onChat(owner, "@adaxx gather 6 logs"), "order again");
		CompanionOrder second = ada.order();
		helper.assertTrue(Conversations.onChat(owner, "@adaxx collect 4 cobblestone"), "a new order replaces it");
		helper.assertValueEqual(second.status(), TaskStatus.CANCELLED, "the old order was cancelled");
		helper.assertTrue(((GatherOrder) ada.order()).spec().displayName().toLowerCase().contains("cobble"), "now on the new job");
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void strangerOffersToJoinThenDoesTheJob(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 0.5, false);
		CompanionEntity dot = companion(helper, Archetype.MYSTIC, "Dotxx", 3.5, 2.5);
		helper.assertTrue(Conversations.onChat(owner, "@dotxx gather 4 logs"), "heard");
		helper.assertValueEqual(dot.lastLineKey(), "ask.join_first", "offers to join first");
		helper.assertFalse(dot.isRecruited(), "not recruited without a yes");
		helper.assertTrue(Conversations.onChat(owner, "yes"), "the yes needs no @");
		helper.assertTrue(dot.isRecruited(), "joined");
		helper.assertValueEqual(dot.order().kind(), CompanionOrder.Kind.GATHER, "and got on with it");
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void farCompanionComesBeforeWorking(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 0.5, false);
		CompanionEntity ada = recruited(helper, owner, Archetype.SELLSWORD, "Adaxx", 3.5, 2.5);
		owner.snapTo(owner.getX() + 40, owner.getY(), owner.getZ(), owner.getYRot(), 0); // wandered off
		Conversations.Outcome outcome = Conversations.apply(owner, ada, Intent.gather("logs", 4));
		helper.assertTrue(outcome.accepted(), "accepted");
		helper.assertValueEqual(outcome.lineKey(), "ack.come", "says it is on its way first");
		helper.assertValueEqual(ada.order().kind(), CompanionOrder.Kind.COME, "travelling");
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void everyoneHearsAtOnce(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 0.5, false);
		CompanionEntity ada = recruited(helper, owner, Archetype.SELLSWORD, "Adaxx", 1.5, 3.5);
		CompanionEntity bo = recruited(helper, owner, Archetype.GUARDIAN, "Boxx", 5.5, 3.5);
		helper.assertTrue(Conversations.onChat(owner, "@everyone wait here"), "heard");
		helper.assertValueEqual(ada.order().kind(), CompanionOrder.Kind.WAIT, "Ada waits");
		helper.assertValueEqual(bo.order().kind(), CompanionOrder.Kind.WAIT, "Bo waits");
		helper.assertTrue(Conversations.onChat(owner, "@party follow me"), "party heard");
		helper.assertValueEqual(ada.order().kind(), CompanionOrder.Kind.FOLLOW, "Ada follows");
		helper.assertValueEqual(bo.order().kind(), CompanionOrder.Kind.FOLLOW, "Bo follows");
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void followUpsLeanOnTheTopic(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 0.5, false);
		CompanionEntity ada = recruited(helper, owner, Archetype.NATURALIST, "Adaxx", 3.5, 2.5);
		Conversations.onChat(owner, "@adaxx tell me about your family");
		helper.assertValueEqual(ada.lastLineKey(), "talk.story", "tells their story");
		Conversations.onChat(owner, "tell me more");
		helper.assertValueEqual(ada.lastLineKey(), "talk.story", "carries on with the same subject");
		Conversations.onChat(owner, "why?");
		helper.assertValueEqual(ada.lastLineKey(), "talk.why", "a why gets a why");
		Conversations.onChat(owner, "I saw a creeper last night");
		helper.assertValueEqual(ada.lastLineKey(), "talk.mention.mob", "picks up on the creeper");
		remove(helper, owner);
		helper.succeed();
	}
}

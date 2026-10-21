package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.intent.QuestionKind;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import io.github.tekless.hearthbound.registry.ModEntities;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Getting to know the player: what they tell a companion is remembered and used, questions get
 * answered, crouching says hello, and big moments get a reaction.
 */
public class FriendshipGameTests {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 6; x++) {
			for (int z = 0; z < 6; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** A player at (x, 1, z) looking south (+z). */
	private static ServerPlayer player(GameTestHelper helper, double x, double z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(x, 1, z));
		Vec3 ahead = helper.absoluteVec(new Vec3(x, 1, z + 3));
		float yaw = (float) Math.toDegrees(Math.atan2(-(ahead.x - pos.x), ahead.z - pos.z));
		player.snapTo(pos.x, pos.y, pos.z, yaw, 0);
		player.setYHeadRot(yaw);
		return player;
	}

	/** A recruited companion waiting where it stands. */
	private static CompanionEntity friend(GameTestHelper helper, ServerPlayer owner, Archetype archetype, String name, double x, double z) {
		CompanionEntity companion = helper.spawn(ModEntities.COMPANION, new Vec3(x, 1, z));
		companion.initIdentity(archetype, name, CompanionSkin.STEVE, false);
		Conversations.apply(owner, companion, Intent.of(IntentType.RECRUIT));
		Conversations.apply(owner, companion, Intent.of(IntentType.WAIT));
		return companion;
	}

	private static void remove(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	@GameTest(maxTicks = 40)
	public void aNicknameIsUsedFromThenOn(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 1.5, 1.5);
		CompanionEntity companion = friend(helper, owner, Archetype.SELLSWORD, "Charlie", 1.5, 3.5);
		Conversations.handleMessage(owner, companion, "call me Captain");
		helper.assertValueEqual(companion.lastLineKey(), "fact.nickname", "acknowledged");
		helper.assertValueEqual(companion.callName(owner), "Captain", "uses the new name");
		Conversations.handleMessage(owner, companion, "don't call me that");
		helper.assertValueEqual(companion.callName(owner), owner.getName().getString(), "and drops it when asked");
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void answersToTheirQuestionsAreRemembered(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 1.5, 1.5);
		CompanionEntity companion = friend(helper, owner, Archetype.NATURALIST, "Hazel", 1.5, 3.5);
		companion.ask(owner, QuestionKind.LIKE, "ask.like", Map.of());
		Conversations.handleMessage(owner, companion, "probably diamonds");
		helper.assertValueEqual(companion.lastLineKey(), "fact.like", "took it as an answer");
		helper.assertValueEqual(companion.factsAbout(owner).likes(), List.of("diamonds"), "remembered");

		companion.ask(owner, QuestionKind.NICKNAME, "ask.nickname", Map.of());
		Conversations.handleMessage(owner, companion, "nah");
		helper.assertValueEqual(companion.lastLineKey(), "ask.nickname.skip", "a no is fine");
		helper.assertValueEqual(companion.callName(owner), owner.getName().getString(), "keeps their own name, and won't ask again");

		companion.ask(owner, QuestionKind.HOW, "ask.how", Map.of());
		Conversations.handleMessage(owner, companion, "honestly pretty tired");
		helper.assertValueEqual(companion.lastLineKey(), "talk.answer.bad", "sympathy");

		// An order is still an order, even straight after a question.
		companion.ask(owner, QuestionKind.LIKE, "ask.like", Map.of());
		Conversations.handleMessage(owner, companion, "follow me");
		helper.assertValueEqual(companion.lastLineKey(), "ack.follow", "followed");
		helper.assertValueEqual(companion.factsAbout(owner).likes(), List.of("diamonds"), "\"follow me\" isn't a favourite thing");

		Conversations.handleMessage(owner, companion, "what do you know about me?");
		helper.assertValueEqual(companion.lastLineKey(), "talk.about_me", "says it back");
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void crouchingTwiceGetsACrouchBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 2.5, 0.5);
		CompanionEntity companion = friend(helper, owner, Archetype.FARMHAND, "Honey", 2.5, 3.5);
		helper.runAfterDelay(5, () -> owner.setShiftKeyDown(true));
		helper.runAfterDelay(7, () -> owner.setShiftKeyDown(false));
		helper.runAfterDelay(9, () -> owner.setShiftKeyDown(true));
		helper.runAfterDelay(11, () -> owner.setShiftKeyDown(false));
		helper.succeedWhen(() -> {
			helper.assertValueEqual(companion.getPose(), Pose.CROUCHING, "crouches back");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 200)
	public void anAdvancementGetsAReaction(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 1.5, 1.5);
		CompanionEntity companion = friend(helper, owner, Archetype.ROGUE, "Nim", 1.5, 3.5);
		AdvancementHolder stoneAge = helper.getLevel().getServer().getAdvancements().get(Identifier.withDefaultNamespace("story/mine_stone"));
		helper.assertTrue(stoneAge != null, "advancement exists");
		for (String criterion : stoneAge.value().criteria().keySet()) {
			owner.getAdvancements().award(stoneAge, criterion);
		}
		helper.succeedWhen(() -> {
			helper.assertValueEqual(companion.lastLineKey(), "react.advancement", "noticed");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 200)
	public void aLevelMilestoneGetsAReaction(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 1.5, 1.5);
		CompanionEntity companion = friend(helper, owner, Archetype.MYSTIC, "Vesper", 1.5, 3.5);
		// Let the first once-a-second check see level 0 before levelling up.
		helper.runAfterDelay(30, () -> owner.giveExperienceLevels(10));
		helper.succeedWhen(() -> {
			helper.assertValueEqual(companion.lastLineKey(), "react.level", "noticed");
			remove(helper, owner);
		});
	}
}

package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import io.github.tekless.hearthbound.entity.order.CompanionOrder;
import io.github.tekless.hearthbound.entity.order.WaitOrder;
import io.github.tekless.hearthbound.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * In-world tests run by {@code ./gradlew build} (and {@code runGameTest}). Each builds a tiny scene,
 * gives real orders through the same code path as chat, and checks the world afterwards.
 */
public class CompanionGameTests {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
			}
		}
	}

	private static ServerPlayer owner(GameTestHelper helper, double x, double z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(x, 1, z));
		player.snapTo(pos.x, pos.y, pos.z, 0, 0);
		return player;
	}

	private static void removePlayer(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static CompanionEntity companion(GameTestHelper helper, Archetype archetype, String name, double x, double z) {
		CompanionEntity companion = helper.spawn(ModEntities.COMPANION, new Vec3(x, 1, z));
		companion.initIdentity(archetype, name, CompanionSkin.ARI, false);
		return companion;
	}

	private static void tree(GameTestHelper helper, int x, int z, int height) {
		for (int y = 1; y <= height; y++) {
			helper.setBlock(x, y, z, Blocks.OAK_LOG);
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					helper.setBlock(x + dx, height, z + dz, Blocks.OAK_LEAVES);
				}
			}
		}
		helper.setBlock(x, height + 1, z, Blocks.OAK_LEAVES);
	}

	@GameTest(maxTicks = 40)
	public void saveAndLoadKeepsIdentityOrdersAndMemory(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = owner(helper, 1.5, 1.5);
		CompanionEntity original = companion(helper, Archetype.GUARDIAN, "Seren", 3.5, 3.5);
		original.setSkinVariant(CompanionSkin.NOOR.encode(true));
		Conversations.apply(player, original, Intent.of(IntentType.RECRUIT));
		original.setOrder(new WaitOrder(original.blockPosition()));
		original.inventory().addItem(new ItemStack(Items.OAK_LOG, 12));
		original.memory().addPlayerLine("hello there");
		original.memory().addCompanionLine("Well met.");
		original.memory().addEvent("fought a zombie with {player}");

		ServerLevel level = helper.getLevel();
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		original.saveWithoutId(out);
		CompoundTag tag = out.buildResult();

		CompanionEntity copy = ModEntities.COMPANION.create(level, EntitySpawnReason.LOAD);
		copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));

		helper.assertValueEqual(copy.companionName(), "Seren", "name");
		helper.assertValueEqual(copy.archetype(), Archetype.GUARDIAN, "archetype");
		helper.assertValueEqual(copy.skinVariant(), CompanionSkin.NOOR.encode(true), "skin");
		helper.assertTrue(CompanionSkin.decodeSlim(copy.skinVariant()), "slim model kept");
		helper.assertValueEqual(copy.ownerId(), player.getUUID(), "owner");
		helper.assertValueEqual(copy.baseMode(), CompanionOrder.Kind.WAIT, "base mode");
		helper.assertValueEqual(copy.order().kind(), CompanionOrder.Kind.WAIT, "order");
		helper.assertValueEqual(copy.countCarried(s -> s.is(Items.OAK_LOG)), 12, "inventory");
		helper.assertValueEqual(copy.memory().lines().size(), 2, "conversation lines");
		helper.assertValueEqual(copy.memory().events().size(), original.memory().events().size(), "events");
		removePlayer(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 900)
	public void gatherLogsAndHandThemOver(GameTestHelper helper) {
		floor(helper);
		tree(helper, 5, 5, 4);
		ServerPlayer player = owner(helper, 1.5, 1.5);
		CompanionEntity companion = companion(helper, Archetype.FARMHAND, "Pip", 2.5, 2.5);
		Conversations.apply(player, companion, Intent.of(IntentType.RECRUIT));
		Conversations.Outcome outcome = Conversations.apply(player, companion, Intent.gather("oak logs", 3));
		helper.assertTrue(outcome.accepted(), "gather order accepted");
		helper.assertValueEqual(companion.order().kind(), CompanionOrder.Kind.GATHER, "now gathering");
		helper.succeedWhen(() -> {
			int received = player.getInventory().countItem(Items.OAK_LOG);
			helper.assertTrue(received >= 3, "player received 3 oak logs (has " + received + ")");
			helper.assertValueEqual(companion.order().kind(), CompanionOrder.Kind.FOLLOW, "back to following");
			helper.assertValueEqual(companion.lastLineKey(), "done.delivered", "announced only after delivery");
			removePlayer(helper, player);
		});
	}

	@GameTest(maxTicks = 300)
	public void gatherReportsNothingFound(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = owner(helper, 1.5, 1.5);
		CompanionEntity companion = companion(helper, Archetype.ROGUE, "Marlo", 3.5, 3.5);
		Conversations.apply(player, companion, Intent.of(IntentType.RECRUIT));
		helper.assertTrue(Conversations.apply(player, companion, Intent.gather("diamonds", 2)).accepted(), "order starts");
		helper.succeedWhen(() -> {
			helper.assertValueEqual(companion.lastLineKey(), "fail.none_found", "explains nothing was found");
			helper.assertValueEqual(companion.order().kind(), CompanionOrder.Kind.FOLLOW, "returns to following");
			helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND), 0, "no invented diamonds");
			removePlayer(helper, player);
		});
	}

	@GameTest(maxTicks = 300)
	public void stoneNeedsAPickaxe(GameTestHelper helper) {
		floor(helper);
		for (int x = 4; x < 7; x++) {
			helper.setBlock(x, 1, 5, Blocks.STONE);
		}
		ServerPlayer player = owner(helper, 1.5, 1.5);
		CompanionEntity companion = companion(helper, Archetype.SELLSWORD, "Bram", 2.5, 2.5);
		Conversations.apply(player, companion, Intent.of(IntentType.RECRUIT));
		helper.assertTrue(Conversations.apply(player, companion, Intent.gather("cobblestone", 4)).accepted(), "order starts");
		helper.succeedWhen(() -> {
			helper.assertValueEqual(companion.lastLineKey(), "fail.needs_tool", "asks for a pickaxe");
			helper.assertBlockPresent(Blocks.STONE, 4, 1, 5);
			removePlayer(helper, player);
		});
	}

	@GameTest(maxTicks = 200)
	public void cancellingStopsTheTask(GameTestHelper helper) {
		floor(helper);
		tree(helper, 6, 6, 5);
		ServerPlayer player = owner(helper, 1.5, 1.5);
		CompanionEntity companion = companion(helper, Archetype.NATURALIST, "Wren", 2.5, 2.5);
		Conversations.apply(player, companion, Intent.of(IntentType.RECRUIT));
		Conversations.apply(player, companion, Intent.gather("logs", 10));
		helper.runAfterDelay(10, () -> {
			Conversations.Outcome outcome = Conversations.apply(player, companion, Intent.of(IntentType.CANCEL));
			helper.assertTrue(outcome.accepted(), "cancel accepted");
			helper.assertValueEqual(outcome.lineKey(), "ack.cancel", "acknowledges cancelling the task");
			helper.assertValueEqual(companion.order().kind(), CompanionOrder.Kind.FOLLOW, "back to following");
		});
		helper.runAfterDelay(150, () -> {
			// Nothing broken after cancelling: every log still there.
			for (int y = 1; y <= 5; y++) {
				helper.assertBlockPresent(Blocks.OAK_LOG, 6, y, 6);
			}
			removePlayer(helper, player);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void followerTeleportsWhenLeftBehind(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = owner(helper, 1.5, 1.5);
		CompanionEntity companion = companion(helper, Archetype.MYSTIC, "Sable", 3.5, 3.5);
		Conversations.apply(player, companion, Intent.of(IntentType.RECRUIT));
		BlockPos far = helper.absolutePos(new BlockPos(1, 1, 1)).offset(40, 0, 0);
		ServerLevel level = helper.getLevel();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				level.setBlockAndUpdate(far.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(far.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(far.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
			}
		}
		player.snapTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 0, 0);
		helper.succeedWhen(() -> {
			helper.assertTrue(companion.distanceTo(player) < 6, "caught up (distance " + companion.distanceTo(player) + ")");
			helper.assertTrue(companion.level().getBlockState(companion.blockPosition().below()).isSolidRender(), "landed on solid ground");
			removePlayer(helper, player);
		});
	}

	@GameTest(maxTicks = 40)
	public void essentialCompanionsAreDownedNotKilled(GameTestHelper helper) {
		floor(helper);
		CompanionEntity companion = companion(helper, Archetype.SELLSWORD, "Hilde", 3.5, 3.5);
		companion.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 100.0F);
		helper.assertTrue(companion.isAlive(), "still alive");
		helper.assertTrue(companion.isDowned(), "knocked down");
		helper.assertValueEqual(companion.lastLineKey(), "downed", "says they're down");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void guardianRefusesToAttackVillagers(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = owner(helper, 1.5, 1.5);
		CompanionEntity companion = companion(helper, Archetype.GUARDIAN, "Aldric", 3.5, 3.5);
		Conversations.apply(player, companion, Intent.of(IntentType.RECRUIT));
		Villager villager = helper.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new Vec3(5.5, 1, 5.5));
		Conversations.Outcome outcome = Conversations.apply(player, companion, Intent.attack("villager"));
		helper.assertFalse(outcome.accepted(), "refused");
		helper.assertValueEqual(outcome.lineKey(), "fail.refuse_target", "explains the refusal");
		helper.assertTrue(companion.getTarget() != villager, "not targeting the villager");
		removePlayer(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void strangersDontTakeOrdersAndPartyIsLimited(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = owner(helper, 1.5, 1.5);
		CompanionEntity stranger = companion(helper, Archetype.ROGUE, "Cass", 3.5, 3.5);
		Conversations.Outcome refused = Conversations.apply(player, stranger, Intent.gather("logs", 4));
		helper.assertFalse(refused.accepted(), "stranger refuses orders");
		helper.assertValueEqual(refused.lineKey(), "not_recruited", "explains they must be recruited");

		CompanionEntity[] party = {
				companion(helper, Archetype.SELLSWORD, "Torvald", 2.5, 5.5),
				companion(helper, Archetype.NATURALIST, "Hazel", 4.5, 5.5),
				companion(helper, Archetype.FARMHAND, "Odo", 6.5, 5.5)};
		for (CompanionEntity member : party) {
			helper.assertTrue(Conversations.apply(player, member, Intent.of(IntentType.RECRUIT)).accepted(), "recruited " + member.companionName());
		}
		Conversations.Outcome full = Conversations.apply(player, stranger, Intent.of(IntentType.RECRUIT));
		helper.assertFalse(full.accepted(), "party limit enforced");
		helper.assertValueEqual(full.lineKey(), "recruit.party_full", "explains the party is full");
		removePlayer(helper, player);
		helper.succeed();
	}
}

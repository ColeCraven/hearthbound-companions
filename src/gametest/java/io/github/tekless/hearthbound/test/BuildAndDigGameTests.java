package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import io.github.tekless.hearthbound.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Digging and building: the world really changes, the companion uses what's in its pack, and it
 * refuses (with the real reason) when it can't do the job safely.
 */
public class BuildAndDigGameTests {
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

	private static CompanionEntity recruited(GameTestHelper helper, ServerPlayer owner, Archetype archetype, String name, double x, double z) {
		CompanionEntity companion = helper.spawn(ModEntities.COMPANION, new Vec3(x, 1, z));
		companion.initIdentity(archetype, name, CompanionSkin.STEVE, false);
		Conversations.apply(owner, companion, Intent.of(IntentType.RECRUIT));
		return companion;
	}

	private static void remove(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	@GameTest(maxTicks = 600)
	public void tunnelThroughStone(GameTestHelper helper) {
		floor(helper);
		for (int x = 2; x <= 7; x++) {
			for (int y = 1; y <= 3; y++) {
				for (int z = 2; z <= 4; z++) {
					helper.setBlock(x, y, z, Blocks.STONE);
				}
			}
		}
		ServerPlayer owner = player(helper, 1.5, 3.5, true);
		CompanionEntity miner = recruited(helper, owner, Archetype.SELLSWORD, "Ragna", 1.5, 1.5);
		miner.inventory().addItem(new ItemStack(Items.STONE_PICKAXE));
		Conversations.Outcome outcome = Conversations.apply(owner, miner, Intent.mine("tunnel", 4));
		helper.assertTrue(outcome.accepted(), "tunnel order accepted (" + outcome.lineKey() + ")");
		helper.succeedWhen(() -> {
			for (int x = 2; x <= 5; x++) {
				helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, 1, 3));
				helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, 2, 3));
			}
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(6, 1, 3)); // stopped where asked
			helper.assertTrue(miner.countCarried(s -> s.is(Items.COBBLESTONE)) >= 8, "the stone went into the pack");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 400)
	public void diggingStopsBeforeLava(GameTestHelper helper) {
		floor(helper);
		for (int x = 2; x <= 7; x++) {
			for (int y = 1; y <= 3; y++) {
				for (int z = 2; z <= 4; z++) {
					helper.setBlock(x, y, z, Blocks.STONE);
				}
			}
		}
		helper.setBlock(4, 1, 3, Blocks.LAVA);
		ServerPlayer owner = player(helper, 1.5, 3.5, true);
		CompanionEntity miner = recruited(helper, owner, Archetype.GUARDIAN, "Sigrun", 1.5, 1.5);
		miner.inventory().addItem(new ItemStack(Items.IRON_PICKAXE));
		helper.assertTrue(Conversations.apply(owner, miner, Intent.mine("tunnel", 6)).accepted(), "order accepted");
		helper.succeedWhen(() -> {
			helper.assertValueEqual(miner.lastLineKey(), "fail.mine_hazard", "stopped and said why");
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 3)); // the block holding the lava back
			helper.assertBlockPresent(Blocks.LAVA, new BlockPos(4, 1, 3));
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 40)
	public void noPickaxeNoDigging(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 1.5, 3.5, true);
		CompanionEntity companion = recruited(helper, owner, Archetype.FARMHAND, "Bess", 1.5, 1.5);
		Conversations.Outcome outcome = Conversations.apply(owner, companion, Intent.mine("trip", null));
		helper.assertFalse(outcome.accepted(), "refused");
		helper.assertValueEqual(outcome.lineKey(), "fail.needs_tool", "asks for a pickaxe");
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 900)
	public void buildsAShelterFromPlanks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 0.5, false);
		CompanionEntity builder = recruited(helper, owner, Archetype.NATURALIST, "Linnea", 3.5, 1.5);
		builder.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 64));
		builder.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 16));
		Conversations.Outcome outcome = Conversations.apply(owner, builder, Intent.build("house"));
		helper.assertTrue(outcome.accepted(), "build accepted (" + outcome.lineKey() + ")");
		helper.succeedWhen(() -> {
			helper.assertValueEqual(builder.lastLineKey(), "done.build", "finished");
			// walls, roof, and an open doorway facing the player
			BlockPos wall = helper.absolutePos(new BlockPos(1, 1, 4));
			BlockPos roof = helper.absolutePos(new BlockPos(3, 4, 4));
			helper.assertTrue(helper.getLevel().getBlockState(wall).is(Blocks.OAK_PLANKS), "side wall built");
			helper.assertTrue(helper.getLevel().getBlockState(roof).is(Blocks.OAK_PLANKS), "roof built");
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 2));
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, 2));
			helper.assertValueEqual(builder.countCarried(s -> s.is(Items.OAK_PLANKS)), 80 - 71, "used exactly the planks it placed");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 40)
	public void refusesToBuildWithoutEnoughBlocks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 0.5, false);
		CompanionEntity builder = recruited(helper, owner, Archetype.ROGUE, "Nim", 3.5, 1.5);
		builder.inventory().addItem(new ItemStack(Items.COBBLESTONE, 10));
		Conversations.Outcome outcome = Conversations.apply(owner, builder, Intent.build("shelter"));
		helper.assertFalse(outcome.accepted(), "refused");
		helper.assertValueEqual(outcome.lineKey(), "fail.build_materials", "says what's missing");
		helper.assertValueEqual(outcome.vars().get("need"), "71", "needs 71");
		helper.assertValueEqual(outcome.vars().get("have"), "10", "has 10");
		Conversations.Outcome castle = Conversations.apply(owner, builder, Intent.build("castle"));
		helper.assertValueEqual(castle.lineKey(), "fail.build_unknown", "a castle is beyond them");
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void bridgesWater(GameTestHelper helper) {
		floor(helper);
		for (int x = 0; x < 8; x++) {
			for (int z = 2; z <= 4; z++) {
				helper.setBlock(x, 0, z, Blocks.WATER);
			}
		}
		ServerPlayer owner = player(helper, 3.5, 1.5, false);
		CompanionEntity builder = recruited(helper, owner, Archetype.FARMHAND, "Clem", 2.5, 1.5);
		builder.inventory().addItem(new ItemStack(Items.COBBLESTONE, 10));
		helper.assertTrue(Conversations.apply(owner, builder, Intent.build("bridge")).accepted(), "bridge accepted");
		helper.succeedWhen(() -> {
			for (int z = 2; z <= 4; z++) {
				helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(3, 0, z));
			}
			helper.assertValueEqual(builder.countCarried(s -> s.is(Items.COBBLESTONE)), 7, "three blocks used");
			remove(helper, owner);
		});
	}
}

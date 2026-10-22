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
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Getting to you without a teleport: up a ladder, up out of a hole on blocks from the pack, across a small gap on a
 * bridge (but never beside something a player built), and through water.
 */
public class MovementGameTests {
	private static void solid(GameTestHelper helper, int x1, int y1, int z1, int x2, int y2, int z2) {
		for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2))) {
			helper.setBlock(pos, Blocks.STONE);
		}
	}

	private static void air(GameTestHelper helper, int x1, int y1, int z1, int x2, int y2, int z2) {
		for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2))) {
			helper.setBlock(pos, Blocks.AIR);
		}
	}

	private static ServerPlayer player(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(x, y, z));
		player.snapTo(pos.x, pos.y, pos.z, 0, 0);
		return player;
	}

	private static CompanionEntity follower(GameTestHelper helper, ServerPlayer owner, Archetype archetype, String name, double x, double y, double z) {
		CompanionEntity companion = helper.spawn(ModEntities.COMPANION, new Vec3(x, y, z));
		companion.initIdentity(archetype, name, CompanionSkin.STEVE, false);
		Conversations.apply(owner, companion, Intent.of(IntentType.RECRUIT));
		return companion;
	}

	private static void remove(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	/** Fails the test if the companion ever jumps more than a couple of blocks in one tick (a teleport). */
	private static void noTeleports(GameTestHelper helper, CompanionEntity companion) {
		Vec3[] last = {companion.position()};
		helper.onEachTick(() -> {
			if (companion.position().distanceTo(last[0]) > 2.5) {
				helper.fail("teleported instead of finding a way");
			}
			last[0] = companion.position();
		});
	}

	@GameTest(maxTicks = 500, padding = 40)
	public void aFollowerClimbsALadderToReachYou(GameTestHelper helper) {
		solid(helper, 0, 0, 0, 7, 0, 7);
		solid(helper, 4, 1, 2, 6, 4, 4); // a block of rock four high
		for (int y = 1; y <= 4; y++) {
			helper.setBlock(new BlockPos(3, y, 3), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		}
		ServerPlayer owner = player(helper, 5.5, 5, 3.5);
		CompanionEntity daisy = follower(helper, owner, Archetype.GUARDIAN, "Gtdai", 1.5, 1, 3.5);
		noTeleports(helper, daisy);
		boolean[] climbed = {false};
		helper.onEachTick(() -> climbed[0] |= daisy.onClimbable());
		double top = helper.absoluteVec(new Vec3(0, 5, 0)).y;
		helper.succeedWhen(() -> {
			helper.assertTrue(climbed[0], "went up the ladder");
			helper.assertTrue(daisy.getY() >= top - 0.2 && daisy.onGround(), "and is up top with you (y " + daisy.getY() + ")");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 500, padding = 40)
	public void aFollowerBuildsTheirWayOutOfAHole(GameTestHelper helper) {
		solid(helper, 0, 0, 0, 7, 3, 7);
		air(helper, 3, 1, 3, 3, 3, 3); // a pit three deep, one wide
		ServerPlayer owner = player(helper, 6.5, 4, 3.5);
		CompanionEntity samuel = follower(helper, owner, Archetype.FARMHAND, "Gtsam", 3.5, 1, 3.5);
		samuel.inventory().addItem(new ItemStack(Items.COBBLESTONE, 8));
		noTeleports(helper, samuel);
		double top = helper.absoluteVec(new Vec3(0, 4, 0)).y;
		helper.succeedWhen(() -> {
			helper.assertTrue(samuel.getY() >= top - 0.2, "out of the hole (y " + samuel.getY() + ")");
			helper.assertValueEqual(samuel.countCarried(s -> s.is(Items.COBBLESTONE)), 5, "on three blocks from the pack");
			helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(3, 1, 3));
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 500, padding = 40)
	public void aFollowerBridgesASmallGap(GameTestHelper helper) {
		solid(helper, 0, 0, 0, 7, 4, 7);
		air(helper, 3, 0, 0, 4, 4, 7); // a trench two wide and too deep to climb out of
		ServerPlayer owner = player(helper, 7.5, 5, 3.5);
		CompanionEntity cole = follower(helper, owner, Archetype.ROGUE, "Gtcol", 1.5, 5, 3.5);
		cole.inventory().addItem(new ItemStack(Items.COBBLESTONE, 4));
		noTeleports(helper, cole);
		double far = helper.absoluteVec(new Vec3(5, 0, 0)).x;
		helper.succeedWhen(() -> {
			helper.assertTrue(cole.getX() >= far, "across the gap");
			helper.assertValueEqual(cole.countCarried(s -> s.is(Items.COBBLESTONE)), 2, "on a two-block bridge");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 300, padding = 40)
	public void noBridgeBesideSomethingAPlayerBuilt(GameTestHelper helper) {
		solid(helper, 0, 0, 0, 7, 4, 7);
		air(helper, 3, 0, 0, 4, 4, 7);
		helper.setBlock(1, 5, 5, Blocks.OAK_PLANKS); // someone's build, right by the gap
		ServerPlayer owner = player(helper, 7.5, 5, 3.5);
		CompanionEntity maddie = follower(helper, owner, Archetype.ROGUE, "Gtmdd", 1.5, 5, 3.5);
		maddie.inventory().addItem(new ItemStack(Items.COBBLESTONE, 4));
		helper.runAfterDelay(200, () -> {
			for (int z = 0; z <= 7; z++) {
				helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 4, z));
				helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, 4, z));
			}
			helper.assertValueEqual(maddie.countCarried(s -> s.is(Items.COBBLESTONE)), 4, "no blocks placed");
			remove(helper, owner);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 500, padding = 40)
	public void aFollowerSwimsAfterYou(GameTestHelper helper) {
		solid(helper, 0, 0, 0, 7, 2, 7);
		for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 0), new BlockPos(2, 2, 7))) {
			helper.setBlock(pos, Blocks.WATER);
		}
		ServerPlayer owner = player(helper, 7.5, 3, 3.5);
		CompanionEntity hazel = follower(helper, owner, Archetype.NATURALIST, "Gthzl", 0.5, 3, 3.5);
		noTeleports(helper, hazel);
		helper.succeedWhen(() -> {
			helper.assertTrue(hazel.distanceTo(owner) < 4.5 && !hazel.isInWater(), "swam across and climbed out (" + hazel.distanceTo(owner) + ")");
			remove(helper, owner);
		});
	}
}

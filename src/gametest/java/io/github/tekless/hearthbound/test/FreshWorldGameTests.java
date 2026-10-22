package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import io.github.tekless.hearthbound.entity.order.GatherOrder;
import io.github.tekless.hearthbound.entity.order.ProjectOrder;
import io.github.tekless.hearthbound.life.HomeAreas;
import io.github.tekless.hearthbound.life.Places;
import io.github.tekless.hearthbound.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * A brand-new world: nothing built, no beds, no farm, no chests. Companions living there fetch the basics, put up a
 * first shelter, say what they need without going on about it, and use a bed and a chest as soon as one goes down.
 * Nothing here relies on a hub or on any fixed spot.
 */
public class FreshWorldGameTests {
	private static void ground(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
			}
		}
	}

	private static CompanionEntity settler(GameTestHelper helper, Archetype archetype, String name, int x, int z, boolean autonomy) {
		CompanionEntity companion = helper.spawn(ModEntities.COMPANION, new Vec3(x + 0.5, 1, z + 0.5));
		companion.initIdentity(archetype, name, CompanionSkin.STEVE, false);
		companion.setHomePos(helper.absolutePos(new BlockPos(x, 1, z)));
		companion.setTestClock(2000L); // a working morning
		companion.setDailyLife(true);
		companion.setTestAutonomy(autonomy);
		return companion;
	}

	private static ServerPlayer visitor(GameTestHelper helper, double x, double z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(x, 1, z));
		player.snapTo(pos.x, pos.y, pos.z, 0, 0);
		return player;
	}

	private static void remove(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	@GameTest(maxTicks = 400, padding = 40)
	public void aCollectorInABareWorldFetchesLogsFirst(GameTestHelper helper) {
		ground(helper, 8);
		for (int y = 1; y <= 4; y++) {
			helper.setBlock(6, y, 6, Blocks.OAK_LOG);
		}
		helper.setBlock(6, 5, 6, Blocks.OAK_LEAVES);
		CompanionEntity fern = settler(helper, Archetype.NATURALIST, "Gtfrn", 2, 2, true);
		helper.succeedWhen(() -> helper.assertTrue(fern.order() instanceof GatherOrder && fern.order().describe().contains("log"),
				"nothing in store, so logs first (" + fern.order().describe() + ")"));
	}

	@GameTest(maxTicks = 400, padding = 40)
	public void aBuilderWithNoRoofPutsUpAShelter(GameTestHelper helper) {
		ground(helper, 16);
		CompanionEntity moss = settler(helper, Archetype.MYSTIC, "Gtmos", 7, 7, true);
		moss.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 64));
		moss.inventory().addItem(new ItemStack(Items.OAK_LOG, 16));
		helper.succeedWhen(() -> helper.assertTrue(moss.order() instanceof ProjectOrder project && project.label().contains("shelter"),
				"no roof and no beds: a shelter of their own (" + moss.order().describe() + ")"));
	}

	@GameTest(maxTicks = 500, padding = 40)
	public void needsAreSaidOnceNotOnALoop(GameTestHelper helper) {
		ground(helper, 8);
		ServerPlayer player = visitor(helper, 5.5, 5.5);
		CompanionEntity sage = settler(helper, Archetype.MYSTIC, "Gtsag", 2, 2, false);
		int[] said = {0};
		int[] lines = {0};
		helper.onEachTick(() -> {
			int now = sage.memory().lines().size();
			if (now != lines[0] && "life.need".equals(sage.lastLineKey())) {
				said[0]++;
			}
			lines[0] = now;
		});
		helper.runAfterDelay(440, () -> {
			helper.assertValueEqual(said[0], 1, "said what they'd need once, then left it");
			helper.assertFalse(sage.tryEvery("need:a roof over our heads", 0, 1), "and it's noted for the day");
			remove(helper, player);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100, padding = 40)
	public void aBedAndChestPutDownLaterAreUsed(GameTestHelper helper) {
		ground(helper, 8);
		CompanionEntity wynn = settler(helper, Archetype.FARMHAND, "Gtwyn", 2, 2, false);
		HomeAreas areas = HomeAreas.get(helper.getLevel().getServer());
		HomeAreas.Area area = areas.areaOf(wynn);
		Places before = areas.places(helper.getLevel(), area);
		helper.assertTrue(before.storage().isEmpty(), "no chest yet");
		helper.assertTrue(areas.bedFor(helper.getLevel(), area, wynn) == null, "no bed yet");
		Block bedBlock = Blocks.AIR;
		for (Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
			if (block instanceof BedBlock) {
				bedBlock = block;
				break;
			}
		}
		BlockState head = bedBlock.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD);
		helper.setBlock(5, 1, 2, head);
		helper.setBlock(5, 1, 3, head.setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(2, 1, 5, Blocks.CHEST);
		areas.rescanSoon(area);
		Places after = areas.places(helper.getLevel(), area);
		helper.assertTrue(after.storage().contains(helper.absolutePos(new BlockPos(2, 1, 5))), "the new chest is the store: " + after.storage());
		helper.assertValueEqual(areas.bedFor(helper.getLevel(), area, wynn), helper.absolutePos(new BlockPos(5, 1, 2)), "and the new bed is theirs");
		helper.succeed();
	}
}

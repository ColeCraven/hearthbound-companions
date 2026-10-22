package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.core.life.Activity;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import io.github.tekless.hearthbound.entity.order.CompanionOrder;
import io.github.tekless.hearthbound.entity.order.FarmOrder;
import io.github.tekless.hearthbound.entity.order.HomeOrder;
import io.github.tekless.hearthbound.life.CatchUp;
import io.github.tekless.hearthbound.life.Favours;
import io.github.tekless.hearthbound.life.HomeAreas;
import io.github.tekless.hearthbound.life.Places;
import io.github.tekless.hearthbound.life.Zone;
import io.github.tekless.hearthbound.registry.ModEntities;
import io.github.tekless.hearthbound.world.Obey;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * Daily life at home: the schedule by the clock, sleeping in a bed of their own, marking places, catching up on
 * time away, the evening fire, favours, orders coming first, and what happens in a brand-new world.
 *
 * <p>Daily life is switched on per companion here (the older tests run with it off), and every test has room
 * round it so the homes of two tests never count as one.
 */
public class LifeGameTests {
	static {
		// The tests written before daily life existed expect companions to stay put at home, so it is off for them;
		// the tests below switch it on companion by companion.
		Hearthbound.config().dailyLife = false;
	}

	private static void floor(GameTestHelper helper, Block block) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, block);
			}
		}
	}

	private static ServerPlayer player(GameTestHelper helper, double x, double z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(x, 1, z));
		player.snapTo(pos.x, pos.y, pos.z, 0, 0);
		return player;
	}

	private static void remove(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	/** Someone who lives here, with daily life on and the clock fixed at {@code clock}. */
	private static CompanionEntity resident(GameTestHelper helper, Archetype archetype, String name, int x, int z, long clock) {
		CompanionEntity companion = helper.spawn(ModEntities.COMPANION, new Vec3(x + 0.5, 1, z + 0.5));
		companion.initIdentity(archetype, name, CompanionSkin.STEVE, false);
		companion.setHomePos(helper.absolutePos(new BlockPos(x, 1, z)));
		companion.setTestClock(clock);
		companion.setDailyLife(true);
		return companion;
	}

	private static Activity activity(CompanionEntity companion) {
		return companion.order() instanceof HomeOrder home ? home.activity() : null;
	}

	private static BlockState at(GameTestHelper helper, int x, int y, int z) {
		return helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z)));
	}

	/** A bed whose head is at (x, 1, z), lying north to south. */
	private static void bed(GameTestHelper helper, int x, int z) {
		Block bedBlock = Blocks.AIR;
		for (Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
			if (block instanceof BedBlock) {
				bedBlock = block;
				break;
			}
		}
		BlockState head = bedBlock.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD);
		helper.setBlock(x, 1, z, head);
		helper.setBlock(x, 1, z + 1, head.setValue(BedBlock.PART, BedPart.FOOT));
	}

	// ------------------------------------------------------------------ the schedule

	@GameTest(maxTicks = 400, padding = 40)
	public void theScheduleFollowsTheClock(GameTestHelper helper) {
		floor(helper, Blocks.GRASS_BLOCK);
		// A farmhand: up before six, works the morning, eats at noon, spends the evening with the others, sleeps at night.
		CompanionEntity honey = resident(helper, Archetype.FARMHAND, "Hunxx", 3, 3, 2000);
		long[] clocks = {2000, 6000, 12000, 18000};
		Activity[] expected = {Activity.WORK, Activity.MEAL, Activity.SOCIAL, Activity.SLEEP};
		int[] step = {0};
		helper.onEachTick(() -> {
			if (step[0] < clocks.length && activity(honey) == expected[step[0]]) {
				step[0]++;
				if (step[0] < clocks.length) {
					honey.setTestClock(clocks[step[0]]);
				}
			}
		});
		helper.succeedWhen(() -> helper.assertValueEqual(step[0], clocks.length, "went through work, a meal, the evening and bed ("
				+ activity(honey) + " at step " + step[0] + ")"));
	}

	@GameTest(maxTicks = 400, padding = 40)
	public void aResidentSleepsInTheirOwnBed(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		bed(helper, 5, 2);
		CompanionEntity luna = resident(helper, Archetype.NATURALIST, "Lunxx", 2, 5, 18000);
		BlockPos head = helper.absolutePos(new BlockPos(5, 1, 2));
		helper.succeedWhen(() -> {
			helper.assertTrue(luna.isSleeping(), "asleep (" + luna.order().describe() + ")");
			helper.assertValueEqual(luna.getSleepingPos().orElse(null), head, "in the bed");
			HomeAreas areas = HomeAreas.get(helper.getLevel().getServer());
			helper.assertValueEqual(areas.bedFor(helper.getLevel(), areas.areaOf(luna), luna), head, "and it is theirs");
		});
	}

	@GameTest(maxTicks = 500, padding = 40)
	public void ordersComeBeforeTheSchedule(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		bed(helper, 5, 2);
		CompanionEntity sleeper = resident(helper, Archetype.NATURALIST, "Slexx", 2, 5, 18000);
		ServerPlayer host = player(helper, 1.5, 1.5);
		Obey.addTestHost(host.getUUID());
		boolean obeyAll = Hearthbound.config().obeyAll;
		Hearthbound.config().obeyAll = true;
		boolean[] ordered = {false};
		helper.onEachTick(() -> {
			if (!ordered[0] && sleeper.isSleeping()) {
				ordered[0] = true;
				helper.assertTrue(Conversations.onChat(host, "@slexx come here"), "heard");
				helper.assertFalse(sleeper.isSleeping(), "woken by the order");
				helper.assertValueEqual(sleeper.order().kind(), CompanionOrder.Kind.COME, "and on their way");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(ordered[0], "slept first");
			helper.assertValueEqual(sleeper.order().kind(), CompanionOrder.Kind.HOME, "back to their day once the order is done");
			Obey.removeTestHost(host.getUUID());
			Hearthbound.config().obeyAll = obeyAll;
			remove(helper, host);
		});
	}

	// ------------------------------------------------------------------ places

	@GameTest(maxTicks = 100, padding = 40)
	public void setplaceMarksTheSpotYouStandOn(GameTestHelper helper) {
		floor(helper, Blocks.GRASS_BLOCK);
		MinecraftServer server = helper.getLevel().getServer();
		CompanionEntity sam = resident(helper, Archetype.FARMHAND, "Samxx", 2, 2, 2000);
		sam.setDailyLife(false); // only their home matters here
		bed(helper, 6, 2);
		helper.setBlock(6, 1, 6, Blocks.CHEST);
		for (int x = 1; x <= 3; x++) {
			helper.setBlock(x, 0, 6, Blocks.FARMLAND);
		}
		ServerPlayer host = player(helper, 6.5, 4.5);
		Obey.addTestHost(host.getUUID());
		server.getCommands().performPrefixedCommand(host.createCommandSourceStack(), "companion setplace storage samxx");
		host.snapTo(helper.absoluteVec(new Vec3(6.5, 1, 1.5)));
		server.getCommands().performPrefixedCommand(host.createCommandSourceStack(), "companion setplace bed samxx");
		host.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 6.5)));
		server.getCommands().performPrefixedCommand(host.createCommandSourceStack(), "companion setplace farm samxx");
		host.snapTo(helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
		server.getCommands().performPrefixedCommand(host.createCommandSourceStack(), "companion setplace commons samxx");
		HomeAreas areas = HomeAreas.get(server);
		HomeAreas.Area area = areas.areaOf(sam);
		Places places = areas.places(helper.getLevel(), area);
		helper.assertTrue(places.storage().contains(helper.absolutePos(new BlockPos(6, 1, 6))), "the chest is their storage");
		helper.assertValueEqual(areas.bedFor(helper.getLevel(), area, sam), helper.absolutePos(new BlockPos(6, 1, 2)), "the bed is Sam's");
		helper.assertTrue(places.farm() != null && places.farmMarked() && places.farm().contains(helper.absolutePos(new BlockPos(2, 0, 6))), "the farmland is the farm");
		helper.assertValueEqual(places.commons(), helper.absolutePos(new BlockPos(4, 1, 4)), "and this is where they meet");
		Obey.removeTestHost(host.getUUID());
		remove(helper, host);
		helper.succeed();
	}

	// ------------------------------------------------------------------ catching up

	@GameTest(maxTicks = 40, padding = 40)
	public void catchUpHarvestsTheFarmIntoTheStorageChestOnly(GameTestHelper helper) {
		floor(helper, Blocks.GRASS_BLOCK);
		ServerLevel level = helper.getLevel();
		for (int x = 2; x <= 4; x++) {
			for (int z = 2; z <= 4; z++) {
				helper.setBlock(x, 0, z, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
				helper.setBlock(x, 1, z, Blocks.WHEAT.defaultBlockState());
			}
		}
		// A crop outside the farm, a storage chest, and somebody else's chest.
		helper.setBlock(6, 0, 6, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
		helper.setBlock(6, 1, 6, Blocks.WHEAT.defaultBlockState());
		helper.setBlock(1, 1, 6, Blocks.CHEST);
		helper.setBlock(6, 1, 1, Blocks.CHEST);
		CompanionEntity farmer = resident(helper, Archetype.FARMHAND, "Farxx", 1, 1, 2000);
		farmer.setDailyLife(false); // the catch-up alone, with nobody working live
		HomeAreas areas = HomeAreas.get(level.getServer());
		HomeAreas.Area area = areas.areaOf(farmer);
		areas.markFarm(area, Zone.of(helper.absolutePos(new BlockPos(2, 0, 2)), helper.absolutePos(new BlockPos(4, 1, 4))));
		areas.markStorage(area, helper.absolutePos(new BlockPos(1, 1, 6)));

		CatchUp.Report report = CatchUp.run(level, areas, area, List.of(farmer), 3 * 24000L, new Random(7));
		helper.assertTrue(report != null && report.storedTotal() > 0, "three days on, the wheat was harvested");
		Container storage = (Container) level.getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 6)));
		Container other = (Container) level.getBlockEntity(helper.absolutePos(new BlockPos(6, 1, 1)));
		int wheat = 0;
		for (int slot = 0; slot < storage.getContainerSize(); slot++) {
			if (storage.getItem(slot).is(Items.WHEAT)) {
				wheat += storage.getItem(slot).getCount();
			}
		}
		helper.assertValueEqual(wheat, report.stored().getOrDefault(Items.WHEAT, 0), "every bit of wheat reported is in the storage chest");
		helper.assertTrue(other.isEmpty(), "the other chest was never touched");
		helper.assertValueEqual(at(helper, 6, 1, 6).getValue(CropBlock.AGE), 0, "the crop outside the farm was left alone");
		for (int x = 2; x <= 4; x++) {
			for (int z = 2; z <= 4; z++) {
				helper.assertTrue(at(helper, x, 1, z).getBlock() instanceof CropBlock, "replanted at " + x + "," + z);
			}
		}
		helper.assertTrue(area.pendingSummary != null && area.pendingSummary.contains("Farxx") && area.pendingSummary.contains(wheat + " wheat"),
				"the summary says who did what, honestly: " + area.pendingSummary);
		helper.succeed();
	}

	// ------------------------------------------------------------------ evenings, favours

	@GameTest(maxTicks = 400, padding = 40)
	public void theMeetingFireIsLitAtDusk(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		helper.setBlock(4, 1, 4, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		CompanionEntity fennel = resident(helper, Archetype.NATURALIST, "Fenxx", 2, 2, 12500);
		helper.succeedWhen(() -> {
			helper.assertValueEqual(activity(fennel), Activity.SOCIAL, "out for the evening");
			helper.assertTrue(at(helper, 4, 1, 4).getValue(CampfireBlock.LIT), "and the fire is lit");
		});
	}

	@GameTest(maxTicks = 40, padding = 40)
	public void aFavourIsPaidFromTheirOwnPack(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		CompanionEntity hazel = resident(helper, Archetype.NATURALIST, "Hazxx", 2, 2, 8000);
		hazel.setDailyLife(false);
		hazel.stockPack(); // a collector starts out with some apples and an emerald
		ServerPlayer visitor = player(helper, 3.5, 3.5);
		helper.assertTrue(Favours.maybeAsk(hazel, visitor, helper.getLevel().getGameTime()), "asks a favour, having something to give for it");
		helper.assertValueEqual(hazel.lastLineKey(), "life.favour.ask", "and says what for");
		helper.assertTrue(Favours.receive(hazel, visitor, new ItemStack(Items.OAK_LOG, 16)), "takes the logs");
		helper.assertValueEqual(visitor.getInventory().countItem(Items.EMERALD), 1, "pays with the emerald from their pack");
		helper.assertValueEqual(hazel.countCarried(s -> s.is(Items.EMERALD)), 0, "which is gone from it");
		helper.assertValueEqual(hazel.countCarried(s -> s.is(Items.OAK_LOG)), 16, "and keeps the logs");
		remove(helper, visitor);
		helper.succeed();
	}

	// ------------------------------------------------------------------ a brand-new world

	@GameTest(maxTicks = 800, padding = 40)
	public void aFarmerInANewWorldStartsAFarmByTheWater(GameTestHelper helper) {
		floor(helper, Blocks.GRASS_BLOCK);
		helper.setBlock(5, 0, 5, Blocks.WATER);
		boolean routines = Hearthbound.config().routines;
		Hearthbound.config().routines = true;
		CompanionEntity farmer = resident(helper, Archetype.FARMHAND, "Plaxx", 2, 2, 2000);
		farmer.inventory().addItem(new ItemStack(Items.WOODEN_HOE));
		farmer.inventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 8));
		helper.succeedWhen(() -> {
			helper.assertTrue(farmer.order() instanceof FarmOrder, "with seeds, a hoe and water, they start a farm of their own (" + farmer.order().describe() + ")");
			Hearthbound.config().routines = routines;
		});
	}

	@GameTest(maxTicks = 400, padding = 40)
	public void withNothingToWorkWithTheySayWhatTheyNeedOnce(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		boolean routines = Hearthbound.config().routines;
		Hearthbound.config().routines = false;
		ServerPlayer visitor = player(helper, 5.5, 5.5);
		CompanionEntity farmer = resident(helper, Archetype.FARMHAND, "Nedxx", 2, 2, 2000);
		boolean[] said = {false};
		helper.onEachTick(() -> said[0] |= farmer.lastLineKey().equals("life.need"));
		helper.succeedWhen(() -> {
			helper.assertTrue(said[0], "no farm and autonomy off: they say what they'd need");
			helper.assertFalse(farmer.tryEvery("need:a bit of farmland", farmer.clockTime() / 24000, 1), "and it's noted, so it isn't said again today");
			helper.assertValueEqual(farmer.order().kind(), CompanionOrder.Kind.HOME, "and they carry on with their day");
			Hearthbound.config().routines = routines;
			remove(helper, visitor);
		});
	}
}

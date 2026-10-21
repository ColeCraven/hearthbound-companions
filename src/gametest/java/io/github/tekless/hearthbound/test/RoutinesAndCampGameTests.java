package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import io.github.tekless.hearthbound.entity.Pastimes;
import io.github.tekless.hearthbound.entity.order.CompanionOrder;
import io.github.tekless.hearthbound.entity.order.Routines;
import io.github.tekless.hearthbound.registry.ModEntities;
import io.github.tekless.hearthbound.world.Exchanges;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;

/** Self-chosen routines (off unless switched on), the camp build, and chats between companions. */
public class RoutinesAndCampGameTests {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

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

	@GameTest(maxTicks = 300)
	public void farmerHarvestsAndReplantsOnlyWhenAutonomyIsOn(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(5, 0, 3, Blocks.FARMLAND);
		helper.setBlock(5, 1, 3, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
		ServerPlayer owner = player(helper, 3.5, 3.5, true);
		CompanionEntity farmer = recruited(helper, owner, Archetype.FARMHAND, "Fenxx", 4.5, 4.5);
		farmer.inventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 2));
		Routines.resetAllowance();
		boolean before = Hearthbound.config().routines;
		Hearthbound.config().routines = false;
		helper.assertTrue(Routines.pick(farmer, owner).isEmpty(), "nothing happens by itself when autonomy is off");
		Hearthbound.config().routines = true;
		Optional<Pastimes.Pastime> job = Routines.pick(farmer, owner);
		helper.assertTrue(job.isPresent(), "a ripe crop is a job when autonomy is on");
		helper.succeedWhen(() -> {
			job.get().tick(farmer, owner);
			helper.assertBlockProperty(new BlockPos(5, 1, 3), CropBlock.AGE, 0); // replanted
			helper.assertTrue(farmer.countCarried(s -> s.is(Items.WHEAT)) >= 1, "the harvest is in the pack");
			Hearthbound.config().routines = before;
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 40)
	public void routinesLeaveProtectedAndBuiltAreasAlone(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.DIRT); // no loose stone to fall back on
			}
		}
		helper.setBlock(5, 1, 3, Blocks.OAK_LOG);
		helper.setBlock(6, 1, 3, Blocks.OAK_PLANKS); // someone's build is right beside it
		ServerPlayer owner = player(helper, 3.5, 3.5, true);
		CompanionEntity collector = recruited(helper, owner, Archetype.NATURALIST, "Collxx", 4.5, 4.5);
		Routines.resetAllowance();
		boolean before = Hearthbound.config().routines;
		Hearthbound.config().routines = true;
		helper.assertTrue(Routines.pick(collector, owner).isEmpty(), "a log beside planks is left alone");
		helper.setBlock(6, 1, 3, Blocks.AIR);
		helper.assertTrue(Routines.pick(collector, owner).isPresent(), "a lone tree log is fair game");
		Hearthbound.config().routines = before;
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void buildersLightDarkSpotsOnlyWithTorches(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 3.5, true);
		CompanionEntity builder = recruited(helper, owner, Archetype.MYSTIC, "Buixx", 4.5, 4.5); // mystics build
		Routines.resetAllowance();
		boolean before = Hearthbound.config().routines;
		Hearthbound.config().routines = true;
		helper.assertTrue(Routines.pick(builder, owner).isEmpty(), "no torches, no job");
		Hearthbound.config().routines = before;
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 700)
	public void buildsACampWithAFire(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 1.5, 3.5, true);
		CompanionEntity builder = recruited(helper, owner, Archetype.ROGUE, "Camxx", 1.5, 5.5);
		builder.inventory().addItem(new ItemStack(Items.OAK_LOG, 8));
		Conversations.Outcome refused = Conversations.apply(owner, builder, Intent.build("camp"));
		helper.assertFalse(refused.accepted(), "no campfire, no camp");
		helper.assertValueEqual(refused.lineKey(), "fail.camp_campfire", "says what is missing");
		builder.inventory().addItem(new ItemStack(Items.CAMPFIRE));
		helper.assertTrue(Conversations.onChat(owner, "@camxx set up camp"), "heard");
		helper.assertValueEqual(builder.order().kind(), CompanionOrder.Kind.BUILD, "building the camp");
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.CAMPFIRE, new BlockPos(4, 1, 3));
			helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(6, 1, 3));
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 500)
	public void companionsChatToEachOther(GameTestHelper helper) {
		floor(helper);
		CompanionEntity a = helper.spawn(ModEntities.COMPANION, new Vec3(2.5, 1, 2.5));
		a.initIdentity(Archetype.SELLSWORD, "Axx", CompanionSkin.STEVE, false);
		CompanionEntity b = helper.spawn(ModEntities.COMPANION, new Vec3(4.5, 1, 2.5));
		b.initIdentity(Archetype.NATURALIST, "Bxx", CompanionSkin.STEVE, false);
		Exchanges.start(null, a, b, Exchanges.Topic.FOOD);
		helper.succeedWhen(() -> {
			Exchanges.tick(helper.getLevel().getGameTime());
			helper.assertTrue(a.lastLineKey().startsWith("exchange.food"), "A opened the chat (" + a.lastLineKey() + ")");
			helper.assertTrue(b.lastLineKey().startsWith("exchange.food") || b.lastLineKey().equals("banter.reply"), "B answered (" + b.lastLineKey() + ")");
		});
	}
}

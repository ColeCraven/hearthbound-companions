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
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Chests, smelting and crafting on request: only the chest the player is looking at, a furnace nearby with real fuel and
 * real furnace time, crafting from the game's recipes with what's in the pack, and an honest answer when something's
 * missing.
 */
public class ChestAndCraftGameTests {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** A player standing at x/z, looking at the middle of the block at {@code look}. */
	private static ServerPlayer player(GameTestHelper helper, double x, double z, BlockPos look) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(x, 1, z));
		Vec3 target = Vec3.atCenterOf(helper.absolutePos(look));
		double dx = target.x - pos.x;
		double dy = target.y - (pos.y + player.getEyeHeight());
		double dz = target.z - pos.z;
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		player.snapTo(pos.x, pos.y, pos.z, yaw, pitch);
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

	private static Container chest(GameTestHelper helper, BlockPos pos) {
		return (Container) helper.getBlockEntity(pos, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
	}

	private static int count(Container container, Item item) {
		int total = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (container.getItem(slot).is(item)) {
				total += container.getItem(slot).getCount();
			}
		}
		return total;
	}

	@GameTest(maxTicks = 300, padding = 40)
	public void logsGoIntoTheChestYouLookAt(GameTestHelper helper) {
		floor(helper);
		BlockPos looked = new BlockPos(6, 1, 3);
		BlockPos other = new BlockPos(6, 1, 6);
		helper.setBlock(looked, Blocks.CHEST);
		helper.setBlock(other, Blocks.CHEST);
		ServerPlayer owner = player(helper, 3.5, 3.5, looked);
		CompanionEntity tom = recruited(helper, owner, Archetype.FARMHAND, "Gttom", 2.5, 1.5);
		tom.inventory().addItem(new ItemStack(Items.OAK_LOG, 8));
		tom.inventory().addItem(new ItemStack(Items.BREAD, 3));
		helper.assertTrue(Conversations.onChat(owner, "@Gttom put the logs in that chest"), "heard");
		helper.succeedWhen(() -> {
			helper.assertValueEqual(count(chest(helper, looked), Items.OAK_LOG), 8, "logs in the chest being looked at");
			helper.assertValueEqual(count(chest(helper, other), Items.OAK_LOG), 0, "not the other one");
			helper.assertValueEqual(tom.countCarried(s -> s.is(Items.BREAD)), 3, "the bread stays in the pack");
			helper.assertValueEqual(tom.lastLineKey(), "chest.stored", "and said so");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 300, padding = 40)
	public void breadComesOutOfTheChestAndNothingElse(GameTestHelper helper) {
		floor(helper);
		BlockPos at = new BlockPos(6, 1, 3);
		helper.setBlock(at, Blocks.CHEST);
		chest(helper, at).setItem(0, new ItemStack(Items.BREAD, 6));
		chest(helper, at).setItem(1, new ItemStack(Items.COBBLESTONE, 4));
		ServerPlayer owner = player(helper, 3.5, 3.5, at);
		CompanionEntity ivy = recruited(helper, owner, Archetype.NATURALIST, "Gtivy", 2.5, 1.5);
		helper.assertTrue(Conversations.apply(owner, ivy, Intent.take("bread", null)).accepted(), "on it");
		helper.succeedWhen(() -> {
			helper.assertValueEqual(ivy.countCarried(s -> s.is(Items.BREAD)), 6, "took the bread");
			helper.assertValueEqual(count(chest(helper, at), Items.COBBLESTONE), 4, "left the cobblestone");
			helper.assertValueEqual(ivy.lastLineKey(), "chest.taken", "and said so");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 100, padding = 40)
	public void noChestInSightIsSaidAndNothingMoves(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(6, 1, 6, Blocks.CHEST);
		ServerPlayer owner = player(helper, 3.5, 3.5, new BlockPos(3, 0, 0)); // looking at the floor
		CompanionEntity ned = recruited(helper, owner, Archetype.SELLSWORD, "Gtned", 2.5, 1.5);
		ned.inventory().addItem(new ItemStack(Items.OAK_LOG, 8));
		Conversations.Outcome outcome = Conversations.apply(owner, ned, Intent.store(null, null));
		helper.assertFalse(outcome.accepted(), "nothing to put it in");
		helper.assertValueEqual(outcome.lineKey(), "chest.none", "asked which chest");
		helper.runAfterDelay(60, () -> {
			helper.assertValueEqual(ned.countCarried(s -> s.is(Items.OAK_LOG)), 8, "kept the logs");
			helper.assertValueEqual(count(chest(helper, new BlockPos(6, 1, 6)), Items.OAK_LOG), 0, "and the chest over there is untouched");
			remove(helper, owner);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200, padding = 40)
	public void walkingOffStopsTheChestJob(GameTestHelper helper) {
		floor(helper);
		BlockPos at = new BlockPos(6, 1, 3);
		helper.setBlock(at, Blocks.CHEST);
		chest(helper, at).setItem(0, new ItemStack(Items.BREAD, 6));
		ServerPlayer owner = player(helper, 3.5, 3.5, at);
		CompanionEntity kit = recruited(helper, owner, Archetype.ROGUE, "Gtkit", 0.5, 0.5);
		helper.assertTrue(Conversations.apply(owner, kit, Intent.take("bread", null)).accepted(), "on it");
		Vec3 away = helper.absoluteVec(new Vec3(3.5, 1, 30.5));
		owner.snapTo(away.x, away.y, away.z, 0, 0);
		helper.succeedWhen(() -> {
			helper.assertValueEqual(kit.lastLineKey(), "chest.left", "stopped when the player left");
			helper.assertValueEqual(count(chest(helper, at), Items.BREAD), 6, "and took nothing");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 1000, padding = 40)
	public void ironIsSmeltedInTheFurnaceNearby(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(6, 1, 6, Blocks.FURNACE);
		ServerPlayer owner = player(helper, 3.5, 2.5, new BlockPos(6, 1, 6));
		CompanionEntity olga = recruited(helper, owner, Archetype.GUARDIAN, "Gtolg", 3.5, 4.5);
		olga.inventory().addItem(new ItemStack(Items.RAW_IRON, 3));
		olga.inventory().addItem(new ItemStack(Items.COAL, 2));
		helper.assertTrue(Conversations.onChat(owner, "@Gtolg smelt this iron"), "heard");
		helper.assertValueEqual(olga.lastLineKey(), "smelt.ack", "said yes");
		helper.succeedWhen(() -> {
			helper.assertValueEqual(olga.countCarried(s -> s.is(Items.IRON_INGOT)), 3, "three ingots");
			helper.assertValueEqual(olga.countCarried(s -> s.is(Items.RAW_IRON)), 0, "from the raw iron");
			helper.assertValueEqual(olga.countCarried(s -> s.is(Items.COAL)), 1, "one coal burnt");
			helper.assertValueEqual(olga.lastLineKey(), "smelt.done", "and said so");
			helper.assertBlockPresent(Blocks.FURNACE, new BlockPos(6, 1, 6));
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 100, padding = 40)
	public void noFuelNoSmelting(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(6, 1, 6, Blocks.FURNACE);
		ServerPlayer owner = player(helper, 3.5, 2.5, new BlockPos(6, 1, 6));
		CompanionEntity pip = recruited(helper, owner, Archetype.FARMHAND, "Gtpip", 3.5, 4.5);
		pip.inventory().addItem(new ItemStack(Items.RAW_IRON, 3));
		Conversations.Outcome outcome = Conversations.apply(owner, pip, Intent.smelt("iron", null));
		helper.assertFalse(outcome.accepted(), "can't");
		helper.assertValueEqual(outcome.lineKey(), "smelt.no_fuel", "and said why");
		helper.assertValueEqual(pip.countCarried(s -> s.is(Items.RAW_IRON)), 3, "kept the iron");
		remove(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 600, padding = 40)
	public void aStonePickaxeIsCraftedAndHandedOver(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 2.5, new BlockPos(3, 0, 6));
		CompanionEntity ada = recruited(helper, owner, Archetype.NATURALIST, "Gtada", 3.5, 4.5);
		ada.inventory().addItem(new ItemStack(Items.COBBLESTONE, 3));
		ada.inventory().addItem(new ItemStack(Items.STICK, 2));
		ada.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 4));
		helper.assertTrue(Conversations.onChat(owner, "@Gtada craft me a stone pickaxe"), "heard");
		helper.assertValueEqual(ada.lastLineKey(), "craft.ack", "said yes");
		helper.succeedWhen(() -> {
			helper.assertValueEqual(owner.getInventory().countItem(Items.STONE_PICKAXE), 1, "a pickaxe for the player");
			helper.assertValueEqual(ada.countCarried(s -> s.is(Items.COBBLESTONE)), 0, "made from the cobblestone");
			helper.assertValueEqual(ada.countCarried(s -> s.is(Items.CRAFTING_TABLE)), 1, "at a table made and picked back up");
			helper.assertValueEqual(ada.lastLineKey(), "craft.done", "and said so");
			for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(7, 2, 7))) {
				helper.assertBlockNotPresent(Blocks.CRAFTING_TABLE, pos);
			}
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 400, padding = 40)
	public void aPickaxeIsTheBestThePackCanMake(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(6, 1, 6, Blocks.CRAFTING_TABLE);
		ServerPlayer owner = player(helper, 3.5, 2.5, new BlockPos(6, 1, 6));
		CompanionEntity ben = recruited(helper, owner, Archetype.SELLSWORD, "Gtben", 3.5, 4.5);
		ben.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 3));
		ben.inventory().addItem(new ItemStack(Items.STICK, 2));
		helper.assertTrue(Conversations.apply(owner, ben, Intent.craft("pickaxe", 1)).accepted(), "can do");
		helper.succeedWhen(() -> {
			helper.assertValueEqual(owner.getInventory().countItem(Items.WOODEN_PICKAXE), 1, "a wooden one, from the planks");
			helper.assertBlockPresent(Blocks.CRAFTING_TABLE, new BlockPos(6, 1, 6));
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 100, padding = 40)
	public void whatsMissingForACraftIsSaid(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 2.5, new BlockPos(3, 0, 6));
		CompanionEntity rue = recruited(helper, owner, Archetype.MYSTIC, "Gtrue", 3.5, 4.5);
		rue.inventory().addItem(new ItemStack(Items.STICK, 1));
		Conversations.Outcome outcome = Conversations.apply(owner, rue, Intent.craft("iron sword", 1));
		helper.assertFalse(outcome.accepted(), "can't");
		helper.assertValueEqual(outcome.lineKey(), "craft.missing", "said what's missing");
		helper.assertTrue(outcome.vars().getOrDefault("missing", "").contains("iron"), "the iron (" + outcome.vars() + ")");
		helper.assertValueEqual(rue.countCarried(s -> s.is(Items.STICK)), 1, "and used nothing");
		Conversations.Outcome unknown = Conversations.apply(owner, rue, Intent.craft("flux capacitor", 1));
		helper.assertValueEqual(unknown.lineKey(), "craft.unknown", "no recipe for nonsense");
		remove(helper, owner);
		helper.succeed();
	}
}

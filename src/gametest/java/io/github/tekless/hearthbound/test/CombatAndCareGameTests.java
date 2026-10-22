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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Fighting and looking after themselves: guardians shoot from range with arrows from the pack and draw the sword up
 * close, shields really block, a hurt companion eats, a hurt player is handed food, and worn gear is swapped for a
 * spare, put away before it breaks, or mended at an anvil with real materials.
 */
public class CombatAndCareGameTests {
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

	private static CompanionEntity recruited(GameTestHelper helper, ServerPlayer owner, Archetype archetype, String name, double x, double z) {
		CompanionEntity companion = helper.spawn(ModEntities.COMPANION, new Vec3(x, 1, z));
		companion.initIdentity(archetype, name, CompanionSkin.STEVE, false);
		Conversations.apply(owner, companion, Intent.of(IntentType.RECRUIT));
		companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		return companion;
	}

	private static void remove(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static <T extends Monster> T still(GameTestHelper helper, EntityType<T> type, double x, double z) {
		return helper.spawnWithNoFreeWill(type, new Vec3(x, 1, z));
	}

	@GameTest(maxTicks = 300, padding = 40)
	public void aGuardianShootsFromRange(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 0.5);
		CompanionEntity dasha = recruited(helper, owner, Archetype.GUARDIAN, "Gtdas", 0.5, 3.5);
		dasha.inventory().addItem(new ItemStack(Items.BOW));
		dasha.inventory().addItem(new ItemStack(Items.ARROW, 16));
		var husk = still(helper, net.minecraft.world.entity.EntityTypes.HUSK, 7.5, 3.5);
		helper.assertTrue(Conversations.apply(owner, dasha, Intent.attack("husk")).accepted(), "told to fight it");
		helper.succeedWhen(() -> {
			helper.assertTrue(dasha.countCarried(s -> s.is(Items.ARROW)) < 16, "arrows from the pack");
			helper.assertTrue(husk.getHealth() < husk.getMaxHealth() || !husk.isAlive(), "and they hit");
			helper.assertTrue(CompanionEntity.isRanged(dasha.getMainHandItem()), "with the bow in hand");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 200, padding = 40)
	public void upCloseTheSwordComesOut(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 0.5);
		CompanionEntity charlie = recruited(helper, owner, Archetype.SELLSWORD, "Gtcha", 3.5, 3.5);
		charlie.inventory().addItem(new ItemStack(Items.BOW));
		charlie.inventory().addItem(new ItemStack(Items.ARROW, 16));
		var husk = still(helper, net.minecraft.world.entity.EntityTypes.HUSK, 5.5, 3.5);
		helper.assertTrue(Conversations.apply(owner, charlie, Intent.attack("husk")).accepted(), "told to fight it");
		helper.succeedWhen(() -> {
			helper.assertTrue(husk.getHealth() < husk.getMaxHealth() || !husk.isAlive(), "hit it");
			helper.assertTrue(charlie.getMainHandItem().is(Items.IRON_SWORD), "with the sword");
			helper.assertValueEqual(charlie.countCarried(s -> s.is(Items.ARROW)), 16, "no arrows wasted at close range");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 200, padding = 40)
	public void aRaisedShieldTakesTheHit(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 0.5);
		CompanionEntity sigrun = recruited(helper, owner, Archetype.GUARDIAN, "Gtsig", 0.5, 3.5);
		sigrun.inventory().addItem(new ItemStack(Items.SHIELD));
		var skeleton = still(helper, net.minecraft.world.entity.EntityTypes.SKELETON, 7.5, 3.5);
		helper.assertTrue(Conversations.apply(owner, sigrun, Intent.attack("skeleton")).accepted(), "told to fight it");
		boolean[] state = {false, false};
		helper.onEachTick(() -> {
			if (!state[0] && sigrun.isBlocking()) {
				state[0] = true;
				float before = sigrun.getHealth();
				sigrun.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(skeleton), 5.0F);
				state[1] = sigrun.getHealth() >= before;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(sigrun.getOffhandItem().is(Items.SHIELD), "the shield went in the off hand");
			helper.assertTrue(state[0], "and up, with a skeleton taking aim");
			helper.assertTrue(state[1], "and it stopped a hit from the front");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 200, padding = 40)
	public void aHurtCompanionEatsFromTheirPack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 0.5);
		CompanionEntity honey = recruited(helper, owner, Archetype.FARMHAND, "Gthon", 3.5, 3.5);
		honey.inventory().addItem(new ItemStack(Items.BREAD, 2));
		honey.inventory().addItem(new ItemStack(Items.ROTTEN_FLESH, 4));
		honey.setHealth(8.0F);
		helper.succeedWhen(() -> {
			helper.assertValueEqual(honey.countCarried(s -> s.is(Items.BREAD)), 1, "ate a piece of bread");
			helper.assertValueEqual(honey.countCarried(s -> s.is(Items.ROTTEN_FLESH)), 4, "and not the rotten flesh");
			helper.assertTrue(honey.getHealth() >= 13.0F, "which healed her (" + honey.getHealth() + ")");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 120, padding = 40)
	public void aBadlyHurtPlayerIsHandedFood(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 2.5);
		owner.setHealth(4.0F);
		owner.getFoodData().setFoodLevel(10);
		CompanionEntity samuel = recruited(helper, owner, Archetype.FARMHAND, "Gtsml", 3.5, 4.5);
		samuel.inventory().addItem(new ItemStack(Items.COOKED_BEEF, 3));
		helper.succeedWhen(() -> {
			helper.assertValueEqual(owner.getInventory().countItem(Items.COOKED_BEEF), 2, "two pieces handed over");
			helper.assertValueEqual(samuel.lastLineKey(), "gift.food", "and said so");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 120, padding = 40)
	public void wornGearIsSwappedForASpare(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 0.5);
		CompanionEntity daisy = recruited(helper, owner, Archetype.GUARDIAN, "Gtdsy", 3.5, 3.5);
		ItemStack worn = new ItemStack(Items.IRON_HELMET);
		worn.setDamageValue(worn.getMaxDamage() - 1);
		daisy.setItemSlot(EquipmentSlot.HEAD, worn);
		daisy.inventory().addItem(new ItemStack(Items.IRON_HELMET));
		helper.succeedWhen(() -> {
			helper.assertValueEqual(daisy.getItemBySlot(EquipmentSlot.HEAD).getDamageValue(), 0, "wearing the spare");
			helper.assertTrue(daisy.inventory().getItems().stream().anyMatch(s -> s.is(Items.IRON_HELMET) && s.getDamageValue() > 0),
					"the worn one is in the pack, still in one piece");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 120, padding = 40)
	public void wornGearWithNoSpareIsPutAwayBeforeItBreaks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 3.5, 0.5);
		CompanionEntity hazel = recruited(helper, owner, Archetype.NATURALIST, "Gthaz", 3.5, 3.5);
		ItemStack worn = new ItemStack(Items.IRON_SWORD);
		worn.setDamageValue(worn.getMaxDamage() - 1);
		hazel.setItemSlot(EquipmentSlot.MAINHAND, worn);
		helper.succeedWhen(() -> {
			helper.assertFalse(hazel.getMainHandItem().is(Items.IRON_SWORD), "not using it any more");
			helper.assertValueEqual(hazel.countCarried(s -> s.is(Items.IRON_SWORD)), 1, "kept in the pack, unbroken");
			helper.assertValueEqual(hazel.lastLineKey(), "gear.worn", "and said so");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 900, padding = 40)
	public void wornGearIsMendedAtAnAnvilWithRealMaterials(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(6, 1, 6, Blocks.ANVIL);
		ServerPlayer owner = player(helper, 3.5, 2.5);
		CompanionEntity birintan = recruited(helper, owner, Archetype.NATURALIST, "Gtbrn", 3.5, 4.5);
		ItemStack sword = birintan.getMainHandItem();
		sword.setDamageValue(sword.getMaxDamage() / 2);
		CompanionEntity without = recruited(helper, owner, Archetype.ROGUE, "Gtwth", 1.5, 4.5);
		without.getMainHandItem().setDamageValue(without.getMainHandItem().getMaxDamage() / 2);
		helper.assertTrue(without.mendable() == null, "no iron, nothing to mend with");
		birintan.inventory().addItem(new ItemStack(Items.IRON_INGOT, 2));
		helper.succeedWhen(() -> {
			helper.assertTrue(birintan.getMainHandItem().getDamageValue() < birintan.getMainHandItem().getMaxDamage() / 2, "the sword was mended");
			helper.assertValueEqual(birintan.countCarried(s -> s.is(Items.IRON_INGOT)), 0, "with the iron from the pack");
			helper.assertTrue(without.getMainHandItem().getDamageValue() == without.getMainHandItem().getMaxDamage() / 2, "the other one's wasn't");
			remove(helper, owner);
		});
	}
}

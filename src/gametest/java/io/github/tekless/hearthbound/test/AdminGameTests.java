package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.config.HearthboundConfig;
import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import io.github.tekless.hearthbound.life.HomeAreas;
import io.github.tekless.hearthbound.life.Places;
import io.github.tekless.hearthbound.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Host settings: who can hurt companions and god mode (each checked and put back within one tick, so the tests
 * running alongside never see them), and marking places from the console or a command block.
 */
public class AdminGameTests {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static int run(GameTestHelper helper, CommandSourceStack source, String command) {
		try {
			return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new AssertionError(command + ": " + e.getMessage(), e);
		}
	}

	private static boolean hurts(GameTestHelper helper, CompanionEntity companion, net.minecraft.world.damagesource.DamageSource source) {
		companion.invulnerableTime = 0;
		companion.setHealth(companion.getMaxHealth());
		companion.hurtServer(helper.getLevel(), source, 3.0F);
		return companion.getHealth() < companion.getMaxHealth();
	}

	@GameTest(maxTicks = 40, padding = 40)
	public void godModeAndPlayerDamageAreTheHostsCall(GameTestHelper helper) {
		floor(helper);
		MinecraftServer server = helper.getLevel().getServer();
		CommandSourceStack console = server.createCommandSourceStack().withSuppressedOutput();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
		owner.snapTo(pos.x, pos.y, pos.z, 0, 0);
		CompanionEntity tess = helper.spawn(ModEntities.COMPANION, new Vec3(4.5, 1, 4.5));
		tess.initIdentity(Archetype.GUARDIAN, "Gttes", CompanionSkin.STEVE, false);
		Conversations.apply(owner, tess, Intent.of(IntentType.RECRUIT));
		HearthboundConfig.Server rules = Hearthbound.config().server;
		boolean godmode = rules.godmode;
		boolean players = rules.playersCanHurt;
		try {
			var generic = helper.getLevel().damageSources().generic();
			var hit = helper.getLevel().damageSources().playerAttack(owner);
			helper.assertTrue(hurts(helper, tess, generic), "hurt as usual");
			helper.assertValueEqual(run(helper, console, "companion godmode on"), 1, "god mode on");
			helper.assertTrue(rules.godmode, "saved");
			helper.assertFalse(hurts(helper, tess, generic), "nothing hurts in god mode");
			helper.assertFalse(hurts(helper, tess, hit), "not even you");
			run(helper, console, "companion godmode off");
			helper.assertTrue(hurts(helper, tess, generic), "and back to normal");
			run(helper, console, "companion damage players off");
			helper.assertFalse(rules.playersCanHurt, "saved");
			helper.assertFalse(hurts(helper, tess, hit), "players can't hurt them now");
			helper.assertTrue(hurts(helper, tess, generic), "the world still can");
			run(helper, console, "companion damage players on");
			helper.assertTrue(hurts(helper, tess, hit), "players can again");
		} finally {
			rules.godmode = godmode;
			rules.playersCanHurt = players;
			Hearthbound.config().save();
			helper.getLevel().getServer().getPlayerList().remove(owner);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 60, padding = 40)
	public void placesCanBeMarkedFromTheConsole(GameTestHelper helper) {
		floor(helper);
		MinecraftServer server = helper.getLevel().getServer();
		CompanionEntity rowan = helper.spawn(ModEntities.COMPANION, new Vec3(2.5, 1, 2.5));
		rowan.initIdentity(Archetype.FARMHAND, "Gtrwn", CompanionSkin.STEVE, false);
		rowan.setHomePos(helper.absolutePos(new BlockPos(2, 1, 2)));
		rowan.setDailyLife(false);
		helper.setBlock(6, 1, 6, Blocks.CHEST);
		CommandSourceStack console = server.createCommandSourceStack().withSuppressedOutput();
		BlockPos byChest = helper.absolutePos(new BlockPos(6, 1, 5));
		BlockPos commons = helper.absolutePos(new BlockPos(4, 1, 4));
		BlockPos patrol = helper.absolutePos(new BlockPos(1, 1, 6));
		// With coordinates, and with a name.
		helper.assertValueEqual(run(helper, console, "companion setplace storage Gtrwn " + byChest.getX() + " " + byChest.getY() + " " + byChest.getZ()), 1, "storage");
		// Without coordinates: where the command runs (a command block, or execute positioned).
		run(helper, console, "execute positioned " + commons.getX() + " " + commons.getY() + " " + commons.getZ() + " run companion setplace commons");
		// Coordinates and no name: the home nearest them.
		run(helper, console, "companion setplace patrol " + patrol.getX() + " " + patrol.getY() + " " + patrol.getZ());
		HomeAreas areas = HomeAreas.get(server);
		HomeAreas.Area area = areas.areaOf(rowan);
		Places places = areas.places(helper.getLevel(), area);
		helper.assertTrue(places.storage().contains(helper.absolutePos(new BlockPos(6, 1, 6))), "the chest is storage: " + places.storage());
		helper.assertValueEqual(places.commons(), commons, "the meeting spot is where the command ran");
		helper.assertTrue(places.patrol().contains(patrol), "and a patrol point: " + places.patrol());
		helper.assertTrue(run(helper, console, "companion places") >= 1, "the console lists every home");
		helper.succeed();
	}
}

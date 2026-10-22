package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.blueprint.Blueprints;
import io.github.tekless.hearthbound.blueprint.SavedBuild;
import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import io.github.tekless.hearthbound.entity.order.CompanionOrder;
import io.github.tekless.hearthbound.registry.ModEntities;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * Copying a build: saving it between two corners, putting it up again in front of the player and turned their way,
 * getting what's short by real crafting (at a table they put down and pick back up), and saying what can't be had.
 */
public class BlueprintGameTests {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** A player at (x, 1, z) facing the given way. */
	private static ServerPlayer player(GameTestHelper helper, double x, double z, Direction facing) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(x, 1, z));
		float yaw = facing.toYRot();
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

	private static BlockState at(GameTestHelper helper, BlockPos absolute) {
		return helper.getLevel().getBlockState(absolute);
	}

	/** Saves the blocks between two corners (relative to the test) under a name, as the save command would. */
	private static void save(GameTestHelper helper, ServerPlayer player, String name, BlockPos a, BlockPos b) {
		SavedBuild build = SavedBuild.capture(helper.getLevel(), helper.absolutePos(a), helper.absolutePos(b), player.getDirection(), name,
				player.getName().getString());
		helper.assertTrue(Blueprints.save(helper.getLevel().getServer(), build), "blueprint saved");
	}

	private static void clear(GameTestHelper helper, int x1, int y1, int z1, int x2, int y2, int z2) {
		for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2))) {
			helper.setBlock(pos, Blocks.AIR);
		}
	}

	@GameTest(maxTicks = 600, padding = 40)
	public void aSavedBuildGoesUpFacingThePlayer(GameTestHelper helper) {
		floor(helper);
		// A row of three planks with a cobblestone on the left-hand end (seen from the player, who faces south).
		helper.setBlock(1, 1, 5, Blocks.OAK_PLANKS);
		helper.setBlock(2, 1, 5, Blocks.OAK_PLANKS);
		helper.setBlock(3, 1, 5, Blocks.OAK_PLANKS);
		helper.setBlock(1, 2, 5, Blocks.COBBLESTONE);
		ServerPlayer owner = player(helper, 2.5, 2.5, Direction.SOUTH);
		Blueprints.mark(owner.getUUID(), owner.level().dimension().identifier().toString(), helper.absolutePos(new BlockPos(1, 1, 5)), true);
		Blueprints.mark(owner.getUUID(), owner.level().dimension().identifier().toString(), helper.absolutePos(new BlockPos(3, 2, 5)), false);
		MinecraftServer server = helper.getLevel().getServer();
		server.getCommands().performPrefixedCommand(owner.createCommandSourceStack(), "companion blueprint save gt row");
		helper.assertTrue(Blueprints.names(server).contains("gt_row"), "the save command stored it");
		clear(helper, 1, 1, 5, 3, 2, 5);
		// Built again by someone facing east: the row runs north to south, two ahead, the cobblestone on the right (south) end.
		ServerPlayer builderOwner = owner;
		builderOwner.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 3.5)).x, helper.absoluteVec(new Vec3(1.5, 1, 3.5)).y,
				helper.absoluteVec(new Vec3(1.5, 1, 3.5)).z, Direction.EAST.toYRot(), 0);
		builderOwner.setYHeadRot(Direction.EAST.toYRot());
		CompanionEntity maddie = recruited(helper, owner, Archetype.ROGUE, "Gtmad", 1.5, 1.5);
		maddie.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 3));
		maddie.inventory().addItem(new ItemStack(Items.COBBLESTONE, 1));
		helper.assertTrue(Conversations.onChat(owner, "@Gtmad build my gt row here"), "heard");
		helper.assertValueEqual(maddie.order().kind(), CompanionOrder.Kind.PROJECT, "copying it");
		BlockPos feet = owner.blockPosition();
		helper.succeedWhen(() -> {
			for (int dz = -1; dz <= 1; dz++) {
				helper.assertTrue(at(helper, feet.offset(2, 0, dz)).is(Blocks.OAK_PLANKS), "plank at " + dz);
			}
			helper.assertTrue(at(helper, feet.offset(2, 1, 1)).is(Blocks.COBBLESTONE), "the cobblestone is on the player's right");
			helper.assertValueEqual(maddie.countCarried(s -> s.is(Items.OAK_PLANKS) || s.is(Items.COBBLESTONE)), 0, "built from her pack");
			helper.assertValueEqual(maddie.lastLineKey(), "done.build", "and she says it's done only now");
			Blueprints.delete(server, "gt_row");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 600, padding = 40)
	public void aCopyShortOfPlanksMakesThemFromLogs(GameTestHelper helper) {
		floor(helper);
		for (int x = 2; x <= 5; x++) {
			helper.setBlock(x, 1, 6, Blocks.BIRCH_PLANKS);
		}
		ServerPlayer owner = player(helper, 3.5, 3.5, Direction.SOUTH);
		save(helper, owner, "gt_planks", new BlockPos(2, 1, 6), new BlockPos(5, 1, 6));
		clear(helper, 2, 1, 6, 5, 1, 6);
		CompanionEntity luke = recruited(helper, owner, Archetype.MYSTIC, "Gtluke", 2.5, 2.5);
		luke.inventory().addItem(new ItemStack(Items.BIRCH_LOG, 1));
		Conversations.Outcome outcome = Conversations.apply(owner, luke, Intent.build("my gt planks"));
		helper.assertTrue(outcome.accepted(), "accepted (" + outcome.lineKey() + ")");
		helper.succeedWhen(() -> {
			int planks = 0;
			for (int dx = -2; dx <= 2; dx++) {
				if (at(helper, owner.blockPosition().offset(dx, 0, 2)).is(Blocks.BIRCH_PLANKS)) {
					planks++;
				}
			}
			helper.assertValueEqual(planks, 4, "four planks, made from the one log");
			helper.assertValueEqual(luke.countCarried(s -> s.is(Items.BIRCH_LOG)), 0, "the log was used");
			Blueprints.delete(helper.getLevel().getServer(), "gt_planks");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 900, padding = 40)
	public void stairsAreCraftedAtATableTheyPutDownAndPickUp(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(3, 1, 6), Blocks.OAK_STAIRS.defaultBlockState());
		ServerPlayer owner = player(helper, 3.5, 3.5, Direction.SOUTH);
		save(helper, owner, "gt_step", new BlockPos(3, 1, 6), new BlockPos(3, 1, 6));
		clear(helper, 3, 1, 6, 3, 1, 6);
		CompanionEntity birintan = recruited(helper, owner, Archetype.NATURALIST, "Gtbir", 1.5, 2.5);
		birintan.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 10)); // six for the stairs, four for a table
		helper.assertTrue(Conversations.apply(owner, birintan, Intent.build("gt step")).accepted(), "accepted");
		BlockPos spot = owner.blockPosition().offset(0, 0, 2);
		helper.succeedWhen(() -> {
			helper.assertTrue(at(helper, spot).is(Blocks.OAK_STAIRS), "the stair is in");
			helper.assertValueEqual(birintan.countCarried(s -> s.is(Items.OAK_STAIRS)), 3, "a real recipe makes four, one went in");
			helper.assertValueEqual(birintan.countCarried(s -> s.is(Items.CRAFTING_TABLE)), 1, "the table they put down is back in the pack");
			for (BlockPos pos : BlockPos.betweenClosed(owner.blockPosition().offset(-6, -1, -6), owner.blockPosition().offset(6, 2, 6))) {
				helper.assertFalse(at(helper, pos).is(Blocks.CRAFTING_TABLE), "and not left lying about");
			}
			Blueprints.delete(helper.getLevel().getServer(), "gt_step");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 200, padding = 40)
	public void whatCantBeHadIsSaidAndNothingIsBuilt(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(3, 1, 6, Blocks.SPONGE);
		ServerPlayer owner = player(helper, 3.5, 3.5, Direction.SOUTH);
		save(helper, owner, "gt_sponge", new BlockPos(3, 1, 6), new BlockPos(3, 1, 6));
		clear(helper, 3, 1, 6, 3, 1, 6);
		CompanionEntity hazel = recruited(helper, owner, Archetype.NATURALIST, "Gthaz", 2.5, 2.5);
		helper.assertTrue(Conversations.apply(owner, hazel, Intent.build("my gt sponge")).accepted(), "taken on, then the materials are worked out");
		helper.succeedWhen(() -> {
			helper.assertValueEqual(hazel.lastLineKey(), "supply.blocked", "says what's missing");
			helper.assertTrue(hazel.order().kind() != CompanionOrder.Kind.PROJECT, "and stops");
			helper.assertFalse(at(helper, owner.blockPosition().offset(0, 0, 2)).is(Blocks.SPONGE), "nothing made up");
			Blueprints.delete(helper.getLevel().getServer(), "gt_sponge");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 600, padding = 40)
	public void aStructureBlockFileCanBeBuiltByName(GameTestHelper helper) throws Exception {
		floor(helper);
		helper.setBlock(5, 1, 5, Blocks.COBBLESTONE);
		helper.setBlock(5, 2, 5, Blocks.COBBLESTONE);
		ServerLevel level = helper.getLevel();
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(new BlockPos(5, 1, 5)), new net.minecraft.core.Vec3i(1, 2, 1), false, java.util.List.of(Blocks.STRUCTURE_VOID));
		Path file = level.getServer().getWorldPath(LevelResource.GENERATED_DIR).resolve("minecraft").resolve("structures").resolve("gt_post.nbt");
		Files.createDirectories(file.getParent());
		NbtIo.writeCompressed(template.save(new net.minecraft.nbt.CompoundTag()), file);
		clear(helper, 5, 1, 5, 5, 2, 5);
		helper.assertTrue(Blueprints.names(level.getServer()).contains("gt_post"), "a structure block's file is found by name");
		ServerPlayer owner = player(helper, 3.5, 3.5, Direction.SOUTH);
		CompanionEntity cole = recruited(helper, owner, Archetype.ROGUE, "Gtcole", 2.5, 2.5);
		cole.inventory().addItem(new ItemStack(Items.COBBLESTONE, 2));
		helper.assertTrue(Conversations.apply(owner, cole, Intent.build("gt post")).accepted(), "accepted");
		BlockPos spot = owner.blockPosition().offset(0, 0, 2);
		helper.succeedWhen(() -> {
			helper.assertTrue(at(helper, spot).is(Blocks.COBBLESTONE) && at(helper, spot.above()).is(Blocks.COBBLESTONE), "the post stands");
			try {
				Files.deleteIfExists(file);
			} catch (java.io.IOException e) {
				throw new IllegalStateException(e);
			}
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 900, padding = 40)
	public void buildersShareASavedBuild(GameTestHelper helper) {
		floor(helper);
		for (int x = 1; x <= 6; x++) {
			helper.setBlock(x, 1, 7, Blocks.STONE_BRICKS);
		}
		ServerPlayer owner = player(helper, 3.5, 4.5, Direction.SOUTH);
		save(helper, owner, "gt_wall", new BlockPos(1, 1, 7), new BlockPos(6, 1, 7));
		clear(helper, 1, 1, 7, 6, 1, 7);
		CompanionEntity one = recruited(helper, owner, Archetype.ROGUE, "Gtone", 1.5, 3.5);
		CompanionEntity two = recruited(helper, owner, Archetype.MYSTIC, "Gttwo", 5.5, 3.5);
		one.inventory().addItem(new ItemStack(Items.STONE_BRICKS, 3));
		two.inventory().addItem(new ItemStack(Items.STONE_BRICKS, 3));
		Block bricks = Blocks.STONE_BRICKS;
		helper.assertTrue(Conversations.onChat(owner, "@party build my gt wall"), "heard");
		helper.assertValueEqual(one.order().kind(), CompanionOrder.Kind.PROJECT, "one is on it");
		helper.assertValueEqual(two.order().kind(), CompanionOrder.Kind.PROJECT, "so is the other");
		helper.succeedWhen(() -> {
			int placed = 0;
			for (int dx = -3; dx <= 3; dx++) {
				if (at(helper, owner.blockPosition().offset(dx, 0, 2)).is(bricks)) {
					placed++;
				}
			}
			helper.assertValueEqual(placed, 6, "the whole wall");
			helper.assertValueEqual(one.countCarried(s -> s.is(Items.STONE_BRICKS)) + two.countCarried(s -> s.is(Items.STONE_BRICKS)), 0, "every brick from their packs");
			Blueprints.delete(helper.getLevel().getServer(), "gt_wall");
			remove(helper, owner);
		});
	}
}

package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.core.persona.CastMember;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import io.github.tekless.hearthbound.entity.order.CompanionOrder;
import io.github.tekless.hearthbound.entity.order.Crafting;
import io.github.tekless.hearthbound.entity.order.FarmOrder;
import io.github.tekless.hearthbound.entity.order.ProjectOrder;
import io.github.tekless.hearthbound.entity.order.Projects;
import io.github.tekless.hearthbound.registry.ModEntities;
import io.github.tekless.hearthbound.world.CompanionRoster;
import io.github.tekless.hearthbound.world.CompanionTracker;
import io.github.tekless.hearthbound.world.Exchanges;
import io.github.tekless.hearthbound.world.Obey;
import io.github.tekless.hearthbound.world.Storehouses;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Talking to groups without an @name, obey mode, homes, directed fights, and the big projects: houses,
 * a farm, a storehouse, crafting from the pack, and honest blockers.
 */
public class GroupAndProjectGameTests {
	private static final String BUILDERS_MESSAGE = "All my builders, please help expand this village. Everyone should have their own house, unless you all "
			+ "want to share, or just some of you do. Work with the collectors, gather materials and start building.";
	private static final String FARMERS_MESSAGE = "All my farmers, build a farm and start harvesting food for us to eat. Connect with the builders and "
			+ "maybe build a building for us to store and cook food.";

	private static void floor(GameTestHelper helper, Block block, int from, int to) {
		for (int x = from; x <= to; x++) {
			for (int z = from; z <= to; z++) {
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

	private static CompanionEntity companion(GameTestHelper helper, Archetype archetype, String name, double x, double z) {
		CompanionEntity companion = helper.spawn(ModEntities.COMPANION, new Vec3(x, 1, z));
		companion.initIdentity(archetype, name, CompanionSkin.STEVE, false);
		return companion;
	}

	private static CompanionEntity recruited(GameTestHelper helper, ServerPlayer owner, Archetype archetype, String name, double x, double z) {
		CompanionEntity companion = companion(helper, archetype, name, x, z);
		Conversations.apply(owner, companion, Intent.of(IntentType.RECRUIT));
		return companion;
	}

	private static void finish(GameTestHelper helper, ServerPlayer player) {
		Obey.set(player, false, false);
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static BlockState at(GameTestHelper helper, int x, int y, int z) {
		return helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z)));
	}

	/** Counts blocks matching a test in a box around the arena. */
	private static int count(GameTestHelper helper, int from, int to, int yFrom, int yTo, java.util.function.Predicate<BlockState> test) {
		int found = 0;
		for (int x = from; x <= to; x++) {
			for (int y = yFrom; y <= yTo; y++) {
				for (int z = from; z <= to; z++) {
					if (test.test(at(helper, x, y, z))) {
						found++;
					}
				}
			}
		}
		return found;
	}

	/** The last few things a companion said, for failure messages. */
	private static String said(CompanionEntity companion) {
		var lines = companion.memory().lines();
		return lines.subList(Math.max(0, lines.size() - 4), lines.size()).stream().map(l -> l.text()).toList() + " / " + companion.order().describe() + " at " + companion.blockPosition().toShortString();
	}

	private static int spoken(CompanionEntity... companions) {
		int total = 0;
		for (CompanionEntity companion : companions) {
			if (!companion.lastLineKey().isEmpty()) {
				total++;
			}
		}
		return total;
	}

	// ------------------------------------------------------------------ groups and obey mode

	@GameTest(maxTicks = 60)
	public void everyoneObeyedWhenObeyIsOn(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		ServerPlayer owner = player(helper, 3.5, 0.5);
		Obey.set(owner, true, false);
		CompanionEntity a = companion(helper, Archetype.SELLSWORD, "Adaxx", 1.5, 5.5);
		CompanionEntity b = companion(helper, Archetype.NATURALIST, "Boxx", 3.5, 5.5);
		CompanionEntity c = companion(helper, Archetype.ROGUE, "Cyxx", 5.5, 5.5);
		helper.assertTrue(Conversations.onChat(owner, "Everyone, come here"), "a plain group address is heard");
		for (CompanionEntity each : new CompanionEntity[] {a, b, c}) {
			helper.assertFalse(each.isRecruited(), each.companionName() + " is not recruited by being asked to come");
			helper.assertValueEqual(each.order().kind(), CompanionOrder.Kind.COME, each.companionName() + " comes");
		}
		helper.assertValueEqual(spoken(a, b, c), 1, "one of them answers for the group");
		helper.assertTrue(Conversations.onChat(owner, "everyone follow me"), "heard");
		for (CompanionEntity each : new CompanionEntity[] {a, b, c}) {
			helper.assertTrue(each.isOwnedBy(owner), each.companionName() + " follows because they were asked to");
			helper.assertValueEqual(each.order().kind(), CompanionOrder.Kind.FOLLOW, each.companionName() + " follows");
		}
		finish(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void strangersIgnoreGroupOrdersWithoutObey(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		ServerPlayer owner = player(helper, 3.5, 0.5);
		Obey.set(owner, false, false);
		CompanionEntity a = companion(helper, Archetype.SELLSWORD, "Adaxx", 1.5, 3.5);
		CompanionEntity b = companion(helper, Archetype.NATURALIST, "Boxx", 4.5, 3.5);
		helper.assertTrue(Conversations.onChat(owner, "guys, wait here"), "the player is told nobody is listening");
		helper.assertValueEqual(a.order().kind(), CompanionOrder.Kind.HOME, "Ada carries on with her day");
		helper.assertValueEqual(b.order().kind(), CompanionOrder.Kind.HOME, "Bo carries on with his day");
		finish(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void roleGroupsReachOnlyThatRole(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		ServerPlayer owner = player(helper, 3.5, 0.5);
		CompanionEntity builder1 = recruited(helper, owner, Archetype.MYSTIC, "Mistxx", 1.5, 3.5);
		CompanionEntity builder2 = recruited(helper, owner, Archetype.MYSTIC, "Moonxx", 5.5, 3.5);
		CompanionEntity farmer = recruited(helper, owner, Archetype.FARMHAND, "Farmxx", 3.5, 3.5);
		helper.assertTrue(Conversations.onChat(owner, "Builders, wait here"), "builders hear it");
		helper.assertValueEqual(builder1.order().kind(), CompanionOrder.Kind.WAIT, "first builder waits");
		helper.assertValueEqual(builder2.order().kind(), CompanionOrder.Kind.WAIT, "second builder waits");
		helper.assertValueEqual(farmer.order().kind(), CompanionOrder.Kind.FOLLOW, "the farmer was not addressed");
		helper.assertTrue(Conversations.onChat(owner, "@farmers wait here"), "@farmers works too");
		helper.assertValueEqual(farmer.order().kind(), CompanionOrder.Kind.WAIT, "the farmer waits");
		helper.assertTrue(Conversations.onChat(owner, "All my builders, follow me"), "all my builders");
		helper.assertValueEqual(builder1.order().kind(), CompanionOrder.Kind.FOLLOW, "builder follows again");
		helper.assertValueEqual(builder2.order().kind(), CompanionOrder.Kind.FOLLOW, "builder follows again");
		helper.assertValueEqual(farmer.order().kind(), CompanionOrder.Kind.WAIT, "the farmer is still waiting");
		finish(helper, owner);
		helper.succeed();
	}

	// ------------------------------------------------------------------ homes

	@GameTest(maxTicks = 80)
	public void sendCompanionsHomeAndTeleportThem(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		ServerPlayer owner = player(helper, 3.5, 0.5);
		MinecraftServer server = helper.getLevel().getServer();
		CompanionEntity ada = recruited(helper, owner, Archetype.SELLSWORD, "Adaxx", 5.5, 5.5);
		CompanionEntity bo = recruited(helper, owner, Archetype.NATURALIST, "Boxx", 6.5, 6.5);
		server.getCommands().performPrefixedCommand(owner.createCommandSourceStack(), "companion sethome party here");
		BlockPos home = owner.blockPosition();
		helper.assertValueEqual(ada.home(), home, "home is where the player stood");
		Vec3 far = helper.absoluteVec(new Vec3(7.5, 1, 7.5));
		ada.teleportTo(far.x, far.y, far.z);
		helper.assertTrue(Conversations.onChat(owner, "@adaxx go home"), "heard");
		helper.assertValueEqual(ada.order().kind(), CompanionOrder.Kind.COME, "walking home");
		helper.assertTrue(Conversations.onChat(owner, "@everyone teleport home"), "heard");
		for (CompanionEntity each : new CompanionEntity[] {ada, bo}) {
			helper.assertTrue(each.distanceToSqr(Vec3.atCenterOf(home)) < 4.0, each.companionName() + " is home straight away");
			helper.assertValueEqual(each.order().kind(), CompanionOrder.Kind.WAIT, each.companionName() + " waits at home");
			helper.assertTrue(each.isOwnedBy(owner), each.companionName() + " is still with the player");
		}
		finish(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void homeCommandsRespectObey(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		ServerPlayer owner = player(helper, 3.5, 0.5);
		MinecraftServer server = helper.getLevel().getServer();
		CompanionEntity stranger = companion(helper, Archetype.ROGUE, "Strxx", 6.5, 6.5);
		Vec3 start = stranger.position();
		Obey.set(owner, false, false);
		server.getCommands().performPrefixedCommand(owner.createCommandSourceStack(), "companion sethome strxx here");
		helper.assertTrue(stranger.home() == null || !stranger.home().equals(owner.blockPosition()), "not theirs to command without obey");
		Obey.set(owner, true, false);
		server.getCommands().performPrefixedCommand(owner.createCommandSourceStack(), "companion sethome strxx here");
		helper.assertValueEqual(stranger.home(), owner.blockPosition(), "with obey on, anyone's home can be set");
		server.getCommands().performPrefixedCommand(owner.createCommandSourceStack(), "companion home strxx teleport");
		helper.assertTrue(stranger.position().distanceToSqr(start) > 1.0, "teleported to the new home");
		helper.assertFalse(stranger.isRecruited(), "still nobody's companion");
		finish(helper, owner);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void hubHomeWorksFromTheConsoleAndRespawnsOnRequest(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		MinecraftServer server = helper.getLevel().getServer();
		var hub = Hearthbound.config().server.hub;
		boolean wasEnabled = hub.enabled;
		int hx = hub.x;
		int hy = hub.y;
		int hz = hub.z;
		String dimension = hub.dimension;
		java.util.List<String> residents = new java.util.ArrayList<>(hub.residents);
		BlockPos spot = helper.absolutePos(new BlockPos(4, 1, 4));
		int protect = hub.protectRadius;
		hub.protectRadius = 0;
		hub.enabled = true;
		hub.residents = new java.util.ArrayList<>(java.util.List.of("dasha"));
		hub.dimension = helper.getLevel().dimension().identifier().toString();
		hub.x = spot.getX();
		hub.y = spot.getY();
		hub.z = spot.getZ();
		// Dasha is on record as travelling with someone, but no body is loaded anywhere.
		CompanionEntity ghost = ModEntities.COMPANION.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
		ghost.initIdentity(CastMember.DASHA);
		CompanionRoster.get(server).noteLoaded(ghost);
		helper.assertTrue(CompanionTracker.all().stream().noneMatch(c -> c.companionName().equals("Dasha")), "no loaded Dasha");
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "companion hub list");
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "companion hub home dasha");
		helper.succeedWhen(() -> {
			helper.assertTrue(CompanionTracker.all().stream().anyMatch(c -> c.companionName().equals("Dasha") && c.isHubResident()),
					"Dasha was respawned at the hub");
			CompanionTracker.all().stream().filter(c -> c.companionName().equals("Dasha")).forEach(c -> c.discard());
			hub.enabled = wasEnabled;
			hub.protectRadius = protect;
			hub.residents = residents;
			hub.x = hx;
			hub.y = hy;
			hub.z = hz;
			hub.dimension = dimension;
		});
	}

	// ------------------------------------------------------------------ fights

	@GameTest(maxTicks = 700)
	public void directedFightEndsWhenOneIsKnockedDown(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		ServerPlayer owner = player(helper, 3.5, 0.5);
		CompanionEntity ada = recruited(helper, owner, Archetype.SELLSWORD, "Adaxx", 2.5, 4.5);
		CompanionEntity bo = recruited(helper, owner, Archetype.NATURALIST, "Boxx", 4.5, 4.5);
		ada.giveStarterGear();
		helper.assertFalse(ada.canAttack(bo), "no fighting unless told to");
		helper.assertTrue(Conversations.onChat(owner, "@adaxx attack boxx"), "heard");
		helper.assertValueEqual(ada.order().kind(), CompanionOrder.Kind.ATTACK, "Ada is fighting Bo");
		helper.assertTrue(ada.canAttack(bo), "directed at exactly that one");
		helper.assertFalse(bo.canAttack(ada), "Bo was not told to fight back");
		helper.succeedWhen(() -> {
			helper.assertTrue(bo.isDowned(), "Bo is knocked down");
			helper.assertTrue(bo.isAlive(), "and not killed");
			helper.assertTrue(ada.order().kind() != CompanionOrder.Kind.ATTACK, "the fight is over");
			finish(helper, owner);
		});
	}

	@GameTest(maxTicks = 100)
	public void companionsNeverStartFightsOrAttackTheirOwner(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		ServerPlayer owner = player(helper, 3.5, 0.5);
		CompanionEntity ada = recruited(helper, owner, Archetype.SELLSWORD, "Adaxx", 2.5, 4.5);
		CompanionEntity bo = recruited(helper, owner, Archetype.NATURALIST, "Boxx", 3.5, 4.5);
		helper.assertFalse(ada.canAttack(bo), "not a companion");
		helper.assertFalse(ada.canAttack(owner), "not their owner");
		Conversations.Outcome self = Conversations.apply(owner, ada, Intent.attack(owner.getName().getString().toLowerCase()));
		helper.assertFalse(self.accepted(), "never the one giving the order");
		ServerPlayer other = player(helper, 6.5, 6.5);
		helper.assertFalse(ada.canAttack(other), "not a player, unless told to");
		helper.runAfterDelay(60, () -> {
			helper.assertValueEqual(bo.getHealth(), bo.getMaxHealth(), "left alone");
			helper.assertTrue(ada.getTarget() == null, "nothing picked on its own");
			finish(helper, owner);
			finish(helper, other);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void fightingAPlayerIsRefusedWhenPvpIsOff(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		ServerPlayer owner = player(helper, 3.5, 0.5);
		ServerPlayer other = player(helper, 3.5, 6.5); // straight ahead of the owner, who faces south
		MinecraftServer server = helper.getLevel().getServer();
		CompanionEntity ada = recruited(helper, owner, Archetype.SELLSWORD, "Adaxx", 1.5, 4.5);
		String name = "that player";
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule pvp false");
		Conversations.Outcome refused = Conversations.apply(owner, ada, Intent.attack(name));
		helper.assertFalse(refused.accepted(), "refused");
		helper.assertValueEqual(refused.lineKey(), "fail.pvp_off", "and says why");
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule pvp true");
		Conversations.Outcome allowed = Conversations.apply(owner, ada, Intent.attack(name));
		helper.assertTrue(allowed.accepted(), "with PvP on, on the commander's say-so");
		helper.assertTrue(ada.canAttack(other), "directed at that player " + owner.getUUID() + " " + other.getUUID() + " " + other.isSpectator() + " " + ada.isOwnedBy(other) + " " + helper.getLevel().isPvpAllowed() + " " + ada.isDowned() + " " + ada.order().describe());
		Conversations.execute(owner, ada, Intent.of(IntentType.CANCEL));
		helper.assertFalse(ada.canAttack(other), "stopping ends it");
		finish(helper, owner);
		finish(helper, other);
		helper.succeed();
	}

	// ------------------------------------------------------------------ crafting

	@GameTest(maxTicks = 40)
	public void craftingOnlyUsesWhatIsCarried(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		CompanionEntity empty = companion(helper, Archetype.MYSTIC, "Nonxx", 2.5, 2.5);
		helper.assertFalse(Crafting.chest(empty), "no planks, no chest");
		helper.assertFalse(Crafting.furnace(empty), "no cobblestone, no furnace");
		helper.assertFalse(Crafting.torches(empty), "no coal, no torches");
		CompanionEntity crafter = companion(helper, Archetype.MYSTIC, "Craxx", 4.5, 4.5);
		crafter.inventory().addItem(new ItemStack(Items.OAK_LOG, 5));
		crafter.inventory().addItem(new ItemStack(Items.COBBLESTONE, 8));
		crafter.inventory().addItem(new ItemStack(Items.COAL, 1));
		helper.assertValueEqual(Crafting.planksFromLogs(crafter), 20, "five logs make twenty planks");
		helper.assertValueEqual(crafter.countCarried(s -> s.is(Items.OAK_LOG)), 0, "the logs were used up");
		helper.assertTrue(Crafting.sticks(crafter), "sticks");
		helper.assertValueEqual(crafter.countCarried(s -> s.is(Items.STICK)), 4, "two planks make four sticks");
		helper.assertTrue(Crafting.torches(crafter), "torches from coal and a stick");
		helper.assertValueEqual(crafter.countCarried(s -> s.is(Items.TORCH)), 4, "four torches");
		helper.assertTrue(Crafting.furnace(crafter), "a furnace from eight cobblestone");
		helper.assertTrue(Crafting.chest(crafter), "a chest from eight planks");
		helper.assertTrue(Crafting.doors(crafter), "doors from six planks");
		helper.assertValueEqual(crafter.countCarried(s -> s.is(Items.OAK_DOOR)), 3, "three doors");
		helper.assertTrue(Crafting.hoe(crafter), "a wooden hoe from planks and sticks");
		helper.assertValueEqual(crafter.countCarried(s -> s.is(Items.OAK_PLANKS)), 2, "two planks are left over");
		helper.succeed();
	}

	// ------------------------------------------------------------------ chattiness and autonomy for everyone

	@GameTest(maxTicks = 1200)
	public void autonomyAppliesToCompanionsWhoAreNotFollowing(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		boolean routines = Hearthbound.config().routines;
		double autonomy = Hearthbound.config().autonomy;
		Hearthbound.config().routines = true;
		Hearthbound.config().autonomy = 2.0;
		helper.setBlock(5, 1, 5, Blocks.OAK_LOG);
		CompanionEntity wanderer = companion(helper, Archetype.NATURALIST, "Wandxx", 2.5, 2.5);
		wanderer.setHomePos(helper.absolutePos(new BlockPos(2, 1, 2)));
		helper.assertFalse(wanderer.isRecruited(), "nobody's companion");
		helper.succeedWhen(() -> {
			helper.assertTrue(at(helper, 5, 1, 5).isAir(), "chopped the log by themselves");
			helper.assertTrue(wanderer.countCarried(s -> s.is(Items.OAK_LOG)) > 0, "and kept it");
			Hearthbound.config().routines = routines;
			Hearthbound.config().autonomy = autonomy;
		});
	}

	@GameTest(maxTicks = 400)
	public void wanderersChatToEachOtherWhenChattinessIsUp(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		double chatter = Hearthbound.config().ambientChatter;
		Hearthbound.config().ambientChatter = 2.0;
		ServerPlayer witness = player(helper, 6.5, 6.5);
		CompanionEntity a = companion(helper, Archetype.NATURALIST, "Wandxx", 2.5, 2.5);
		CompanionEntity b = companion(helper, Archetype.ROGUE, "Roamxx", 3.5, 2.5);
		MinecraftServer server = helper.getLevel().getServer();
		helper.onEachTick(() -> {
			for (int i = 0; i < 30; i++) {
				Exchanges.hubTick(server);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(spoken(a, b) > 0, "two wanderers talked, hub or no hub");
			Hearthbound.config().ambientChatter = chatter;
			finish(helper, witness);
		});
	}

	// ------------------------------------------------------------------ big projects
	//
	// These need room well beyond the 8x8 test box, whose edges are barrier blocks: those are cleared first
	// so the sites the builders choose are never cut by them.

	private static final int O = 0;
	private static final int R = 14;

	private static void clearBarriers(GameTestHelper helper) {
		// Keep every chunk of the arena ticking, or companions that wander over a chunk edge simply stop.
		BlockPos low = helper.absolutePos(new BlockPos(O - R - 6, 0, O - R - 6));
		BlockPos high = helper.absolutePos(new BlockPos(O + R + 6, 0, O + R + 6));
		for (int cx = low.getX() >> 4; cx <= high.getX() >> 4; cx++) {
			for (int cz = low.getZ() >> 4; cz <= high.getZ() >> 4; cz++) {
				helper.getLevel().setChunkForced(cx, cz, true);
			}
		}
		for (int x = O - R - 6; x <= O + R + 6; x++) {
			for (int y = -1; y <= 10; y++) {
				for (int z = O - R - 6; z <= O + R + 6; z++) {
					if (at(helper, x, y, z).is(Blocks.BARRIER)) {
						helper.setBlock(x, y, z, Blocks.AIR);
					}
				}
			}
		}
	}

	private static int bigCount(GameTestHelper helper, int yFrom, int yTo, java.util.function.Predicate<BlockState> test) {
		return count(helper, O - R, O + R, yFrom, yTo, test);
	}

	@GameTest(maxTicks = 2400, padding = 60)
	public void buildersBuildAHouseEachFromTheFirstMessage(GameTestHelper helper) {
		clearBarriers(helper);
		floor(helper, Blocks.STONE, O - R - 6, O + R + 6);
		ServerPlayer owner = player(helper, O + 0.5, O + 0.5);
		CompanionEntity builder1 = recruited(helper, owner, Archetype.MYSTIC, "Mistxx", O + 1.5, O + 2.5);
		CompanionEntity builder2 = recruited(helper, owner, Archetype.MYSTIC, "Moonxx", O - 1.5, O + 2.5);
		CompanionEntity collector = recruited(helper, owner, Archetype.NATURALIST, "Collxx", O + 2.5, O - 2.5);
		CompanionEntity farmer = companion(helper, Archetype.FARMHAND, "Farmxx", O - 2.5, O - 2.5); // not recruited, obey off: not theirs to order
		for (CompanionEntity builder : new CompanionEntity[] {builder1, builder2}) {
			builder.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 64));
			builder.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 40));
		}
		helper.assertTrue(Conversations.onChat(owner, BUILDERS_MESSAGE), "the whole message is understood as an order");
		helper.assertTrue(builder1.order() instanceof ProjectOrder && builder2.order() instanceof ProjectOrder, "each builder has a house project");
		helper.assertValueEqual(spoken(builder1, builder2), 1, "one builder acknowledges for the group");
		helper.assertTrue(builder1.lastLineKey().equals("project.ack.group") || builder2.lastLineKey().equals("project.ack.group"), "with the plan");
		helper.assertTrue(farmer.order().kind() == CompanionOrder.Kind.HOME, "the farmer was not asked (" + farmer.order().describe() + ")");
		helper.assertTrue(collector.order().kind() == CompanionOrder.Kind.GATHER, "the collector was asked to gather materials");
		helper.succeedWhen(() -> {
			helper.assertTrue(!(builder1.order() instanceof ProjectOrder) && !(builder2.order() instanceof ProjectOrder),
					"both finished " + said(builder1) + said(builder2));
			helper.assertTrue(bigCount(helper, 1, 6, s -> s.is(Blocks.OAK_PLANKS)) >= 120, "two houses of planks are standing");
			helper.assertTrue(bigCount(helper, 1, 6, s -> s.getBlock() instanceof DoorBlock) >= 2, "each has a door");
			finish(helper, owner);
		});
	}

	@GameTest(maxTicks = 3000, padding = 60)
	public void aBuilderShortOfBlocksGathersLogsCraftsPlanksThenBuilds(GameTestHelper helper) {
		clearBarriers(helper);
		floor(helper, Blocks.STONE, O - R - 6, O + R + 6);
		for (int[] tree : new int[][] {{6, 6}, {8, 6}, {6, 8}, {-6, 6}}) {
			for (int y = 1; y <= 5; y++) {
				helper.setBlock(O + tree[0], y, O + tree[1], Blocks.OAK_LOG);
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					helper.setBlock(O + tree[0] + dx, 6, O + tree[1] + dz, Blocks.OAK_LEAVES);
					helper.setBlock(O + tree[0] + dx, 7, O + tree[1] + dz, Blocks.OAK_LEAVES);
				}
			}
		}
		ServerPlayer owner = player(helper, O + 0.5, O + 0.5);
		CompanionEntity builder = recruited(helper, owner, Archetype.MYSTIC, "Mistxx", O + 1.5, O + 2.5);
		builder.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 40));
		builder.inventory().addItem(new ItemStack(Items.IRON_AXE));
		helper.assertTrue(Conversations.onChat(owner, "Builders, everyone should have their own house"), "understood");
		helper.assertTrue(builder.order() instanceof ProjectOrder, "a house project");
		helper.succeedWhen(() -> {
			helper.assertTrue(!(builder.order() instanceof ProjectOrder), "finished " + said(builder));
			helper.assertTrue(bigCount(helper, 1, 6, s -> s.is(Blocks.OAK_PLANKS)) >= 60, "the house stands, built from the wood they chopped");
			helper.assertTrue(bigCount(helper, 1, 5, s -> s.is(Blocks.OAK_LOG)) < 15, "some of the trees were chopped");
			finish(helper, owner);
		});
	}

	@GameTest(maxTicks = 1500, padding = 60)
	public void noMaterialsMeansAnHonestBlockerAndNoHouse(GameTestHelper helper) {
		clearBarriers(helper);
		floor(helper, Blocks.STONE, O - R - 6, O + R + 6);
		ServerPlayer owner = player(helper, O + 0.5, O + 0.5);
		CompanionEntity builder = recruited(helper, owner, Archetype.MYSTIC, "Mistxx", O + 1.5, O + 2.5);
		helper.assertTrue(Conversations.onChat(owner, "Builders, everyone should have their own house"), "understood");
		boolean[] said = {false};
		helper.onEachTick(() -> said[0] |= builder.lastLineKey().equals("project.blocked.materials"));
		helper.succeedWhen(() -> {
			helper.assertTrue(said[0], "says what is missing " + said(builder));
			helper.assertTrue(!(builder.order() instanceof ProjectOrder), "gave up");
			helper.assertValueEqual(bigCount(helper, 1, 6, s -> !s.isAir()), 0, "and built nothing");
			finish(helper, owner);
		});
	}

	@GameTest(maxTicks = 4000, padding = 60)
	public void farmersBuildAFarmAndABuilderMakesTheStorehouse(GameTestHelper helper) {
		clearBarriers(helper);
		floor(helper, Blocks.GRASS_BLOCK, O - R - 6, O + R + 6);
		helper.setBlock(O + 8, 0, O, Blocks.WATER);
		ServerPlayer owner = player(helper, O + 0.5, O + 0.5);
		CompanionEntity farmer1 = recruited(helper, owner, Archetype.FARMHAND, "Farmxx", O + 1.5, O + 2.5);
		CompanionEntity farmer2 = recruited(helper, owner, Archetype.FARMHAND, "Plowxx", O - 1.5, O + 2.5);
		CompanionEntity builder = recruited(helper, owner, Archetype.MYSTIC, "Mistxx", O + 2.5, O - 2.5);
		for (CompanionEntity farmer : new CompanionEntity[] {farmer1, farmer2}) {
			farmer.inventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 16));
			farmer.inventory().addItem(new ItemStack(Items.WOODEN_HOE));
		}
		builder.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 64));
		builder.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 64));
		builder.inventory().addItem(new ItemStack(Items.COBBLESTONE, 16));
		helper.assertTrue(Conversations.onChat(owner, FARMERS_MESSAGE), "the whole message is understood as an order");
		helper.assertTrue(farmer1.order() instanceof FarmOrder && farmer2.order() instanceof FarmOrder,
				"both farmers work the plot " + said(farmer1) + " " + said(farmer2) + " " + said(builder));
		helper.assertTrue(builder.order() instanceof ProjectOrder, "the builder was asked to make the storehouse");
		// A storehouse chest already stands (as if built earlier), so the farmers' deliveries are tested on their own.
		helper.setBlock(5, 1, 6, Blocks.CHEST);
		Storehouses.register(helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 6)));
		boolean[] ripened = {false};
		helper.succeedWhen(() -> {
			int crops = bigCount(helper, 1, 1, s -> s.getBlock() instanceof CropBlock);
			helper.assertTrue(crops >= 18, "the farm is planted (" + crops + " crops)");
			if (!ripened[0]) {
				ripened[0] = true;
				for (int x = O - R; x <= O + R; x++) {
					for (int z = O - R; z <= O + R; z++) {
						if (at(helper, x, 1, z).getBlock() instanceof CropBlock crop) {
							helper.setBlock(x, 1, z, crop.getStateForAge(crop.getMaxAge()));
						}
					}
				}
			}
			int stored = 0;
			for (int x = O - R; x <= O + R && stored == 0; x++) {
				for (int z = O - R; z <= O + R && stored == 0; z++) {
					if (at(helper, x, 1, z).is(Blocks.CHEST) && helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, 1, z))) instanceof Container chest) {
						for (int slot = 0; slot < chest.getContainerSize(); slot++) {
							if (chest.getItem(slot).is(Items.WHEAT)) {
								stored += chest.getItem(slot).getCount();
							}
						}
					}
				}
			}
			helper.assertTrue(stored > 0, "harvested wheat was put into the storehouse chest");
			finish(helper, owner);
		});
	}

	@GameTest(maxTicks = 2400, padding = 60)
	public void aBuilderMakesAStorehouseWithAChestAndAFurnace(GameTestHelper helper) {
		clearBarriers(helper);
		floor(helper, Blocks.STONE, O - R - 6, O + R + 6);
		ServerPlayer owner = player(helper, O + 0.5, O + 0.5);
		CompanionEntity builder = recruited(helper, owner, Archetype.MYSTIC, "Mistxx", O + 1.5, O + 2.5);
		builder.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 64));
		builder.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 64));
		builder.inventory().addItem(new ItemStack(Items.COBBLESTONE, 16));
		helper.assertTrue(Conversations.onChat(owner, "Builders, please build a storehouse for us to store and cook food"), "understood");
		helper.assertTrue(builder.order() instanceof ProjectOrder, "a storehouse project");
		helper.succeedWhen(() -> {
			helper.assertTrue(bigCount(helper, 1, 4, s -> s.is(Blocks.CHEST)) == 1, "a chest " + said(builder));
			helper.assertTrue(bigCount(helper, 1, 4, s -> s.is(Blocks.FURNACE)) == 1, "and a furnace");
			helper.assertTrue(bigCount(helper, 1, 6, s -> s.is(Blocks.OAK_PLANKS)) >= 50, "inside walls and a roof");
			finish(helper, owner);
		});
	}

	@GameTest(maxTicks = 120, padding = 60)
	public void aFarmWithNoWaterIsReportedNotFaked(GameTestHelper helper) {
		clearBarriers(helper);
		floor(helper, Blocks.GRASS_BLOCK, O - R - 6, O + R + 6);
		ServerPlayer owner = player(helper, O + 0.5, O + 0.5);
		CompanionEntity farmer = recruited(helper, owner, Archetype.FARMHAND, "Farmxx", O + 1.5, O + 2.5);
		farmer.inventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 16));
		helper.assertTrue(Conversations.onChat(owner, "Farmers, build a farm and start harvesting food"), "understood");
		helper.assertValueEqual(farmer.lastLineKey(), "project.blocked.water", "no water, and no bucket");
		helper.assertTrue(!(farmer.order() instanceof FarmOrder), "no farm was started");
		helper.assertValueEqual(count(helper, O - R, O + R, 0, 0, s -> s.is(Blocks.FARMLAND)), 0, "nothing was tilled");
		finish(helper, owner);
		helper.succeed();
	}
}

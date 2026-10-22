package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import io.github.tekless.hearthbound.entity.order.CompanionOrder;
import io.github.tekless.hearthbound.entity.order.TeamJob;
import io.github.tekless.hearthbound.registry.ModEntities;
import io.github.tekless.hearthbound.world.Obey;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Obey mode for the host and for operators (and nobody else), every kind of order under it, the party limit, and
 * plain requests like "build a house" turning into a job for whoever is around.
 */
public class ObeyAndTeamGameTests {
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
		companion.setHomePos(companion.blockPosition());
		return companion;
	}

	private static CompanionEntity recruited(GameTestHelper helper, ServerPlayer owner, Archetype archetype, String name, double x, double z) {
		CompanionEntity companion = companion(helper, archetype, name, x, z);
		Conversations.apply(owner, companion, Intent.of(IntentType.RECRUIT));
		return companion;
	}

	private static void remove(GameTestHelper helper, ServerPlayer player) {
		Obey.set(player, false, false);
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static BlockState at(GameTestHelper helper, int x, int y, int z) {
		return helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z)));
	}

	// ------------------------------------------------------------------ who may use obey mode

	@GameTest(maxTicks = 60, padding = 60)
	public void theHostCommandsEveryoneIncludingOtherPlayersCompanions(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		ServerPlayer host = player(helper, 3.5, 0.5);
		ServerPlayer friend = player(helper, 7.5, 7.5);
		Obey.addTestHost(host.getUUID());
		boolean obeyAll = Hearthbound.config().obeyAll;
		Hearthbound.config().obeyAll = true;
		try {
			CompanionEntity stranger = companion(helper, Archetype.ROGUE, "Strxx", 2.5, 4.5);
			CompanionEntity friends = recruited(helper, friend, Archetype.GUARDIAN, "Frixx", 5.5, 4.5);
			helper.assertTrue(Obey.active(host), "obey mode is on for the host by default");
			helper.assertTrue(Conversations.onChat(host, "everyone, wait here"), "heard");
			helper.assertValueEqual(stranger.order().kind(), CompanionOrder.Kind.WAIT, "a companion with nobody waits");
			helper.assertValueEqual(friends.order().kind(), CompanionOrder.Kind.WAIT, "so does the friend's companion (the host's override)");
			helper.assertTrue(friends.isOwnedBy(friend), "who still travels with the friend");
			helper.assertTrue(Conversations.onChat(host, "everyone, follow me"), "heard");
			helper.assertTrue(stranger.isOwnedBy(host), "the one with nobody joins the host when asked to follow");
			helper.assertTrue(friends.isOwnedBy(friend) && friends.commanderPlayer() == host, "the friend's companion follows the host for now, still the friend's");
			helper.assertTrue(Conversations.onChat(friend, "@frixx follow me"), "the friend calls them back");
			helper.assertTrue(friends.commanderPlayer() == friend, "and they go back to their own player");
			Conversations.Outcome poach = Conversations.apply(host, friends, Intent.of(IntentType.RECRUIT));
			helper.assertFalse(poach.accepted(), "nobody changes sides, not even for the host");
		} finally {
			Obey.removeTestHost(host.getUUID());
			Hearthbound.config().obeyAll = obeyAll;
		}
		remove(helper, host);
		remove(helper, friend);
		helper.succeed();
	}

	@GameTest(maxTicks = 60, padding = 60)
	public void operatorsCanUseObeyButOtherPlayersCannot(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer op = player(helper, 1.5, 0.5);
		ServerPlayer guest = player(helper, 6.5, 0.5);
		server.getPlayerList().op(op.nameAndId());
		CompanionEntity stranger = companion(helper, Archetype.SELLSWORD, "Wanxx", 3.5, 5.5);
		try {
			server.getCommands().performPrefixedCommand(guest.createCommandSourceStack(), "companion obey on");
			helper.assertFalse(Obey.active(guest), "a player without operator rights can't turn obey mode on");
			helper.assertFalse(Obey.set(guest, true, false), "not even directly");
			helper.assertTrue(Conversations.onChat(guest, "everyone, come here"), "they're told nobody nearby is theirs");
			helper.assertValueEqual(stranger.order().kind(), CompanionOrder.Kind.HOME, "and nobody comes");
			server.getCommands().performPrefixedCommand(op.createCommandSourceStack(), "companion obey on");
			helper.assertTrue(Obey.active(op), "an operator can");
			helper.assertTrue(Conversations.onChat(op, "everyone, come here"), "heard");
			helper.assertValueEqual(stranger.order().kind(), CompanionOrder.Kind.COME, "and is obeyed");
			server.getCommands().performPrefixedCommand(op.createCommandSourceStack(), "companion obey off");
			helper.assertFalse(Obey.active(op), "and can turn it off again");
		} finally {
			server.getPlayerList().deop(op.nameAndId());
		}
		remove(helper, op);
		remove(helper, guest);
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = 60)
	public void everyKindOfOrderWorksInObeyMode(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		ServerPlayer host = player(helper, 3.5, 0.5);
		ServerPlayer friend = player(helper, 7.5, 7.5);
		Obey.addTestHost(host.getUUID());
		boolean obeyAll = Hearthbound.config().obeyAll;
		Hearthbound.config().obeyAll = true;
		helper.setBlock(1, 1, 6, Blocks.OAK_LOG);
		try {
			CompanionEntity stranger = companion(helper, Archetype.MYSTIC, "Strxx", 2.5, 3.5);
			CompanionEntity friends = recruited(helper, friend, Archetype.NATURALIST, "Frixx", 5.5, 3.5);
			for (CompanionEntity worker : new CompanionEntity[] {stranger, friends}) {
				String who = worker.companionName();
				worker.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 64));
				worker.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 30));
				worker.inventory().addItem(new ItemStack(Items.IRON_PICKAXE));
				helper.assertTrue(Conversations.apply(host, worker, Intent.of(IntentType.WAIT)).accepted(), who + ": wait");
				helper.assertTrue(Conversations.apply(host, worker, Intent.of(IntentType.COME)).accepted(), who + ": come");
				helper.assertTrue(Conversations.apply(host, worker, Intent.of(IntentType.STATUS)).accepted(), who + ": status");
				helper.assertTrue(Conversations.apply(host, worker, Intent.gather("oak logs", 1)).accepted(), who + ": gather");
				helper.assertTrue(Conversations.apply(host, worker, Intent.of(IntentType.CANCEL)).accepted(), who + ": stop");
				helper.assertTrue(Conversations.apply(host, worker, Intent.build("shelter")).accepted(), who + ": build");
				helper.assertTrue(Conversations.apply(host, worker, Intent.mine("tunnel", 3)).accepted(), who + ": dig");
				helper.assertTrue(Conversations.apply(host, worker, Intent.give("planks", 1)).accepted(), who + ": give");
				helper.assertTrue(Conversations.apply(host, worker, new Intent(IntentType.HOME, null, null, "walk")).accepted(), who + ": go home");
			}
			var zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(4.5, 1, 6.5));
			helper.assertTrue(Conversations.apply(host, stranger, Intent.attack("zombie")).accepted(), "attack");
			zombie.discard();
			helper.assertTrue(Conversations.apply(host, stranger, Intent.of(IntentType.FOLLOW)).accepted() && stranger.isOwnedBy(host), "follow (joins the host)");
			helper.assertTrue(Conversations.apply(host, friends, Intent.of(IntentType.DISMISS)).accepted(), "the host can send someone else's companion home");
			helper.assertFalse(friends.isRecruited(), "who is free again");
		} finally {
			Obey.removeTestHost(host.getUUID());
			Hearthbound.config().obeyAll = obeyAll;
		}
		remove(helper, host);
		remove(helper, friend);
		helper.succeed();
	}

	// ------------------------------------------------------------------ the party limit

	@GameTest(maxTicks = 60)
	public void thePartyLimitCanBeLiftedByTheHostButNotByOthers(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer host = player(helper, 1.5, 0.5);
		ServerPlayer guest = player(helper, 6.5, 0.5);
		Obey.addTestHost(host.getUUID());
		int limit = Hearthbound.config().partyLimit;
		try {
			server.getCommands().performPrefixedCommand(guest.createCommandSourceStack(), "companion partylimit 1");
			helper.assertValueEqual(Hearthbound.config().partyLimit, limit, "a guest can't change it");
			server.getCommands().performPrefixedCommand(host.createCommandSourceStack(), "companion partylimit unlimited");
			helper.assertValueEqual(Hearthbound.config().partyLimit, 0, "the host lifts it");
			for (int i = 0; i < 4; i++) {
				CompanionEntity member = companion(helper, Archetype.FARMHAND, "Memxx" + (char) ('a' + i), 1.5 + i, 4.5);
				helper.assertTrue(Conversations.apply(host, member, Intent.of(IntentType.RECRUIT)).accepted(), "recruit number " + (i + 1));
			}
			server.getCommands().performPrefixedCommand(host.createCommandSourceStack(), "companion partylimit " + guest.getName().getString() + " 1");
			helper.assertValueEqual(Hearthbound.config().partyLimitFor(guest.getUUID()), 1, "one player can be given their own limit");
			CompanionEntity first = companion(helper, Archetype.ROGUE, "Gonexx", 6.5, 6.5);
			CompanionEntity second = companion(helper, Archetype.ROGUE, "Gtwoxx", 5.5, 6.5);
			helper.assertTrue(Conversations.apply(guest, first, Intent.of(IntentType.RECRUIT)).accepted(), "the guest's first");
			helper.assertFalse(Conversations.apply(guest, second, Intent.of(IntentType.RECRUIT)).accepted(), "but not a second");
		} finally {
			Hearthbound.config().partyLimit = limit;
			Hearthbound.config().partyLimits.clear();
			Obey.removeTestHost(host.getUUID());
		}
		remove(helper, host);
		remove(helper, guest);
		helper.succeed();
	}

	// ------------------------------------------------------------------ plain requests: whoever is around

	private static final int O = 0;
	private static final int R = 14;

	private static void openArena(GameTestHelper helper) {
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
		floor(helper, Blocks.STONE, O - R - 6, O + R + 6);
	}

	private static int count(GameTestHelper helper, java.util.function.Predicate<BlockState> test) {
		int found = 0;
		for (int x = O - R; x <= O + R; x++) {
			for (int y = 1; y <= 6; y++) {
				for (int z = O - R; z <= O + R; z++) {
					if (test.test(at(helper, x, y, z))) {
						found++;
					}
				}
			}
		}
		return found;
	}

	@GameTest(maxTicks = 4000, padding = 60)
	public void aPlainRequestBecomesOneHouseBuiltTogether(GameTestHelper helper) {
		openArena(helper);
		for (int[] tree : new int[][] {{7, 7}, {9, 7}, {7, 9}, {-7, 7}}) {
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
		CompanionEntity maddie = recruited(helper, owner, Archetype.MYSTIC, "Madxx", O + 1.5, O - 1.5);
		CompanionEntity luke = recruited(helper, owner, Archetype.MYSTIC, "Lukxx", O - 1.5, O - 1.5);
		CompanionEntity hazel = recruited(helper, owner, Archetype.NATURALIST, "Hazxx", O + 2.5, O - 2.5);
		maddie.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 24));
		luke.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 24));
		hazel.inventory().addItem(new ItemStack(Items.IRON_AXE));
		helper.assertTrue(Conversations.onChat(owner, "let's build a house"), "a plain request, nobody named");
		helper.assertTrue(maddie.order() instanceof TeamJob.Builder && luke.order() instanceof TeamJob.Builder, "both builders are on it");
		helper.assertTrue(((TeamJob.Builder) maddie.order()).job() == ((TeamJob.Builder) luke.order()).job(), "on the same house");
		helper.assertValueEqual(hazel.order().kind(), CompanionOrder.Kind.GATHER, "the collector fetches wood for them");
		helper.assertTrue(maddie.lastLineKey().equals("team.plan") || luke.lastLineKey().equals("team.plan"), "one of them says the plan");
		TeamJob job = ((TeamJob.Builder) maddie.order()).job();
		helper.succeedWhen(() -> {
			helper.assertTrue(job.status() == io.github.tekless.hearthbound.core.task.TaskStatus.SUCCEEDED,
					"finished (" + job.placed() + "/" + job.total() + ", " + maddie.order().describe() + " / " + luke.order().describe() + " / " + hazel.order().describe() + ")");
			helper.assertTrue(job.placed() > 48, "more blocks went in than the builders started with, so the collector's wood was used");
			helper.assertTrue(count(helper, s -> s.is(Blocks.OAK_PLANKS)) >= 60, "the house stands");
			helper.assertTrue(count(helper, s -> s.getBlock() instanceof DoorBlock) <= 2, "one house, one door at most");
			remove(helper, owner);
		});
	}

	@GameTest(maxTicks = 60)
	public void aStatementStartsNoWork(GameTestHelper helper) {
		floor(helper, Blocks.STONE, 0, 7);
		ServerPlayer owner = player(helper, 3.5, 0.5);
		CompanionEntity maddie = recruited(helper, owner, Archetype.MYSTIC, "Madxx", 2.5, 3.5);
		CompanionEntity hazel = recruited(helper, owner, Archetype.NATURALIST, "Hazxx", 4.5, 3.5);
		maddie.inventory().addItem(new ItemStack(Items.OAK_PLANKS, 64));
		Conversations.onChat(owner, "I built a house yesterday");
		Conversations.onChat(owner, "I love building houses");
		Conversations.onChat(owner, "we don't need a farm");
		for (CompanionEntity each : new CompanionEntity[] {maddie, hazel}) {
			helper.assertValueEqual(each.order().kind(), CompanionOrder.Kind.FOLLOW, each.companionName() + " carries on following");
			helper.assertFalse(each.lastLineKey().equals("team.plan"), "nobody makes a plan");
		}
		remove(helper, owner);
		helper.succeed();
	}
}

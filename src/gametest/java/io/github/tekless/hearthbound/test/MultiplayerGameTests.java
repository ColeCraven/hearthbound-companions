package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.config.HearthboundConfig;
import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.core.persona.CastMember;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import io.github.tekless.hearthbound.entity.order.CompanionOrder;
import io.github.tekless.hearthbound.registry.ModEntities;
import io.github.tekless.hearthbound.world.CompanionRoster;
import io.github.tekless.hearthbound.world.CompanionTracker;
import io.github.tekless.hearthbound.world.HubResidents;
import io.github.tekless.hearthbound.world.WorldRules;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Shared-server behaviour: other players can't boss around or hurt someone else's companion,
 * companions never fight players, nothing gets broken in protected places, and the hub spawns
 * its residents once and takes them back when they're dismissed.
 */
public class MultiplayerGameTests {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
			}
		}
	}

	private static ServerPlayer player(GameTestHelper helper, double x, double z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(x, 1, z));
		player.snapTo(pos.x, pos.y, pos.z, 0, 0);
		return player;
	}

	private static void remove(GameTestHelper helper, ServerPlayer... players) {
		for (ServerPlayer player : players) {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
	}

	private static CompanionEntity companion(GameTestHelper helper, String name, double x, double z) {
		CompanionEntity companion = helper.spawn(ModEntities.COMPANION, new Vec3(x, 1, z));
		companion.initIdentity(Archetype.SELLSWORD, name, CompanionSkin.KAI, false);
		return companion;
	}

	/** Where a player looks follows their head, so turn both. */
	private static void turn(ServerPlayer player, float yaw) {
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
	}

	/** Runs the test body with a temporary server config, restoring the old one afterwards. */
	private static HearthboundConfig.Server swapServerConfig(HearthboundConfig.Server replacement) {
		HearthboundConfig.Server old = Hearthbound.config().server;
		Hearthbound.config().server = replacement;
		return old;
	}

	@GameTest(maxTicks = 40)
	public void onlyTheOwnerGivesOrders(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 1.5, 1.5);
		ServerPlayer stranger = player(helper, 6.5, 6.5);
		CompanionEntity companion = companion(helper, "Wulf", 3.5, 3.5);
		helper.assertTrue(Conversations.apply(owner, companion, Intent.of(IntentType.RECRUIT)).accepted(), "owner recruits");

		Conversations.Outcome follow = Conversations.apply(stranger, companion, Intent.of(IntentType.FOLLOW));
		helper.assertFalse(follow.accepted(), "a stranger's order is refused");
		helper.assertValueEqual(follow.lineKey(), "recruit.taken", "refusal says who they travel with");
		helper.assertValueEqual(follow.vars().get("owner"), owner.getName().getString(), "names the owner");
		Conversations.Outcome dismiss = Conversations.apply(stranger, companion, Intent.of(IntentType.DISMISS));
		helper.assertFalse(dismiss.accepted(), "a stranger can't dismiss them either");
		helper.assertTrue(companion.isOwnedBy(owner), "still with the owner");
		helper.assertValueEqual(companion.ownerName(), owner.getName().getString(), "owner name is synced");
		remove(helper, owner, stranger);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void strangersCantHurtCompanionsAndCompanionsNeverFightPlayers(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 1.5, 1.5);
		ServerPlayer stranger = player(helper, 4.5, 3.5);
		CompanionEntity companion = companion(helper, "Ragna", 3.5, 3.5);
		Conversations.apply(owner, companion, Intent.of(IntentType.RECRUIT));
		float before = companion.getHealth();
		companion.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(stranger), 6.0F);
		helper.assertValueEqual(companion.getHealth(), before, "a stranger's hit does nothing");
		helper.assertFalse(companion.canAttack(stranger), "never targets another player");
		helper.assertFalse(companion.canAttack(owner), "never targets the owner");
		remove(helper, owner, stranger);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void noGatheringInsideProtectedAreasOrNextToBuilds(GameTestHelper helper) {
		floor(helper);
		BlockPos centre = helper.absolutePos(new BlockPos(4, 1, 4));
		HearthboundConfig.Server server = new HearthboundConfig.Server();
		HearthboundConfig.Area area = new HearthboundConfig.Area();
		area.dimension = helper.getLevel().dimension().identifier().toString();
		area.x = centre.getX();
		area.z = centre.getZ();
		area.radius = 6;
		server.protectedAreas.add(area);
		HearthboundConfig.Server old = swapServerConfig(server);
		try {
			ServerPlayer owner = player(helper, 1.5, 1.5);
			CompanionEntity companion = companion(helper, "Torvald", 3.5, 3.5);
			Conversations.apply(owner, companion, Intent.of(IntentType.RECRUIT));
			Conversations.Outcome outcome = Conversations.apply(owner, companion, Intent.gather("oak logs", 4));
			helper.assertFalse(outcome.accepted(), "gathering refused in a protected area");
			helper.assertValueEqual(outcome.lineKey(), "fail.protected", "says why");
			helper.assertValueEqual(companion.order().kind(), CompanionOrder.Kind.FOLLOW, "keeps following");

			server.protectedAreas.clear();
			helper.setBlock(6, 1, 6, Blocks.OAK_PLANKS);
			helper.assertFalse(WorldRules.mayBreak(helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 5))), "not right next to a build");
			helper.assertTrue(WorldRules.mayBreak(helper.getLevel(), helper.absolutePos(new BlockPos(0, 6, 0)).above(10)), "fine out in the open");
			remove(helper, owner);
		} finally {
			swapServerConfig(old);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void hubSpawnsResidentsOnceAndTakesThemBack(GameTestHelper helper) {
		floor(helper);
		BlockPos hub = helper.absolutePos(new BlockPos(4, 1, 4));
		HearthboundConfig.Server server = new HearthboundConfig.Server();
		server.hub.enabled = true;
		server.hub.dimension = helper.getLevel().dimension().identifier().toString();
		server.hub.x = hub.getX();
		server.hub.y = hub.getY();
		server.hub.z = hub.getZ();
		server.hub.wanderRadius = 3;
		server.hub.welcomeTip = false;
		server.hub.residents = new ArrayList<>(List.of("freya", "bram"));
		HearthboundConfig.Server old = swapServerConfig(server);
		try {
			CompanionRoster roster = CompanionRoster.get(helper.getLevel().getServer());
			for (CastMember member : List.of(CastMember.FREYA, CastMember.BRAM)) {
				// Other tests may have left these two in the roster; start clean.
				new ArrayList<>(roster.entries().entrySet()).stream()
						.filter(e -> member.id().equals(e.getValue().cast))
						.forEach(e -> roster.forget(UUID.fromString(e.getKey())));
			}
			helper.assertValueEqual(HubResidents.spawnMissing(helper.getLevel(), hub), 2, "both residents spawn");
			helper.assertValueEqual(HubResidents.spawnMissing(helper.getLevel(), hub), 0, "nobody spawns twice");

			CompanionEntity freya = CompanionTracker.all().stream()
					.filter(c -> c.castMember().orElse(null) == CastMember.FREYA && c.level() == helper.getLevel()).findFirst().orElseThrow();
			helper.assertTrue(freya.isHubResident(), "Freya lives at the hub");
			helper.assertTrue(freya.distanceToSqr(Vec3.atBottomCenterOf(hub)) < 64, "and stands near it");
			helper.assertFalse(freya.getMainHandItem().isEmpty(), "with her starting weapon");

			ServerPlayer friend = player(helper, 1.5, 1.5);
			helper.assertTrue(Conversations.apply(friend, freya, Intent.of(IntentType.RECRUIT)).accepted(), "a friend recruits her");
			helper.assertValueEqual(roster.ownedBy(friend.getUUID()), 1, "the roster counts her in the party");
			Conversations.apply(friend, freya, Intent.of(IntentType.DISMISS));
			helper.assertFalse(freya.isRecruited(), "dismissed");
			helper.assertValueEqual(freya.home(), hub, "home is the hub again");
			helper.assertValueEqual(freya.order().kind(), CompanionOrder.Kind.HOME, "heading home");
			helper.assertValueEqual(roster.ownedBy(friend.getUUID()), 0, "and no longer counted");
			remove(helper, friend);
		} finally {
			swapServerConfig(old);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void plainChatCarriesOnAConversation(GameTestHelper helper) {
		floor(helper);
		ServerPlayer talker = player(helper, 1.5, 1.5);
		CompanionEntity companion = companion(helper, "Wulf", 3.5, 3.5);
		// Other tests run beside this one, so their players count as "people around": face Wulf.
		talker.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, companion.getEyePosition());
		helper.assertTrue(Conversations.onChat(talker, "@wulf hello"), "addressed by name");
		helper.assertTrue(Conversations.onChat(talker, "how are you?"), "then no @ needed");
		helper.assertTrue(Conversations.onChat(talker, "tell me about your family"), "still talking");
		helper.assertTrue(companion.memory().lines().stream().anyMatch(l -> l.fromPlayer() && l.text().equals("tell me about your family")),
				"the companion heard it");
		helper.assertFalse(Conversations.onChat(talker, "look at this @Steve"), "a message for another player stays theirs");

		// With a friend around, plain chat only goes to the companion while you're facing them.
		ServerPlayer friend = player(helper, 6.5, 1.5);
		float facing = talker.getYRot();
		turn(talker, facing + 180); // turned away from Wulf
		helper.assertFalse(Conversations.onChat(talker, "lol nice"), "chat with a friend isn't taken over");
		turn(talker, facing);
		helper.assertTrue(Conversations.onChat(talker, "lol nice"), "facing the companion, it's for them");
		Conversations.onChat(talker, "bye");
		turn(talker, facing + 180);
		helper.assertFalse(Conversations.onChat(talker, "anyway"), "bye ends the conversation");
		remove(helper, talker, friend);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void summonedCastMembersGetTheirOwnGear(GameTestHelper helper) {
		floor(helper);
		ServerPlayer op = player(helper, 3.5, 1.5);
		var server = helper.getLevel().getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(op).withPosition(op.position())
				.withLevel(helper.getLevel()), "companion summon bram");
		CompanionEntity bram = CompanionTracker.all().stream()
				.filter(c -> c.level() == helper.getLevel() && c.companionName().equals("Bram") && c.distanceTo(op) < 4)
				.findFirst().orElseThrow(() -> helper.assertionException("Bram was summoned"));
		helper.assertValueEqual(bram.archetype(), Archetype.SELLSWORD, "Bram is a sellsword");
		helper.assertTrue(bram.getMainHandItem().is(net.minecraft.world.item.Items.IRON_SWORD), "with a sellsword's iron sword, not someone else's kit");
		bram.discard();
		remove(helper, op);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void repliesReachTheSpeakerAndPeopleNearby(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = player(helper, 1.5, 1.5);
		ServerPlayer stranger = player(helper, 6.5, 6.5);
		CompanionEntity companion = companion(helper, "Sigrun", 3.5, 3.5);
		Conversations.apply(owner, companion, Intent.of(IntentType.RECRUIT));
		Conversations.handleMessage(stranger, companion, "hello there");
		helper.assertTrue(companion.memory().partner().equals(stranger.getUUID().toString()), "talking with the stranger now");
		Conversations.handleMessage(owner, companion, "how are you?");
		helper.assertValueEqual(companion.memory().lines().stream().filter(l -> l.fromPlayer()).count(), 1L,
				"the stranger's words were forgotten when the owner spoke");
		helper.assertTrue(Conversations.onChat(owner, "hey @sigrun, how are you?"), "an @name mid-message reaches her");
		helper.assertTrue(companion.memory().lines().stream().anyMatch(l -> l.fromPlayer() && l.text().equals("hey how are you?")),
				"with the name taken out");
		helper.assertFalse(Conversations.onChat(owner, "look at this @Steve"), "mentions of other players are left alone");
		remove(helper, owner, stranger);
		helper.succeed();
	}
}

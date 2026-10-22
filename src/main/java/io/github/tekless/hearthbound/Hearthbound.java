package io.github.tekless.hearthbound;

import io.github.tekless.hearthbound.ai.AiService;
import io.github.tekless.hearthbound.command.CompanionCommands;
import io.github.tekless.hearthbound.config.HearthboundConfig;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.order.CompanionOrder;
import io.github.tekless.hearthbound.network.HearthboundNetwork;
import io.github.tekless.hearthbound.registry.ModEntities;
import io.github.tekless.hearthbound.registry.ModItems;
import io.github.tekless.hearthbound.server.SkinProfiles;
import io.github.tekless.hearthbound.server.VanillaClients;
import io.github.tekless.hearthbound.world.AmbientChatter;
import io.github.tekless.hearthbound.world.Gestures;
import io.github.tekless.hearthbound.world.Moments;
import io.github.tekless.hearthbound.world.CompanionRoster;
import io.github.tekless.hearthbound.world.CompanionTracker;
import io.github.tekless.hearthbound.world.HubResidents;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Hearthbound implements ModInitializer {
	public static final String MOD_ID = "hearthbound";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static HearthboundConfig config = new HearthboundConfig();
	private static AiService ai;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static HearthboundConfig config() {
		return config;
	}

	/** The AI service, or null when AI dialogue is off. */
	public static AiService ai() {
		return ai;
	}

	@Override
	public void onInitialize() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		config = HearthboundConfig.load(configDir.resolve("hearthbound.json"));
		boolean vanillaClients = VanillaClients.decide(config);
		ModEntities.init();
		ModItems.init(vanillaClients);
		HearthboundNetwork.init();
		if (vanillaClients) {
			VanillaClients.start();
		}
		CompanionTracker.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, selection) -> CompanionCommands.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ai = AiService.create(config, configDir, server);
			SkinProfiles.load(configDir);
			CompanionRoster.get(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			CompanionRoster.close();
			io.github.tekless.hearthbound.life.HomeAreas.close();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ai = null;
			Conversations.reset();
			io.github.tekless.hearthbound.entity.order.Projects.reset();
			io.github.tekless.hearthbound.world.Storehouses.reset();
			io.github.tekless.hearthbound.world.Exchanges.reset();
			Moments.reset();
			Gestures.reset();
			io.github.tekless.hearthbound.life.LifeTicker.reset();
			io.github.tekless.hearthbound.life.Favours.reset();
			io.github.tekless.hearthbound.blueprint.Blueprints.reset();
		});

		// "@Hazel follow me" in normal chat. Runs on the server thread after the message is shown.
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
			String text = message.signedContent();
			sender.level().getServer().execute(() -> Conversations.onChat(sender, text));
		});

		// Following companions travel with their owner through portals and long teleports.
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> bringFollowers(player, origin));
		ServerTickEvents.END_SERVER_TICK.register(Hearthbound::tick);

		// A companion's arrows and swings only ever hurt what they're fighting: never a player, villager or pet caught in the way.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(source.getEntity() instanceof CompanionEntity companion) || companion == entity || companion.mayHarm(entity));

		// Companions notice what happens to their player (advancements come from PlayerAdvancementsMixin).
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				Moments.playerDied(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				Moments.respawned(newPlayer);
			}
		});
		EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) {
				Moments.wentToBed(player);
				// A bed a player sleeps in is theirs: no companion will claim it.
				if (player.level() instanceof ServerLevel level && io.github.tekless.hearthbound.life.HomeAreas.bedHead(level, pos) != null) {
					io.github.tekless.hearthbound.life.HomeAreas.get(level.getServer()).markPlayerBed(level, io.github.tekless.hearthbound.life.HomeAreas.bedHead(level, pos));
				}
			}
		});
		// Right-clicking a bed a companion is asleep in gets them up, and the bed is the player's from then on.
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world instanceof ServerLevel level && player instanceof ServerPlayer && !player.isSpectator()) {
				net.minecraft.core.BlockPos head = io.github.tekless.hearthbound.life.HomeAreas.bedHead(level, hit.getBlockPos());
				if (head != null) {
					for (CompanionEntity companion : CompanionTracker.all()) {
						if (companion.level() == level && companion.isSleeping() && companion.getSleepingPos().filter(head::equals).isPresent()) {
							companion.wakeUp();
							io.github.tekless.hearthbound.life.HomeAreas.get(level.getServer()).markPlayerBed(level, head);
							companion.sayMaybe("life.bed_taken", java.util.Map.of(), 1.0);
						}
					}
				}
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) {
				Moments.wokeUp(player);
			}
		});
		LOGGER.info("Hearthbound Companions ready (AI provider: {})", config.ai.provider);
	}

	private static void tick(MinecraftServer server) {
		Gestures.tick(server);
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		HubResidents.tick(server);
		io.github.tekless.hearthbound.life.LifeTicker.tick(server);
		if (server.getTickCount() % 200 == 0) {
			CompanionRoster.get(server).save();
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (CompanionEntity companion : Conversations.party(player)) {
				if (companion.baseMode() == CompanionOrder.Kind.FOLLOW && companion.order().kind() == CompanionOrder.Kind.FOLLOW
						&& companion.commanderPlayer() == player && companion.distanceTo(player) > 48) {
					// Long teleports (/tp, ender pearls): catch up before our chunk unloads.
					companion.teleportNear(player);
				}
			}
			AmbientChatter.tick(player);
			Moments.tick(player);
		}
	}

	private static void bringFollowers(ServerPlayer player, ServerLevel origin) {
		for (CompanionEntity companion : CompanionTracker.all()) {
			// Only loaded companions are here, i.e. the ones that were near the player when they left.
			// That includes someone following this player in obey mode.
			// Knocked-down followers come too: otherwise they'd be stranded in an unloaded chunk.
			if (companion.level() == origin && companion.baseMode() == CompanionOrder.Kind.FOLLOW && companion.commanderPlayer() == player) {
				boolean moved = companion.teleportNear(player);
				LOGGER.info("{} followed {} from {}: {}", companion.companionName(), player.getName().getString(),
						origin.dimension().identifier(), moved ? "ok" : "no safe spot");
			}
		}
	}
}

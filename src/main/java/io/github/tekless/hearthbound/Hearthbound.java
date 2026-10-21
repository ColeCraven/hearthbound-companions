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
import io.github.tekless.hearthbound.world.AmbientChatter;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
		ModEntities.init();
		ModItems.init();
		HearthboundNetwork.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, selection) -> CompanionCommands.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> ai = AiService.create(config, configDir, server));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ai = null);

		// "@Wren follow me" in normal chat. Runs on the server thread after the message is shown.
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
			String text = message.signedContent();
			sender.level().getServer().execute(() -> Conversations.onChat(sender, text));
		});

		// Following companions travel with their owner through portals and long teleports.
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> bringFollowers(player, origin));
		ServerTickEvents.END_SERVER_TICK.register(Hearthbound::tick);
		LOGGER.info("Hearthbound Companions ready (AI provider: {})", config.ai.provider);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (CompanionEntity companion : Conversations.party(player)) {
				if (companion.baseMode() == CompanionOrder.Kind.FOLLOW && companion.order().kind() == CompanionOrder.Kind.FOLLOW
						&& companion.distanceTo(player) > 48) {
					// Long teleports (/tp, ender pearls): catch up before our chunk unloads.
					companion.teleportNear(player);
				}
			}
			AmbientChatter.tick(player);
		}
	}

	private static void bringFollowers(ServerPlayer player, ServerLevel origin) {
		java.util.List<Entity> loaded = new java.util.ArrayList<>();
		origin.getAllEntities().forEach(loaded::add);
		for (Entity entity : loaded) {
			// Only loaded companions are here, i.e. the ones that were near the player when they left.
			// Knocked-down followers come too: otherwise they'd be stranded in an unloaded chunk.
			if (entity instanceof CompanionEntity companion && companion.isOwnedBy(player) && companion.isAlive()
					&& companion.baseMode() == CompanionOrder.Kind.FOLLOW) {
				boolean moved = companion.teleportNear(player);
				LOGGER.info("{} followed {} from {}: {}", companion.companionName(), player.getName().getString(),
						origin.dimension().identifier(), moved ? "ok" : "no safe spot");
			}
		}
	}
}

package io.github.tekless.hearthbound.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.client.render.CompanionRenderer;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.network.PartyStatusPayload;
import io.github.tekless.hearthbound.network.TalkPayloads;
import io.github.tekless.hearthbound.registry.ModEntities;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class HearthboundClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Hearthbound.id("main"));
	private static final Pattern AT_NAME = Pattern.compile("(?:^|\\s)@([A-Za-z]+)");
	private static KeyMapping talkKey;
	private static KeyMapping conversationKey;
	private static KeyMapping hudKey;
	private static boolean hudVisible = true;
	private static String lastAddressed;

	public static boolean hudVisible() {
		return hudVisible;
	}

	/** The "talk to companion" key (exposed for client game tests). */
	public static KeyMapping talkKey() {
		return talkKey;
	}

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.COMPANION, CompanionRenderer::new);

		talkKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hearthbound.talk", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, CATEGORY));
		hudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hearthbound.toggle_hud", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, CATEGORY));
		conversationKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hearthbound.conversation", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, CATEGORY));

		ClientPlayNetworking.registerGlobalReceiver(PartyStatusPayload.TYPE, (payload, context) -> PartyHud.update(payload));
		ClientPlayNetworking.registerGlobalReceiver(TalkPayloads.State.TYPE, (payload, context) -> TalkScreen.receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PartyHud.clear());
		// "@Charlie join me" makes Charlie the one the talk key fills in next, even before the party list updates.
		ClientSendMessageEvents.CHAT.register(HearthboundClient::rememberAddressee);
		HudElementRegistry.addLast(Hearthbound.id("party"), PartyHud::render);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (talkKey.consumeClick()) {
				openTalk(client);
			}
			while (conversationKey.consumeClick()) {
				openConversation(client);
			}
			while (hudKey.consumeClick()) {
				hudVisible = !hudVisible;
			}
		});
	}

	/**
	 * Opens the conversation screen for the companion under the crosshair, else the one you last talked to, else the
	 * first in your party. On a server without the mod there's nothing to talk to the screen, so the chat box opens
	 * instead.
	 */
	private static void openConversation(Minecraft client) {
		if (client.player == null || client.gui.screen() != null) {
			return;
		}
		if (!ClientPlayNetworking.canSend(TalkPayloads.Open.TYPE)) {
			openTalk(client);
			return;
		}
		CompanionEntity target = null;
		if (client.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof CompanionEntity companion) {
			target = companion;
		} else if (client.crosshairPickEntity instanceof CompanionEntity companion) {
			target = companion;
		}
		int id = -1;
		if (target != null) {
			id = target.getId();
			lastAddressed = firstName(target);
		} else {
			for (PartyStatusPayload.Entry entry : PartyHud.entries()) {
				if (lastAddressed != null && entry.name().split(" ")[0].equalsIgnoreCase(lastAddressed)) {
					id = entry.entityId();
				}
			}
			if (id < 0 && !PartyHud.entries().isEmpty()) {
				id = PartyHud.entries().get(0).entityId();
			}
		}
		if (id < 0) {
			openTalk(client); // nobody to talk to yet: the chat box, ready for "@Name"
			return;
		}
		ClientPlayNetworking.send(new TalkPayloads.Open(id));
		client.gui.setScreen(new TalkScreen(id));
	}

	/**
	 * Opens the normal chat box with "@Name " filled in: the companion under the crosshair, else the
	 * one you last talked to, else the first in your party.
	 */
	private static void openTalk(Minecraft client) {
		if (client.player == null || client.gui.screen() != null) {
			return;
		}
		String name = null;
		HitResult hit = client.hitResult;
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof CompanionEntity companion) {
			name = firstName(companion);
		} else {
			Entity crosshair = client.crosshairPickEntity;
			if (crosshair instanceof CompanionEntity companion) {
				name = firstName(companion);
			}
		}
		if (name == null) {
			name = lastAddressed != null ? lastAddressed
					: PartyHud.entries().isEmpty() ? null : PartyHud.entries().get(0).name().split(" ")[0];
		}
		if (name != null) {
			lastAddressed = name;
		}
		client.gui.setScreen(new ChatScreen(name == null ? "@" : "@" + name + " ", false));
	}

	private static void rememberAddressee(String message) {
		Minecraft client = Minecraft.getInstance();
		Matcher at = AT_NAME.matcher(message);
		if (client.level == null || !at.find()) {
			return;
		}
		String typed = at.group(1);
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof CompanionEntity companion && firstName(companion).equalsIgnoreCase(typed)) {
				lastAddressed = firstName(companion);
				return;
			}
		}
	}

	private static String firstName(CompanionEntity companion) {
		return companion.getCustomName() == null ? "" : companion.getCustomName().getString().split(" ")[0];
	}
}

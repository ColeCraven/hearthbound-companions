package io.github.tekless.hearthbound.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.client.render.CompanionRenderer;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.network.PartyStatusPayload;
import io.github.tekless.hearthbound.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
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
	private static KeyMapping talkKey;
	private static KeyMapping hudKey;
	private static boolean hudVisible = true;
	private static String lastAddressed;

	public static boolean hudVisible() {
		return hudVisible;
	}

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.COMPANION, CompanionRenderer::new);

		talkKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hearthbound.talk", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, CATEGORY));
		hudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hearthbound.toggle_hud", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, CATEGORY));

		ClientPlayNetworking.registerGlobalReceiver(PartyStatusPayload.TYPE, (payload, context) -> PartyHud.update(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PartyHud.clear());
		HudElementRegistry.addLast(Hearthbound.id("party"), PartyHud::render);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (talkKey.consumeClick()) {
				openTalk(client);
			}
			while (hudKey.consumeClick()) {
				hudVisible = !hudVisible;
			}
		});
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

	private static String firstName(CompanionEntity companion) {
		return companion.getCustomName() == null ? "" : companion.getCustomName().getString().split(" ")[0];
	}
}

package io.github.tekless.hearthbound.network;

import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class HearthboundNetwork {
	private HearthboundNetwork() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(PartyStatusPayload.TYPE, PartyStatusPayload.CODEC);
	}

	/** Sends the party HUD data (called about once a second). */
	public static void sendPartyStatus(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, PartyStatusPayload.TYPE)) {
			return;
		}
		List<PartyStatusPayload.Entry> entries = new ArrayList<>();
		for (CompanionEntity companion : Conversations.party(player)) {
			if (entries.size() >= 8) {
				break;
			}
			entries.add(new PartyStatusPayload.Entry(companion.getId(), companion.companionName(), companion.archetype().id(),
					companion.isDowned() ? "knocked down" : companion.order().describe(),
					companion.getHealth(), companion.getMaxHealth(), companion.isDowned()));
		}
		ServerPlayNetworking.send(player, new PartyStatusPayload(entries));
	}
}

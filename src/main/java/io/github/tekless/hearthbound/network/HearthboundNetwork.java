package io.github.tekless.hearthbound.network;

import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.dialogue.TalkView;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class HearthboundNetwork {
	private HearthboundNetwork() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(PartyStatusPayload.TYPE, PartyStatusPayload.CODEC);
		// The conversation screen (clients with the mod only; the chat box works for everyone).
		PayloadTypeRegistry.serverboundPlay().register(TalkPayloads.Open.TYPE, TalkPayloads.Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TalkPayloads.Say.TYPE, TalkPayloads.Say.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TalkPayloads.State.TYPE, TalkPayloads.State.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TalkPayloads.Open.TYPE, (payload, context) -> sendTalk(context.player(), payload.entityId()));
		ServerPlayNetworking.registerGlobalReceiver(TalkPayloads.Say.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (player.level().getEntity(payload.entityId()) instanceof CompanionEntity companion && TalkView.say(player, companion, payload.text())) {
				sendTalk(player, companion.getId());
			}
		});
	}

	/** What the conversation screen shows about one companion, if this player may talk to them. */
	public static void sendTalk(ServerPlayer player, int entityId) {
		Entity entity = player.level().getEntity(entityId);
		if (TalkView.canTalk(player, entity) && ServerPlayNetworking.canSend(player, TalkPayloads.State.TYPE)) {
			ServerPlayNetworking.send(player, TalkView.state(player, (CompanionEntity) entity));
		}
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

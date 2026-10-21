package io.github.tekless.hearthbound.network;

import io.github.tekless.hearthbound.Hearthbound;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the player's companions and what each is doing, for the party HUD. */
public record PartyStatusPayload(List<Entry> entries) implements CustomPacketPayload {
	public static final Type<PartyStatusPayload> TYPE = new Type<>(Hearthbound.id("party_status"));

	public record Entry(int entityId, String name, String archetype, String task, float health, float maxHealth, boolean downed) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Entry::entityId,
				ByteBufCodecs.STRING_UTF8, Entry::name,
				ByteBufCodecs.STRING_UTF8, Entry::archetype,
				ByteBufCodecs.STRING_UTF8, Entry::task,
				ByteBufCodecs.FLOAT, Entry::health,
				ByteBufCodecs.FLOAT, Entry::maxHealth,
				ByteBufCodecs.BOOL, Entry::downed,
				Entry::new);
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, PartyStatusPayload> CODEC = Entry.CODEC
			.apply(ByteBufCodecs.list(8))
			.map(PartyStatusPayload::new, PartyStatusPayload::entries);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

package io.github.tekless.hearthbound.network;

import io.github.tekless.hearthbound.Hearthbound;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The conversation screen's messages. Only clients with the mod send or receive them; everyone else keeps using the
 * chat box, which works the same as ever.
 */
public final class TalkPayloads {
	private TalkPayloads() {
	}

	/** Client -> server: "show me this companion" (on opening the screen, and about once a second while it's open). */
	public record Open(int entityId) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(Hearthbound.id("talk_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Open::entityId, Open::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: something typed (or a quick reply clicked) for this companion. */
	public record Say(int entityId, String text) implements CustomPacketPayload {
		public static final Type<Say> TYPE = new Type<>(Hearthbound.id("talk_say"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Say> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Say::entityId,
				ByteBufCodecs.stringUtf8(256), Say::text,
				Say::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** One line of the conversation so far. */
	public record Line(boolean fromPlayer, String text) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Line> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, Line::fromPlayer,
				ByteBufCodecs.STRING_UTF8, Line::text,
				Line::new);
	}

	/** Who they are and how they are. */
	public record Header(int entityId, String name, String archetype, String role, String mood, String need, String task) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Header> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Header::entityId,
				ByteBufCodecs.STRING_UTF8, Header::name,
				ByteBufCodecs.STRING_UTF8, Header::archetype,
				ByteBufCodecs.STRING_UTF8, Header::role,
				ByteBufCodecs.STRING_UTF8, Header::mood,
				ByteBufCodecs.STRING_UTF8, Header::need,
				ByteBufCodecs.STRING_UTF8, Header::task,
				Header::new);
	}

	/** Server -> client: the companion, the conversation so far, and a few things worth saying right now. */
	public record State(Header header, List<Line> lines, List<String> suggestions) implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(Hearthbound.id("talk_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(
				Header.CODEC, State::header,
				Line.CODEC.apply(ByteBufCodecs.list(32)), State::lines,
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(8)), State::suggestions,
				State::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}

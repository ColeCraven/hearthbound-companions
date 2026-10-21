package io.github.tekless.hearthbound.entity;

import io.github.tekless.hearthbound.core.persona.CastMember;
import java.util.Locale;
import java.util.Optional;

/**
 * Companion looks. Variants 0-17 are the nine default player skins shipped with Minecraft 26.3,
 * each in a wide (4px arms) and a slim (3px arms) version, read from the game's own textures at
 * {@code textures/entity/player/<wide|slim>/<name>.png} - nothing is copied into this mod.
 * Variants from {@link CastMember#SKIN_OFFSET} are the original cast skins bundled with the mod.
 */
public enum CompanionSkin {
	STEVE, ALEX, ARI, EFE, KAI, MAKENA, NOOR, SUNNY, ZURI;

	public static final int VARIANTS = values().length * 2;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Encodes skin + arm width as one small int for syncing (0-8 wide, 9-17 slim). */
	public int encode(boolean slim) {
		return ordinal() + (slim ? values().length : 0);
	}

	public static CompanionSkin decodeSkin(int encoded) {
		return values()[Math.floorMod(encoded, values().length)];
	}

	public static boolean decodeSlim(int encoded) {
		Optional<CastMember> cast = CastMember.bySkinVariant(encoded);
		if (cast.isPresent()) {
			return cast.get().slim();
		}
		return Math.floorMod(encoded, VARIANTS) >= values().length;
	}

	/** Keeps a stored variant within the known ranges (default skins or cast skins). */
	public static int sanitize(int encoded) {
		if (CastMember.bySkinVariant(encoded).isPresent()) {
			return encoded;
		}
		return Math.floorMod(encoded, VARIANTS);
	}

	/** Texture as {namespace, path}: vanilla default skins or this mod's cast skins. */
	public static String[] texture(int encoded) {
		Optional<CastMember> cast = CastMember.bySkinVariant(encoded);
		if (cast.isPresent()) {
			return new String[] {"hearthbound", "textures/entity/companion/" + cast.get().id() + ".png"};
		}
		return new String[] {"minecraft", "textures/entity/player/" + (decodeSlim(encoded) ? "slim/" : "wide/") + decodeSkin(encoded).id() + ".png"};
	}

	/** Short description of a variant for messages, e.g. "noor (slim)" or "Wren's outfit". */
	public static String describe(int encoded) {
		return CastMember.bySkinVariant(encoded).map(c -> c.displayName() + "'s outfit")
				.orElse(decodeSkin(encoded).id() + " (" + (decodeSlim(encoded) ? "slim" : "wide") + ")");
	}

	public static Optional<CompanionSkin> byId(String id) {
		for (CompanionSkin skin : values()) {
			if (skin.id().equalsIgnoreCase(id)) {
				return Optional.of(skin);
			}
		}
		return Optional.empty();
	}
}

package io.github.tekless.hearthbound.entity;

import java.util.Locale;
import java.util.Optional;

/**
 * The nine default player skins shipped with Minecraft 26.3. Each exists in a wide (4px arms) and
 * a slim (3px arms) variant; together that's 18 looks, all read from the game's own textures at
 * {@code textures/entity/player/<wide|slim>/<name>.png} - nothing is copied into this mod.
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
		return Math.floorMod(encoded, VARIANTS) >= values().length;
	}

	/** Texture path inside the vanilla {@code minecraft} namespace, without the .png suffix. */
	public static String texturePath(int encoded) {
		return "textures/entity/player/" + (decodeSlim(encoded) ? "slim/" : "wide/") + decodeSkin(encoded).id() + ".png";
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

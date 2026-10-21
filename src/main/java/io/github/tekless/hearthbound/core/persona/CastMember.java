package io.github.tekless.hearthbound.core.persona;

import java.util.Locale;
import java.util.Optional;

/**
 * The named Hearthbound cast: twelve original characters, two per archetype, each with their own
 * skin (drawn by {@code tools/skins/cast.py}), arm width, pronouns and a short background that
 * keeps their look, voice and story consistent. Companions can also be generic wanderers using
 * the game's default skins.
 */
public enum CastMember {
	BRAM("Bram", Archetype.SELLSWORD, false, "he/him",
			"a scarred veteran sellsword from the southern marches who has outlived three mercenary companies"),
	FREYA("Freya", Archetype.SELLSWORD, true, "she/her",
			"a golden-haired shieldmaiden from the north who left her clan to see the world; blunt, fearless and fiercely loyal"),
	HAZEL("Hazel", Archetype.NATURALIST, true, "she/her",
			"an auburn-haired scholar-ranger who sketches every creature, flower and stone she meets in her battered notebooks"),
	EMMETT("Emmett", Archetype.NATURALIST, false, "he/him",
			"a soft-spoken herbalist, always with a sprig of herbs behind his ear, who knows the use of every root and leaf"),
	CASS("Cass", Archetype.ROGUE, true, "she/her",
			"a sharp-tongued treasure hunter in a crimson bandana who has looted more old tombs than she will ever admit"),
	LUCAS("Lucas", Archetype.ROGUE, false, "he/him",
			"a silver-tongued thief with a weakness for shiny things and lost causes"),
	DAISY("Daisy", Archetype.GUARDIAN, true, "she/her",
			"a paladin of the Dawn with a gold circlet and a long braid, sworn to guard travellers on the wild roads"),
	CONOBY("Conoby", Archetype.GUARDIAN, false, "he/him",
			"a blond, greying old knight in plate and a crimson tabard who has buried too many friends and refuses to lose another"),
	SAMUEL("Samuel", Archetype.FARMHAND, false, "he/him",
			"a farm lad who ran off to see the world and still isn't sure it was a good idea"),
	ROSIE("Rosie", Archetype.FARMHAND, true, "she/her",
			"a sunny farm girl in a flower crown, off on her first real adventure, who can calm any animal and bakes bread whenever she's nervous"),
	LUNA("Luna", Archetype.MYSTIC, true, "she/her",
			"a silver-haired mystic in a star-stitched travelling cape, who reads omens in the night sky and is drawn to the End"),
	CASPER("Casper", Archetype.MYSTIC, false, "he/him",
			"a quiet rune-mage in a long coat stitched with glowing runes, who speaks rarely and notices everything");

	/** Skin variants from this value up are cast skins; below are the 18 default player skins. */
	public static final int SKIN_OFFSET = 100;

	private final String displayName;
	private final Archetype archetype;
	private final boolean slim;
	private final String pronouns;
	private final String background;

	CastMember(String displayName, Archetype archetype, boolean slim, String pronouns, String background) {
		this.displayName = displayName;
		this.archetype = archetype;
		this.slim = slim;
		this.pronouns = pronouns;
		this.background = background;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String displayName() {
		return displayName;
	}

	public Archetype archetype() {
		return archetype;
	}

	/** Slim (3 px) arms instead of wide (4 px). */
	public boolean slim() {
		return slim;
	}

	public String pronouns() {
		return pronouns;
	}

	public String background() {
		return background;
	}

	/** Encoded skin variant for this character (see CompanionSkin). */
	public int skinVariant() {
		return SKIN_OFFSET + ordinal();
	}

	public static Optional<CastMember> byName(String name) {
		if (name == null) {
			return Optional.empty();
		}
		String key = name.trim().toLowerCase(Locale.ROOT);
		for (CastMember member : values()) {
			if (member.id().equals(key)) {
				return Optional.of(member);
			}
		}
		return Optional.empty();
	}

	public static Optional<CastMember> bySkinVariant(int variant) {
		int index = variant - SKIN_OFFSET;
		return index >= 0 && index < values().length ? Optional.of(values()[index]) : Optional.empty();
	}
}

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
	HILDE("Hilde", Archetype.SELLSWORD, false, "she/her",
			"a northern shieldmaiden who left her clan to see the world; blunt, fearless and fiercely loyal"),
	WREN("Wren", Archetype.NATURALIST, true, "she/her",
			"a scholar-ranger who sketches every creature, flower and stone she meets in her battered notebooks"),
	TOBIAS("Tobias", Archetype.NATURALIST, false, "he/him",
			"a soft-spoken herbalist with round spectacles who knows the name and use of every root and leaf"),
	CASS("Cass", Archetype.ROGUE, true, "she/her",
			"a sharp-tongued treasure hunter who has looted more old tombs than she will ever admit"),
	MARLO("Marlo", Archetype.ROGUE, false, "he/him",
			"a silver-tongued thief with a weakness for shiny things and lost causes"),
	ISOLDE("Isolde", Archetype.GUARDIAN, false, "she/her",
			"a paladin of the Dawn who swore an oath to guard travellers on the wild roads"),
	ALDRIC("Aldric", Archetype.GUARDIAN, false, "he/him",
			"a grey-bearded knight-protector who has buried too many friends and refuses to lose another"),
	PIP("Pip", Archetype.FARMHAND, false, "he/him",
			"a farm lad who ran off to see the world and still isn't sure it was a good idea"),
	TILLY("Tilly", Archetype.FARMHAND, true, "she/her",
			"a cheerful farm girl who can calm any animal and bakes bread whenever she's nervous"),
	SABLE("Sable", Archetype.MYSTIC, true, "she/her",
			"a wandering mystic in violet robes who reads omens in the stars and is drawn to the End"),
	ASHE("Ashe", Archetype.MYSTIC, false, "he/him",
			"a hooded seer marked with glowing runes, who speaks rarely and notices everything");

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

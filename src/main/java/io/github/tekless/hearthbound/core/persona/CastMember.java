package io.github.tekless.hearthbound.core.persona;

import java.util.Locale;
import java.util.Optional;

/**
 * The named Hearthbound cast: twelve original characters, each with a voice (their archetype), a role (what they are good at), their own
 * skin (drawn by {@code tools/skins/cast.py}), arm width, pronouns and a short background that
 * keeps their look, voice and story consistent. Companions can also be generic wanderers using
 * the game's default skins.
 */
public enum CastMember {
	CHARLIE("Charlie", "Bram", Role.GUARDIAN, Archetype.SELLSWORD, false, "he/him",
			"a scarred, dry-witted veteran sellsword from the southern marches who has outlived three mercenary companies, "
					+ "and never goes anywhere without a clay pipe and a pouch of whatever herbs he found that morning"),
	DASHA("Dasha", "Freya", Role.GUARDIAN, Archetype.GUARDIAN, true, "she/her",
			"a golden-haired shieldmaiden from the north who left her clan to see the world; blunt, fearless and fiercely loyal"),
	HAZEL("Hazel", null, Role.COLLECTOR, Archetype.NATURALIST, true, "she/her",
			"an auburn-haired scholar-ranger who sketches every creature, flower and stone she meets in her battered notebooks"),
	BIRINTAN("Birintan", "Emmett", Role.BUILDER, Archetype.NATURALIST, false, "he/him",
			"a curious, quick-minded herbalist and tinkerer with a sprig of herbs behind his ear, who works out how a thing is put together before he builds one"),
	MADDIE("Maddie", "Cass", Role.BUILDER, Archetype.ROGUE, true, "she/her",
			"a sharp-tongued builder-for-hire in a crimson bandana who has patched more roofs and old tomb walls than she will ever admit"),
	AJ("AJ", "Lucas", Role.GUARDIAN, Archetype.ROGUE, false, "he/him",
			"the funny one: a cheerful chancer who keeps trying his luck with anyone who will talk to him and has never once got anywhere, "
					+ "and takes it on the chin every time"),
	DAISY("Daisy", null, Role.COLLECTOR, Archetype.GUARDIAN, true, "she/her",
			"a steady woodland warden with a gold circlet and a long braid, who keeps travellers supplied on the wild roads and guards everything she gathers"),
	COLE("Cole", "Conoby", Role.GUARDIAN, Archetype.ROGUE, false, "he/him",
			"a young blond adventurer with a devilish grin and a rapier, cheeky and sharper than he lets on, who talks his way into trouble and out again"),
	SAMUEL("Samuel", null, Role.FARMER, Archetype.FARMHAND, false, "he/him",
			"a farm lad who ran off to see the world and still isn't sure it was a good idea"),
	HONEY("Honey", "Rosie", Role.FARMER, Archetype.FARMHAND, true, "she/her",
			"a sunny farm girl in a flower crown, off on her first real adventure, who can calm any animal and bakes bread whenever she's nervous"),
	LUNA("Luna", null, Role.COLLECTOR, Archetype.MYSTIC, true, "she/her",
			"a silver-haired mystic in a star-stitched travelling cape, who reads omens in the night sky, scouts for what it promises, and is drawn to the End"),
	LUKE("Luke", "Casper", Role.BUILDER, Archetype.MYSTIC, false, "he/him",
			"a young sorcerer who shapes stone and timber the way other people cast spells, speaks rarely and notices everything");

	/** Skin variants from this value up are cast skins; below are the 18 default player skins. */
	public static final int SKIN_OFFSET = 100;

	private final String displayName;
	/** The name this character had before 0.4, so old saves and config files still find them. */
	private final String formerName;
	private final Role role;
	private final Archetype archetype;
	private final boolean slim;
	private final String pronouns;
	private final String background;

	CastMember(String displayName, String formerName, Role role, Archetype archetype, boolean slim, String pronouns, String background) {
		this.displayName = displayName;
		this.formerName = formerName;
		this.role = role;
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

	/** How they talk. */
	public Archetype archetype() {
		return archetype;
	}

	/** What they are good at. */
	public Role role() {
		return role;
	}

	/** The name used before the 0.4 cast changes, or null if they have not been renamed. */
	public String formerName() {
		return formerName;
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
			if (member.id().equals(key) || (member.formerName != null && member.formerName.toLowerCase(Locale.ROOT).equals(key))) {
				return Optional.of(member);
			}
		}
		return Optional.empty();
	}

	/** True if {@code name} is this character's current name or the one they had before 0.4. */
	public boolean answersTo(String name) {
		return name != null && (displayName.equalsIgnoreCase(name.trim()) || (formerName != null && formerName.equalsIgnoreCase(name.trim())));
	}

	public static Optional<CastMember> bySkinVariant(int variant) {
		int index = variant - SKIN_OFFSET;
		return index >= 0 && index < values().length ? Optional.of(values()[index]) : Optional.empty();
	}
}

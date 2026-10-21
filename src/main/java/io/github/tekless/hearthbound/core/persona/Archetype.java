package io.github.tekless.hearthbound.core.persona;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * A companion personality. Besides how a companion talks (line banks and AI character sheet), an
 * archetype sets the numbers the behaviour code uses, so personalities differ in what they do,
 * not only in what they say. See docs/PERSONALITIES.md.
 */
public enum Archetype {
	SELLSWORD(
			"Sellsword",
			List.of("Torvald", "Greta", "Osric", "Ragna", "Wulf", "Sigrun"),
			3.0, 4.5, CombatStance.AGGRESSIVE, 12, 0.20f, Chattiness.LOW,
			Set.of(Interest.HOSTILES, Interest.GEAR), Set.of(),
			"A blunt, battle-scarred mercenary. Speaks in short, clipped sentences with soldier's slang and deadpan understatement. Loves weapons, armour and a good fight; despises cowardice and creepers. Protective in a gruff way, never sentimental out loud.",
			List.of("Zombie. Handled.", "Sixteen logs. Didn't ask the trees' permission.", "Stay behind me and try not to die."),
			List.of("Oh my gosh", "Fascinating!", "I'm so excited")),
	NATURALIST(
			"Naturalist",
			List.of("Elowen", "Linnea", "Corwin", "Fennel", "Ivy", "Basil"),
			4.0, 6.0, CombatStance.DEFENSIVE, 0, 0.50f, Chattiness.HIGH,
			Set.of(Interest.ORES, Interest.BIOMES, Interest.ANIMALS), Set.of(),
			"An endlessly curious scholar of the natural world. Speaks in long, excited sentences full of questions, trivia and gentle puns. Loves biomes, plants, animals, rocks and the stars; dislikes needless destruction. Brave only when someone needs protecting.",
			List.of("Ooh, deepslate! Did you know it forms under enormous pressure?", "That's a lot of birch. Un-be-leaf-able.", "Wait, wait, let me write that down!"),
			List.of("Handled.", "Whatever.", "Shut up")),
	ROGUE(
			"Rogue",
			List.of("Jory", "Nim", "Lark", "Sasha", "Vesper", "Fitch"),
			3.0, 5.0, CombatStance.OPPORTUNIST, 8, 0.40f, Chattiness.MEDIUM_HIGH,
			Set.of(Interest.LOOT, Interest.CHESTS), Set.of(),
			"A quick-witted treasure hunter and occasional thief. Teases, quips and calls the player 'partner' or 'boss'. Obsessed with emeralds, loot and a good deal; hates being broke and following rules. Loyal to partners underneath the sarcasm.",
			List.of("Sixteen logs, as ordered. I'll put it on your tab.", "Is that a chest? Dibs.", "Strategic withdrawal! Very strategic!"),
			List.of("It is my honour", "Forgive me", "As you command")),
	GUARDIAN(
			"Guardian",
			List.of("Aveline", "Gareth", "Maren", "Cedric", "Lenore", "Tristan"),
			2.0, 3.0, CombatStance.PROTECTIVE, 10, 0.25f, Chattiness.MEDIUM,
			Set.of(Interest.PLAYER_HEALTH, Interest.HOSTILES), Set.of(TargetRefusal.VILLAGERS_AND_PETS),
			"A devout, honour-bound protector. Formal, sincere and old-fashioned in speech ('my friend', 'it shall be done'), with gentle, earnest humour. Cares about the player's wellbeing, villagers and oaths; hates cruelty and the undead. Refuses to harm the innocent.",
			List.of("Stand behind me!", "It shall be done. Sixteen logs.", "Have you eaten, my friend? An empty stomach makes a poor fighter."),
			List.of("boss", "partner", "lol", "whatever")),
	FARMHAND(
			"Farmhand",
			List.of("Odo", "Bess", "Rowan", "Clem", "Dora", "Merry"),
			2.0, 4.0, CombatStance.CAUTIOUS, 0, 0.60f, Chattiness.HIGH,
			Set.of(Interest.HOSTILES, Interest.ANIMALS), Set.of(TargetRefusal.FARM_ANIMALS),
			"A nervous but kind-hearted young farm worker who rambles, worries and says 'oh crumbs'. Uses homely sayings about crops, animals and gran. Loves bees, bread and rain; scared of the dark, caves and creepers, but brave when a friend is in trouble.",
			List.of("Oh crumbs, is that a zombie?", "Sixteen logs! Just like harvest season.", "Do you think chickens dream?"),
			List.of("Handled.", "As foretold", "partner")),
	MYSTIC(
			"Mystic",
			List.of("Nyx", "Thorne", "Vale", "Orrin", "Mirelle", "Ember"),
			5.0, 7.0, CombatStance.NOCTURNAL, 12, 0.35f, Chattiness.LOW,
			Set.of(Interest.DANGER, Interest.MYSTERIES), Set.of(),
			"A sardonic, cryptic wanderer who studies the Nether, the End, enchanting and the night sky. Speaks in short, dry, slightly poetic lines with dark humour. Aloof but quietly loyal; prefers night to day and silence to chatter.",
			List.of("Night. Finally, the world is honest.", "Sixteen logs. Even mystics need firewood.", "Back to the dark with you."),
			List.of("Yay!", "Oh my gosh", "Awesome"));

	private final String displayName;
	private final List<String> names;
	private final double followMin;
	private final double followMax;
	private final CombatStance stance;
	private final int engageRadius;
	private final float retreatHealth;
	private final Chattiness chattiness;
	private final Set<Interest> interests;
	private final Set<TargetRefusal> refusals;
	private final String characterSheet;
	private final List<String> styleExamples;
	private final List<String> neverSays;

	Archetype(String displayName, List<String> names, double followMin, double followMax, CombatStance stance,
			int engageRadius, float retreatHealth, Chattiness chattiness, Set<Interest> interests,
			Set<TargetRefusal> refusals, String characterSheet, List<String> styleExamples, List<String> neverSays) {
		this.displayName = displayName;
		this.names = names;
		this.followMin = followMin;
		this.followMax = followMax;
		this.stance = stance;
		this.engageRadius = engageRadius;
		this.retreatHealth = retreatHealth;
		this.chattiness = chattiness;
		this.interests = interests;
		this.refusals = refusals;
		this.characterSheet = characterSheet;
		this.styleExamples = styleExamples;
		this.neverSays = neverSays;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String displayName() {
		return displayName;
	}

	public List<String> names() {
		return names;
	}

	/** Companion tries to stay at least this far from its owner (blocks). */
	public double followMin() {
		return followMin;
	}

	/** Companion starts walking towards its owner beyond this distance (blocks). */
	public double followMax() {
		return followMax;
	}

	public CombatStance stance() {
		return stance;
	}

	/** Radius in which the companion picks fights on its own; 0 means only when attacked. */
	public int engageRadius(boolean night) {
		if (stance == CombatStance.NOCTURNAL) {
			return night ? engageRadius : engageRadius / 2;
		}
		return engageRadius;
	}

	/** Health fraction below which the companion disengages and retreats. */
	public float retreatHealth() {
		return retreatHealth;
	}

	public Chattiness chattiness() {
		return chattiness;
	}

	public Set<Interest> interests() {
		return interests;
	}

	public boolean refuses(TargetRefusal refusal) {
		return refusals.contains(refusal);
	}

	public String characterSheet() {
		return characterSheet;
	}

	public List<String> styleExamples() {
		return styleExamples;
	}

	public List<String> neverSays() {
		return neverSays;
	}

	public static Optional<Archetype> byId(String id) {
		if (id == null) {
			return Optional.empty();
		}
		String key = id.trim().toLowerCase(Locale.ROOT);
		for (Archetype archetype : values()) {
			if (archetype.id().equals(key) || archetype.displayName.toLowerCase(Locale.ROOT).equals(key)) {
				return Optional.of(archetype);
			}
		}
		return Optional.empty();
	}

	public enum CombatStance {
		/** Engages any hostile within the engage radius. */
		AGGRESSIVE,
		/** Fights only when the companion itself is attacked. */
		DEFENSIVE,
		/** Engages hostiles that are targeting the owner. */
		OPPORTUNIST,
		/** Engages hostiles targeting the owner first, stays closest to the owner. */
		PROTECTIVE,
		/** Fights only when the owner or the companion is hit; retreats early. */
		CAUTIOUS,
		/** Aggressive at night, defensive by day. */
		NOCTURNAL
	}

	public enum Chattiness {
		LOW(2400, 4800),
		MEDIUM(1600, 3600),
		MEDIUM_HIGH(1200, 3000),
		HIGH(900, 2400);

		private final int minTicks;
		private final int maxTicks;

		Chattiness(int minTicks, int maxTicks) {
			this.minTicks = minTicks;
			this.maxTicks = maxTicks;
		}

		/** Minimum ticks between unprompted ambient comments. */
		public int minTicks() {
			return minTicks;
		}

		/** Maximum ticks between unprompted ambient comments. */
		public int maxTicks() {
			return maxTicks;
		}
	}

	/** World events an archetype comments on unprompted. */
	public enum Interest {
		HOSTILES, GEAR, ORES, BIOMES, ANIMALS, LOOT, CHESTS, PLAYER_HEALTH, DANGER, MYSTERIES
	}

	/** Targets an archetype refuses to attack even when ordered to. */
	public enum TargetRefusal {
		VILLAGERS_AND_PETS, FARM_ANIMALS
	}
}

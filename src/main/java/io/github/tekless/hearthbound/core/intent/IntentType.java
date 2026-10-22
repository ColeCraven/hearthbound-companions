package io.github.tekless.hearthbound.core.intent;

import java.util.Locale;
import java.util.Optional;

/** Everything a companion can be told to do. Anything else is either talk or unsupported. */
public enum IntentType {
	FOLLOW,
	WAIT,
	COME,
	GATHER,
	CANCEL,
	GIVE,
	ATTACK,
	STATUS,
	INVENTORY,
	RECRUIT,
	DISMISS,
	/** Go back to their own home; the target is "walk" or "teleport". Nobody is dismissed. */
	HOME,
	/** Build a simple structure; the item names it ("shelter", "wall", "bridge"). */
	BUILD,
	/** Dig: the target is "tunnel", "down" or "trip" (down then along); the count is how far. */
	MINE,
	/** Put things from the pack into the chest the player is looking at; the item says what (null: everything). */
	STORE,
	/** Take things out of the chest the player is looking at into the pack; the item says what (null: anything). */
	TAKE,
	/** Smelt something from the pack in a furnace; the item names it ("iron"), the count how many. */
	SMELT,
	/** Craft something with the game's recipes from what's in the pack; the item names it ("a pickaxe"). */
	CRAFT;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Optional<IntentType> byId(String id) {
		if (id == null) {
			return Optional.empty();
		}
		String key = id.trim().toUpperCase(Locale.ROOT);
		if (key.equals("STAY")) {
			key = "WAIT";
		} else if (key.equals("STOP")) {
			key = "CANCEL";
		}
		for (IntentType type : values()) {
			if (type.name().equals(key)) {
				return Optional.of(type);
			}
		}
		return Optional.empty();
	}
}

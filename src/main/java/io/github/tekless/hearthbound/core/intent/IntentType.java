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
	DISMISS;

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

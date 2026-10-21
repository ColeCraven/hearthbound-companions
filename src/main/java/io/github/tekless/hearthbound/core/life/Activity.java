package io.github.tekless.hearthbound.core.life;

import java.util.Locale;

/** What a companion who lives somewhere is doing with this part of the day. */
public enum Activity {
	SLEEP("asleep"),
	WAKE("getting up"),
	WORK("working"),
	MEAL("having a meal"),
	LEISURE("taking it easy"),
	SOCIAL("with the others at the meeting spot"),
	SHELTER("keeping out of the rain");

	private final String description;

	Activity(String description) {
		this.description = description;
	}

	/** Short phrase for status replies and the party list, e.g. "having a meal". */
	public String describe() {
		return description;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}

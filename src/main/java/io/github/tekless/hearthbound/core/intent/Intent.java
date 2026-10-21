package io.github.tekless.hearthbound.core.intent;

import java.util.Objects;

/**
 * A structured request for a companion. Produced by the rule-based {@link CommandParser}, by
 * buttons, by commands, or proposed by an AI model; always checked by the game before it runs.
 *
 * @param type   what to do
 * @param item   raw item phrase for GATHER/GIVE ("oak logs"), or null
 * @param count  requested amount, or null when unspecified
 * @param target raw target phrase for ATTACK ("zombie", "that"), or null
 */
public record Intent(IntentType type, String item, Integer count, String target) {
	public Intent {
		Objects.requireNonNull(type, "type");
	}

	public static Intent of(IntentType type) {
		return new Intent(type, null, null, null);
	}

	public static Intent gather(String item, Integer count) {
		return new Intent(IntentType.GATHER, item, count, null);
	}

	public static Intent give(String item, Integer count) {
		return new Intent(IntentType.GIVE, item, count, null);
	}

	public static Intent attack(String target) {
		return new Intent(IntentType.ATTACK, null, null, target);
	}

	public Intent withCount(Integer newCount) {
		return new Intent(type, item, newCount, target);
	}

	public Intent withItem(String newItem) {
		return new Intent(type, newItem, count, target);
	}
}

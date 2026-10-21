package io.github.tekless.hearthbound.core.intent;

/** What the rule-based parser made of a message. */
public sealed interface ParseResult {
	/** A clear request to do something. */
	record Command(Intent intent) implements ParseResult {
	}

	/** Small talk with a recognised topic. */
	record Talk(TalkTopic topic) implements ParseResult {
	}

	/** The player telling us about themselves ("call me Captain", "I love the ocean"). */
	record Share(FactKind kind, String value) implements ParseResult {
	}

	/** Looks like a request, but something essential is missing (e.g. "gather" without an item). */
	record Unclear(UnclearReason reason) implements ParseResult {
	}

	/** A request for something companions can't do at all ("build me a house"). */
	record Unsupported(String action) implements ParseResult {
	}

	/** Nothing recognised; free-form conversation. */
	record Unrecognised() implements ParseResult {
	}

	enum UnclearReason {
		GATHER_WHAT,
		GENERIC
	}
}

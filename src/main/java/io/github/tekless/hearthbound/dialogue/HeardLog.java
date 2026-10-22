package io.github.tekless.hearthbound.dialogue;

import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.ParseResult;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The last thing each companion heard, how it was read, who else it was for and what came of it, so
 * {@code /companion explain} can show why someone did (or didn't do) what they were asked. Kept in memory only.
 */
public final class HeardLog {
	/**
	 * @param reading how the parser read it ("order: gather, item logs, count 16")
	 * @param recipients who it was for ("Hazel" or "the party: Hazel, Charlie")
	 * @param result what came of it ("accepted (ack.gather)", "refused: craft.missing ...")
	 */
	public record Entry(String speaker, String text, long tick, String reading, String recipients, String result) {
		Entry withRecipients(String who) {
			return new Entry(speaker, text, tick, reading, who, result);
		}

		Entry withResult(String outcome) {
			return new Entry(speaker, text, tick, reading, recipients, outcome);
		}
	}

	private static final Map<UUID, Entry> LAST = new ConcurrentHashMap<>();

	private HeardLog() {
	}

	static void heard(CompanionEntity companion, String speaker, String text, ParseResult result) {
		LAST.put(companion.getUUID(), new Entry(speaker, text, companion.level().getGameTime(), reading(result), companion.companionName(), "no reply yet"));
	}

	static void recipients(CompanionEntity companion, String who) {
		LAST.computeIfPresent(companion.getUUID(), (id, entry) -> entry.withRecipients(who));
	}

	static void result(CompanionEntity companion, String outcome) {
		LAST.computeIfPresent(companion.getUUID(), (id, entry) -> entry.withResult(outcome));
	}

	static void result(CompanionEntity companion, Conversations.Outcome outcome) {
		if (outcome == null) {
			result(companion, "they asked you something back first (" + companion.lastLineKey() + ")");
			return;
		}
		String vars = outcome.vars() == null || outcome.vars().isEmpty() ? ""
				: " " + outcome.vars().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> e.getKey() + "=" + e.getValue())
						.collect(Collectors.joining(", ", "[", "]"));
		result(companion, (outcome.accepted() ? "accepted" : "refused") + (outcome.lineKey() == null ? "" : ": " + outcome.lineKey()) + vars);
	}

	public static Optional<Entry> last(CompanionEntity companion) {
		return Optional.ofNullable(LAST.get(companion.getUUID()));
	}

	public static void reset() {
		LAST.clear();
	}

	/** The parser's reading, in words. */
	static String reading(ParseResult result) {
		return switch (result) {
			case ParseResult.Command command -> "order: " + describe(command.intent());
			case ParseResult.Talk talk -> "talk (" + talk.topic().name().toLowerCase(java.util.Locale.ROOT) + ")";
			case ParseResult.Share share -> "telling them something about you";
			case ParseResult.Unclear unclear -> "unclear (" + unclear.reason().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + ")";
			case ParseResult.Unsupported unsupported -> "something they can't do (" + unsupported.action() + ")";
			case ParseResult.Unrecognised unrecognised -> "not an order or a question they know, so just chat";
		};
	}

	static String describe(Intent intent) {
		StringBuilder out = new StringBuilder(intent.type().id());
		if (intent.item() != null) {
			out.append(", item \"").append(intent.item()).append('"');
		}
		if (intent.count() != null) {
			out.append(", count ").append(intent.count());
		}
		if (intent.target() != null) {
			out.append(", target ").append(intent.target());
		}
		return out.toString();
	}
}

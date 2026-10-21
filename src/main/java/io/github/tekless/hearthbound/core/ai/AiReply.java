package io.github.tekless.hearthbound.core.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import java.util.Optional;

/**
 * A model's answer, parsed and sanity-checked. The model may suggest an intent, but nothing here
 * runs it: the game validates and executes (or refuses) it separately.
 *
 * @param reply  what the companion would say (already cleaned up)
 * @param intent proposed action, or empty for plain conversation
 */
public record AiReply(String reply, Optional<Intent> intent) {
	public static final int MAX_REPLY_CHARS = 280;
	public static final int MAX_COUNT = 256;

	/** The JSON schema every provider is asked to follow (flat, nullable fields, no min/max). */
	public static final String SCHEMA = """
			{
			  "type": "object",
			  "properties": {
			    "reply": {"type": "string", "description": "What you say out loud: 1-2 short sentences, in character."},
			    "intent": {"type": "string", "enum": ["none", "follow", "wait", "come", "gather", "cancel", "give", "attack", "status", "recruit", "dismiss", "build", "mine"],
			               "description": "An action you agree to start now, or none."},
			    "item": {"anyOf": [{"type": "string"}, {"type": "null"}], "description": "For gather/give: the item, e.g. oak logs, cobblestone, iron. For build: shelter, wall or bridge. Otherwise null."},
			    "count": {"anyOf": [{"type": "integer"}, {"type": "null"}], "description": "For gather/give: how many. For mine: how many blocks to dig. Otherwise null."},
			    "target": {"anyOf": [{"type": "string"}, {"type": "null"}], "description": "For attack: the creature, e.g. zombie. For mine: tunnel, down, or trip (down then along). Otherwise null."}
			  },
			  "required": ["reply", "intent", "item", "count", "target"],
			  "additionalProperties": false
			}""";

	/**
	 * Parses model output. Accepts a bare JSON object or one wrapped in a ```json fence. Throws
	 * {@link IllegalArgumentException} for anything unusable, so callers fall back to scripts.
	 */
	public static AiReply parse(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("empty reply");
		}
		String text = raw.trim();
		int start = text.indexOf('{');
		int end = text.lastIndexOf('}');
		if (start < 0 || end <= start) {
			throw new IllegalArgumentException("no JSON object in reply");
		}
		JsonObject json;
		try {
			json = JsonParser.parseString(text.substring(start, end + 1)).getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("malformed JSON", e);
		}
		String reply = clean(string(json, "reply"));
		if (reply.isEmpty()) {
			throw new IllegalArgumentException("reply text missing");
		}
		String intentName = string(json, "intent");
		Optional<Intent> intent = Optional.empty();
		if (!intentName.isEmpty() && !intentName.equalsIgnoreCase("none")) {
			IntentType type = IntentType.byId(intentName)
					.orElseThrow(() -> new IllegalArgumentException("unknown intent " + intentName));
			if (type == IntentType.INVENTORY) {
				throw new IllegalArgumentException("intent not allowed for the model: " + intentName);
			}
			String item = blankToNull(clean(string(json, "item")));
			Integer count = integer(json, "count");
			if (count != null) {
				count = Math.max(1, Math.min(MAX_COUNT, count));
			}
			String target = blankToNull(clean(string(json, "target")));
			intent = Optional.of(new Intent(type, item, count, target));
		}
		return new AiReply(reply, intent);
	}

	/** Strips formatting codes, control characters and markdown, and caps length. */
	public static String clean(String text) {
		if (text == null) {
			return "";
		}
		String cleaned = text
				.replaceAll("§.", "")
				.replaceAll("[\\p{Cntrl}&&[^\n]]", "")
				.replace('\n', ' ')
				.replaceAll("[*_`#]", "")
				.replaceAll("\\s+", " ")
				.trim();
		if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() > 1) {
			cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
		}
		if (cleaned.length() > MAX_REPLY_CHARS) {
			int cut = cleaned.lastIndexOf(' ', MAX_REPLY_CHARS);
			cleaned = cleaned.substring(0, cut > 200 ? cut : MAX_REPLY_CHARS) + "...";
		}
		return cleaned;
	}

	private static String string(JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull()) {
			return "";
		}
		return element.isJsonPrimitive() ? element.getAsString() : "";
	}

	private static Integer integer(JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
			return null;
		}
		try {
			return (int) Math.round(element.getAsDouble());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() || value.equalsIgnoreCase("null") ? null : value;
	}
}

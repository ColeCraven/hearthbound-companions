package io.github.tekless.hearthbound.core.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.tekless.hearthbound.core.persona.Archetype;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** All scripted lines for one archetype, keyed by situation ("ack.follow", "talk.joke", ...). */
public final class LineBank {
	private static final Map<Archetype, LineBank> CACHE = new EnumMap<>(Archetype.class);

	private final Archetype archetype;
	private final Map<String, List<String>> lines;

	LineBank(Archetype archetype, Map<String, List<String>> lines) {
		this.archetype = archetype;
		this.lines = lines;
	}

	/** Loads (once) the bank bundled at {@code data/hearthbound/dialogue/<archetype>.json}. */
	public static synchronized LineBank of(Archetype archetype) {
		return CACHE.computeIfAbsent(archetype, LineBank::load);
	}

	private static LineBank load(Archetype archetype) {
		String path = "/data/hearthbound/dialogue/" + archetype.id() + ".json";
		try (InputStream in = LineBank.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("Missing dialogue file " + path);
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return parse(archetype, reader);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}

	static LineBank parse(Archetype archetype, Reader reader) {
		JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
		JsonObject entries = root.getAsJsonObject("lines");
		Map<String, List<String>> lines = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
			List<String> variants = new ArrayList<>();
			for (JsonElement element : entry.getValue().getAsJsonArray()) {
				variants.add(element.getAsString());
			}
			lines.put(entry.getKey(), Collections.unmodifiableList(variants));
		}
		return new LineBank(archetype, Collections.unmodifiableMap(lines));
	}

	public Archetype archetype() {
		return archetype;
	}

	public List<String> get(String key) {
		return lines.getOrDefault(key, List.of());
	}

	public boolean has(String key) {
		return !get(key).isEmpty();
	}

	public Set<String> keys() {
		return lines.keySet();
	}
}

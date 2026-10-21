package io.github.tekless.hearthbound.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.tekless.hearthbound.Hearthbound;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Finds an API key without ever storing one in the mod's main config or repository. Order:
 * {@code HEARTHBOUND_API_KEY}, the provider's usual variable ({@code OPENAI_API_KEY} /
 * {@code ANTHROPIC_API_KEY}), then {@code config/hearthbound-secrets.json} {"apiKey": "..."}.
 * Keys are never logged or sent to clients.
 */
public final class Secrets {
	private Secrets() {
	}

	public static Optional<String> apiKey(String provider, Path configDir) {
		String key = System.getenv("HEARTHBOUND_API_KEY");
		if (isSet(key)) {
			return Optional.of(key.trim());
		}
		String providerVar = switch (provider) {
			case "openai" -> "OPENAI_API_KEY";
			case "anthropic" -> "ANTHROPIC_API_KEY";
			default -> null;
		};
		if (providerVar != null && isSet(System.getenv(providerVar))) {
			return Optional.of(System.getenv(providerVar).trim());
		}
		Path file = configDir.resolve("hearthbound-secrets.json");
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				if (json.has("apiKey") && isSet(json.get("apiKey").getAsString())) {
					return Optional.of(json.get("apiKey").getAsString().trim());
				}
			} catch (IOException | RuntimeException e) {
				Hearthbound.LOGGER.warn("Could not read {} (the key itself is not logged)", file.getFileName());
			}
		}
		return Optional.empty();
	}

	private static boolean isSet(String value) {
		return value != null && !value.isBlank() && !value.contains("PUT-YOUR-KEY-HERE");
	}
}

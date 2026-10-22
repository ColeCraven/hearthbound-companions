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

	/** Where the key for this provider would come from, in words (never the key itself), or empty if there isn't one. */
	public static Optional<String> source(String provider, Path configDir) {
		if (isSet(System.getenv("HEARTHBOUND_API_KEY"))) {
			return Optional.of("the HEARTHBOUND_API_KEY environment variable");
		}
		String providerVar = switch (provider) {
			case "openai" -> "OPENAI_API_KEY";
			case "anthropic" -> "ANTHROPIC_API_KEY";
			default -> null;
		};
		if (providerVar != null && isSet(System.getenv(providerVar))) {
			return Optional.of("the " + providerVar + " environment variable");
		}
		return apiKey(provider, configDir).map(key -> "config/hearthbound-secrets.json");
	}

	/** Makes an empty config/hearthbound-secrets.json to paste a key into, if there isn't one. Returns true if it made one. */
	public static boolean writeTemplate(Path configDir) {
		Path file = configDir.resolve("hearthbound-secrets.json");
		if (Files.exists(file)) {
			return false;
		}
		try {
			Files.createDirectories(configDir);
			Files.writeString(file, "{\n  \"apiKey\": \"PUT-YOUR-KEY-HERE\"\n}\n", StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			Hearthbound.LOGGER.warn("Could not create {}: {}", file.getFileName(), e.getMessage());
			return false;
		}
	}

	private static boolean isSet(String value) {
		return value != null && !value.isBlank() && !value.contains("PUT-YOUR-KEY-HERE");
	}
}

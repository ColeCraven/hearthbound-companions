package io.github.tekless.hearthbound.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.tekless.hearthbound.Hearthbound;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Settings from {@code config/hearthbound.json}. API keys are deliberately <b>not</b> stored here;
 * see {@link Secrets}.
 */
public final class HearthboundConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Companions are knocked down instead of dying, so you never lose one for good. */
	public boolean essentialCompanions = true;
	/** How many companions one player can recruit at once. */
	public int partyLimit = 3;
	/** 0 = never comment unprompted, 1 = normal, 2 = chatty. */
	public double ambientChatter = 1.0;
	/** How often companions do things of their own accord while you stand still: 0 = never, 1 = normal, 2 = often. */
	public double autonomy = 1.0;
	public Ai ai = new Ai();

	public static final class Ai {
		/** none | ollama | openai | anthropic */
		public String provider = "none";
		/** Model name for the chosen provider, e.g. "qwen3.5:9b" or "claude-opus-5-5". */
		public String model = "";
		/** Override the provider's default endpoint (e.g. for OpenRouter, Groq, LM Studio). */
		public String baseUrl = "";
		public int timeoutSeconds = 12;
		/** Send your in-game name to the model; when false it sees "the player". */
		public boolean sharePlayerName = true;
		/** Allow Ollama ":cloud" models, which forward prompts to ollama.com. */
		public boolean allowCloudModels = false;
		/** Anthropic only: effort level for models that support it (low keeps replies quick). */
		public String effort = "low";
	}

	public boolean essentialCompanions() {
		return essentialCompanions;
	}

	public int partyLimit() {
		return Math.max(1, partyLimit);
	}

	public static HearthboundConfig load(Path file) {
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				HearthboundConfig config = GSON.fromJson(reader, HearthboundConfig.class);
				if (config != null) {
					if (config.ai == null) {
						config.ai = new Ai();
					}
					return config;
				}
			} catch (IOException | JsonParseException e) {
				Hearthbound.LOGGER.warn("Could not read {}, using defaults: {}", file, e.getMessage());
				return new HearthboundConfig();
			}
		}
		HearthboundConfig defaults = new HearthboundConfig();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(defaults, writer);
			}
		} catch (IOException e) {
			Hearthbound.LOGGER.warn("Could not write default config {}: {}", file, e.getMessage());
		}
		return defaults;
	}
}

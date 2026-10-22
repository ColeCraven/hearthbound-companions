package io.github.tekless.hearthbound.ai;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.config.HearthboundConfig;
import io.github.tekless.hearthbound.config.Secrets;
import io.github.tekless.hearthbound.core.ai.HttpModels;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/**
 * One-click AI setup behind {@code /companion ai}: find the models a local Ollama has and switch to one, or switch to
 * OpenAI or Anthropic once a key is in place (an environment variable or config/hearthbound-secrets.json, never typed
 * into chat), and check it with one test reply. If the test fails, the old settings come back. Keys are never shown,
 * logged or sent to anyone.
 */
public final class AiSetup {
	public static final String OLLAMA = "http://127.0.0.1:11434";

	private AiSetup() {
	}

	private static Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	/** {@code /companion ai status}: what's on, which model, and where the key comes from. */
	public static List<String> status() {
		HearthboundConfig.Ai ai = Hearthbound.config().ai;
		String provider = provider(ai);
		List<String> out = new ArrayList<>();
		AiService service = Hearthbound.ai();
		if (provider.equals("none")) {
			out.add("AI replies are off. Companions use their own lines. Turn it on with /companion ai setup.");
			return out;
		}
		out.add("AI replies: " + (service != null ? "on, " + service.describe() : "set to " + provider + " but not running (see the log, or run /companion ai setup " + provider + ")"));
		out.add("Address: " + (ai.baseUrl.isBlank() ? (provider.equals("ollama") ? OLLAMA : "the provider's usual one") : ai.baseUrl));
		if (!provider.equals("ollama")) {
			out.add("Key: " + Secrets.source(provider, configDir()).map(where -> "found, from " + where).orElse("none found"));
		}
		return out;
	}

	/** {@code /companion ai off}. */
	public static List<String> off(MinecraftServer server) {
		HearthboundConfig config = Hearthbound.config();
		config.ai.provider = "none";
		config.save();
		Hearthbound.restartAi(server);
		return List.of("AI replies are off. Companions use their own lines again. /companion ai setup turns it back on.");
	}

	/** What a local Ollama has: model names, or why it couldn't be asked. */
	public record Found(List<String> models, Optional<String> problem) {
	}

	/** Step one for Ollama: ask it which models are pulled. */
	public static CompletableFuture<Found> findOllamaModels() {
		String base = ollamaAddress();
		return HttpModels.ollamaModels(base).handle((models, error) -> error == null
				? new Found(models, models.isEmpty() ? Optional.of("Ollama is running at " + base + " but has no models yet. Pull one first, e.g. ollama pull qwen3.5:9b, then run /companion ai setup again.") : Optional.empty())
				: new Found(List.of(), Optional.of("Ollama isn't answering at " + base + ". Install it from ollama.com and start it, pull a model (ollama pull qwen3.5:9b), then run /companion ai setup again.")));
	}

	private static String ollamaAddress() {
		HearthboundConfig.Ai ai = Hearthbound.config().ai;
		return provider(ai).equals("ollama") && !ai.baseUrl.isBlank() ? ai.baseUrl : OLLAMA;
	}

	/** Step two for Ollama: use this model, then check it answers. */
	public static CompletableFuture<List<String>> useOllama(MinecraftServer server, String model) {
		return enableAndTest(server, "ollama", model.trim(), provider(Hearthbound.config().ai).equals("ollama") ? Hearthbound.config().ai.baseUrl : "");
	}

	/**
	 * {@code /companion ai setup openai|anthropic [model]}: with a key in place, switch over and test; without one, say
	 * where it goes (and make an empty secrets file to paste it into).
	 *
	 * @return the lines to show now, and the test to wait for if one was started
	 */
	public static Cloud cloud(MinecraftServer server, String provider, String model) {
		HearthboundConfig.Ai ai = Hearthbound.config().ai;
		String name = model == null || model.isBlank() ? (provider(ai).equals(provider) ? ai.model : "") : model.trim();
		if (name.isBlank() && provider.equals("anthropic")) {
			name = "claude-opus-5-5";
		}
		boolean local = provider.equals("openai") && (ai.baseUrl.contains("localhost") || ai.baseUrl.contains("127.0.0.1"));
		if (Secrets.source(provider, configDir()).isEmpty() && !local) {
			boolean made = Secrets.writeTemplate(configDir());
			String variable = provider.equals("openai") ? "OPENAI_API_KEY" : "ANTHROPIC_API_KEY";
			List<String> out = new ArrayList<>();
			out.add("I can't find a key for " + label(provider) + ". Don't type it in chat. Put it in one of these:");
			out.add(" - the " + variable + " (or HEARTHBOUND_API_KEY) environment variable, set before the game or server starts");
			out.add(" - config/hearthbound-secrets.json as {\"apiKey\": \"your key\"}" + (made ? " (I've made the file, just paste the key in)" : ""));
			out.add("Then run /companion ai setup " + provider + (name.isBlank() ? " <model>" : " " + name) + " again. Nothing has changed yet.");
			return new Cloud(out, null);
		}
		if (name.isBlank()) {
			return new Cloud(List.of("Which model? Run /companion ai setup openai <model>, with the model's name as your provider lists it."), null);
		}
		return new Cloud(List.of("Key found. Switching to " + label(provider) + " " + name + " and asking for one test reply..."),
				enableAndTest(server, provider, name, local ? ai.baseUrl : provider(ai).equals(provider) ? ai.baseUrl : ""));
	}

	/** Lines to show at once, and maybe a test still running. */
	public record Cloud(List<String> now, CompletableFuture<List<String>> test) {
	}

	/** Switches the config over, starts the service, and asks for one reply. On failure the old settings come back. */
	private static CompletableFuture<List<String>> enableAndTest(MinecraftServer server, String provider, String model, String baseUrl) {
		HearthboundConfig config = Hearthbound.config();
		String oldProvider = config.ai.provider;
		String oldModel = config.ai.model;
		String oldBase = config.ai.baseUrl;
		config.ai.provider = provider;
		config.ai.model = model;
		config.ai.baseUrl = baseUrl == null ? "" : baseUrl;
		AiService service = Hearthbound.restartAi(server);
		if (service == null) {
			restore(server, oldProvider, oldModel, oldBase);
			return CompletableFuture.completedFuture(List.of("That didn't start (the log says why). AI settings are as they were."));
		}
		long started = System.currentTimeMillis();
		return service.testReply().handle((reply, error) -> {
			if (error == null) {
				server.execute(config::save);
				return List.of("Test reply from " + service.describe() + " in " + (System.currentTimeMillis() - started) / 100 / 10.0 + " s: \"" + reply + "\"",
						"AI replies are on. /companion ai status shows the settings, /companion ai off turns them off.");
			}
			server.execute(() -> restore(server, oldProvider, oldModel, oldBase));
			return List.of("The test reply didn't come back: " + clean(error, provider) + ". AI settings are as they were.");
		});
	}

	private static void restore(MinecraftServer server, String provider, String model, String baseUrl) {
		HearthboundConfig config = Hearthbound.config();
		config.ai.provider = provider;
		config.ai.model = model;
		config.ai.baseUrl = baseUrl;
		Hearthbound.restartAi(server);
	}

	/** The reason, short, with the key blanked out in case a provider ever echoes it back. */
	private static String clean(Throwable error, String provider) {
		Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
		String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
		if (cause instanceof java.util.concurrent.TimeoutException || message.contains("timed out")) {
			message = "it took too long";
		}
		Optional<String> key = Secrets.apiKey(provider, configDir());
		if (key.isPresent() && key.get().length() > 4) {
			message = message.replace(key.get(), "****");
		}
		return message.length() > 160 ? message.substring(0, 160) : message;
	}

	private static String provider(HearthboundConfig.Ai ai) {
		return ai.provider == null || ai.provider.isBlank() ? "none" : ai.provider.trim().toLowerCase(Locale.ROOT);
	}

	private static String label(String provider) {
		return switch (provider) {
			case "openai" -> "OpenAI";
			case "anthropic" -> "Anthropic";
			default -> "Ollama";
		};
	}
}

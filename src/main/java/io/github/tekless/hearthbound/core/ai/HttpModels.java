package io.github.tekless.hearthbound.core.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Minimal clients for the three supported APIs, using only the JDK's HTTP client and Gson (which
 * Minecraft already ships). All calls are asynchronous with connect and request timeouts.
 */
public final class HttpModels {
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(5))
			.executor(Executors.newFixedThreadPool(2, runnable -> {
				Thread thread = new Thread(runnable, "Hearthbound-AI");
				thread.setDaemon(true);
				return thread;
			}))
			.build();

	private HttpModels() {
	}

	public static LanguageModel ollama(String baseUrl, String model, int timeoutSeconds) {
		String url = (baseUrl.isBlank() ? "http://127.0.0.1:11434" : stripSlash(baseUrl)) + "/api/chat";
		return new LanguageModel() {
			@Override
			public CompletableFuture<String> complete(PromptBuilder.Prompt prompt) {
				JsonObject body = new JsonObject();
				body.addProperty("model", model);
				body.add("messages", messages(prompt, true));
				body.addProperty("stream", false);
				body.add("format", schema());
				body.addProperty("think", false);
				body.addProperty("keep_alive", "15m");
				JsonObject options = new JsonObject();
				options.addProperty("temperature", 0.8);
				options.addProperty("num_ctx", 8192);
				options.addProperty("num_predict", 300);
				body.add("options", options);
				return post(url, body, timeoutSeconds, null).thenApply(json -> {
					JsonObject message = json.getAsJsonObject("message");
					if (message == null || !message.has("content")) {
						throw new IllegalStateException("Ollama reply had no message");
					}
					return message.get("content").getAsString();
				});
			}

			@Override
			public String describe() {
				return "Ollama " + model;
			}
		};
	}

	public static LanguageModel openAiCompatible(String baseUrl, String model, String apiKey, int timeoutSeconds) {
		String url = (baseUrl.isBlank() ? "https://api.openai.com/v1" : stripSlash(baseUrl)) + "/chat/completions";
		return new LanguageModel() {
			@Override
			public CompletableFuture<String> complete(PromptBuilder.Prompt prompt) {
				JsonObject body = new JsonObject();
				body.addProperty("model", model);
				body.add("messages", messages(prompt, true));
				JsonObject format = new JsonObject();
				format.addProperty("type", "json_schema");
				JsonObject jsonSchema = new JsonObject();
				jsonSchema.addProperty("name", "companion_turn");
				jsonSchema.addProperty("strict", true);
				jsonSchema.add("schema", schema());
				format.add("json_schema", jsonSchema);
				body.add("response_format", format);
				return post(url, body, timeoutSeconds, apiKey == null ? null : new String[] {"Authorization", "Bearer " + apiKey}).thenApply(json -> {
					JsonArray choices = json.getAsJsonArray("choices");
					if (choices == null || choices.isEmpty()) {
						throw new IllegalStateException("no choices in reply");
					}
					JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
					if (message.has("refusal") && !message.get("refusal").isJsonNull()) {
						throw new IllegalStateException("model refused");
					}
					return message.get("content").getAsString();
				});
			}

			@Override
			public String describe() {
				return "OpenAI-compatible " + model;
			}
		};
	}

	public static LanguageModel anthropic(String baseUrl, String model, String apiKey, String effort, int timeoutSeconds) {
		String url = (baseUrl.isBlank() ? "https://api.anthropic.com" : stripSlash(baseUrl)) + "/v1/messages";
		return new LanguageModel() {
			@Override
			public CompletableFuture<String> complete(PromptBuilder.Prompt prompt) {
				JsonObject body = new JsonObject();
				body.addProperty("model", model);
				body.addProperty("max_tokens", 2048);
				body.addProperty("system", prompt.system());
				body.add("messages", messages(prompt, false));
				JsonObject outputConfig = new JsonObject();
				JsonObject format = new JsonObject();
				format.addProperty("type", "json_schema");
				format.add("schema", schema());
				outputConfig.add("format", format);
				if (effort != null && !effort.isBlank()) {
					outputConfig.addProperty("effort", effort);
				}
				body.add("output_config", outputConfig);
				String[] headers = {"x-api-key", apiKey, "anthropic-version", "2023-06-01"};
				return post(url, body, timeoutSeconds, headers).thenApply(json -> {
					String stop = json.has("stop_reason") && !json.get("stop_reason").isJsonNull() ? json.get("stop_reason").getAsString() : "";
					if (stop.equals("refusal") || stop.equals("max_tokens")) {
						throw new IllegalStateException("stopped: " + stop);
					}
					for (JsonElement block : json.getAsJsonArray("content")) {
						JsonObject content = block.getAsJsonObject();
						if ("text".equals(content.get("type").getAsString())) {
							return content.get("text").getAsString();
						}
					}
					throw new IllegalStateException("no text block in reply");
				});
			}

			@Override
			public String describe() {
				return "Anthropic " + model;
			}
		};
	}

	private static JsonArray messages(PromptBuilder.Prompt prompt, boolean includeSystem) {
		JsonArray messages = new JsonArray();
		if (includeSystem) {
			messages.add(message("system", prompt.system()));
		}
		messages.add(message("user", prompt.user()));
		return messages;
	}

	private static JsonObject message(String role, String content) {
		JsonObject message = new JsonObject();
		message.addProperty("role", role);
		message.addProperty("content", content);
		return message;
	}

	static JsonObject schema() {
		return JsonParser.parseString(AiReply.SCHEMA).getAsJsonObject();
	}

	private static CompletableFuture<JsonObject> post(String url, JsonObject body, int timeoutSeconds, String[] headers) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(timeoutSeconds))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
		if (headers != null) {
			for (int i = 0; i + 1 < headers.length; i += 2) {
				request.header(headers[i], headers[i + 1]);
			}
		}
		return CLIENT.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString())
				.orTimeout(timeoutSeconds + 2L, TimeUnit.SECONDS)
				.thenApply(response -> {
					if (response.statusCode() / 100 != 2) {
						String snippet = response.body() == null ? "" : response.body().replaceAll("\\s+", " ");
						throw new IllegalStateException("HTTP " + response.statusCode() + ": "
								+ (snippet.length() > 200 ? snippet.substring(0, 200) : snippet));
					}
					return JsonParser.parseString(response.body()).getAsJsonObject();
				});
	}

	private static String stripSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}

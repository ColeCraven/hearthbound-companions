package io.github.tekless.hearthbound.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Each provider client against a local fake server: request shape, response parsing, failures. */
class HttpModelsTest {
	private static final PromptBuilder.Prompt PROMPT = new PromptBuilder.Prompt("system text", "user text");
	private static final String TURN = "{\"reply\":\"Hello!\",\"intent\":\"none\",\"item\":null,\"count\":null,\"target\":null}";

	private HttpServer server;
	private final AtomicReference<JsonObject> lastBody = new AtomicReference<>();
	private final AtomicReference<String> lastAuth = new AtomicReference<>();
	private volatile int status = 200;
	private volatile String response = "{}";
	private volatile long delayMillis;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			lastBody.set(JsonParser.parseString(body).getAsJsonObject());
			String auth = exchange.getRequestHeaders().getFirst("Authorization");
			lastAuth.set(auth != null ? auth : exchange.getRequestHeaders().getFirst("x-api-key"));
			try {
				Thread.sleep(delayMillis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private String base() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	/** The model's JSON as a JSON string value (providers wrap it in a "content"/"text" field). */
	private static String quoted(String json) {
		return new com.google.gson.JsonPrimitive(json).toString();
	}

	@Test
	void ollamaSendsSchemaAndDisablesThinking() throws Exception {
		response = "{\"message\":{\"role\":\"assistant\",\"content\":" + quoted(TURN) + "},\"done\":true}";
		String raw = HttpModels.ollama(base(), "qwen3.5:9b", 5).complete(PROMPT).get(10, TimeUnit.SECONDS);
		assertEquals("Hello!", AiReply.parse(raw).reply());
		JsonObject body = lastBody.get();
		assertEquals("qwen3.5:9b", body.get("model").getAsString());
		assertFalse(body.get("stream").getAsBoolean());
		assertFalse(body.get("think").getAsBoolean());
		assertTrue(body.getAsJsonObject("format").has("properties"));
		assertEquals("system", body.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
	}

	@Test
	void openAiCompatibleUsesStrictJsonSchemaAndBearerKey() throws Exception {
		response = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + quoted(TURN) + ",\"refusal\":null}}]}";
		String raw = HttpModels.openAiCompatible(base() + "/v1", "some-model", "test-key", 5).complete(PROMPT).get(10, TimeUnit.SECONDS);
		assertEquals("Hello!", AiReply.parse(raw).reply());
		JsonObject format = lastBody.get().getAsJsonObject("response_format");
		assertEquals("json_schema", format.get("type").getAsString());
		assertTrue(format.getAsJsonObject("json_schema").get("strict").getAsBoolean());
		assertEquals("Bearer test-key", lastAuth.get());
	}

	@Test
	void anthropicUsesOutputConfigAndSkipsNonTextBlocks() throws Exception {
		response = "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"thinking\",\"thinking\":\"\"},{\"type\":\"text\",\"text\":" + quoted(TURN) + "}]}";
		String raw = HttpModels.anthropic(base(), "claude-opus-5-5", "test-key", "low", 5).complete(PROMPT).get(10, TimeUnit.SECONDS);
		assertEquals("Hello!", AiReply.parse(raw).reply());
		JsonObject body = lastBody.get();
		assertEquals("system text", body.get("system").getAsString());
		assertEquals("json_schema", body.getAsJsonObject("output_config").getAsJsonObject("format").get("type").getAsString());
		assertEquals("low", body.getAsJsonObject("output_config").get("effort").getAsString());
		assertEquals("test-key", lastAuth.get());
	}

	@Test
	void anthropicRefusalIsAFailureNotAReply() {
		response = "{\"stop_reason\":\"refusal\",\"content\":[]}";
		ExecutionException error = assertThrows(ExecutionException.class,
				() -> HttpModels.anthropic(base(), "claude-opus-5-5", "test-key", "low", 5).complete(PROMPT).get(10, TimeUnit.SECONDS));
		assertTrue(error.getCause().getMessage().contains("refusal"));
	}

	@Test
	void httpErrorsBecomeFailures() {
		status = 401;
		response = "{\"error\":\"bad key\"}";
		ExecutionException error = assertThrows(ExecutionException.class,
				() -> HttpModels.openAiCompatible(base(), "m", "k", 5).complete(PROMPT).get(10, TimeUnit.SECONDS));
		assertTrue(error.getCause().getMessage().contains("401"));
	}

	@Test
	void slowServersTimeOut() {
		delayMillis = 6000;
		response = "{\"message\":{\"content\":\"{}\"}}";
		assertThrows(ExecutionException.class, () -> HttpModels.ollama(base(), "m", 3).complete(PROMPT).get(15, TimeUnit.SECONDS));
	}

	@Test
	void unreachableServerFailsFast() {
		server.stop(0);
		assertThrows(ExecutionException.class, () -> HttpModels.ollama(base(), "m", 3).complete(PROMPT).get(15, TimeUnit.SECONDS));
	}
}

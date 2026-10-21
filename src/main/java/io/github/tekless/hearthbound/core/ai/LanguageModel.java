package io.github.tekless.hearthbound.core.ai;

import java.util.concurrent.CompletableFuture;

/** A chat model that answers with JSON text following {@link AiReply#SCHEMA}. */
public interface LanguageModel {
	/** Sends the prompt; completes on a background thread with the model's raw JSON text. */
	CompletableFuture<String> complete(PromptBuilder.Prompt prompt);

	/** Human-readable provider/model name for logs (never includes secrets). */
	String describe();
}

package io.github.tekless.hearthbound.ai;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.config.HearthboundConfig;
import io.github.tekless.hearthbound.config.Secrets;
import io.github.tekless.hearthbound.core.ai.AiReply;
import io.github.tekless.hearthbound.core.ai.CommitmentGuard;
import io.github.tekless.hearthbound.core.ai.HttpModels;
import io.github.tekless.hearthbound.core.ai.LanguageModel;
import io.github.tekless.hearthbound.core.ai.PromptBuilder;
import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.core.intent.IntentType;
import io.github.tekless.hearthbound.core.intent.TalkTopic;
import io.github.tekless.hearthbound.core.memory.CompanionMemory;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Runs AI conversations off the game thread. The flow for one message:
 * <ol>
 * <li>snapshot the world on the server thread and send the prompt asynchronously;</li>
 * <li>back on the server thread, check the companion and conversation are still current;</li>
 * <li>if the model proposed an intent, let the game validate and run it - the model's reply is
 * only shown if the game accepted; otherwise the truthful scripted refusal is used;</li>
 * <li>replies that promise actions the game isn't doing are replaced with scripted lines.</li>
 * </ol>
 * Any failure (timeout, bad JSON, refusal) falls back to scripted dialogue. After three failures
 * in a row the provider is paused for two minutes.
 */
public final class AiService {
	private static final int FAILURES_BEFORE_PAUSE = 3;
	private static final long PAUSE_MILLIS = 120_000;

	private final LanguageModel model;
	private final MinecraftServer server;
	private final boolean sharePlayerName;
	private final Map<UUID, AtomicInteger> generations = new ConcurrentHashMap<>();
	private int consecutiveFailures;
	private long pausedUntil;
	private boolean warnedPlayer;

	private AiService(LanguageModel model, MinecraftServer server, boolean sharePlayerName) {
		this.model = model;
		this.server = server;
		this.sharePlayerName = sharePlayerName;
	}

	/** Creates the service from config, or returns null when AI is off or misconfigured. */
	public static AiService create(HearthboundConfig config, Path configDir, MinecraftServer server) {
		HearthboundConfig.Ai ai = config.ai;
		String provider = ai.provider == null ? "none" : ai.provider.trim().toLowerCase(Locale.ROOT);
		int timeout = Math.max(3, Math.min(60, ai.timeoutSeconds));
		LanguageModel model;
		switch (provider) {
			case "none", "" -> {
				return null;
			}
			case "ollama" -> {
				String name = ai.model.isBlank() ? "qwen3.5:9b" : ai.model;
				if (name.endsWith(":cloud") && !ai.allowCloudModels) {
					Hearthbound.LOGGER.warn("Ollama model {} runs in the cloud; set ai.allowCloudModels=true to allow it. AI dialogue is off.", name);
					return null;
				}
				model = HttpModels.ollama(ai.baseUrl, name, timeout);
			}
			case "openai" -> {
				Optional<String> key = Secrets.apiKey("openai", configDir);
				boolean local = ai.baseUrl.contains("localhost") || ai.baseUrl.contains("127.0.0.1");
				if (key.isEmpty() && !local) {
					Hearthbound.LOGGER.warn("ai.provider is openai but no API key was found (HEARTHBOUND_API_KEY, OPENAI_API_KEY or config/hearthbound-secrets.json). AI dialogue is off.");
					return null;
				}
				if (ai.model.isBlank()) {
					Hearthbound.LOGGER.warn("ai.provider is openai but ai.model is empty. AI dialogue is off.");
					return null;
				}
				model = HttpModels.openAiCompatible(ai.baseUrl, ai.model, key.orElse(null), timeout);
			}
			case "anthropic" -> {
				Optional<String> key = Secrets.apiKey("anthropic", configDir);
				if (key.isEmpty()) {
					Hearthbound.LOGGER.warn("ai.provider is anthropic but no API key was found (HEARTHBOUND_API_KEY, ANTHROPIC_API_KEY or config/hearthbound-secrets.json). AI dialogue is off.");
					return null;
				}
				String name = ai.model.isBlank() ? "claude-opus-5-5" : ai.model;
				model = HttpModels.anthropic(ai.baseUrl, name, key.get(), ai.effort, timeout);
			}
			default -> {
				Hearthbound.LOGGER.warn("Unknown ai.provider '{}'. Use none, ollama, openai or anthropic. AI dialogue is off.", provider);
				return null;
			}
		}
		Hearthbound.LOGGER.info("AI dialogue enabled: {}", model.describe());
		return new AiService(model, server, ai.sharePlayerName);
	}

	/** Starts an AI reply. Returns false (caller uses scripts) if the provider is paused. */
	public boolean converse(ServerPlayer player, CompanionEntity companion, String message) {
		if (System.currentTimeMillis() < pausedUntil) {
			return false;
		}
		int generation = generations.computeIfAbsent(companion.getUUID(), id -> new AtomicInteger()).incrementAndGet();
		PromptBuilder.Context context = snapshot(player, companion);
		PromptBuilder.Prompt prompt = PromptBuilder.build(context, message);
		long started = System.currentTimeMillis();
		model.complete(prompt).whenComplete((raw, error) -> server.execute(() -> {
			if (error != null) {
				onFailure(player, companion, generation, error.getMessage());
				return;
			}
			AiReply reply;
			try {
				reply = AiReply.parse(raw);
			} catch (IllegalArgumentException e) {
				onFailure(player, companion, generation, "unusable reply: " + e.getMessage());
				return;
			}
			consecutiveFailures = 0;
			Hearthbound.LOGGER.debug("AI reply for {} in {} ms", companion.companionName(), System.currentTimeMillis() - started);
			deliver(player, companion, generation, reply);
		}));
		return true;
	}

	private void deliver(ServerPlayer player, CompanionEntity companion, int generation, AiReply reply) {
		if (!stillCurrent(player, companion, generation)) {
			return;
		}
		Optional<IntentType> accepted = Optional.empty();
		if (reply.intent().isPresent()) {
			Intent intent = reply.intent().get();
			Conversations.Outcome outcome = Conversations.apply(player, companion, intent);
			if (!outcome.accepted()) {
				// The model agreed to something the game refused: tell the truth instead.
				if (outcome.lineKey() != null) {
					companion.say(outcome.lineKey(), outcome.vars());
				}
				return;
			}
			accepted = Optional.of(intent.type());
		}
		if (CommitmentGuard.breaksPromise(reply.reply(), accepted)) {
			Hearthbound.LOGGER.debug("Replaced AI reply that promised an action not being performed: {}", reply.reply());
			if (accepted.isPresent()) {
				Conversations.Outcome outcome = new Conversations.Outcome(true, ackKey(accepted.get()), Map.of());
				companion.say(outcome.lineKey(), outcome.vars());
			} else {
				Conversations.talk(player, companion, TalkTopic.GENERIC);
			}
			return;
		}
		if (isRepeat(companion, reply.reply())) {
			Conversations.talk(player, companion, TalkTopic.GENERIC);
			return;
		}
		companion.sayLiteral(reply.reply());
	}

	private static String ackKey(IntentType type) {
		return switch (type) {
			case FOLLOW -> "ack.follow";
			case WAIT -> "ack.wait";
			case COME, GIVE -> "ack.come";
			case GATHER -> "status.idle";
			case ATTACK -> "combat.engage";
			case CANCEL -> "ack.cancel.nothing";
			case RECRUIT -> "recruit.accept";
			case DISMISS -> "dismiss.accept";
			case STATUS, INVENTORY -> "talk.generic";
		};
	}

	private static boolean isRepeat(CompanionEntity companion, String text) {
		String normalised = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", "");
		for (CompanionMemory.Line line : companion.memory().lines()) {
			if (!line.fromPlayer() && line.text().toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", "").equals(normalised)) {
				return true;
			}
		}
		return false;
	}

	private void onFailure(ServerPlayer player, CompanionEntity companion, int generation, String reason) {
		consecutiveFailures++;
		Hearthbound.LOGGER.warn("AI dialogue failed ({}): {}", model.describe(), reason);
		if (consecutiveFailures >= FAILURES_BEFORE_PAUSE) {
			pausedUntil = System.currentTimeMillis() + PAUSE_MILLIS;
			consecutiveFailures = 0;
			if (!warnedPlayer && !player.hasDisconnected()) {
				warnedPlayer = true;
				player.sendSystemMessage(Component.translatable("message.hearthbound.ai_paused").withStyle(ChatFormatting.GRAY));
			}
		}
		if (stillCurrent(player, companion, generation)) {
			Conversations.talk(player, companion, TalkTopic.GENERIC);
		}
	}

	/** The reply is only shown if nothing changed while we waited. */
	private boolean stillCurrent(ServerPlayer player, CompanionEntity companion, int generation) {
		AtomicInteger latest = generations.get(companion.getUUID());
		return latest != null && latest.get() == generation && companion.isAlive() && !companion.isRemoved()
				&& !player.hasDisconnected() && player.level() == companion.level() && companion.distanceTo(player) < 96;
	}

	private PromptBuilder.Context snapshot(ServerPlayer player, CompanionEntity companion) {
		Map<String, String> world = new LinkedHashMap<>();
		long time = companion.level().getOverworldClockTime() % 24000;
		world.put("Dimension", companion.level().dimension().identifier().getPath().replace('_', ' '));
		world.put("Biome", Conversations.biomeName(companion));
		world.put("Time of day", time < 1000 ? "dawn" : time < 11500 ? "day" : time < 13500 ? "dusk" : time < 23000 ? "night" : "dawn");
		world.put("Weather", companion.level().isThundering() ? "thunderstorm" : companion.level().isRaining() ? "rain" : "clear");
		world.put("Your health", Math.round(companion.getHealth()) + "/" + Math.round(companion.getMaxHealth()));
		world.put("Player health", Math.round(player.getHealth()) + "/" + Math.round(player.getMaxHealth()));
		world.put("Player is holding", player.getMainHandItem().isEmpty() ? "nothing" : player.getMainHandItem().getHoverName().getString());
		world.put("You are holding", companion.getMainHandItem().isEmpty() ? "nothing" : companion.getMainHandItem().getHoverName().getString());
		world.put("Underground", companion.level().canSeeSky(companion.blockPosition()) ? "no" : "yes");
		List<String> hostiles = companion.level().getEntities(companion, companion.getBoundingBox().inflate(16), e -> e instanceof Enemy && e.isAlive())
				.stream().map(Entity::getName).map(Component::getString).distinct().limit(5).toList();
		world.put("Hostile creatures nearby", hostiles.isEmpty() ? "none" : String.join(", ", hostiles));
		world.put("Other companions nearby", String.join(", ", companion.level()
				.getEntitiesOfClass(CompanionEntity.class, companion.getBoundingBox().inflate(16), c -> c != companion)
				.stream().map(CompanionEntity::companionName).limit(4).toList()));
		List<CompanionMemory.Line> lines = companion.memory().lines();
		List<CompanionMemory.Line> recent = lines.subList(Math.max(0, lines.size() - 8), lines.size());
		String playerName = sharePlayerName ? player.getName().getString() : "the player";
		return new PromptBuilder.Context(companion.companionName(), companion.archetype(), playerName, world, recent,
				companion.memory().events(), companion.memory().affinity(), companion.order().describe(), companion.isRecruited());
	}
}

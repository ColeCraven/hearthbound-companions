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
import java.util.ArrayList;
import java.util.List;

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
	public Server server = new Server();

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
		/** Most AI replies one player can ask for per minute; past that, companions answer with their own lines. */
		public int maxRequestsPerPlayerPerMinute = 6;
		/** Most AI requests waiting at the same time, across everyone on the server. */
		public int maxConcurrentRequests = 4;
	}

	/** Multiplayer settings. Single-player games can ignore all of this. */
	public static final class Server {
		/**
		 * Let players without the mod join: companions show up as vanilla mannequins (needs Polymer on the server).
		 * auto = on for a dedicated server with Polymer installed, on = always try, off = never.
		 */
		public String vanillaClients = "auto";
		/**
		 * How players without the mod see the cast's outfits.
		 * auto = signed skins from config/hearthbound-skins.json if there, else the resource pack if the player has it,
		 * else a default skin; signed / resource_pack / default force one source (with the default skin as fallback).
		 */
		public String skins = "auto";
		/** Players other than the owner can hurt companions. */
		public boolean otherPlayersCanHurt = false;
		/** Gathering skips blocks this close to anything man-made (planks, glass, doors, beds...). 0 turns it off. */
		public int buildGuardRadius = 4;
		/** Extra places where companions never break blocks. */
		public List<Area> protectedAreas = new ArrayList<>();
		public Hub hub = new Hub();
	}

	/** A spawn hub where the cast lives while nobody has recruited them. */
	public static final class Hub {
		public boolean enabled = false;
		public String dimension = "minecraft:overworld";
		/** Where residents stand around (a walkable block inside the hub). */
		public int x;
		public int y;
		public int z;
		/** How far residents wander from that spot. */
		public int wanderRadius = 12;
		/** No gathering within this distance of the hub. */
		public int protectRadius = 64;
		/** Cast members who live here: ["all"] or names like ["freya", "bram"]. */
		public List<String> residents = new ArrayList<>(List.of("all"));
		/** Show newcomers a one-time hint about how to talk to companions. */
		public boolean welcomeTip = true;
	}

	/** A cylinder (all heights) where companions won't break blocks. */
	public static final class Area {
		public String dimension = "minecraft:overworld";
		public int x;
		public int z;
		public int radius = 32;
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
					if (config.server == null) {
						config.server = new Server();
					}
					if (config.server.hub == null) {
						config.server.hub = new Hub();
					}
					if (config.server.protectedAreas == null) {
						config.server.protectedAreas = new ArrayList<>();
					}
					if (config.server.hub.residents == null) {
						config.server.hub.residents = new ArrayList<>();
					}
					write(file, config); // adds settings introduced since the file was written
					return config;
				}
			} catch (IOException | JsonParseException e) {
				Hearthbound.LOGGER.warn("Could not read {}, using defaults: {}", file, e.getMessage());
				return new HearthboundConfig();
			}
		}
		HearthboundConfig defaults = new HearthboundConfig();
		write(file, defaults);
		return defaults;
	}

	private static void write(Path file, HearthboundConfig config) {
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			Hearthbound.LOGGER.warn("Could not write config {}: {}", file, e.getMessage());
		}
	}
}

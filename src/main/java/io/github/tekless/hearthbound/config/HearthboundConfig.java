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
	/** How many companions one player can recruit at once. 0 means no limit. */
	public int partyLimit = 3;
	/** Per-player limits (by UUID) that replace {@link #partyLimit} for that player. 0 means no limit. */
	public java.util.Map<String, Integer> partyLimits = new java.util.LinkedHashMap<>();
	/** Companions who live somewhere follow a daily schedule: work, meals, the evening together, bed. */
	public boolean dailyLife = true;
	/** Keep a diary on a lectern near the meeting spot (if there is an empty one), a page per "while you were away". */
	public boolean diary = true;
	/** 0 = never comment unprompted, 1 = normal, 2 = chatty. */
	public double ambientChatter = 1.0;
	/** Idle companions pick their own jobs (gather logs, harvest crops, hunt, light torches) while following. Off unless you turn it on. */
	public boolean routines = false;
	private transient Path file;
	/** How often companions do things of their own accord while you stand still: 0 = never, 1 = normal, 2 = often. */
	public double autonomy = 1.0;
	/** Single player: every companion in the world takes orders from the host, recruited or not. On unless you turn it off. */
	public boolean obeyAll = true;
	/** Dedicated servers: operators (by UUID) who turned obey mode on for themselves. */
	public List<String> obeyPlayers = new ArrayList<>();
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
		/** Whether players can hurt companions at all (a companion's own player only ever does a little). /companion damage players on|off. */
		public boolean playersCanHurt = true;
		/** Companions take no damage from anything but /kill. /companion godmode on|off. */
		public boolean godmode = false;
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
		/** Cast members who live here: ["all"] or names like ["dasha", "charlie"]. */
		public List<String> residents = new ArrayList<>(List.of("all"));
		/** Show newcomers a one-time hint about how to talk to companions. */
		public boolean welcomeTip = true;
		/**
		 * Where things are at the hub, for the residents' daily life. Anything left out is found on its own
		 * (beds, a bell or campfire, a furnace or smoker beside a chest, farmland). See docs/examples/skyhaven-places.json.
		 */
		public Places places = new Places();
	}

	/**
	 * Places at a home (the hub, from the config). Points are [x, y, z]; boxes are {"from": [x, y, z], "to": [x, y, z]}
	 * or {"at": [x, y, z]}.
	 */
	public static final class Places {
		/** The meeting spot: a great tree, a bell, a campfire. */
		public int[] commons;
		/** Where meals happen. */
		public Box kitchen;
		/** The crops the farmers look after. Only crops inside it are ever harvested. */
		public Box farm;
		/** Where the harvest goes: every chest inside this box (and no other chest). */
		public Box storage;
		/** Beds by companion name, each the bed's head block. */
		public java.util.Map<String, int[]> beds = new java.util.LinkedHashMap<>();
		/** Points the guardians walk between. */
		public List<int[]> patrol = new ArrayList<>();
		/** Other named spots residents like to wander to ("star tower", "arrivals"). */
		public java.util.Map<String, int[]> spots = new java.util.LinkedHashMap<>();
	}

	/** A box of blocks, given by two corners, or one block. */
	public static final class Box {
		public int[] from;
		public int[] to;
		public int[] at;

		public Box() {
		}

		public Box(int[] from, int[] to) {
			this.from = from;
			this.to = to;
		}

		/** True if the corners are usable. */
		public boolean valid() {
			return (at != null && at.length == 3) || (from != null && from.length == 3 && (to == null || to.length == 3));
		}

		public int[] low() {
			int[] a = at != null ? at : from;
			int[] b = at != null ? at : to != null ? to : from;
			return new int[] {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])};
		}

		public int[] high() {
			int[] a = at != null ? at : from;
			int[] b = at != null ? at : to != null ? to : from;
			return new int[] {Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
		}
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

	/** Writes the settings back to the file they were loaded from (used by the in-game commands). */
	public void save() {
		if (file != null) {
			write(file, this);
		}
	}

	/** The named chattiness levels and what they mean as an {@link #ambientChatter} value. */
	public static final java.util.Map<String, Double> CHATTINESS = new java.util.LinkedHashMap<>(java.util.Map.of());

	static {
		CHATTINESS.put("off", 0.0);
		CHATTINESS.put("low", 0.4);
		CHATTINESS.put("normal", 1.0);
		CHATTINESS.put("high", 1.8);
	}

	/** The level name closest to the current setting. */
	public String chattinessName() {
		String best = "normal";
		double distance = Double.MAX_VALUE;
		for (java.util.Map.Entry<String, Double> level : CHATTINESS.entrySet()) {
			double d = Math.abs(level.getValue() - ambientChatter);
			if (d < distance) {
				distance = d;
				best = level.getKey();
			}
		}
		return best;
	}

	/** This player's party limit: their own if an admin set one, else the world's. 0 means no limit. */
	public int partyLimitFor(java.util.UUID player) {
		Integer own = partyLimits.get(player.toString());
		return Math.max(0, own != null ? own : partyLimit);
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
					if (config.server.hub.places == null) {
						config.server.hub.places = new Places();
					}
					if (config.partyLimits == null) {
						config.partyLimits = new java.util.LinkedHashMap<>();
					}
					if (config.obeyPlayers == null) {
						config.obeyPlayers = new ArrayList<>();
					}
					config.file = file;
					write(file, config); // adds settings introduced since the file was written
					return config;
				}
			} catch (IOException | JsonParseException e) {
				Hearthbound.LOGGER.warn("Could not read {}, using defaults: {}", file, e.getMessage());
				return new HearthboundConfig();
			}
		}
		HearthboundConfig defaults = new HearthboundConfig();
		defaults.file = file;
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

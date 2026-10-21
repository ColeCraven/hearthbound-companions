package io.github.tekless.hearthbound.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.core.persona.CastMember;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Who exists in this world, even while their chunk isn't loaded: every companion, which cast
 * character they are, who they travel with, and whether they live at the hub. Used for the party
 * limit, for not spawning a cast member twice, and for the hub's welcome tip. Saved as
 * {@code <world>/data/hearthbound_roster.json}.
 */
public final class CompanionRoster {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static @Nullable CompanionRoster current;
	private static @Nullable MinecraftServer currentServer;

	/** One companion's record. */
	public static final class Entry {
		public String name = "";
		public String cast = "";
		public @Nullable String owner;
		public @Nullable String ownerName;
		public boolean resident;
	}

	private static final class Data {
		Map<String, Entry> companions = new LinkedHashMap<>();
		Set<String> welcomed = new HashSet<>();
	}

	private final transient Path file;
	private Data data = new Data();
	private transient boolean dirty;

	private CompanionRoster(Path file) {
		this.file = file;
	}

	public static CompanionRoster get(MinecraftServer server) {
		if (current == null || currentServer != server) {
			current = new CompanionRoster(server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("hearthbound_roster.json"));
			current.load();
			currentServer = server;
		}
		return current;
	}

	/** Forgets the cached roster (called when the server stops, after saving). */
	public static void close() {
		if (current != null) {
			current.save();
		}
		current = null;
		currentServer = null;
	}

	// ---------------------------------------------------------------- updates

	/** Records a companion's current state (identity, owner, residence). */
	public void noteLoaded(CompanionEntity companion) {
		String key = companion.getUUID().toString();
		Entry entry = data.companions.computeIfAbsent(key, k -> new Entry());
		String cast = companion.castMember().map(CastMember::id).orElse("");
		String owner = companion.ownerId() == null ? null : companion.ownerId().toString();
		String ownerName = companion.ownerName();
		boolean changed = !entry.name.equals(companion.companionName()) || !entry.cast.equals(cast)
				|| !java.util.Objects.equals(entry.owner, owner) || !java.util.Objects.equals(entry.ownerName, ownerName)
				|| entry.resident != companion.isHubResident();
		if (changed) {
			entry.name = companion.companionName();
			entry.cast = cast;
			entry.owner = owner;
			entry.ownerName = ownerName;
			entry.resident = companion.isHubResident();
			dirty = true;
		}
	}

	/** The companion is gone for good (killed or removed by a command). */
	public void forget(UUID id) {
		if (data.companions.remove(id.toString()) != null) {
			dirty = true;
		}
	}

	// ---------------------------------------------------------------- queries

	/** Companions travelling with this player, loaded or not. */
	public int ownedBy(UUID player) {
		String key = player.toString();
		int count = 0;
		for (Entry entry : data.companions.values()) {
			if (key.equals(entry.owner)) {
				count++;
			}
		}
		return count;
	}

	/** True if this cast character already exists somewhere in the world. */
	public boolean castExists(CastMember member) {
		for (Entry entry : data.companions.values()) {
			if (member.id().equals(entry.cast)) {
				return true;
			}
		}
		return false;
	}

	public @Nullable Entry entryForCast(CastMember member) {
		for (Entry entry : data.companions.values()) {
			if (member.id().equals(entry.cast)) {
				return entry;
			}
		}
		return null;
	}

	public boolean welcomed(UUID player) {
		return data.welcomed.contains(player.toString());
	}

	public void markWelcomed(UUID player) {
		if (data.welcomed.add(player.toString())) {
			dirty = true;
		}
	}

	public Map<String, Entry> entries() {
		return java.util.Collections.unmodifiableMap(data.companions);
	}

	// ---------------------------------------------------------------- storage

	private void load() {
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Data loaded = GSON.fromJson(reader, Data.class);
			if (loaded != null) {
				if (loaded.companions == null) {
					loaded.companions = new LinkedHashMap<>();
				}
				if (loaded.welcomed == null) {
					loaded.welcomed = new HashSet<>();
				}
				data = loaded;
				// Cast characters renamed in 0.4: old names and ids in an existing roster become the new ones.
				for (Entry entry : data.companions.values()) {
					CastMember.byName(entry.cast).ifPresent(member -> {
						entry.cast = member.id();
						if (member.answersTo(entry.name)) {
							entry.name = member.displayName();
						}
					});
				}
			}
		} catch (IOException | JsonParseException e) {
			Hearthbound.LOGGER.warn("Could not read {}, starting a fresh roster: {}", file, e.getMessage());
		}
	}

	/** Writes the roster if anything changed. Cheap to call often. */
	public void save() {
		if (!dirty) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (IOException e) {
			Hearthbound.LOGGER.warn("Could not save {}: {}", file, e.getMessage());
		}
	}
}

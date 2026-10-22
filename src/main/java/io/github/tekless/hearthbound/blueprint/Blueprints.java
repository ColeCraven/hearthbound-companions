package io.github.tekless.hearthbound.blueprint;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.core.blueprint.Layout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * The builds players saved for companions to copy, kept with the world in {@code <world>/data/hearthbound/blueprints}
 * as structure files. Structure files from elsewhere can be used by name too, without any extra step: ones a
 * structure block saved in this world ({@code <world>/generated/<namespace>/structures}) and ones dropped into
 * {@code config/hearthbound/blueprints} (shared by every world).
 */
public final class Blueprints {
	/** The two corners a player has marked, and where. */
	public record Corners(String dimension, @Nullable BlockPos first, @Nullable BlockPos second) {
	}

	private static final Map<UUID, Corners> CORNERS = new HashMap<>();

	private Blueprints() {
	}

	/** Forgets marked corners (the world closed). */
	public static void reset() {
		CORNERS.clear();
	}

	public static Corners corners(UUID player) {
		return CORNERS.getOrDefault(player, new Corners("", null, null));
	}

	/** Marks the first or second corner. Corners in another dimension than the other one are dropped. */
	public static Corners mark(UUID player, String dimension, BlockPos pos, boolean first) {
		Corners old = corners(player);
		boolean same = old.dimension().equals(dimension);
		Corners next = first ? new Corners(dimension, pos.immutable(), same ? old.second() : null)
				: new Corners(dimension, same ? old.first() : null, pos.immutable());
		CORNERS.put(player, next);
		return next;
	}

	private static Path ownDir(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("hearthbound").resolve("blueprints");
	}

	private static Path sharedDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("hearthbound").resolve("blueprints");
	}

	/** Every structure file that can be built by name, own ones first (an own one wins over an import of the same name). */
	public static Map<String, Path> files(MinecraftServer server) {
		Map<String, Path> found = new LinkedHashMap<>();
		collect(ownDir(server), found);
		Path generated = server.getWorldPath(LevelResource.GENERATED_DIR);
		if (Files.isDirectory(generated)) {
			try (Stream<Path> namespaces = Files.list(generated)) {
				namespaces.map(ns -> ns.resolve("structures")).filter(Files::isDirectory).forEach(dir -> collect(dir, found));
			} catch (IOException e) {
				Hearthbound.LOGGER.warn("Could not list {}: {}", generated, e.getMessage());
			}
		}
		collect(sharedDir(), found);
		return found;
	}

	private static void collect(Path dir, Map<String, Path> into) {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> files = Files.walk(dir, 3)) {
			files.filter(p -> p.getFileName().toString().endsWith(".nbt")).sorted().forEach(p -> {
				String file = p.getFileName().toString();
				Layout.cleanName(file.substring(0, file.length() - 4)).ifPresent(name -> into.putIfAbsent(name, p));
			});
		} catch (IOException e) {
			Hearthbound.LOGGER.warn("Could not list {}: {}", dir, e.getMessage());
		}
	}

	public static List<String> names(MinecraftServer server) {
		return new ArrayList<>(files(server).keySet());
	}

	/** True if the file came from the world's own blueprints (so it can be deleted with a command). */
	public static boolean isOwn(MinecraftServer server, String name) {
		Path path = files(server).get(name);
		return path != null && path.startsWith(ownDir(server));
	}

	public static Optional<SavedBuild> load(MinecraftServer server, String name) {
		Path path = files(server).get(name);
		if (path == null) {
			return Optional.empty();
		}
		try {
			CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.create(64L * 1024 * 1024));
			return Optional.of(SavedBuild.fromTag(name, tag, server.registryAccess().lookupOrThrow(Registries.BLOCK), Direction.NORTH));
		} catch (IOException | RuntimeException e) {
			Hearthbound.LOGGER.warn("Could not read blueprint {} ({}): {}", name, path.getFileName(), e.getMessage());
			return Optional.empty();
		}
	}

	public static boolean save(MinecraftServer server, SavedBuild build) {
		Path path = ownDir(server).resolve(build.name() + ".nbt");
		try {
			Files.createDirectories(path.getParent());
			NbtIo.writeCompressed(build.toTag(), path);
			return true;
		} catch (IOException e) {
			Hearthbound.LOGGER.warn("Could not save blueprint {}: {}", build.name(), e.getMessage());
			return false;
		}
	}

	/** Deletes one of the world's own blueprints. Imported files are left alone. */
	public static boolean delete(MinecraftServer server, String name) {
		Path path = files(server).get(name);
		if (path == null || !path.startsWith(ownDir(server))) {
			return false;
		}
		try {
			return Files.deleteIfExists(path);
		} catch (IOException e) {
			return false;
		}
	}

	/** The saved build a request means ("build my cottage here"), if any. */
	public static Optional<String> match(MinecraftServer server, String phrase) {
		return Layout.findName(phrase, names(server));
	}
}

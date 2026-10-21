package io.github.tekless.hearthbound.life;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.config.HearthboundConfig;
import io.github.tekless.hearthbound.core.life.Bonds;
import io.github.tekless.hearthbound.core.persona.CastMember;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.world.CompanionTracker;
import io.github.tekless.hearthbound.world.HubResidents;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * The places companions live: the hub, and every spot a dismissed companion or a wanderer calls home. Homes
 * within {@link #AREA_RADIUS} blocks of each other share one area, with its beds, meeting spot, kitchen, farm,
 * storage and patrol points (see {@link Places}), and the last time anyone there was awake to live through the
 * day, which is what the catch-up works from. Saved as {@code <world>/data/hearthbound_places.json}, along with
 * friendships between companions and the storehouse chests companions built.
 */
public final class HomeAreas {
	public static final String HUB = "hub";
	/** Homes this close to an area's centre belong to it. */
	public static final int AREA_RADIUS = 32;
	/** How often (ticks) an area's surroundings are looked over again, so new beds, farms and chests are noticed. */
	private static final long RESCAN = 2400;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static @Nullable HomeAreas current;
	private static @Nullable MinecraftServer currentServer;

	/** One home area. Fields marked by hand (setplace) win over what a scan finds. */
	public static final class Area {
		public String id = "";
		public String dimension = "minecraft:overworld";
		public int[] centre = {0, 0, 0};
		/** Game time at which someone living here was last loaded and going about their day, or -1. */
		public long lastLive = -1;
		public int[] commons;
		public int[] kitchen;
		public int[] farmLow;
		public int[] farmHigh;
		public int[] storage;
		public List<int[]> patrol = new ArrayList<>();
		/** Beds by companion UUID (claimed, or marked by hand). */
		public Map<String, int[]> beds = new LinkedHashMap<>();
		/** Companions whose bed was marked by hand, so it isn't given away. */
		public List<String> markedBeds = new ArrayList<>();
		/** The meeting fire was lit by companions (so only they put it out). */
		public boolean litFire;
		/** A "while you were away" waiting for someone to come by. */
		public @Nullable String pendingSummary;
		/** When the surroundings were last looked over, or -1. */
		transient long scannedAt = -1;
		transient PlaceScan.Found found = PlaceScan.Found.NOTHING;
		transient @Nullable Places places;
		transient long placesAt = -1;

		public BlockPos centrePos() {
			return new BlockPos(centre[0], centre[1], centre[2]);
		}

		public boolean isHub() {
			return HUB.equals(id);
		}

		/** Forget the cached places so the next look picks up a change. */
		public void invalidate() {
			places = null;
			scannedAt = -1;
		}
	}

	/** A storehouse chest companions built. */
	public static final class Store {
		public String dimension = "minecraft:overworld";
		public int[] pos = {0, 0, 0};
	}

	private static final class Data {
		Map<String, Area> areas = new LinkedHashMap<>();
		Map<String, Integer> bonds = new LinkedHashMap<>();
		List<Store> storehouses = new ArrayList<>();
	}

	private final transient Path file;
	private Data data = new Data();
	private transient boolean dirty;
	/** Which area each loaded companion belongs to (forgotten when their home changes). */
	private final transient Map<UUID, String> members = new HashMap<>();
	private transient long lastScan = -1;

	private HomeAreas(Path file) {
		this.file = file;
	}

	public static HomeAreas get(MinecraftServer server) {
		if (current == null || currentServer != server) {
			current = new HomeAreas(server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("hearthbound_places.json"));
			current.load();
			currentServer = server;
		}
		return current;
	}

	/** Saves and forgets the cached data (the server is stopping). */
	public static void close() {
		if (current != null) {
			current.save();
		}
		current = null;
		currentServer = null;
	}

	// ---------------------------------------------------------------- areas

	/** The area a companion lives in (the hub for hub residents), created when it's a new home. */
	public Area areaOf(CompanionEntity companion) {
		String known = members.get(companion.getUUID());
		if (known != null && data.areas.containsKey(known)) {
			return data.areas.get(known);
		}
		String dimension = companion.level().dimension().identifier().toString();
		Area area;
		if (companion.isHubResident() && HubResidents.enabled() && dimension.equals(Hearthbound.config().server.hub.dimension)) {
			area = hubArea();
		} else {
			BlockPos home = companion.home() != null ? companion.home() : companion.blockPosition();
			area = nearest(dimension, home, AREA_RADIUS);
			if (area == null) {
				area = new Area();
				area.id = dimension + "@" + home.getX() + "," + home.getY() + "," + home.getZ();
				area.dimension = dimension;
				area.centre = new int[] {home.getX(), home.getY(), home.getZ()};
				data.areas.put(area.id, area);
				dirty = true;
			}
		}
		members.put(companion.getUUID(), area.id);
		return area;
	}

	private Area hubArea() {
		HearthboundConfig.Hub hub = Hearthbound.config().server.hub;
		Area area = data.areas.computeIfAbsent(HUB, id -> {
			Area fresh = new Area();
			fresh.id = HUB;
			dirty = true;
			return fresh;
		});
		if (area.centre[0] != hub.x || area.centre[1] != hub.y || area.centre[2] != hub.z || !area.dimension.equals(hub.dimension)) {
			area.centre = new int[] {hub.x, hub.y, hub.z};
			area.dimension = hub.dimension;
			area.invalidate();
			dirty = true;
		}
		return area;
	}

	/** The area whose centre is closest to {@code pos}, within {@code range} blocks, in that dimension. */
	public @Nullable Area nearest(String dimension, BlockPos pos, double range) {
		Area best = null;
		double bestDistance = range * range;
		for (Area area : data.areas.values()) {
			if (!area.dimension.equals(dimension)) {
				continue;
			}
			double distance = area.centrePos().distSqr(pos);
			if (distance <= bestDistance) {
				best = area;
				bestDistance = distance;
			}
		}
		return best;
	}

	public @Nullable Area byId(String id) {
		return data.areas.get(id);
	}

	/** The companion's home moved: work out their area again next time. */
	public void forgetMember(CompanionEntity companion) {
		members.remove(companion.getUUID());
	}

	/** Loaded companions living in this area (not travelling with anyone). */
	public List<CompanionEntity> residents(Area area) {
		List<CompanionEntity> found = new ArrayList<>();
		for (CompanionEntity companion : CompanionTracker.all()) {
			if (!companion.isRecruited() && companion.level().dimension().identifier().toString().equals(area.dimension)
					&& areaOf(companion) == area) {
				found.add(companion);
			}
		}
		return found;
	}

	public static Optional<ServerLevel> level(MinecraftServer server, Area area) {
		Identifier id = Identifier.tryParse(area.dimension);
		return id == null ? Optional.empty() : Optional.ofNullable(server.getLevel(ResourceKey.create(Registries.DIMENSION, id)));
	}

	// ---------------------------------------------------------------- places

	/**
	 * The places of an area, cached for half a minute. The hub's config comes first, then what a player marked,
	 * then what a scan of the surroundings found, then home itself.
	 */
	public Places places(ServerLevel level, Area area) {
		long now = level.getGameTime();
		if (area.places != null && area.placesAt >= 0 && now - area.placesAt < 600 && now >= area.placesAt) {
			return area.places;
		}
		if (area.scannedAt < 0 || now - area.scannedAt > RESCAN || now < area.scannedAt) {
			// A first look happens straight away; after that rescans are spread out, one area a second.
			if (area.scannedAt < 0 || lastScan < 0 || now - lastScan >= 20 || now < lastScan) {
				area.found = PlaceScan.scan(level, area.centrePos());
				area.scannedAt = now;
				lastScan = now;
			}
		}
		HearthboundConfig.Places config = area.isHub() ? Hearthbound.config().server.hub.places : null;
		PlaceScan.Found found = area.found;
		BlockPos centre = area.centrePos();

		BlockPos commons = point(config != null ? config.commons : null);
		boolean commonsFire = false;
		if (commons == null) {
			commons = point(area.commons);
		}
		if (commons == null && found.commons() != null) {
			commons = found.commons();
		}
		if (commons == null) {
			commons = centre;
		}
		commonsFire = level.getBlockState(commons).is(net.minecraft.tags.BlockTags.CAMPFIRES);

		Zone kitchenZone = config != null ? Zone.of(config.kitchen) : null;
		BlockPos kitchen = kitchenZone != null ? kitchenZone.centre() : point(area.kitchen);
		if (kitchen == null) {
			kitchen = found.kitchen() != null ? found.kitchen() : commons;
		}

		Zone farm = config != null ? Zone.of(config.farm) : null;
		boolean farmMarked = farm != null;
		if (farm == null && area.farmLow != null && area.farmHigh != null) {
			farm = Zone.of(point(area.farmLow), point(area.farmHigh));
			farmMarked = true;
		}
		if (farm == null) {
			farm = found.farm();
		}

		List<BlockPos> storage = new ArrayList<>();
		Zone storageZone = config != null ? Zone.of(config.storage) : null;
		if (storageZone != null && storageZone.volume() <= 4096) {
			for (BlockPos pos : storageZone.blocks()) {
				if (level.hasChunkAt(pos) && PlaceScan.isChest(level.getBlockState(pos))) {
					storage.add(pos.immutable());
				}
			}
		}
		BlockPos marked = point(area.storage);
		if (marked != null && PlaceScan.isChest(level.getBlockState(marked))) {
			storage.add(marked);
		}
		for (BlockPos store : stores(level)) {
			if (store.distSqr(centre) <= (double) AREA_RADIUS * AREA_RADIUS && !storage.contains(store)) {
				storage.add(store);
			}
		}

		List<BlockPos> patrol = new ArrayList<>();
		if (config != null && config.patrol != null) {
			config.patrol.stream().map(HomeAreas::point).filter(p -> p != null).forEach(patrol::add);
		}
		if (patrol.isEmpty()) {
			area.patrol.stream().map(HomeAreas::point).filter(p -> p != null).forEach(patrol::add);
		}
		if (patrol.isEmpty()) {
			patrol.addAll(found.patrol());
		}

		List<BlockPos> spots = new ArrayList<>();
		if (config != null && config.spots != null) {
			config.spots.values().stream().map(HomeAreas::point).filter(p -> p != null).forEach(spots::add);
		}
		area.places = new Places(commons, commonsFire, kitchen, farm, farmMarked, storage, patrol, spots, found.lectern());
		area.placesAt = now;
		return area.places;
	}

	/** The bed this companion sleeps in: the config's, one marked for them, the one they claimed, or a free one nearby. */
	public @Nullable BlockPos bedFor(ServerLevel level, Area area, CompanionEntity companion) {
		String key = companion.getUUID().toString();
		BlockPos configured = configuredBed(area, companion);
		if (configured != null) {
			return configured;
		}
		BlockPos claimed = point(area.beds.get(key));
		if (claimed != null && (isBedHead(level, claimed) || !level.hasChunkAt(claimed))) {
			return claimed;
		}
		if (claimed != null) {
			area.beds.remove(key); // the bed is gone
			area.markedBeds.remove(key);
			dirty = true;
		}
		places(level, area); // makes sure the area has been looked at
		BlockPos home = companion.home() != null ? companion.home() : area.centrePos();
		BlockPos best = null;
		for (BlockPos bed : area.found.beds()) {
			if (takenByOther(level, area, bed, key) || !isBedHead(level, bed) || level.getBlockState(bed).getValue(BedBlock.OCCUPIED)) {
				continue;
			}
			if (best == null || bed.distSqr(home) < best.distSqr(home)) {
				best = bed;
			}
		}
		if (best != null) {
			area.beds.put(key, new int[] {best.getX(), best.getY(), best.getZ()});
			dirty = true;
		}
		return best;
	}

	private boolean takenByOther(ServerLevel level, Area area, BlockPos bed, String key) {
		for (Map.Entry<String, int[]> entry : area.beds.entrySet()) {
			if (!entry.getKey().equals(key) && bed.equals(point(entry.getValue()))) {
				return true;
			}
		}
		if (area.isHub()) {
			for (int[] configured : Hearthbound.config().server.hub.places.beds.values()) {
				if (bed.equals(point(configured))) {
					return true;
				}
			}
		}
		return false;
	}

	/** The hub config's bed for this companion, matched by first name or cast id. */
	private static @Nullable BlockPos configuredBed(Area area, CompanionEntity companion) {
		if (!area.isHub()) {
			return null;
		}
		Map<String, int[]> beds = Hearthbound.config().server.hub.places.beds;
		if (beds == null || beds.isEmpty()) {
			return null;
		}
		String first = companion.companionName().split(" ")[0].toLowerCase(Locale.ROOT);
		String cast = companion.castMember().map(CastMember::id).orElse("");
		for (Map.Entry<String, int[]> entry : beds.entrySet()) {
			String name = entry.getKey().toLowerCase(Locale.ROOT);
			if (name.equals(first) || name.equals(cast) || CastMember.byName(name).map(CastMember::id).orElse("-").equals(cast)) {
				return point(entry.getValue());
			}
		}
		return null;
	}

	public static boolean isBedHead(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
	}

	// ---------------------------------------------------------------- marking places by hand

	public void markCommons(Area area, BlockPos pos) {
		area.commons = array(pos);
		changed(area);
	}

	public void markKitchen(Area area, BlockPos pos) {
		area.kitchen = array(pos);
		changed(area);
	}

	public void markFarm(Area area, Zone zone) {
		area.farmLow = array(zone.low());
		area.farmHigh = array(zone.high());
		changed(area);
	}

	/** The patch of farmland around a spot (for marking a farm by hand). */
	public static Zone farmAround(List<BlockPos> farmland, BlockPos at) {
		Zone zone = PlaceScan.farm(farmland, at);
		return zone != null ? zone : Zone.of(at.offset(-4, -1, -4), at.offset(4, 1, 4));
	}

	public void markStorage(Area area, BlockPos chest) {
		area.storage = array(chest);
		changed(area);
	}

	/** Adds a patrol point (eight at most; the oldest goes first). */
	public void addPatrol(Area area, BlockPos pos) {
		area.patrol.add(array(pos));
		while (area.patrol.size() > 8) {
			area.patrol.remove(0);
		}
		changed(area);
	}

	/** Gives this bed to this companion, taking it off anyone else in the area. */
	public void markBed(Area area, CompanionEntity companion, BlockPos bed) {
		String key = companion.getUUID().toString();
		area.beds.values().removeIf(other -> bed.equals(point(other)));
		area.beds.put(key, array(bed));
		if (!area.markedBeds.contains(key)) {
			area.markedBeds.add(key);
		}
		changed(area);
	}

	private void changed(Area area) {
		area.invalidate();
		dirty = true;
	}

	// ---------------------------------------------------------------- storehouses companions built

	public void registerStore(ServerLevel level, BlockPos chest) {
		Store store = new Store();
		store.dimension = level.dimension().identifier().toString();
		store.pos = array(chest);
		data.storehouses.removeIf(s -> s.dimension.equals(store.dimension) && chest.equals(point(s.pos)));
		data.storehouses.add(store);
		dirty = true;
	}

	/** Storehouse chests companions built in this level that are still chests (where loaded). */
	public List<BlockPos> stores(ServerLevel level) {
		String dimension = level.dimension().identifier().toString();
		List<BlockPos> found = new ArrayList<>();
		for (Store store : data.storehouses) {
			BlockPos pos = point(store.pos);
			if (pos != null && store.dimension.equals(dimension) && (!level.hasChunkAt(pos) || PlaceScan.isChest(level.getBlockState(pos)))) {
				found.add(pos);
			}
		}
		return found;
	}

	/**
	 * The storage chest nearest {@code from} within {@code range} blocks: one marked by a player, one in the hub
	 * config's storage, or a storehouse chest companions built. Never any other chest.
	 */
	public @Nullable BlockPos nearestStorage(ServerLevel level, BlockPos from, double range) {
		String dimension = level.dimension().identifier().toString();
		BlockPos best = null;
		double bestDistance = range * range;
		for (Area area : data.areas.values()) {
			if (!area.dimension.equals(dimension) || area.centrePos().distSqr(from) > (range + AREA_RADIUS) * (range + AREA_RADIUS)) {
				continue;
			}
			for (BlockPos chest : places(level, area).storage()) {
				double distance = chest.distSqr(from);
				if (distance <= bestDistance && level.hasChunkAt(chest) && PlaceScan.isChest(level.getBlockState(chest))) {
					best = chest;
					bestDistance = distance;
				}
			}
		}
		for (BlockPos chest : stores(level)) {
			double distance = chest.distSqr(from);
			if (distance <= bestDistance && level.hasChunkAt(chest) && PlaceScan.isChest(level.getBlockState(chest))) {
				best = chest;
				bestDistance = distance;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- friendships

	/** How two companions get on: -50 (can't stand each other) to 100 (best friends). */
	public int bond(UUID a, UUID b) {
		return data.bonds.getOrDefault(pair(a, b), 0);
	}

	public void growBond(UUID a, UUID b, int delta) {
		String key = pair(a, b);
		int value = Math.max(-50, Math.min(100, data.bonds.getOrDefault(key, 0) + delta));
		data.bonds.put(key, value);
		dirty = true;
	}

	/** The companion's closest friend among those loaded nearby, or null. */
	public @Nullable CompanionEntity bestFriend(CompanionEntity companion, List<CompanionEntity> others) {
		return others.stream().filter(o -> o != companion && bond(companion.getUUID(), o.getUUID()) >= Bonds.FRIENDS)
				.max(Comparator.comparingInt(o -> bond(companion.getUUID(), o.getUUID()))).orElse(null);
	}

	private static String pair(UUID a, UUID b) {
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	// ---------------------------------------------------------------- the clock

	/** Notes that someone here is living through the day right now. */
	public void touch(Area area, long now) {
		area.lastLive = now;
		dirty = true;
	}

	/** Players close enough to hear about this area. */
	public static List<ServerPlayer> playersNear(ServerLevel level, BlockPos centre, double range) {
		List<ServerPlayer> near = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.blockPosition().distSqr(centre) <= range * range) {
				near.add(player);
			}
		}
		return near;
	}

	public void markDirty() {
		dirty = true;
	}

	public java.util.Collection<Area> areas() {
		return data.areas.values();
	}

	// ---------------------------------------------------------------- storage

	static @Nullable BlockPos point(int @Nullable [] values) {
		return values == null || values.length != 3 ? null : new BlockPos(values[0], values[1], values[2]);
	}

	static int[] array(BlockPos pos) {
		return new int[] {pos.getX(), pos.getY(), pos.getZ()};
	}

	private void load() {
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Data loaded = GSON.fromJson(reader, Data.class);
			if (loaded != null) {
				if (loaded.areas == null) {
					loaded.areas = new LinkedHashMap<>();
				}
				if (loaded.bonds == null) {
					loaded.bonds = new LinkedHashMap<>();
				}
				if (loaded.storehouses == null) {
					loaded.storehouses = new ArrayList<>();
				}
				for (Area area : loaded.areas.values()) {
					if (area.patrol == null) {
						area.patrol = new ArrayList<>();
					}
					if (area.beds == null) {
						area.beds = new LinkedHashMap<>();
					}
					if (area.markedBeds == null) {
						area.markedBeds = new ArrayList<>();
					}
					if (area.centre == null || area.centre.length != 3) {
						area.centre = new int[] {0, 0, 0};
					}
					area.scannedAt = -1;
					area.placesAt = -1;
					area.found = PlaceScan.Found.NOTHING;
				}
				data = loaded;
			}
		} catch (IOException | JsonParseException e) {
			Hearthbound.LOGGER.warn("Could not read {}, starting afresh: {}", file, e.getMessage());
		}
	}

	/** Writes the file if anything changed. Cheap to call often. */
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

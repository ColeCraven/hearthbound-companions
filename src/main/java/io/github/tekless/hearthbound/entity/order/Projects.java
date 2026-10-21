package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.intent.ProjectParser;
import io.github.tekless.hearthbound.core.persona.Role;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.world.GatherCatalog;
import io.github.tekless.hearthbound.world.WorldRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Starts the big jobs a group can be given: a house each, a farm, a storehouse with a kitchen. It picks
 * building sites near the player, spaced apart and clear of existing structures and protected areas,
 * hands each companion a {@link ProjectOrder} or {@link FarmOrder}, and has collectors bring materials
 * to the builders. Everything is bounded: one project per companion, small buildings only.
 */
public final class Projects {
	/** Centre-to-centre distance between building sites. */
	private static final int SPACING = 9;
	private static final int MAX_CANDIDATES = 90;

	private record Reservation(String dimension, BlockPos centre) {
	}

	private static final List<Reservation> RESERVED = new ArrayList<>();

	/** Where a building goes: the player-style "feet" spot the blueprint is laid out from, and the way the door faces. */
	public record Site(BlockPos feet, Direction facing) {
		BlockPos centre() {
			return feet.relative(facing, 4);
		}
	}

	/**
	 * What was started.
	 *
	 * @param involved everyone who got a job
	 * @param plan     a short phrase for the group's acknowledgement
	 * @param problems things that could not be started, each already spoken by someone
	 */
	public record Launch(List<CompanionEntity> involved, String plan, List<String> problems) {
	}

	private Projects() {
	}

	public static void reset() {
		RESERVED.clear();
	}

	/**
	 * @param addressed  the companions the player spoke to
	 * @param collectors nearby collectors who were not addressed (only used when the message asks for them)
	 * @param builders   nearby builders who were not addressed (the storehouse is theirs when they were mentioned)
	 */
	public static Launch launch(ServerPlayer player, List<CompanionEntity> addressed, List<CompanionEntity> collectors,
			List<CompanionEntity> builders, ProjectParser.Plan plan) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos centre = player.blockPosition();
		List<CompanionEntity> involved = new ArrayList<>();
		List<String> parts = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		CompanionEntity lead = addressed.get(0);

		// Who does what.
		List<CompanionEntity> farmers = new ArrayList<>();
		if (plan.farm()) {
			addressed.stream().filter(c -> c.role() == Role.FARMER).forEach(farmers::add);
			if (farmers.isEmpty()) {
				farmers.add(lead);
			}
		}
		List<CompanionEntity> housers = new ArrayList<>();
		if (plan.houses()) {
			addressed.stream().filter(c -> !farmers.contains(c)).forEach(housers::add);
		}
		CompanionEntity storeBuilder = null;
		if (plan.storehouse()) {
			if (plan.builders() && !builders.isEmpty()) {
				storeBuilder = builders.get(0);
			} else {
				storeBuilder = addressed.stream().filter(c -> c.role() == Role.BUILDER && !housers.contains(c)).findFirst()
						.orElse(addressed.stream().filter(c -> !housers.contains(c) && !farmers.contains(c)).findFirst()
								.orElse(housers.isEmpty() ? lead : housers.get(0)));
			}
		}

		// Farm: the plot is planned first so no house is laid over it.
		FarmOrder.Plot plot = null;
		if (plan.farm()) {
			boolean bucket = farmers.stream().anyMatch(f -> f.countCarried(s -> s.is(Items.WATER_BUCKET)) > 0);
			plot = FarmOrder.plan(level, centre, bucket);
			if (plot == null) {
				boolean water = FarmOrder.waterNear(level, centre);
				lead.say(water ? "project.blocked.site" : "project.blocked.water", Map.of("thing", "a farm"));
				problems.add(water ? "no room for a farm" : "no water for a farm");
			} else {
				RESERVED.add(new Reservation(level.dimension().identifier().toString(), plot.water()));
			}
		}

		// Sites: one per house plus one for the storehouse.
		int wanted = housers.size() + (storeBuilder != null ? 1 : 0);
		List<Site> sites = findSites(level, centre, wanted);
		boolean supply = plan.collectors() && !collectors.isEmpty();
		java.util.Map<CompanionEntity, List<ProjectOrder.Step>> jobs = new java.util.LinkedHashMap<>();
		java.util.Map<CompanionEntity, List<String>> labels = new java.util.HashMap<>();
		List<Site> reserved = new ArrayList<>();

		// Houses: the first sites are the houses' own; anyone left over shares one.
		boolean storeSite = storeBuilder != null && !sites.isEmpty() && (housers.isEmpty() || sites.size() > 1);
		int own = Math.min(housers.size(), sites.size() - (storeSite ? 1 : 0));
		if (!housers.isEmpty() && own <= 0) {
			lead.say("project.blocked.site", Map.of("thing", "houses"));
			problems.add("no room for houses");
		}
		List<String> sharing = new ArrayList<>();
		for (int i = 0; i < housers.size() && own > 0; i++) {
			CompanionEntity builder = housers.get(i);
			boolean owner = i < own;
			Site site = sites.get(owner ? i : i % own);
			Blueprint blueprint = Blueprint.plan(Blueprint.Kind.HOUSE, site.feet(), site.facing(), level);
			List<ProjectOrder.Step> steps = jobs.computeIfAbsent(builder, b -> new ArrayList<>());
			steps.add(new ProjectSteps.Materials(blueprint, supply, owner ? 6 : 0, owner ? 1 : 2));
			steps.add(new ProjectSteps.Craft(Blueprint.Kind.HOUSE, owner));
			steps.add(new ProjectSteps.Build(blueprint, site.feet(), site.facing()));
			labels.computeIfAbsent(builder, b -> new ArrayList<>()).add("a house");
			if (owner) {
				reserve(level, site);
				reserved.add(site);
			} else {
				sharing.add(shortName(builder) + " shares " + shortName(housers.get(i % own)) + "'s");
			}
		}
		if (own > 0) {
			parts.add(own == housers.size() ? (housers.size() == 1 ? "a house" : "a house each")
					: own + " houses, where " + String.join(", ", sharing) + " house");
		}

		// Storehouse, built after the builder's house if they have one.
		if (storeBuilder != null) {
			if (storeSite) {
				Site site = sites.get(sites.size() - 1);
				Blueprint blueprint = Blueprint.plan(Blueprint.Kind.STOREHOUSE, site.feet(), site.facing(), level);
				List<ProjectOrder.Step> steps = jobs.computeIfAbsent(storeBuilder, b -> new ArrayList<>());
				steps.add(new ProjectSteps.Materials(blueprint, supply, 14, 1));
				steps.add(new ProjectSteps.Craft(Blueprint.Kind.STOREHOUSE, true));
				steps.add(new ProjectSteps.Build(blueprint, site.feet(), site.facing()));
				labels.computeIfAbsent(storeBuilder, b -> new ArrayList<>()).add("a storehouse");
				reserve(level, site);
				reserved.add(site);
				parts.add("a storehouse with a chest and a furnace (" + shortName(storeBuilder) + ")");
			} else {
				storeBuilder.say("project.blocked.site", Map.of("thing", "a storehouse"));
				problems.add("no room for a storehouse");
			}
		}
		Runnable freeAll = () -> reserved.forEach(site -> free(level, site));
		java.util.concurrent.atomic.AtomicInteger running = new java.util.concurrent.atomic.AtomicInteger(jobs.size());
		for (Map.Entry<CompanionEntity, List<ProjectOrder.Step>> job : jobs.entrySet()) {
			// Sites stay reserved until every builder on the project is done with it.
			start(player, job.getKey(), new ProjectOrder(String.join(" and ", labels.get(job.getKey())), job.getValue(), () -> {
				if (running.decrementAndGet() <= 0) {
					freeAll.run();
				}
			}));
			involved.add(job.getKey());
		}

		if (plot != null) {
			BlockPos waterAt = plot.water();
			java.util.concurrent.atomic.AtomicInteger working = new java.util.concurrent.atomic.AtomicInteger(farmers.size());
			for (int i = 0; i < farmers.size(); i++) {
				start(player, farmers.get(i), new FarmOrder(plot, i, farmers.size(), () -> {
					if (working.decrementAndGet() <= 0) {
						String dimension = level.dimension().identifier().toString();
						RESERVED.removeIf(r -> r.dimension().equals(dimension) && r.centre().equals(waterAt));
					}
				}));
				if (!involved.contains(farmers.get(i))) {
					involved.add(farmers.get(i));
				}
			}
			parts.add("a farm of " + plot.size() + " plots " + (plot.placesWater() ? "around a new water source" : "beside the water"));
		}

		// Collectors bring logs (and stone for a furnace) to the builders.
		if (supply && !involved.isEmpty()) {
			List<CompanionEntity> needy = involved.stream().filter(c -> c.order() instanceof ProjectOrder).toList();
			if (!needy.isEmpty() && GatherCatalog.resolve("logs") instanceof GatherCatalog.Resolution.Gatherable logs) {
				for (int i = 0; i < collectors.size(); i++) {
					CompanionEntity collector = collectors.get(i);
					CompanionEntity to = needy.get(i % needy.size());
					boolean stone = plan.storehouse() && i == 1 && GatherCatalog.resolve("cobblestone") instanceof GatherCatalog.Resolution.Gatherable;
					GatherCatalog.Resolution wanted2 = stone ? GatherCatalog.resolve("cobblestone") : logs;
					if (wanted2 instanceof GatherCatalog.Resolution.Gatherable gatherable) {
						collector.setCommander(player);
						collector.setOrder(new GatherOrder(collector, gatherable.spec(), stone ? 16 : 24).deliverTo(to));
						involved.add(collector);
					}
				}
				parts.add(collectors.size() == 1 ? shortName(collectors.get(0)) + " gathering materials for the builders" : "collectors gathering materials for the builders");
			}
		}
		return new Launch(involved, String.join(", ", parts), problems);
	}

	/**
	 * A small shelter a builder puts up for themselves near home when nobody there has a roof (autonomy only):
	 * a clear site nearby, wood chopped and crafted into planks if they're short, and an honest stop if there's no
	 * wood to be had. Null when there is nowhere suitable to build.
	 */
	public static ProjectOrder ownShelter(ServerLevel level, BlockPos home) {
		List<Site> sites = findSites(level, home, 1);
		if (sites.isEmpty()) {
			return null;
		}
		Site site = sites.get(0);
		Blueprint blueprint = Blueprint.plan(Blueprint.Kind.SHELTER, site.feet(), site.facing(), level);
		reserve(level, site);
		List<ProjectOrder.Step> steps = new ArrayList<>();
		steps.add(new ProjectSteps.Materials(blueprint, false, 2, 1));
		steps.add(new ProjectSteps.Craft(Blueprint.Kind.SHELTER, true));
		steps.add(new ProjectSteps.Build(blueprint, site.feet(), site.facing()));
		return new ProjectOrder("a shelter of our own", steps, () -> free(level, site));
	}

	private static void start(ServerPlayer player, CompanionEntity companion, CompanionOrder order) {
		companion.setCommander(player);
		companion.setOrder(order);
	}

	private static String shortName(CompanionEntity companion) {
		return companion.companionName().split(" ")[0];
	}

	// ------------------------------------------------------------------ sites

	private static void reserve(ServerLevel level, Site site) {
		RESERVED.add(new Reservation(level.dimension().identifier().toString(), site.centre()));
	}

	private static void free(ServerLevel level, Site site) {
		if (site != null) {
			String dimension = level.dimension().identifier().toString();
			RESERVED.removeIf(r -> r.dimension().equals(dimension) && r.centre().equals(site.centre()));
		}
	}

	private static boolean reservedNear(ServerLevel level, BlockPos centre) {
		String dimension = level.dimension().identifier().toString();
		for (Reservation r : RESERVED) {
			if (r.dimension().equals(dimension) && Math.abs(r.centre().getX() - centre.getX()) < SPACING && Math.abs(r.centre().getZ() - centre.getZ()) < SPACING) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Up to {@code count} building sites around {@code centre}: flat enough, clear, not protected, not beside
	 * anything a player built, spaced apart, with the door facing the middle of the village.
	 */
	public static List<Site> findSites(ServerLevel level, BlockPos centre, int count) {
		List<Site> found = new ArrayList<>();
		if (count <= 0) {
			return found;
		}
		List<BlockPos> candidates = new ArrayList<>();
		for (int i = -5; i <= 5; i++) {
			for (int j = -5; j <= 5; j++) {
				if (Math.abs(i * SPACING) + Math.abs(j * SPACING) >= 6) {
					candidates.add(centre.offset(i * SPACING, 0, j * SPACING));
				}
			}
		}
		candidates.sort(Comparator.comparingDouble(p -> p.distSqr(centre)));
		List<Site> chosen = new ArrayList<>();
		for (int n = 0; n < Math.min(MAX_CANDIDATES, candidates.size()) && found.size() < count; n++) {
			BlockPos column = candidates.get(n);
			if (!level.hasChunkAt(column)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			BlockPos feet = new BlockPos(column.getX(), y, column.getZ());
			Direction facing = Direction.getApproximateNearest(centre.getX() - feet.getX(), 0, centre.getZ() - feet.getZ());
			if (facing.getAxis().isVertical()) {
				facing = Direction.NORTH;
			}
			Site site = new Site(feet, facing);
			if (reservedNear(level, site.centre()) || chosen.stream().anyMatch(s -> Math.abs(s.centre().getX() - site.centre().getX()) < SPACING
					&& Math.abs(s.centre().getZ() - site.centre().getZ()) < SPACING) || !siteFree(level, site)) {
				continue;
			}
			chosen.add(site);
			found.add(site);
		}
		return found;
	}

	private static boolean siteFree(ServerLevel level, Site site) {
		Blueprint blueprint = Blueprint.plan(Blueprint.Kind.HOUSE, site.feet(), site.facing(), level);
		for (Blueprint.Spot spot : blueprint.spots()) {
			if (WorldRules.isProtected(level, spot.pos())) {
				return false;
			}
		}
		if (blueprint.siteProblem(level).isPresent()) {
			return false;
		}
		BlockPos c = site.centre();
		for (BlockPos pos : BlockPos.betweenClosed(c.offset(-6, -1, -6), c.offset(6, 5, 6))) {
			if (level.hasChunkAt(pos) && WorldRules.isManMade(level.getBlockState(pos))) {
				return false;
			}
		}
		return true;
	}
}

package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.dialogue.English;
import io.github.tekless.hearthbound.core.persona.Role;
import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.world.GatherCatalog;
import io.github.tekless.hearthbound.world.WorldRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One building, put up by several companions together from a plain request ("build a house", "let's build a
 * house"). There is one blueprint and one list of blocks still to place: each builder claims the next free spot,
 * places it from their own pack and claims another, so the walls go up from several sides at once. Builders who
 * run dry are topped up by the others; collectors chop wood and hand it over; guardians walk round the site and
 * deal with anything hostile. Progress is announced at the halfway mark and at the end, and when the blocks run out
 * and nothing more is coming, someone says what's missing and the job stops. Nothing is claimed as built unless it is.
 */
public final class TeamJob {
	private static final double REACH = 6.0;
	private static final int PLACE_EVERY = 5;
	/** How long builders wait on collectors before chopping wood themselves (ticks). */
	private static final int WAIT_FOR_SUPPLY = 1200;

	private final Blueprint blueprint;
	private final BlockPos feet;
	private final Direction facing;
	private final String label;
	private final List<BlockPos> queue;
	private final Map<BlockPos, CompanionEntity> claims = new HashMap<>();
	private final List<CompanionEntity> builders = new ArrayList<>();
	private final List<GatherOrder> supply = new ArrayList<>();
	private final int total;
	private int placed;
	private boolean halfwaySaid;
	private TaskStatus status = TaskStatus.RUNNING;
	private @Nullable CompanionEntity lead;

	/**
	 * What was started.
	 *
	 * @param plan the plan in plain words ("Maddie and I'll do the walls, Hazel, get us some logs")
	 */
	public record Launch(List<CompanionEntity> involved, String plan, CompanionEntity speaker) {
	}

	private TeamJob(Blueprint blueprint, BlockPos feet, Direction facing, String label, ServerLevel level) {
		this.blueprint = blueprint;
		this.feet = feet;
		this.facing = facing;
		this.label = label;
		List<BlockPos> open = new ArrayList<>(blueprint.openSpots(level));
		open.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingDouble(p -> p.distSqr(feet)));
		this.queue = open;
		this.total = open.size();
	}

	/**
	 * Lays out the building in front of the player (or the nearest clear site), shares it out, and starts everyone.
	 * Returns null, after the lead has said why, when there's nowhere to build.
	 *
	 * @param crew  the companions within earshot who take the player's orders, nearest first
	 * @param lead  the one who answers (the one the player is talking to, or the nearest)
	 */
	public static @Nullable Launch start(ServerPlayer player, List<CompanionEntity> crew, CompanionEntity lead, Blueprint.Kind kind) {
		ServerLevel level = (ServerLevel) player.level();
		Direction facing = player.getDirection();
		BlockPos feet = player.blockPosition();
		Blueprint blueprint = Blueprint.plan(kind, feet, facing, level);
		if (!siteOk(level, blueprint)) {
			List<Projects.Site> sites = Projects.findSites(level, feet, 1);
			if (sites.isEmpty()) {
				lead.say("project.blocked.site", Map.of("thing", kind.label()));
				return null;
			}
			feet = sites.get(0).feet();
			facing = sites.get(0).facing();
			blueprint = Blueprint.plan(kind, feet, facing, level);
		}
		TeamJob job = new TeamJob(blueprint, feet, facing, kind.label(), level);
		List<CompanionEntity> builders = pickBuilders(crew, lead);
		List<CompanionEntity> watch = crew.stream().filter(c -> c.role() == Role.GUARDIAN && !builders.contains(c)).toList();
		List<CompanionEntity> helpers = crew.stream().filter(c -> c.role() != Role.GUARDIAN && !builders.contains(c)).toList();
		job.lead = builders.contains(lead) ? lead : builders.get(0);
		for (CompanionEntity builder : builders) {
			job.builders.add(builder);
			builder.setCommander(player);
			builder.setOrder(new Builder(job));
		}
		// Collectors (and anyone else free) bring wood when the builders between them are short.
		int carried = builders.stream().mapToInt(BuildOrder::countBuildingBlocks).sum()
				+ builders.stream().mapToInt(b -> b.countCarried(s -> s.is(net.minecraft.tags.ItemTags.LOGS)) * 4).sum();
		int missing = job.total + 4 - carried;
		List<CompanionEntity> suppliers = new ArrayList<>();
		if (missing > 0 && !helpers.isEmpty() && GatherCatalog.resolve("logs") instanceof GatherCatalog.Resolution.Gatherable logs) {
			int each = Math.min(32, (missing + 3) / 4 / helpers.size() + 2);
			List<CompanionEntity> neediest = builders.stream().sorted(Comparator.comparingInt(BuildOrder::countBuildingBlocks)).toList();
			for (int i = 0; i < helpers.size(); i++) {
				CompanionEntity helper = helpers.get(i);
				GatherOrder gather = new GatherOrder(helper, logs.spec(), each).deliverTo(neediest.get(i % neediest.size()));
				helper.setCommander(player);
				helper.setOrder(gather);
				job.supply.add(gather);
				suppliers.add(helper);
			}
		}
		for (CompanionEntity guard : watch) {
			guard.setCommander(player);
			guard.setOrder(new Watch(job));
		}
		List<CompanionEntity> involved = new ArrayList<>(builders);
		involved.addAll(suppliers);
		involved.addAll(watch);
		return new Launch(involved, plan(job.lead, builders, suppliers, watch, kind), job.lead);
	}

	/** The builders, or if there are none, everyone but the guardians, or at the very least whoever answered. */
	private static List<CompanionEntity> pickBuilders(List<CompanionEntity> crew, CompanionEntity lead) {
		List<CompanionEntity> builders = crew.stream().filter(c -> c.role() == Role.BUILDER).toList();
		if (builders.isEmpty()) {
			builders = crew.stream().filter(c -> c.role() != Role.GUARDIAN).toList();
		}
		return builders.isEmpty() ? List.of(lead) : builders;
	}

	private static boolean siteOk(ServerLevel level, Blueprint blueprint) {
		for (Blueprint.Spot spot : blueprint.spots()) {
			if (WorldRules.isProtected(level, spot.pos())) {
				return false;
			}
		}
		return blueprint.siteProblem(level).isEmpty();
	}

	/** "Maddie and I'll do the walls, Hazel, get us some logs, and Dasha keeps watch". */
	static String plan(CompanionEntity speaker, List<CompanionEntity> builders, List<CompanionEntity> suppliers, List<CompanionEntity> watch,
			Blueprint.Kind kind) {
		List<String> parts = new ArrayList<>();
		String work = kind == Blueprint.Kind.WALL ? "the wall" : "the walls and the roof";
		List<String> others = builders.stream().filter(b -> b != speaker).map(TeamJob::name).toList();
		if (builders.contains(speaker)) {
			parts.add(others.isEmpty() ? "I'll do " + work : English.list(others) + " and I'll do " + work);
		} else {
			parts.add(English.list(builders.stream().map(TeamJob::name).toList()) + (builders.size() == 1 ? " does " : " do ") + work);
		}
		if (!suppliers.isEmpty()) {
			parts.add(English.list(suppliers.stream().map(TeamJob::name).toList()) + ", get us some logs");
		}
		if (!watch.isEmpty()) {
			parts.add(English.list(watch.stream().map(TeamJob::name).toList()) + (watch.size() == 1 ? " keeps watch" : " keep watch"));
		}
		if (parts.size() == 1) {
			return parts.get(0);
		}
		return String.join(", ", parts.subList(0, parts.size() - 1)) + ", and " + parts.get(parts.size() - 1);
	}

	private static String name(CompanionEntity companion) {
		return companion.companionName().split(" ")[0];
	}

	public String label() {
		return label;
	}

	/** How many blocks are in, and how many the building needs. */
	public int placed() {
		return placed;
	}

	public int total() {
		return total;
	}

	public TaskStatus status() {
		return status;
	}

	private BlockPos centre() {
		return feet.relative(facing, 4);
	}

	/** The next free spot for this builder: the lowest layer first, nearest them, not someone else's. */
	private @Nullable BlockPos claim(CompanionEntity builder, ServerLevel level) {
		queue.removeIf(pos -> !Blueprint.isOpen(level.getBlockState(pos)));
		BlockPos best = null;
		int lowest = Integer.MAX_VALUE;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : queue) {
			if (claims.containsKey(pos) || !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) {
				continue;
			}
			double distance = pos.distSqr(builder.blockPosition());
			if (pos.getY() < lowest || pos.getY() == lowest && distance < bestDistance) {
				lowest = pos.getY();
				best = pos;
				bestDistance = distance;
			}
		}
		if (best != null) {
			claims.put(best, builder);
		}
		return best;
	}

	private void release(@Nullable BlockPos pos) {
		if (pos != null) {
			claims.remove(pos);
		}
	}

	/** True while a collector is still out getting wood for the build. */
	private boolean supplyComing() {
		return supply.stream().anyMatch(order -> !order.status().isFinished());
	}

	private void finish(ServerLevel level, CompanionEntity by) {
		if (status.isFinished()) {
			return;
		}
		// The finishing touches come from whoever has them: a door, a torch, a chest and a furnace for a storehouse.
		CompanionEntity doorman = builders.stream().filter(b -> b.countCarried(s -> s.getItem() instanceof BlockItem item && item.getBlock() instanceof DoorBlock) > 0)
				.findFirst().orElse(null);
		if (doorman == null) {
			for (CompanionEntity builder : builders) {
				Crafting.planksFromLogs(builder);
				if (Crafting.doors(builder)) {
					doorman = builder;
					break;
				}
			}
		}
		BlockPos workSpot = feet.relative(facing, 2);
		BuildOrder.finishTouches(level, blueprint, workSpot, facing, doorman != null ? doorman : by);
		status = TaskStatus.SUCCEEDED;
		CompanionEntity speaker = lead != null && lead.isAlive() ? lead : by;
		speaker.say("team.done", Map.of("thing", label));
		for (CompanionEntity builder : builders) {
			builder.memory().addEvent("We built " + label + " together for {player}");
		}
	}

	private void fail(CompanionEntity by, int have) {
		if (status.isFinished()) {
			return;
		}
		status = TaskStatus.FAILED;
		by.say("project.blocked.materials", Map.of("thing", label, "need", Integer.toString(queue.size()), "have", Integer.toString(have)));
	}

	private void placedOne(CompanionEntity by) {
		placed++;
		if (!halfwaySaid && placed * 2 >= total && placed < total) {
			halfwaySaid = true;
			by.say("team.progress", Map.of("thing", label, "count", Integer.toString(placed), "max", Integer.toString(total)));
		}
	}

	/** Someone stopped (an order, "stop"): the rest carry on, and the stopped one's claim is freed. */
	private void leave(CompanionEntity builder, @Nullable BlockPos claim) {
		release(claim);
		builders.remove(builder);
		if (builders.isEmpty() && !status.isFinished()) {
			status = TaskStatus.CANCELLED;
		}
	}

	// ------------------------------------------------------------------ the builders

	/** One builder's share of the job. */
	public static final class Builder implements CompanionOrder {
		private final TeamJob job;
		private @Nullable BlockPos claim;
		private @Nullable GatherOrder chopping;
		private int chopped;
		private int cooldown;
		private int waited;
		private int repath;
		private int stuck;
		private boolean saidShort;
		private TaskStatus status = TaskStatus.RUNNING;

		Builder(TeamJob job) {
			this.job = job;
		}

		public TeamJob job() {
			return job;
		}

		@Override
		public Kind kind() {
			return Kind.PROJECT;
		}

		@Override
		public void tick(CompanionEntity c) {
			if (status.isFinished()) {
				return;
			}
			if (job.status.isFinished()) {
				status = job.status == TaskStatus.SUCCEEDED ? TaskStatus.SUCCEEDED : TaskStatus.FAILED;
				return;
			}
			ServerLevel level = (ServerLevel) c.level();
			if (chopping != null) {
				chopping.tick(c);
				if (!chopping.status().isFinished()) {
					return;
				}
				chopping = null;
			}
			if (c.tickCount % 20 == 0) {
				Crafting.planksFromLogs(c);
			}
			if (BuildOrder.countBuildingBlocks(c) == 0) {
				job.release(claim);
				claim = null;
				outOfBlocks(c, level);
				return;
			}
			waited = 0;
			if (claim == null || !Blueprint.isOpen(level.getBlockState(claim))) {
				job.release(claim);
				claim = job.claim(c, level);
				if (claim == null) {
					if (job.queue.isEmpty() && job.claims.isEmpty()) {
						job.finish(level, c);
					}
					stayNear(c, job.feet.relative(job.facing, 1));
					return;
				}
			}
			if (c.getEyePosition().distanceTo(Vec3.atCenterOf(claim)) > REACH) {
				stayNear(c, standFor(claim));
				if (++stuck > 400) {
					job.release(claim); // can't get to that one; someone else may
					claim = null;
					stuck = 0;
				}
				return;
			}
			stuck = 0;
			c.getLookControl().setLookAt(Vec3.atCenterOf(claim));
			if (--cooldown > 0) {
				return;
			}
			cooldown = Math.max(1, (int) Math.round(PLACE_EVERY / c.role().multiplier(Role.Task.BUILD)));
			if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(claim)).isEmpty()) {
				job.release(claim);
				claim = null;
				return;
			}
			Crafting.planksFromLogs(c); // logs handed over a moment ago go in as planks, not as bits of tree
			ItemStack material = BuildOrder.pickMaterial(c);
			if (material == null) {
				return;
			}
			BlockState state = ((BlockItem) material.getItem()).getBlock().defaultBlockState();
			level.setBlockAndUpdate(claim, state);
			level.playSound(null, claim, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
			c.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			material.shrink(1);
			job.queue.remove(claim);
			job.release(claim);
			claim = null;
			job.placedOne(c);
		}

		/** No blocks left: a teammate with plenty shares, a collector is on the way, or they chop their own; else it's over. */
		private void outOfBlocks(CompanionEntity c, ServerLevel level) {
			for (CompanionEntity other : job.builders) {
				int spare = other == c ? 0 : BuildOrder.countBuildingBlocks(other);
				if (spare >= 8 && other.distanceTo(c) < 12) {
					other.giveTo(c, BuildOrder::isBuildingBlock, spare / 2);
					return;
				}
			}
			if (!saidShort) {
				saidShort = true;
				c.say("project.short", Map.of("thing", job.label, "need", Integer.toString(job.queue.size()), "have", "0"));
			}
			stayNear(c, job.feet.relative(job.facing, 1));
			if (job.supplyComing() && waited++ < WAIT_FOR_SUPPLY) {
				return;
			}
			if (chopped < 2 && GatherCatalog.resolve("logs") instanceof GatherCatalog.Resolution.Gatherable logs) {
				chopped++;
				int wanted = Math.min(32, job.queue.size() / 4 / Math.max(1, job.builders.size()) + 2);
				chopping = new GatherOrder(c, logs.spec(), wanted).keepForSelf();
				return;
			}
			job.fail(c, 0);
		}

		/** Where to stand to reach a spot: the doorway for the first builder, inside for the rest (the doorway stays open till the end). */
		private BlockPos standFor(BlockPos spot) {
			BlockPos doorway = job.feet.relative(job.facing, 2);
			int index = job.builders.indexOf(job.builders.stream().filter(b -> b.order() == this).findFirst().orElse(null));
			if (index <= 0 || job.blueprint.kind() == Blueprint.Kind.WALL) {
				return job.blueprint.kind() == Blueprint.Kind.WALL ? job.feet.relative(job.facing, 1) : doorway;
			}
			Direction right = job.facing.getClockWise();
			return job.feet.relative(job.facing, 4).relative(right, index % 2 == 0 ? 1 : -1);
		}

		private void stayNear(CompanionEntity c, BlockPos spot) {
			if (c.distanceToSqr(Vec3.atBottomCenterOf(spot)) > 2.5) {
				if (--repath <= 0 || c.getNavigation().isDone()) {
					repath = 20;
					c.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
				}
			}
		}

		@Override
		public void pause(CompanionEntity c) {
			if (chopping != null) {
				chopping.pause(c);
			}
			CompanionOrder.super.pause(c);
		}

		@Override
		public void cancel(CompanionEntity c) {
			if (chopping != null) {
				chopping.cancel(c);
			}
			if (!status.isFinished()) {
				status = TaskStatus.CANCELLED;
				job.leave(c, claim);
			}
			c.getNavigation().stop();
		}

		@Override
		public TaskStatus status() {
			return status;
		}

		@Override
		public String describe() {
			return "building " + job.label + " with the others (" + job.placed + "/" + job.total + " blocks)";
		}
	}

	// ------------------------------------------------------------------ the guardians

	/** Walking round the site and seeing off anything hostile until the building is done. */
	public static final class Watch implements CompanionOrder {
		private final TeamJob job;
		private int point;
		private int pause;
		private int repath;
		private TaskStatus status = TaskStatus.RUNNING;

		Watch(TeamJob job) {
			this.job = job;
		}

		public TeamJob job() {
			return job;
		}

		@Override
		public Kind kind() {
			return Kind.PROJECT;
		}

		@Override
		public void tick(CompanionEntity c) {
			if (status.isFinished()) {
				return;
			}
			if (job.status.isFinished()) {
				status = TaskStatus.SUCCEEDED;
				return;
			}
			ServerLevel level = (ServerLevel) c.level();
			if (c.tickCount % 20 == 0 && c.getTarget() == null) {
				level.getEntitiesOfClass(LivingEntity.class, new AABB(job.centre()).inflate(16), e -> e instanceof Enemy && e.isAlive() && c.canAttack(e))
						.stream().min(Comparator.comparingDouble(c::distanceToSqr)).ifPresent(c::setTarget);
			}
			Direction side = Direction.from2DDataValue(point % 4);
			BlockPos spot = job.centre().relative(side, 7);
			if (pause > 0) {
				pause--;
				c.getLookControl().setLookAt(spot.getX() + side.getStepX() * 6, c.getEyeY(), spot.getZ() + side.getStepZ() * 6);
				return;
			}
			if (c.distanceToSqr(Vec3.atBottomCenterOf(spot)) < 4.0) {
				pause = 60;
				point++;
				c.getNavigation().stop();
			} else if (--repath <= 0 || c.getNavigation().isDone()) {
				repath = 30;
				if (!c.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.8)) {
					point++;
				}
			}
		}

		@Override
		public void cancel(CompanionEntity c) {
			if (!status.isFinished()) {
				status = TaskStatus.CANCELLED;
			}
			c.getNavigation().stop();
		}

		@Override
		public TaskStatus status() {
			return status;
		}

		@Override
		public String describe() {
			return "keeping watch while the others build " + job.label;
		}
	}
}

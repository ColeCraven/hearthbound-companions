package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.blueprint.SavedBuild;
import io.github.tekless.hearthbound.core.blueprint.Layout;
import io.github.tekless.hearthbound.core.dialogue.English;
import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.world.WorldRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A build a player saved, put up again where they stand and turned to face the way they face. One or more builders
 * share it: the first gets whatever the pack is short of (gathering, crafting at a table, smelting), while everyone
 * places what they carry. A block goes in from the builder's own pack, or the lead's if they're close; the other half
 * of a door or a bed goes in with it. Blocks only go into empty space, never inside protected areas, and nothing is
 * called built unless it is.
 */
public final class CopyBuild {
	private static final double REACH = 5.5;
	private static final int PLACE_EVERY = 4;

	/** One block still to put down. */
	record Placement(BlockPos pos, BlockState state, Item item, int items) {
	}

	/** How starting went. */
	public sealed interface Result permits Launch, Refusal {
	}

	/** What was started, and the plan in words. */
	public record Launch(List<CompanionEntity> builders, String plan, CompanionEntity lead) implements Result {
	}

	/** Why it couldn't start. */
	public record Refusal(String lineKey, Map<String, String> vars) implements Result {
	}

	private final String thing;
	private final List<Placement> queue;
	private final Map<BlockPos, Integer> tries = new HashMap<>();
	private final Map<BlockPos, CompanionEntity> claims = new HashMap<>();
	private final List<CompanionEntity> builders = new ArrayList<>();
	private final Set<BlockPos> footprint;
	private final int total;
	private CompanionEntity lead;
	private Supply.@Nullable Step supply;
	private final Supply.Workshop workshop;
	private boolean supplying = true;
	private int placed;
	private int skipped;
	private boolean halfwaySaid;
	private TaskStatus status = TaskStatus.RUNNING;

	private CopyBuild(String thing, List<Placement> queue, Set<BlockPos> footprint) {
		this.thing = thing;
		this.queue = queue;
		this.footprint = footprint;
		this.total = queue.size();
		this.workshop = new Supply.Workshop(footprint::contains);
	}

	/** Where every block goes for a player standing at {@code feet} and facing {@code facing}. */
	public static List<SavedBuild.Piece> layOut(SavedBuild build, BlockPos feet, Direction facing) {
		int turns = Layout.turnsBetween(build.front().get2DDataValue(), facing.get2DDataValue());
		Rotation rotation = SavedBuild.rotation(turns);
		List<Layout.Offset> turned = new ArrayList<>();
		for (SavedBuild.Piece piece : build.pieces()) {
			turned.add(Layout.turn(new Layout.Offset(piece.offset().getX(), piece.offset().getY(), piece.offset().getZ()), turns));
		}
		Layout.Offset anchor = Layout.anchor(turned, facing.get2DDataValue(), 2);
		int lowest = turned.stream().mapToInt(Layout.Offset::y).min().orElse(0);
		List<SavedBuild.Piece> placed = new ArrayList<>();
		for (int i = 0; i < turned.size(); i++) {
			Layout.Offset o = turned.get(i);
			BlockPos pos = feet.offset(anchor.x() + o.x(), o.y() - lowest, anchor.z() + o.z());
			placed.add(new SavedBuild.Piece(pos, build.pieces().get(i).state().rotate(rotation)));
		}
		return placed;
	}

	/**
	 * Lays the build out in front of the player, checks the site, and starts the builders.
	 *
	 * @param crew the builders, the lead (the one who answers) first
	 * @return the launch, or why not
	 */
	public static Result start(ServerPlayer player, List<CompanionEntity> crew, SavedBuild build) {
		ServerLevel level = (ServerLevel) player.level();
		String thing = "your " + Layout.spoken(build.name());
		List<SavedBuild.Piece> pieces = layOut(build, player.blockPosition(), player.getDirection());
		if (pieces.isEmpty()) {
			return new Refusal("fail.build_site", Map.of("thing", thing, "why", "there's nothing in that blueprint a companion can place"));
		}
		Set<BlockPos> footprint = new HashSet<>();
		int blocked = 0;
		int lowest = pieces.stream().mapToInt(p -> p.offset().getY()).min().orElse(0);
		int bottom = 0;
		int supported = 0;
		List<Placement> queue = new ArrayList<>();
		for (SavedBuild.Piece piece : pieces) {
			BlockPos pos = piece.offset();
			footprint.add(pos);
			if (WorldRules.isProtected(level, pos)) {
				return new Refusal("fail.protected", Map.of());
			}
			if (level.getFluidState(pos).is(FluidTags.LAVA)) {
				return new Refusal("fail.build_site", Map.of("thing", thing, "why", "there's lava"));
			}
			BlockState here = level.getBlockState(pos);
			if (pos.getY() == lowest) {
				bottom++;
				if (!Blueprint.isOpen(level.getBlockState(pos.below()))) {
					supported++;
				}
			}
			if (here.is(piece.state().getBlock())) {
				continue; // already there
			}
			if (!Blueprint.isOpen(here)) {
				blocked++;
				continue;
			}
			BlockState state = piece.state();
			if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
				state = state.setValue(BlockStateProperties.WATERLOGGED, false);
			}
			queue.add(new Placement(pos.immutable(), state, SavedBuild.itemFor(state), SavedBuild.itemsFor(state)));
		}
		if (blocked > pieces.size() / 5) {
			return new Refusal("fail.build_site", Map.of("thing", thing, "why", "there's too much in the way"));
		}
		if (bottom > 0 && supported < bottom / 2) {
			return new Refusal("fail.build_site", Map.of("thing", thing, "why", "the ground's too uneven"));
		}
		BlockPos feet = player.blockPosition();
		// Solid blocks first, bottom up, then everything that hangs on them (torches, doors, stairs on a roof).
		queue.sort(Comparator.<Placement>comparingInt(p -> p.state().isCollisionShapeFullBlock(level, p.pos()) ? 0 : 1)
				.thenComparingInt(p -> p.pos().getY()).thenComparingDouble(p -> p.pos().distSqr(feet)));
		CopyBuild job = new CopyBuild(thing, queue, footprint);
		job.lead = crew.get(0);
		// Builders pool their wood and stone with the lead, who does any crafting.
		for (CompanionEntity helper : crew.subList(1, crew.size())) {
			helper.giveTo(job.lead, s -> s.is(net.minecraft.tags.ItemTags.LOGS) || s.is(net.minecraft.tags.ItemTags.PLANKS)
					|| s.is(net.minecraft.world.item.Items.COBBLESTONE) || s.is(net.minecraft.world.item.Items.STICK), 256);
		}
		job.supply = new Supply.Step(job::leadNeeds, job.workshop, thing);
		for (CompanionEntity builder : crew) {
			job.builders.add(builder);
			builder.setCommander(player);
			builder.setOrder(new Worker(job));
		}
		return new Launch(crew, plan(crew), job.lead);
	}

	/** "Maddie and I'll build it" / "I'll build it". */
	private static String plan(List<CompanionEntity> crew) {
		List<String> others = crew.subList(1, crew.size()).stream().map(c -> c.companionName().split(" ")[0]).toList();
		return others.isEmpty() ? "I'll build it" : English.list(others) + " and I'll build it";
	}

	/** What the lead still has to get: everything left to place, less what the other builders carry for it. */
	private Map<Item, Integer> leadNeeds() {
		Map<Item, Integer> needs = new LinkedHashMap<>();
		for (Placement p : queue) {
			needs.merge(p.item(), p.items(), Integer::sum);
		}
		for (CompanionEntity builder : builders) {
			if (builder != lead && !builder.isRemoved()) {
				needs.replaceAll((item, count) -> Math.max(0, count - builder.countCarried(s -> s.is(item))));
			}
		}
		needs.values().removeIf(count -> count <= 0);
		return needs;
	}

	public String label() {
		return thing;
	}

	public TaskStatus status() {
		return status;
	}

	public int placed() {
		return placed;
	}

	public int total() {
		return total;
	}

	/** The pack a builder takes a block from: their own, or the lead's if they stand close. */
	private @Nullable ItemStack source(CompanionEntity builder, Item item) {
		for (CompanionEntity from : builder == lead ? List.of(builder) : List.of(builder, lead)) {
			if (from != builder && (from.isRemoved() || from.distanceTo(builder) > 8)) {
				continue;
			}
			for (ItemStack stack : from.inventory().getItems()) {
				if (!stack.isEmpty() && stack.is(item)) {
					return stack;
				}
			}
		}
		return null;
	}

	private int carriedFor(Placement p, CompanionEntity builder) {
		int have = builder.countCarried(s -> s.is(p.item()));
		if (builder != lead && !lead.isRemoved() && lead.distanceTo(builder) <= 8) {
			have += lead.countCarried(s -> s.is(p.item()));
		}
		return have;
	}

	private @Nullable Placement claim(CompanionEntity builder) {
		Placement mine = null;
		for (Map.Entry<BlockPos, CompanionEntity> claim : claims.entrySet()) {
			if (claim.getValue() == builder) {
				for (Placement p : queue) {
					if (p.pos().equals(claim.getKey())) {
						return p;
					}
				}
			}
		}
		for (Placement p : queue) {
			if (!claims.containsKey(p.pos()) && carriedFor(p, builder) >= p.items()) {
				mine = p;
				break;
			}
		}
		if (mine != null) {
			claims.put(mine.pos(), builder);
		}
		return mine;
	}

	private void finish(TaskStatus result) {
		if (status.isFinished()) {
			return;
		}
		status = result;
		if (!lead.isRemoved()) {
			workshop.tidy(lead);
		}
	}

	/** One builder's part in it. */
	public static final class Worker implements CompanionOrder {
		private final CopyBuild job;
		private int cooldown;
		private int repath;
		private int walking;
		private boolean waiting;

		Worker(CopyBuild job) {
			this.job = job;
		}

		public CopyBuild job() {
			return job;
		}

		@Override
		public @org.jspecify.annotations.Nullable String blocker() {
			if (job.supplying && job.supply != null) {
				String supply = job.supply.blocker();
				return supply != null ? supply : job.lead == null ? null : "waiting while " + job.lead.companionName() + " gets the materials together";
			}
			return waiting ? "waiting for materials" : null;
		}

		@Override
		public Kind kind() {
			return Kind.PROJECT;
		}

		@Override
		public void tick(CompanionEntity c) {
			if (job.status.isFinished()) {
				return;
			}
			ServerLevel level = (ServerLevel) c.level();
			if (c == job.lead && job.supplying && job.supply != null) {
				ProjectOrder.State state = job.supply.tick(c);
				if (state == ProjectOrder.State.RUNNING) {
					return;
				}
				job.supplying = false;
				if (state == ProjectOrder.State.FAILED) {
					job.finish(TaskStatus.FAILED);
					return;
				}
				job.workshop.tidy(c);
			}
			if (job.queue.isEmpty()) {
				complete(c);
				return;
			}
			Placement next = job.claim(c);
			if (next == null) {
				if (c != job.lead) {
					// Nothing this one can place: wait while the lead is still getting materials, else they're done.
					if (job.supplying || job.builders.stream().anyMatch(b -> b != c && !b.isRemoved() && b.order() instanceof Worker)) {
						waitBy(c);
						return;
					}
				}
				if (!job.supplying && c == job.lead) {
					Map<Item, Integer> left = new LinkedHashMap<>();
					for (Placement p : job.queue) {
						boolean someoneHasIt = job.claims.containsKey(p.pos())
								|| job.builders.stream().anyMatch(b -> !b.isRemoved() && job.carriedFor(p, b) >= p.items());
						if (!someoneHasIt) {
							left.merge(p.item(), p.items(), Integer::sum);
						}
					}
					if (left.isEmpty()) {
						waitBy(c);
						return;
					}
					c.say("supply.blocked", Map.of("thing", job.thing, "missing", Supply.describe(left)));
					job.finish(TaskStatus.FAILED);
				}
				return;
			}
			waiting = false;
			BlockState current = level.getBlockState(next.pos());
			if (!Blueprint.isOpen(current)) {
				done(next, false); // something's there now; leave it be
				return;
			}
			if (c.getEyePosition().distanceTo(Vec3.atCenterOf(next.pos())) > REACH) {
				if (--repath <= 0 || c.getNavigation().isDone()) {
					repath = 20;
					c.getNavigation().moveTo(next.pos().getX() + 0.5, next.pos().getY(), next.pos().getZ() + 0.5, 1.0);
				}
				if (++walking > 300) {
					walking = 0;
					retry(next);
				}
				return;
			}
			walking = 0;
			c.getLookControl().setLookAt(Vec3.atCenterOf(next.pos()));
			if (--cooldown > 0) {
				return;
			}
			cooldown = Math.max(1, (int) Math.round(PLACE_EVERY / c.role().multiplier(io.github.tekless.hearthbound.core.persona.Role.Task.BUILD)));
			if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(next.pos())).isEmpty() || !next.state().canSurvive(level, next.pos())) {
				retry(next);
				return;
			}
			ItemStack stack = job.source(c, next.item());
			if (stack == null || (next.items() > 1 && job.carriedFor(next, c) < next.items())) {
				job.claims.remove(next.pos());
				return;
			}
			level.setBlockAndUpdate(next.pos(), next.state());
			SavedBuild.Piece other = SavedBuild.otherHalf(next.pos(), next.state());
			if (other != null && Blueprint.isOpen(level.getBlockState(other.offset()))) {
				level.setBlockAndUpdate(other.offset(), other.state());
			}
			level.playSound(null, next.pos(), next.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
			c.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			for (int i = 0; i < next.items(); i++) {
				ItemStack from = job.source(c, next.item());
				if (from != null) {
					from.shrink(1);
				}
			}
			done(next, true);
		}

		private void done(Placement p, boolean placedIt) {
			job.queue.remove(p);
			job.claims.remove(p.pos());
			if (placedIt) {
				job.placed++;
				if (job.builders.size() > 1 && !job.halfwaySaid && job.placed * 2 >= job.total && job.total > 8) {
					job.halfwaySaid = true;
					job.lead.say("team.progress", Map.of("thing", job.thing, "count", Integer.toString(job.placed), "max", Integer.toString(job.total)));
				}
			}
		}

		/** Leaves a block for later; after a few goes it is skipped and counted. */
		private void retry(Placement p) {
			job.claims.remove(p.pos());
			job.queue.remove(p);
			if (job.tries.merge(p.pos(), 1, Integer::sum) < 4) {
				job.queue.add(p);
			} else {
				job.skipped++;
			}
		}

		private void waitBy(CompanionEntity c) {
			if (!waiting) {
				waiting = true;
				c.getNavigation().stop();
			}
			if (job.lead != c && c.distanceTo(job.lead) > 6 && c.tickCount % 20 == 0) {
				c.getNavigation().moveTo(job.lead, 1.0);
			}
		}

		private void complete(CompanionEntity c) {
			if (job.status.isFinished()) {
				return;
			}
			job.finish(TaskStatus.SUCCEEDED);
			CompanionEntity speaker = job.lead.isRemoved() ? c : job.lead;
			if (job.skipped > 0) {
				speaker.say("blueprint.done_partly", Map.of("thing", job.thing, "count", Integer.toString(job.skipped)));
			} else {
				speaker.say(job.builders.size() > 1 ? "team.done" : "done.build", Map.of("thing", job.thing));
			}
			for (CompanionEntity builder : job.builders) {
				builder.memory().addEvent("I built " + job.thing.replace("your ", "{player}'s ") + " from a blueprint");
			}
		}

		@Override
		public void pause(CompanionEntity c) {
			if (c == job.lead && job.supply != null && job.supplying) {
				job.supply.pause(c);
			}
			CompanionOrder.super.pause(c);
		}

		@Override
		public void cancel(CompanionEntity c) {
			job.claims.values().removeIf(b -> b == c);
			job.builders.remove(c);
			if (c == job.lead) {
				if (job.supply != null && job.supplying) {
					job.supply.stop(c);
				}
				job.finish(TaskStatus.CANCELLED);
			} else if (job.builders.isEmpty()) {
				job.finish(TaskStatus.CANCELLED);
			}
			c.getNavigation().stop();
		}

		@Override
		public TaskStatus status() {
			if (job.status.isFinished()) {
				return job.status;
			}
			// A helper with nothing more to do steps away once the lead has stopped getting materials.
			return TaskStatus.RUNNING;
		}

		@Override
		public String describe() {
			String inner = job.supplying && job.supply != null ? ": " + job.supply.describe() : "";
			return "building " + job.thing + " (" + job.placed + "/" + job.total + " blocks" + inner + ")";
		}
	}

	/** Exposed for status replies. */
	public static Optional<CopyBuild> of(CompanionOrder order) {
		return order instanceof Worker worker ? Optional.of(worker.job()) : Optional.empty();
	}
}

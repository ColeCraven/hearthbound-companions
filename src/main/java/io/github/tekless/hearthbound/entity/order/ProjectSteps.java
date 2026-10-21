package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.world.GatherCatalog;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DoorBlock;

/** The stages a house or storehouse project is made of. */
final class ProjectSteps {
	private ProjectSteps() {
	}

	/**
	 * Makes sure the builder carries enough blocks: logs become planks; if still short they say so, wait a
	 * while for collectors to bring some (when collectors were asked to), then go and chop their own.
	 */
	static final class Materials implements ProjectOrder.Step {
		private static final int WAIT_FOR_SUPPLY = 1200;
		private final Blueprint blueprint;
		private final boolean expectSupply;
		private final int extra;
		private final int shareDivisor;
		private GatherOrder gathering;
		private int attempts;
		private int waited;
		private boolean complained;
		private int lastNeed;
		private int lastHave;

		@Override
		public String describe() {
			return gathering != null ? "gathering wood for the build" : lastNeed > 0 ? "short of blocks (" + lastHave + " of " + lastNeed + ")" : "checking materials";
		}

		Materials(Blueprint blueprint, boolean expectSupply, int extra, int shareDivisor) {
			this.blueprint = blueprint;
			this.expectSupply = expectSupply;
			this.extra = extra;
			this.shareDivisor = Math.max(1, shareDivisor);
		}

		@Override
		public ProjectOrder.State tick(CompanionEntity c) {
			if (gathering != null) {
				gathering.tick(c);
				if (!gathering.status().isFinished()) {
					return ProjectOrder.State.RUNNING;
				}
				gathering = null;
				attempts++;
			} else if (complained && c.tickCount % 10 != 0) {
				return ProjectOrder.State.RUNNING;
			}
			ServerLevel level = (ServerLevel) c.level();
			Crafting.planksFromLogs(c);
			int need = blueprint.openSpots(level).size() / shareDivisor + extra;
			int have = BuildOrder.countBuildingBlocks(c);
			if (have >= need) {
				return ProjectOrder.State.DONE;
			}
			lastNeed = need;
			lastHave = have;
			Map<String, String> vars = Map.of("thing", blueprint.kind().label(), "need", Integer.toString(need), "have", Integer.toString(have));
			if (!complained) {
				complained = true;
				c.say("project.short", vars);
			}
			if (expectSupply && waited < WAIT_FOR_SUPPLY) {
				waited += 10;
				return ProjectOrder.State.RUNNING;
			}
			if (attempts >= 2 || !(GatherCatalog.resolve("logs") instanceof GatherCatalog.Resolution.Gatherable logs)) {
				c.say("project.blocked.materials", vars);
				return ProjectOrder.State.FAILED;
			}
			gathering = new GatherOrder(c, logs.spec(), Math.min(32, (need - have) / 4 + 2)).keepForSelf();
			return ProjectOrder.State.RUNNING;
		}

		@Override
		public void pause(CompanionEntity c) {
			if (gathering != null) {
				gathering.pause(c);
			}
			ProjectOrder.Step.super.pause(c);
		}

		@Override
		public void stop(CompanionEntity c) {
			if (gathering != null) {
				gathering.cancel(c);
			}
		}
	}

	/** Crafts the extras a building needs from the pack: a door, and for a storehouse a chest and a furnace. */
	static final class Craft implements ProjectOrder.Step {
		private final Blueprint.Kind kind;
		private final boolean owner;

		Craft(Blueprint.Kind kind, boolean owner) {
			this.kind = kind;
			this.owner = owner;
		}

		@Override
		public ProjectOrder.State tick(CompanionEntity c) {
			if (!owner) {
				return ProjectOrder.State.DONE; // a helper builds with the owner's door and furniture
			}
			Crafting.planksFromLogs(c);
			if (c.countCarried(s -> s.getItem() instanceof BlockItem item && item.getBlock() instanceof DoorBlock) == 0) {
				Crafting.doors(c);
			}
			if (kind == Blueprint.Kind.STOREHOUSE) {
				if (c.countCarried(s -> s.is(Items.CHEST)) == 0) {
					Crafting.chest(c);
				}
				if (c.countCarried(s -> s.is(Items.FURNACE) || s.is(Items.SMOKER)) == 0 && !Crafting.furnace(c)) {
					c.say("project.nofurnace", Map.of());
				}
			}
			return ProjectOrder.State.DONE;
		}
	}

	/** Puts the blocks down, via a {@link BuildOrder}, after checking the site is still fine. */
	static final class Build implements ProjectOrder.Step {
		private final Blueprint blueprint;
		private final BlockPos feet;
		private final Direction facing;
		private BuildOrder order;

		Build(Blueprint blueprint, BlockPos feet, Direction facing) {
			this.blueprint = blueprint;
			this.feet = feet;
			this.facing = facing;
		}

		@Override
		public ProjectOrder.State tick(CompanionEntity c) {
			ServerLevel level = (ServerLevel) c.level();
			if (order == null) {
				Optional<String> problem = blueprint.siteProblem(level);
				if (problem.isPresent()) {
					c.say("project.blocked.site", Map.of("thing", blueprint.kind().label()));
					return ProjectOrder.State.FAILED;
				}
				order = new BuildOrder(c, blueprint, feet, facing, level);
			}
			order.tick(c);
			if (order.status() == TaskStatus.SUCCEEDED) {
				return ProjectOrder.State.DONE;
			}
			return order.status().isFinished() ? ProjectOrder.State.FAILED : ProjectOrder.State.RUNNING;
		}

		@Override
		public void stop(CompanionEntity c) {
			if (order != null) {
				order.cancel(c);
			}
		}

		@Override
		public String describe() {
			return order == null ? "getting ready to build" : order.describe();
		}
	}
}

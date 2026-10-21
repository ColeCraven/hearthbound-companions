package io.github.tekless.hearthbound.entity.order;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The few things companions know how to build, laid out in front of the player who asked:
 * a small shelter with a doorway, a wall, and a bridge across water or a gap. Blocks are only
 * ever placed into empty space, so a build can't wreck anything that's already there.
 */
public final class Blueprint {
	public enum Kind {
		SHELTER("a shelter"), WALL("a wall"), BRIDGE("a bridge"), CAMP("a camp");

		private final String label;

		Kind(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		public static Optional<Kind> byPhrase(String phrase) {
			String p = phrase == null ? "" : phrase.toLowerCase(Locale.ROOT).trim();
			if (p.matches(".*\\b(camp|campsite|campfire|bonfire)s?\\b.*")) {
				return Optional.of(CAMP);
			}
			if (p.matches(".*\\b(shelter|hut|house|cabin|home|shack|base|cottage|hideout|tent|room)s?\\b.*")) {
				return Optional.of(SHELTER);
			}
			if (p.matches(".*\\b(wall|barrier|barricade)s?\\b.*")) {
				return Optional.of(WALL);
			}
			if (p.matches(".*\\b(bridges?|walkway|path across|way across)\\b.*")) {
				return Optional.of(BRIDGE);
			}
			return Optional.empty();
		}
	}

	/** A block to place. Doorway spots get a door if the builder carries one, else stay open. */
	public record Spot(BlockPos pos, boolean doorway) {
	}

	private final Kind kind;
	/** Where a camp's fire goes (null for everything else). */
	private BlockPos centre;
	private final List<Spot> spots;
	private final List<BlockPos> mustBeClear;
	private final List<BlockPos> needsGround;

	private Blueprint(Kind kind, List<Spot> spots, List<BlockPos> mustBeClear, List<BlockPos> needsGround) {
		this.kind = kind;
		this.spots = spots;
		this.mustBeClear = mustBeClear;
		this.needsGround = needsGround;
	}

	public Kind kind() {
		return kind;
	}

	/** The campfire spot, or null if this isn't a camp. */
	public BlockPos centre() {
		return centre;
	}

	public List<Spot> spots() {
		return spots;
	}

	/**
	 * Lays out a structure relative to where the player stands and faces.
	 *
	 * @param feet   the player's feet position
	 * @param facing the horizontal direction the player faces
	 */
	public static Blueprint plan(Kind kind, BlockPos feet, Direction facing, ServerLevel level) {
		Direction right = facing.getClockWise();
		List<Spot> spots = new ArrayList<>();
		List<BlockPos> clear = new ArrayList<>();
		List<BlockPos> ground = new ArrayList<>();
		switch (kind) {
			case SHELTER -> {
				// 5x5 outside, 3x3 inside, walls 3 high, flat roof; doorway in the middle of the near wall.
				BlockPos corner = feet.relative(facing, 2).relative(right, -2);
				for (int y = 0; y <= 3; y++) {
					for (int f = 0; f < 5; f++) {
						for (int r = 0; r < 5; r++) {
							BlockPos pos = corner.relative(facing, f).relative(right, r).above(y);
							boolean edge = f == 0 || f == 4 || r == 0 || r == 4;
							if (y == 3) {
								spots.add(new Spot(pos, false)); // roof
							} else if (edge) {
								boolean doorway = f == 0 && r == 2 && y <= 1;
								spots.add(new Spot(pos, doorway));
							} else {
								clear.add(pos);
							}
							if (y == 0) {
								ground.add(pos.below());
							}
						}
					}
				}
			}
			case WALL -> {
				BlockPos start = feet.relative(facing, 2).relative(right, -3);
				for (int y = 0; y < 3; y++) {
					for (int r = 0; r < 7; r++) {
						BlockPos pos = start.relative(right, r).above(y);
						spots.add(new Spot(pos, false));
						if (y == 0) {
							ground.add(pos.below());
						}
					}
				}
			}
			case CAMP -> {
				// A campfire three blocks ahead with three seats around it (the fourth side is where you stand).
				BlockPos centre = feet.relative(facing, 3);
				List<BlockPos> seats = List.of(centre.relative(right, 2), centre.relative(right, -2), centre.relative(facing, 2));
				for (BlockPos seat : seats) {
					spots.add(new Spot(seat, false));
					clear.add(seat.above());
					ground.add(seat.below());
				}
				clear.add(centre);
				clear.add(centre.above());
				ground.add(centre.below());
				Blueprint blueprint = new Blueprint(kind, spots, clear, ground);
				blueprint.centre = centre;
				return blueprint;
			}
			case BRIDGE -> {
				// A walkway at the level the player stands on, out to the first solid ground (32 at most).
				BlockPos floor = feet.below();
				for (int i = 1; i <= 32; i++) {
					BlockPos pos = floor.relative(facing, i);
					BlockState state = level.getBlockState(pos);
					if (!state.isAir() && !state.canBeReplaced()) {
						break; // reached the far side
					}
					spots.add(new Spot(pos, false));
				}
			}
		}
		return new Blueprint(kind, spots, clear, ground);
	}

	/** Spots that still need a block (empty or replaceable, like grass), excluding doorways. */
	public List<BlockPos> openSpots(ServerLevel level) {
		List<BlockPos> open = new ArrayList<>();
		for (Spot spot : spots) {
			if (!spot.doorway() && isOpen(level.getBlockState(spot.pos()))) {
				open.add(spot.pos());
			}
		}
		return open;
	}

	/** Why the site won't do, or empty if it's fine: "uneven", "blocked", "lava" or "nothing to bridge". */
	public Optional<String> siteProblem(ServerLevel level) {
		if (kind == Kind.BRIDGE) {
			if (spots.isEmpty()) {
				return Optional.of("nothing to bridge");
			}
			for (Spot spot : spots) {
				if (level.getFluidState(spot.pos()).is(net.minecraft.tags.FluidTags.LAVA)) {
					return Optional.of("lava");
				}
			}
			return Optional.empty();
		}
		int supported = 0;
		for (BlockPos below : needsGround) {
			BlockState state = level.getBlockState(below);
			if (!state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty()) {
				supported++;
			}
		}
		if (supported < needsGround.size() * 0.7) {
			return Optional.of("uneven");
		}
		int blocked = 0;
		for (BlockPos pos : mustBeClear) {
			if (!isOpen(level.getBlockState(pos))) {
				blocked++;
			}
		}
		if (blocked > mustBeClear.size() / 4) {
			return Optional.of("blocked");
		}
		return Optional.empty();
	}

	static boolean isOpen(BlockState state) {
		return state.isAir() || state.canBeReplaced();
	}
}

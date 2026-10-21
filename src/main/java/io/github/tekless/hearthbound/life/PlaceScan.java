package io.github.tekless.hearthbound.life;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.jspecify.annotations.Nullable;

/**
 * One look around a home for the places a daily life needs: beds, a bell or campfire to meet at, a furnace or
 * smoker (or campfire) with a chest beside it for a kitchen, farmland, a lectern. It reads about twenty thousand
 * blocks, so {@link HomeAreas} does it at most once a second for one home, and only every few minutes per home.
 */
final class PlaceScan {
	static final int RADIUS = 20;
	static final int HEIGHT = 6;
	/** A found farm is never bigger than this across. */
	static final int MAX_FARM = 24;

	record Found(List<BlockPos> beds, @Nullable BlockPos commons, boolean commonsFire, @Nullable BlockPos kitchen, @Nullable Zone farm,
			List<BlockPos> patrol, @Nullable BlockPos lectern, List<BlockPos> chests) {
		static final Found NOTHING = new Found(List.of(), null, false, null, null, List.of(), null, List.of());
	}

	private PlaceScan() {
	}

	static Found scan(ServerLevel level, BlockPos centre) {
		List<BlockPos> beds = new ArrayList<>();
		List<BlockPos> bells = new ArrayList<>();
		List<BlockPos> fires = new ArrayList<>();
		List<BlockPos> cookers = new ArrayList<>();
		List<BlockPos> chests = new ArrayList<>();
		List<BlockPos> farmland = new ArrayList<>();
		List<BlockPos> lecterns = new ArrayList<>();
		for (int x = centre.getX() - RADIUS; x <= centre.getX() + RADIUS; x++) {
			for (int z = centre.getZ() - RADIUS; z <= centre.getZ() + RADIUS; z++) {
				if (!level.hasChunkAt(new BlockPos(x, centre.getY(), z))) {
					continue;
				}
				for (int y = centre.getY() - HEIGHT; y <= centre.getY() + HEIGHT; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
						beds.add(pos);
					} else if (state.is(Blocks.BELL)) {
						bells.add(pos);
					} else if (state.is(BlockTags.CAMPFIRES)) {
						fires.add(pos);
					} else if (state.is(Blocks.FURNACE) || state.is(Blocks.SMOKER)) {
						cookers.add(pos);
					} else if (isChest(state)) {
						chests.add(pos);
					} else if (state.is(Blocks.FARMLAND)) {
						farmland.add(pos);
					} else if (state.is(Blocks.LECTERN)) {
						lecterns.add(pos);
					}
				}
			}
		}
		Comparator<BlockPos> byDistance = Comparator.comparingDouble(p -> p.distSqr(centre));
		beds.sort(byDistance);
		BlockPos commons = bells.stream().min(byDistance).orElse(fires.stream().min(byDistance).orElse(null));
		boolean commonsFire = commons != null && fires.contains(commons);
		BlockPos kitchen = null;
		List<BlockPos> stoves = new ArrayList<>(cookers);
		stoves.addAll(fires);
		stoves.sort(byDistance);
		for (BlockPos stove : stoves) {
			if (chests.stream().anyMatch(chest -> chest.distSqr(stove) <= 16)) {
				kitchen = stove;
				break;
			}
		}
		BlockPos meet = commons != null ? commons : centre;
		BlockPos lectern = lecterns.stream().filter(p -> p.distSqr(meet) <= 64).min(Comparator.comparingDouble(p -> p.distSqr(meet))).orElse(null);
		return new Found(beds, commons, commonsFire, kitchen, farm(farmland, centre), patrol(level, centre), lectern, chests);
	}

	static boolean isChest(BlockState state) {
		return state.getBlock() instanceof ChestBlock || state.is(Blocks.BARREL);
	}

	/** The patch of farmland nearest home, grown outwards over gaps of up to three blocks, no more than {@link #MAX_FARM} across. */
	static @Nullable Zone farm(List<BlockPos> farmland, BlockPos centre) {
		if (farmland.isEmpty()) {
			return null;
		}
		List<BlockPos> left = new ArrayList<>(farmland);
		left.sort(Comparator.comparingDouble(p -> p.distSqr(centre)));
		BlockPos first = left.remove(0);
		int minX = first.getX();
		int maxX = first.getX();
		int minY = first.getY();
		int maxY = first.getY();
		int minZ = first.getZ();
		int maxZ = first.getZ();
		boolean grew = true;
		while (grew) {
			grew = false;
			for (int i = 0; i < left.size(); i++) {
				BlockPos p = left.get(i);
				boolean near = p.getX() >= minX - 3 && p.getX() <= maxX + 3 && p.getZ() >= minZ - 3 && p.getZ() <= maxZ + 3
						&& p.getY() >= minY - 1 && p.getY() <= maxY + 1;
				int nMinX = Math.min(minX, p.getX());
				int nMaxX = Math.max(maxX, p.getX());
				int nMinZ = Math.min(minZ, p.getZ());
				int nMaxZ = Math.max(maxZ, p.getZ());
				if (near && nMaxX - nMinX < MAX_FARM && nMaxZ - nMinZ < MAX_FARM) {
					minX = nMinX;
					maxX = nMaxX;
					minZ = nMinZ;
					maxZ = nMaxZ;
					minY = Math.min(minY, p.getY());
					maxY = Math.max(maxY, p.getY());
					left.remove(i--);
					grew = true;
				}
			}
		}
		// The farmland and the crops on it.
		return new Zone(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY + 1, maxZ));
	}

	/** Four points around home, about twelve blocks out, for a guardian's rounds. */
	static List<BlockPos> patrol(ServerLevel level, BlockPos centre) {
		List<BlockPos> points = new ArrayList<>();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos spot = standableNear(level, centre.relative(direction, 12), 6);
			if (spot != null) {
				points.add(spot);
			}
		}
		return points;
	}

	/** A spot to stand in the column at {@code pos}, within {@code dy} blocks up or down, or null. */
	static @Nullable BlockPos standableNear(Level level, BlockPos pos, int dy) {
		if (!level.hasChunkAt(pos)) {
			return null;
		}
		for (int d = 0; d <= dy; d++) {
			for (int sign : new int[] {1, -1}) {
				BlockPos at = pos.above(d * sign);
				if (standable(level, at)) {
					return at;
				}
				if (d == 0) {
					break;
				}
			}
		}
		return null;
	}

	static boolean standable(Level level, BlockPos pos) {
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
				&& level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
				&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
				&& level.getFluidState(pos).isEmpty();
	}
}

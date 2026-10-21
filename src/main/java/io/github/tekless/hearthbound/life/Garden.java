package io.github.tekless.hearthbound.life;

import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.Pastimes;
import io.github.tekless.hearthbound.world.WorldRules;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A companion's own little project, carried on over several days: a three by three flower bed near home,
 * one flower a day at most, using flowers they picked themselves. It only happens with autonomy on, only on
 * plain grass or dirt, never in a protected area and never beside anything a player built, and it stops at
 * nine flowers.
 */
public final class Garden {
	public static final int SIZE = 9;

	private Garden() {
	}

	/** A free three by three patch of grass or dirt near home for the bed, or null. The result is the flower level of one corner. */
	static @Nullable BlockPos chooseSite(ServerLevel level, BlockPos home) {
		for (int r = 4; r <= 10; r += 2) {
			for (int[] d : new int[][] {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
				BlockPos spot = PlaceScan.standableNear(level, home.offset(d[0] * r, 0, d[1] * r), 3);
				if (spot != null && suitable(level, spot)) {
					return spot;
				}
			}
		}
		return null;
	}

	private static boolean suitable(ServerLevel level, BlockPos corner) {
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				BlockPos flower = corner.offset(dx, 0, dz);
				BlockState ground = level.getBlockState(flower.below());
				if (!(ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT)) || !level.getBlockState(flower).isAir()
						|| !WorldRules.mayBreak(level, flower.below())) {
					return false;
				}
			}
		}
		return true;
	}

	/** The next empty spot in the bed, or null when it's full (or something else took the space). */
	static @Nullable BlockPos nextSpot(ServerLevel level, BlockPos corner) {
		for (int i = 0; i < SIZE; i++) {
			BlockPos flower = corner.offset(i % 3, 0, i / 3);
			BlockState ground = level.getBlockState(flower.below());
			if (level.getBlockState(flower).isAir() && (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT))) {
				return flower;
			}
		}
		return null;
	}

	static boolean isFlower(ItemStack stack) {
		return stack.is(BlockItemTags.SMALL_FLOWERS.item()) && stack.getItem() instanceof BlockItem;
	}

	static @Nullable ItemStack carriedFlower(CompanionEntity companion) {
		for (ItemStack stack : companion.inventory().getItems()) {
			if (!stack.isEmpty() && isFlower(stack)) {
				return stack;
			}
		}
		return null;
	}

	/** Walk to the bed and plant one flower from the pack. */
	static final class Plant implements Pastimes.Pastime {
		private final BlockPos pos;
		private final long day;
		private int ticks;

		Plant(BlockPos pos, long day) {
			this.pos = pos;
			this.day = day;
		}

		@Override
		public boolean tick(CompanionEntity c, @Nullable ServerPlayer owner) {
			ServerLevel level = (ServerLevel) c.level();
			if (++ticks > 400 || !level.getBlockState(pos).isAir() || WorldRules.isProtected(level, pos)) {
				return false;
			}
			c.getLookControl().setLookAt(Vec3.atCenterOf(pos));
			if (c.distanceToSqr(Vec3.atCenterOf(pos)) > 6.0) {
				if (ticks % 20 == 1) {
					c.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.7);
				}
				return true;
			}
			ItemStack flower = carriedFlower(c);
			if (flower == null) {
				return false;
			}
			BlockState state = ((BlockItem) flower.getItem()).getBlock().defaultBlockState();
			if (!state.canSurvive(level, pos)) {
				return false;
			}
			c.getNavigation().stop();
			c.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.setBlockAndUpdate(pos, state);
			flower.shrink(1);
			c.plantedInGarden(day);
			int planted = c.gardenPlanted();
			c.sayMaybe(planted >= SIZE ? "life.project.done" : "life.project.progress",
					Map.of("count", Integer.toString(planted), "max", Integer.toString(SIZE)), planted >= SIZE ? 1.0 : 0.5);
			return false;
		}
	}
}

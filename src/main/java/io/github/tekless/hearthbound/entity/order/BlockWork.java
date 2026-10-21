package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Breaking one block the way a player would: the best tool in hand, a believable speed for that
 * tool and block, crack marks and arm swings while working, then the real drops into the pack.
 */
final class BlockWork {
	enum Result { IN_PROGRESS, DONE, GONE }

	private final CompanionEntity companion;
	private BlockPos breakingPos;
	private float progress;
	private int lastStage = -1;
	private int swingCooldown;

	BlockWork(CompanionEntity companion) {
		this.companion = companion;
	}

	/** One tick of work on {@code target}. GONE means it's air now or can't be broken at all. */
	Result step(BlockPos target) {
		ServerLevel level = (ServerLevel) companion.level();
		BlockState state = level.getBlockState(target);
		if (state.isAir()) {
			clear();
			return Result.GONE;
		}
		if (!target.equals(breakingPos)) {
			clear();
			breakingPos = target.immutable();
			companion.holdBestToolFor(state);
		}
		companion.getLookControl().setLookAt(Vec3.atCenterOf(target));
		float hardness = state.getDestroySpeed(level, target);
		if (hardness < 0) {
			clear();
			return Result.GONE;
		}
		ItemStack tool = companion.getMainHandItem();
		boolean correct = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
		float speed = Math.max(1.0F, tool.getDestroySpeed(state));
		progress += hardness == 0 ? 1.0F : speed / hardness / (correct ? 30.0F : 100.0F);
		if (--swingCooldown <= 0) {
			swingCooldown = 5;
			companion.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}
		int stage = Math.min(9, (int) (progress * 10));
		if (stage != lastStage) {
			lastStage = stage;
			level.destroyBlockProgress(companion.getId(), target, stage);
		}
		if (progress < 1.0F) {
			return Result.IN_PROGRESS;
		}
		BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(target) : null;
		List<ItemStack> drops = Block.getDrops(state, level, target, blockEntity, companion, tool);
		level.destroyBlock(target, false, companion);
		if (!tool.isEmpty() && tool.isDamageableItem()) {
			tool.hurtAndBreak(1, companion, EquipmentSlot.MAINHAND);
		}
		for (ItemStack drop : drops) {
			companion.storeOrDrop(drop);
		}
		clear();
		return Result.DONE;
	}

	void clear() {
		if (breakingPos != null && companion.level() instanceof ServerLevel level) {
			level.destroyBlockProgress(companion.getId(), breakingPos, -1);
		}
		breakingPos = null;
		progress = 0;
		lastStage = -1;
	}
}

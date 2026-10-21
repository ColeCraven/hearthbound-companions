package io.github.tekless.hearthbound.world;

import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What to gather: which blocks to break and which dropped items count towards the goal.
 *
 * @param key           stable id used when saving the order ("logs", "minecraft:oak_log")
 * @param displayName   what companions call it ("oak logs")
 * @param blocks        blocks worth breaking
 * @param counts        dropped items that count towards the goal
 * @param defaultCount  amount used when the player didn't say how many
 * @param treesOnly     only break logs that belong to a natural tree (leaves nearby)
 */
public record GatherSpec(
		String key,
		String displayName,
		Predicate<BlockState> blocks,
		Predicate<ItemStack> counts,
		int defaultCount,
		boolean treesOnly) {
}

package io.github.tekless.hearthbound.life;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Where things are around one home, worked out from the config (at the hub), then what a player marked with
 * {@code /companion setplace}, then what was found nearby, and lastly home itself.
 *
 * @param commons     the meeting spot for evenings (a bell, a campfire, or home)
 * @param commonsFire the meeting spot is a campfire, lit at dusk
 * @param kitchen     where meals happen
 * @param farm        the crops the farmers look after, or null
 * @param farmMarked  the farm was marked by a player or the config, so the farmers may always work it
 * @param storage     chests the harvest may go into (and no others)
 * @param patrol      points the guardians walk between
 * @param spots       other places people like to wander to
 * @param lectern     an empty or diary lectern near the meeting spot, or null
 */
public record Places(BlockPos commons, boolean commonsFire, BlockPos kitchen, @Nullable Zone farm, boolean farmMarked,
		List<BlockPos> storage, List<BlockPos> patrol, List<BlockPos> spots, @Nullable BlockPos lectern) {
}

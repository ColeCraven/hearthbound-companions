package io.github.tekless.hearthbound.world;

import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.order.GoHomeOrder;
import io.github.tekless.hearthbound.entity.order.HomeOrder;
import io.github.tekless.hearthbound.entity.order.WaitOrder;
import net.minecraft.core.BlockPos;

/**
 * Companions' homes: where each one lives (saved with the companion), and sending them there on foot or
 * straight away. A companion who travels with someone stays that person's, parked at home; one who
 * isn't just goes back to their usual spot.
 */
public final class Homes {
	private Homes() {
	}

	/** Makes this spot their home. */
	public static void set(CompanionEntity companion, BlockPos pos) {
		companion.setHomePos(pos);
	}

	/**
	 * Sends the companion home.
	 *
	 * @param teleport go there at once instead of walking
	 * @return false if they have no home yet
	 */
	public static boolean send(CompanionEntity companion, boolean teleport) {
		BlockPos home = companion.home();
		if (home == null) {
			return false;
		}
		if (teleport) {
			companion.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
			companion.getNavigation().stop();
			companion.setOrder(companion.isRecruited() ? new WaitOrder(home) : new HomeOrder(home));
		} else {
			companion.setOrder(companion.isRecruited() ? new GoHomeOrder(home) : new HomeOrder(home));
		}
		return true;
	}
}

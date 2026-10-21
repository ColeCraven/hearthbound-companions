package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import net.minecraft.core.BlockPos;

/** After being dismissed: walk back to where the companion was recruited, then idle there. */
public final class HomeOrder implements CompanionOrder {
	private final BlockPos home;
	private int repathCooldown;
	private int ticks;

	public HomeOrder(BlockPos home) {
		this.home = home.immutable();
	}

	public BlockPos home() {
		return home;
	}

	@Override
	public Kind kind() {
		return Kind.HOME;
	}

	@Override
	public void tick(CompanionEntity companion) {
		double distSq = companion.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
		if (distSq < 16) {
			return;
		}
		ticks++;
		if (ticks > 6000) {
			// Too far or blocked: settle where we are instead of walking forever.
			return;
		}
		if (--repathCooldown <= 0 || companion.getNavigation().isDone()) {
			repathCooldown = 40;
			companion.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.8);
		}
	}

	@Override
	public void cancel(CompanionEntity companion) {
		companion.getNavigation().stop();
	}

	@Override
	public TaskStatus status() {
		return TaskStatus.RUNNING;
	}

	@Override
	public String describe() {
		return "heading home";
	}
}

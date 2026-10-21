package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import net.minecraft.core.BlockPos;

/**
 * A travelling companion walks back to their home and waits there (they stay with you, just parked at home).
 * If the way is blocked for too long they are taken the rest of the way.
 */
public final class GoHomeOrder implements CompanionOrder {
	private static final int TIMEOUT = 2400;
	private final BlockPos home;
	private TaskStatus status = TaskStatus.RUNNING;
	private int ticks;
	private int repathCooldown;

	public GoHomeOrder(BlockPos home) {
		this.home = home.immutable();
	}

	@Override
	public Kind kind() {
		return Kind.COME;
	}

	@Override
	public void tick(CompanionEntity companion) {
		if (status.isFinished()) {
			return;
		}
		boolean timedOut = ++ticks > TIMEOUT;
		if (timedOut || companion.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) <= 4.0) {
			if (timedOut) {
				companion.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
			}
			companion.getNavigation().stop();
			companion.setOrder(new WaitOrder(home));
			status = TaskStatus.SUCCEEDED;
			return;
		}
		if (--repathCooldown <= 0 || companion.getNavigation().isDone()) {
			repathCooldown = 20;
			companion.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.1);
		}
	}

	@Override
	public void cancel(CompanionEntity companion) {
		if (!status.isFinished()) {
			status = TaskStatus.CANCELLED;
		}
		companion.getNavigation().stop();
	}

	@Override
	public TaskStatus status() {
		return status;
	}

	@Override
	public String describe() {
		return "heading home";
	}
}

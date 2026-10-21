package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.task.FollowPolicy;
import io.github.tekless.hearthbound.core.task.StuckDetector;
import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import net.minecraft.server.level.ServerPlayer;

/** Stay with the owner: keep a personal distance, hurry when far, teleport when separated. */
public final class FollowOrder implements CompanionOrder {
	private final StuckDetector stuck = new StuckDetector(60, 0.75);
	private boolean moving;
	private int repathCooldown;

	@Override
	public Kind kind() {
		return Kind.FOLLOW;
	}

	@Override
	public void tick(CompanionEntity companion) {
		ServerPlayer owner = companion.ownerPlayer();
		if (owner == null || owner.isSpectator()) {
			companion.getNavigation().stop();
			moving = false;
			return;
		}
		boolean sameLevel = owner.level() == companion.level();
		double dx = owner.getX() - companion.getX();
		double dz = owner.getZ() - companion.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double vertical = Math.abs(owner.getY() - companion.getY());
		boolean isStuck = stuck.update(companion.getX(), companion.getZ(), moving);

		FollowPolicy.Action action = companion.followPolicy().decide(horizontal, vertical, sameLevel, moving, isStuck);
		switch (action) {
			case TELEPORT -> {
				moving = false;
				stuck.reset();
				companion.requestCatchUp(owner);
			}
			case HURRY -> walk(companion, owner, 1.35);
			case WALK -> walk(companion, owner, 1.0);
			case HOLD -> {
				if (moving) {
					companion.getNavigation().stop();
				}
				moving = false;
				companion.getLookControl().setLookAt(owner, 10.0F, companion.getMaxHeadXRot());
			}
		}
	}

	private void walk(CompanionEntity companion, ServerPlayer owner, double speed) {
		moving = true;
		companion.getLookControl().setLookAt(owner, 10.0F, companion.getMaxHeadXRot());
		if (--repathCooldown <= 0 || companion.getNavigation().isDone()) {
			repathCooldown = 10;
			companion.getNavigation().moveTo(owner, speed);
		} else {
			companion.getNavigation().setSpeedModifier(speed);
		}
	}

	@Override
	public void pause(CompanionEntity companion) {
		moving = false;
		stuck.reset();
		CompanionOrder.super.pause(companion);
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
		return "following you";
	}
}

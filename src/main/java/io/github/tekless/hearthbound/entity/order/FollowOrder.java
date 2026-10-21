package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.task.FollowPolicy;
import io.github.tekless.hearthbound.core.task.StuckDetector;
import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.Pastimes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Stay with the owner: keep a personal distance, hurry when far, teleport when separated. When the
 * owner stands still for a while, the companion may pick a pastime of its own (see {@link Pastimes}).
 */
public final class FollowOrder implements CompanionOrder {
	private final StuckDetector stuck = new StuckDetector(60, 0.75);
	private boolean moving;
	private int repathCooldown;
	private Pastimes.Pastime pastime;
	private Vec3 ownerAnchor;
	private int ownerStillTicks;
	private int pastimeCooldown = 300;

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
		if (pastime != null) {
			boolean ownerMoved = ownerAnchor != null && owner.position().distanceTo(ownerAnchor) > 2.0;
			if (!sameLevel || ownerMoved || horizontal > companion.archetype().followMax() + 7 || !pastime.tick(companion, owner)) {
				endPastime(companion);
			} else {
				return;
			}
		}
		if (ownerAnchor == null || owner.position().distanceTo(ownerAnchor) > 1.0) {
			ownerAnchor = owner.position();
			ownerStillTicks = 0;
		} else {
			ownerStillTicks++;
		}
		if (pastimeCooldown > 0) {
			pastimeCooldown--;
		}
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
				double autonomy = Hearthbound.config().autonomy;
				if (autonomy > 0 && ownerStillTicks > 100 && pastimeCooldown <= 0 && companion.getTarget() == null) {
					pastimeCooldown = cooldown(companion, autonomy);
					pastime = Pastimes.pick(companion, owner).orElse(null);
				}
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

	private void endPastime(CompanionEntity companion) {
		pastime.stop(companion);
		pastime = null;
		ownerStillTicks = 0;
		pastimeCooldown = cooldown(companion, Hearthbound.config().autonomy);
	}

	private static int cooldown(CompanionEntity companion, double autonomy) {
		return (int) ((600 + companion.getRandom().nextInt(900)) / Math.max(0.25, autonomy));
	}

	/** True while the companion is busy with a pastime of its own choosing. */
	public boolean onPastime() {
		return pastime != null;
	}

	@Override
	public void pause(CompanionEntity companion) {
		if (pastime != null) {
			endPastime(companion);
		}
		moving = false;
		stuck.reset();
		CompanionOrder.super.pause(companion);
	}

	@Override
	public void cancel(CompanionEntity companion) {
		if (pastime != null) {
			endPastime(companion);
		}
		companion.getNavigation().stop();
	}

	@Override
	public TaskStatus status() {
		return TaskStatus.RUNNING;
	}

	@Override
	public String describe() {
		return pastime != null ? "following you (pottering about)" : "following you";
	}
}

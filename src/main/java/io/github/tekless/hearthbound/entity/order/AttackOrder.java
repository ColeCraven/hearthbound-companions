package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;

/** Fight a specific creature the owner pointed at. Ends when it dies or gets away. */
public final class AttackOrder implements CompanionOrder {
	private final LivingEntity target;
	private final String targetName;
	private TaskStatus status = TaskStatus.RUNNING;

	public AttackOrder(LivingEntity target, String targetName) {
		this.target = target;
		this.targetName = targetName;
	}

	@Override
	public Kind kind() {
		return Kind.ATTACK;
	}

	@Override
	public void tick(CompanionEntity companion) {
		if (status.isFinished()) {
			return;
		}
		if (!target.isAlive()) {
			status = TaskStatus.SUCCEEDED;
			companion.say("combat.kill", Map.of("mob", targetName));
			return;
		}
		if (target.isRemoved() || target.level() != companion.level() || companion.distanceTo(target) > 40) {
			status = TaskStatus.FAILED;
			companion.say("fail.no_target", Map.of("mob", targetName));
			return;
		}
		if (companion.getTarget() != target) {
			companion.setTarget(target);
		}
		// The melee goal (higher priority) does the actual chasing and hitting while a target is set.
	}

	@Override
	public void cancel(CompanionEntity companion) {
		if (!status.isFinished()) {
			status = TaskStatus.CANCELLED;
		}
		if (companion.getTarget() == target) {
			companion.setTarget(null);
		}
	}

	@Override
	public TaskStatus status() {
		return status;
	}

	@Override
	public String describe() {
		return "fighting the " + targetName;
	}
}

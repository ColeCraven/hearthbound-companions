package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.task.StuckDetector;
import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import net.minecraft.server.level.ServerPlayer;

/** Walk to the owner once; then the companion returns to its standing mode. */
public final class ComeOrder implements CompanionOrder {
	private static final int TIMEOUT = 1200;

	private final StuckDetector stuck = new StuckDetector(60, 0.75);
	private TaskStatus status = TaskStatus.RUNNING;
	private int ticks;
	private int repathCooldown;
	/** What to do on arrival, for orders given to a companion who was far away ("gather wood near me"). */
	private final java.util.function.Consumer<CompanionEntity> then;

	public ComeOrder() {
		this(null);
	}

	public ComeOrder(java.util.function.Consumer<CompanionEntity> then) {
		this.then = then;
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
		ServerPlayer owner = companion.ownerPlayer();
		if (owner == null) {
			status = TaskStatus.FAILED;
			return;
		}
		if (owner.level() != companion.level() || companion.distanceTo(owner) > 32) {
			companion.requestCatchUp(owner);
			return;
		}
		if (companion.distanceTo(owner) <= 2.5) {
			companion.getNavigation().stop();
			status = TaskStatus.SUCCEEDED;
			if (then != null) {
				then.accept(companion);
			}
			return;
		}
		if (then != null && ++ticks > TIMEOUT) {
			ticks = 0; // there is a job waiting at the other end, so keep trying (the teleport does the rest)
			companion.requestCatchUp(owner);
			return;
		}
		if (++ticks > TIMEOUT || (stuck.update(companion.getX(), companion.getZ(), true) && stuck.consecutiveStuck() >= 3)) {
			// Couldn't walk there: catching up by teleport is better than standing around.
			companion.requestCatchUp(owner);
			status = TaskStatus.SUCCEEDED;
			return;
		}
		if (--repathCooldown <= 0 || companion.getNavigation().isDone()) {
			repathCooldown = 10;
			companion.getNavigation().moveTo(owner, 1.25);
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
		return "coming to you";
	}
}

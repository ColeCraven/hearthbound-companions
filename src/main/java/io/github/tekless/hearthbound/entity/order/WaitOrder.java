package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import net.minecraft.core.BlockPos;

/** Hold a position. After a fight or being pushed, walk back to it. */
public final class WaitOrder implements CompanionOrder {
	private final BlockPos post;
	private int repathCooldown;

	public WaitOrder(BlockPos post) {
		this.post = post.immutable();
	}

	public BlockPos post() {
		return post;
	}

	@Override
	public Kind kind() {
		return Kind.WAIT;
	}

	@Override
	public void tick(CompanionEntity companion) {
		double distSq = companion.distanceToSqr(post.getX() + 0.5, post.getY(), post.getZ() + 0.5);
		if (distSq > 2.25) {
			if (--repathCooldown <= 0 || companion.getNavigation().isDone()) {
				repathCooldown = 20;
				companion.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 1.0);
			}
		} else if (!companion.getNavigation().isDone()) {
			companion.getNavigation().stop();
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
		return "waiting here";
	}
}

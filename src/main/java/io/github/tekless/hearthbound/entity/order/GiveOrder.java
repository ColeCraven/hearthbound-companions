package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** Walk to the owner and hand over carried items (all of them, or one kind). */
public final class GiveOrder implements CompanionOrder {
	private static final int TIMEOUT = 1200;

	private final Predicate<ItemStack> filter;
	private final String itemName;
	private final int max;
	private TaskStatus status = TaskStatus.RUNNING;
	private int ticks;
	private int repathCooldown;

	public GiveOrder(Predicate<ItemStack> filter, String itemName, int max) {
		this.filter = filter;
		this.itemName = itemName;
		this.max = max;
	}

	@Override
	public Kind kind() {
		return Kind.GIVE;
	}

	@Override
	public void tick(CompanionEntity companion) {
		if (status.isFinished()) {
			return;
		}
		ServerPlayer owner = companion.commanderPlayer();
		if (owner == null) {
			status = TaskStatus.FAILED;
			return;
		}
		if (owner.level() != companion.level() || companion.distanceTo(owner) > 32) {
			companion.requestCatchUp(owner);
			return;
		}
		if (companion.distanceTo(owner) > 3.0) {
			if (++ticks > TIMEOUT) {
				companion.requestCatchUp(owner);
			}
			if (--repathCooldown <= 0 || companion.getNavigation().isDone()) {
				repathCooldown = 10;
				companion.getNavigation().moveTo(owner, 1.2);
			}
			return;
		}
		companion.getNavigation().stop();
		companion.getLookControl().setLookAt(owner);
		int given = companion.giveTo(owner, filter, max);
		if (given > 0) {
			companion.level().playSound(null, owner.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.1F);
			companion.say("ack.give", Map.of("count", Integer.toString(given), "item", itemName));
		} else {
			companion.say("ack.give.nothing", Map.of());
		}
		status = TaskStatus.SUCCEEDED;
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
		return "bringing you " + itemName;
	}
}

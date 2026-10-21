package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.world.HubResidents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Not travelling with anyone: walk back home (where they were recruited, or the hub), then hang
 * around there - strolling a little, turning to look at people who come close.
 */
public final class HomeOrder implements CompanionOrder {
	private final BlockPos home;
	private int repathCooldown;
	private int ticks;
	private int idleCooldown = 60;

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
		int radius = companion.homeRadius();
		double distSq = companion.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
		if (distSq > (double) radius * radius) {
			walkHome(companion);
			return;
		}
		ticks = 0;
		if (!companion.hasHome()) {
			companion.setHomeTo(home, radius); // keeps random strolls near home
		}
		if (!companion.getNavigation().isDone()) {
			return;
		}
		Player watched = companion.level().getNearestPlayer(companion, 8.0);
		if (watched != null && !watched.isSpectator()) {
			companion.getLookControl().setLookAt(watched);
		}
		if (--idleCooldown <= 0) {
			idleCooldown = 120 + companion.getRandom().nextInt(300);
			// Someone standing right here talking? Stay put for them.
			if (watched != null && watched.distanceTo(companion) < 4) {
				return;
			}
			Vec3 spot = LandRandomPos.getPos(companion, Math.max(2, radius / 2), 3);
			if (spot != null && spot.distanceToSqr(Vec3.atBottomCenterOf(home)) <= radius * radius) {
				companion.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.5);
			}
		}
	}

	private void walkHome(CompanionEntity companion) {
		ticks++;
		if (ticks > 6000) {
			// Blocked or too far to walk: hub residents find their own way back, others settle here.
			if (companion.isHubResident() && companion.level() instanceof ServerLevel) {
				ticks = 0;
				HubResidents.sendHome(companion);
			}
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

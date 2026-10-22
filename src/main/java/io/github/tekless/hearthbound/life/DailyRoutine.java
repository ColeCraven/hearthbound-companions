package io.github.tekless.hearthbound.life;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.core.life.Activity;
import io.github.tekless.hearthbound.core.life.Needs;
import io.github.tekless.hearthbound.core.life.Schedule;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.core.persona.Role;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.Pastimes;
import io.github.tekless.hearthbound.entity.order.Routines;
import io.github.tekless.hearthbound.world.WorldRules;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One companion's day at home, run from their {@code HomeOrder}: every few seconds they look at the clock (see
 * {@link Schedule}) and decide what they should be doing, then spend the time in between getting there and doing
 * it. Asleep in their own bed at night, up at dawn, at work by role in the morning, a meal at midday, work or
 * leisure in the afternoon, the evening together at the meeting spot, and indoors when it rains.
 *
 * <p>Cheap by design: one decision every three seconds, no searching every tick, the usual pathing, and any
 * work that changes the world goes through the same limits as autonomy (protected areas, player builds, the
 * per-minute cap). Explicit orders always replace this, and it comes back when they're done.
 */
public final class DailyRoutine {
	private static final int DECIDE_EVERY = 60;
	/** Close enough to where they're going. */
	private static final double ARRIVED = 2.5;

	private @Nullable Activity activity;
	private int decideIn;
	private @Nullable BlockPos target;
	private @Nullable BlockPos bed;
	private int repath;
	private int travelTicks;
	private Pastimes.@Nullable Pastime job;
	private int jobCooldown = 100;
	private int patrolIndex;
	private int patrolPause;
	private boolean ate;
	private boolean fresh = true;
	private boolean saidBedTaken;
	private int idleCooldown;

	public DailyRoutine(CompanionEntity companion) {
		// Spread decisions out so a whole hub doesn't think on the same tick.
		this.decideIn = 1 + Math.floorMod(companion.getUUID().hashCode(), DECIDE_EVERY);
	}

	/** Daily life applies to companions who aren't travelling with anyone, while it is switched on. */
	public static boolean enabledFor(CompanionEntity companion) {
		return companion.dailyLifeEnabled() && !companion.isRecruited() && companion.level() instanceof ServerLevel;
	}

	public @Nullable Activity activity() {
		return activity;
	}

	public String describe() {
		if (activity == null) {
			return "at home";
		}
		if (job instanceof Garden.Plant) {
			return "planting my flower bed";
		}
		return activity.describe();
	}

	/** One tick of the day. */
	public void tick(CompanionEntity c, BlockPos home) {
		ServerLevel level = (ServerLevel) c.level();
		HomeAreas areas = HomeAreas.get(level.getServer());
		HomeAreas.Area area = areas.areaOf(c);
		if (c.isBeingTalkedTo()) {
			// Someone is talking to them: stop and listen (and wake up for it).
			if (c.isSleeping()) {
				c.wakeUp();
			}
			c.getNavigation().stop();
			return;
		}
		if (--decideIn <= 0) {
			decideIn = DECIDE_EVERY;
			decide(c, level, areas, area, home);
		}
		if (activity == null) {
			return;
		}
		if (job != null) {
			if (job.tick(c, null)) {
				return;
			}
			job.stop(c);
			job = null;
			jobCooldown = 200 + c.getRandom().nextInt(400);
		}
		if (jobCooldown > 0) {
			jobCooldown--;
		}
		if (activity == Activity.SLEEP) {
			sleep(c, level, home);
			return;
		}
		Places places = areas.places(level, area);
		if (activity == Activity.WORK && c.role() == Role.GUARDIAN && !places.patrol().isEmpty()) {
			patrol(c, places.patrol());
			return;
		}
		if (target == null) {
			return;
		}
		if (c.distanceToSqr(Vec3.atBottomCenterOf(target)) > ARRIVED * ARRIVED) {
			walk(c, level, target, 0.7);
			return;
		}
		travelTicks = 0;
		switch (activity) {
			case MEAL -> eat(c);
			case WORK -> work(c, level, areas, area, places, home);
			case LEISURE -> leisure(c, level, places, home);
			default -> {
			}
		}
		idle(c, level, places);
	}

	/** Ends the day's business (an order came in, or they joined someone). */
	public void stop(CompanionEntity c) {
		if (job != null) {
			job.stop(c);
			job = null;
		}
		if (c.isSleeping()) {
			c.stopSleeping();
		}
		c.setOnWatch(false);
	}

	// ------------------------------------------------------------------ deciding

	private void decide(CompanionEntity c, ServerLevel level, HomeAreas areas, HomeAreas.Area area, BlockPos home) {
		long clock = c.clockTime();
		long dayTime = Math.floorMod(clock, (long) Schedule.DAY);
		long day = clock / Schedule.DAY;
		Needs needs = c.needs();
		boolean company = hasCompany(c, level);
		if (activity != null) {
			needs.pass(activity, DECIDE_EVERY / 20, company);
		}
		Schedule.Conditions conditions = new Schedule.Conditions(level.isRaining(), Schedule.fullMoon(clock), needs.hunger(), needs.tiredness(),
				needs.loneliness());
		Activity next = Schedule.at(dayTime, day, c.lifeTraits(), conditions);
		if (next == Activity.SLEEP && c.awakeUntil() > level.getGameTime()) {
			next = Activity.LEISURE; // someone woke them; they'll go back to bed in a bit
		}
		Places places = areas.places(level, area);
		if (next != activity) {
			switchTo(c, level, areas, area, places, home, next);
		} else if (activity == Activity.LEISURE || activity == Activity.WORK && c.role() != Role.GUARDIAN) {
			// A change of scene now and then during the long parts of the day.
			if (job == null && c.getRandom().nextInt(6) == 0) {
				target = activity == Activity.WORK ? workPlace(c, level, places, home) : leisurePlace(c, level, places, home);
			}
		}
		if (activity == Activity.WORK || activity == Activity.LEISURE) {
			Player near = level.getNearestPlayer(c, 5.0);
			if (near instanceof ServerPlayer player && !player.isSpectator() && c.getRandom().nextInt(15) == 0) {
				Favours.maybeAsk(c, player, level.getGameTime());
			}
		}
	}

	private void switchTo(CompanionEntity c, ServerLevel level, HomeAreas areas, HomeAreas.Area area, Places places, BlockPos home, Activity next) {
		Activity previous = activity;
		activity = next;
		if (job != null) {
			job.stop(c);
			job = null;
		}
		c.setOnWatch(false);
		ate = false;
		travelTicks = 0;
		patrolPause = 0;
		saidBedTaken = false;
		if (previous == Activity.SLEEP && c.isSleeping()) {
			c.stopSleeping();
		}
		target = switch (next) {
			case SLEEP -> {
				bed = areas.bedFor(level, area, c);
				if (bed == null) {
					Initiative.noBed(c, level, c.clockTime() / Schedule.DAY);
				}
				// No bed of their own: somewhere dry near home will have to do.
				yield bed != null ? bed : shelter(level, places, home, c);
			}
			case WAKE -> c.blockPosition();
			case WORK -> workPlace(c, level, places, home);
			case MEAL -> standingSpot(level, places.kitchen(), c, 2);
			case LEISURE -> leisurePlace(c, level, places, home);
			case SOCIAL -> socialSpot(level, areas, area, places, c);
			case SHELTER -> shelter(level, places, home, c);
		};
		if (fresh) {
			fresh = false;
			// Just loaded at night (someone came back after a while): they're in bed already, not wandering to it.
			if (next == Activity.SLEEP && bed != null && level.getNearestPlayer(c, 24.0) == null && HomeAreas.isBedHead(level, bed)) {
				c.teleportTo(bed.getX() + 0.5, bed.getY() + 0.6, bed.getZ() + 0.5);
			}
		} else if (previous != null && level.getNearestPlayer(c, 16.0) != null) {
			c.sayMaybe("life." + next.id(), Map.of(), 0.2 * c.needs().chattiness());
		}
	}

	private boolean hasCompany(CompanionEntity c, ServerLevel level) {
		if (level.getNearestPlayer(c, 8.0) != null) {
			return true;
		}
		return !level.getEntitiesOfClass(CompanionEntity.class, new AABB(c.blockPosition()).inflate(8), o -> o != c && o.isAlive()).isEmpty();
	}

	// ------------------------------------------------------------------ places for each part of the day

	private BlockPos workPlace(CompanionEntity c, ServerLevel level, Places places, BlockPos home) {
		return switch (c.role()) {
			case FARMER -> places.farm() != null ? farmTile(level, places.farm(), c) : standingSpot(level, home, c, 3);
			case GUARDIAN -> places.patrol().isEmpty() ? standingSpot(level, places.commons(), c, 4) : places.patrol().get(0);
			case BUILDER -> standingSpot(level, !places.storage().isEmpty() ? places.storage().get(0) : places.kitchen(), c, 3);
			case COLLECTOR -> !places.spots().isEmpty() ? standingSpot(level, places.spots().get(c.getRandom().nextInt(places.spots().size())), c, 3)
					: standingSpot(level, home, c, 6);
		};
	}

	private BlockPos leisurePlace(CompanionEntity c, ServerLevel level, Places places, BlockPos home) {
		int roll = c.getRandom().nextInt(4);
		if (roll == 0 && !places.spots().isEmpty()) {
			return standingSpot(level, places.spots().get(c.getRandom().nextInt(places.spots().size())), c, 3);
		}
		if (roll == 1) {
			return standingSpot(level, places.commons(), c, 4);
		}
		return standingSpot(level, home, c, 5);
	}

	/** A place in a ring round the meeting spot, so they gather facing each other instead of in a heap. */
	private BlockPos socialSpot(ServerLevel level, HomeAreas areas, HomeAreas.Area area, Places places, CompanionEntity c) {
		List<CompanionEntity> residents = areas.residents(area);
		residents.sort(java.util.Comparator.comparing(CompanionEntity::getUUID));
		int index = Math.max(0, residents.indexOf(c));
		int radius = places.commonsFire() ? 3 : 4;
		double angle = Math.PI * 2 * index / Math.max(4, residents.size());
		BlockPos around = places.commons().offset((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius));
		BlockPos spot = PlaceScan.standableNear(level, around, 3);
		return spot != null ? spot : standingSpot(level, places.commons(), c, 3);
	}

	/** Somewhere with a roof: their bed, the kitchen, storage, the meeting spot or home if one is covered, else a covered spot nearby. */
	private BlockPos shelter(ServerLevel level, Places places, BlockPos home, CompanionEntity c) {
		List<BlockPos> candidates = new java.util.ArrayList<>();
		if (bed != null) {
			candidates.add(bed);
		}
		candidates.add(places.kitchen());
		candidates.addAll(places.storage());
		candidates.add(places.commons());
		candidates.add(home);
		for (BlockPos candidate : candidates) {
			BlockPos spot = PlaceScan.standableNear(level, candidate, 2);
			if (spot == null) {
				for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
					spot = PlaceScan.standableNear(level, candidate.relative(side), 2);
					if (spot != null) {
						break;
					}
				}
			}
			if (spot != null && !level.canSeeSky(spot)) {
				return spot;
			}
		}
		for (int r = 3; r <= 12; r += 3) {
			for (int i = 0; i < 8; i++) {
				double angle = Math.PI * 2 * i / 8;
				BlockPos spot = PlaceScan.standableNear(level, home.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r)), 4);
				if (spot != null && !level.canSeeSky(spot)) {
					return spot;
				}
			}
		}
		return standingSpot(level, home, c, 0); // nowhere dry: they put up with it
	}

	private BlockPos farmTile(ServerLevel level, Zone farm, CompanionEntity c) {
		for (int tries = 0; tries < 6; tries++) {
			BlockPos column = new BlockPos(farm.low().getX() + c.getRandom().nextInt(farm.high().getX() - farm.low().getX() + 1), farm.high().getY(),
					farm.low().getZ() + c.getRandom().nextInt(farm.high().getZ() - farm.low().getZ() + 1));
			BlockPos spot = PlaceScan.standableNear(level, column, 2);
			if (spot != null) {
				return spot;
			}
		}
		BlockPos spot = PlaceScan.standableNear(level, farm.centre(), 3);
		return spot != null ? spot : farm.centre().above();
	}

	/** A spot to stand near a place (a bell or a furnace is a block, not a floor), spread out a little per companion. */
	private BlockPos standingSpot(ServerLevel level, BlockPos near, CompanionEntity c, int spread) {
		for (int tries = 0; tries < 4; tries++) {
			int dx = spread == 0 ? 0 : c.getRandom().nextInt(spread * 2 + 1) - spread;
			int dz = spread == 0 ? 0 : c.getRandom().nextInt(spread * 2 + 1) - spread;
			BlockPos spot = PlaceScan.standableNear(level, near.offset(dx, 0, dz), 3);
			if (spot != null) {
				return spot;
			}
		}
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			BlockPos spot = PlaceScan.standableNear(level, near.relative(side), 3);
			if (spot != null) {
				return spot;
			}
		}
		return near;
	}

	// ------------------------------------------------------------------ doing it

	private void walk(CompanionEntity c, ServerLevel level, BlockPos to, double speed) {
		if (--repath <= 0 || c.getNavigation().isDone()) {
			repath = 40;
			c.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, speed);
		}
		// A minute without getting there, and nobody watching: they found another way round.
		if (++travelTicks > 1200 && level.getNearestPlayer(c, 24.0) == null && PlaceScan.standable(level, to)) {
			travelTicks = 0;
			c.getNavigation().stop();
			c.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
		}
	}

	private void sleep(CompanionEntity c, ServerLevel level, BlockPos home) {
		if (c.isSleeping()) {
			return;
		}
		if (bed == null || !HomeAreas.isBedHead(level, bed)) {
			// No bed of their own: they doze at home, standing, head down.
			BlockPos spot = target != null ? target : home;
			if (c.distanceToSqr(Vec3.atBottomCenterOf(spot)) > ARRIVED * ARRIVED) {
				walk(c, level, spot, 0.6);
			} else {
				c.getNavigation().stop();
				c.getLookControl().setLookAt(c.getX(), c.getY() - 1, c.getZ() + 1);
			}
			return;
		}
		if (c.position().distanceTo(Vec3.atBottomCenterOf(bed)) > 1.8) {
			walk(c, level, bed, 0.6);
			return;
		}
		BlockState state = level.getBlockState(bed);
		if (state.getValue(BedBlock.OCCUPIED)) {
			if (!saidBedTaken) {
				saidBedTaken = true;
				c.sayMaybe("life.bed_taken", Map.of(), 0.6);
			}
			bed = null;
			target = home;
			return;
		}
		c.getNavigation().stop();
		c.startSleeping(bed);
	}

	private void eat(CompanionEntity c) {
		if (ate) {
			return;
		}
		ate = true;
		for (ItemStack stack : c.inventory().getItems()) {
			if (!stack.isEmpty() && stack.get(DataComponents.FOOD) != null && !stack.is(Items.ROTTEN_FLESH) && !stack.is(Items.SPIDER_EYE)
					&& !stack.is(Items.POISONOUS_POTATO) && !stack.is(Items.PUFFERFISH)) {
				stack.shrink(1); // their own food: it's gone once eaten
				break;
			}
		}
		c.needs().eat();
		c.getNavigation().stop();
		c.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
	}

	private void work(CompanionEntity c, ServerLevel level, HomeAreas areas, HomeAreas.Area area, Places places, BlockPos home) {
		if (job != null || jobCooldown > 0) {
			return;
		}
		jobCooldown = 60;
		boolean autonomy = Routines.enabled(c);
		long day = c.clockTime() / Schedule.DAY;
		// What isn't there yet in a new world: a roof, a farm. Started at most every few days, or just mentioned.
		if (Initiative.shelter(c, level, area, home, day) || Initiative.farm(c, level, places, home, day)) {
			return;
		}
		if (c.role() == Role.FARMER && places.farm() != null) {
			// Their own crops: always in a farm someone marked for them, and in one they found only with autonomy on.
			if (places.farmMarked() || autonomy) {
				BlockPos chest = Storage.nearestChest(c, places.storage());
				if (chest != null && Storage.surplus(c) >= 16) {
					job = new Storage.Deposit(chest);
					return;
				}
				BlockPos ripe = ripeCrop(level, places.farm(), c, places.farmMarked());
				if (ripe != null && Routines.hasAllowance(c) && c.hasRoomFor(s -> s.is(Items.WHEAT))) {
					job = Routines.harvestAt(ripe);
					return;
				}
				if (chest != null && Storage.surplus(c) > 0 && c.getRandom().nextInt(4) == 0) {
					job = new Storage.Deposit(chest);
					return;
				}
				if (chest == null && Storage.surplus(c) >= 16) {
					Initiative.noStorage(c, level, day);
				}
			}
			target = farmTile(level, places.farm(), c);
			return;
		}
		if (autonomy) {
			job = Routines.pickAround(c, home).orElse(null);
		}
	}

	private void leisure(CompanionEntity c, ServerLevel level, Places places, BlockPos home) {
		if (job != null || jobCooldown > 0) {
			return;
		}
		jobCooldown = 100;
		if (!Routines.enabled(c)) {
			return;
		}
		// Their own little project: a flower bed, a flower a day.
		long day = c.clockTime() / Schedule.DAY;
		if (c.gardenPlanted() < Garden.SIZE && c.gardenDay() != day) {
			BlockPos corner = c.gardenAt();
			if (corner == null) {
				corner = Garden.chooseSite(level, home);
				if (corner != null) {
					c.setGardenAt(corner);
				}
			}
			if (corner != null) {
				ItemStack flower = Garden.carriedFlower(c);
				BlockPos spot = Garden.nextSpot(level, corner);
				if (flower != null && spot != null) {
					job = new Garden.Plant(spot, day);
					return;
				}
				BlockPos wild = wildFlower(level, c, home);
				if (flower == null && wild != null) {
					job = Pastimes.pickFlower(wild);
					return;
				}
			}
		}
		if (c.archetype() == Archetype.NATURALIST || c.archetype() == Archetype.MYSTIC) {
			job = Routines.pickAround(c, home).orElse(null);
		}
	}

	/** A wild flower within reach of home (one look at a small box). */
	private static @Nullable BlockPos wildFlower(ServerLevel level, CompanionEntity c, BlockPos home) {
		for (int tries = 0; tries < 24; tries++) {
			BlockPos pos = home.offset(c.getRandom().nextInt(25) - 12, c.getRandom().nextInt(7) - 3, c.getRandom().nextInt(25) - 12);
			if (level.hasChunkAt(pos) && level.getBlockState(pos).is(BlockTags.SMALL_FLOWERS) && Pastimes.isWild(level, pos)
					&& !WorldRules.isProtected(level, pos)) {
				return pos;
			}
		}
		return null;
	}

	/** The ripe crop in the farm nearest the companion, or null. Only crops inside the farm, and not beside player builds unless it was marked for them. */
	private static @Nullable BlockPos ripeCrop(ServerLevel level, Zone farm, CompanionEntity c, boolean marked) {
		if (farm.volume() > 4096) {
			return null;
		}
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : farm.blocks()) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && (marked || !WorldRules.isProtected(level, pos))) {
				double distance = c.distanceToSqr(Vec3.atCenterOf(pos));
				if (distance < bestDistance) {
					best = pos.immutable();
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	private void patrol(CompanionEntity c, List<BlockPos> points) {
		c.setOnWatch(true);
		BlockPos point = points.get(Math.floorMod(patrolIndex, points.size()));
		if (patrolPause > 0) {
			if (--patrolPause == 0) {
				patrolIndex++;
			}
			// Looking outwards, away from home.
			Vec3 out = Vec3.atCenterOf(point).add(Vec3.atCenterOf(point).subtract(c.position()).normalize().scale(4));
			c.getLookControl().setLookAt(out.x, c.getEyeY(), out.z);
			return;
		}
		if (c.distanceToSqr(Vec3.atBottomCenterOf(point)) <= 4.0) {
			c.getNavigation().stop();
			patrolPause = 60 + c.getRandom().nextInt(60);
			return;
		}
		walk(c, (ServerLevel) c.level(), point, 0.6);
	}

	/** At their spot: turn to people nearby, and shuffle about a little now and then. */
	private void idle(CompanionEntity c, ServerLevel level, Places places) {
		if (!c.getNavigation().isDone()) {
			return;
		}
		Player watched = level.getNearestPlayer(c, 8.0);
		if (watched != null && !watched.isSpectator()) {
			c.getLookControl().setLookAt(watched);
		} else if (activity == Activity.SOCIAL) {
			c.getLookControl().setLookAt(Vec3.atCenterOf(places.commons()));
		}
		if (--idleCooldown <= 0) {
			idleCooldown = 200 + c.getRandom().nextInt(400);
			if (activity == Activity.LEISURE || activity == Activity.WORK) {
				Vec3 spot = net.minecraft.world.entity.ai.util.LandRandomPos.getPos(c, 3, 2);
				if (spot != null && target != null && spot.distanceToSqr(Vec3.atBottomCenterOf(target)) <= 25) {
					c.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.5);
				}
			}
		}
	}
}

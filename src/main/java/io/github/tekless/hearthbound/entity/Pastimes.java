package io.github.tekless.hearthbound.entity;

import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.world.GatherCatalog;
import io.github.tekless.hearthbound.world.WorldRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Things companions choose to do by themselves while the player stands still - their "free will".
 * Every pastime is a real action in the world (walking over to a flower, picking up loot left on
 * the ground, petting a cow, walking a guard circuit), picked to fit the personality, kept close to
 * the player, and dropped the moment the player moves on or gives an order.
 */
public final class Pastimes {
	private static final int RANGE = 9;
	private static final int MAX_TICKS = 260;

	/** One activity in progress. */
	public interface Pastime {
		/** Advances the activity; returns false when it is finished. */
		boolean tick(CompanionEntity companion, ServerPlayer owner);

		default void stop(CompanionEntity companion) {
			companion.getNavigation().stop();
		}
	}

	private Pastimes() {
	}

	/** Picks something fitting to do now, if anything is available nearby. */
	public static Optional<Pastime> pick(CompanionEntity companion, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) companion.level();
		List<Pastime> options = new ArrayList<>();
		Archetype archetype = companion.archetype();
		boolean night = level.isDarkOutside();
		switch (archetype) {
			case SELLSWORD -> {
				// Charlie picks herbs for his pipe whenever he spots some.
				if (companion.castMember().filter(m -> m == io.github.tekless.hearthbound.core.persona.CastMember.CHARLIE).isPresent()) {
					findBlock(level, owner, s -> s.is(BlockTags.SMALL_FLOWERS)).filter(pos -> isWild(level, pos)).ifPresent(pos -> {
						options.add(new PickFlower(pos, "pastime.herbs"));
						options.add(new PickFlower(pos, "pastime.herbs"));
					});
				}
				options.add(new Patrol(owner));
				options.add(new Practice());
				lootNearby(level, companion, owner, 1200).ifPresent(options::add);
			}
			case NATURALIST -> {
				findBlock(level, owner, s -> s.is(BlockTags.SMALL_FLOWERS)).ifPresent(pos -> {
					options.add(new Study(pos, null, name(level.getBlockState(pos))));
					if (isWild(level, pos)) {
						options.add(new PickFlower(pos));
					}
				});
				findAnimal(level, owner).ifPresent(animal -> options.add(new Study(null, animal, animal.getName().getString().toLowerCase(Locale.ROOT))));
				findBlock(level, owner, s -> s.is(BlockTags.LEAVES) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.MUSHROOM_STEM) || s.is(BlockTags.LOGS))
						.ifPresent(pos -> options.add(new Study(pos, null, name(level.getBlockState(pos)))));
			}
			case ROGUE -> {
				lootNearby(level, companion, owner, 300).ifPresent(options::add);
				lootNearby(level, companion, owner, 300).ifPresent(options::add);
				findBlock(level, owner, s -> s.is(Blocks.CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.GOLD_BLOCK) || s.is(Blocks.BELL))
						.ifPresent(pos -> options.add(new Study(pos, null, name(level.getBlockState(pos)))));
				options.add(new Patrol(owner));
			}
			case GUARDIAN -> {
				options.add(new Patrol(owner));
				options.add(new Patrol(owner));
				lootNearby(level, companion, owner, 1200).ifPresent(options::add);
			}
			case FARMHAND -> {
				findAnimal(level, owner).ifPresent(animal -> options.add(new Pet(animal)));
				findBlock(level, owner, s -> s.is(BlockTags.SMALL_FLOWERS)).filter(pos -> isWild(level, pos)).ifPresent(pos -> options.add(new PickFlower(pos)));
				findBlock(level, owner, s -> s.is(BlockTags.CROPS) || s.is(Blocks.HAY_BLOCK) || s.is(Blocks.BEEHIVE) || s.is(Blocks.BEE_NEST))
						.ifPresent(pos -> options.add(new Study(pos, null, name(level.getBlockState(pos)))));
				lootNearby(level, companion, owner, 1200).ifPresent(options::add);
			}
			case MYSTIC -> {
				if (night && level.canSeeSky(companion.blockPosition())) {
					options.add(new Stargaze());
					options.add(new Stargaze());
				}
				findBlock(level, owner, s -> s.is(Blocks.AMETHYST_CLUSTER) || s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN)
						|| s.is(Blocks.SCULK) || s.is(Blocks.ENCHANTING_TABLE) || s.is(Blocks.LAVA))
						.ifPresent(pos -> options.add(new Study(pos, null, name(level.getBlockState(pos)))));
				options.add(new Patrol(owner));
			}
		}
		// With autonomy on, a job suited to their role is twice as likely as the other things they might do.
		io.github.tekless.hearthbound.entity.order.Routines.pick(companion, owner).ifPresent(job -> {
			options.add(job);
			options.add(job);
		});
		if (options.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(options.get(companion.getRandom().nextInt(options.size())));
	}

	// ------------------------------------------------------------------ activities

	/** Walk over to something interesting and look at it for a while. */
	static final class Study implements Pastime {
		private final BlockPos pos;
		private final Entity entity;
		private final String thing;
		private int ticks;
		private boolean arrived;

		Study(BlockPos pos, Entity entity, String thing) {
			this.pos = pos;
			this.entity = entity;
			this.thing = thing;
		}

		@Override
		public boolean tick(CompanionEntity c, ServerPlayer owner) {
			Vec3 target = entity != null ? entity.position() : Vec3.atCenterOf(pos);
			if (entity != null && !entity.isAlive()) {
				return false;
			}
			if (++ticks > MAX_TICKS) {
				return false;
			}
			c.getLookControl().setLookAt(target.x, target.y + (entity != null ? entity.getBbHeight() * 0.7 : 0), target.z);
			if (!arrived) {
				if (c.position().distanceTo(target) < 2.5) {
					arrived = true;
					c.getNavigation().stop();
					c.sayMaybe("pastime.study", Map.of("thing", thing), 0.7);
				} else if (ticks % 20 == 1 && !c.getNavigation().moveTo(target.x, target.y, target.z, 0.8)) {
					return false;
				}
				return true;
			}
			return ticks < 160;
		}
	}

	/** Pick one wild flower and keep it. */
	static final class PickFlower implements Pastime {
		private final BlockPos pos;
		private final String lineKey;
		private int ticks;

		PickFlower(BlockPos pos) {
			this(pos, "pastime.flower");
		}

		PickFlower(BlockPos pos, String lineKey) {
			this.pos = pos;
			this.lineKey = lineKey;
		}

		@Override
		public boolean tick(CompanionEntity c, ServerPlayer owner) {
			ServerLevel level = (ServerLevel) c.level();
			BlockState state = level.getBlockState(pos);
			if (!state.is(BlockTags.SMALL_FLOWERS) || ++ticks > MAX_TICKS) {
				return false;
			}
			c.getLookControl().setLookAt(Vec3.atCenterOf(pos));
			if (c.position().distanceTo(Vec3.atCenterOf(pos)) > 2.2) {
				if (ticks % 20 == 1) {
					c.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.8);
				}
				return true;
			}
			c.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			ItemStack flower = new ItemStack(state.getBlock().asItem());
			String name = flower.getHoverName().getString().toLowerCase(Locale.ROOT);
			level.destroyBlock(pos, false, c);
			c.storeOrDrop(flower);
			c.sayMaybe(lineKey, Map.of("item", name), 0.8);
			return false;
		}
	}

	/** Pick up something left lying on the ground (it goes into the pack; ask for it back any time). */
	static final class Loot implements Pastime {
		private final ItemEntity item;
		private int ticks;

		Loot(ItemEntity item) {
			this.item = item;
		}

		@Override
		public boolean tick(CompanionEntity c, ServerPlayer owner) {
			if (!item.isAlive() || ++ticks > MAX_TICKS) {
				return false;
			}
			c.getLookControl().setLookAt(item);
			if (c.distanceTo(item) > 1.6) {
				if (ticks % 15 == 1) {
					c.getNavigation().moveTo(item, 0.9);
				}
				return true;
			}
			ItemStack stack = item.getItem().copy();
			if (!c.hasRoomFor(s -> ItemStack.isSameItemSameComponents(s, stack))) {
				return false;
			}
			String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
			c.take(item, stack.getCount());
			item.discard();
			c.storeOrDrop(stack);
			c.sayMaybe("pastime.loot", Map.of("item", name), 0.9);
			return false;
		}
	}

	/** Walk a small guard circuit around the player, looking outwards. */
	static final class Patrol implements Pastime {
		private final List<Vec3> points = new ArrayList<>();
		private int index;
		private int ticks;
		private int pause;

		Patrol(ServerPlayer owner) {
			double start = owner.getRandom().nextDouble() * Math.PI * 2;
			for (int i = 0; i < 3; i++) {
				double angle = start + i * (Math.PI * 2 / 3);
				points.add(owner.position().add(Math.cos(angle) * 5, 0, Math.sin(angle) * 5));
			}
		}

		@Override
		public boolean tick(CompanionEntity c, ServerPlayer owner) {
			if (++ticks > MAX_TICKS || index >= points.size()) {
				return false;
			}
			if (ticks == 1) {
				c.sayMaybe("pastime.patrol", Map.of(), 0.5);
			}
			Vec3 point = points.get(index);
			if (pause > 0) {
				pause--;
				Vec3 outward = point.add(point.subtract(owner.position()).normalize().scale(6));
				c.getLookControl().setLookAt(outward.x, c.getEyeY(), outward.z);
				if (pause == 0) {
					index++;
				}
				return true;
			}
			if (c.position().distanceTo(point) < 1.5) {
				pause = 30;
				c.getNavigation().stop();
			} else if (ticks % 20 == 1 && !c.getNavigation().moveTo(point.x, point.y, point.z, 0.7)) {
				index++;
			}
			return true;
		}
	}

	/** A few practice swings. */
	static final class Practice implements Pastime {
		private int ticks;

		@Override
		public boolean tick(CompanionEntity c, ServerPlayer owner) {
			if (ticks == 0) {
				c.getNavigation().stop();
				c.sayMaybe("pastime.practice", Map.of(), 0.6);
			}
			ticks++;
			if (ticks % 12 == 0) {
				c.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				c.setYRot(c.getYRot() + 40);
			}
			return ticks < 72;
		}
	}

	/** Make a fuss of a nearby animal. */
	static final class Pet implements Pastime {
		private final Animal animal;
		private int ticks;
		private boolean arrived;

		Pet(Animal animal) {
			this.animal = animal;
		}

		@Override
		public boolean tick(CompanionEntity c, ServerPlayer owner) {
			if (!animal.isAlive() || ++ticks > MAX_TICKS) {
				return false;
			}
			c.getLookControl().setLookAt(animal);
			if (!arrived) {
				if (c.distanceTo(animal) < 2.0) {
					arrived = true;
					c.getNavigation().stop();
					c.sayMaybe("pastime.pet", Map.of("mob", animal.getName().getString().toLowerCase(Locale.ROOT)), 0.8);
				} else if (ticks % 15 == 1) {
					c.getNavigation().moveTo(animal, 0.8);
				}
				return true;
			}
			if (ticks % 20 == 0 && c.level() instanceof ServerLevel level) {
				c.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				level.sendParticles(ParticleTypes.HEART, animal.getX(), animal.getY() + animal.getBbHeight() + 0.3, animal.getZ(), 1, 0.2, 0.1, 0.2, 0);
			}
			return ticks < 140;
		}
	}

	/** Stand still and look at the night sky. */
	static final class Stargaze implements Pastime {
		private int ticks;

		@Override
		public boolean tick(CompanionEntity c, ServerPlayer owner) {
			if (ticks == 0) {
				c.getNavigation().stop();
				c.sayMaybe("pastime.stargaze", Map.of(), 0.7);
			}
			ticks++;
			c.getLookControl().setLookAt(c.getX() + Math.sin(ticks / 40.0) * 3, c.getEyeY() + 8, c.getZ() + Math.cos(ticks / 40.0) * 3);
			return ticks < 140;
		}
	}

	// ------------------------------------------------------------------ helpers

	private static Optional<Pastime> lootNearby(ServerLevel level, CompanionEntity companion, ServerPlayer owner, int minAge) {
		if (WorldRules.isProtected(level, owner.blockPosition())) {
			return Optional.empty();
		}
		// Only things the owner dropped or nobody did (mob drops): never another player's stuff.
		return level.getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(RANGE),
						item -> item.isAlive() && item.getAge() >= minAge && !item.hasPickUpDelay()
								&& (item.getOwner() == null || item.getOwner() == owner))
				.stream()
				.filter(item -> companion.hasRoomFor(s -> ItemStack.isSameItemSameComponents(s, item.getItem())))
				.min(Comparator.comparingDouble(item -> item.distanceToSqr(companion)))
				.map(Loot::new);
	}

	private static Optional<Animal> findAnimal(ServerLevel level, ServerPlayer owner) {
		List<Animal> animals = level.getEntitiesOfClass(Animal.class, owner.getBoundingBox().inflate(RANGE), Animal::isAlive);
		return animals.isEmpty() ? Optional.empty() : Optional.of(animals.get(owner.getRandom().nextInt(animals.size())));
	}

	private static Optional<BlockPos> findBlock(ServerLevel level, ServerPlayer owner, java.util.function.Predicate<BlockState> test) {
		List<BlockPos> found = new ArrayList<>();
		BlockPos origin = owner.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-RANGE, -3, -RANGE), origin.offset(RANGE, 3, RANGE))) {
			if (test.test(level.getBlockState(pos))) {
				found.add(pos.immutable());
				if (found.size() > 24) {
					break;
				}
			}
		}
		return found.isEmpty() ? Optional.empty() : Optional.of(found.get(owner.getRandom().nextInt(found.size())));
	}

	/** True when nothing crafted is nearby - so we only pick flowers in the wild, not from a garden. */
	static boolean isWild(ServerLevel level, BlockPos pos) {
		if (WorldRules.isProtected(level, pos)) {
			return false;
		}
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -2, -3), pos.offset(3, 2, 3))) {
			BlockState state = level.getBlockState(p);
			if (!(state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.SMALL_FLOWERS)
					|| GatherCatalog.isNatural(state) || !state.getFluidState().isEmpty() || state.is(BlockTags.SNOW))) {
				return false;
			}
		}
		return true;
	}

	private static String name(BlockState state) {
		return state.getBlock().getName().getString().toLowerCase(Locale.ROOT);
	}
}

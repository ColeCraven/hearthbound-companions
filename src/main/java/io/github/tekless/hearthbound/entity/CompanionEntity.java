package io.github.tekless.hearthbound.entity;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.core.dialogue.English;
import io.github.tekless.hearthbound.core.dialogue.LineBank;
import io.github.tekless.hearthbound.core.dialogue.LineSelector;
import io.github.tekless.hearthbound.core.intent.QuestionKind;
import io.github.tekless.hearthbound.core.memory.CompanionMemory;
import io.github.tekless.hearthbound.core.memory.PlayerFacts;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.core.task.FollowPolicy;
import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.dialogue.Conversations;
import io.github.tekless.hearthbound.dialogue.Texts;
import io.github.tekless.hearthbound.entity.order.ComeOrder;
import io.github.tekless.hearthbound.entity.order.CompanionOrder;
import io.github.tekless.hearthbound.entity.order.FollowOrder;
import io.github.tekless.hearthbound.entity.order.GatherOrder;
import io.github.tekless.hearthbound.entity.order.HomeOrder;
import io.github.tekless.hearthbound.entity.order.MineOrder;
import io.github.tekless.hearthbound.entity.order.WaitOrder;
import io.github.tekless.hearthbound.world.CompanionRoster;
import io.github.tekless.hearthbound.world.GatherCatalog;
import io.github.tekless.hearthbound.world.GatherSpec;
import io.github.tekless.hearthbound.world.HubResidents;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A companion's body in the world. Movement and actions are driven by {@link CompanionOrder}s and
 * vanilla AI goals; what they say goes through {@link #say}. Nothing in here calls an AI model.
 */
public class CompanionEntity extends PathfinderMob {
	public static final int INVENTORY_SIZE = 18;
	private static final int DOWNED_TICKS = 300;
	private static final int SAVE_VERSION = 1;

	private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_DOWNED = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<String> DATA_ARCHETYPE = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
	/** Name of the player they travel with ("" when free); shown to players without the mod. */
	private static final EntityDataAccessor<String> DATA_OWNER_NAME = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
	/** Players within this distance hear what a companion says, besides its owner. */
	private static final double HEARING_RANGE = 16;

	private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
	private final CompanionMemory memory = new CompanionMemory();
	private final LineSelector lines = new LineSelector();

	private Archetype archetype = Archetype.SELLSWORD;
	private @Nullable UUID ownerId;
	/** Whoever is giving an unrecruited companion orders right now (obey mode); forgotten when the job ends. */
	private @Nullable UUID commanderId;
	/** The player or companion this one has been told to fight. Nobody else is ever a target. */
	private @Nullable UUID directedId;
	private @Nullable BlockPos home;
	private CompanionOrder order;
	/** What to go back to when a task (gather, give, come, attack) ends. */
	private CompanionOrder.Kind baseMode = CompanionOrder.Kind.HOME;
	private @Nullable BlockPos waitPost;
	private int downedTicks;
	private @Nullable ServerPlayer pendingCatchUp;
	private int catchUpCooldown;
	private int toolSlotSwapped = -1;
	private int talkCooldown;
	private long lastGiftTime = -100000;
	/** Lives at the server's hub when nobody has recruited them. */
	private boolean hubResident;
	/** Game time at which a dismissed hub resident heads back to the hub, or -1. */
	private long returnToHubAt = -1;
	/** Whoever spoke to this companion last, so they hear the answer wherever they stand. */
	private @Nullable UUID lastSpeaker;
	private long lastSpokeAt;
	private final Map<UUID, Long> hintedAt = new HashMap<>();
	/** Situation key of the last scripted line (useful for tests and debugging). */
	private String lastLineKey = "";
	/** A question we asked a player and are waiting to hear back on, or null. */
	private @Nullable AskedQuestion asked;
	/** Counts down while crouching back at someone (two quick bobs). */
	private int crouchTicks;
	private long lastCrouchHello = -100000;
	/** Hunger, rest and company, for companions who live somewhere (see DailyRoutine). */
	private final io.github.tekless.hearthbound.core.life.Needs needs = new io.github.tekless.hearthbound.core.life.Needs();
	private io.github.tekless.hearthbound.core.life.Schedule.@Nullable Traits traits;
	/** Woken by someone: stays up until this game time. */
	private long awakeUntil = -1;
	/** A guardian walking their rounds at home: hostile mobs near home are fair game. */
	private boolean onWatch;
	/** Their own little flower bed (a multi-day project): one corner, flowers planted, the day of the last one. */
	private @Nullable BlockPos gardenAt;
	private int gardenPlanted;
	private long gardenDay = -1;
	/** Has had the few things of their own a home life starts with (food, seeds, torches). */
	private boolean stocked;
	/** When (which day) they last tried or mentioned something, so nobody keeps at a thing that can't work. */
	private final Map<String, Long> lastTried = new HashMap<>();
	/** Tests only: daily life on or off for this one companion, whatever the config says (null: the config decides). */
	private @Nullable Boolean dailyLife;
	/** Tests only: the time of day this companion sees (null: the world's clock). */
	private @Nullable Long testClock;
	/** Tests only: autonomy on or off for this one companion, whatever the config says (null: the config decides). */
	private @Nullable Boolean testAutonomy;

	/** A question this companion asked, so the player's next words can be read as the answer. */
	public record AskedQuestion(QuestionKind kind, UUID player, long tick) {
	}

	public CompanionEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.setCanPickUpLoot(false);
		if (this.getNavigation() instanceof GroundPathNavigation ground) {
			ground.setCanOpenDoors(true);
			ground.setCanFloat(true);
		}
		this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 16.0F);
		this.setPathfindingMalus(PathType.FIRE, -1.0F);
		this.setPathfindingMalus(PathType.WATER, 4.0F);
		this.setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 2.0)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.ARMOR, 2.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SKIN, 0);
		builder.define(DATA_DOWNED, false);
		builder.define(DATA_ARCHETYPE, Archetype.SELLSWORD.id());
		builder.define(DATA_OWNER_NAME, "");
	}

	/** Synced fields whose change alters how players without the mod see this companion. */
	public static boolean affectsLook(int dataId) {
		return dataId == DATA_SKIN.id() || dataId == DATA_DOWNED.id() || dataId == DATA_OWNER_NAME.id() || dataId == DATA_ARCHETYPE.id();
	}

	// ------------------------------------------------------------------ AI goals

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new DownedGoal());
		goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Creeper.class, c -> ((Creeper) c).getSwellDir() > 0 || ((Creeper) c).isIgnited(), 6.0F, 1.1, 1.4, e -> true));
		goalSelector.addGoal(3, new RetreatGoal());
		goalSelector.addGoal(4, new RangedGoal());
		goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.2, true));
		goalSelector.addGoal(6, new OrderGoal());
		goalSelector.addGoal(7, new OpenDoorGoal(this, true));
		goalSelector.addGoal(8, new IdleStrollGoal());
		goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F) {
			@Override
			public boolean canUse() {
				return !isSleeping() && super.canUse();
			}
		});
		goalSelector.addGoal(10, new RandomLookAroundGoal(this) {
			@Override
			public boolean canUse() {
				return !isSleeping() && super.canUse();
			}
		});

		targetSelector.addGoal(1, new HurtByTargetGoal(this, CompanionEntity.class, Player.class));
		targetSelector.addGoal(2, new DefendOwnerGoal());
		targetSelector.addGoal(3, new AssistOwnerGoal());
		targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, true, this::wantsToEngage));
	}

	private boolean wantsToEngage(LivingEntity target, ServerLevel level) {
		if (!(target instanceof Enemy) || target instanceof Creeper || isDowned() || (!isRecruited() && !onWatch) || isSleeping()) {
			return false;
		}
		if (!isRecruited()) {
			// On their rounds at home: anything hostile that comes close to home.
			return home != null && target.blockPosition().distSqr(home) < 24 * 24 && distanceTo(target) < 12;
		}
		if (getHealth() / getMaxHealth() < archetype.retreatHealth()) {
			return false;
		}
		int radius = archetype.engageRadius(level.isDarkOutside());
		if (radius <= 0 || distanceTo(target) > radius) {
			return false;
		}
		return switch (archetype.stance()) {
			case AGGRESSIVE, NOCTURNAL -> true;
			case PROTECTIVE, OPPORTUNIST -> target instanceof Mob mob && mob.getTarget() != null && mob.getTarget().getUUID().equals(ownerId);
			case DEFENSIVE, CAUTIOUS -> false;
		};
	}

	/**
	 * Never fight a player or a fellow companion on their own: only the one they were told to fight, never
	 * their own owner or the one commanding them, never a player while PvP is off, and not a companion
	 * who is already knocked down.
	 */
	@Override
	public boolean canAttack(LivingEntity target) {
		if (target instanceof Player || target instanceof CompanionEntity) {
			if (directedId == null || !directedId.equals(target.getUUID()) || isDowned()) {
				return false;
			}
			if (target instanceof Player player) {
				if (isOwnedBy(player) || player.getUUID().equals(commanderId) || player.isSpectator()
						|| !(level() instanceof ServerLevel serverLevel) || !serverLevel.isPvpAllowed()) {
					return false;
				}
			}
			if (target instanceof CompanionEntity other && (other == this || other.isDowned())) {
				return false;
			}
			return target.isAlive(); // told to, so even on peaceful difficulty
		}
		return super.canAttack(target);
	}

	/** Fight this player or companion (the commander said so). Cleared by {@link #clearDirected()}. */
	public void directAt(LivingEntity target) {
		directedId = target.getUUID();
	}

	public void clearDirected() {
		directedId = null;
	}

	/**
	 * Who this companion is taking orders from right now: someone using obey mode on them (a companion who
	 * travels with nobody, or an admin overriding another player's companion) while that person is online,
	 * otherwise the player they travel with, or null.
	 */
	public @Nullable ServerPlayer commanderPlayer() {
		if (commanderId != null && level() instanceof ServerLevel serverLevel) {
			ServerPlayer commander = serverLevel.getServer().getPlayerList().getPlayer(commanderId);
			if (commander != null) {
				return commander;
			}
		}
		return ownerPlayer();
	}

	/** The player giving the current order. Their own owner clears any obey-mode commander. */
	public void setCommander(ServerPlayer player) {
		commanderId = isOwnedBy(player) ? null : player.getUUID();
	}

	/** Moves items to another companion's pack (what is left over stays here). */
	public int giveTo(CompanionEntity other, Predicate<ItemStack> filter, int max) {
		int given = 0;
		for (int slot = 0; slot < inventory.getContainerSize() && given < max; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || !filter.test(stack)) {
				continue;
			}
			ItemStack moved = inventory.removeItem(slot, Math.min(stack.getCount(), max - given));
			ItemStack rest = other.inventory().addItem(moved);
			given += moved.getCount() - rest.getCount();
			if (!rest.isEmpty()) {
				inventory.addItem(rest);
			}
		}
		return given;
	}

	/** Makes this spot their home (where an unrecruited companion lives; where they go when sent home). */
	public void setHomePos(BlockPos pos) {
		home = pos.immutable();
		forgetArea();
		clearHome();
		if (!isRecruited() && order != null && order.kind() == CompanionOrder.Kind.HOME) {
			order = new HomeOrder(home);
		}
		updateRoster();
	}

	// ------------------------------------------------------------------ ticking

	@Override
	public void tick() {
		if (!level().isClientSide() && pendingCatchUp != null) {
			ServerPlayer owner = pendingCatchUp;
			pendingCatchUp = null;
			if (teleportNear(owner)) {
				return;
			}
		}
		super.tick();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (catchUpCooldown > 0) {
			catchUpCooldown--;
		}
		if (talkCooldown > 0) {
			talkCooldown--;
		}
		if (isDowned()) {
			if (--downedTicks <= 0) {
				setDowned(false);
				setHealth(Math.max(getHealth(), getMaxHealth() * 0.3F));
				say("revived", Map.of());
			}
			return;
		}
		if (crouchTicks > 0) {
			crouchTicks--;
			setPose(crouchTicks > 0 && (crouchTicks / 4) % 2 == 1 ? Pose.CROUCHING : Pose.STANDING);
		}
		// Eye contact with whoever is talking to us, unless we're busy working or fighting.
		if (lastSpeaker != null && level.getGameTime() - lastSpokeAt < 160 && getTarget() == null && order != null && order.kind().isMode()) {
			ServerPlayer speaker = level.getServer().getPlayerList().getPlayer(lastSpeaker);
			if (speaker != null && speaker.level() == level && distanceTo(speaker) < 10) {
				getLookControl().setLookAt(speaker);
			}
		}
		if (tickCount % 40 == 0) {
			equipBestGear();
			careForGear(level);
			if (getHealth() < getMaxHealth() && getTarget() == null && tickCount % 80 == 0) {
				heal(1.0F);
			}
		}
		if (getTarget() != null && getOffhandItem().isEmpty() && tickCount % 5 == 0) {
			equipShield(); // a fight starting doesn't wait for the next look through the pack
		}
		guardWithShield();
		eatWhenHurt(level);
		if (isSleeping() && (getTarget() != null || !(order instanceof HomeOrder))) {
			stopSleeping();
		}
		if (returnToHubAt >= 0 && level.getGameTime() >= returnToHubAt) {
			returnToHubAt = -1;
			if (!isRecruited()) {
				HubResidents.sendHome(this);
			}
		}
		if (order == null) {
			order = createBaseOrder();
		}
		TaskStatus status = order.status();
		if (status.isFinished() && !order.kind().isMode()) {
			finishTask();
		}
	}

	private void finishTask() {
		CompanionOrder.Kind finished = order.kind();
		commanderId = null;
		directedId = null;
		order = createBaseOrder();
		if (finished == CompanionOrder.Kind.GATHER || finished == CompanionOrder.Kind.MINE) {
			restoreWeapon();
		}
	}

	private CompanionOrder createBaseOrder() {
		if (!isRecruited()) {
			return new HomeOrder(home != null ? home : blockPosition());
		}
		if (baseMode == CompanionOrder.Kind.WAIT) {
			return new WaitOrder(waitPost != null ? waitPost : blockPosition());
		}
		return new FollowOrder();
	}

	// ------------------------------------------------------------------ orders (called by game code only)

	public CompanionOrder order() {
		if (order == null) {
			order = createBaseOrder();
		}
		return order;
	}

	/** Replaces the current order. Standing modes (follow/wait) also become the base mode. */
	public void setOrder(CompanionOrder next) {
		if (isSleeping() && order != next) {
			stopSleeping();
		}
		if (order != null && order != next) {
			order.cancel(this);
			if (order.kind() == CompanionOrder.Kind.GATHER || order.kind() == CompanionOrder.Kind.MINE) {
				restoreWeapon();
			}
		}
		order = next;
		if (next.kind() == CompanionOrder.Kind.FOLLOW) {
			baseMode = CompanionOrder.Kind.FOLLOW;
			waitPost = null;
		} else if (next.kind() == CompanionOrder.Kind.WAIT) {
			baseMode = CompanionOrder.Kind.WAIT;
			waitPost = ((WaitOrder) next).post();
		}
	}

	/** Cancels the active task, returning to follow/wait. Returns what was cancelled, if anything. */
	public Optional<String> cancelTask() {
		if (order == null || order.kind().isMode()) {
			return Optional.empty();
		}
		String description = order.describe();
		order.cancel(this);
		setTarget(null);
		finishTask();
		return Optional.of(description);
	}

	public CompanionOrder.Kind baseMode() {
		return baseMode;
	}

	public FollowPolicy followPolicy() {
		return new FollowPolicy(archetype.followMin(), archetype.followMax());
	}

	/** Asks for a safe teleport next to the owner at the start of the next tick. */
	public void requestCatchUp(ServerPlayer owner) {
		if (catchUpCooldown <= 0) {
			pendingCatchUp = owner;
		}
	}

	/** Teleports to a safe, walkable spot 2-3 blocks from the owner. Works across dimensions. */
	public boolean teleportNear(ServerPlayer owner) {
		if (!(owner.level() instanceof ServerLevel targetLevel) || owner.isSpectator()) {
			return false;
		}
		catchUpCooldown = 40;
		BlockPos center = owner.blockPosition();
		for (int attempt = 0; attempt < 20; attempt++) {
			int dx = random.nextIntBetweenInclusive(-3, 3);
			int dz = random.nextIntBetweenInclusive(-3, 3);
			if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
				continue;
			}
			int dy = random.nextIntBetweenInclusive(-1, 1);
			BlockPos spot = center.offset(dx, dy, dz);
			if (isSafeSpot(targetLevel, spot)) {
				getNavigation().stop();
				if (targetLevel == level()) {
					snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, getYRot(), getXRot());
				} else {
					teleportTo(targetLevel, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, Set.of(), getYRot(), getXRot(), false);
				}
				if (talkCooldown <= 0 && random.nextInt(3) == 0) {
					say("move.caught_up", Map.of());
				}
				return true;
			}
		}
		// Nowhere free nearby (a tight tunnel, a 1x2 hole): wherever the player can stand, so can we.
		if (owner.onGround() || owner.isInWater() || targetLevel != level()) {
			getNavigation().stop();
			if (targetLevel == level()) {
				snapTo(owner.getX(), owner.getY(), owner.getZ(), getYRot(), getXRot());
			} else {
				teleportTo(targetLevel, owner.getX(), owner.getY(), owner.getZ(), Set.of(), getYRot(), getXRot(), false);
			}
			return true;
		}
		return false;
	}

	private boolean isSafeSpot(ServerLevel level, BlockPos pos) {
		if (level != level()) {
			// Different dimension: only the cheap checks are possible from here.
			BlockState below = level.getBlockState(pos.below());
			return below.isSolidRender() && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
					&& level.getFluidState(pos).isEmpty();
		}
		PathType type = WalkNodeEvaluator.getPathTypeStatic(this, pos);
		if (type != PathType.WALKABLE) {
			return false;
		}
		if (level.getBlockState(pos.below()).getBlock() instanceof LeavesBlock) {
			return false;
		}
		BlockPos delta = pos.subtract(blockPosition());
		return level.noCollision(this, getBoundingBox().move(delta));
	}

	// ------------------------------------------------------------------ identity

	public void initIdentity(Archetype archetype, String name, CompanionSkin skin, boolean slim) {
		initIdentity(archetype, name, skin.encode(slim));
	}

	public void initIdentity(Archetype archetype, String name, int skinVariant) {
		setArchetype(archetype);
		setCustomName(Component.literal(name));
		setCustomNameVisible(true);
		setSkinVariant(skinVariant);
		applyRoleStats();
	}

	/** Makes this companion one of the named cast characters (name, personality and outfit). */
	public void initIdentity(io.github.tekless.hearthbound.core.persona.CastMember member) {
		initIdentity(member.archetype(), member.displayName(), member.skinVariant());
	}

	/** What this companion is good at: their cast role, or one that fits their personality. */
	public io.github.tekless.hearthbound.core.persona.Role role() {
		return castMember().map(io.github.tekless.hearthbound.core.persona.CastMember::role)
				.orElseGet(() -> io.github.tekless.hearthbound.core.persona.Role.forArchetype(archetype));
	}

	/** Fighters hit harder and take more punishment; everyone else is as sturdy as ever. */
	private void applyRoleStats() {
		io.github.tekless.hearthbound.core.persona.Role role = role();
		net.minecraft.world.entity.ai.attributes.AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) {
			damage.setBaseValue(2.0 * role.multiplier(io.github.tekless.hearthbound.core.persona.Role.Task.COMBAT));
		}
		net.minecraft.world.entity.ai.attributes.AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			float oldMax = getMaxHealth();
			health.setBaseValue(role.isStrongAt(io.github.tekless.hearthbound.core.persona.Role.Task.COMBAT) ? 24.0 : 20.0);
			if (getHealth() >= oldMax || getHealth() > getMaxHealth()) {
				setHealth(getMaxHealth());
			}
		}
	}

	/** The cast character this companion is, if it is one (matched by name and outfit). */
	public java.util.Optional<io.github.tekless.hearthbound.core.persona.CastMember> castMember() {
		return io.github.tekless.hearthbound.core.persona.CastMember.bySkinVariant(skinVariant())
				.filter(member -> member.displayName().equals(companionName()));
	}

	public Archetype archetype() {
		return archetype;
	}

	public void setArchetype(Archetype archetype) {
		this.archetype = archetype;
		entityData.set(DATA_ARCHETYPE, archetype.id());
	}

	/** Archetype id as seen by the client (for name colours). */
	public String syncedArchetype() {
		return entityData.get(DATA_ARCHETYPE);
	}

	public String companionName() {
		return getCustomName() != null ? getCustomName().getString() : archetype.displayName();
	}

	public int skinVariant() {
		return entityData.get(DATA_SKIN);
	}

	public void setSkinVariant(int encoded) {
		entityData.set(DATA_SKIN, CompanionSkin.sanitize(encoded));
	}

	public boolean isRecruited() {
		return ownerId != null;
	}

	public @Nullable UUID ownerId() {
		return ownerId;
	}

	public boolean isOwnedBy(Player player) {
		return player.getUUID().equals(ownerId);
	}

	public @Nullable ServerPlayer ownerPlayer() {
		if (ownerId == null || !(level() instanceof ServerLevel serverLevel)) {
			return null;
		}
		return serverLevel.getServer().getPlayerList().getPlayer(ownerId);
	}

	public void recruit(ServerPlayer player) {
		ownerId = player.getUUID();
		commanderId = null;
		entityData.set(DATA_OWNER_NAME, player.getName().getString());
		if (!hubResident) {
			home = blockPosition();
		}
		returnToHubAt = -1;
		clearHome();
		forgetArea();
		baseMode = CompanionOrder.Kind.FOLLOW;
		setOrder(new FollowOrder());
		memory.addEvent("I joined {player} on the road");
		memory.changeAffinity(3);
		updateRoster();
	}

	public void dismiss() {
		ownerId = null;
		entityData.set(DATA_OWNER_NAME, "");
		baseMode = CompanionOrder.Kind.HOME;
		setTarget(null);
		if (hubResident && HubResidents.hubPos().isPresent()) {
			home = HubResidents.hubPos().get();
			// Walk off for a few seconds, then make their own way back to the hub.
			returnToHubAt = level().getGameTime() + 100;
		}
		forgetArea();
		setOrder(new HomeOrder(home != null ? home : blockPosition()));
		memory.addEvent("I parted ways with {player}");
		updateRoster();
	}

	/** Name of the player this companion travels with, or null. */
	public @Nullable String ownerName() {
		String name = entityData.get(DATA_OWNER_NAME);
		return name.isEmpty() ? null : name;
	}

	public boolean isHubResident() {
		return hubResident;
	}

	/** Makes this companion live at the hub: that's home, and where they go back to when dismissed. */
	public void makeHubResident(BlockPos hub) {
		hubResident = true;
		home = hub.immutable();
		forgetArea();
		if (!isRecruited()) {
			setOrder(new HomeOrder(home));
		}
		updateRoster();
	}

	public @Nullable BlockPos home() {
		return home;
	}

	/** How far an unrecruited companion strolls from home. */
	public int homeRadius() {
		return hubResident ? Math.max(3, Hearthbound.config().server.hub.wanderRadius) : 3;
	}

	// ------------------------------------------------------------------ daily life

	/** The home area is worked out again next time (their home moved, or they joined or left someone). */
	private void forgetArea() {
		if (level() instanceof ServerLevel serverLevel) {
			io.github.tekless.hearthbound.life.HomeAreas.get(serverLevel.getServer()).forgetMember(this);
		}
	}

	public io.github.tekless.hearthbound.core.life.Needs needs() {
		return needs;
	}

	/** When this companion gets up, eats and turns in: their personality, nudged a little by who they are. */
	public io.github.tekless.hearthbound.core.life.Schedule.Traits lifeTraits() {
		if (traits == null || !archetype.id().equals(tracedArchetype)) {
			tracedArchetype = archetype.id();
			traits = io.github.tekless.hearthbound.core.life.Schedule.Traits.of(archetype.id(), getUUID().getMostSignificantBits() ^ getUUID().getLeastSignificantBits());
		}
		return traits;
	}

	private String tracedArchetype = "";

	/** Gets up (someone needs them) and stays up for half a minute. */
	public void wakeUp() {
		if (isSleeping()) {
			stopSleeping();
		}
		awakeUntil = level().getGameTime() + 600;
	}

	/** Whether this companion follows a daily schedule when at home (the config's {@code dailyLife}, unless a test says otherwise). */
	public boolean dailyLifeEnabled() {
		return dailyLife != null ? dailyLife : Hearthbound.config().dailyLife;
	}

	/** Tests only: switch daily life on or off for this companion (null to follow the config). */
	public void setDailyLife(@Nullable Boolean enabled) {
		dailyLife = enabled;
	}

	/** The world's clock (time of day plus whole days), as this companion's day sees it. */
	public long clockTime() {
		return testClock != null ? testClock : level().getOverworldClockTime();
	}

	/** Tests only: fix the time of day this companion lives by (null for the real clock). */
	public void setTestClock(@Nullable Long clock) {
		testClock = clock;
	}

	/** Tests only: autonomy for this companion alone (null: the config decides), so tests can run side by side. */
	public void setTestAutonomy(@Nullable Boolean autonomy) {
		testAutonomy = autonomy;
	}

	public @Nullable Boolean testAutonomy() {
		return testAutonomy;
	}

	/**
	 * True (and noted) if {@code what} hasn't been tried or said in the last {@code days} days: how initiatives and
	 * "we could do with a bed" are kept from repeating.
	 */
	public boolean tryEvery(String what, long day, int days) {
		Long last = lastTried.get(what);
		if (last != null && day >= last && day - last < days) {
			return false;
		}
		lastTried.put(what, day);
		return true;
	}

	public long awakeUntil() {
		return awakeUntil;
	}

	public void setOnWatch(boolean watching) {
		onWatch = watching;
	}

	/** Someone spoke to them in the last few seconds and is still close by. */
	public boolean isBeingTalkedTo() {
		if (lastSpeaker == null || !(level() instanceof ServerLevel serverLevel) || serverLevel.getGameTime() - lastSpokeAt > 160) {
			return false;
		}
		ServerPlayer speaker = serverLevel.getServer().getPlayerList().getPlayer(lastSpeaker);
		return speaker != null && speaker.level() == level() && distanceTo(speaker) < 10;
	}

	public @Nullable BlockPos gardenAt() {
		return gardenAt;
	}

	public void setGardenAt(BlockPos corner) {
		gardenAt = corner.immutable();
	}

	public int gardenPlanted() {
		return gardenPlanted;
	}

	public long gardenDay() {
		return gardenDay;
	}

	public void plantedInGarden(long day) {
		gardenPlanted++;
		gardenDay = day;
	}

	/**
	 * The few things of their own someone who lives somewhere starts with, once: a little food for meals, and
	 * something their work uses, which is also what they can offer in return for a favour.
	 */
	public void stockPack() {
		if (stocked) {
			return;
		}
		stocked = true;
		switch (role()) {
			case FARMER -> {
				inventory.addItem(new ItemStack(net.minecraft.world.item.Items.BREAD, 6));
				inventory.addItem(new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS, 12));
			}
			case COLLECTOR -> {
				inventory.addItem(new ItemStack(net.minecraft.world.item.Items.APPLE, 6));
				inventory.addItem(new ItemStack(net.minecraft.world.item.Items.EMERALD, 1));
			}
			case BUILDER -> {
				inventory.addItem(new ItemStack(net.minecraft.world.item.Items.BREAD, 3));
				inventory.addItem(new ItemStack(net.minecraft.world.item.Items.TORCH, 12));
			}
			case GUARDIAN -> {
				inventory.addItem(new ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, 4));
				inventory.addItem(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2));
			}
		}
	}

	private void updateRoster() {
		if (level() instanceof ServerLevel serverLevel) {
			CompanionRoster.get(serverLevel.getServer()).noteLoaded(this);
		}
	}

	/** Remembers who is talking to us, so they hear the reply even if they stand far off. */
	public void noteSpeaker(ServerPlayer player) {
		lastSpeaker = player.getUUID();
		lastSpokeAt = level().getGameTime();
	}

	public CompanionMemory memory() {
		return memory;
	}

	/** What we call this player: the name they asked for, or their own. */
	public String callName(Player player) {
		return memory.knownFacts(player.getUUID().toString()).flatMap(PlayerFacts::nickname).orElse(player.getName().getString());
	}

	public PlayerFacts factsAbout(Player player) {
		return memory.factsAbout(player.getUUID().toString());
	}

	/** Asks a player something (a line from {@code key}) and listens for the answer for the next half minute. */
	public void ask(ServerPlayer player, QuestionKind kind, String key, Map<String, String> vars) {
		noteSpeaker(player);
		say(key, vars);
		asked = new AskedQuestion(kind, player.getUUID(), level().getGameTime());
	}

	/** The question this player is answering, if we asked them one recently. Asking is forgotten either way. */
	public @Nullable AskedQuestion takeQuestion(ServerPlayer player) {
		AskedQuestion question = asked;
		asked = null;
		if (question == null || !question.player().equals(player.getUUID()) || level().getGameTime() - question.tick() > 600) {
			return null;
		}
		return question;
	}

	// ------------------------------------------------------------------ gestures

	/** Crouches back at a player who crouched at us: the oldest hello in Minecraft. */
	public void crouchBack(ServerPlayer player) {
		if (isDowned()) {
			return;
		}
		crouchTicks = 16;
		noteSpeaker(player);
		long now = level().getGameTime();
		if (now - lastCrouchHello > 6000) {
			lastCrouchHello = now;
			if (isOwnedBy(player)) {
				memory.changeAffinity(1);
			}
			sayMaybe("gesture.crouch", Map.of("player", callName(player)), 0.6);
		}
	}

	/** A wave: hello, goodbye, over here. */
	public void wave() {
		if (!isDowned()) {
			swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}
	}

	/** A little hop, for good news. */
	public void hop() {
		if (!isDowned() && onGround() && !isInWater() && order != null && order.kind().isMode()) {
			getJumpControl().jump();
		}
	}

	// ------------------------------------------------------------------ talking

	/** Says a scripted line for a situation to the owner (or nearby players if unowned). */
	public void say(String key, Map<String, String> vars) {
		if (!(level() instanceof ServerLevel)) {
			return;
		}
		lastLineKey = key;
		String text = lines.pick(LineBank.of(archetype, castMember().map(io.github.tekless.hearthbound.core.persona.CastMember::id).orElse(null)), key, withDefaults(vars));
		sayLiteral(text);
	}

	/** Says a line only sometimes (and never over itself), for optional flavour. */
	public void sayMaybe(String key, Map<String, String> vars, double chance) {
		if (talkCooldown <= 0 && random.nextDouble() < chance * Math.max(0.0, Math.min(2.0, Hearthbound.config().ambientChatter))) {
			say(key, vars);
		}
	}

	public String lastLineKey() {
		return lastLineKey;
	}

	/** Shows an exact line (already validated) and remembers it. */
	public void sayLiteral(String text) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		memory.addCompanionLine(text);
		talkCooldown = 40;
		Component message = chatLine(text);
		// The owner always hears; so does whoever just spoke to us, and anyone standing close by.
		UUID speaker = lastSpeaker != null && serverLevel.getGameTime() - lastSpokeAt < 200 ? lastSpeaker : null;
		for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
			boolean near = player.level() == serverLevel && player.distanceTo(this) < HEARING_RANGE;
			if (isOwnedBy(player) || player.getUUID().equals(speaker) || near) {
				player.sendSystemMessage(message);
			}
		}
	}

	private Component chatLine(String text) {
		MutableComponent name = Component.literal(companionName()).withStyle(nameColour(archetype.id()));
		return Component.literal("<").append(name).append("> ").append(Component.literal(text));
	}

	public static ChatFormatting nameColour(String archetypeId) {
		return switch (archetypeId) {
			case "sellsword" -> ChatFormatting.RED;
			case "naturalist" -> ChatFormatting.GREEN;
			case "rogue" -> ChatFormatting.GOLD;
			case "guardian" -> ChatFormatting.AQUA;
			case "farmhand" -> ChatFormatting.YELLOW;
			case "mystic" -> ChatFormatting.LIGHT_PURPLE;
			default -> ChatFormatting.WHITE;
		};
	}

	/** RGB for HUD text, matching {@link #nameColour}. */
	public static int nameRgb(String archetypeId) {
		return switch (archetypeId) {
			case "sellsword" -> 0xFF5555;
			case "naturalist" -> 0x55FF55;
			case "rogue" -> 0xFFAA00;
			case "guardian" -> 0x55FFFF;
			case "farmhand" -> 0xFFFF55;
			case "mystic" -> 0xFF55FF;
			default -> 0xFFFFFF;
		};
	}

	private Map<String, String> withDefaults(Map<String, String> vars) {
		Map<String, String> all = new HashMap<>(vars);
		all.putIfAbsent("name", companionName());
		ServerPlayer owner = ownerPlayer();
		if (owner != null) {
			all.putIfAbsent("player", callName(owner));
		}
		all.putIfAbsent("max", "256");
		return all;
	}

	// ------------------------------------------------------------------ inventory and gear

	public SimpleContainer inventory() {
		return inventory;
	}

	public int countCarried(Predicate<ItemStack> filter) {
		int total = 0;
		for (ItemStack stack : inventory.getItems()) {
			if (!stack.isEmpty() && filter.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public boolean carriesAnything() {
		return !inventory.isEmpty();
	}

	public boolean hasRoomFor(Predicate<ItemStack> filter) {
		for (ItemStack stack : inventory.getItems()) {
			if (stack.isEmpty() || (filter.test(stack) && stack.getCount() < stack.getMaxStackSize())) {
				return true;
			}
		}
		return false;
	}

	/** Puts an item into the pack; whatever doesn't fit is dropped at the companion's feet. */
	public void storeOrDrop(ItemStack stack) {
		ItemStack rest = inventory.addItem(stack);
		if (!rest.isEmpty() && level() instanceof ServerLevel serverLevel) {
			spawnAtLocation(serverLevel, rest);
		}
	}

	/** Moves up to {@code max} matching items into the player's inventory (overflow dropped at their feet). */
	public int giveTo(Player player, Predicate<ItemStack> filter, int max) {
		int given = 0;
		for (int slot = 0; slot < inventory.getContainerSize() && given < max; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || !filter.test(stack)) {
				continue;
			}
			int take = Math.min(stack.getCount(), max - given);
			ItemStack moved = inventory.removeItem(slot, take);
			given += moved.getCount();
			if (!player.getInventory().add(moved) && !moved.isEmpty()) {
				player.drop(moved, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		return given;
	}

	/** Best tool in hand or pack for breaking this block, if any helps. */
	public Optional<ItemStack> bestToolFor(BlockState state) {
		ItemStack best = null;
		float bestSpeed = 1.0F;
		for (ItemStack stack : allCarried()) {
			if (stack.isEmpty() || nearlyBroken(stack)) {
				continue;
			}
			float speed = stack.getDestroySpeed(state);
			boolean correct = stack.isCorrectToolForDrops(state);
			if ((correct && (best == null || !best.isCorrectToolForDrops(state))) || speed > bestSpeed) {
				best = stack;
				bestSpeed = Math.max(speed, bestSpeed);
			}
		}
		return Optional.ofNullable(best);
	}

	private List<ItemStack> allCarried() {
		List<ItemStack> all = new java.util.ArrayList<>(inventory.getItems());
		all.add(getMainHandItem());
		return all;
	}

	/** Swaps the best tool into the main hand while working (the weapon goes into the pack). */
	public void holdBestToolFor(BlockState state) {
		Optional<ItemStack> tool = bestToolFor(state);
		if (tool.isEmpty() || tool.get() == getMainHandItem()) {
			return;
		}
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot) == tool.get()) {
				ItemStack previous = getMainHandItem();
				setItemSlot(EquipmentSlot.MAINHAND, tool.get());
				inventory.setItem(slot, previous);
				if (toolSlotSwapped < 0) {
					toolSlotSwapped = slot;
				}
				return;
			}
		}
	}

	/** Puts the best weapon back in hand after working. */
	public void restoreWeapon() {
		toolSlotSwapped = -1;
		equipBestWeapon();
	}

	private void equipBestGear() {
		if (!(order instanceof GatherOrder) && !(order instanceof MineOrder) && !isRanged(getMainHandItem())) {
			equipBestWeapon();
		}
		equipShield();
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (!getItemBySlot(slot).isEmpty()) {
				continue;
			}
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (!stack.isEmpty() && getEquipmentSlotForItem(stack) == slot && !nearlyBroken(stack)) {
					setItemSlot(slot, inventory.removeItemNoUpdate(i));
					break;
				}
			}
		}
	}

	/** A shield from the pack goes in the off hand, if the off hand is free. */
	private void equipShield() {
		if (!getOffhandItem().isEmpty()) {
			return;
		}
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS) && !nearlyBroken(stack)) {
				setItemSlot(EquipmentSlot.OFFHAND, inventory.removeItemNoUpdate(i));
				return;
			}
		}
	}

	private void equipBestWeapon() {
		ItemStack held = getMainHandItem();
		int bestSlot = -1;
		int bestRank = weaponRank(held);
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			int rank = weaponRank(inventory.getItem(i));
			if (rank > bestRank) {
				bestRank = rank;
				bestSlot = i;
			}
		}
		if (bestSlot >= 0) {
			ItemStack weapon = inventory.getItem(bestSlot);
			inventory.setItem(bestSlot, held);
			setItemSlot(EquipmentSlot.MAINHAND, weapon);
		}
	}

	private static int weaponRank(ItemStack stack) {
		if (stack.isEmpty() || nearlyBroken(stack)) {
			return 0;
		}
		int base = stack.is(ItemTags.SWORDS) ? 20 : stack.is(ItemTags.AXES) ? 10 : 0;
		if (base == 0) {
			return 0;
		}
		String id = stack.getItem().toString();
		int tier = id.contains("netherite") ? 6 : id.contains("diamond") ? 5 : id.contains("iron") ? 4 : id.contains("copper") ? 3
				: id.contains("stone") ? 2 : id.contains("gold") ? 1 : 1;
		return base + tier;
	}

	public long lastGiftTime() {
		return lastGiftTime;
	}

	public void setLastGiftTime(long time) {
		lastGiftTime = time;
	}

	/** Re-checks gear on the next equipment pass (after receiving a weapon or armour). */
	public void equipFromPackSoon() {
		if (!(order instanceof GatherOrder) && !(order instanceof MineOrder)) {
			equipBestGear();
		}
	}

	public void openInventory(ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider((id, playerInventory, p) -> new ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x2, id, playerInventory, inventory, 2),
				Texts.tr("container.hearthbound.companion", companionName())));
	}

	// ------------------------------------------------------------------ interaction

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown() && isOwnedBy(player)) {
			openInventory(serverPlayer);
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			getLookControl().setLookAt(player);
			noteSpeaker(serverPlayer);
			Conversations.beginChat(serverPlayer, this);
			say(greetingKey(player), Map.of("player", callName(player)));
			wave();
			maybeHintRecruit(serverPlayer);
			return InteractionResult.SUCCESS;
		}
		if (isOwnedBy(player) && !Gifts.isTool(held)) {
			Gifts.receive(this, serverPlayer, held);
			return InteractionResult.SUCCESS;
		}
		if (io.github.tekless.hearthbound.life.Favours.receive(this, serverPlayer, held)) {
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** Tells a stranger how to ask this companion along (at most every five minutes). */
	private void maybeHintRecruit(ServerPlayer player) {
		if (isRecruited()) {
			return;
		}
		long now = level().getGameTime();
		Long last = hintedAt.get(player.getUUID());
		if (last == null || now - last > 6000) {
			hintedAt.put(player.getUUID(), now);
			player.sendSystemMessage(Texts.tr("message.hearthbound.recruit_hint", companionName().split(" ")[0])
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	private String greetingKey(Player player) {
		if (!isOwnedBy(player)) {
			return "greet.stranger";
		}
		int affinity = memory.affinity();
		return affinity >= 25 ? "greet.friend" : affinity <= -20 ? "greet.cold" : "greet.owner";
	}

	// ------------------------------------------------------------------ damage, downed state, death

	public boolean isDowned() {
		return entityData.get(DATA_DOWNED);
	}

	private void setDowned(boolean downed) {
		entityData.set(DATA_DOWNED, downed);
		if (downed) {
			downedTicks = DOWNED_TICKS;
			getNavigation().stop();
			setTarget(null);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		io.github.tekless.hearthbound.config.HearthboundConfig.Server rules = Hearthbound.config().server;
		if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && (rules.godmode || (source.getEntity() instanceof Player && !rules.playersCanHurt))) {
			return false; // god mode, or players can't hurt companions on this world (both set by the host)
		}
		if (source.getEntity() instanceof Player player && !isOwnedBy(player) && !Hearthbound.config().server.otherPlayersCanHurt) {
			return false; // someone else's companion (or a hub resident) is off limits; ops can still use /kill
		}
		if (source.getEntity() instanceof Player player && isOwnedBy(player) && !player.isCreative()) {
			// Friendly fire: a small grudge, never a fight back.
			memory.changeAffinity(-2);
			if (talkCooldown <= 0) {
				say("hurt_by_player", Map.of());
			}
			return super.hurtServer(level, source, Math.min(damage, 1.0F));
		}
		if (isDowned() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		if (blocksWithShield(source)) {
			return super.hurtServer(level, source, damage); // the shield takes it, the game works out how much
		}
		boolean essential = Hearthbound.config().essentialCompanions();
		if (essential && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && damage >= getHealth()) {
			setHealth(1.0F);
			setDowned(true);
			say("downed", Map.of());
			memory.addEvent("I got knocked down by " + (source.getEntity() != null ? (source.getEntity() instanceof Player killer ? killer.getName().getString() : English.withArticle(source.getEntity().getName().getString())) : "something"));
			return true;
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && isAlive() && getHealth() > 0 && getHealth() / getMaxHealth() < archetype.retreatHealth() && talkCooldown <= 0) {
			say("combat.low_health", Map.of());
		}
		return hurt;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		for (ItemStack stack : inventory.removeAllItems()) {
			spawnAtLocation(level, stack);
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = getItemBySlot(slot);
			if (!stack.isEmpty()) {
				spawnAtLocation(level, stack);
				setItemSlot(slot, ItemStack.EMPTY);
			}
		}
	}

	@Override
	public void die(DamageSource source) {
		ServerPlayer owner = ownerPlayer();
		super.die(source);
		if (owner != null) {
			owner.sendSystemMessage(Texts.tr("message.hearthbound.died", companionName()).withStyle(ChatFormatting.GRAY));
		}
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		if (getCustomName() == null) {
			// Usually one of the named cast who isn't already around; sometimes a wanderer in a default skin.
			java.util.List<io.github.tekless.hearthbound.core.persona.CastMember> free = new java.util.ArrayList<>();
			CompanionRoster roster = CompanionRoster.get(level.getLevel().getServer());
			for (io.github.tekless.hearthbound.core.persona.CastMember member : io.github.tekless.hearthbound.core.persona.CastMember.values()) {
				if (!roster.castExists(member)) {
					free.add(member);
				}
			}
			if (!free.isEmpty() && random.nextInt(4) != 0) {
				initIdentity(free.get(random.nextInt(free.size())));
			} else {
				Archetype rolled = Archetype.values()[random.nextInt(Archetype.values().length)];
				String name = rolled.names().get(random.nextInt(rolled.names().size()));
				CompanionSkin skin = CompanionSkin.values()[random.nextInt(CompanionSkin.values().length)];
				initIdentity(rolled, name, skin, random.nextBoolean());
			}
			giveStarterGear();
		}
		home = blockPosition();
		return data;
	}

	/** A weapon (and a piece of armour for fighters) that suits the personality. */
	public void giveStarterGear() {
		switch (archetype) {
			case SELLSWORD -> {
				setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
				setItemSlot(EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.CHAINMAIL_CHESTPLATE));
			}
			case GUARDIAN -> {
				setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
				setItemSlot(EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
			}
			case ROGUE, MYSTIC -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.STONE_SWORD));
			case NATURALIST -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.STONE_AXE));
			case FARMHAND -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.STONE_SHOVEL));
		}
	}

	// ------------------------------------------------------------------ saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("HearthboundVersion", SAVE_VERSION);
		output.putString("Archetype", archetype.id());
		output.putInt("Skin", skinVariant());
		if (ownerId != null) {
			output.putString("Owner", ownerId.toString());
		}
		if (home != null) {
			output.putIntArray("Home", new int[] {home.getX(), home.getY(), home.getZ()});
		}
		output.putString("BaseMode", baseMode.name());
		if (ownerName() != null) {
			output.putString("OwnerName", ownerName());
		}
		output.putBoolean("HubResident", hubResident);
		if (waitPost != null) {
			output.putIntArray("WaitPost", new int[] {waitPost.getX(), waitPost.getY(), waitPost.getZ()});
		}
		if (order instanceof GatherOrder gather && !gather.status().isFinished()) {
			ValueOutput task = output.child("GatherTask");
			task.putString("Item", gather.spec().key());
			task.putInt("Goal", gather.goal());
			task.putInt("Baseline", gather.baseline());
		}
		output.putInt("Downed", isDowned() ? downedTicks : 0);
		output.putIntArray("Needs", new int[] {needs.hunger(), needs.tiredness(), needs.loneliness()});
		output.putBoolean("Stocked", stocked);
		if (gardenAt != null) {
			output.putIntArray("Garden", new int[] {gardenAt.getX(), gardenAt.getY(), gardenAt.getZ(), gardenPlanted});
			output.putLong("GardenDay", gardenDay);
		}
		inventory.storeAsItemList(output.list("Inventory", ItemStack.CODEC));
		ValueOutput.TypedOutputList<String> chat = output.list("Conversation", com.mojang.serialization.Codec.STRING);
		for (CompanionMemory.Line line : memory.lines()) {
			chat.add((line.fromPlayer() ? "P:" : "C:") + line.text());
		}
		ValueOutput.TypedOutputList<String> events = output.list("Events", com.mojang.serialization.Codec.STRING);
		memory.events().forEach(events::add);
		ValueOutput.TypedOutputList<String> facts = output.list("Facts", com.mojang.serialization.Codec.STRING);
		memory.allFacts().forEach((id, known) -> {
			known.nickname().ifPresent(name -> facts.add(id + "|N|" + name));
			known.likes().forEach(thing -> facts.add(id + "|L|" + thing));
			known.dislikes().forEach(thing -> facts.add(id + "|D|" + thing));
		});
		output.putInt("Affinity", memory.affinity());
		if (memory.partner() != null) {
			output.putString("ConversationWith", memory.partner());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setArchetype(Archetype.byId(input.getStringOr("Archetype", "sellsword")).orElse(Archetype.SELLSWORD));
		setSkinVariant(input.getIntOr("Skin", 0));
		// Cast characters renamed in 0.4 keep their skin: carry them over to their new name and voice.
		io.github.tekless.hearthbound.core.persona.CastMember.bySkinVariant(skinVariant()).filter(member -> member.answersTo(companionName())).ifPresent(member -> {
			if (!member.displayName().equals(companionName())) {
				setCustomName(Component.literal(member.displayName()));
			}
			setArchetype(member.archetype());
		});
		applyRoleStats();
		ownerId = input.getString("Owner").map(UUID::fromString).orElse(null);
		entityData.set(DATA_OWNER_NAME, ownerId == null ? "" : input.getStringOr("OwnerName", ""));
		hubResident = input.getBooleanOr("HubResident", false);
		home = input.getIntArray("Home").filter(a -> a.length == 3).map(a -> new BlockPos(a[0], a[1], a[2])).orElse(null);
		waitPost = input.getIntArray("WaitPost").filter(a -> a.length == 3).map(a -> new BlockPos(a[0], a[1], a[2])).orElse(null);
		try {
			baseMode = CompanionOrder.Kind.valueOf(input.getStringOr("BaseMode", isRecruited() ? "FOLLOW" : "HOME"));
		} catch (IllegalArgumentException e) {
			baseMode = isRecruited() ? CompanionOrder.Kind.FOLLOW : CompanionOrder.Kind.HOME;
		}
		inventory.clearContent();
		input.list("Inventory", ItemStack.CODEC).ifPresent(inventory::fromItemList);
		memory.clear();
		input.listOrEmpty("Conversation", com.mojang.serialization.Codec.STRING).forEach(entry -> {
			if (entry.startsWith("P:")) {
				memory.addPlayerLine(entry.substring(2));
			} else if (entry.startsWith("C:")) {
				memory.addCompanionLine(entry.substring(2));
			}
		});
		input.listOrEmpty("Events", com.mojang.serialization.Codec.STRING).forEach(memory::addEvent);
		input.listOrEmpty("Facts", com.mojang.serialization.Codec.STRING).forEach(entry -> {
			String[] parts = entry.split("\\|", 3);
			if (parts.length == 3) {
				PlayerFacts known = memory.factsAbout(parts[0]);
				switch (parts[1]) {
					case "N" -> known.setNickname(parts[2]);
					case "L" -> known.like(parts[2]);
					case "D" -> known.dislike(parts[2]);
					default -> {
					}
				}
			}
		});
		memory.setAffinity(input.getIntOr("Affinity", 0));
		input.getString("ConversationWith").ifPresent(memory::setPartner);
		input.getIntArray("Needs").filter(a -> a.length == 3).ifPresent(a -> {
			io.github.tekless.hearthbound.core.life.Needs saved = new io.github.tekless.hearthbound.core.life.Needs(a[0], a[1], a[2]);
			needs.copyFrom(saved);
		});
		stocked = input.getBooleanOr("Stocked", false);
		input.getIntArray("Garden").filter(a -> a.length == 4).ifPresent(a -> {
			gardenAt = new BlockPos(a[0], a[1], a[2]);
			gardenPlanted = a[3];
		});
		gardenDay = input.getLongOr("GardenDay", -1L);
		int downed = input.getIntOr("Downed", 0);
		if (downed > 0) {
			entityData.set(DATA_DOWNED, true);
			downedTicks = downed;
		}
		order = createBaseOrder();
		input.child("GatherTask").ifPresent(task -> {
			Optional<GatherSpec> spec = GatherCatalog.byKey(task.getStringOr("Item", ""));
			int goal = task.getIntOr("Goal", 0);
			if (spec.isPresent() && goal > 0 && isRecruited()) {
				order = new GatherOrder(this, spec.get(), goal, task.getIntOr("Baseline", 0));
			}
		});
	}

	// ------------------------------------------------------------------ fighting at range, shields, food and worn gear

	private int eatTicks;
	private ItemStack eating = ItemStack.EMPTY;
	private int eatCooldown;

	/** Food a companion won't eat (or give anyone). */
	public static final Set<net.minecraft.world.item.Item> BAD_FOOD = Set.of(net.minecraft.world.item.Items.ROTTEN_FLESH,
			net.minecraft.world.item.Items.SPIDER_EYE, net.minecraft.world.item.Items.POISONOUS_POTATO, net.minecraft.world.item.Items.PUFFERFISH,
			net.minecraft.world.item.Items.SUSPICIOUS_STEW, net.minecraft.world.item.Items.CHORUS_FRUIT);

	/** Worn so far that one or two more uses would break it. */
	public static boolean nearlyBroken(ItemStack stack) {
		return stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= Math.max(2, stack.getMaxDamage() / 40);
	}

	public static boolean isRanged(ItemStack stack) {
		return stack.getItem() instanceof net.minecraft.world.item.BowItem || stack.getItem() instanceof net.minecraft.world.item.CrossbowItem;
	}

	/** A bow or crossbow in the pack that isn't about to break, or empty. */
	private ItemStack rangedInPack() {
		for (ItemStack stack : inventory.getItems()) {
			if (isRanged(stack) && !nearlyBroken(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	public boolean hasArrows() {
		return countCarried(s -> s.is(ItemTags.ARROWS)) > 0;
	}

	/** True while eating something from the pack. */
	public boolean isEating() {
		return eatTicks > 0;
	}

	/** Takes a bow or crossbow out of the pack (the sword goes in). */
	private void holdRanged() {
		if (isRanged(getMainHandItem())) {
			return;
		}
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (isRanged(stack) && !nearlyBroken(stack)) {
				ItemStack previous = getMainHandItem();
				setItemSlot(EquipmentSlot.MAINHAND, stack);
				inventory.setItem(slot, previous);
				return;
			}
		}
	}

	/** Nobody friendly in the way: no player, companion, villager, golem or pet between us and the target. */
	private boolean clearShot(LivingEntity target) {
		Vec3 from = getEyePosition();
		Vec3 to = target.getEyePosition();
		for (net.minecraft.world.entity.Entity other : level().getEntities(this, new net.minecraft.world.phys.AABB(from, to).inflate(1.0),
				e -> e != target && (e instanceof Player || e instanceof CompanionEntity || e instanceof net.minecraft.world.entity.npc.villager.AbstractVillager
						|| e instanceof net.minecraft.world.entity.animal.golem.IronGolem || e instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame()))) {
			if (other.getBoundingBox().inflate(0.4).clip(from, to).isPresent()) {
				return false;
			}
		}
		return true;
	}

	/** One arrow from the pack, from the bow or crossbow in hand, at the target. */
	private void shoot(LivingEntity target, ItemStack weapon) {
		ItemStack arrows = null;
		for (ItemStack stack : inventory.getItems()) {
			if (!stack.isEmpty() && stack.is(ItemTags.ARROWS)) {
				arrows = stack;
				break;
			}
		}
		if (arrows == null || !(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		boolean crossbow = weapon.getItem() instanceof net.minecraft.world.item.CrossbowItem;
		net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow = net.minecraft.world.entity.projectile.ProjectileUtil.getMobArrow(this,
				arrows.copyWithCount(1), 1.0F, weapon);
		arrow.pickup = net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.ALLOWED; // a real arrow: you can pick it up again
		double dx = target.getX() - getX();
		double dz = target.getZ() - getZ();
		double dy = target.getY(0.3333) - arrow.getY();
		double flat = Math.sqrt(dx * dx + dz * dz);
		arrow.shoot(dx, dy + flat * (crossbow ? 0.1 : 0.2), dz, crossbow ? 3.15F : 1.6F, 2.0F);
		serverLevel.addFreshEntity(arrow);
		playSound(crossbow ? net.minecraft.sounds.SoundEvents.CROSSBOW_SHOOT : net.minecraft.sounds.SoundEvents.ARROW_SHOOT, 1.0F,
				1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
		arrows.shrink(1);
		weapon.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
	}

	/** Up with the shield while something hostile closes in or takes aim; down again to swing. */
	private void guardWithShield() {
		ItemStack offhand = getOffhandItem();
		LivingEntity target = getTarget();
		boolean raise = false;
		if (offhand.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS) && target != null && target.isAlive() && !isDowned()
				&& !(isUsingItem() && getUsedItemHand() == InteractionHand.MAIN_HAND)) {
			double distance = distanceTo(target);
			boolean shooter = target instanceof net.minecraft.world.entity.monster.RangedAttackMob;
			raise = (shooter && distance > 2.8 && distance < 24 && hasLineOfSight(target)) || (distance > 2.8 && distance < 7);
		}
		if (raise) {
			getLookControl().setLookAt(target, 30.0F, 30.0F); // face what you're shielding against
		}
		if (raise && !isUsingItem()) {
			startUsingItem(InteractionHand.OFF_HAND);
		} else if (!raise && isUsingItem() && getUsedItemHand() == InteractionHand.OFF_HAND) {
			stopUsingItem();
		}
	}

	/** The shield is up and the hit comes from in front (and isn't the kind shields can't stop). */
	private boolean blocksWithShield(DamageSource source) {
		if (!isBlocking() || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.getSourcePosition() == null
				|| source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow && arrow.getPierceLevel() > 0) {
			return false;
		}
		// The same rule the game uses: the hit has to come from in front of where the head is facing. A little margin, so
		// anything this lets through to the shield really is blocked.
		Vec3 towards = source.getSourcePosition().subtract(position()).multiply(1, 0, 1);
		Vec3 look = calculateViewVector(0.0F, getYHeadRot());
		return towards.lengthSqr() > 1.0E-4 && towards.normalize().dot(look) > 0.1;
	}

	/** Hurt and carrying food: eat some (a couple of seconds of munching), which heals as much as the food is worth. */
	private void eatWhenHurt(ServerLevel level) {
		if (eatTicks > 0) {
			eatTicks--;
			if (eatTicks % 4 == 0) {
				level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EAT.value(), net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F,
						0.9F + getRandom().nextFloat() * 0.2F);
				level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, eating.getItem()),
						getX(), getEyeY() - 0.2, getZ(), 4, 0.15, 0.1, 0.15, 0.05);
			}
			if (eatTicks == 0) {
				for (ItemStack stack : inventory.getItems()) {
					if (!stack.isEmpty() && stack.is(eating.getItem())) {
						net.minecraft.world.food.FoodProperties food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
						stack.shrink(1);
						heal(food == null ? 2.0F : Math.max(1, food.nutrition()));
						level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_BURP, net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 1.0F);
						break;
					}
				}
				eating = ItemStack.EMPTY;
				eatCooldown = 40;
			}
			return;
		}
		if (eatCooldown > 0) {
			eatCooldown--;
			return;
		}
		if (tickCount % 20 != 0 || isDowned() || isSleeping() || getHealth() >= getMaxHealth()) {
			return;
		}
		float share = getHealth() / getMaxHealth();
		if (share < (getTarget() != null ? 0.35F : 0.6F)) {
			ItemStack food = bestFoodInPack();
			if (food != null) {
				eating = food.copyWithCount(1);
				eatTicks = 32;
				swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			}
		}
	}

	/** The most filling food in the pack (never anything that would make them ill). */
	public @Nullable ItemStack bestFoodInPack() {
		ItemStack best = null;
		int bestValue = 0;
		for (ItemStack stack : inventory.getItems()) {
			net.minecraft.world.food.FoodProperties food = stack.isEmpty() ? null : stack.get(net.minecraft.core.component.DataComponents.FOOD);
			if (food != null && !BAD_FOOD.contains(stack.getItem()) && food.nutrition() > bestValue) {
				best = stack;
				bestValue = food.nutrition();
			}
		}
		return best;
	}

	/**
	 * Before something breaks: a spare of the same kind from the pack takes its place. With no spare it goes into the
	 * pack, unbroken, and they say so once a day. (Mending happens at an anvil, see Pastimes.)
	 */
	private void careForGear(ServerLevel level) {
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
				EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack worn = getItemBySlot(slot);
			if (worn.isEmpty() || !nearlyBroken(worn)) {
				continue;
			}
			if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) && isUsingItem()) {
				stopUsingItem();
			}
			int spare = -1;
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (!stack.isEmpty() && !nearlyBroken(stack) && sameKind(stack, worn, slot)) {
					spare = i;
					break;
				}
			}
			String name = gearName(worn);
			if (spare >= 0) {
				ItemStack replacement = inventory.getItem(spare);
				inventory.setItem(spare, worn);
				setItemSlot(slot, replacement);
				sayMaybe("gear.swapped", Map.of("item", name), 0.8);
			} else {
				setItemSlot(slot, ItemStack.EMPTY);
				storeOrDrop(worn);
				if (tryEvery("worn:" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(worn.getItem()), clockTime() / 24000L, 1)) {
					say("gear.worn", Map.of("item", name));
				}
			}
		}
	}

	private static String gearName(ItemStack stack) {
		String name = stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
		return name.contains(".") ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().replace('_', ' ') : name;
	}

	private boolean sameKind(ItemStack candidate, ItemStack worn, EquipmentSlot slot) {
		if (slot.isArmor()) {
			return getEquipmentSlotForItem(candidate) == slot;
		}
		for (net.minecraft.tags.TagKey<net.minecraft.world.item.Item> kind : List.of(ItemTags.SWORDS, ItemTags.AXES, ItemTags.PICKAXES, ItemTags.SHOVELS, ItemTags.HOES)) {
			if (worn.is(kind)) {
				return candidate.is(kind);
			}
		}
		if (worn.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)) {
			return candidate.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
		}
		return candidate.getItem().getClass() == worn.getItem().getClass();
	}

	/** Gear worn down by a quarter or more, with the material to mend it in the pack, or null. */
	public @Nullable ItemStack mendable() {
		for (ItemStack stack : gear()) {
			if (!stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() / 4 && repairMaterial(stack) != null) {
				return stack;
			}
		}
		return null;
	}

	/** Everything they carry or wear. */
	private List<ItemStack> gear() {
		List<ItemStack> all = new java.util.ArrayList<>(inventory.getItems());
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			all.add(getItemBySlot(slot));
		}
		return all;
	}

	private @Nullable ItemStack repairMaterial(ItemStack worn) {
		for (ItemStack stack : inventory.getItems()) {
			if (!stack.isEmpty() && stack != worn && worn.isValidRepairItem(stack)) {
				return stack;
			}
		}
		return null;
	}

	/**
	 * Mends worn gear at an anvil, a material at a time, each putting back a quarter of the wear (as an anvil does for
	 * you). Each use can chip the anvil. Companions have no experience to spend, so it costs only the material.
	 *
	 * @return how many materials were used
	 */
	public int repairAt(ServerLevel level, BlockPos anvil) {
		int used = 0;
		while (used < 16) {
			ItemStack worn = null;
			for (ItemStack stack : gear()) {
				if (!stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() >= Math.max(1, stack.getMaxDamage() / 8) && repairMaterial(stack) != null) {
					worn = stack;
					break;
				}
			}
			BlockState state = level.getBlockState(anvil);
			if (worn == null || !state.is(BlockTags.ANVIL)) {
				break;
			}
			ItemStack material = repairMaterial(worn);
			if (material == null) {
				break;
			}
			material.shrink(1);
			worn.setDamageValue(Math.max(0, worn.getDamageValue() - worn.getMaxDamage() / 4));
			used++;
			level.playSound(null, anvil, net.minecraft.sounds.SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
			if (getRandom().nextFloat() < 0.12F) {
				BlockState chipped = net.minecraft.world.level.block.AnvilBlock.damage(state);
				if (chipped == null) {
					level.removeBlock(anvil, false);
					level.playSound(null, anvil, net.minecraft.sounds.SoundEvents.ANVIL_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
					break;
				}
				level.setBlockAndUpdate(anvil, chipped);
			}
		}
		if (used > 0) {
			swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}
		return used;
	}

	/** Damage from a companion only ever reaches the one they're fighting (never a friend caught by an arrow). */
	public boolean mayHarm(net.minecraft.world.entity.Entity victim) {
		if (victim == getTarget()) {
			return true;
		}
		boolean friendly = victim instanceof Player || victim instanceof CompanionEntity || victim instanceof net.minecraft.world.entity.npc.villager.AbstractVillager
				|| victim instanceof net.minecraft.world.entity.animal.golem.IronGolem || victim instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame();
		return !friendly || victim.getUUID().equals(directedId);
	}

	// ------------------------------------------------------------------ goals

	/**
	 * Guardians with a bow or crossbow and arrows in the pack shoot from range, standing their ground when they can see the
	 * target; up close (or out of arrows) the sword takes over.
	 */
	private final class RangedGoal extends Goal {
		private int draw;
		private int cooldown;

		RangedGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = getTarget();
			return target != null && target.isAlive() && !isDowned() && role() == io.github.tekless.hearthbound.core.persona.Role.GUARDIAN
					&& distanceTo(target) > 6 && distanceTo(target) < 24 && hasArrows()
					&& (isRanged(getMainHandItem()) || !rangedInPack().isEmpty()) && hasLineOfSight(target);
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = getTarget();
			return target != null && target.isAlive() && !isDowned() && distanceTo(target) > 4 && distanceTo(target) < 28 && hasArrows()
					&& isRanged(getMainHandItem()) && !nearlyBroken(getMainHandItem());
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			holdRanged();
			draw = 0;
			cooldown = 8;
			setAggressive(true);
		}

		@Override
		public void stop() {
			if (isUsingItem() && getUsedItemHand() == InteractionHand.MAIN_HAND) {
				stopUsingItem();
			}
			setAggressive(false);
			restoreWeapon();
		}

		@Override
		public void tick() {
			LivingEntity target = getTarget();
			if (target == null) {
				return;
			}
			getLookControl().setLookAt(target, 30.0F, 30.0F);
			boolean sees = hasLineOfSight(target);
			if (!sees || distanceTo(target) > 16) {
				getNavigation().moveTo(target, 1.0);
			} else {
				getNavigation().stop();
			}
			if (cooldown > 0) {
				cooldown--;
				return;
			}
			ItemStack weapon = getMainHandItem();
			if (!isUsingItem()) {
				startUsingItem(InteractionHand.MAIN_HAND);
			}
			boolean crossbow = weapon.getItem() instanceof net.minecraft.world.item.CrossbowItem;
			if (++draw >= (crossbow ? 25 : 20) && sees && clearShot(target)) {
				shoot(target, weapon);
				stopUsingItem();
				draw = 0;
				cooldown = crossbow ? 20 : 12;
			}
		}
	}

	/** While downed: no moving, no fighting, just recovering. */
	private final class DownedGoal extends Goal {
		DownedGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP, Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			return isDowned();
		}

		@Override
		public void start() {
			getNavigation().stop();
		}
	}

	/** Runs the current order whenever nothing more urgent (fighting, fleeing) needs the legs. */
	private final class OrderGoal extends Goal {
		OrderGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return !isDowned() && getTarget() == null && level() instanceof ServerLevel;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			order().tick(CompanionEntity.this);
		}

		@Override
		public void stop() {
			if (order != null) {
				order.pause(CompanionEntity.this);
			}
		}
	}

	/** Below the archetype's retreat threshold: break off and run towards the owner. */
	private final class RetreatGoal extends Goal {
		private int ticks;

		RetreatGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return !isDowned() && getLastHurtByMob() != null && getHealth() / getMaxHealth() < archetype.retreatHealth()
					&& tickCount - getLastHurtByMobTimestamp() < 100;
		}

		@Override
		public void start() {
			ticks = 0;
			setTarget(null);
			say("combat.retreat", Map.of());
			moveAway();
		}

		@Override
		public boolean canContinueToUse() {
			return ticks < 100 && !isDowned();
		}

		@Override
		public void tick() {
			ticks++;
			if (ticks % 20 == 0) {
				moveAway();
			}
		}

		private void moveAway() {
			ServerPlayer owner = ownerPlayer();
			LivingEntity threat = getLastHurtByMob();
			if (owner != null && owner.level() == level() && distanceTo(owner) > 3) {
				getNavigation().moveTo(owner, 1.4);
			} else if (threat != null) {
				Vec3 away = position().subtract(threat.position()).normalize().scale(8).add(position());
				getNavigation().moveTo(away.x, away.y, away.z, 1.4);
			}
		}
	}

	/** Wander gently around home when not travelling with anyone. */
	private final class IdleStrollGoal extends WaterAvoidingRandomStrollGoal {
		IdleStrollGoal() {
			super(CompanionEntity.this, 0.6);
		}

		@Override
		public boolean canUse() {
			return !isRecruited() && !isDowned() && !isSleeping() && super.canUse()
					&& (home == null || home.distSqr(blockPosition()) < 100)
					&& !(order instanceof HomeOrder homeOrder && homeOrder.activity() != null); // daily life decides where they go
		}
	}

	/** Attack whatever just hurt the owner. */
	private final class DefendOwnerGoal extends TargetGoal {
		private LivingEntity attacker;
		private int timestamp;

		DefendOwnerGoal() {
			super(CompanionEntity.this, false);
			setFlags(EnumSet.of(Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			if (archetype.stance() == Archetype.CombatStance.DEFENSIVE || isDowned()) {
				return false;
			}
			ServerPlayer owner = ownerPlayer();
			if (owner == null || owner.level() != level()) {
				return false;
			}
			attacker = owner.getLastHurtByMob();
			return attacker != null && owner.getLastHurtByMobTimestamp() != timestamp && !(attacker instanceof CompanionEntity)
					&& canAttack(attacker, TargetingConditions.DEFAULT) && distanceTo(attacker) < 24;
		}

		@Override
		public void start() {
			mob.setTarget(attacker);
			ServerPlayer owner = ownerPlayer();
			if (owner != null) {
				timestamp = owner.getLastHurtByMobTimestamp();
			}
			if (talkCooldown <= 0) {
				say("combat.engage", Map.of("mob", attacker.getName().getString().toLowerCase()));
			}
			super.start();
		}
	}

	/** Join in on whatever the owner is hitting (except things the archetype won't fight). */
	private final class AssistOwnerGoal extends TargetGoal {
		private LivingEntity victim;
		private int timestamp;

		AssistOwnerGoal() {
			super(CompanionEntity.this, false);
			setFlags(EnumSet.of(Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			Archetype.CombatStance stance = archetype.stance();
			if (stance == Archetype.CombatStance.DEFENSIVE || stance == Archetype.CombatStance.CAUTIOUS || isDowned()) {
				return false;
			}
			ServerPlayer owner = ownerPlayer();
			if (owner == null || owner.level() != level()) {
				return false;
			}
			victim = owner.getLastHurtMob();
			return victim != null && owner.getLastHurtMobTimestamp() != timestamp && victim instanceof Enemy
					&& canAttack(victim, TargetingConditions.DEFAULT) && distanceTo(victim) < 24;
		}

		@Override
		public void start() {
			mob.setTarget(victim);
			ServerPlayer owner = ownerPlayer();
			if (owner != null) {
				timestamp = owner.getLastHurtMobTimestamp();
			}
			super.start();
		}
	}
}

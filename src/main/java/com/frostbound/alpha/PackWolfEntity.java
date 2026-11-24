package com.frostbound.alpha;

import com.frostbound.config.FrostboundConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.passive.WolfVariant;
import net.minecraft.entity.passive.WolfVariants;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A wolf in an Alpha's pack: hostile, untameable, and organised. It never acts alone:
 *
 *  - It takes its target from the Alpha (and the Alpha from it), so the whole pack turns on whoever
 *    one wolf picks a fight with.
 *  - Only a few pack wolves lunge at a time ({@link AlphaWolfEntity#tryClaimLunge}); the rest circle
 *    the target and wait their turn, then fall back after their bite (hit and run). While the Alpha
 *    makes a big move the pack gives it room, and when it howls they howl with it.
 *  - When it dies, the rest of the pack is enraged ({@link AlphaWolfEntity#enrage}).
 *  - Without its Alpha (killed, or gone for {@link #ALPHA_GRACE_TICKS}) it {@link #scatter scatters}:
 *    runs from the nearest player and vanishes once it has got away.
 *  - A den pack ({@link #getDen}) sleeps in its den until something attacks one of them, then the
 *    whole pack wakes ({@link AlphaWolfEntity#wake}). After a fight it walks back to its own spot
 *    ({@link #getBed}) and lies down again.
 *
 * What it's doing ({@link WolfAction}) is synced, so the client can pose it for every tell.
 * Extends the vanilla wolf for its model, animations and sounds; everything tame (bones, sitting,
 * breeding, collars, leads, wolf armour) is switched off.
 */
public class PackWolfEntity extends WolfEntity {
	/** How long a wolf waits for an Alpha it can't find (e.g. not loaded yet) before it scatters. */
	public static final int ALPHA_GRACE_TICKS = 200;
	/** A scattering wolf vanishes once no player is this close (after a short head start), or after {@link #MAX_SCATTER_TICKS}. */
	public static final double ESCAPED_DISTANCE = 24.0;
	public static final int MAX_SCATTER_TICKS = 200;
	/** Ticks a pack wolf takes to get up; the Alpha takes {@link AlphaWolfEntity#WAKE_TICKS}. */
	public static final int WAKE_TICKS = 12;

	private static final TrackedData<Byte> ACTION = DataTracker.registerData(PackWolfEntity.class, TrackedDataHandlerRegistry.BYTE);

	@Nullable
	private UUID alphaUuid;
	@Nullable
	private AlphaWolfEntity alphaCache;
	private int alphaMissingTicks;
	private boolean scattering;
	int scatterTicks;
	/** Ticks before this wolf may lunge again. */
	int lungeCooldown;
	/** True while its lunge runs; the Alpha counts these against simultaneousAttackers. */
	boolean lunging;

	/** The den this pack sleeps in (its centre), or null for a roaming pack. */
	@Nullable
	private BlockPos den;
	/** Where in the den this wolf lies down. */
	@Nullable
	private BlockPos bed;
	/** Ticks spent in the current action (server). */
	int actionTicks;
	/** Ticks until a sleeping wolf starts to get up (the pack wakes in a ripple), or -1. */
	private int pendingWake = -1;
	@Nullable
	private LivingEntity wakeTarget;
	/** Ticks awake with nothing to fight. */
	int calmTicks;

	/** Client: when the action last changed (in age ticks) and what it was before, for blending poses. */
	private int actionStartAge;
	private WolfAction previousAction = WolfAction.NONE;

	public PackWolfEntity(EntityType<? extends PackWolfEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createPackWolfAttributes() {
		FrostboundConfig.AlphaWolfOptions c = FrostboundConfig.get().alphaWolf;
		return WolfEntity.createWolfAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, c.packWolfHealth)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, c.packWolfDamage)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ACTION, (byte) WolfAction.NONE.ordinal());
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new PackGoals.RestGoal(this));
		this.goalSelector.add(1, new PackGoals.ScatterGoal(this));
		this.goalSelector.add(2, new PackGoals.ChorusGoal(this));
		this.goalSelector.add(3, new PackGoals.LungeGoal(this));
		this.goalSelector.add(4, new PackGoals.CircleGoal(this));
		this.goalSelector.add(5, new PackGoals.GoToBedGoal(this));
		this.goalSelector.add(6, new PackGoals.FollowAlphaGoal(this));
		this.goalSelector.add(8, new WanderAroundFarGoal(this, 0.8));
		this.goalSelector.add(10, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
		this.goalSelector.add(10, new LookAroundGoal(this));
		this.addTargetGoals();
	}

	protected void addTargetGoals() {
		this.targetSelector.add(1, new PackGoals.PackRevengeGoal(this));
		this.targetSelector.add(2, new PackGoals.AlphaTargetGoal(this));
		// A roaming pack goes for any player it sees. A den pack sleeps until attacked, but once up it
		// finds you again if it loses you mid-fight, until it gives up and heads back to bed.
		this.targetSelector.add(3, new PackGoals.PackTargetGoal(this, target -> this.isUpForAFight()));
	}

	/** Awake and not scattering, and for a den pack, not on its way back to bed (or already there). */
	public boolean isUpForAFight() {
		if (this.getAction().isResting() || this.scattering) {
			return false;
		}
		if (this.den == null) {
			return true;
		}
		AlphaWolfEntity alpha = this.getAlpha();
		return alpha != null && !alpha.isGoingHome() && !alpha.isSleeping();
	}

	// --- What it's doing ---

	public WolfAction getAction() {
		return WolfAction.byId(this.dataTracker.get(ACTION));
	}

	public void setAction(WolfAction action) {
		if (this.getAction() != action) {
			this.dataTracker.set(ACTION, (byte) action.ordinal());
			this.actionTicks = 0;
		}
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (ACTION.equals(data)) {
			this.actionStartAge = this.age;
		}
	}

	/** Server: ticks spent in the current action. */
	public int actionTicks() {
		return this.actionTicks;
	}

	/** Client: ticks (with partial tick) since the current action started, for animating it. */
	public float actionProgress(float tickDelta) {
		return this.age - this.actionStartAge + tickDelta;
	}

	/** Client: the action before this one (so getting up can blend out of lying down). */
	public WolfAction previousAction() {
		return this.previousAction;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient) {
			WolfAction now = this.getAction();
			if (now != this.lastSeenAction) {
				this.previousAction = this.lastSeenAction;
				this.lastSeenAction = now;
			}
		}
	}

	private WolfAction lastSeenAction = WolfAction.NONE;

	// --- The pack ---

	/** This wolf's Alpha (itself for the Alpha), or null if it's dead, gone or not loaded. */
	@Nullable
	public AlphaWolfEntity getAlpha() {
		if (this instanceof AlphaWolfEntity self) {
			return self;
		}
		if (this.alphaCache != null && this.alphaCache.isAlive() && !this.alphaCache.isRemoved()) {
			return this.alphaCache;
		}
		this.alphaCache = null;
		if (this.alphaUuid != null && this.getWorld() instanceof ServerWorld world
				&& world.getEntity(this.alphaUuid) instanceof AlphaWolfEntity alpha && alpha.isAlive()) {
			this.alphaCache = alpha;
		}
		return this.alphaCache;
	}

	public void setAlpha(AlphaWolfEntity alpha) {
		this.alphaUuid = alpha.getUuid();
		this.alphaCache = alpha;
	}

	public boolean isInPackOf(AlphaWolfEntity alpha) {
		return this == alpha || alpha.getUuid().equals(this.alphaUuid);
	}

	public boolean isScattering() {
		return this.scattering;
	}

	public boolean isLunging() {
		return this.lunging;
	}

	// --- The den: sleeping and waking ---

	@Nullable
	public BlockPos getDen() {
		return this.den;
	}

	@Nullable
	public BlockPos getBed() {
		return this.bed;
	}

	/** Makes this a den wolf: it lies down at {@code bed} and won't wander off for good (or despawn). */
	public void setDen(BlockPos den, @Nullable BlockPos bed) {
		this.den = den.toImmutable();
		this.bed = bed == null ? null : bed.toImmutable();
		this.setPersistent();
	}

	public boolean isSleeping() {
		return this.getAction() == WolfAction.SLEEPING;
	}

	/** Lies down where it stands and goes to sleep. */
	public void fallAsleep() {
		this.getNavigation().stop();
		super.setTarget(null);
		this.setAttacker(null);
		this.setInSittingPose(false);
		this.setBegging(false);
		this.lunging = false;
		this.calmTicks = 0;
		this.pendingWake = -1;
		this.setAction(WolfAction.SLEEPING);
	}

	/** Starts getting up in {@code delay} ticks; once up it goes for {@code target} (if any). */
	void wakeLater(int delay, @Nullable LivingEntity target) {
		if (!this.isSleeping()) {
			if (target != null && this.getTarget() == null && !this.getAction().isResting()) {
				this.setTarget(target);
			}
			return;
		}
		this.wakeTarget = target;
		if (this.pendingWake < 0 || delay < this.pendingWake) {
			this.pendingWake = delay;
		}
	}

	protected int wakeTicks() {
		return WAKE_TICKS;
	}

	/** Gets up now (the start of WAKING): a growl and a stretch. */
	protected void startWaking() {
		this.pendingWake = -1;
		this.setAction(WolfAction.WAKING);
		this.playSound(SoundEvents.ENTITY_WOLF_GROWL, 1.0f, this.getSoundPitch());
	}

	/** Up and ready: takes up the fight that woke it. */
	protected void finishWaking() {
		this.setAction(WolfAction.NONE);
		this.calmTicks = 0;
		LivingEntity target = this.wakeTarget;
		this.wakeTarget = null;
		if (target != null && PackGoals.isValidTarget(this, target)) {
			this.setTarget(target);
		}
	}

	/** The pack is broken: stop fighting, run from the player, and vanish once away (see {@link PackGoals.ScatterGoal}). */
	public void scatter() {
		if (this.scattering) {
			return;
		}
		this.scattering = true;
		this.scatterTicks = 0;
		this.setAction(WolfAction.NONE);
		super.setTarget(null);
		this.setAttacker(null);
		this.getNavigation().stop();
		this.playSound(SoundEvents.ENTITY_WOLF_WHINE, 1.0f, 0.9f + this.random.nextFloat() * 0.2f);
	}

	/** Gone in a puff of snow, without drops. */
	public void vanish() {
		if (this.getWorld() instanceof ServerWorld world) {
			world.spawnParticles(ParticleTypes.POOF, this.getX(), this.getBodyY(0.5), this.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
			world.spawnParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getBodyY(0.5), this.getZ(), 10, 0.4, 0.3, 0.4, 0.02);
		}
		this.discard();
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		// A scattering wolf won't be drawn back into the fight, even when hit, and a resting one isn't up yet.
		if (target != null && (this.scattering || this.getAction().isResting())) {
			return;
		}
		super.setTarget(target);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean hurt = super.damage(source, amount);
		if (!this.getWorld().isClient && this.isAlive() && source.getAttacker() instanceof LivingEntity attacker
				&& !(attacker instanceof PackWolfEntity)) {
			if (this.getAction().isResting()) {
				AlphaWolfEntity alpha = this.getAlpha();
				if (alpha != null) {
					alpha.wake(attacker);
				} else {
					this.wakeLater(0, attacker);
				}
			}
		}
		return hurt;
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		this.actionTicks++;
		if (this.lungeCooldown > 0) {
			this.lungeCooldown--;
		}
		WolfAction action = this.getAction();
		if (action == WolfAction.SLEEPING) {
			if (this.pendingWake >= 0 && this.pendingWake-- == 0) {
				this.startWaking();
			} else if (this.age % 20 == 0 && this.getHealth() < this.getMaxHealth()) {
				this.heal((float) FrostboundConfig.get().alphaWolf.den.sleepHealPerSecond);
			}
			return;
		}
		if (action == WolfAction.WAKING) {
			if (this.actionTicks >= this.wakeTicks()) {
				this.finishWaking();
			}
			return;
		}

		LivingEntity target = this.getTarget();
		if (target != null && !PackGoals.isValidTarget(this, target)) {
			super.setTarget(null);
			target = null;
		}
		this.calmTicks = target == null ? this.calmTicks + 1 : 0;
		if (!(this instanceof AlphaWolfEntity) && !this.scattering && this.age % 20 == 0) {
			if (this.getAlpha() == null) {
				this.alphaMissingTicks += 20;
				if (this.alphaMissingTicks >= ALPHA_GRACE_TICKS) {
					this.scatter();
				}
			} else {
				this.alphaMissingTicks = 0;
			}
		}
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (this.getWorld().isClient || this.scattering || this instanceof AlphaWolfEntity) {
			return;
		}
		AlphaWolfEntity alpha = this.getAlpha();
		if (alpha != null) {
			alpha.enrage(this);
		}
	}

	/** A pack wolf's coat: the local wolf, but never black (the Alpha's colour). */
	static RegistryEntry<WolfVariant> memberVariant(WorldAccess world, BlockPos pos) {
		RegistryEntry<WolfVariant> variant = WolfVariants.fromBiome(world.getRegistryManager(), world.getBiome(pos));
		return variant.matchesKey(WolfVariants.BLACK) ? variant(world, WolfVariants.ASHEN) : variant;
	}

	static RegistryEntry<WolfVariant> variant(WorldAccess world, RegistryKey<WolfVariant> key) {
		return world.getRegistryManager().get(RegistryKeys.WOLF_VARIANT).getEntry(key)
				.<RegistryEntry<WolfVariant>>map(entry -> entry)
				.orElseGet(() -> WolfVariants.fromBiome(world.getRegistryManager(), world.getBiome(BlockPos.ORIGIN)));
	}

	// --- Not a pet: no taming, breeding, sitting, collars, leads or armour ---

	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		return ActionResult.PASS;
	}

	@Override
	public boolean isBreedingItem(ItemStack stack) {
		return false;
	}

	@Override
	public boolean canBreedWith(AnimalEntity other) {
		return false;
	}

	@Nullable
	@Override
	public WolfEntity createChild(ServerWorld world, PassiveEntity entity) {
		return null;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	/** Vanilla wolves calm down after a while; a pack wolf's anger is the pack's, handled by its goals. */
	@Override
	public void tickAngerLogic(ServerWorld world, boolean angerPersistent) {
	}

	/** Hostile monsters, gone in Peaceful. */
	@Override
	protected boolean isDisallowedInPeaceful() {
		return true;
	}

	/** A sleeping wolf can't be shoved around the den. */
	@Override
	public boolean isPushable() {
		return !this.getAction().isResting() && super.isPushable();
	}

	/** The raised, bristling tail of an angry wolf, except asleep (the model lays it down). */
	@Override
	public float getTailAngle() {
		return 1.5393804f;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		if (this.isSleeping()) {
			return SoundEvents.ENTITY_FOX_SLEEP;
		}
		return this.scattering ? SoundEvents.ENTITY_WOLF_WHINE : SoundEvents.ENTITY_WOLF_GROWL;
	}

	@Override
	protected float getSoundVolume() {
		return this.isSleeping() ? 0.5f : super.getSoundVolume();
	}

	@Override
	protected int getXpToDrop() {
		return 3;
	}

	@Override
	public SoundCategory getSoundCategory() {
		return SoundCategory.HOSTILE;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (this.alphaUuid != null) {
			nbt.putUuid("FrostboundAlpha", this.alphaUuid);
		}
		nbt.putBoolean("FrostboundScattering", this.scattering);
		if (this.den != null) {
			nbt.put("FrostboundDen", NbtHelper.fromBlockPos(this.den));
		}
		if (this.bed != null) {
			nbt.put("FrostboundBed", NbtHelper.fromBlockPos(this.bed));
		}
		// Asleep stays asleep; anything mid-move comes back standing.
		nbt.putBoolean("FrostboundSleeping", this.isSleeping());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.alphaUuid = nbt.containsUuid("FrostboundAlpha") ? nbt.getUuid("FrostboundAlpha") : null;
		this.scattering = nbt.getBoolean("FrostboundScattering");
		this.den = NbtHelper.toBlockPos(nbt, "FrostboundDen").orElse(null);
		this.bed = NbtHelper.toBlockPos(nbt, "FrostboundBed").orElse(null);
		this.setAction(nbt.getBoolean("FrostboundSleeping") ? WolfAction.SLEEPING : WolfAction.NONE);
	}
}

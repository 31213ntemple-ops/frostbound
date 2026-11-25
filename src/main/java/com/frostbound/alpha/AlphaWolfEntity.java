package com.frostbound.alpha;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.WolfVariants;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Alpha Wolf: a big black wolf with burning eyes that leads a pack (see {@link PackWolfEntity}).
 * It lives in a den (see den.WolfDenStructure) where the pack sleeps until one of them is attacked.
 * Spawned from its egg or with /summon it brings a roaming pack instead.
 *
 * Every move reads as tell → action → recovery (see {@link WolfAction} and {@link AlphaGoals}):
 *
 *  - <b>Bite</b> ({@link PackGoals.LungeGoal}): up close, a crouch and a growl, then a lunge.
 *  - <b>Pounce</b>: crouches with its rear up while a red ring marks where it will land (the ring
 *    follows you, then stops); leaps and slams down there, then stands winded.
 *  - <b>Call of the Pack</b>: sits back and howls with the whole pack joining in; hit it hard enough
 *    to cut it off, or the pack heals and reinforcements arrive.
 *  - <b>Bloodied</b> (below half health): it roars, throwing back anyone close, its eyes flare, it
 *    gets faster, and it starts to <b>Charge</b>: paws the ground while a pale line shows its path,
 *    then runs it. If the line ends at a wall or pillar, it runs into it and is stunned (and takes
 *    extra damage), so the den's pillars are cover worth using.
 *  - <b>Pincer</b>: barks an order, and two wolves on opposite sides of you crouch while pale lines
 *    show where each will land, then leap one after the other (see {@link AlphaGoals.PincerGoal}).
 *  - <b>Draining Bite</b> (any pack wolf; see {@link DrainingBite}) and <b>Enrage</b> ({@link #enrage}).
 *  - While it winds up or carries out a big move the pack gives it room ({@link #holdsPackBack}).
 *
 * It runs the hunt ({@link PackTactics}): on new prey the pack first spreads out round it
 * ({@link #beginHunt}), its wolves keep an even ring that turns round the prey together
 * ({@link #ringPoint}), and each bite goes to the wolf furthest behind where the prey is looking
 * ({@link #nextBiter}).
 *  - When it dies, the pack breaks: the rest scatter and vanish ({@link #breakUp}).
 *
 * A boss bar (white, red once Bloodied) shows its health and how many wolves it leads, to players
 * within {@link #BAR_RANGE} blocks while it's awake. It fills up as the Alpha wakes.
 */
public class AlphaWolfEntity extends PackWolfEntity {
	/** How far the pack ranges from its Alpha, for counting it. */
	public static final double PACK_RANGE = 48.0;
	public static final double BAR_RANGE = 40.0;
	/** Distance at which a roaming Alpha nobody is near despawns (like any monster), taking its pack with it. */
	public static final double DESPAWN_RANGE = 128.0;
	/** Ticks the Alpha takes to get up: it lifts its head, stands and howls the pack awake. */
	public static final int WAKE_TICKS = 30;
	private static final Identifier BLOODIED_SPEED = Frostbound.id("alpha_bloodied_speed");
	private static final TrackedData<Boolean> BLOODIED = DataTracker.registerData(AlphaWolfEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	private final ServerBossBar bossBar = new ServerBossBar(Text.translatable("entity.frostbound.alpha_wolf"),
			BossBar.Color.WHITE, BossBar.Style.NOTCHED_10);
	private final Set<PackWolfEntity> attackers = new HashSet<>();
	@Nullable
	private List<PackWolfEntity> packCache;
	private long packCacheTime = Long.MIN_VALUE;

	/** Ticks of fighting before the next howl. */
	int callCooldown;
	/** Wolves the Alpha can still call in this fight. */
	int reinforcementsLeft;
	boolean calling;
	boolean callInterrupted;
	private float callDamage;
	/** Ticks of fighting before the next big move (Pounce or Charge). */
	int moveCooldown;
	/** Whether its next big move is a Charge (only once Bloodied) rather than a Pounce. */
	boolean nextMoveIsCharge;
	/** It has just become Bloodied and will roar as soon as it's free to. */
	boolean pendingRoar;
	/** Walking back to its den to sleep. */
	boolean goingHome;
	/** The boss bar's shown fill (0–1); it fills up as the Alpha wakes rather than appearing full. */
	private float barFill = 1.0f;

	/** The hunt: each circling wolf's spot round the prey, recomputed every {@link #RING_UPDATE_TICKS}. */
	private final Map<PackWolfEntity, Double> ringSlots = new HashMap<>();
	@Nullable
	private LivingEntity ringPrey;
	private long ringTime = Long.MIN_VALUE;
	/** Which way the ring turns this hunt (1 or -1). */
	private int ringDirection = 1;
	/** Whose turn to bite is next, worked out once a tick; and how long the same wolf has been offered it. */
	@Nullable
	private PackWolfEntity nextBiter;
	@Nullable
	private LivingEntity nextBiterPrey;
	private long nextBiterTime = Long.MIN_VALUE;
	private int offeredTicks;
	/** The Pincer under way, if any, and ticks of fighting before the next. */
	@Nullable
	Pincer pincer;
	int pincerCooldown;

	static final int RING_UPDATE_TICKS = 10;
	/** How far round (radians) the ring turns at each update: a slow prowl. */
	static final double RING_TURN = 0.18;

	public AlphaWolfEntity(EntityType<? extends AlphaWolfEntity> type, World world) {
		super(type, world);
		FrostboundConfig.CallOfThePack call = FrostboundConfig.get().alphaWolf.callOfThePack;
		this.callCooldown = ticks(call.firstCallSeconds);
		this.reinforcementsLeft = call.maxReinforcements;
		this.moveCooldown = 40;
		this.pincerCooldown = ticks(FrostboundConfig.get().alphaWolf.pincer.firstSeconds);
		// The scale attribute's default doesn't resize the hitbox by itself.
		this.calculateDimensions();
	}

	public static DefaultAttributeContainer.Builder createAlphaAttributes() {
		FrostboundConfig.AlphaWolfOptions c = FrostboundConfig.get().alphaWolf;
		return createPackWolfAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, c.health)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, c.damage)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.33)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
				.add(EntityAttributes.GENERIC_ARMOR, 4.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6)
				.add(EntityAttributes.GENERIC_SCALE, c.scale);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(BLOODIED, false);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new PackGoals.RestGoal(this));
		this.goalSelector.add(2, new AlphaGoals.RoarGoal(this));
		this.goalSelector.add(2, new PackGoals.CallOfThePackGoal(this));
		this.goalSelector.add(3, new AlphaGoals.ChargeGoal(this));
		this.goalSelector.add(3, new AlphaGoals.PounceGoal(this));
		this.goalSelector.add(3, new AlphaGoals.PincerGoal(this));
		this.goalSelector.add(4, new PackGoals.LungeGoal(this));
		this.goalSelector.add(5, new PackGoals.CircleGoal(this));
		this.goalSelector.add(6, new AlphaGoals.ReturnHomeGoal(this));
		this.goalSelector.add(8, new WanderAroundFarGoal(this, 0.7));
		this.goalSelector.add(10, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
		this.goalSelector.add(10, new LookAroundGoal(this));
		this.addTargetGoals();
	}

	static int ticks(double seconds) {
		return (int) Math.round(seconds * 20.0);
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data) {
		EntityData result = super.initialize(world, difficulty, reason, new PassiveEntity.PassiveData(false));
		this.setVariant(variant(world, WolfVariants.BLACK));
		// A den brings its own pack (populateDen); anything else brings a roaming one.
		if (reason != SpawnReason.STRUCTURE) {
			this.spawnPack(FrostboundConfig.get().alphaWolf.initialPackSize - 1);
		}
		return result;
	}

	/**
	 * Fills a den: the Alpha asleep at {@code alphaBed} and a pack wolf asleep at each of {@code beds}
	 * (up to the starting pack size), all remembering the den. Works during world generation
	 * ({@code world} may be a chunk region). Returns the Alpha.
	 */
	@Nullable
	public static AlphaWolfEntity populateDen(ServerWorldAccess world, BlockPos den, BlockPos alphaBed, List<BlockPos> beds, Random random) {
		AlphaWolfEntity alpha = AlphaWolves.ALPHA_WOLF.create(world.toServerWorld());
		if (alpha == null) {
			return null;
		}
		float facing = random.nextFloat() * 360.0f;
		alpha.refreshPositionAndAngles(alphaBed.getX() + 0.5, alphaBed.getY(), alphaBed.getZ() + 0.5, facing, 0.0f);
		alpha.setHeadYaw(facing);
		alpha.setBodyYaw(facing);
		alpha.initialize(world, world.getLocalDifficulty(alphaBed), SpawnReason.STRUCTURE, null);
		alpha.setDen(den, alphaBed);
		alpha.fallAsleep();
		world.spawnEntityAndPassengers(alpha);

		int wolves = Math.min(beds.size(), FrostboundConfig.get().alphaWolf.initialPackSize - 1);
		for (int i = 0; i < wolves; i++) {
			BlockPos bed = beds.get(i);
			PackWolfEntity wolf = AlphaWolves.PACK_WOLF.create(world.toServerWorld());
			if (wolf == null) {
				continue;
			}
			// Curled up facing the Alpha, give or take.
			float yaw = (float) (MathHelper.atan2(den.getZ() - bed.getZ(), den.getX() - bed.getX()) * MathHelper.DEGREES_PER_RADIAN) - 90.0f
					+ (random.nextFloat() - 0.5f) * 70.0f;
			wolf.refreshPositionAndAngles(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, yaw, 0.0f);
			wolf.setHeadYaw(yaw);
			wolf.setBodyYaw(yaw);
			wolf.setAlpha(alpha);
			wolf.setVariant(memberVariant(world, bed));
			wolf.setDen(den, bed);
			wolf.fallAsleep();
			world.spawnEntityAndPassengers(wolf);
		}
		return alpha;
	}

	// --- The pack ---

	/** Every living wolf in the pack, the Alpha included. */
	public List<PackWolfEntity> getPack() {
		long time = this.getWorld().getTime();
		if (this.packCache == null || this.packCacheTime != time) {
			this.packCache = this.getWorld().getEntitiesByClass(PackWolfEntity.class, this.getBoundingBox().expand(PACK_RANGE),
					wolf -> wolf.isAlive() && !wolf.isScattering() && wolf.isInPackOf(this));
			this.packCacheTime = time;
		}
		return this.packCache;
	}

	/** Wolves alive in the pack, the Alpha included. */
	public int packSize() {
		return (int) this.getPack().stream().filter(LivingEntity::isAlive).count();
	}

	/** Spawns {@code count} pack wolves close around the Alpha (a new pack). */
	public List<PackWolfEntity> spawnPack(int count) {
		List<PackWolfEntity> spawned = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			PackWolfEntity wolf = this.spawnMember(false);
			if (wolf != null) {
				spawned.add(wolf);
			}
		}
		return spawned;
	}

	/** One more pack wolf: next to the Alpha for a new pack, or out at the edge of the fight for reinforcements. */
	@Nullable
	PackWolfEntity spawnMember(boolean reinforcement) {
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return null;
		}
		PackWolfEntity wolf = AlphaWolves.PACK_WOLF.create(world);
		if (wolf == null) {
			return null;
		}
		Vec3d pos = null;
		for (int i = 0; i < 10 && pos == null; i++) {
			Vec3d found = reinforcement ? NoPenaltyTargeting.find(this, 12, 4) : NoPenaltyTargeting.find(this, 4, 2);
			if (found != null && (!reinforcement || found.squaredDistanceTo(this.getPos()) >= 36.0)) {
				pos = found;
			}
		}
		if (pos == null) {
			pos = this.getPos();
		}
		wolf.refreshPositionAndAngles(pos.x, pos.y, pos.z, this.random.nextFloat() * 360.0f, 0.0f);
		wolf.setAlpha(this);
		wolf.setVariant(memberVariant(world, wolf.getBlockPos()));
		if (this.getDen() != null) {
			wolf.setDen(this.getDen(), null);
		}
		if (this.getTarget() != null) {
			wolf.setTarget(this.getTarget());
		}
		world.spawnEntity(wolf);
		this.packCache = null;
		if (reinforcement) {
			world.spawnParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.5, pos.z, 20, 0.4, 0.4, 0.4, 0.05);
			world.spawnParticles(ParticleTypes.POOF, pos.x, pos.y + 0.3, pos.z, 8, 0.3, 0.2, 0.3, 0.02);
			world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.HOSTILE, 1.2f, 1.3f);
		}
		return wolf;
	}

	/**
	 * Whether {@code wolf} could take a turn to lunge now. The Alpha always can; a pack wolf only while a
	 * turn is free, the Alpha isn't in the middle of a big move (they give it room), and it's the wolf the
	 * Alpha sends next ({@link #nextBiter}: the one its prey can least see).
	 */
	public boolean canClaimLunge(PackWolfEntity wolf) {
		if (wolf == this) {
			return true;
		}
		this.attackers.removeIf(w -> !w.isAlive() || w.isRemoved() || !w.lunging);
		if (this.attackers.contains(wolf)) {
			return true;
		}
		if (this.holdsPackBack() || this.attackers.size() >= FrostboundConfig.get().alphaWolf.simultaneousAttackers) {
			return false;
		}
		LivingEntity prey = wolf.getTarget();
		return prey != null && this.nextBiter(prey) == wolf;
	}

	/**
	 * Whose turn to bite {@code prey} is next: of the pack wolves ready to (not on cooldown, not busy),
	 * the one furthest behind where the prey is looking ({@link PackTactics#behindness}), so the bite
	 * comes from where you aren't watching. Worked out once a tick. A wolf that's been offered the turn
	 * for 2 seconds without taking it (stuck, say) is passed over for a while.
	 */
	@Nullable
	public PackWolfEntity nextBiter(LivingEntity prey) {
		long time = this.getWorld().getTime();
		if (time == this.nextBiterTime && prey == this.nextBiterPrey) {
			return this.nextBiter;
		}
		this.nextBiterTime = time;
		this.nextBiterPrey = prey;
		PackWolfEntity best = null;
		double behind = -1.0;
		for (PackWolfEntity wolf : this.getPack()) {
			if (this.readyToBite(wolf, prey)) {
				double b = PackTactics.behindness(prey, wolf.getPos());
				if (b > behind) {
					behind = b;
					best = wolf;
				}
			}
		}
		if (best != null && best == this.nextBiter) {
			if (++this.offeredTicks > 40) {
				best.lungeCooldown = 30;
				best = null;
			}
		} else {
			this.offeredTicks = 0;
		}
		this.nextBiter = best;
		return best;
	}

	private boolean readyToBite(PackWolfEntity wolf, LivingEntity prey) {
		WolfAction action = wolf.getAction();
		return wolf != this && wolf.isAlive() && !wolf.isScattering() && !wolf.lunging && wolf.lungeCooldown <= 0 && wolf.getTarget() == prey
				&& !action.isResting() && action != WolfAction.CHORUS && this.pincerRole(wolf) < 0;
	}

	/**
	 * Where pack wolf {@code wolf} should be in the ring round {@code prey}: the pack's circling wolves
	 * spread evenly round it, keeping their order, about {@code hunting.ringRadius} out, and the whole
	 * ring turns slowly one way (see {@link PackTactics#ringSlots}).
	 */
	public Vec3d ringPoint(PackWolfEntity wolf, LivingEntity prey) {
		long time = this.getWorld().getTime();
		if (prey != this.ringPrey || time - this.ringTime >= RING_UPDATE_TICKS || !this.ringSlots.containsKey(wolf)) {
			this.ringPrey = prey;
			this.ringTime = time;
			this.ringSlots.clear();
			List<PackWolfEntity> circling = new ArrayList<>();
			for (PackWolfEntity member : this.getPack()) {
				if (member != this && (member == wolf || (member.circling && member.getTarget() == prey))) {
					circling.add(member);
				}
			}
			if (!circling.contains(wolf)) {
				circling.add(wolf);
			}
			double[] angles = new double[circling.size()];
			for (int i = 0; i < angles.length; i++) {
				angles[i] = PackTactics.angleAround(prey.getPos(), circling.get(i).getPos());
			}
			double[] slots = PackTactics.ringSlots(angles, this.ringDirection * RING_TURN);
			for (int i = 0; i < slots.length; i++) {
				this.ringSlots.put(circling.get(i), slots[i]);
			}
		}
		double angle = this.ringSlots.get(wolf);
		// A little give in the ring, so it doesn't look drawn with a compass.
		double radius = FrostboundConfig.get().alphaWolf.hunting.ringRadius + (Math.floorMod(wolf.getId(), 3) - 1) * 0.5;
		return prey.getPos().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
	}

	/**
	 * New prey: the pack spreads out round it before anyone bites (every wolf waits
	 * {@code hunting.encircleSeconds}, give or take), the ring picks a way to turn, and the Pincer
	 * waits its first {@code pincer.firstSeconds}.
	 */
	public void beginHunt() {
		FrostboundConfig.AlphaWolfOptions config = FrostboundConfig.get().alphaWolf;
		int encircle = ticks(config.hunting.encircleSeconds);
		for (PackWolfEntity wolf : this.getPack()) {
			wolf.lungeCooldown = Math.max(wolf.lungeCooldown, encircle + this.random.nextInt(20));
			wolf.gazeChecks = 0;
		}
		this.ringDirection = this.random.nextBoolean() ? 1 : -1;
		this.pincerCooldown = Math.max(this.pincerCooldown, ticks(config.pincer.firstSeconds));
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		LivingEntity before = this.getTarget();
		super.setTarget(target);
		if (target != null && before == null && this.getTarget() == target && !this.getWorld().isClient) {
			this.beginHunt();
		}
	}

	// --- Pincer ---

	/** A Pincer under way: the prey, the two wolves sent in, where each will land, and ticks since the order. */
	public static final class Pincer {
		public final LivingEntity prey;
		public final PackWolfEntity[] wolves;
		public final Vec3d[] landing;
		public final boolean[] done = new boolean[2];
		int ticks;

		Pincer(LivingEntity prey, PackWolfEntity[] wolves) {
			this.prey = prey;
			this.wolves = wolves;
			this.landing = new Vec3d[] {landingFor(prey, wolves[0]), landingFor(prey, wolves[1])};
		}

		/** Just short of the prey on the wolf's own side, so the two don't land on top of each other. */
		static Vec3d landingFor(LivingEntity prey, PackWolfEntity wolf) {
			Vec3d side = new Vec3d(wolf.getX() - prey.getX(), 0.0, wolf.getZ() - prey.getZ());
			return side.lengthSquared() < 1.0E-6 ? prey.getPos() : prey.getPos().add(side.normalize().multiply(0.6));
		}

		/** Ticks since the Alpha's order. */
		public int ticks() {
			return this.ticks;
		}

		/** When wolf {@code role} (0 or 1) leaps: the second goes {@code staggerTicks} after the first. */
		public static int leapAt(int role) {
			FrostboundConfig.Pincer config = FrostboundConfig.get().alphaWolf.pincer;
			return config.windupTicks + role * config.staggerTicks;
		}
	}

	/** The Pincer under way, or null. */
	@Nullable
	public Pincer pincer() {
		return this.pincer;
	}

	/** Which of the Pincer's two wolves {@code wolf} is (0 leaps first, 1 second), or -1. */
	public int pincerRole(PackWolfEntity wolf) {
		if (this.pincer != null) {
			for (int i = 0; i < 2; i++) {
				if (this.pincer.wolves[i] == wolf) {
					return i;
				}
			}
		}
		return -1;
	}

	/**
	 * The two wolves to send at {@code prey}: of those free (not mid-bite, on the ground, 2.5–10 blocks
	 * away, about level, in sight), the pair most nearly opposite each other round it, at least
	 * {@code pincer.minAngleDegrees} apart. Null if there's no such pair.
	 */
	@Nullable
	PackWolfEntity[] pincerPair(LivingEntity prey) {
		List<PackWolfEntity> free = new ArrayList<>();
		for (PackWolfEntity wolf : this.getPack()) {
			WolfAction action = wolf.getAction();
			double distance = Math.sqrt(PackTactics.horizontalSquaredDistance(wolf.getPos(), prey.getPos()));
			if (wolf != this && wolf.isAlive() && !wolf.lunging && !wolf.isScattering() && wolf.isOnGround() && wolf.getTarget() == prey
					&& !action.isResting() && action != WolfAction.CHORUS && distance >= 2.5 && distance <= 10.0
					&& Math.abs(wolf.getY() - prey.getY()) < 2.5 && wolf.canSee(prey)) {
				free.add(wolf);
			}
		}
		return PackTactics.pincerPair(free, prey.getPos(), FrostboundConfig.get().alphaWolf.pincer.minAngleDegrees);
	}

	void beginPincer(LivingEntity prey, PackWolfEntity[] wolves) {
		this.pincer = new Pincer(prey, wolves);
	}

	void endPincer() {
		this.pincer = null;
		this.pincerCooldown = ticks(FrostboundConfig.get().alphaWolf.pincer.intervalSeconds) + this.random.nextInt(20);
	}

	/** Ticks of fighting left before the next Pincer. */
	public int pincerCooldown() {
		return this.pincerCooldown;
	}

	public void setPincerCooldown(int ticks) {
		this.pincerCooldown = ticks;
	}

	/** Takes a lunge turn for {@code wolf}; false if the pack's turns are all taken. Release it with {@link #releaseLunge}. */
	public boolean tryClaimLunge(PackWolfEntity wolf) {
		if (!this.canClaimLunge(wolf)) {
			return false;
		}
		if (wolf != this) {
			this.attackers.add(wolf);
		}
		return true;
	}

	public void releaseLunge(PackWolfEntity wolf) {
		this.attackers.remove(wolf);
	}

	/** Pack wolves (not the Alpha) holding a lunge turn right now. */
	public int lungingCount() {
		this.attackers.removeIf(w -> !w.isAlive() || w.isRemoved() || !w.lunging);
		return this.attackers.size();
	}

	/**
	 * Winding up or carrying out something big (a howl, the Pounce's crouch and leap, the Charge's windup
	 * and run, the roar, the Pincer's order, waking): no pack wolf starts a bite, and any still running in
	 * peels off, so the move can be read. Not while it's winded or stunned afterwards, or while a Pincer's
	 * two wolves are in the air: the pack keeps up the pressure through those.
	 */
	public boolean holdsPackBack() {
		return switch (this.getAction()) {
			case POUNCE_WINDUP, CHARGE_WINDUP, CHARGING, ROARING, HOWLING, WAKING, COMMANDING -> true;
			case LEAPING -> !this.lunging; // the Pounce's leap, not a bite
			default -> this.calling;
		};
	}

	/**
	 * In the middle of something big (a howl, Pounce, Charge, Pincer, the roar, a stun, waking), recovery
	 * included: it won't start another. (What holds the pack back is {@link #holdsPackBack}.)
	 */
	public boolean isPerformingMove() {
		return switch (this.getAction()) {
			case POUNCE_WINDUP, CHARGE_WINDUP, CHARGING, STUNNED, ROARING, HOWLING, WAKING, RECOVERING, COMMANDING -> true;
			case LEAPING -> !this.lunging; // the Pounce's leap, not a bite
			default -> this.calling || this.pincer != null;
		};
	}

	/**
	 * Enrage: a pack wolf died, so every other living wolf (reinforcements too) gets a short burst of
	 * Strength and may lunge again straight away. Strength is refreshed, never stacked, so deaths close
	 * together don't pile it up.
	 */
	public void enrage(PackWolfEntity fallen) {
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		this.packCache = null;
		FrostboundConfig.Enrage config = FrostboundConfig.get().alphaWolf.enrage;
		int duration = ticks(config.seconds);
		if (duration <= 0 || config.strengthLevel <= 0) {
			return;
		}
		for (PackWolfEntity wolf : this.getPack()) {
			if (wolf == fallen || !wolf.isAlive()) {
				continue;
			}
			wolf.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, duration, config.strengthLevel - 1, false, true, true));
			wolf.lungeCooldown = 0;
			world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, wolf.getX(), wolf.getBodyY(1.0) + 0.3, wolf.getZ(), 2, 0.25, 0.1, 0.25, 0.0);
		}
		world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WOLF_GROWL, SoundCategory.HOSTILE, 2.0f, 0.6f);
	}

	// --- The den: waking and going back to sleep ---

	/**
	 * Something attacked the pack: the Alpha gets up (lifting its head, standing, howling), and the
	 * rest wake in a ripple out from it. Everyone goes for {@code attacker} once up.
	 */
	public void wake(@Nullable LivingEntity attacker) {
		if (!this.getAction().isResting()) {
			if (attacker != null && this.getTarget() == null) {
				this.setTarget(attacker);
			}
			return;
		}
		this.wakeLater(0, attacker);
		int delay = 6;
		for (PackWolfEntity wolf : this.getPack()) {
			if (wolf != this) {
				wolf.wakeLater(delay, attacker);
				delay += 2 + this.random.nextInt(3);
			}
		}
	}

	@Override
	protected int wakeTicks() {
		return WAKE_TICKS;
	}

	@Override
	protected void startWaking() {
		super.startWaking();
		this.goingHome = false;
		this.barFill = 0.0f;
		if (this.getWorld() instanceof ServerWorld world) {
			world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WOLF_GROWL, SoundCategory.HOSTILE, 2.5f, 0.45f);
			world.spawnParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getBodyY(0.4), this.getZ(), 30, 0.9, 0.3, 0.9, 0.04);
		}
	}

	/** The waking animation's beat: halfway up (see {@link #WAKE_TICKS}), it throws its head back and howls the pack awake. */
	private void mobTickWaking() {
		// Halfway up, it throws its head back and howls the pack awake.
		if (this.actionTicks == WAKE_TICKS / 2 && this.getWorld() instanceof ServerWorld world) {
			world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.HOSTILE, 3.0f, 0.55f);
		}
	}

	@Override
	protected void finishWaking() {
		super.finishWaking();
		// Slept it off: a fresh fight starts unbloodied.
		if (this.isBloodied() && this.getHealth() > this.getMaxHealth() * FrostboundConfig.get().alphaWolf.bloodiedBelow) {
			this.setBloodied(false);
		}
		this.moveCooldown = 30;
		this.callCooldown = ticks(FrostboundConfig.get().alphaWolf.callOfThePack.firstCallSeconds);
	}

	public boolean isGoingHome() {
		return this.goingHome;
	}

	/** Back in the den after a fight: lies down, and the pack gets its reinforcement budget back for next time. */
	public void goToSleep() {
		this.goingHome = false;
		this.fallAsleep();
		FrostboundConfig.AlphaWolfOptions config = FrostboundConfig.get().alphaWolf;
		this.reinforcementsLeft = config.callOfThePack.maxReinforcements;
		this.callCooldown = ticks(config.callOfThePack.firstCallSeconds);
		this.calling = false;
		this.pendingRoar = false;
		this.pincer = null;
		this.bossBar.clearPlayers();
	}

	@Override
	public void fallAsleep() {
		super.fallAsleep();
		this.calling = false;
	}

	// --- Bloodied ---

	public boolean isBloodied() {
		return this.dataTracker.get(BLOODIED);
	}

	void setBloodied(boolean bloodied) {
		this.dataTracker.set(BLOODIED, bloodied);
		EntityAttributeInstance speed = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(BLOODIED_SPEED);
			if (bloodied) {
				speed.addTemporaryModifier(new EntityAttributeModifier(BLOODIED_SPEED,
						FrostboundConfig.get().alphaWolf.bloodiedSpeedBonus, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
		this.bossBar.setColor(bloodied ? BossBar.Color.RED : BossBar.Color.WHITE);
	}

	/** Ticks until the next big move, and picks it: once Bloodied, a Charge 60% of the time. */
	int moveInterval() {
		FrostboundConfig.AlphaWolfOptions c = FrostboundConfig.get().alphaWolf;
		this.nextMoveIsCharge = this.isBloodied() && this.random.nextFloat() < 0.6f;
		return ticks(this.isBloodied() ? c.bloodiedMoveIntervalSeconds : c.moveIntervalSeconds) + this.random.nextInt(20);
	}

	// --- Call of the Pack ---

	void beginCall() {
		this.calling = true;
		this.callInterrupted = false;
		this.callDamage = 0.0f;
		this.getNavigation().stop();
		this.setInSittingPose(true);
		this.setAction(WolfAction.HOWLING);
		// Loud enough to carry across the fight (16 blocks per unit of volume).
		this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.HOSTILE, 3.0f, 0.7f);
	}

	void endCall(boolean answered) {
		this.calling = false;
		this.setInSittingPose(false);
		if (this.getAction() == WolfAction.HOWLING) {
			this.setAction(WolfAction.NONE);
		}
		FrostboundConfig.CallOfThePack config = FrostboundConfig.get().alphaWolf.callOfThePack;
		this.callCooldown = ticks(answered ? config.intervalSeconds : config.interruptedRetrySeconds);
		if (!answered && this.callInterrupted && this.getWorld() instanceof ServerWorld world) {
			world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WOLF_HURT, SoundCategory.HOSTILE, 2.0f, 1.5f);
			world.spawnParticles(ParticleTypes.CRIT, this.getX(), this.getBodyY(0.8), this.getZ(), 20, 0.5, 0.4, 0.5, 0.3);
			for (ServerPlayerEntity player : this.bossBar.getPlayers()) {
				player.sendMessage(Text.translatable("entity.frostbound.alpha_wolf.interrupted").formatted(Formatting.GRAY), true);
			}
		}
	}

	public boolean isCalling() {
		return this.calling;
	}

	/**
	 * The pack answers the howl: every living wolf heals {@code healPerWolf} for each wolf alive (counted
	 * before the reinforcements arrive), then up to {@code wolvesPerCall} wolves join, never past
	 * {@code maxPackSize} alive or the fight's {@code maxReinforcements}. Returns how many joined.
	 */
	public int answerCall() {
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return 0;
		}
		FrostboundConfig.AlphaWolfOptions config = FrostboundConfig.get().alphaWolf;
		this.packCache = null;
		List<PackWolfEntity> pack = List.copyOf(this.getPack());
		float heal = (float) (config.callOfThePack.healPerWolf * pack.size());
		for (PackWolfEntity wolf : pack) {
			wolf.heal(heal);
			world.spawnParticles(ParticleTypes.HEART, wolf.getX(), wolf.getBodyY(1.0) + 0.2, wolf.getZ(), 2, 0.3, 0.1, 0.3, 0.0);
		}
		int count = Math.max(0, Math.min(config.callOfThePack.wolvesPerCall,
				Math.min(config.maxPackSize - pack.size(), this.reinforcementsLeft)));
		int joined = 0;
		for (int i = 0; i < count; i++) {
			if (this.spawnMember(true) != null) {
				joined++;
			}
		}
		this.reinforcementsLeft -= joined;
		return joined;
	}

	public int reinforcementsLeft() {
		return this.reinforcementsLeft;
	}

	public void setReinforcementsLeft(int wolves) {
		this.reinforcementsLeft = wolves;
	}

	/** Ticks of fighting left before the next howl. */
	public int callCooldown() {
		return this.callCooldown;
	}

	public void setCallCooldown(int ticks) {
		this.callCooldown = ticks;
	}

	/** Ticks of fighting left before the next Pounce or Charge. */
	public int moveCooldown() {
		return this.moveCooldown;
	}

	public void setMoveCooldown(int ticks) {
		this.moveCooldown = ticks;
	}

	/** Makes its next big move come now: a Charge if {@code charge} (once Bloodied), else a Pounce. */
	public void forceNextMove(boolean charge) {
		this.nextMoveIsCharge = charge;
		this.moveCooldown = 0;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (this.getAction() == WolfAction.STUNNED && !this.getWorld().isClient) {
			amount *= (float) FrostboundConfig.get().alphaWolf.charge.stunnedDamageMultiplier;
		}
		boolean hurt = super.damage(source, amount);
		if (hurt && !this.getWorld().isClient) {
			if (this.calling) {
				this.callDamage += amount;
				if (this.callDamage >= FrostboundConfig.get().alphaWolf.callOfThePack.interruptDamage) {
					this.callInterrupted = true;
				}
			}
			if (this.isAlive() && !this.isBloodied()
					&& this.getHealth() <= this.getMaxHealth() * FrostboundConfig.get().alphaWolf.bloodiedBelow) {
				this.setBloodied(true);
				this.pendingRoar = true;
			}
		}
		return hurt;
	}

	// --- Ticking, boss bar, death and despawning ---

	@Override
	public void tickMovement() {
		super.tickMovement();
		// Bloodied: embers drift up from its burning eyes.
		if (this.getWorld().isClient && this.isBloodied() && !this.getAction().isResting() && this.random.nextFloat() < 0.45f) {
			float yaw = this.headYaw * MathHelper.RADIANS_PER_DEGREE;
			double forward = 0.42 * this.getScale();
			double side = (this.random.nextBoolean() ? 1 : -1) * 0.12 * this.getScale();
			double x = this.getX() - MathHelper.sin(yaw) * forward + MathHelper.cos(yaw) * side;
			double z = this.getZ() + MathHelper.cos(yaw) * forward + MathHelper.sin(yaw) * side;
			this.getWorld().addParticle(ParticleTypes.SMALL_FLAME, x, this.getEyeY() + 0.05, z, 0.0, 0.015, 0.0);
		}
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		WolfAction action = this.getAction();
		if (action == WolfAction.WAKING) {
			this.mobTickWaking();
		}
		LivingEntity target = this.getTarget();
		if (!action.isResting()) {
			if (target == null) {
				// Take up any fight a pack wolf has started.
				for (PackWolfEntity wolf : this.getPack()) {
					LivingEntity theirs = wolf.getTarget();
					if (wolf != this && theirs != null && PackGoals.isValidTarget(this, theirs)) {
						this.setTarget(theirs);
						break;
					}
				}
			} else {
				if (!this.calling && this.callCooldown > 0) {
					this.callCooldown--;
				}
				if (this.moveCooldown > 0 && !this.isPerformingMove()) {
					this.moveCooldown--;
				}
				if (this.pincerCooldown > 0 && !this.isPerformingMove()) {
					this.pincerCooldown--;
				}
			}
		}
		this.tickBossBar(action);
	}

	private void tickBossBar(WolfAction action) {
		float health = this.getHealth() / this.getMaxHealth();
		// Fill up as it wakes, then follow its health.
		this.barFill = this.barFill < health ? Math.min(health, this.barFill + 0.05f) : health;
		this.bossBar.setPercent(this.barFill);
		if (this.age % 10 == 0) {
			this.bossBar.setName(Text.translatable("entity.frostbound.alpha_wolf.bar", this.getDisplayName(), this.packSize()));
			if (action == WolfAction.SLEEPING) {
				this.bossBar.clearPlayers();
			} else {
				this.updateBarPlayers();
			}
		}
	}

	private void updateBarPlayers() {
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		double range = BAR_RANGE * BAR_RANGE;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.isAlive() && !player.isSpectator() && player.squaredDistanceTo(this) <= range) {
				this.bossBar.addPlayer(player);
			} else {
				this.bossBar.removePlayer(player);
			}
		}
		for (ServerPlayerEntity player : List.copyOf(this.bossBar.getPlayers())) {
			if (player.getWorld() != world) {
				this.bossBar.removePlayer(player);
			}
		}
	}

	/** Players shown the boss bar (for tests). */
	public boolean isBarShownTo(ServerPlayerEntity player) {
		return this.bossBar.getPlayers().contains(player);
	}

	public BossBar.Color barColor() {
		return this.bossBar.getColor();
	}

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		this.bossBar.removePlayer(player);
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (!this.getWorld().isClient) {
			this.bossBar.clearPlayers();
			this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WOLF_WHINE, SoundCategory.HOSTILE, 3.0f, 0.6f);
			this.breakUp(false);
		}
	}

	/** The pack breaks: every wolf scatters (or, if {@code now}, vanishes on the spot). */
	public void breakUp(boolean now) {
		this.packCache = null;
		for (PackWolfEntity wolf : List.copyOf(this.getPack())) {
			if (wolf == this) {
				continue;
			}
			if (now) {
				wolf.vanish();
			} else {
				wolf.scatter();
			}
		}
		this.attackers.clear();
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		this.bossBar.clearPlayers();
	}

	/**
	 * A den Alpha never despawns. A roaming one despawns like a monster when no player is within
	 * {@link #DESPAWN_RANGE} blocks (never at random just for being out of sight, so a pack waits for
	 * you), and its pack goes with it. Named Alphas stay.
	 */
	@Override
	public void checkDespawn() {
		if (this.getWorld().getDifficulty() == Difficulty.PEACEFUL && this.isDisallowedInPeaceful()) {
			this.breakUp(true);
			this.discard();
			return;
		}
		if (this.getDen() != null || this.isPersistent() || this.cannotDespawn()) {
			return;
		}
		// Like any mob: with no (non-spectator) player around at all, it stays put.
		PlayerEntity player = this.getWorld().getClosestPlayer(this, -1.0);
		if (player != null && player.squaredDistanceTo(this) > DESPAWN_RANGE * DESPAWN_RANGE) {
			this.breakUp(true);
			this.discard();
		}
	}

	/** Lower, rougher voice than a pack wolf. */
	@Override
	public float getSoundPitch() {
		return 0.65f + (this.random.nextFloat() - this.random.nextFloat()) * 0.1f;
	}

	@Override
	protected float getSoundVolume() {
		return this.isSleeping() ? 0.8f : 1.4f;
	}

	@Override
	protected int getXpToDrop() {
		return 25;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("FrostboundCallCooldown", this.callCooldown);
		nbt.putInt("FrostboundReinforcements", this.reinforcementsLeft);
		nbt.putBoolean("FrostboundBloodied", this.isBloodied());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("FrostboundCallCooldown")) {
			this.callCooldown = nbt.getInt("FrostboundCallCooldown");
		}
		if (nbt.contains("FrostboundReinforcements")) {
			this.reinforcementsLeft = MathHelper.clamp(nbt.getInt("FrostboundReinforcements"), 0,
					FrostboundConfig.get().alphaWolf.callOfThePack.maxReinforcements);
		}
		if (nbt.getBoolean("FrostboundBloodied")) {
			this.setBloodied(true);
		}
		if (this.isSleeping()) {
			this.barFill = 0.0f;
		}
	}
}

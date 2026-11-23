package com.frostbound.alpha;

import com.frostbound.config.FrostboundConfig;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Alpha Wolf: a big black wolf with burning eyes that leads a pack (see {@link PackWolfEntity}).
 * Spawning one (naturally, from its egg, or with /summon) brings its pack with it.
 *
 *  - <b>Call of the Pack</b>: every so often in a fight it sits back and howls. If it isn't cut off
 *    (enough damage during the howl interrupts it), every living wolf heals a little for each wolf
 *    alive and reinforcements arrive, never past the pack's size limit or the fight's reinforcement budget.
 *  - <b>Draining Bite</b> (any pack wolf; see {@link DrainingBite}) and <b>Enrage</b> ({@link #enrage}).
 *  - It coordinates the pack: it hands out the few lunge "turns" ({@link #tryClaimLunge}).
 *  - When it dies, the pack breaks: the rest scatter and vanish ({@link #breakUp}).
 *
 * A boss bar shows its health and how many wolves it leads, to players within {@link #BAR_RANGE} blocks.
 */
public class AlphaWolfEntity extends PackWolfEntity {
	/** How far the pack ranges from its Alpha, for counting it. */
	public static final double PACK_RANGE = 48.0;
	public static final double BAR_RANGE = 40.0;
	/** Distance at which an Alpha nobody is near despawns (like any monster), taking its pack with it. */
	public static final double DESPAWN_RANGE = 128.0;

	private final ServerBossBar bossBar = new ServerBossBar(Text.translatable("entity.frostbound.alpha_wolf"),
			BossBar.Color.RED, BossBar.Style.NOTCHED_10);
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

	public AlphaWolfEntity(EntityType<? extends AlphaWolfEntity> type, World world) {
		super(type, world);
		FrostboundConfig.CallOfThePack call = FrostboundConfig.get().alphaWolf.callOfThePack;
		this.callCooldown = ticks(call.firstCallSeconds);
		this.reinforcementsLeft = call.maxReinforcements;
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
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(2, new PackGoals.CallOfThePackGoal(this));
		this.goalSelector.add(3, new PackGoals.LungeGoal(this));
		this.goalSelector.add(4, new PackGoals.CircleGoal(this));
		this.goalSelector.add(8, new WanderAroundFarGoal(this, 0.7));
		this.goalSelector.add(10, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
		this.goalSelector.add(10, new LookAroundGoal(this));
		this.targetSelector.add(1, new RevengeGoal(this));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	static int ticks(double seconds) {
		return (int) Math.round(seconds * 20.0);
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data) {
		EntityData result = super.initialize(world, difficulty, reason, new PassiveEntity.PassiveData(false));
		this.setVariant(variant(world, WolfVariants.BLACK));
		this.spawnPack(FrostboundConfig.get().alphaWolf.initialPackSize - 1);
		return result;
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

	/** Whether {@code wolf} could take a turn to lunge now (the Alpha always can). */
	public boolean canClaimLunge(PackWolfEntity wolf) {
		if (wolf == this) {
			return true;
		}
		this.attackers.removeIf(w -> !w.isAlive() || w.isRemoved() || !w.lunging);
		return this.attackers.contains(wolf) || this.attackers.size() < FrostboundConfig.get().alphaWolf.simultaneousAttackers;
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

	// --- Call of the Pack ---

	void beginCall() {
		this.calling = true;
		this.callInterrupted = false;
		this.callDamage = 0.0f;
		this.getNavigation().stop();
		this.setInSittingPose(true);
		// Loud enough to carry across the fight (16 blocks per unit of volume).
		this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.HOSTILE, 3.0f, 0.7f);
	}

	void endCall(boolean answered) {
		this.calling = false;
		this.setInSittingPose(false);
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

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean hurt = super.damage(source, amount);
		if (hurt && this.calling && !this.getWorld().isClient) {
			this.callDamage += amount;
			if (this.callDamage >= FrostboundConfig.get().alphaWolf.callOfThePack.interruptDamage) {
				this.callInterrupted = true;
			}
		}
		return hurt;
	}

	// --- Ticking, boss bar, death and despawning ---

	@Override
	protected void mobTick() {
		super.mobTick();
		LivingEntity target = this.getTarget();
		if (target == null) {
			// Take up any fight a pack wolf has started.
			for (PackWolfEntity wolf : this.getPack()) {
				LivingEntity theirs = wolf.getTarget();
				if (wolf != this && theirs != null && PackGoals.isValidTarget(this, theirs)) {
					this.setTarget(theirs);
					break;
				}
			}
		} else if (!this.calling && this.callCooldown > 0) {
			this.callCooldown--;
		}

		this.bossBar.setPercent(this.getHealth() / this.getMaxHealth());
		if (this.age % 10 == 0) {
			this.bossBar.setName(Text.translatable("entity.frostbound.alpha_wolf.bar", this.getDisplayName(), this.packSize()));
			this.updateBarPlayers();
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
	 * Despawns like a monster when no player is within {@link #DESPAWN_RANGE} blocks (never at random
	 * just for being out of sight, so a pack waits for you), and its pack goes with it. Named Alphas stay.
	 */
	@Override
	public void checkDespawn() {
		if (this.getWorld().getDifficulty() == Difficulty.PEACEFUL && this.isDisallowedInPeaceful()) {
			this.breakUp(true);
			this.discard();
			return;
		}
		if (this.isPersistent() || this.cannotDespawn()) {
			return;
		}
		PlayerEntity player = this.getWorld().getClosestPlayer(this, -1.0);
		if (player == null || player.squaredDistanceTo(this) > DESPAWN_RANGE * DESPAWN_RANGE) {
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
		return 1.4f;
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
	}
}

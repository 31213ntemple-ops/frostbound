package com.frostbound.alpha;

import com.frostbound.config.FrostboundConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.passive.WolfVariant;
import net.minecraft.entity.passive.WolfVariants;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
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
 *    the target and wait their turn, then fall back after their bite (hit and run).
 *  - When it dies, the rest of the pack is enraged ({@link AlphaWolfEntity#enrage}).
 *  - Without its Alpha (killed, or gone for {@link #ALPHA_GRACE_TICKS}) it {@link #scatter scatters}:
 *    runs from the nearest player and vanishes once it has got away.
 *
 * Extends the vanilla wolf for its model, animations and sounds; everything tame (bones, sitting,
 * breeding, collars, leads, wolf armour) is switched off.
 */
public class PackWolfEntity extends WolfEntity {
	/** How long a wolf waits for an Alpha it can't find (e.g. not loaded yet) before it scatters. */
	public static final int ALPHA_GRACE_TICKS = 200;
	/** A scattering wolf vanishes once no player is this close (after a short head start), or after {@link #MAX_SCATTER_TICKS}. */
	public static final double ESCAPED_DISTANCE = 24.0;
	public static final int MAX_SCATTER_TICKS = 200;

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
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new PackGoals.ScatterGoal(this));
		this.goalSelector.add(3, new PackGoals.LungeGoal(this));
		this.goalSelector.add(4, new PackGoals.CircleGoal(this));
		this.goalSelector.add(6, new PackGoals.FollowAlphaGoal(this));
		this.goalSelector.add(8, new WanderAroundFarGoal(this, 0.8));
		this.goalSelector.add(10, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
		this.goalSelector.add(10, new LookAroundGoal(this));
		this.targetSelector.add(1, new RevengeGoal(this));
		this.targetSelector.add(2, new PackGoals.AlphaTargetGoal(this));
		this.targetSelector.add(3, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

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

	/** The pack is broken: stop fighting, run from the player, and vanish once away (see {@link PackGoals.ScatterGoal}). */
	public void scatter() {
		if (this.scattering) {
			return;
		}
		this.scattering = true;
		this.scatterTicks = 0;
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
		// A scattering wolf won't be drawn back into the fight, even when hit.
		if (this.scattering && target != null) {
			return;
		}
		super.setTarget(target);
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		if (this.lungeCooldown > 0) {
			this.lungeCooldown--;
		}
		LivingEntity target = this.getTarget();
		if (target != null && !PackGoals.isValidTarget(this, target)) {
			super.setTarget(null);
		}
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

	/** Always the raised, bristling tail of an angry wolf. */
	@Override
	public float getTailAngle() {
		return 1.5393804f;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.scattering ? SoundEvents.ENTITY_WOLF_WHINE : SoundEvents.ENTITY_WOLF_GROWL;
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
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.alphaUuid = nbt.containsUuid("FrostboundAlpha") ? nbt.getUuid("FrostboundAlpha") : null;
		this.scattering = nbt.getBoolean("FrostboundScattering");
	}
}

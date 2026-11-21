package com.frostbound.mixin.mobs;

import com.frostbound.environment.FrostboundClimate;
import com.frostbound.mobs.FrostStats;
import com.frostbound.mobs.FrozenMobs;
import com.frostbound.mobs.FrozenMob;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.world.GameRules;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives every mob a synced "frozen" flag, saves it to NBT, and rolls it on spawn.
 *
 * Tracked data (DataTracker) is what makes the flag reach the client, which the
 * renderer needs for the shiver + tint. Registering it inside MobEntity's static
 * init keeps the IDs consistent for every mob subclass on both sides.
 */
@Mixin(MobEntity.class)
public abstract class MobEntityMixin extends LivingEntity implements FrozenMob {
	@Unique
	private static final TrackedData<Boolean> FROSTBOUND_FROZEN =
			DataTracker.registerData(MobEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	@Unique
	private static final String NBT_KEY = "Frostbound";

	protected MobEntityMixin(EntityType<? extends LivingEntity> type, World world) {
		super(type, world);
	}

	@Inject(method = "initDataTracker", at = @At("TAIL"))
	private void frostbound$initTracker(DataTracker.Builder builder, CallbackInfo ci) {
		builder.add(FROSTBOUND_FROZEN, false);
	}

	@Override
	public boolean frostbound$isFrozen() {
		return this.dataTracker.get(FROSTBOUND_FROZEN);
	}

	@Override
	public void frostbound$setFrozen(boolean frozen) {
		this.dataTracker.set(FROSTBOUND_FROZEN, frozen);
		if (!this.getWorld().isClient) {
			MobEntity self = (MobEntity) (Object) this;
			if (frozen) {
				FrostStats.apply(self);
			} else {
				FrostStats.remove(self);
			}
		}
	}

	// ---- Saving / loading (also what makes /summon ... {Frostbound:1b} work) ----

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void frostbound$save(NbtCompound nbt, CallbackInfo ci) {
		if (this.frostbound$isFrozen()) {
			nbt.putBoolean(NBT_KEY, true);
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void frostbound$load(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.contains(NBT_KEY)) {
			this.frostbound$setFrozen(nbt.getBoolean(NBT_KEY));
		}
	}

	// ---- Spawn roll. initialize() runs for natural spawns, spawners, spawn eggs and plain /summon ----

	@Inject(method = "initialize", at = @At("TAIL"))
	private void frostbound$rollFrozen(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
			@Nullable EntityData entityData, CallbackInfoReturnable<EntityData> cir) {
		if (!FrozenMobs.isEnabled() || this.frostbound$isFrozen() || !this.getType().isIn(FrozenMobs.CAN_BE_FROZEN)) {
			return;
		}
		GameRules rules = world.toServerWorld().getGameRules();
		// "Cold" = arctic band (≤ -20 °C). Every biome is below freezing in the winter
		// environment, so plain vanilla "snowy" would match everywhere.
		boolean cold = FrostboundClimate.isArctic(world.getBiome(this.getBlockPos()));
		int chance = rules.getInt(cold ? FrozenMobs.COLD_BIOME_CHANCE : FrozenMobs.SPAWN_CHANCE);
		if (this.random.nextInt(100) < chance) {
			this.frostbound$setFrozen(true);
		}
	}

	// ---- Client-side snowflakes drifting off frozen mobs ----

	@Inject(method = "tick", at = @At("TAIL"))
	private void frostbound$frostParticles(CallbackInfo ci) {
		if (this.getWorld().isClient && this.frostbound$isFrozen() && !this.isInvisible()
				&& this.random.nextInt(3) == 0) {
			this.getWorld().addParticle(ParticleTypes.SNOWFLAKE,
					this.getParticleX(0.6), this.getRandomBodyY(), this.getParticleZ(0.6),
					0.0, -0.03, 0.0);
		}
	}
}

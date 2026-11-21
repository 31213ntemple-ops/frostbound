package com.frostbound.mixin.mobs;

import com.frostbound.mobs.FrostStats;
import com.frostbound.mobs.FrozenMobs;
import com.frostbound.mobs.FrozenMob;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Combat and death behaviour. This targets LivingEntity (not MobEntity) because
 * damage, loot and death are handled there — and because the *victim* of a
 * frozen mob's attack is often a player, which isn't a MobEntity.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
	@Shadow
	@Nullable
	protected PlayerEntity attackingPlayer;

	protected LivingEntityMixin(EntityType<?> type, World world) {
		super(type, world);
	}

	/** Frozen attackers hit 1.5x harder; frozen victims take 2x fire damage. */
	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
	private float frostbound$scaleDamage(float amount, @Local(argsOnly = true) DamageSource source) {
		if (FrozenMob.isFrozen(source.getAttacker())) {
			amount *= FrostStats.DAMAGE_MULTIPLIER;
		}
		if (FrozenMob.isFrozen(this) && source.isIn(DamageTypeTags.IS_FIRE)) {
			amount *= FrostStats.FIRE_WEAKNESS;
		}
		return amount;
	}

	/** Frostbite: a successful hit from a frozen mob slows you and frosts your screen edges. */
	@Inject(method = "damage", at = @At("RETURN"))
	private void frostbound$frostbite(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() || this.getWorld().isClient) {
			return;
		}
		Entity attacker = source.getAttacker();
		if (attacker == this || !FrozenMob.isFrozen(attacker)) {
			return;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		self.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, FrostStats.FROSTBITE_TICKS, 0), attacker);
		if (self.canFreeze()) {
			// Stay just under the threshold where powder-snow freeze damage starts.
			int ticks = Math.min(self.getFrozenTicks() + 100, self.getMinFreezeDamageTicks() - 1);
			self.setFrozenTicks(ticks);
		}
	}

	/** Roll the extra ice loot table on top of the mob's normal drops. */
	@Inject(method = "dropLoot", at = @At("TAIL"))
	private void frostbound$dropIce(DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
		if (!FrozenMob.isFrozen(this) || !(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(world)
				.add(LootContextParameters.THIS_ENTITY, this)
				.add(LootContextParameters.ORIGIN, this.getPos())
				.add(LootContextParameters.DAMAGE_SOURCE, source)
				.addOptional(LootContextParameters.ATTACKING_ENTITY, source.getAttacker())
				.addOptional(LootContextParameters.DIRECT_ATTACKING_ENTITY, source.getSource());
		if (causedByPlayer && this.attackingPlayer != null) {
			builder = builder.add(LootContextParameters.LAST_DAMAGE_PLAYER, this.attackingPlayer)
					.luck(this.attackingPlayer.getLuck());
		}
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(FrozenMobs.FROZEN_LOOT);
		table.generateLoot(builder.build(LootContextTypes.ENTITY), this::dropStack);
	}

	/** Shatter effect: ice shards + glass break sound when a frozen mob dies. */
	@Inject(method = "onDeath", at = @At("TAIL"))
	private void frostbound$shatter(DamageSource source, CallbackInfo ci) {
		if (!FrozenMob.isFrozen(this) || !(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		double halfWidth = this.getWidth() / 2.0;
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.ICE.getDefaultState()),
				this.getX(), this.getBodyY(0.5), this.getZ(),
				40, halfWidth, this.getHeight() / 3.0, halfWidth, 0.15);
		world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, this.getSoundCategory(),
				1.0f, 0.8f + this.random.nextFloat() * 0.4f);
	}
}

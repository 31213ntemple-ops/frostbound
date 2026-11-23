package com.frostbound.mixin.imbuement;

import com.frostbound.imbuement.FrostBonuses;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Cold Iron, Frostmail and Quenched: frost bonuses that change how much damage a hit does. */
@Mixin(LivingEntity.class)
abstract class FrostBonusDamageMixin {
	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
	private float frostbound$frostBonuses(float amount, @Local(argsOnly = true) DamageSource source) {
		return FrostBonuses.modifyDamage((LivingEntity) (Object) this, source, amount);
	}
}

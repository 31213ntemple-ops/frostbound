package com.frostbound.mixin.vessels;

import com.frostbound.vessels.FrozenHeart;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The frozen heart soaks up damage first: after armour, enchantments and resistance have been
 * applied, but before absorption (golden apples) and health.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	@ModifyExpressionValue(method = "applyDamage", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/player/PlayerEntity;modifyAppliedDamage(Lnet/minecraft/entity/damage/DamageSource;F)F"))
	private float frostbound$frozenHeartFirst(float amount, @Local(argsOnly = true) DamageSource source) {
		return FrozenHeart.absorb((PlayerEntity) (Object) this, source, amount);
	}
}

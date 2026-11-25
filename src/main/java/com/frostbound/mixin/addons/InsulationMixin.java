package com.frostbound.mixin.addons;

import com.frostbound.addons.FlameExtract;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Flame extract's insulation ({@link FlameExtract}): fully insulated, you can't freeze (canFreeze, which powder
 * snow and Frozen mobs' Frostbite both ask); partly insulated, each tick powder snow would freeze you a little
 * more is skipped with a chance equal to how insulated you are (25% per extract), so it takes that much longer.
 */
@Mixin(LivingEntity.class)
abstract class InsulationMixin {
	@Inject(method = "canFreeze", at = @At("RETURN"), cancellable = true)
	private void frostbound$fullyInsulated(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && FlameExtract.isFullyInsulated((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@WrapOperation(method = "tickMovement",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;setFrozenTicks(I)V"))
	private void frostbound$partlyInsulated(LivingEntity self, int ticks, Operation<Void> original) {
		if (ticks > self.getFrozenTicks() && self.getRandom().nextDouble() < FlameExtract.insulation(self)) {
			return;
		}
		original.call(self, ticks);
	}
}

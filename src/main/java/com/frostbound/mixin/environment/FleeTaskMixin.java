package com.frostbound.mixin.environment;

import com.frostbound.environment.GoatGrudges;
import net.minecraft.entity.ai.brain.task.FleeTask;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A goat with a grudge (GoatGrudges) stands its ground instead of panicking. */
@Mixin(FleeTask.class)
public abstract class FleeTaskMixin {
	@Inject(method = "shouldRun(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/mob/PathAwareEntity;)Z",
			at = @At("HEAD"), cancellable = true)
	private void frostbound$noPanicWithAGrudge(ServerWorld world, PathAwareEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (GoatGrudges.target(entity) != null) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "shouldKeepRunning(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/mob/PathAwareEntity;J)Z",
			at = @At("HEAD"), cancellable = true)
	private void frostbound$stopPanickingWithAGrudge(ServerWorld world, PathAwareEntity entity, long time,
			CallbackInfoReturnable<Boolean> cir) {
		if (GoatGrudges.target(entity) != null) {
			cir.setReturnValue(false);
		}
	}
}

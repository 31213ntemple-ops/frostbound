package com.frostbound.mixin.environment;

import com.frostbound.environment.GoatGrudges;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.PrepareRamTask;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A goat with a grudge (GoatGrudges) lines up its ram on whoever hit it, instead of the first
 * mob it happens to see. The rest of the ram (backing off, the head-down tell, the charge) is vanilla.
 */
@Mixin(PrepareRamTask.class)
public abstract class PrepareRamTaskMixin {
	@Shadow
	private void findRam(PathAwareEntity entity, LivingEntity target) {
		throw new AssertionError();
	}

	@Inject(method = "run(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/mob/PathAwareEntity;J)V",
			at = @At("HEAD"), cancellable = true)
	private void frostbound$ramWhoeverHitUs(ServerWorld world, PathAwareEntity entity, long time, CallbackInfo ci) {
		LivingEntity target = GoatGrudges.target(entity);
		if (target != null) {
			this.findRam(entity, target);
			ci.cancel();
		}
	}

	/**
	 * No ram lined up (no path to a start spot, often because the hit knocked the goat into the air):
	 * vanilla puts it on a 30 s cooldown, but a goat with a grudge tries again shortly.
	 */
	@Inject(method = "finishRunning(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/mob/PathAwareEntity;J)V",
			at = @At("HEAD"), cancellable = true)
	private void frostbound$retryWithAGrudge(ServerWorld world, PathAwareEntity entity, long time, CallbackInfo ci) {
		Brain<?> brain = entity.getBrain();
		if (!brain.hasMemoryModule(MemoryModuleType.RAM_TARGET) && GoatGrudges.target(entity) != null) {
			world.sendEntityStatus(entity, EntityStatuses.FINISH_RAM);
			brain.remember(MemoryModuleType.RAM_COOLDOWN_TICKS, GoatGrudges.RETRY_TICKS);
			ci.cancel();
		}
	}
}

package com.frostbound.mixin.environment;

import com.frostbound.environment.SnowyPlants;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Snowfall: snow landing on a plant makes the plant snowy instead of replacing it. */
@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
	@WrapOperation(method = "tickIceAndSnow", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/world/ServerWorld;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Z"))
	private boolean frostbound$snowIntoPlant(ServerWorld world, BlockPos pos, BlockState state,
			Operation<Boolean> original) {
		return original.call(world, pos, SnowyPlants.snowInto(world.getBlockState(pos), state));
	}
}

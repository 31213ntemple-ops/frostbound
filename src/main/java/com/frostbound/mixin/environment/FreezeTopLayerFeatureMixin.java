package com.frostbound.mixin.environment;

import com.frostbound.environment.SnowyPlants;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.FreezeTopLayerFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** World generation: snow placed on top of a plant makes the plant snowy instead of replacing it. */
@Mixin(FreezeTopLayerFeature.class)
public abstract class FreezeTopLayerFeatureMixin {
	@WrapOperation(method = "generate", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/StructureWorldAccess;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
	private boolean frostbound$snowIntoPlant(StructureWorldAccess world, BlockPos pos, BlockState state, int flags,
			Operation<Boolean> original) {
		return original.call(world, pos, SnowyPlants.snowInto(world.getBlockState(pos), state), flags);
	}
}

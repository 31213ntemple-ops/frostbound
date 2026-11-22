package com.frostbound.mixin.environment;

import com.frostbound.ice.IceChanges;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.block.IceBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla turns ice into water when it's broken without Silk Touch (over anything solid or
 * liquid). With the Ice module on, breaking it just leaves air and drops the block.
 */
@Mixin(IceBlock.class)
public abstract class IceBlockMixin {
	@WrapOperation(method = "afterBreak", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Z"))
	private boolean frostbound$dontMelt(World world, BlockPos pos, BlockState state, Operation<Boolean> original) {
		return IceChanges.isEnabled() ? false : original.call(world, pos, state);
	}
}

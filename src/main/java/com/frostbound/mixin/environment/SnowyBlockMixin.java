package com.frostbound.mixin.environment;

import com.frostbound.environment.SnowyPlants;
import net.minecraft.block.BlockState;
import net.minecraft.block.SnowyBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Grass blocks, podzol and mycelium turn snowy when snow is on top of them. Count a
 * snowy plant as snow too, so the grass under a snowy flower gets its snowy sides.
 */
@Mixin(SnowyBlock.class)
public abstract class SnowyBlockMixin {
	@Inject(method = "isSnow", at = @At("RETURN"), cancellable = true)
	private static void frostbound$snowyPlantCountsAsSnow(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && SnowyPlants.isSnowy(state)) {
			cir.setReturnValue(true);
		}
	}
}

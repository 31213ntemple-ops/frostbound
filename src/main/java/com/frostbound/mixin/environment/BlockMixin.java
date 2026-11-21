package com.frostbound.mixin.environment;

import com.frostbound.environment.SnowyPlants;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Adds the {@code snowy} property to snowloggable plants (see SnowyPlants) and makes
 * sure it defaults to false. Every block's constructor builds its states through
 * appendProperties, so wrapping that call reaches every plant class, vanilla or modded.
 */
@Mixin(Block.class)
public abstract class BlockMixin {
	@WrapOperation(method = "<init>", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/block/Block;appendProperties(Lnet/minecraft/state/StateManager$Builder;)V"))
	private void frostbound$addSnowyProperty(Block self, StateManager.Builder<Block, BlockState> builder,
			Operation<Void> original) {
		original.call(self, builder);
		if (SnowyPlants.isSnowloggable(self)) {
			builder.add(SnowyPlants.SNOWY);
		}
	}

	// A new boolean property would otherwise default to true (its first value).
	@ModifyVariable(method = "setDefaultState", at = @At("HEAD"), argsOnly = true)
	private BlockState frostbound$notSnowyByDefault(BlockState state) {
		return state.contains(SnowyPlants.SNOWY) ? state.with(SnowyPlants.SNOWY, false) : state;
	}
}

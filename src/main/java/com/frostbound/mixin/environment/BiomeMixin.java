package com.frostbound.mixin.environment;

import com.frostbound.environment.SnowyPlants;
import com.frostbound.environment.WinterEnvironment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * - Open water: in biomes tagged frostbound:open_water, snow still falls but water
 *   never turns to ice. The tag is empty when the environment module is off.
 * - Snow on plants: vanilla only lets snow settle into air. This also allows it into
 *   a plant that can become snowy (see SnowyPlants), with the same checks vanilla uses.
 *
 * canSetIce / canSetSnow are what both world generation (freeze_top_layer) and live
 * snowfall ask, so these cover both.
 */
@Mixin(Biome.class)
public abstract class BiomeMixin {
	@Inject(method = "canSetIce(Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;Z)Z",
			at = @At("HEAD"), cancellable = true)
	private void frostbound$openWater(WorldView world, BlockPos pos, boolean doWaterCheck,
			CallbackInfoReturnable<Boolean> cir) {
		RegistryEntry<Biome> self = world.getRegistryManager().get(RegistryKeys.BIOME).getEntry((Biome) (Object) this);
		if (self.isIn(WinterEnvironment.OPEN_WATER)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canSetSnow", at = @At("HEAD"), cancellable = true)
	private void frostbound$snowOnPlants(WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		BlockState state = world.getBlockState(pos);
		if (!SnowyPlants.canBecomeSnowy(state) || !WinterEnvironment.isEnabled()) {
			return; // not a plant: let vanilla decide
		}
		Biome self = (Biome) (Object) this;
		cir.setReturnValue(!self.doesNotSnow(pos)
				&& pos.getY() >= world.getBottomY() && pos.getY() < world.getTopY()
				&& world.getLightLevel(LightType.BLOCK, pos) < 10
				&& Blocks.SNOW.getDefaultState().canPlaceAt(world, pos));
	}
}

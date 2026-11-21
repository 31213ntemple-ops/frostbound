package com.frostbound.mixin.environment;

import com.frostbound.environment.WinterEnvironment;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Open water: in biomes tagged frostbound:open_water, snow still falls but water
 * never turns to ice. canSetIce is what both world generation (the
 * freeze_top_layer feature) and live ice formation ask, so this covers both.
 *
 * The tag is empty when the environment module is off, so this does nothing then.
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
}

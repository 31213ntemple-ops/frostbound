package com.frostbound.mixin.campfire;

import com.frostbound.campfire.CampfireWarmth;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Tough As Nails' heat sources: {@code proximityModifier} flood-fills the air round a position
 * (near_heat_cool_proximity steps) and collects the heating and cooling blocks it touches, then warms
 * the position one step if there are more heating ones. Right after the fill, campfires the fill found
 * are dropped and the one {@link CampfireWarmth} finds (a circle, the radius in frostbound.json) is put
 * in instead, so the warm area matches the ring drawn round the fire. Other heat sources are untouched.
 *
 * Only applied when TAN is installed (FrostboundMixinPlugin); compiles without it, hence {@link Pseudo}
 * and no TAN types in the handler (TemperatureLevel is left out of the arguments).
 */
@Pseudo
@Mixin(targets = "toughasnails.temperature.TemperatureHelperImpl", remap = false)
public abstract class TemperatureHelperImplMixin {
	@Inject(method = "proximityModifier",
			at = @At(value = "INVOKE", target = "Ltoughasnails/temperature/AreaFill;fill", shift = At.Shift.AFTER),
			remap = false)
	private static void frostbound$campfireCircle(CallbackInfoReturnable<?> cir,
			@Local(argsOnly = true) World world, @Local(ordinal = 0) BlockPos pos,
			@Local(ordinal = 0) Set<BlockPos> heating) {
		heating.removeIf(found -> CampfireWarmth.isWarming(world.getBlockState(found)));
		CampfireWarmth.findWarmingCampfire(world, pos).ifPresent(heating::add);
	}
}

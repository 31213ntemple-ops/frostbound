package com.frostbound.mixin.environment;

import com.frostbound.environment.WinterBiomePool;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

/**
 * Cold biomes only: when the "minecraft:overworld" parameter list is built (each time a
 * world's registries load), every removed biome in vanilla's climate grid is looked up as
 * its replacement instead. See {@link WinterBiomePool}.
 */
@Mixin(MultiNoiseBiomeSourceParameterList.class)
abstract class MultiNoiseBiomeSourceParameterListMixin {
	@Shadow
	@Final
	private MultiNoiseBiomeSourceParameterList.Preset preset;

	@ModifyArg(method = "<init>", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/biome/source/MultiNoiseBiomeSourceParameterList$Preset$BiomeSourceFunction;apply(Ljava/util/function/Function;)Lnet/minecraft/world/biome/source/util/MultiNoiseUtil$Entries;"))
	private Function<RegistryKey<Biome>, Object> frostbound$coldBiomesOnly(Function<RegistryKey<Biome>, Object> lookup) {
		if (preset != MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD || !WinterBiomePool.isEnabled()) {
			return lookup;
		}
		return key -> lookup.apply(WinterBiomePool.replace(key));
	}
}

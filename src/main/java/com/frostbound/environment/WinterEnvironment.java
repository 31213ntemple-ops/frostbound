package com.frostbound.environment;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.world.biome.Biome;

/**
 * Module: Winter Environment. Every Overworld biome becomes a winter variant.
 *
 *  - Climate table:        {@link WinterClimate}
 *  - Applying it:          {@link WinterBiomeModifications}
 *  - Open (unfreezing) water: tag frostbound:open_water, read by mixin.environment.BiomeMixin
 *  - Temperature-mod data: toughasnails:cold_biomes tag
 *
 * All data files are in the built-in pack resourcepacks/winter_environment.
 */
public final class WinterEnvironment {
	/** Biomes where snow falls but water never freezes. */
	public static final TagKey<Biome> OPEN_WATER = TagKey.of(RegistryKeys.BIOME, Frostbound.id("open_water"));

	private WinterEnvironment() {}

	public static boolean isEnabled() {
		return FrostboundConfig.get().modules.environment;
	}

	public static void init() {
		WinterBiomeModifications.register();
		WinterWildlife.register();
		GoatGrudges.register();

		// The module's data (biome tags, temperature-mod compat) lives in a built-in data pack
		// at resources/resourcepacks/winter_environment, so it's only present while the module is on.
		ModContainer mod = FabricLoader.getInstance().getModContainer(Frostbound.MOD_ID).orElseThrow();
		ResourceManagerHelper.registerBuiltinResourcePack(Frostbound.id("winter_environment"), mod,
				Text.literal("Frostbound: Winter Environment"), ResourcePackActivationType.ALWAYS_ENABLED);
	}
}

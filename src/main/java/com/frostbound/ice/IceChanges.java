package com.frostbound.ice;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.world.gen.feature.PlacedFeature;

/**
 * Module: Ice. Ice is a resource here, so:
 *
 *  - Ice, packed ice and blue ice drop themselves when mined with a pickaxe better than stone
 *    (item tag frostbound:ice_mining_tools: iron and up, plus a copper pickaxe if present), no
 *    Silk Touch needed. Ice also no longer turns into water when broken (see mixin.environment.IceBlockMixin).
 *  - Packed ice and blue ice can't be crafted (their recipes are switched off).
 *  - Blue ice no longer generates in the Overworld (no blue ice patches, no blue icebergs).
 *  - New block textures for all three.
 *
 * The loot tables, recipes and textures are in the built-in pack resourcepacks/ice.
 */
public final class IceChanges {
	private static final RegistryKey<PlacedFeature> BLUE_ICE_PATCH = feature("blue_ice");
	private static final RegistryKey<PlacedFeature> BLUE_ICEBERG = feature("iceberg_blue");

	private IceChanges() {}

	public static boolean isEnabled() {
		return FrostboundConfig.get().modules.ice;
	}

	public static void init() {
		ModContainer mod = FabricLoader.getInstance().getModContainer(Frostbound.MOD_ID).orElseThrow();
		ResourceManagerHelper.registerBuiltinResourcePack(Frostbound.id("ice"), mod,
				Text.literal("Frostbound: Ice"), ResourcePackActivationType.ALWAYS_ENABLED);

		BiomeModifications.create(Frostbound.id("no_blue_ice")).add(ModificationPhase.REMOVALS,
				BiomeSelectors.foundInOverworld(),
				context -> {
					context.getGenerationSettings().removeFeature(BLUE_ICE_PATCH);
					context.getGenerationSettings().removeFeature(BLUE_ICEBERG);
				});
	}

	private static RegistryKey<PlacedFeature> feature(String path) {
		return RegistryKey.of(RegistryKeys.PLACED_FEATURE, net.minecraft.util.Identifier.ofVanilla(path));
	}
}

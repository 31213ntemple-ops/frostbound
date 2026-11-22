package com.frostbound.environment;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which biomes new Overworld terrain can use. Only cold biomes (plus Frostbound's own)
 * generate; every other vanilla biome is swapped for a cold one in the same spot of
 * vanilla's climate grid, so dry spots stay open, humid spots stay wooded and
 * mountains stay mountains.
 *
 * The swap is applied to the "minecraft:overworld" biome parameter list by
 * mixin.environment.MultiNoiseBiomeSourceParameterListMixin, so terrain shape (vanilla
 * or a terrain mod like Tectonic) is untouched; only the biome painted on it changes.
 * It only affects chunks generated from now on: explored chunks of an old world keep
 * their biomes (still turned into winter versions by {@link WinterClimate}).
 *
 * Cave biomes are left alone. Biomes from mods that add their own Overworld biome list
 * (e.g. Terralith) aren't affected.
 */
public final class WinterBiomePool {
	private WinterBiomePool() {}

	/** Open snowy land with occasional small oaks, horses and donkeys. */
	public static final RegistryKey<Biome> SPARSE_FOREST = RegistryKey.of(RegistryKeys.BIOME, Frostbound.id("sparse_forest"));

	/** Removed biome -> the biome that generates in its place. */
	public static final Map<RegistryKey<Biome>, RegistryKey<Biome>> REPLACEMENTS = new LinkedHashMap<>();

	static {
		// Open lowland: plains, flowers and savanna become sparse forest.
		swap(BiomeKeys.PLAINS, SPARSE_FOREST);
		swap(BiomeKeys.SUNFLOWER_PLAINS, SPARSE_FOREST);
		swap(BiomeKeys.FLOWER_FOREST, SPARSE_FOREST);
		swap(BiomeKeys.SAVANNA, SPARSE_FOREST);
		swap(BiomeKeys.SAVANNA_PLATEAU, SPARSE_FOREST);
		swap(BiomeKeys.MEADOW, SPARSE_FOREST);

		// Woodland: broadleaf forests become spruce.
		swap(BiomeKeys.FOREST, BiomeKeys.TAIGA);
		swap(BiomeKeys.BIRCH_FOREST, BiomeKeys.TAIGA);
		swap(BiomeKeys.OLD_GROWTH_BIRCH_FOREST, BiomeKeys.OLD_GROWTH_PINE_TAIGA);
		swap(BiomeKeys.DARK_FOREST, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA);
		swap(BiomeKeys.JUNGLE, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA);
		swap(BiomeKeys.BAMBOO_JUNGLE, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA);
		swap(BiomeKeys.SPARSE_JUNGLE, BiomeKeys.TAIGA);

		// Wetlands.
		swap(BiomeKeys.SWAMP, BiomeKeys.SNOWY_TAIGA);
		swap(BiomeKeys.MANGROVE_SWAMP, BiomeKeys.SNOWY_TAIGA);

		// Dry land: deserts and badlands become tundra.
		swap(BiomeKeys.DESERT, BiomeKeys.SNOWY_PLAINS);
		swap(BiomeKeys.BADLANDS, BiomeKeys.SNOWY_PLAINS);
		swap(BiomeKeys.ERODED_BADLANDS, BiomeKeys.ICE_SPIKES);
		swap(BiomeKeys.WOODED_BADLANDS, BiomeKeys.SNOWY_TAIGA);

		// Highlands.
		swap(BiomeKeys.WINDSWEPT_SAVANNA, BiomeKeys.WINDSWEPT_HILLS);
		swap(BiomeKeys.CHERRY_GROVE, BiomeKeys.GROVE);

		// Coasts, rivers and seas.
		swap(BiomeKeys.BEACH, BiomeKeys.SNOWY_BEACH);
		swap(BiomeKeys.RIVER, BiomeKeys.FROZEN_RIVER);
		swap(BiomeKeys.OCEAN, BiomeKeys.COLD_OCEAN);
		swap(BiomeKeys.DEEP_OCEAN, BiomeKeys.DEEP_COLD_OCEAN);
		swap(BiomeKeys.LUKEWARM_OCEAN, BiomeKeys.COLD_OCEAN);
		swap(BiomeKeys.DEEP_LUKEWARM_OCEAN, BiomeKeys.DEEP_COLD_OCEAN);
		swap(BiomeKeys.WARM_OCEAN, BiomeKeys.COLD_OCEAN);
		swap(BiomeKeys.MUSHROOM_FIELDS, BiomeKeys.SNOWY_PLAINS);
	}

	private static void swap(RegistryKey<Biome> removed, RegistryKey<Biome> replacement) {
		REPLACEMENTS.put(removed, replacement);
	}

	public static boolean isEnabled() {
		return WinterEnvironment.isEnabled() && FrostboundConfig.get().environment.coldBiomesOnly;
	}

	/** The biome that generates where vanilla would put {@code key}. */
	public static RegistryKey<Biome> replace(RegistryKey<Biome> key) {
		return REPLACEMENTS.getOrDefault(key, key);
	}
}

package com.frostbound.environment;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The winter climate of every vanilla Overworld biome. This table is the single
 * source of truth: {@link WinterBiomeModifications} turns it into biome changes,
 * and the vanilla temperature it produces is what temperature mods read.
 *
 * To tweak a biome, change its line in {@link #TABLE}. Biomes from other mods
 * get a profile from {@link #forModdedBiome(float, boolean)}.
 */
public final class WinterClimate {
	private WinterClimate() {}

	/** What happens to still water in the biome. */
	public enum WaterMode {
		/** Freezes solid (lakes, rivers, swamps). */
		FROZEN,
		/** Patchy ice floes with open water between them (vanilla frozen-ocean noise). */
		FLOES,
		/** Snow falls but water never freezes (tag frostbound:open_water). */
		OPEN
	}

	/** Broad climate band. Each band has its own winter colour palette. */
	public enum Zone {
		//            grass     foliage   water     waterFog  sky       fog
		ARCTIC(   0x80B497, 0x60A17B, 0x3938C9, 0x050533, 0x7FA1FF, 0xD8E6F2),
		BOREAL(   0x86B87F, 0x68A55F, 0x3D57D6, 0x050533, 0x7BA4FF, 0xCFDCE8),
		TEMPERATE(0x8EB27C, 0x6FA059, 0x3F62C9, 0x060A33, 0x87A8E8, 0xD3DDE6),
		MILD(     0x90AE6C, 0x6E9B47, 0x3A70BE, 0x061033, 0x92AEDD, 0xD8E0E6),
		DRY(      0xA9A777, 0x9B9A60, 0x4A7CB4, 0x081433, 0x9FB4D4, 0xDFE3E6);

		public final int grass, foliage, water, waterFog, sky, fog;

		Zone(int grass, int foliage, int water, int waterFog, int sky, int fog) {
			this.grass = grass;
			this.foliage = foliage;
			this.water = water;
			this.waterFog = waterFog;
			this.sky = sky;
			this.fog = fog;
		}

		static Zone forCelsius(float celsius) {
			if (celsius <= -20) return ARCTIC;
			if (celsius <= -13) return BOREAL;
			if (celsius <= -7) return TEMPERATE;
			if (celsius <= -3) return MILD;
			return DRY;
		}
	}

	/**
	 * @param celsius typical winter air temperature at the surface
	 */
	public record WinterProfile(float celsius, Zone zone, WaterMode water) {
		/** Vanilla biome temperature. Below 0.15 = snow; every winter biome is below 0 °C, so it always snows. */
		public float vanillaTemperature() {
			return toVanilla(celsius);
		}
	}

	/** 0 °C sits exactly on vanilla's snow line (0.15); each 10 °C is 0.25. */
	public static float toVanilla(float celsius) {
		return 0.15f + celsius / 40f;
	}

	public static float toCelsius(float vanillaTemperature) {
		return (vanillaTemperature - 0.15f) * 40f;
	}

	/** Cave biomes are left alone: Tough As Nails ignores biomes underground, so caves stay a refuge. */
	public static final Set<RegistryKey<Biome>> UNCHANGED = Set.of(
			BiomeKeys.DRIPSTONE_CAVES, BiomeKeys.LUSH_CAVES, BiomeKeys.DEEP_DARK);

	public static final Map<RegistryKey<Biome>, WinterProfile> TABLE = new LinkedHashMap<>();

	static {
		// Arctic: the original snowy biomes, now the harshest places.
		arctic(BiomeKeys.SNOWY_PLAINS, -22, WaterMode.FROZEN);
		arctic(BiomeKeys.ICE_SPIKES, -26, WaterMode.FROZEN);
		arctic(BiomeKeys.SNOWY_TAIGA, -22, WaterMode.FROZEN);
		arctic(BiomeKeys.SNOWY_SLOPES, -26, WaterMode.FROZEN);
		arctic(BiomeKeys.GROVE, -24, WaterMode.FROZEN);
		arctic(BiomeKeys.FROZEN_PEAKS, -30, WaterMode.FROZEN);
		arctic(BiomeKeys.JAGGED_PEAKS, -30, WaterMode.FROZEN);
		arctic(BiomeKeys.SNOWY_BEACH, -20, WaterMode.FROZEN);
		arctic(BiomeKeys.FROZEN_RIVER, -22, WaterMode.FROZEN);
		arctic(BiomeKeys.FROZEN_OCEAN, -20, WaterMode.FLOES);
		arctic(BiomeKeys.DEEP_FROZEN_OCEAN, -20, WaterMode.FLOES);

		// Boreal: taigas and windswept highlands.
		put(BiomeKeys.TAIGA, -15, WaterMode.FROZEN);
		put(BiomeKeys.OLD_GROWTH_PINE_TAIGA, -15, WaterMode.FROZEN);
		put(BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA, -16, WaterMode.FROZEN);
		put(BiomeKeys.WINDSWEPT_HILLS, -17, WaterMode.FROZEN);
		put(BiomeKeys.WINDSWEPT_GRAVELLY_HILLS, -17, WaterMode.FROZEN);
		put(BiomeKeys.WINDSWEPT_FOREST, -16, WaterMode.FROZEN);
		put(BiomeKeys.STONY_PEAKS, -18, WaterMode.FROZEN);
		put(BiomeKeys.STONY_SHORE, -14, WaterMode.FROZEN);
		put(BiomeKeys.COLD_OCEAN, -13, WaterMode.FLOES);
		put(BiomeKeys.DEEP_COLD_OCEAN, -13, WaterMode.FLOES);

		// Temperate: plains and broadleaf forests under deep snow.
		put(BiomeKeys.PLAINS, -8, WaterMode.FROZEN);
		put(BiomeKeys.SUNFLOWER_PLAINS, -8, WaterMode.FROZEN);
		put(BiomeKeys.FOREST, -9, WaterMode.FROZEN);
		put(BiomeKeys.FLOWER_FOREST, -8, WaterMode.FROZEN);
		put(BiomeKeys.BIRCH_FOREST, -9, WaterMode.FROZEN);
		put(BiomeKeys.OLD_GROWTH_BIRCH_FOREST, -9, WaterMode.FROZEN);
		put(BiomeKeys.DARK_FOREST, -10, WaterMode.FROZEN);
		put(BiomeKeys.MEADOW, -12, WaterMode.FROZEN);
		put(BiomeKeys.CHERRY_GROVE, -10, WaterMode.FROZEN);
		put(BiomeKeys.SWAMP, -7, WaterMode.FROZEN);
		put(BiomeKeys.RIVER, -8, WaterMode.FROZEN);
		put(BiomeKeys.BEACH, -7, WaterMode.FROZEN);
		put(BiomeKeys.OCEAN, -7, WaterMode.FLOES);
		put(BiomeKeys.DEEP_OCEAN, -7, WaterMode.FLOES);

		// Frostbound's own biomes.
		put(WinterBiomePool.SPARSE_FOREST, -12, WaterMode.FROZEN);

		// Mild: jungles and savannas, the gentlest winter.
		put(BiomeKeys.JUNGLE, -3, WaterMode.FROZEN);
		put(BiomeKeys.SPARSE_JUNGLE, -3, WaterMode.FROZEN);
		put(BiomeKeys.BAMBOO_JUNGLE, -3, WaterMode.FROZEN);
		put(BiomeKeys.SAVANNA, -4, WaterMode.FROZEN);
		put(BiomeKeys.SAVANNA_PLATEAU, -5, WaterMode.FROZEN);
		put(BiomeKeys.WINDSWEPT_SAVANNA, -6, WaterMode.FROZEN);
		put(BiomeKeys.MANGROVE_SWAMP, -3, WaterMode.FROZEN);
		put(BiomeKeys.MUSHROOM_FIELDS, -4, WaterMode.FROZEN);
		put(BiomeKeys.LUKEWARM_OCEAN, -3, WaterMode.FLOES);
		put(BiomeKeys.DEEP_LUKEWARM_OCEAN, -3, WaterMode.FLOES);

		// Dry: deserts and badlands, cold and barren but barely below freezing.
		put(BiomeKeys.DESERT, -1, WaterMode.FROZEN);
		put(BiomeKeys.BADLANDS, -2, WaterMode.FROZEN);
		put(BiomeKeys.WOODED_BADLANDS, -2, WaterMode.FROZEN);
		put(BiomeKeys.ERODED_BADLANDS, -2, WaterMode.FROZEN);
		put(BiomeKeys.WARM_OCEAN, -1, WaterMode.OPEN);
	}

	private static void arctic(RegistryKey<Biome> key, float celsius, WaterMode water) {
		TABLE.put(key, new WinterProfile(celsius, Zone.ARCTIC, water));
	}

	private static void put(RegistryKey<Biome> key, float celsius, WaterMode water) {
		TABLE.put(key, new WinterProfile(celsius, Zone.forCelsius(celsius), water));
	}

	// Original vanilla temperature -> winter °C, interpolated between these points.
	private static final float[] ORIGINAL = {-0.5f, 0.0f, 0.25f, 0.8f, 0.95f, 2.0f};
	private static final float[] WINTER_C = {-30f, -22f, -15f, -8f, -3f, -1f};

	/** A winter profile for a biome we don't know (from another mod), based on how warm it originally was. */
	public static WinterProfile forModdedBiome(float originalTemperature, boolean ocean) {
		float celsius = interpolate(originalTemperature);
		return new WinterProfile(celsius, Zone.forCelsius(celsius), ocean ? WaterMode.FLOES : WaterMode.FROZEN);
	}

	private static float interpolate(float t) {
		if (t <= ORIGINAL[0]) return WINTER_C[0];
		for (int i = 1; i < ORIGINAL.length; i++) {
			if (t <= ORIGINAL[i]) {
				float f = (t - ORIGINAL[i - 1]) / (ORIGINAL[i] - ORIGINAL[i - 1]);
				return WINTER_C[i - 1] + f * (WINTER_C[i] - WINTER_C[i - 1]);
			}
		}
		return WINTER_C[WINTER_C.length - 1];
	}
}

package com.frostbound.environment;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;

/**
 * Public climate API, for Frostbound's own modules and for other mods.
 *
 * Temperatures are derived from the biome's vanilla base temperature, which the
 * environment module sets from {@link WinterClimate}. So for winter biomes this
 * returns exactly the table's °C value, and modded biomes get a consistent number too.
 */
public final class FrostboundClimate {
	/** At or below this, a biome counts as arctic (the harshest band). */
	public static final float ARCTIC_CELSIUS = -20f;

	private FrostboundClimate() {}

	/** Typical surface air temperature of the biome in °C. */
	public static float getCelsius(RegistryEntry<Biome> biome) {
		return WinterClimate.toCelsius(biome.value().getTemperature());
	}

	public static boolean isArctic(RegistryEntry<Biome> biome) {
		return getCelsius(biome) <= ARCTIC_CELSIUS;
	}
}

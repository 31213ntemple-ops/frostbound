package com.frostbound.environment;

import com.frostbound.Frostbound;
import com.frostbound.environment.WinterClimate.WaterMode;
import com.frostbound.environment.WinterClimate.WinterProfile;
import net.fabricmc.fabric.api.biome.v1.BiomeModification;
import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeEffects;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.SpawnSettings;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Applies {@link WinterClimate} to the biomes with Fabric's biome modification API.
 * This changes the biomes in place (same IDs), so structures, other mods and
 * existing worlds keep working.
 */
final class WinterBiomeModifications {
	/** These use a noise-based grass tint that would hide the winter palette. */
	private static final Set<RegistryKey<Biome>> SWAMPS = Set.of(BiomeKeys.SWAMP, BiomeKeys.MANGROVE_SWAMP);

	private WinterBiomeModifications() {}

	static void register() {
		BiomeModification winter = BiomeModifications.create(Frostbound.id("winter"));

		// Vanilla biomes: from the table.
		winter.add(ModificationPhase.POST_PROCESSING,
				BiomeSelectors.includeByKey(WinterClimate.TABLE.keySet()),
				(selection, context) -> {
					RegistryKey<Biome> key = selection.getBiomeKey();
					apply(context, WinterClimate.TABLE.get(key));
					if (SWAMPS.contains(key)) {
						context.getEffects().setGrassColorModifier(BiomeEffects.GrassColorModifier.NONE);
					}
				});

		// Biomes from other mods: a generic winter version based on how warm they were.
		winter.add(ModificationPhase.POST_PROCESSING, WinterBiomeModifications::isModdedSurfaceBiome,
				(selection, context) -> apply(context, WinterClimate.forModdedBiome(
						selection.getBiome().getTemperature(),
						selection.hasTag(ConventionalBiomeTags.IS_OCEAN))));

		// Strays join the skeletons outside the arctic (mushroom fields stay monster-free).
		winter.add(ModificationPhase.ADDITIONS,
				BiomeSelectors.includeByKey(WinterClimate.TABLE.keySet())
						.and(s -> WinterClimate.TABLE.get(s.getBiomeKey()).zone() != WinterClimate.Zone.ARCTIC)
						.and(BiomeSelectors.excludeByKey(BiomeKeys.MUSHROOM_FIELDS)),
				context -> context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER,
						new SpawnSettings.SpawnEntry(EntityType.STRAY, 40, 4, 4)));
	}

	private static boolean isModdedSurfaceBiome(BiomeSelectionContext s) {
		Predicate<BiomeSelectionContext> overworld = BiomeSelectors.foundInOverworld();
		RegistryKey<Biome> key = s.getBiomeKey();
		return overworld.test(s)
				&& !WinterClimate.TABLE.containsKey(key)
				&& !WinterClimate.UNCHANGED.contains(key)
				&& !s.hasTag(ConventionalBiomeTags.IS_CAVE)
				&& !s.hasTag(ConventionalBiomeTags.IS_UNDERGROUND);
	}

	private static void apply(BiomeModificationContext context, WinterProfile profile) {
		BiomeModificationContext.WeatherContext weather = context.getWeather();
		weather.setTemperature(profile.vanillaTemperature());
		weather.setPrecipitation(true);
		weather.setTemperatureModifier(profile.water() == WaterMode.FLOES
				? Biome.TemperatureModifier.FROZEN
				: Biome.TemperatureModifier.NONE);

		WinterClimate.Zone zone = profile.zone();
		BiomeModificationContext.EffectsContext effects = context.getEffects();
		effects.setGrassColor(zone.grass);
		effects.setFoliageColor(zone.foliage);
		effects.setWaterColor(zone.water);
		effects.setWaterFogColor(zone.waterFog);
		effects.setSkyColor(zone.sky);
		effects.setFogColor(zone.fog);
	}
}

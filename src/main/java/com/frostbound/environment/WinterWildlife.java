package com.frostbound.environment;

import com.frostbound.Frostbound;
import net.fabricmc.fabric.api.biome.v1.BiomeModification;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.SpawnSettings;

import java.util.Set;

/**
 * Which animals live where in the winter Overworld:
 *  - Horses and donkeys only in the Sparse Forest.
 *  - Food animals (sheep, cows, chickens, pigs) in the forests: the taigas, Windswept Forest and
 *    the Sparse Forest already have them in vanilla, so nothing is added there.
 *  - Goats in the harsh biomes. Snowy Slopes and the peaks already have them; Snowy Plains,
 *    Grove and the bare Windswept Hills get herds. The Windswept Hills lose their food animals
 *    (their llamas stay), and Grove gets sheep as its only food animal besides the goats.
 *  - Ice Spikes stay desolate (only vanilla's rabbits and the odd polar bear).
 *
 * Goats drop mutton (resourcepacks/winter_environment, loot_table/entities/goat.json) and ram
 * whatever hurts them ({@link GoatGrudges}).
 */
public final class WinterWildlife {
	private WinterWildlife() {}

	/**
	 * The only biome horses and donkeys spawn in. They're taken out of every other biome, including
	 * ones new terrain no longer uses (plains, savannas, meadow) so old chunks don't keep them.
	 */
	public static final RegistryKey<Biome> HORSE_BIOME = WinterBiomePool.SPARSE_FOREST;

	/** Harsh biomes that get goat herds (on top of vanilla's goats in Snowy Slopes and the peaks). */
	public static final Set<RegistryKey<Biome>> GOAT_HERDS = Set.of(
			BiomeKeys.SNOWY_PLAINS, BiomeKeys.GROVE, BiomeKeys.WINDSWEPT_HILLS, BiomeKeys.WINDSWEPT_GRAVELLY_HILLS);

	/** Harsh biomes whose vanilla food animals are taken out: goats are the food there. */
	public static final Set<RegistryKey<Biome>> NO_FOOD_ANIMALS = Set.of(
			BiomeKeys.WINDSWEPT_HILLS, BiomeKeys.WINDSWEPT_GRAVELLY_HILLS);

	public static final Set<EntityType<?>> FOOD_ANIMALS = Set.of(
			EntityType.SHEEP, EntityType.COW, EntityType.CHICKEN, EntityType.PIG);

	/** Goats come in herds of 2 to 4 (vanilla's own goats are 1 to 3). */
	private static final SpawnSettings.SpawnEntry GOAT_HERD = new SpawnSettings.SpawnEntry(EntityType.GOAT, 10, 2, 4);
	/** Same as vanilla's sheep in forests. */
	private static final SpawnSettings.SpawnEntry GROVE_SHEEP = new SpawnSettings.SpawnEntry(EntityType.SHEEP, 12, 4, 4);

	static void register() {
		BiomeModification wildlife = BiomeModifications.create(Frostbound.id("wildlife"));

		wildlife.add(ModificationPhase.REMOVALS,
				BiomeSelectors.excludeByKey(HORSE_BIOME),
				context -> {
					context.getSpawnSettings().removeSpawnsOfEntityType(EntityType.HORSE);
					context.getSpawnSettings().removeSpawnsOfEntityType(EntityType.DONKEY);
				});

		wildlife.add(ModificationPhase.REMOVALS, BiomeSelectors.includeByKey(NO_FOOD_ANIMALS),
				context -> FOOD_ANIMALS.forEach(context.getSpawnSettings()::removeSpawnsOfEntityType));

		wildlife.add(ModificationPhase.ADDITIONS, BiomeSelectors.includeByKey(GOAT_HERDS),
				context -> context.getSpawnSettings().addSpawn(SpawnGroup.CREATURE, GOAT_HERD));

		wildlife.add(ModificationPhase.ADDITIONS, BiomeSelectors.includeByKey(BiomeKeys.GROVE),
				context -> context.getSpawnSettings().addSpawn(SpawnGroup.CREATURE, GROVE_SHEEP));
	}
}

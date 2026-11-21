package com.frostbound.test;

import com.frostbound.environment.FrostboundClimate;
import com.frostbound.environment.WinterClimate;
import com.frostbound.environment.WinterClimate.WaterMode;
import com.frostbound.environment.WinterClimate.WinterProfile;
import com.frostbound.environment.WinterEnvironment;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks for the winter environment module. Run with: gradlew runGametest
 *
 * The test world itself is a flat plains world, so these tests look biomes up in
 * the registry and ask them directly rather than visiting them.
 */
public class WinterEnvironmentTests implements FabricGameTest {
	private static final TagKey<Biome> TAN_COLD = TagKey.of(RegistryKeys.BIOME, Identifier.of("toughasnails", "cold_biomes"));
	private static final float EPSILON = 1.0e-4f;

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void everyVanillaOverworldBiomeHasAWinterProfile(TestContext ctx) {
		List<String> missing = new ArrayList<>();
		for (RegistryEntry<Biome> entry : biomes(ctx).iterateEntries(BiomeTags.IS_OVERWORLD)) {
			RegistryKey<Biome> key = entry.getKey().orElseThrow();
			if (key.getValue().getNamespace().equals("minecraft")
					&& !WinterClimate.TABLE.containsKey(key) && !WinterClimate.UNCHANGED.contains(key)) {
				missing.add(key.getValue().getPath());
			}
		}
		check(missing.isEmpty(), "no winter profile for: " + missing);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void everyWinterBiomeIsBelowFreezingAndSnows(TestContext ctx) {
		List<String> problems = new ArrayList<>();
		for (Map.Entry<RegistryKey<Biome>, WinterProfile> row : WinterClimate.TABLE.entrySet()) {
			String name = row.getKey().getValue().getPath();
			WinterProfile profile = row.getValue();
			RegistryEntry<Biome> entry = biomes(ctx).entryOf(row.getKey());
			Biome biome = entry.value();

			if (Math.abs(biome.getTemperature() - profile.vanillaTemperature()) > EPSILON) {
				problems.add(name + " temperature " + biome.getTemperature() + " != " + profile.vanillaTemperature());
			}
			if (biome.getTemperature() >= 0.15f) {
				problems.add(name + " is above the snow line");
			}
			if (Math.abs(FrostboundClimate.getCelsius(entry) - profile.celsius()) > 0.01f) {
				problems.add(name + " °C " + FrostboundClimate.getCelsius(entry) + " != " + profile.celsius());
			}
			// Ice-floe biomes use vanilla's frozen-ocean noise: roughly half the surface is open
			// water (and rains), like vanilla frozen oceans. So only require a good share to snow.
			int snowing = 0, samples = 0;
			for (int x = 0; x < 5; x++) {
				for (int z = 0; z < 5; z++) {
					samples++;
					if (biome.getPrecipitation(new BlockPos(x * 97, 63, z * 89)) == Biome.Precipitation.SNOW) {
						snowing++;
					}
				}
			}
			boolean ok = profile.water() == WaterMode.FLOES ? snowing * 4 >= samples : snowing == samples;
			if (!ok) {
				problems.add(name + " snows at only " + snowing + "/" + samples + " sample points");
			}
		}
		check(problems.isEmpty(), String.join("; ", problems));
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void waterFreezesExceptInOpenWaterBiomes(TestContext ctx) {
		BlockPos rel = new BlockPos(1, 1, 1);
		ctx.setBlockState(rel, Blocks.WATER);
		BlockPos water = ctx.getAbsolutePos(rel);

		check(canSetIce(ctx, BiomeKeys.FROZEN_RIVER, water), "frozen river water should freeze");
		check(canSetIce(ctx, BiomeKeys.PLAINS, water), "plains water should freeze");
		check(canSetIce(ctx, BiomeKeys.DESERT, water), "desert water should freeze");
		check(!canSetIce(ctx, BiomeKeys.WARM_OCEAN, water), "warm ocean water should stay open");

		for (Map.Entry<RegistryKey<Biome>, WinterProfile> row : WinterClimate.TABLE.entrySet()) {
			boolean tagged = biomes(ctx).entryOf(row.getKey()).isIn(WinterEnvironment.OPEN_WATER);
			check(tagged == (row.getValue().water() == WaterMode.OPEN),
					row.getKey().getValue().getPath() + ": open_water tag doesn't match the table");
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void netherAndCavesAreUntouched(TestContext ctx) {
		Biome nether = biomes(ctx).entryOf(BiomeKeys.NETHER_WASTES).value();
		check(Math.abs(nether.getTemperature() - 2.0f) < EPSILON, "nether wastes temperature changed: " + nether.getTemperature());
		check(!nether.hasPrecipitation(), "nether should have no precipitation");

		Biome lush = biomes(ctx).entryOf(BiomeKeys.LUSH_CAVES).value();
		check(Math.abs(lush.getTemperature() - 0.5f) < EPSILON, "lush caves temperature changed: " + lush.getTemperature());
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void temperatureModTagsAreFilled(TestContext ctx) {
		for (RegistryKey<Biome> key : List.of(BiomeKeys.PLAINS, BiomeKeys.DESERT, BiomeKeys.JUNGLE, BiomeKeys.SNOWY_PLAINS)) {
			check(biomes(ctx).entryOf(key).isIn(TAN_COLD), key.getValue().getPath() + " should be in toughasnails:cold_biomes");
		}
		check(!biomes(ctx).entryOf(BiomeKeys.NETHER_WASTES).isIn(TAN_COLD), "nether must not be in toughasnails:cold_biomes");

		RegistryEntry<Biome> plains = biomes(ctx).entryOf(BiomeKeys.PLAINS);
		check(plains.isIn(BiomeTags.SPAWNS_SNOW_FOXES), "plains should spawn snow foxes");
		check(plains.isIn(BiomeTags.SPAWNS_WHITE_RABBITS), "plains should spawn white rabbits");
		check(plains.isIn(BiomeTags.SPAWNS_COLD_VARIANT_FROGS), "plains should spawn cold frogs");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void winterColoursAndStraysApplied(TestContext ctx) {
		Biome plains = biomes(ctx).entryOf(BiomeKeys.PLAINS).value();
		check(plains.getEffects().getGrassColor().orElse(-1) == WinterClimate.Zone.TEMPERATE.grass, "plains grass colour");
		check(plains.getSkyColor() == WinterClimate.Zone.TEMPERATE.sky, "plains sky colour");

		check(hasStrays(ctx, BiomeKeys.PLAINS), "strays should spawn in plains");
		check(hasStrays(ctx, BiomeKeys.DESERT), "strays should spawn in desert");
		check(!hasStrays(ctx, BiomeKeys.MUSHROOM_FIELDS), "mushroom fields must stay monster-free");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void climateHelpers(TestContext ctx) {
		check(FrostboundClimate.isArctic(biomes(ctx).entryOf(BiomeKeys.SNOWY_PLAINS)), "snowy plains should be arctic");
		check(!FrostboundClimate.isArctic(biomes(ctx).entryOf(BiomeKeys.PLAINS)), "plains should not be arctic");

		WinterProfile warmModded = WinterClimate.forModdedBiome(2.0f, false);
		check(Math.abs(warmModded.celsius() - -1f) < 0.01f, "hot modded biome -> -1 °C, got " + warmModded.celsius());
		WinterProfile temperateModded = WinterClimate.forModdedBiome(0.8f, true);
		check(Math.abs(temperateModded.celsius() - -8f) < 0.01f, "temperate modded biome -> -8 °C, got " + temperateModded.celsius());
		check(temperateModded.water() == WaterMode.FLOES, "modded oceans get ice floes");
		check(WinterClimate.forModdedBiome(-2f, false).celsius() == -30f, "very cold modded biome clamps to -30 °C");
		ctx.complete();
	}

	// ---- helpers ----

	private static Registry<Biome> biomes(TestContext ctx) {
		return ctx.getWorld().getRegistryManager().get(RegistryKeys.BIOME);
	}

	private static boolean canSetIce(TestContext ctx, RegistryKey<Biome> key, BlockPos waterPos) {
		return biomes(ctx).entryOf(key).value().canSetIce(ctx.getWorld(), waterPos, false);
	}

	private static boolean hasStrays(TestContext ctx, RegistryKey<Biome> key) {
		return biomes(ctx).entryOf(key).value().getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries()
				.stream().anyMatch(e -> e.type == EntityType.STRAY);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}

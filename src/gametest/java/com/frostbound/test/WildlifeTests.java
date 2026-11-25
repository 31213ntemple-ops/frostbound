package com.frostbound.test;

import com.frostbound.environment.GoatGrudges;
import com.frostbound.environment.WinterBiomePool;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.GoatEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.SpawnSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Which animals spawn where in the winter Overworld (environment/WinterWildlife), and goats:
 * mutton, and ramming whatever hits them (environment/GoatGrudges).
 */
public class WildlifeTests implements FabricGameTest {
	private static final int START = 10;

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void horsesOnlySpawnInSparseForest(TestContext ctx) {
		List<String> elsewhere = new ArrayList<>();
		for (RegistryEntry.Reference<Biome> entry : biomes(ctx).streamEntries().toList()) {
			RegistryKey<Biome> key = entry.registryKey();
			if (!key.equals(WinterBiomePool.SPARSE_FOREST)
					&& (spawns(entry.value(), EntityType.HORSE) || spawns(entry.value(), EntityType.DONKEY))) {
				elsewhere.add(key.getValue().toString());
			}
		}
		check(elsewhere.isEmpty(), "horses or donkeys still spawn in " + elsewhere);
		Biome sparse = biome(ctx, WinterBiomePool.SPARSE_FOREST);
		check(spawns(sparse, EntityType.HORSE) && spawns(sparse, EntityType.DONKEY), "Sparse Forest keeps its horses and donkeys");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void forestsHaveFoodAnimals(TestContext ctx) {
		for (RegistryKey<Biome> key : List.of(BiomeKeys.TAIGA, BiomeKeys.SNOWY_TAIGA, BiomeKeys.OLD_GROWTH_PINE_TAIGA,
				BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA, BiomeKeys.WINDSWEPT_FOREST, WinterBiomePool.SPARSE_FOREST)) {
			Biome biome = biome(ctx, key);
			for (EntityType<?> type : List.of(EntityType.SHEEP, EntityType.COW, EntityType.CHICKEN)) {
				check(spawns(biome, type), key.getValue() + " should spawn " + EntityType.getId(type));
			}
			check(!spawns(biome, EntityType.GOAT), key.getValue() + " is a forest: no goats");
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void harshBiomesHaveGoats(TestContext ctx) {
		for (RegistryKey<Biome> key : List.of(BiomeKeys.SNOWY_PLAINS, BiomeKeys.SNOWY_SLOPES, BiomeKeys.FROZEN_PEAKS,
				BiomeKeys.JAGGED_PEAKS, BiomeKeys.GROVE, BiomeKeys.WINDSWEPT_HILLS, BiomeKeys.WINDSWEPT_GRAVELLY_HILLS)) {
			check(spawns(biome(ctx, key), EntityType.GOAT), key.getValue() + " should spawn goats");
		}

		for (RegistryKey<Biome> key : List.of(BiomeKeys.WINDSWEPT_HILLS, BiomeKeys.WINDSWEPT_GRAVELLY_HILLS)) {
			Biome biome = biome(ctx, key);
			for (EntityType<?> type : List.of(EntityType.SHEEP, EntityType.COW, EntityType.CHICKEN, EntityType.PIG)) {
				check(!spawns(biome, type), key.getValue() + " shouldn't spawn " + EntityType.getId(type) + " any more");
			}
			check(spawns(biome, EntityType.LLAMA), key.getValue() + " keeps its llamas");
		}

		Biome grove = biome(ctx, BiomeKeys.GROVE);
		check(spawns(grove, EntityType.SHEEP), "Grove gets sheep");
		for (EntityType<?> type : List.of(EntityType.COW, EntityType.CHICKEN, EntityType.PIG)) {
			check(!spawns(grove, type), "Grove's only food animals are sheep and goats, not " + EntityType.getId(type));
		}
		for (EntityType<?> type : List.of(EntityType.WOLF, EntityType.FOX, EntityType.RABBIT)) {
			check(spawns(grove, type), "Grove keeps its " + EntityType.getId(type));
		}

		Biome snowyPlains = biome(ctx, BiomeKeys.SNOWY_PLAINS);
		check(spawns(snowyPlains, EntityType.RABBIT) && spawns(snowyPlains, EntityType.POLAR_BEAR),
				"Snowy Plains keeps its rabbits and polar bears");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void iceSpikesStayDesolate(TestContext ctx) {
		// Only vanilla's animals: other mods may add their own here (DecorationX adds snowmen).
		Set<EntityType<?>> creatures = new java.util.HashSet<>();
		for (SpawnSettings.SpawnEntry entry : biome(ctx, BiomeKeys.ICE_SPIKES).getSpawnSettings().getSpawnEntries(SpawnGroup.CREATURE).getEntries()) {
			if (EntityType.getId(entry.type).getNamespace().equals("minecraft")) {
				creatures.add(entry.type);
			}
		}
		check(creatures.equals(Set.of(EntityType.RABBIT, EntityType.POLAR_BEAR)),
				"Ice Spikes should only have vanilla's rabbits and polar bears, has " + creatures);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void goatsDropMutton(TestContext ctx) {
		GoatEntity raw = spawnGoat(ctx, ctx.getAbsolutePos(new BlockPos(0, 2, 0)));
		GoatEntity cooked = spawnGoat(ctx, ctx.getAbsolutePos(new BlockPos(0, 2, 0)).add(6, 0, 0));
		cooked.setOnFireFor(5.0f);
		ServerWorld world = ctx.getWorld();
		raw.damage(world.getDamageSources().generic(), 1000.0f);
		cooked.damage(world.getDamageSources().generic(), 1000.0f);
		ctx.waitAndRun(2, () -> {
			check(dropped(world, raw.getBlockPos(), Items.MUTTON) >= 1, "a goat drops mutton");
			check(dropped(world, cooked.getBlockPos(), Items.COOKED_MUTTON) >= 1, "a goat that dies burning drops cooked mutton");
			ctx.complete();
		});
	}

	/** A pig that can't move hits a goat; the goat backs off, lowers its head and rams it. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
	public void goatsRamWhatHitsThem(TestContext ctx) {
		BlockPos floor = WolfDenTests.arena(ctx, -1700, 10);
		ServerWorld world = ctx.getWorld();
		GoatEntity[] goat = new GoatEntity[1];
		PigEntity[] pig = new PigEntity[1];
		boolean[] backedOff = new boolean[1];
		ctx.runAtTick(START, () -> {
			goat[0] = spawnGoat(ctx, floor.up());
			pig[0] = EntityType.PIG.create(world);
			pig[0].setAiDisabled(true);
			pig[0].setPersistent();
			pig[0].refreshPositionAndAngles(floor.getX() + 2.5, floor.getY() + 1, floor.getZ() + 0.5, 0.0f, 0.0f);
			world.spawnEntity(pig[0]);
		});
		ctx.runAtTick(START + 5, () -> {
			goat[0].damage(world.getDamageSources().mobAttack(pig[0]), 1.0f);
			check(GoatGrudges.target(goat[0]) == pig[0], "the goat should hold a grudge against the pig");
		});
		ctx.runAtEveryTick(() -> {
			if (ctx.getTick() <= START + 5 || goat[0] == null) {
				return;
			}
			check(!goat[0].getBrain().hasMemoryModule(MemoryModuleType.IS_PANICKING), "a goat with a grudge doesn't panic");
			if (goat[0].squaredDistanceTo(pig[0]) >= 3.5 * 3.5) {
				backedOff[0] = true;
			}
			if (pig[0].getHealth() < pig[0].getMaxHealth()) {
				check(backedOff[0], "the goat should back off before it charges");
				ctx.complete();
			}
		});
	}

	// --- Helpers ---

	private static GoatEntity spawnGoat(TestContext ctx, BlockPos pos) {
		GoatEntity goat = EntityType.GOAT.create(ctx.getWorld());
		goat.setPersistent();
		goat.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
		ctx.getWorld().spawnEntity(goat);
		return goat;
	}

	private static int dropped(ServerWorld world, BlockPos pos, Item item) {
		return world.getEntitiesByClass(ItemEntity.class, new Box(pos).expand(2.0), e -> e.getStack().isOf(item))
				.stream().mapToInt(e -> e.getStack().getCount()).sum();
	}

	private static Registry<Biome> biomes(TestContext ctx) {
		return ctx.getWorld().getRegistryManager().get(RegistryKeys.BIOME);
	}

	private static Biome biome(TestContext ctx, RegistryKey<Biome> key) {
		return biomes(ctx).getOrThrow(key);
	}

	private static boolean spawns(Biome biome, EntityType<?> type) {
		return biome.getSpawnSettings().getSpawnEntries(SpawnGroup.CREATURE).getEntries().stream().anyMatch(e -> e.type == type);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}

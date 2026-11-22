package com.frostbound.test;

import com.frostbound.environment.SnowyPlants;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowyBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.List;
import java.util.Optional;

/**
 * Snow settling into grass, flowers and other low plants ("snowy" plants).
 *
 * Snow only lands on the highest exposed block, but game test areas are boxed in.
 * So the tests that make it snow build on top of the column ({@link #skyGround}) and
 * use their own batches, so the weather and the 16x16 freeze feature can't disturb
 * other tests.
 */
public class SnowyPlantTests implements FabricGameTest {
	private static final BlockPos GROUND = new BlockPos(1, 1, 1);
	private static final BlockPos PLANT = GROUND.up();

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void plantsHaveSnowyStateDefaultingToFalse(TestContext ctx) {
		if (skipWithSnowRealMagic(ctx)) return;
		for (Block block : List.of(Blocks.SHORT_GRASS, Blocks.FERN, Blocks.POPPY, Blocks.DANDELION, Blocks.WITHER_ROSE,
				Blocks.DEAD_BUSH, Blocks.OAK_SAPLING, Blocks.BROWN_MUSHROOM, Blocks.SWEET_BERRY_BUSH, Blocks.PINK_PETALS,
				Blocks.TALL_GRASS, Blocks.SUNFLOWER, Blocks.LARGE_FERN)) {
			BlockState state = block.getDefaultState();
			check(state.contains(SnowyPlants.SNOWY), name(block) + " should have the snowy property");
			check(!state.get(SnowyPlants.SNOWY), name(block) + " should not be snowy by default");
		}
		for (Block block : List.of(Blocks.WHEAT, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS, Blocks.SMALL_DRIPLEAF,
				Blocks.MANGROVE_PROPAGULE, Blocks.PITCHER_CROP, Blocks.SUGAR_CANE, Blocks.STONE)) {
			check(!block.getDefaultState().contains(SnowyPlants.SNOWY), name(block) + " should NOT have the snowy property");
		}
		// The sapling's own default (stage 0) must survive our default fix.
		check(Blocks.OAK_SAPLING.getDefaultState().get(Properties.STAGE) == 0, "sapling default stage changed");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void snowCanSettleIntoPlants(TestContext ctx) {
		if (skipWithSnowRealMagic(ctx)) return;
		ctx.setBlockState(GROUND, Blocks.GRASS_BLOCK);
		ctx.setBlockState(PLANT, Blocks.POPPY);
		Biome plains = ctx.getWorld().getRegistryManager().get(RegistryKeys.BIOME).entryOf(BiomeKeys.PLAINS).value();
		BlockPos plant = ctx.getAbsolutePos(PLANT);

		check(plains.canSetSnow(ctx.getWorld(), plant), "snow should be able to settle into a poppy");
		ctx.setBlockState(PLANT, Blocks.POPPY.getDefaultState().with(SnowyPlants.SNOWY, true));
		check(!plains.canSetSnow(ctx.getWorld(), plant), "an already snowy poppy can't take more snow");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "snowfall")
	public void snowfallMakesPlantsSnowy(TestContext ctx) {
		if (skipWithSnowRealMagic(ctx)) return;
		ServerWorld world = ctx.getWorld();
		BlockPos ground = skyGround(ctx, GROUND);
		world.setBlockState(ground, Blocks.GRASS_BLOCK.getDefaultState());
		world.setBlockState(ground.up(), Blocks.SHORT_GRASS.getDefaultState());

		BlockPos tallGround = skyGround(ctx, GROUND.east(2));
		world.setBlockState(tallGround, Blocks.GRASS_BLOCK.getDefaultState());
		TallPlantBlock.placeAt(world, Blocks.TALL_GRASS.getDefaultState(), tallGround.up(), 3);

		// isRaining() looks at the rain strength, which normally fades in over a few seconds.
		world.setWeather(0, 6000, true, false);
		world.setRainGradient(1.0f);
		try {
			world.tickIceAndSnow(ground.up());
			world.tickIceAndSnow(tallGround.up());
		} finally {
			world.setWeather(6000, 0, false, false);
			world.setRainGradient(0.0f);
		}

		BlockState grass = world.getBlockState(ground.up());
		check(grass.isOf(Blocks.SHORT_GRASS), "short grass should still be there, got " + grass);
		check(grass.get(SnowyPlants.SNOWY), "short grass should be snowy after snowfall");
		check(world.getBlockState(ground).get(SnowyBlock.SNOWY), "grass block under a snowy plant should turn snowy");

		BlockState lower = world.getBlockState(tallGround.up());
		BlockState upper = world.getBlockState(tallGround.up(2));
		check(lower.isOf(Blocks.TALL_GRASS) && lower.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER,
				"tall grass bottom should still be there, got " + lower);
		check(lower.get(SnowyPlants.SNOWY), "tall grass bottom half should be snowy");
		check(upper.isOf(Blocks.TALL_GRASS) && !upper.get(SnowyPlants.SNOWY), "tall grass top half should stay, not snowy");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "snow_worldgen")
	public void worldgenSnowLayerMakesPlantsSnowy(TestContext ctx) {
		if (skipWithSnowRealMagic(ctx)) return;
		ServerWorld world = ctx.getWorld();
		BlockPos ground = skyGround(ctx, GROUND);
		world.setBlockState(ground, Blocks.GRASS_BLOCK.getDefaultState());
		world.setBlockState(ground.up(), Blocks.DANDELION.getDefaultState());
		BlockPos bareGround = skyGround(ctx, GROUND.east(2));
		world.setBlockState(bareGround, Blocks.GRASS_BLOCK.getDefaultState());

		Feature.FREEZE_TOP_LAYER.generate(new FeatureContext<>(Optional.empty(), world,
				world.getChunkManager().getChunkGenerator(), world.getRandom(), ground, FeatureConfig.DEFAULT));

		BlockState flower = world.getBlockState(ground.up());
		check(flower.isOf(Blocks.DANDELION), "dandelion should still be there, got " + flower);
		check(flower.get(SnowyPlants.SNOWY), "dandelion should be snowy after world generation");
		check(world.getBlockState(bareGround.up()).isOf(Blocks.SNOW), "bare ground should get a normal snow layer");
		ctx.complete();
	}

	/** The first open-sky block above the test area's column: build "ground" here. */
	private static BlockPos skyGround(TestContext ctx, BlockPos rel) {
		return ctx.getWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING, ctx.getAbsolutePos(rel));
	}

	private static String name(Block block) {
		return block.getTranslationKey();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}

	/** Snow! Real Magic handles snow in plants when it's installed, and this feature turns itself off. */
	private static boolean skipWithSnowRealMagic(TestContext ctx) {
		if (SnowyPlants.ENABLED) {
			return false;
		}
		ctx.complete();
		return true;
	}
}

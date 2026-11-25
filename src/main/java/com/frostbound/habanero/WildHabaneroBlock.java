package com.frostbound.habanero;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * A wild habanero bush, found in the coldest biomes. Grows on what those biomes are made of (block tag
 * {@code frostbound:wild_habanero_grows_on}: dirt, stone, gravel, snow blocks...). Drops peppers and seeds.
 */
public class WildHabaneroBlock extends PlantBlock {
	public static final MapCodec<WildHabaneroBlock> CODEC = createCodec(WildHabaneroBlock::new);
	private static final VoxelShape SHAPE = createCuboidShape(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

	public WildHabaneroBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends PlantBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
		return floor.isIn(Habaneros.WILD_HABANERO_GROWS_ON);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}
}

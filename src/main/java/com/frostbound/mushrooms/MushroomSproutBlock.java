package com.frostbound.mushrooms;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * A small mushroom that grows on a giant cap while you mine it: vanilla's red or brown mushroom
 * ({@link #BROWN}, matching the cap), drawn with vanilla's own models. Breaks in one hit; left alone for
 * {@code giantMushrooms.sproutFuseTicks} it bursts ({@link MushroomSprouts#burst}). The countdown over it is
 * drawn by client.mushrooms.SproutCountdownRenderer from its block entity. If the cap block under it
 * goes, it goes too, quietly.
 */
public class MushroomSproutBlock extends Block implements BlockEntityProvider {
	public static final MapCodec<MushroomSproutBlock> CODEC = createCodec(MushroomSproutBlock::new);
	public static final BooleanProperty BROWN = BooleanProperty.of("brown");
	/** Vanilla mushroom's outline. */
	private static final VoxelShape SHAPE = createCuboidShape(5.0, 0.0, 5.0, 11.0, 6.0, 11.0);

	public MushroomSproutBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(BROWN, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(BROWN);
	}

	@Override
	public MapCodec<? extends MushroomSproutBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		return world.getBlockState(pos.down()).getBlock() instanceof GiantCapBlock;
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
			WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (direction == Direction.DOWN && !canPlaceAt(state, world, pos)) {
			return net.minecraft.block.Blocks.AIR.getDefaultState();
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new MushroomSproutBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient || type != GiantMushrooms.SPROUT_ENTITY) {
			return null;
		}
		return (w, pos, s, be) -> MushroomSprouts.sproutTick((ServerWorld) w, pos, (MushroomSproutBlockEntity) be);
	}
}

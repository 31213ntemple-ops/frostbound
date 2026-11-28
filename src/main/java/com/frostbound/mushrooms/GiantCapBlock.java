package com.frostbound.mushrooms;

import com.frostbound.config.FrostboundConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LandingBlock;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.world.WorldEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * A block of a giant mushroom's cap. Lets skylight straight through ({@link #isTransparent}; it's
 * registered non-opaque), so the stem under a big cap isn't left in the dark, while still looking solid:
 * faces between cap blocks are hidden ({@link #isSideInvisible}).
 *
 * Mined with a hoe (mineable/hoe), and never in one hit: no tool,
 * enchantment or effect gets it under {@code giantMushrooms.capMinBreakTicks}. While you mine a cap,
 * sprouts grow on it ({@link GiantMushrooms}); when nearly all of it is gone, the rest falls apart.
 *
 * {@link #REGROWN}: grown back by a sprout's burst. Blighted (a discoloured texture), and worth nothing:
 * no drops (its loot table checks the property) and no spores ({@link Spores#yields}).
 */
public class GiantCapBlock extends Block implements LandingBlock {
	public static final MapCodec<GiantCapBlock> CODEC = createCodec(GiantCapBlock::new);
	public static final BooleanProperty REGROWN = BooleanProperty.of("regrown");

	public GiantCapBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.getDefaultState().with(REGROWN, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(REGROWN);
	}

	@Override
	public MapCodec<? extends GiantCapBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
		return Math.min(super.calcBlockBreakingDelta(state, player, world, pos),
				MushroomMining.maxDelta(FrostboundConfig.get().giantMushrooms.capMinBreakTicks));
	}

	/** A cap that fell apart ({@link GiantMushrooms#collapse}): each falling block crumbles where it lands. */
	@Override
	public void onDestroyedOnLanding(World world, BlockPos pos, FallingBlockEntity falling) {
		world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(falling.getBlockState()));
	}

	@Override
	protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
		return true;
	}

	@Override
	protected boolean isSideInvisible(BlockState state, BlockState stateFrom, Direction direction) {
		return stateFrom.getBlock() instanceof GiantCapBlock || super.isSideInvisible(state, stateFrom, direction);
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!newState.isOf(state.getBlock()) && world instanceof ServerWorld serverWorld) {
			GiantMushrooms.blockChanged(serverWorld, pos);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}

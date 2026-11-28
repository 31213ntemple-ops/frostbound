package com.frostbound.mushrooms;

import com.frostbound.config.FrostboundConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * A block of a giant mushroom's stem. Mined with an axe (mineable/axe), from the top down: only the
 * {@link #ACTIVE} layer mines at a normal pace; every other layer, and the whole stem while any of the
 * cap is left, mines at {@code giantMushrooms.inactiveLayerSpeed} of it.
 *
 * The server decides which layer is active and sets the property ({@link GiantMushrooms#refresh}), so
 * the client, which works out its own mining progress, always agrees with it, and the active layer can
 * look different.
 */
public class GiantStemBlock extends Block {
	public static final MapCodec<GiantStemBlock> CODEC = createCodec(GiantStemBlock::new);
	public static final BooleanProperty ACTIVE = BooleanProperty.of("active");

	public GiantStemBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(ACTIVE, false));
	}

	@Override
	public MapCodec<? extends GiantStemBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE);
	}

	@Override
	protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
		FrostboundConfig.GiantMushroomOptions options = FrostboundConfig.get().giantMushrooms;
		float delta = super.calcBlockBreakingDelta(state, player, world, pos);
		if (!state.get(ACTIVE)) {
			delta *= (float) options.inactiveLayerSpeed;
		}
		return Math.min(delta, GiantMushrooms.maxDelta(options.stemMinBreakTicks));
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!newState.isOf(state.getBlock()) && world instanceof ServerWorld serverWorld) {
			GiantMushrooms.blockChanged(serverWorld, pos);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}

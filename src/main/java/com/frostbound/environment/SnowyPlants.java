package com.frostbound.environment;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DeadBushBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.FlowerbedBlock;
import net.minecraft.block.MushroomPlantBlock;
import net.minecraft.block.PitcherCropBlock;
import net.minecraft.block.PropaguleBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.ShortPlantBlock;
import net.minecraft.block.SmallDripleafBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.TallSeagrassBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;

/**
 * "Snowlogged" plants: low plants get a {@code snowy} block state, so snow can settle
 * in the same block as grass and flowers instead of being blocked by them.
 *
 *  - The property is added to the blocks in mixin.environment.BlockMixin. It's added
 *    no matter what the config says, so client and server always agree on block states.
 *  - Snow settles into plants via BiomeMixin (canSetSnow), FreezeTopLayerFeatureMixin
 *    (world generation) and ServerWorldMixin (snowfall).
 *  - The client draws a thin snow layer around snowy plants (client.SnowyPlantModel).
 *
 * Modded plants that extend these vanilla classes get it automatically.
 *
 * Off when Snow! Real Magic is installed: it handles snow in plants (and much more), so
 * mixin.FrostboundMixinPlugin leaves all of this out and plants get no snowy property.
 */
public final class SnowyPlants {
	/** Separate from grass blocks' Properties.SNOWY so code that checks that one isn't confused. */
	public static final BooleanProperty SNOWY = BooleanProperty.of("snowy");

	/** False when Snow! Real Magic handles snow in plants instead. */
	public static final boolean ENABLED = !FabricLoader.getInstance().isModLoaded("snowrealmagic");

	private SnowyPlants() {}

	/** Whether this block gets the snowy property. Called while blocks are being constructed. */
	public static boolean isSnowloggable(Block block) {
		if (block instanceof TallSeagrassBlock || block instanceof SmallDripleafBlock
				|| block instanceof PitcherCropBlock || block instanceof PropaguleBlock) {
			return false; // underwater, waterloggable or crop plants
		}
		return block instanceof ShortPlantBlock      // short grass, fern
				|| block instanceof FlowerBlock      // all small flowers
				|| block instanceof FlowerbedBlock   // pink petals
				|| block instanceof DeadBushBlock
				|| block instanceof SaplingBlock
				|| block instanceof MushroomPlantBlock
				|| block instanceof SweetBerryBushBlock
				|| block instanceof TallPlantBlock;  // tall grass, large fern, tall flowers (bottom half only)
	}

	/** A plant that snow could settle into right now. Tall plants only take snow in their bottom half. */
	public static boolean canBecomeSnowy(BlockState state) {
		return state.contains(SNOWY) && !state.get(SNOWY)
				&& (!state.contains(Properties.DOUBLE_BLOCK_HALF)
						|| state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER);
	}

	public static boolean isSnowy(BlockState state) {
		return state.contains(SNOWY) && state.get(SNOWY);
	}

	/**
	 * Used where the game is about to place a snow layer: if a plant is already there,
	 * return the snowy plant instead, otherwise the snow layer unchanged.
	 */
	public static BlockState snowInto(BlockState existing, BlockState placing) {
		if (placing.isOf(Blocks.SNOW) && canBecomeSnowy(existing)) {
			return existing.with(SNOWY, true);
		}
		return placing;
	}
}

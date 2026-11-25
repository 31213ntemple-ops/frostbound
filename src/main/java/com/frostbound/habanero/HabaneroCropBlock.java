package com.frostbound.habanero;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.CropBlock;
import net.minecraft.item.ItemConvertible;

/** Habaneros on farmland: grows like wheat (ages 0–7, drawn in four stages), planted from habanero seeds. */
public class HabaneroCropBlock extends CropBlock {
	public static final MapCodec<HabaneroCropBlock> CODEC = createCodec(HabaneroCropBlock::new);

	public HabaneroCropBlock(Settings settings) {
		super(settings);
	}

	@Override
	public MapCodec<? extends CropBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected ItemConvertible getSeedsItem() {
		return Habaneros.HABANERO_SEEDS;
	}
}

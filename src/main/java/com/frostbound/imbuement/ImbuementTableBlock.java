package com.frostbound.imbuement;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The imbuement table: right-click to open its grid ({@link ImbuementScreenHandler}). Keeps nothing inside, like a crafting table. */
public class ImbuementTableBlock extends CraftingTableBlock {
	public static final MapCodec<ImbuementTableBlock> CODEC = createCodec(ImbuementTableBlock::new);
	public static final Text TITLE = Text.translatable("container.frostbound.imbuement_table");

	public ImbuementTableBlock(Settings settings) {
		super(settings);
	}

	@Override
	public MapCodec<? extends CraftingTableBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
		return new SimpleNamedScreenHandlerFactory(
				(syncId, inventory, player) -> new ImbuementScreenHandler(syncId, inventory, ScreenHandlerContext.create(world, pos)), TITLE);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		player.openHandledScreen(state.createScreenHandlerFactory(world, pos));
		return ActionResult.CONSUME;
	}
}

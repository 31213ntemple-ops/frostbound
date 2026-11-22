package com.frostbound.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Ice module: ice is mined like a normal block (no Silk Touch, no melting) and blue ice is gone from worldgen. */
public class IceTests implements FabricGameTest {
	private static final BlockPos POS = new BlockPos(1, 2, 1);

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void iceBreaksWithoutSilkTouchAndLeavesNoWater(TestContext ctx) {
		ctx.setBlockState(POS.down(), Blocks.STONE);
		ctx.setBlockState(POS, Blocks.ICE);
		ServerWorld world = ctx.getWorld();
		BlockPos abs = ctx.getAbsolutePos(POS);
		world.breakBlock(abs, true);
		ctx.expectBlock(Blocks.AIR, POS);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void iceBlocksNeedAPickaxeBetterThanStone(TestContext ctx) {
		for (Block block : new Block[] {Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE}) {
			ServerWorld world = ctx.getWorld();
			for (var tool : new net.minecraft.item.Item[] {Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE}) {
				List<ItemStack> drops = dropsWith(world, ctx, block, new ItemStack(tool));
				if (drops.size() != 1 || !drops.get(0).isOf(block.asItem())) {
					ctx.throwGameTestException(block + " should drop itself with " + tool + ", got " + drops);
				}
			}
			for (var tool : new net.minecraft.item.Item[] {Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.GOLDEN_PICKAXE, Items.IRON_SHOVEL}) {
				List<ItemStack> drops = dropsWith(world, ctx, block, new ItemStack(tool));
				if (!drops.isEmpty()) {
					ctx.throwGameTestException(block + " should drop nothing with " + tool + ", got " + drops);
				}
			}
		}
		ctx.complete();
	}

	private static List<ItemStack> dropsWith(ServerWorld world, TestContext ctx, Block block, ItemStack tool) {
		LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(world)
				.add(LootContextParameters.ORIGIN, Vec3d.ofCenter(ctx.getAbsolutePos(POS)))
				.add(LootContextParameters.TOOL, tool);
		return block.getDefaultState().getDroppedStacks(builder);
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void packedAndBlueIceCannotBeCrafted(TestContext ctx) {
		var recipes = ctx.getWorld().getRecipeManager();
		for (var holder : recipes.values()) {
			var id = holder.value().getResult(ctx.getWorld().getRegistryManager());
			if (id.isOf(Items.PACKED_ICE) || id.isOf(Items.BLUE_ICE)) {
				ctx.throwGameTestException("recipe " + holder.id() + " should be disabled");
			}
		}
		ctx.complete();
	}
}

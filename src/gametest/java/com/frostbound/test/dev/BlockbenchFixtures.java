package com.frostbound.test.dev;

import com.frostbound.Frostbound;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Dev only: a block and an item for the Blockbench test fixtures (src/gametest/blockbench), so their
 * imported models load in the game and can be looked at in runScenes. Never shipped.
 */
public class BlockbenchFixtures implements ModInitializer {
	public static Block LANTERN;
	public static Item WAND;

	@Override
	public void onInitialize() {
		LANTERN = Registry.register(Registries.BLOCK, Frostbound.id("bb_test_lantern"),
				new Block(AbstractBlock.Settings.copy(Blocks.GLASS).nonOpaque()));
		Registry.register(Registries.ITEM, Frostbound.id("bb_test_lantern"), new BlockItem(LANTERN, new Item.Settings()));
		WAND = Registry.register(Registries.ITEM, Frostbound.id("bb_test_wand"), new Item(new Item.Settings()));
	}
}

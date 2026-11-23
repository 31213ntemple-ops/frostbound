package com.frostbound.imbuement;

import com.frostbound.Frostbound;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;

/**
 * The imbuement table and what it does: Cold, Icy and Frozen armour ({@link FrostTiers}) with their
 * material bonuses ({@link FrostBonuses}). Always registered, like the other items.
 */
public final class Imbuement {
	public static final Block TABLE = Registry.register(Registries.BLOCK, Frostbound.id("imbuement_table"),
			new ImbuementTableBlock(AbstractBlock.Settings.copy(Blocks.SMITHING_TABLE)));
	public static final Item TABLE_ITEM = Registry.register(Registries.ITEM, Frostbound.id("imbuement_table"),
			new BlockItem(TABLE, new Item.Settings()));

	public static final ScreenHandlerType<ImbuementScreenHandler> SCREEN_HANDLER = Registry.register(Registries.SCREEN_HANDLER,
			Frostbound.id("imbuement_table"), new ScreenHandlerType<>(ImbuementScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

	private Imbuement() {}

	public static void init() {
		FrostTiers.init();
		FrostBonuses.init();
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries ->
				entries.addAfter(Items.SMITHING_TABLE, TABLE_ITEM));
	}
}

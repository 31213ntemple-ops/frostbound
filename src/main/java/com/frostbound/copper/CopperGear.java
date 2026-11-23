package com.frostbound.copper;

import com.frostbound.Frostbound;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.item.AnimalArmorItem;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvent;

import java.util.EnumMap;
import java.util.List;

/**
 * Copper tools, armour, horse armour and nuggets, backported from Minecraft 1.21.9 ("The Copper Age")
 * with the same stats, recipes and textures. Copper sits between stone and iron: it mines what stone
 * mines, lasts longer and digs faster. Copper tools also have the {@link Conductivity} passive.
 *
 * Always registered (not a module), like the other items, so saved gear never disappears.
 */
public final class CopperGear {
	/** Blocks copper tools can't harvest (as 1.21.9: anything needing iron or diamond). */
	public static final TagKey<Block> INCORRECT_FOR_COPPER_TOOL =
			TagKey.of(RegistryKeys.BLOCK, Frostbound.id("incorrect_for_copper_tool"));
	/** What copper tools are crafted and repaired with (copper ingots). */
	public static final TagKey<Item> COPPER_TOOL_MATERIALS =
			TagKey.of(RegistryKeys.ITEM, Frostbound.id("copper_tool_materials"));

	public static final ToolMaterial TOOL_MATERIAL = new ToolMaterial() {
		@Override public int getDurability() { return 190; }
		@Override public float getMiningSpeedMultiplier() { return 5.0f; }
		@Override public float getAttackDamage() { return 1.0f; }
		@Override public TagKey<Block> getInverseTag() { return INCORRECT_FOR_COPPER_TOOL; }
		@Override public int getEnchantability() { return 13; }
		@Override public Ingredient getRepairIngredient() { return Ingredient.fromTag(COPPER_TOOL_MATERIALS); }
	};

	public static final SoundEvent EQUIP_SOUND = Registry.register(Registries.SOUND_EVENT,
			Frostbound.id("item.armor.equip_copper"), SoundEvent.of(Frostbound.id("item.armor.equip_copper")));

	/** frostbound:copper. Textures: textures/models/armor/copper_layer_{1,2}.png and horse/armor/horse_armor_copper.png. */
	public static final RegistryEntry<ArmorMaterial> ARMOR_MATERIAL = Registry.registerReference(Registries.ARMOR_MATERIAL,
			Frostbound.id("copper"), new ArmorMaterial(
					new EnumMap<>(java.util.Map.of(
							ArmorItem.Type.BOOTS, 1,
							ArmorItem.Type.LEGGINGS, 3,
							ArmorItem.Type.CHESTPLATE, 4,
							ArmorItem.Type.HELMET, 2,
							ArmorItem.Type.BODY, 4)),
					8, Registries.SOUND_EVENT.getEntry(EQUIP_SOUND), () -> Ingredient.ofItems(Items.COPPER_INGOT),
					List.of(new ArmorMaterial.Layer(Frostbound.id("copper"))), 0.0f, 0.0f));

	/** Armour durability multiplier (leather 5, iron 15). */
	private static final int ARMOR_DURABILITY = 11;

	public static final Item COPPER_NUGGET = register("copper_nugget", new Item(new Item.Settings()));

	public static final Item COPPER_SWORD = register("copper_sword", new SwordItem(TOOL_MATERIAL,
			new Item.Settings().attributeModifiers(SwordItem.createAttributeModifiers(TOOL_MATERIAL, 3, -2.4f))));
	public static final Item COPPER_SHOVEL = register("copper_shovel", new ShovelItem(TOOL_MATERIAL,
			new Item.Settings().attributeModifiers(ShovelItem.createAttributeModifiers(TOOL_MATERIAL, 1.5f, -3.0f))));
	public static final Item COPPER_PICKAXE = register("copper_pickaxe", new PickaxeItem(TOOL_MATERIAL,
			new Item.Settings().attributeModifiers(PickaxeItem.createAttributeModifiers(TOOL_MATERIAL, 1.0f, -2.8f))));
	public static final Item COPPER_AXE = register("copper_axe", new AxeItem(TOOL_MATERIAL,
			new Item.Settings().attributeModifiers(AxeItem.createAttributeModifiers(TOOL_MATERIAL, 7.0f, -3.2f))));
	public static final Item COPPER_HOE = register("copper_hoe", new HoeItem(TOOL_MATERIAL,
			new Item.Settings().attributeModifiers(HoeItem.createAttributeModifiers(TOOL_MATERIAL, -1.0f, -2.0f))));

	public static final Item COPPER_HELMET = armor("copper_helmet", ArmorItem.Type.HELMET);
	public static final Item COPPER_CHESTPLATE = armor("copper_chestplate", ArmorItem.Type.CHESTPLATE);
	public static final Item COPPER_LEGGINGS = armor("copper_leggings", ArmorItem.Type.LEGGINGS);
	public static final Item COPPER_BOOTS = armor("copper_boots", ArmorItem.Type.BOOTS);

	public static final Item COPPER_HORSE_ARMOR = register("copper_horse_armor", new AnimalArmorItem(ARMOR_MATERIAL,
			AnimalArmorItem.Type.EQUESTRIAN, false, new Item.Settings().maxCount(1)));

	private CopperGear() {}

	private static Item armor(String name, ArmorItem.Type type) {
		return register(name, new ArmorItem(ARMOR_MATERIAL, type,
				new Item.Settings().maxDamage(type.getMaxDamage(ARMOR_DURABILITY))));
	}

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, Frostbound.id(name), item);
	}

	public static void init() {
		// Creative tabs: where 1.21.9 puts them (copper between stone and iron, armour after leather).
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries ->
				entries.addAfter(Items.STONE_HOE, COPPER_SHOVEL, COPPER_PICKAXE, COPPER_AXE, COPPER_HOE));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.addAfter(Items.STONE_SWORD, COPPER_SWORD);
			entries.addAfter(Items.STONE_AXE, COPPER_AXE);
			entries.addAfter(Items.LEATHER_BOOTS, COPPER_HELMET, COPPER_CHESTPLATE, COPPER_LEGGINGS, COPPER_BOOTS);
			entries.addBefore(Items.IRON_HORSE_ARMOR, COPPER_HORSE_ARMOR);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries ->
				entries.addBefore(Items.IRON_NUGGET, COPPER_NUGGET));

		Conductivity.init();
	}
}

package com.frostbound.nothirst;

import com.frostbound.config.FrostboundConfig;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Module: No Thirst. Frostbound is played with Tough As Nails for its temperature, but not its thirst.
 * When TAN is installed:
 *
 *  - TAN's thirst is switched off, whatever config/toughasnails/thirst.toml says (no thirst bar,
 *    no drinking, no thirst effects; see mixin.nothirst.ThirstConfigMixin).
 *  - Its thirst items ({@link #REMOVED_ITEMS}: canteens, water bottles, juices, the water purifier and
 *    rain collector) can't be crafted or purified (mixin.nothirst.RecipeManagerMixin), aren't in the
 *    creative tabs, and are never traded (mixin.nothirst.MerchantEntityMixin).
 *  - The Water Cleansing enchantment (canteens only) is left out of the loot and trade tags
 *    (mixin.nothirst.TagGroupLoaderMixin), so its books stop turning up.
 *
 * The items stay registered (another mod's items can't be unregistered), so ones already in a
 * world are kept. TAN's temperature items (thermometer, gauge, thermoregulator, wool and leaf
 * armour, ice cream, Charc-Os) are untouched.
 */
public final class NoThirst {
	public static final String TAN = "toughasnails";

	private static final Set<String> CANTEEN_MATERIALS = Set.of("leather", "copper", "iron", "gold", "diamond", "netherite");
	private static final Set<String> JUICES = Set.of("apple", "cactus", "chorus_fruit", "glow_berry", "melon", "pumpkin", "sweet_berry");

	/** TAN's thirst items and blocks. */
	public static final Set<Identifier> REMOVED_ITEMS = Stream.of(
			CANTEEN_MATERIALS.stream().flatMap(m -> Stream.of(
					"empty_" + m + "_canteen", m + "_water_canteen", m + "_dirty_water_canteen", m + "_purified_water_canteen")),
			JUICES.stream().map(j -> j + "_juice"),
			Stream.of("dirty_water_bottle", "purified_water_bottle", "water_purifier", "rain_collector"))
			.flatMap(s -> s)
			.map(path -> Identifier.of(TAN, path))
			.collect(Collectors.toUnmodifiableSet());

	/** The water purifier's recipe type: every one of its recipes goes. */
	public static final Identifier WATER_PURIFYING = Identifier.of(TAN, "water_purifying");
	/** Canteen-only enchantment. */
	public static final Identifier WATER_CLEANSING = Identifier.of(TAN, "water_cleansing");

	private NoThirst() {}

	/** On when the config allows it and Tough As Nails is installed. Safe to call before Minecraft classes load. */
	public static boolean isActive() {
		return FrostboundConfig.get().modules.noThirst && FabricLoader.getInstance().isModLoaded(TAN);
	}

	public static void init() {
		ItemGroupEvents.MODIFY_ENTRIES_ALL.register((group, entries) -> {
			entries.getDisplayStacks().removeIf(NoThirst::isRemoved);
			entries.getSearchTabStacks().removeIf(NoThirst::isRemoved);
		});
	}

	public static boolean isRemoved(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (REMOVED_ITEMS.contains(Registries.ITEM.getId(stack.getItem()))) {
			return true;
		}
		return stack.isOf(Items.ENCHANTED_BOOK) && onlyWaterCleansing(stack.get(DataComponentTypes.STORED_ENCHANTMENTS));
	}

	private static boolean onlyWaterCleansing(ItemEnchantmentsComponent enchantments) {
		return enchantments != null && !enchantments.isEmpty()
				&& enchantments.getEnchantments().stream().allMatch(e -> e.matchesId(WATER_CLEANSING));
	}

	public static boolean isRemoved(Recipe<?> recipe, RegistryWrapper.WrapperLookup registries) {
		if (WATER_PURIFYING.equals(Registries.RECIPE_TYPE.getId(recipe.getType()))) {
			return true;
		}
		ItemStack result;
		try {
			result = recipe.getResult(registries);
		} catch (RuntimeException e) {
			return false; // special recipes that can't say what they make
		}
		return result != null && isRemoved(result);
	}

	/** The offer, or null (no trade) when it buys or sells a thirst item and the module is on. */
	public static TradeOffer filter(TradeOffer offer) {
		return offer != null && isActive() && isRemoved(offer) ? null : offer;
	}

	public static boolean isRemoved(TradeOffer offer) {
		return isRemoved(offer.getSellItem()) || isRemoved(offer.getOriginalFirstBuyItem())
				|| offer.getSecondBuyItem().map(item -> isRemoved(item.itemStack())).orElse(false);
	}
}

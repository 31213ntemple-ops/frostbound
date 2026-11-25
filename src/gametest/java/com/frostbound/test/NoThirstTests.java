package com.frostbound.test;

import com.frostbound.nothirst.NoThirst;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;

import java.util.List;

/**
 * No Thirst module: Tough As Nails' thirst is off and its thirst items can't be had. Tough As Nails
 * is in the dev runs' mods folder (syncDevMods); without it these only check the module stays out.
 */
public class NoThirstTests implements FabricGameTest {
	private static final Identifier APPLE_JUICE = Identifier.of(NoThirst.TAN, "apple_juice");

	private static boolean hasTan(TestContext ctx) {
		if (NoThirst.isActive()) {
			return true;
		}
		ctx.complete();
		return false;
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void thirstIsSwitchedOff(TestContext ctx) throws ReflectiveOperationException {
		if (!hasTan(ctx)) {
			return;
		}
		Object config = Class.forName("toughasnails.init.ModConfig").getField("thirst").get(null);
		check(!config.getClass().getField("enableThirst").getBoolean(config), "TAN thirst should be off");
		check(!config.getClass().getField("enableHandDrinking").getBoolean(config), "TAN hand drinking should be off");
		// TAN re-reads the file when it changes: it must stay off after a reload too.
		config.getClass().getMethod("load").invoke(config);
		check(!config.getClass().getField("enableThirst").getBoolean(config), "TAN thirst should stay off after a reload");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void thirstItemsCannotBeCrafted(TestContext ctx) {
		if (!hasTan(ctx)) {
			return;
		}
		// Every removed id is a real TAN item (catches a rename in a TAN update).
		for (Identifier id : NoThirst.REMOVED_ITEMS) {
			check(Registries.ITEM.containsId(id), "no item " + id + " in Tough As Nails");
		}
		var registries = ctx.getWorld().getRegistryManager();
		var recipes = ctx.getWorld().getRecipeManager();
		for (var entry : recipes.values()) {
			check(!NoThirst.WATER_PURIFYING.equals(Registries.RECIPE_TYPE.getId(entry.value().getType())),
					"water purifier recipe " + entry.id() + " should be gone");
			ItemStack result = entry.value().getResult(registries);
			check(!NoThirst.REMOVED_ITEMS.contains(Registries.ITEM.getId(result.getItem())),
					"recipe " + entry.id() + " makes " + result + ", a thirst item");
		}
		// TAN's temperature items keep their recipes.
		for (String kept : List.of("ice_cream", "charc_os", "thermometer", "temperature_gauge", "thermoregulator", "wool_helmet")) {
			check(recipes.get(Identifier.of(NoThirst.TAN, kept)).isPresent(), "recipe toughasnails:" + kept + " should stay");
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void creativeTabsHideThirstItems(TestContext ctx) {
		if (!hasTan(ctx)) {
			return;
		}
		var registries = ctx.getWorld().getRegistryManager();
		ItemGroups.updateDisplayContext(FeatureFlags.VANILLA_FEATURES, true, registries);
		ItemGroup tan = Registries.ITEM_GROUP.get(Identifier.of(NoThirst.TAN, "main"));
		check(tan != null, "no Tough As Nails creative tab");
		check(tan.getDisplayStacks().stream().anyMatch(s -> s.isOf(Registries.ITEM.get(Identifier.of(NoThirst.TAN, "thermometer")))),
				"the thermometer should stay in TAN's tab");
		for (ItemGroup group : ItemGroups.getGroups()) {
			for (ItemStack stack : group.getDisplayStacks()) {
				check(!NoThirst.isRemoved(stack), stack + " should not be in creative tab " + group.getDisplayName().getString());
			}
			for (ItemStack stack : group.getSearchTabStacks()) {
				check(!NoThirst.isRemoved(stack), stack + " should not be in the search tab");
			}
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void waterCleansingIsOutOfLootAndTrades(TestContext ctx) {
		if (!hasTan(ctx)) {
			return;
		}
		var enchantments = ctx.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		RegistryEntry<Enchantment> cleansing = enchantments.entryOf(RegistryKey.of(RegistryKeys.ENCHANTMENT, NoThirst.WATER_CLEANSING));
		RegistryEntry<Enchantment> tuning = enchantments.entryOf(RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(NoThirst.TAN, "thermal_tuning")));
		for (TagKey<Enchantment> tag : List.of(EnchantmentTags.ON_RANDOM_LOOT, EnchantmentTags.TRADEABLE, EnchantmentTags.TREASURE)) {
			check(!cleansing.isIn(tag), "Water Cleansing should not be in " + tag.id());
			check(tuning.isIn(tag), "Thermal Tuning (temperature) should stay in " + tag.id());
		}
		// A book of it counts as a removed item; one with something else on it too doesn't.
		ItemEnchantmentsComponent.Builder only = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
		only.add(cleansing, 1);
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		book.set(DataComponentTypes.STORED_ENCHANTMENTS, only.build());
		check(NoThirst.isRemoved(book), "a Water Cleansing book should count as removed");
		only.add(tuning, 1);
		book.set(DataComponentTypes.STORED_ENCHANTMENTS, only.build());
		check(!NoThirst.isRemoved(book), "a book with Thermal Tuning too should stay");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void wanderingTradersNeverSellJuice(TestContext ctx) {
		if (!hasTan(ctx)) {
			return;
		}
		ItemStack juice = new ItemStack(Registries.ITEM.get(APPLE_JUICE));
		check(NoThirst.isRemoved(new TradeOffer(new TradedItem(Items.EMERALD, 1), juice, 1, 1, 0f)), "a juice trade should count as removed");
		check(NoThirst.isRemoved(new TradeOffer(new TradedItem(juice.getItem(), 1), new ItemStack(Items.EMERALD), 1, 1, 0f)),
				"buying juice should count as removed too");

		// TAN really does give the wandering trader juice trades (so the check below means something)...
		WanderingTraderEntity probe = ctx.spawnEntity(EntityType.WANDERING_TRADER, new BlockPos(1, 2, 1));
		boolean tanSellsJuice = false;
		for (TradeOffers.Factory factory : TradeOffers.WANDERING_TRADER_TRADES.get(1)) {
			TradeOffer offer = factory.create(probe, probe.getRandom());
			tanSellsJuice |= offer != null && NoThirst.isRemoved(offer);
		}
		check(tanSellsJuice, "expected Tough As Nails to add juice trades to the wandering trader");

		// ...yet no trader ends up with one, and none is left short of trades.
		for (int i = 0; i < 40; i++) {
			WanderingTraderEntity trader = ctx.spawnEntity(EntityType.WANDERING_TRADER, new BlockPos(1, 2, 1));
			var offers = trader.getOffers();
			for (TradeOffer offer : offers) {
				check(!NoThirst.isRemoved(offer), "wandering trader offered " + offer.getSellItem());
			}
			check(offers.size() >= 6, "wandering trader should have a full set of trades, had " + offers.size());
			trader.discard();
		}
		probe.discard();
		ctx.complete();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}

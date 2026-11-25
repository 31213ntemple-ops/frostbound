package com.frostbound.habanero;

import com.frostbound.Frostbound;
import com.frostbound.addons.FlameExtract;
import com.frostbound.config.FrostboundConfig;
import com.frostbound.environment.WinterClimate;
import com.frostbound.passives.Passive;
import com.frostbound.passives.Passives;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.AliasedBlockItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import net.minecraft.util.Unit;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

/**
 * Habanero peppers, built to go with Farmer's Delight (Refabricated), whose cooking pot cooks them:
 *
 * <ul>
 *   <li><b>Wild habaneros</b> grow in the very coldest biomes ({@link #growsWild}: arctic and boreal ones like
 *       stony peaks, not forests, never ice spikes). They drop peppers and seeds; the seeds grow on farmland.</li>
 *   <li><b>Hot sauce</b>: two peppers in a cooking pot over heat, served into a glass bottle. Shapeless with any
 *       savory dish (tag {@code frostbound:savory_dishes}) it makes the dish Spicy ({@link #SPICY}): eating it
 *       gives the {@link SpicyEffect Spicy} effect, which warms you a little for two minutes.</li>
 *   <li><b>Flame extract</b>: three peppers cooked with a lava bucket in the pot (the empty bucket comes back),
 *       served into a glass bottle. An armour add-on ({@link FlameExtract}) for insulation.</li>
 * </ul>
 *
 * The cooking recipes ({@code data/frostbound/recipe/cooking}) only load with Farmer's Delight. Everything else is
 * always registered, like the mod's other items.
 */
public final class Habaneros {
	/** What wild habaneros grow on (the ground of the coldest biomes). */
	public static final TagKey<Block> WILD_HABANERO_GROWS_ON = TagKey.of(RegistryKeys.BLOCK, Frostbound.id("wild_habanero_grows_on"));
	/** Biomes that never get wild habaneros, however cold (ice spikes). */
	public static final TagKey<Biome> NO_WILD_HABANEROS = TagKey.of(RegistryKeys.BIOME, Frostbound.id("no_wild_habaneros"));
	/** Dishes hot sauce can go on. */
	public static final TagKey<Item> SAVORY_DISHES = TagKey.of(RegistryKeys.ITEM, Frostbound.id("savory_dishes"));

	public static final RegistryKey<PlacedFeature> WILD_HABANERO_PATCH = RegistryKey.of(RegistryKeys.PLACED_FEATURE,
			Frostbound.id("patch_wild_habaneros"));

	public static final Block WILD_HABANERO = Registry.register(Registries.BLOCK, Frostbound.id("wild_habanero"),
			new WildHabaneroBlock(AbstractBlock.Settings.copy(Blocks.SWEET_BERRY_BUSH).noCollision().breakInstantly()
					.sounds(BlockSoundGroup.SWEET_BERRY_BUSH).offset(AbstractBlock.OffsetType.XZ).pistonBehavior(PistonBehavior.DESTROY)));
	public static final Block HABANERO_CROP = Registry.register(Registries.BLOCK, Frostbound.id("habaneros"),
			new HabaneroCropBlock(AbstractBlock.Settings.copy(Blocks.WHEAT)));

	public static final Item WILD_HABANERO_ITEM = item("wild_habanero", new BlockItem(WILD_HABANERO, new Item.Settings()));
	public static final Item HABANERO = item("habanero", new Item(new Item.Settings()));
	public static final Item HABANERO_SEEDS = item("habanero_seeds", new AliasedBlockItem(HABANERO_CROP, new Item.Settings()));
	public static final Item HOT_SAUCE = item("hot_sauce", new TooltipItem(
			new Item.Settings().maxCount(16).recipeRemainder(Items.GLASS_BOTTLE), 2, () -> new Object[0]));
	public static final Item FLAME_EXTRACT = item("flame_extract", new TooltipItem(
			new Item.Settings().maxCount(16).recipeRemainder(Items.GLASS_BOTTLE).rarity(Rarity.UNCOMMON).fireproof(), 2,
			() -> new Object[] {Passive.number(100.0 / FlameExtract.forFull()), FlameExtract.forFull()}));

	/** On a dish: eating it makes you Spicy. Set by {@link SpicyDishRecipe}. */
	public static final ComponentType<Unit> SPICY = Registry.register(Registries.DATA_COMPONENT_TYPE, Frostbound.id("spicy"),
			ComponentType.<Unit>builder().codec(Unit.CODEC).packetCodec(net.minecraft.network.codec.PacketCodec.unit(Unit.INSTANCE)).build());

	public static final RegistryEntry<StatusEffect> SPICY_EFFECT = Registry.registerReference(Registries.STATUS_EFFECT,
			Frostbound.id("spicy"), new SpicyEffect());

	public static final RecipeSerializer<SpicyDishRecipe> SPICY_DISH_SERIALIZER = Registry.register(Registries.RECIPE_SERIALIZER,
			Frostbound.id("spicy_dish"), new SpecialRecipeSerializer<>(SpicyDishRecipe::new));

	/** "Spicy" on a dish with hot sauce on it. */
	public static final Passive SPICY_PASSIVE = Passives.register(new Passive("spicy", Formatting.RED,
			stack -> stack.contains(SPICY), stack -> new Object[] {Passive.number(FrostboundConfig.get().habanero.spicySeconds / 60.0)}));

	private Habaneros() {}

	private static Item item(String name, Item item) {
		return Registry.register(Registries.ITEM, Frostbound.id(name), item);
	}

	/** Whether hot sauce can go on {@code stack}: a savory dish that isn't spicy yet. */
	public static boolean canSpice(ItemStack stack) {
		return stack.isIn(SAVORY_DISHES) && !stack.contains(SPICY);
	}

	/** Called when {@code eater} finishes eating {@code food} (mixin/habanero/LivingEntityEatMixin). */
	public static void onEaten(LivingEntity eater, ItemStack food) {
		if (!eater.getWorld().isClient && food.contains(SPICY)) {
			eater.addStatusEffect(new StatusEffectInstance(SPICY_EFFECT, FrostboundConfig.get().habanero.spicySeconds * 20));
		}
	}

	/**
	 * Very cold land biomes, where wild habaneros grow: a biome whose winter temperature ({@link WinterClimate}'s
	 * table for vanilla biomes; an Overworld biome from another mod gets its estimate from how warm it was) is at
	 * most {@code habanero.wildMaxCelsius}, not an ocean, river or cave, and not in {@link #NO_WILD_HABANEROS}.
	 */
	public static boolean growsWild(BiomeSelectionContext s) {
		RegistryKey<Biome> key = s.getBiomeKey();
		if (s.hasTag(NO_WILD_HABANEROS) || s.hasTag(ConventionalBiomeTags.IS_OCEAN) || s.hasTag(ConventionalBiomeTags.IS_RIVER)
				|| s.hasTag(ConventionalBiomeTags.IS_CAVE) || s.hasTag(ConventionalBiomeTags.IS_UNDERGROUND) || WinterClimate.UNCHANGED.contains(key)) {
			return false;
		}
		if (!WinterClimate.TABLE.containsKey(key) && !BiomeSelectors.foundInOverworld().test(s)) {
			return false;
		}
		return winterCelsius(key, s.getBiome().getTemperature()) <= FrostboundConfig.get().habanero.wildMaxCelsius;
	}

	/** A biome's winter temperature in °C: its line in the table, or the modded-biome estimate from its original temperature. */
	public static float winterCelsius(RegistryKey<Biome> key, float originalTemperature) {
		WinterClimate.WinterProfile profile = WinterClimate.TABLE.get(key);
		return profile != null ? profile.celsius() : WinterClimate.forModdedBiome(originalTemperature, false).celsius();
	}

	public static void init() {
		FlameExtract.init();
		LavaPots.init();
		// Before the winter changes (POST_PROCESSING), so a modded biome's temperature is still its original one.
		BiomeModifications.addFeature(Habaneros::growsWild, GenerationStep.Feature.VEGETAL_DECORATION, WILD_HABANERO_PATCH);

		CompostingChanceRegistry.INSTANCE.add(HABANERO_SEEDS, 0.3f);
		CompostingChanceRegistry.INSTANCE.add(HABANERO, 0.65f);
		CompostingChanceRegistry.INSTANCE.add(WILD_HABANERO_ITEM, 0.65f);

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
			entries.addAfter(Items.BEETROOT_SEEDS, HABANERO_SEEDS);
			entries.addAfter(Items.SWEET_BERRIES, WILD_HABANERO_ITEM);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> {
			entries.addAfter(Items.BEETROOT, HABANERO);
			entries.addAfter(Items.HONEY_BOTTLE, HOT_SAUCE);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.addAfter(Items.BLAZE_POWDER, FLAME_EXTRACT));
	}
}

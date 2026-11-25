package com.frostbound.test;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import com.frostbound.habanero.Habaneros;
import com.frostbound.passives.Passive;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.feature.PlacedFeature;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Habaneros: where they grow wild, the crop, hot sauce and flame extract in Farmer's Delight's cooking pot
 * (which runs in dev), spicy dishes and the Spicy effect.
 */
public class HabaneroTests implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void wildHabanerosGrowOnlyInVeryColdBiomes(TestContext ctx) {
		Registry<Biome> biomes = ctx.getWorld().getRegistryManager().get(RegistryKeys.BIOME);
		for (RegistryKey<Biome> cold : List.of(BiomeKeys.STONY_PEAKS, BiomeKeys.SNOWY_PLAINS, BiomeKeys.SNOWY_SLOPES, BiomeKeys.FROZEN_PEAKS,
				BiomeKeys.GROVE, BiomeKeys.TAIGA, BiomeKeys.WINDSWEPT_HILLS)) {
			check(hasWildHabaneros(biomes, cold), cold.getValue() + " is very cold and should grow wild habaneros");
		}
		for (RegistryKey<Biome> not : List.of(BiomeKeys.ICE_SPIKES, BiomeKeys.FOREST, BiomeKeys.PLAINS, BiomeKeys.BIRCH_FOREST,
				BiomeKeys.DESERT, BiomeKeys.FROZEN_RIVER, BiomeKeys.FROZEN_OCEAN, BiomeKeys.DEEP_DARK, BiomeKeys.MEADOW)) {
			check(!hasWildHabaneros(biomes, not), not.getValue() + " shouldn't grow wild habaneros");
		}
		check(Habaneros.winterCelsius(BiomeKeys.STONY_PEAKS, 1.0f) <= FrostboundConfig.get().habanero.wildMaxCelsius, "stony peaks is under the line");
		check(Habaneros.winterCelsius(BiomeKeys.FOREST, 0.7f) > FrostboundConfig.get().habanero.wildMaxCelsius, "forest is over the line");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void wildHabaneroGrowsOnColdGroundAndDropsPeppers(TestContext ctx) {
		BlockState wild = Habaneros.WILD_HABANERO.getDefaultState();
		for (Block ground : List.of(Blocks.STONE, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.GRAVEL, Blocks.SNOW_BLOCK, Blocks.CALCITE)) {
			ctx.setBlockState(1, 1, 1, ground);
			check(wild.canPlaceAt(ctx.getWorld(), ctx.getAbsolutePos(new BlockPos(1, 2, 1))), "wild habaneros should grow on " + ground);
		}
		for (Block ground : List.of(Blocks.SAND, Blocks.ICE, Blocks.OAK_PLANKS)) {
			ctx.setBlockState(1, 1, 1, ground);
			check(!wild.canPlaceAt(ctx.getWorld(), ctx.getAbsolutePos(new BlockPos(1, 2, 1))), "wild habaneros shouldn't grow on " + ground);
		}
		ctx.setBlockState(1, 1, 1, Blocks.STONE);
		ctx.setBlockState(1, 2, 1, wild);
		List<ItemStack> drops = Block.getDroppedStacks(wild, ctx.getWorld(), ctx.getAbsolutePos(new BlockPos(1, 2, 1)), null);
		check(drops.stream().anyMatch(s -> s.isOf(Habaneros.HABANERO)), "a wild habanero should drop peppers, dropped " + drops);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void habaneroCropGrowsFromSeeds(TestContext ctx) {
		CropBlock crop = (CropBlock) Habaneros.HABANERO_CROP;
		check(crop.getPickStack(ctx.getWorld(), BlockPos.ORIGIN, crop.getDefaultState()).isOf(Habaneros.HABANERO_SEEDS), "seeds plant the crop");
		check(Habaneros.HABANERO_SEEDS instanceof net.minecraft.item.AliasedBlockItem seeds && seeds.getBlock() == crop, "seeds place the crop");
		ctx.setBlockState(1, 1, 1, Blocks.FARMLAND);
		BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		check(crop.getDefaultState().canPlaceAt(ctx.getWorld(), pos), "the crop goes on farmland");
		ctx.setBlockState(1, 2, 1, crop.withAge(crop.getMaxAge()));
		List<ItemStack> ripe = Block.getDroppedStacks(crop.withAge(crop.getMaxAge()), ctx.getWorld(), pos, null);
		check(ripe.stream().anyMatch(s -> s.isOf(Habaneros.HABANERO)), "a ripe crop drops peppers, dropped " + ripe);
		List<ItemStack> young = Block.getDroppedStacks(crop.withAge(2), ctx.getWorld(), pos, null);
		check(young.stream().noneMatch(s -> s.isOf(Habaneros.HABANERO)) && young.stream().anyMatch(s -> s.isOf(Habaneros.HABANERO_SEEDS)),
				"an unripe crop only gives its seed back, dropped " + young);
		check(crop.getDefaultState().isIn(net.minecraft.registry.tag.BlockTags.CROPS), "habaneros are in minecraft:crops (bees, farmers)");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void cookingRecipesLoadWithFarmersDelight(TestContext ctx) {
		check(FabricLoader.getInstance().isModLoaded("farmersdelight"), "Farmer's Delight should run in dev");
		var manager = ctx.getWorld().getRecipeManager();
		Optional<RecipeEntry<?>> sauce = manager.get(Frostbound.id("cooking/hot_sauce"));
		Optional<RecipeEntry<?>> extract = manager.get(Frostbound.id("cooking/flame_extract"));
		check(sauce.isPresent() && extract.isPresent(), "both cooking pot recipes should load");
		Identifier cooking = Identifier.of("farmersdelight", "cooking");
		check(cooking.equals(Registries.RECIPE_TYPE.getId(sauce.get().value().getType())), "hot sauce is a cooking pot recipe");
		var registries = ctx.getWorld().getRegistryManager();
		check(sauce.get().value().getResult(registries).isOf(Habaneros.HOT_SAUCE), "the pot makes hot sauce");
		check(extract.get().value().getResult(registries).isOf(Habaneros.FLAME_EXTRACT), "the pot makes flame extract");
		check(extract.get().value().getIngredients().stream().anyMatch(i -> i.test(new ItemStack(Items.LAVA_BUCKET))),
				"flame extract is cooked in lava");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
	public void cookingPotMakesHotSauce(TestContext ctx) {
		Object inventory = pot(ctx);
		put(inventory, 0, new ItemStack(Habaneros.HABANERO));
		put(inventory, 1, new ItemStack(Habaneros.HABANERO));
		put(inventory, 7, new ItemStack(Items.GLASS_BOTTLE)); // the container slot
		ctx.runAtTick(300, () -> {
			ItemStack meal = get(inventory, 6), output = get(inventory, 8);
			check(meal.isOf(Habaneros.HOT_SAUCE) || output.isOf(Habaneros.HOT_SAUCE),
					"two peppers over heat should cook into hot sauce, got " + meal + " / " + output);
			check(get(inventory, 0).isEmpty() && get(inventory, 1).isEmpty(), "the peppers are used up");
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 600)
	public void cookingPotMakesFlameExtractInLava(TestContext ctx) {
		Object inventory = pot(ctx);
		put(inventory, 0, new ItemStack(Items.LAVA_BUCKET));
		for (int i = 1; i <= 3; i++) {
			put(inventory, i, new ItemStack(Habaneros.HABANERO));
		}
		put(inventory, 7, new ItemStack(Items.GLASS_BOTTLE));
		ctx.runAtTick(500, () -> {
			ItemStack meal = get(inventory, 6), output = get(inventory, 8);
			check(meal.isOf(Habaneros.FLAME_EXTRACT) || output.isOf(Habaneros.FLAME_EXTRACT),
					"peppers cooked in lava should make flame extract, got " + meal + " / " + output);
			Box around = new Box(ctx.getAbsolutePos(new BlockPos(1, 2, 1))).expand(3);
			boolean bucket = !ctx.getWorld().getEntitiesByClass(ItemEntity.class, around, e -> e.getStack().isOf(Items.BUCKET)).isEmpty();
			for (int i = 0; i < 9; i++) {
				bucket |= get(inventory, i).isOf(Items.BUCKET);
			}
			check(bucket, "the empty bucket should come back");
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void hotSauceMakesSavoryDishesSpicy(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		RecipeEntry<?> entry = world.getRecipeManager().get(Frostbound.id("spicy_dish")).orElseThrow(() -> new GameTestException("no spicy dish recipe"));
		CraftingRecipe recipe = (CraftingRecipe) entry.value();
		check(recipe.getType() == RecipeType.CRAFTING, "spicing is a crafting recipe");

		CraftingRecipeInput steak = grid(new ItemStack(Items.COOKED_BEEF), new ItemStack(Habaneros.HOT_SAUCE));
		check(recipe.matches(steak, world), "hot sauce goes on steak");
		ItemStack spicy = recipe.craft(steak, world.getRegistryManager());
		check(spicy.isOf(Items.COOKED_BEEF) && spicy.getCount() == 1 && spicy.contains(Habaneros.SPICY), "it makes one spicy steak, got " + spicy);
		DefaultedList<ItemStack> left = recipe.getRemainder(steak);
		check(left.stream().anyMatch(s -> s.isOf(Items.GLASS_BOTTLE)), "the sauce's bottle is handed back");

		check(recipe.matches(grid(new ItemStack(Habaneros.HOT_SAUCE), ItemStack.EMPTY, new ItemStack(Items.RABBIT_STEW)), world),
				"anywhere in the grid, with a stew");
		check(!recipe.matches(grid(spicy, new ItemStack(Habaneros.HOT_SAUCE)), world), "a spicy dish can't be spiced again");
		check(!recipe.matches(grid(new ItemStack(Items.COOKIE), new ItemStack(Habaneros.HOT_SAUCE)), world), "cookies aren't savory");
		check(!recipe.matches(grid(new ItemStack(Items.COOKED_BEEF)), world), "no sauce, no spice");
		check(!recipe.matches(grid(new ItemStack(Items.COOKED_BEEF), new ItemStack(Habaneros.HOT_SAUCE), new ItemStack(Habaneros.HOT_SAUCE)), world),
				"one sauce per dish");
		check(!recipe.matches(grid(new ItemStack(Items.COOKED_BEEF), new ItemStack(Items.BAKED_POTATO), new ItemStack(Habaneros.HOT_SAUCE)), world),
				"one dish at a time");
		check(new ItemStack(Registries.ITEM.get(Identifier.of("farmersdelight", "beef_stew"))).isIn(Habaneros.SAVORY_DISHES),
				"Farmer's Delight meals are savory");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void eatingASpicyDishWarmsYou(TestContext ctx) {
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		ItemStack plain = new ItemStack(Items.COOKED_BEEF);
		player.eatFood(ctx.getWorld(), plain, plain.get(DataComponentTypes.FOOD));
		check(!player.hasStatusEffect(Habaneros.SPICY_EFFECT), "plain steak isn't spicy");

		ItemStack spicy = new ItemStack(Items.COOKED_BEEF);
		spicy.set(Habaneros.SPICY, net.minecraft.util.Unit.INSTANCE);
		player.eatFood(ctx.getWorld(), spicy, spicy.get(DataComponentTypes.FOOD));
		StatusEffectInstance effect = player.getStatusEffect(Habaneros.SPICY_EFFECT);
		check(effect != null && effect.getDuration() == FrostboundConfig.get().habanero.spicySeconds * 20,
				"a spicy steak should make you Spicy for " + FrostboundConfig.get().habanero.spicySeconds + " seconds, got " + effect);
		check(Habaneros.SPICY_EFFECT.value().isBeneficial(), "Spicy is a good effect");

		// It thaws you: frost drops while Spicy.
		player.setFrozenTicks(100);
		for (int t = 0; t < 20; t++) {
			if (Habaneros.SPICY_EFFECT.value().canApplyUpdateEffect(t, 0)) {
				Habaneros.SPICY_EFFECT.value().applyUpdateEffect(player, 0);
			}
		}
		check(player.getFrozenTicks() == 90, "Spicy should thaw 1 frost every 2 ticks, left " + player.getFrozenTicks());
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void spicyTooltipText(TestContext ctx) {
		Passive passive = Habaneros.SPICY_PASSIVE;
		check(passive.title().getString().equals("Spicy"), "title should read Spicy, got " + passive.title().getString());
		check(Formatting.RED.getColorValue().equals(passive.title().getStyle().getColor().getRgb()) && !passive.title().getStyle().isBold(),
				"Spicy should be red, not bold");
		String expected = "Warms you up for " + Passive.number(FrostboundConfig.get().habanero.spicySeconds / 60.0) + " minutes once eaten.";
		check(passive.description().getString().equals(expected), "description should be \"" + expected + "\", got \"" + passive.description().getString() + "\"");
		ItemStack spicy = new ItemStack(Items.MUSHROOM_STEW);
		check(!passive.appliesTo(spicy), "a plain stew has no Spicy line");
		spicy.set(Habaneros.SPICY, net.minecraft.util.Unit.INSTANCE);
		check(passive.appliesTo(spicy), "a spicy stew says Spicy");
		ctx.complete();
	}

	// --- Helpers ---

	private static boolean hasWildHabaneros(Registry<Biome> biomes, RegistryKey<Biome> key) {
		Biome biome = biomes.get(key);
		if (biome == null) {
			throw new GameTestException("no biome " + key.getValue());
		}
		for (RegistryEntryList<PlacedFeature> step : biome.getGenerationSettings().getFeatures()) {
			for (RegistryEntry<PlacedFeature> feature : step) {
				if (feature.matchesKey(Habaneros.WILD_HABANERO_PATCH)) {
					return true;
				}
			}
		}
		return false;
	}

	private static CraftingRecipeInput grid(ItemStack... stacks) {
		return CraftingRecipeInput.create(3, 1, List.of(stacks.length > 0 ? stacks[0] : ItemStack.EMPTY,
				stacks.length > 1 ? stacks[1] : ItemStack.EMPTY, stacks.length > 2 ? stacks[2] : ItemStack.EMPTY));
	}

	/** A cooking pot on a magma block (a heat source); returns its item handler (FD's own class, by reflection). */
	private static Object pot(TestContext ctx) {
		ctx.setBlockState(1, 1, 1, Blocks.MAGMA_BLOCK);
		ctx.setBlockState(1, 2, 1, Registries.BLOCK.get(Identifier.of("farmersdelight", "cooking_pot")));
		BlockEntity pot = ctx.getWorld().getBlockEntity(ctx.getAbsolutePos(new BlockPos(1, 2, 1)));
		check(pot != null, "the cooking pot should have a block entity");
		try {
			return pot.getClass().getMethod("getInventory").invoke(pot);
		} catch (ReflectiveOperationException e) {
			throw new GameTestException("can't reach the cooking pot's inventory: " + e);
		}
	}

	private static void put(Object inventory, int slot, ItemStack stack) {
		try {
			Method set = inventory.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
			set.invoke(inventory, slot, stack);
		} catch (ReflectiveOperationException e) {
			throw new GameTestException("can't fill the cooking pot: " + e);
		}
	}

	private static ItemStack get(Object inventory, int slot) {
		try {
			return (ItemStack) inventory.getClass().getMethod("getStackInSlot", int.class).invoke(inventory, slot);
		} catch (ReflectiveOperationException e) {
			throw new GameTestException("can't read the cooking pot: " + e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}

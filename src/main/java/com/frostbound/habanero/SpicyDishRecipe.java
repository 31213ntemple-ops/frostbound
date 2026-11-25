package com.frostbound.habanero;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

/**
 * Hot sauce + any savory dish (item tag {@code frostbound:savory_dishes}) in a crafting grid, anywhere, gives
 * the dish back Spicy ({@link Habaneros#SPICY}). The sauce's bottle is handed back. A dish that's already
 * spicy can't be spiced again.
 */
public class SpicyDishRecipe extends SpecialCraftingRecipe {
	public SpicyDishRecipe(CraftingRecipeCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingRecipeInput input, World world) {
		return !dish(input).isEmpty();
	}

	@Override
	public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
		ItemStack dish = dish(input);
		if (dish.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack spicy = dish.copyWithCount(1);
		spicy.set(Habaneros.SPICY, net.minecraft.util.Unit.INSTANCE);
		return spicy;
	}

	/** The one savory dish in the grid if the grid holds exactly that and one hot sauce, else EMPTY. */
	private static ItemStack dish(CraftingRecipeInput input) {
		ItemStack dish = ItemStack.EMPTY;
		int sauces = 0;
		for (ItemStack stack : input.getStacks()) {
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.isOf(Habaneros.HOT_SAUCE)) {
				sauces++;
			} else if (Habaneros.canSpice(stack) && dish.isEmpty()) {
				dish = stack;
			} else {
				return ItemStack.EMPTY;
			}
		}
		return sauces == 1 ? dish : ItemStack.EMPTY;
	}

	@Override
	public boolean fits(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return Habaneros.SPICY_DISH_SERIALIZER;
	}
}

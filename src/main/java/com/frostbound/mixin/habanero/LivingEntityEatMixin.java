package com.frostbound.mixin.habanero;

import com.frostbound.habanero.Habaneros;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Eating a spicy dish makes you Spicy. eatFood is where every food ends up (players call it through super, and
 * Farmer's Delight's meals through Item.finishUsing); at its head the stack hasn't been used up yet.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityEatMixin {
	@Inject(method = "eatFood", at = @At("HEAD"))
	private void frostbound$spicy(World world, ItemStack stack, FoodComponent food, CallbackInfoReturnable<ItemStack> cir) {
		Habaneros.onEaten((LivingEntity) (Object) this, stack);
	}
}

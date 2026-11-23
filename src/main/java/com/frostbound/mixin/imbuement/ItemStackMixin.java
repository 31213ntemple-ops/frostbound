package com.frostbound.mixin.imbuement;

import com.frostbound.imbuement.FrostTiers;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiConsumer;

/**
 * Cold, Icy and Frozen armour's stats: more durability, and extra armour and toughness merged into the
 * piece's own modifiers, both for wearing it (applyAttributeModifiers) and for the tooltip
 * (applyAttributeModifier). See {@link FrostTiers}.
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
	@Inject(method = "getMaxDamage", at = @At("RETURN"), cancellable = true)
	private void frostbound$frostDurability(CallbackInfoReturnable<Integer> cir) {
		int base = cir.getReturnValueI();
		int boosted = FrostTiers.maxDamage((ItemStack) (Object) this, base);
		if (boosted != base) {
			cir.setReturnValue(boosted);
		}
	}

	@ModifyVariable(method = "applyAttributeModifier", at = @At("HEAD"), argsOnly = true)
	private BiConsumer<RegistryEntry<EntityAttribute>, EntityAttributeModifier> frostbound$tooltipBonus(
			BiConsumer<RegistryEntry<EntityAttribute>, EntityAttributeModifier> consumer) {
		return FrostTiers.withBonus((ItemStack) (Object) this, consumer);
	}

	@ModifyVariable(method = "applyAttributeModifiers", at = @At("HEAD"), argsOnly = true)
	private BiConsumer<RegistryEntry<EntityAttribute>, EntityAttributeModifier> frostbound$wornBonus(
			BiConsumer<RegistryEntry<EntityAttribute>, EntityAttributeModifier> consumer) {
		return FrostTiers.withBonus((ItemStack) (Object) this, consumer);
	}
}

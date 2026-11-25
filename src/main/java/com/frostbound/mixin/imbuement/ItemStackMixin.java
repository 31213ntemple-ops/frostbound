package com.frostbound.mixin.imbuement;

import com.frostbound.imbuement.FrostTiers;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiConsumer;

/**
 * Cold, Icy and Frozen gear's stats: more durability, and extra armour, toughness or attack damage merged
 * into the piece's own modifiers when it's worn or held (applyAttributeModifiers). The tooltip
 * (applyAttributeModifier, only used there) keeps vanilla's line and adds the bonus after it, "+8 Armor (+1)".
 * See {@link FrostTiers}.
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
		return FrostTiers.forTooltip((ItemStack) (Object) this, consumer);
	}

	@ModifyArg(method = "appendAttributeModifierTooltip",
			at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
	private Object frostbound$tooltipBonusText(Object line) {
		return FrostTiers.withTooltipBonus(line);
	}

	@ModifyVariable(method = "applyAttributeModifiers", at = @At("HEAD"), argsOnly = true)
	private BiConsumer<RegistryEntry<EntityAttribute>, EntityAttributeModifier> frostbound$wornBonus(
			BiConsumer<RegistryEntry<EntityAttribute>, EntityAttributeModifier> consumer) {
		return FrostTiers.withBonus((ItemStack) (Object) this, consumer);
	}
}

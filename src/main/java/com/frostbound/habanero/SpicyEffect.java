package com.frostbound.habanero;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * Spicy, from eating a dish with hot sauce on it: warms you up a little. With Tough As Nails you're one
 * step warmer (never pushed into Hot; compat/TanTemperature). Frost also thaws off you faster, so powder
 * snow takes twice as long to freeze you.
 */
public class SpicyEffect extends StatusEffect {
	public SpicyEffect() {
		super(StatusEffectCategory.BENEFICIAL, 0xE8481C);
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return duration % 2 == 0;
	}

	@Override
	public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
		if (!entity.getWorld().isClient && entity.getFrozenTicks() > 0) {
			entity.setFrozenTicks(entity.getFrozenTicks() - 1);
		}
		return true;
	}
}

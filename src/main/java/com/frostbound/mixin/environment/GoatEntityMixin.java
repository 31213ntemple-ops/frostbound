package com.frostbound.mixin.environment;

import com.frostbound.environment.GoatGrudges;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.GoatEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Stores a goat's grudge (see GoatGrudges). Not saved: it only lasts a few seconds. */
@Mixin(GoatEntity.class)
public abstract class GoatEntityMixin implements GoatGrudges.Holder {
	@Unique
	@Nullable
	private LivingEntity frostbound$grudge;
	@Unique
	private long frostbound$grudgeUntil;

	@Override
	@Nullable
	public LivingEntity frostbound$grudgeTarget() {
		return this.frostbound$grudge;
	}

	@Override
	public long frostbound$grudgeUntil() {
		return this.frostbound$grudgeUntil;
	}

	@Override
	public void frostbound$setGrudge(@Nullable LivingEntity target, long until) {
		this.frostbound$grudge = target;
		this.frostbound$grudgeUntil = until;
	}
}

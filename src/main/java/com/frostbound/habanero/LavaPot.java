package com.frostbound.habanero;

/**
 * A Farmer's Delight cooking pot that can hold lava (mixed into its block entity by
 * mixin/habanero/CookingPotBlockEntityMixin). See {@link LavaPots}.
 */
public interface LavaPot {
	boolean frostbound$hasLava();

	void frostbound$setLava(boolean lava);
}

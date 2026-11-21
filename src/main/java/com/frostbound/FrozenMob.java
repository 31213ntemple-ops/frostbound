package com.frostbound;

import net.minecraft.entity.Entity;

/**
 * Added to every MobEntity by MobEntityMixin. Method names carry the mod id
 * prefix so they can never collide with another mod's mixins.
 */
public interface FrozenMob {
	boolean frostbound$isFrozen();

	void frostbound$setFrozen(boolean frozen);

	/** Null-safe check usable on any entity (players, arrows, null attackers...). */
	static boolean isFrozen(Entity entity) {
		return entity instanceof FrozenMob mob && mob.frostbound$isFrozen();
	}
}

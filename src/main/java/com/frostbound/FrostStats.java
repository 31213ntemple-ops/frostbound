package com.frostbound;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

/**
 * All the balance numbers in one place, plus applying/removing the attribute changes.
 *
 * Attribute modifiers are saved with the mob automatically, so a Frozen mob keeps
 * its stats across world reloads without us doing anything extra.
 */
public final class FrostStats {
	public static final double HEALTH_BONUS = 0.75;        // +75% max health
	public static final double SPEED_PENALTY = -0.35;      // -35% movement speed
	public static final double KNOCKBACK_RESIST = 0.30;    // heavy with ice: +30% knockback resistance
	public static final float DAMAGE_MULTIPLIER = 1.5f;    // all damage they deal (melee, arrows, explosions)
	public static final float FIRE_WEAKNESS = 2.0f;        // fire/lava damage taken
	public static final int FROSTBITE_TICKS = 60;          // Slowness duration on hit (3 seconds)

	private static final Identifier HEALTH_ID = Frostbound.id("frozen_health");
	private static final Identifier SPEED_ID = Frostbound.id("frozen_speed");
	private static final Identifier KNOCKBACK_ID = Frostbound.id("frozen_knockback");

	private FrostStats() {}

	public static void apply(MobEntity mob) {
		boolean healthAdded = add(mob, EntityAttributes.GENERIC_MAX_HEALTH, HEALTH_ID,
				HEALTH_BONUS, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		add(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID,
				SPEED_PENALTY, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		add(mob, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK_ID,
				KNOCKBACK_RESIST, EntityAttributeModifier.Operation.ADD_VALUE);

		// Only top up health when the bonus is freshly added (not when reloading a saved mob).
		if (healthAdded) {
			mob.setHealth(mob.getMaxHealth());
		}
	}

	public static void remove(MobEntity mob) {
		removeModifier(mob, EntityAttributes.GENERIC_MAX_HEALTH, HEALTH_ID);
		removeModifier(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID);
		removeModifier(mob, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
		if (mob.getHealth() > mob.getMaxHealth()) {
			mob.setHealth(mob.getMaxHealth());
		}
	}

	private static boolean add(MobEntity mob, RegistryEntry<EntityAttribute> attribute, Identifier id,
			double value, EntityAttributeModifier.Operation operation) {
		EntityAttributeInstance instance = mob.getAttributeInstance(attribute);
		if (instance == null || instance.hasModifier(id)) {
			return false;
		}
		instance.addPersistentModifier(new EntityAttributeModifier(id, value, operation));
		return true;
	}

	private static void removeModifier(MobEntity mob, RegistryEntry<EntityAttribute> attribute, Identifier id) {
		EntityAttributeInstance instance = mob.getAttributeInstance(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}
}

package com.frostbound.alpha;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import com.frostbound.copper.Conductivity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Life Steal I–II (data/frostbound/enchantment/life_steal.json), only from books crafted with wolf teeth.
 *
 * The rule, the same for melee and ranged: a hit heals its attacker {@code healPerLevel} × level of the
 * health the target actually lost. The level is read from the weapon the hit came from, so:
 *
 *  - melee (sword, axe, also its sweep): the weapon in your main hand;
 *  - arrows and bolts: the bow or crossbow that shot them (switching items mid-flight changes nothing);
 *  - a thrown trident: the trident itself.
 *
 * "Health actually lost" is after armour, resistances and absorption, and capped at what the target had
 * left, so overkill and hits on golden hearts heal nothing extra. Arcs (Conductivity), thorns, Diamond
 * Dust, explosions and other indirect damage never heal. Armour stands don't count.
 */
public final class LifeSteal {
	public static final RegistryKey<Enchantment> KEY = RegistryKey.of(RegistryKeys.ENCHANTMENT, Frostbound.id("life_steal"));
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.75f, 0.05f, 0.12f), 0.9f);

	/** Each target's health as a hit begins, to work out what it actually lost. */
	private static final Map<LivingEntity, Float> HEALTH_BEFORE = new WeakHashMap<>();

	private LifeSteal() {}

	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			HEALTH_BEFORE.put(entity, entity.getHealth());
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, baseDamage, damageTaken, blocked) -> {
			Float before = HEALTH_BEFORE.remove(target);
			if (before != null && !blocked) {
				heal(target, source, before - Math.max(0.0f, target.getHealth()));
			}
		});
		// Fabric reports a killing blow here instead of AFTER_DAMAGE: the target lost all it had left.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Float before = HEALTH_BEFORE.remove(entity);
			if (before != null) {
				heal(entity, source, before);
			}
		});
	}

	/** The Life Steal level of the weapon {@code source} came from, or 0 if it's not a hit that can steal. */
	public static int level(DamageSource source) {
		if (!Conductivity.isMelee(source) && !source.isIn(DamageTypeTags.IS_PROJECTILE)) {
			return 0;
		}
		ItemStack weapon = source.getWeaponStack();
		if (weapon == null || weapon.isEmpty() || !(source.getAttacker() instanceof LivingEntity attacker)) {
			return 0;
		}
		Optional<RegistryEntry.Reference<Enchantment>> entry = attacker.getWorld().getRegistryManager()
				.get(RegistryKeys.ENCHANTMENT).getEntry(KEY);
		return entry.map(e -> EnchantmentHelper.getLevel(e, weapon)).orElse(0);
	}

	/** Heals the attacker for a hit that took {@code healthLost} from {@code target}. Returns how much it healed. */
	public static float heal(LivingEntity target, DamageSource source, float healthLost) {
		if (healthLost <= 0.0f || target instanceof ArmorStandEntity || !(source.getAttacker() instanceof LivingEntity attacker)
				|| attacker == target || !attacker.isAlive()) {
			return 0.0f;
		}
		int level = level(source);
		if (level <= 0) {
			return 0.0f;
		}
		float amount = (float) (healthLost * level * FrostboundConfig.get().lifeSteal.healPerLevel);
		if (amount <= 0.0f) {
			return 0.0f;
		}
		attacker.heal(amount);
		if (attacker.getWorld() instanceof ServerWorld world) {
			world.spawnParticles(BLOOD, target.getX(), target.getBodyY(0.6), target.getZ(), 6, 0.25, 0.3, 0.25, 0.0);
			world.spawnParticles(BLOOD, attacker.getX(), attacker.getBodyY(0.6), attacker.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
		}
		return amount;
	}
}

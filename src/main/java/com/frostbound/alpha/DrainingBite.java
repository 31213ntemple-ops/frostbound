package com.frostbound.alpha;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import com.frostbound.copper.Conductivity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

/**
 * Draining Bite: a pack wolf's bite that lands on a player (not blocked by a shield) applies Drained,
 * which takes {@code hearts} off their max health for {@code seconds}. When it ends (or is cured with
 * milk, or on death) the max health comes back; current health doesn't, so it reads as a wound.
 *
 * It can't pile up: a bite while Drained neither stacks nor extends it, so a player is never down more
 * than {@code hearts}, and never for longer than {@code seconds} after the bite that started it.
 *
 * Distinct from damage: a dark red status effect with its own icon, a heartbeat and crimson mist on the
 * bite, and the lost hearts drawn as hollow, dark hearts in the health bar (client.alpha.DrainedHeartsHud).
 */
public final class DrainingBite {
	/** The max health modifier's id; the HUD reads it to know how many hearts to draw as drained. */
	public static final Identifier MODIFIER_ID = Frostbound.id("draining_bite");
	public static final int COLOR = 0x6B1020;
	private static final DustParticleEffect MIST = new DustParticleEffect(new Vector3f(0.42f, 0.04f, 0.1f), 1.2f);

	private DrainingBite() {}

	/** The Drained status effect; its max health penalty comes from the config (restart to change). */
	static final class DrainedEffect extends StatusEffect {
		DrainedEffect() {
			super(StatusEffectCategory.HARMFUL, COLOR);
			this.addAttributeModifier(EntityAttributes.GENERIC_MAX_HEALTH, MODIFIER_ID,
					-2.0 * FrostboundConfig.get().alphaWolf.drainingBite.hearts, EntityAttributeModifier.Operation.ADD_VALUE);
		}
	}

	static void init() {
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register(
				(target, source, baseDamage, damageTaken, blocked) -> onHit(target, source, damageTaken, blocked));
	}

	/** Applies Drained if this was a pack wolf's bite that landed on a player. Returns whether it did. */
	public static boolean onHit(LivingEntity target, DamageSource source, float damageTaken, boolean blocked) {
		if (blocked || damageTaken <= 0.0f || !(target instanceof PlayerEntity player) || !player.isAlive()
				|| !(source.getAttacker() instanceof PackWolfEntity) || !Conductivity.isMelee(source)
				|| player.hasStatusEffect(AlphaWolves.DRAINED)) {
			return false;
		}
		int ticks = AlphaWolfEntity.ticks(FrostboundConfig.get().alphaWolf.drainingBite.seconds);
		if (ticks <= 0) {
			return false;
		}
		player.addStatusEffect(new StatusEffectInstance(AlphaWolves.DRAINED, ticks, 0, false, true, true), source.getAttacker());
		// Health that no longer fits goes at once (vanilla would only trim it on the next tick).
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
		if (player.getWorld() instanceof ServerWorld world) {
			world.spawnParticles(MIST, player.getX(), player.getBodyY(0.6), player.getZ(), 16, 0.3, 0.4, 0.3, 0.0);
			world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
					SoundCategory.PLAYERS, 1.0f, 0.7f);
		}
		return true;
	}

	/** Hearts {@code entity} is drained of right now (0 if none), read from its max health modifier. */
	public static int drainedHearts(LivingEntity entity) {
		var maxHealth = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		EntityAttributeModifier modifier = maxHealth == null ? null : maxHealth.getModifier(MODIFIER_ID);
		return modifier == null ? 0 : (int) Math.ceil(-modifier.value() / 2.0 - 1.0E-6);
	}
}

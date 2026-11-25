package com.frostbound.environment;

import com.frostbound.config.FrostboundConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.GoatEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Goats ram whatever hurts them, instead of running off like vanilla goats.
 *
 * A hit gives the goat a grudge against the attacker for {@code goats.grudgeSeconds}. While it
 * holds one it doesn't panic, its ram cooldown is cleared, and its next ram goes for the attacker.
 * The ram itself is vanilla's (mixin.environment.PrepareRamTaskMixin only picks the target), so
 * it keeps vanilla's tell: the goat backs off 4 to 7 blocks in a straight line, lowers its head
 * for a second with the prepare-ram sound, then charges. After the ram it goes back on vanilla's
 * cooldown, so each ram takes another hit to set off. If it can't line one up (no path, or still
 * in the air from the hit) it tries again after {@link #RETRY_TICKS} instead of vanilla's 30 s.
 *
 * The panic is turned off by mixin.environment.FleeTaskMixin and the grudge is stored on the goat
 * by mixin.environment.GoatEntityMixin (not saved: it only lasts a few seconds).
 */
public final class GoatGrudges {
	/** Farther than this and the goat forgets about it. */
	private static final double FORGET_DISTANCE = 24.0;
	/** A goat with a grudge that couldn't line up a ram tries again after this many ticks. */
	public static final int RETRY_TICKS = 10;

	private GoatGrudges() {}

	/** Implemented on {@link GoatEntity} by mixin.environment.GoatEntityMixin. */
	public interface Holder {
		@Nullable LivingEntity frostbound$grudgeTarget();

		long frostbound$grudgeUntil();

		void frostbound$setGrudge(@Nullable LivingEntity target, long until);
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (entity instanceof GoatEntity goat && !blocked && goat.isAlive()) {
				onHurt(goat, source);
			}
		});
	}

	/** Holds a grudge against whoever caused {@code source}, if it's a living thing other than a goat. */
	public static void onHurt(GoatEntity goat, DamageSource source) {
		FrostboundConfig.Goats config = FrostboundConfig.get().goats;
		if (!config.retaliate || !(source.getAttacker() instanceof LivingEntity attacker)
				|| attacker instanceof GoatEntity || !attacker.isPartOfGame()) {
			return;
		}
		((Holder) goat).frostbound$setGrudge(attacker, goat.getWorld().getTime() + config.grudgeSeconds * 20L);
		Brain<GoatEntity> brain = goat.getBrain();
		brain.forget(MemoryModuleType.RAM_COOLDOWN_TICKS);
		brain.forget(MemoryModuleType.IS_PANICKING);
	}

	/** Who {@code entity} wants to ram right now, or null (not a goat, no grudge, or it ran out). */
	@Nullable
	public static LivingEntity target(PathAwareEntity entity) {
		if (!(entity instanceof GoatEntity goat)) {
			return null;
		}
		Holder holder = (Holder) goat;
		LivingEntity target = holder.frostbound$grudgeTarget();
		if (target == null) {
			return null;
		}
		if (!target.isAlive() || !target.isPartOfGame() || target.getWorld() != goat.getWorld()
				|| goat.getWorld().getTime() > holder.frostbound$grudgeUntil()
				|| target.squaredDistanceTo(goat) > FORGET_DISTANCE * FORGET_DISTANCE) {
			holder.frostbound$setGrudge(null, 0L);
			return null;
		}
		return target;
	}
}

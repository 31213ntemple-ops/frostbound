package com.frostbound.copper;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import com.frostbound.imbuement.FrostBonuses;
import com.frostbound.passives.Passive;
import com.frostbound.passives.Passives;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Conductivity, the copper passive: a melee hit with a conductive weapon has a small chance to arc to
 * every other mob near the target, dealing them 150% of the hit's damage, and each mob it shocks can
 * arc on in turn with the same chance (no limit on the chain). Balance is in
 * {@code conductivity} in config/frostbound.json; which weapons conduct is the item tag
 * {@code frostbound:conductive} (the copper tools).
 */
public final class Conductivity {
	public static final TagKey<Item> CONDUCTIVE = TagKey.of(RegistryKeys.ITEM, Frostbound.id("conductive"));
	/** Damage type of the arc, so deaths read "was electrocuted by". data/frostbound/damage_type/conductivity.json */
	public static final RegistryKey<DamageType> DAMAGE_TYPE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Frostbound.id("conductivity"));

	public static final Passive PASSIVE = Passives.register(new Passive("conductivity", Formatting.GOLD, CONDUCTIVE, () -> {
		FrostboundConfig.ConductivityOptions c = FrostboundConfig.get().conductivity;
		return new Object[] {Passive.number(c.chance * 100), Passive.number(c.damageMultiplier * 100), Passive.number(c.radius)};
	}));

	/** The age (tick) each attacker last rolled on, so a sword's sweep hits don't each get a roll. */
	private static final Map<LivingEntity, Integer> LAST_ROLL = new WeakHashMap<>();

	private Conductivity() {}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, baseDamage, damageTaken, blocked) -> {
			if (!blocked && baseDamage > 0.0f && source.getAttacker() instanceof LivingEntity attacker && isMelee(source)) {
				float chance = chanceFor(attacker);
				if (chance > 0.0f && firstHitThisTick(attacker) && attacker.getRandom().nextFloat() < chance) {
					arc(attacker, target, baseDamage);
				}
			}
		});
	}

	/**
	 * Chance that a melee hit by {@code attacker} arcs, and that each link of the chain arcs on: the
	 * Conductivity chance if it holds a conductive weapon, plus Superconductor from Icy or Frozen copper
	 * armour (which works with any weapon).
	 */
	public static float chanceFor(LivingEntity attacker) {
		float chance = attacker.getMainHandStack().isIn(CONDUCTIVE) ? FrostboundConfig.get().conductivity.chance : 0.0f;
		return chance + (float) FrostBonuses.SUPERCONDUCTOR.total(attacker);
	}

	/** A direct swing: not an arrow, trident or thorns, and not another arc. */
	public static boolean isMelee(DamageSource source) {
		return source.isDirect() && (source.isOf(DamageTypes.PLAYER_ATTACK) || source.isOf(DamageTypes.MOB_ATTACK)
				|| source.isOf(DamageTypes.MOB_ATTACK_NO_AGGRO));
	}

	private static boolean firstHitThisTick(LivingEntity attacker) {
		Integer last = LAST_ROLL.put(attacker, attacker.age);
		return last == null || last != attacker.age;
	}

	/** Arcs from {@code target} and lets every mob it shocks roll to arc on; see {@link #arc(LivingEntity, LivingEntity, float, Random)}. */
	public static List<LivingEntity> arc(LivingEntity attacker, LivingEntity target, float damage) {
		return arc(attacker, target, damage, attacker.getRandom()::nextFloat);
	}

	/** Where the chain's rolls come from (tests pass a scripted one). */
	@FunctionalInterface
	public interface Random {
		float nextFloat();
	}

	/**
	 * Shocks every mob within the configured radius of {@code target} for {@code damage} × the multiplier.
	 * Each mob it shocks then rolls the same chance ({@link #chanceFor}) to arc again from itself, with no limit on how far the
	 * chain runs; a mob is shocked at most once per chain, so it always ends. Every link deals 150% of the
	 * original hit (it doesn't compound). Returns every mob the chain hit, in order.
	 */
	public static List<LivingEntity> arc(LivingEntity attacker, LivingEntity target, float damage, Random random) {
		if (!(target.getWorld() instanceof ServerWorld world)) {
			return List.of();
		}
		FrostboundConfig.ConductivityOptions config = FrostboundConfig.get().conductivity;
		float chance = chanceFor(attacker);
		DamageSource shock = world.getDamageSources().create(DAMAGE_TYPE, attacker);
		Set<LivingEntity> touched = new HashSet<>(List.of(attacker, target));
		List<LivingEntity> hit = new ArrayList<>();
		Deque<LivingEntity> sources = new ArrayDeque<>(List.of(target));
		boolean first = true;
		while (!sources.isEmpty()) {
			LivingEntity from = sources.poll();
			// The struck mob already passed its roll; every shocked mob rolls for itself.
			if (!first && random.nextFloat() >= chance) {
				continue;
			}
			first = false;
			Box area = from.getBoundingBox().expand(config.radius);
			List<LivingEntity> nearby = world.getEntitiesByClass(LivingEntity.class, area,
					other -> !touched.contains(other) && canShock(attacker, other));
			if (nearby.isEmpty()) {
				continue;
			}
			Vec3d origin = from.getPos().add(0.0, from.getHeight() * 0.6, 0.0);
			for (LivingEntity other : nearby) {
				touched.add(other);
				hit.add(other);
				sources.add(other);
				other.damage(shock, damage * config.damageMultiplier);
				sparks(world, origin, other.getPos().add(0.0, other.getHeight() * 0.6, 0.0));
			}
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, origin.x, origin.y, origin.z, 12, 0.3, 0.3, 0.3, 0.1);
			world.playSound(null, from.getX(), from.getY(), from.getZ(), SoundEvents.BLOCK_COPPER_BULB_TURN_ON,
					SoundCategory.PLAYERS, 1.0f, 1.4f);
			world.playSound(null, from.getX(), from.getY(), from.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,
					SoundCategory.PLAYERS, 0.35f, 1.8f);
		}
		return hit;
	}

	/**
	 * Who an arc can reach: living mobs, not the attacker, its pets or teammates, nor armour stands
	 * or spectators. When a player swings, other players are spared (no friendly fire in co-op).
	 */
	private static boolean canShock(LivingEntity attacker, LivingEntity other) {
		if (other == attacker || !other.isAlive() || other.isSpectator() || other instanceof ArmorStandEntity
				|| attacker.isTeammate(other)) {
			return false;
		}
		if (other instanceof Tameable pet && attacker.getUuid().equals(pet.getOwnerUuid())) {
			return false;
		}
		if (other instanceof PlayerEntity player) {
			return !(attacker instanceof PlayerEntity) && !player.isCreative();
		}
		return true;
	}

	/** A jagged line of sparks from the target to a shocked mob. */
	private static void sparks(ServerWorld world, Vec3d from, Vec3d to) {
		Vec3d step = to.subtract(from);
		int count = Math.max(2, (int) (step.length() * 4));
		for (int i = 0; i <= count; i++) {
			Vec3d at = from.add(step.multiply(i / (double) count));
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	/** For tests: true if {@code entity} counts as one an arc from {@code attacker} could reach. */
	public static boolean wouldShock(LivingEntity attacker, Entity entity) {
		return entity instanceof LivingEntity living && canShock(attacker, living);
	}
}

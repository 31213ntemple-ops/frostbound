package com.frostbound.test;

import com.frostbound.FrostStats;
import com.frostbound.Frostbound;
import com.frostbound.FrozenMob;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Automated checks for Frostbound. Run with: gradlew runGametest
 *
 * Each method is one test. It gets a small empty area to spawn mobs in, and must
 * call ctx.complete() to pass (or throw to fail). Mobs spawned with
 * ctx.spawnEntity don't run initialize(), so the random spawn roll never
 * interferes: a mob is only frozen when a test freezes it.
 *
 * To add a test: copy one of these methods, rename it, change the body.
 */
public class FrozenMobTests implements FabricGameTest {
	private static final BlockPos POS = new BlockPos(1, 2, 1);
	private static final double EPSILON = 1.0e-4;

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void frozenStatsApplied(TestContext ctx) {
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, POS);
		double baseSpeed = zombie.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		freeze(zombie);

		assertNear(20.0 * (1 + FrostStats.HEALTH_BONUS), zombie.getMaxHealth(), "max health");
		assertNear(zombie.getMaxHealth(), zombie.getHealth(), "spawns at full health");
		assertNear(baseSpeed * (1 + FrostStats.SPEED_PENALTY),
				zombie.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), "movement speed");
		assertNear(FrostStats.KNOCKBACK_RESIST,
				zombie.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE), "knockback resistance");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void unfreezingRemovesStats(TestContext ctx) {
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, POS);
		freeze(zombie);
		((FrozenMob) zombie).frostbound$setFrozen(false);

		check(!FrozenMob.isFrozen(zombie), "zombie should no longer be frozen");
		assertNear(20.0, zombie.getMaxHealth(), "max health back to normal");
		assertNear(20.0, zombie.getHealth(), "health clamped to new max");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void frozenFlagSavesAndLoads(TestContext ctx) {
		ZombieEntity original = ctx.spawnEntity(EntityType.ZOMBIE, POS);
		freeze(original);
		NbtCompound nbt = original.writeNbt(new NbtCompound());
		check(nbt.getBoolean("Frostbound"), "NBT should contain Frostbound:1b");

		ZombieEntity reloaded = EntityType.ZOMBIE.create(ctx.getWorld());
		check(reloaded != null, "could not create zombie");
		reloaded.readNbt(nbt);
		check(FrozenMob.isFrozen(reloaded), "reloaded zombie should still be frozen");
		assertNear(original.getMaxHealth(), reloaded.getMaxHealth(), "reloaded max health (no double bonus)");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void frozenAttackerDealsBonusDamageAndFrostbite(TestContext ctx) {
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, POS);
		freeze(zombie);
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, POS.east(2));

		pig.damage(ctx.getWorld().getDamageSources().mobAttack(zombie), 4.0f);

		assertNear(10.0 - 4.0 * FrostStats.DAMAGE_MULTIPLIER, pig.getHealth(), "pig health after frozen hit");
		check(pig.hasStatusEffect(StatusEffects.SLOWNESS), "victim should get Slowness");
		check(pig.getFrozenTicks() > 0, "victim should get frosted (frozen ticks)");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void normalAttackerDealsNormalDamage(TestContext ctx) {
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, POS);
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, POS.east(2));

		pig.damage(ctx.getWorld().getDamageSources().mobAttack(zombie), 4.0f);

		assertNear(6.0, pig.getHealth(), "pig health after normal hit");
		check(!pig.hasStatusEffect(StatusEffects.SLOWNESS), "no frostbite from a normal mob");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void frozenMobTakesDoubleFireDamage(TestContext ctx) {
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, POS);
		freeze(pig);
		float before = pig.getHealth();

		pig.damage(ctx.getWorld().getDamageSources().inFire(), 1.0f);

		assertNear(before - 1.0f * FrostStats.FIRE_WEAKNESS, pig.getHealth(), "health after fire damage");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void frozenMobDropsIce(TestContext ctx) {
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, POS);
		freeze(pig);
		Vec3d at = pig.getPos();

		pig.damage(ctx.getWorld().getDamageSources().generic(), 1000.0f);

		boolean droppedIce = ctx.getWorld()
				.getEntitiesByClass(ItemEntity.class, new Box(at, at).expand(3), e -> e.getStack().isOf(Items.ICE))
				.size() > 0;
		check(droppedIce, "frozen mob should always drop at least 1 ice");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void spawnTagContainsExpectedMobs(TestContext ctx) {
		check(EntityType.ZOMBIE.isIn(Frostbound.CAN_BE_FROZEN), "zombie should be in can_be_frozen");
		check(EntityType.CREEPER.isIn(Frostbound.CAN_BE_FROZEN), "creeper should be in can_be_frozen");
		check(!EntityType.COW.isIn(Frostbound.CAN_BE_FROZEN), "cow should NOT be in can_be_frozen");
		ctx.complete();
	}

	// ---- helpers ----

	private static void freeze(MobEntity mob) {
		((FrozenMob) mob).frostbound$setFrozen(true);
		check(FrozenMob.isFrozen(mob), mob.getType().getUntranslatedName() + " should be frozen");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}

	private static void assertNear(double expected, double actual, String what) {
		if (Math.abs(expected - actual) > EPSILON) {
			throw new GameTestException(what + ": expected " + expected + " but was " + actual);
		}
	}
}

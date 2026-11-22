package com.frostbound.test;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import com.frostbound.items.FrostboundItems;
import com.frostbound.items.IceBallEntity;
import com.frostbound.mobs.FrozenMob;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Ice balls: thrown for real (spawned above the target and dropped onto it), so these go
 * through the same collision code as a player's throw.
 */
public class IceBallTests implements FabricGameTest {
	private static final BlockPos MOB_POS = new BlockPos(3, 2, 3);
	private static final double EPSILON = 1.0e-4;

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void iceBallDamagesSlowsAndFrosts(TestContext ctx) {
		PigEntity pig = target(ctx, EntityType.PIG);
		FrostboundConfig.IceBalls config = FrostboundConfig.get().iceBalls;
		dropBallOn(ctx, pig.getPos());

		succeedWhen(ctx, () -> {
			check(pig.getHealth() < pig.getMaxHealth(), "pig should be hit by the ice ball");
			assertNear(pig.getMaxHealth() - config.damage, pig.getHealth(), "pig health");
			StatusEffectInstance slow = pig.getStatusEffect(StatusEffects.SLOWNESS);
			check(slow != null, "pig should get Slowness");
			check(slow.getAmplifier() == config.slownessLevel - 1, "Slowness level should be " + config.slownessLevel);
			check(pig.getFrozenTicks() > 0, "pig should be frosted");
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void blazesTakeExtraDamage(TestContext ctx) {
		BlazeEntity blaze = target(ctx, EntityType.BLAZE);
		FrostboundConfig.IceBalls config = FrostboundConfig.get().iceBalls;
		dropBallOn(ctx, blaze.getPos());

		succeedWhen(ctx, () -> {
			check(blaze.getHealth() < blaze.getMaxHealth(), "blaze should be hit by the ice ball");
			assertNear(blaze.getMaxHealth() - config.damage * config.weakToIceMultiplier, blaze.getHealth(),
					"blaze health");
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void frozenMobsShrugOffTheSlowness(TestContext ctx) {
		PigEntity pig = target(ctx, EntityType.PIG);
		((FrozenMob) pig).frostbound$setFrozen(true);
		float before = pig.getHealth();
		dropBallOn(ctx, pig.getPos());

		succeedWhen(ctx, () -> {
			check(pig.getHealth() < before, "frozen pig should still take damage");
			check(!pig.hasStatusEffect(StatusEffects.SLOWNESS), "frozen pig should not be slowed");
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void iceBallPutsOutBurningTarget(TestContext ctx) {
		PigEntity pig = target(ctx, EntityType.PIG);
		pig.setOnFireFor(30);
		dropBallOn(ctx, pig.getPos());

		succeedWhen(ctx, () -> {
			check(pig.hasStatusEffect(StatusEffects.SLOWNESS), "pig should be hit by the ice ball");
			check(!pig.isOnFire(), "the hit should put the pig out");
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void iceBallFreezesWater(TestContext ctx) {
		// A 5x5 pool (x/z 1..5) inside a stone basin.
		for (BlockPos pos : BlockPos.iterate(0, 0, 0, 6, 1, 6)) {
			boolean pool = pos.getY() == 1 && pos.getX() >= 1 && pos.getX() <= 5 && pos.getZ() >= 1 && pos.getZ() <= 5;
			ctx.setBlockState(pos, pool ? Blocks.WATER : Blocks.STONE);
		}
		dropBallOn(ctx, ctx.getAbsolute(new Vec3d(3.5, 1.9, 3.5)));

		int radius = FrostboundConfig.get().iceBalls.freezeWaterRadius;
		succeedWhen(ctx, () -> {
			ctx.expectBlock(Blocks.FROSTED_ICE, new BlockPos(3, 1, 3));
			ctx.expectBlock(Blocks.FROSTED_ICE, new BlockPos(3 - radius, 1, 3));
			ctx.expectBlock(Blocks.FROSTED_ICE, new BlockPos(3, 1, 3 + radius));
			ctx.expectBlock(Blocks.WATER, new BlockPos(1, 1, 1)); // corner is outside the disc
			check(ctx.getWorld().getEntitiesByClass(IceBallEntity.class, area(ctx), e -> true).isEmpty(),
					"the ice ball should shatter on the water");
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void iceBallPutsOutFire(TestContext ctx) {
		BlockPos ground = new BlockPos(3, 1, 3);
		ctx.setBlockState(ground, Blocks.NETHERRACK);
		ctx.setBlockState(ground.up(), Blocks.FIRE);
		dropBallOn(ctx, ctx.getAbsolute(new Vec3d(3.5, 2.0, 3.5)));

		succeedWhen(ctx, () -> ctx.expectBlock(Blocks.AIR, ground.up()));
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void iceBallPutsOutCampfire(TestContext ctx) {
		BlockPos campfire = new BlockPos(3, 1, 3);
		ctx.setBlockState(campfire, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true));
		dropBallOn(ctx, ctx.getAbsolute(new Vec3d(3.5, 1.5, 3.5)));

		succeedWhen(ctx, () -> check(!ctx.getBlockState(campfire).get(CampfireBlock.LIT), "campfire should go out"));
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void dispensersFireIceBalls(TestContext ctx) {
		BlockPos dispenserPos = new BlockPos(3, 1, 3);
		ctx.setBlockState(dispenserPos, Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING, Direction.UP));
		DispenserBlockEntity dispenser = ctx.getBlockEntity(dispenserPos);
		dispenser.addToFirstFreeSlot(new ItemStack(FrostboundItems.ICE_BALL));
		ctx.setBlockState(dispenserPos.east(), Blocks.REDSTONE_BLOCK);

		ServerWorld world = ctx.getWorld();
		Box around = area(ctx).expand(0, 16, 0);
		succeedWhen(ctx, () -> {
			check(dispenser.isEmpty(), "dispenser should have used the ice ball");
			check(!world.getEntitiesByClass(IceBallEntity.class, around, e -> true).isEmpty(),
					"dispenser should shoot an ice ball");
			check(world.getEntitiesByClass(ItemEntity.class, around, e -> e.getStack().isOf(FrostboundItems.ICE_BALL))
					.isEmpty(), "dispenser should shoot it, not drop it");
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void recipeAndTagsLoad(TestContext ctx) {
		check(ctx.getWorld().getRecipeManager().get(Frostbound.id("ice_ball")).isPresent(), "ice ball recipe missing");
		check(EntityType.BLAZE.isIn(FrostboundItems.WEAK_TO_ICE), "blaze should be in weak_to_ice");
		check(!EntityType.PIG.isIn(FrostboundItems.WEAK_TO_ICE), "pig should NOT be in weak_to_ice");
		ctx.complete();
	}

	// ---- helpers ----

	/** Re-runs the checks every tick and passes the first time they all hold (fails on timeout). */
	private static void succeedWhen(TestContext ctx, Runnable checks) {
		ctx.runAtEveryTick(() -> {
			try {
				checks.run();
			} catch (GameTestException notYet) {
				return;
			}
			ctx.complete();
		});
	}

	/** A mob that stays put in mid-air (no AI, no gravity) so the ball can't miss. */
	private static <T extends MobEntity> T target(TestContext ctx, EntityType<T> type) {
		T mob = ctx.spawnEntity(type, MOB_POS);
		mob.setAiDisabled(true);
		mob.setNoGravity(true);
		return mob;
	}

	/** Spawns an ice ball 3 blocks above an absolute position, falling straight down onto it. */
	private static void dropBallOn(TestContext ctx, Vec3d target) {
		ServerWorld world = ctx.getWorld();
		IceBallEntity ball = new IceBallEntity(world, target.x, target.y + 3, target.z);
		ball.setVelocity(0, -0.6, 0);
		world.spawnEntity(ball);
	}

	private static Box area(TestContext ctx) {
		BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
		return new Box(origin).stretch(8, 8, 8).expand(2);
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

package com.frostbound.alpha;

import com.frostbound.config.FrostboundConfig;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * The pack's AI. The fight is built from a few readable behaviours rather than raw numbers:
 *
 *  - {@link LungeGoal}: hit and run. A wolf takes one of the pack's few turns, closes in, stops and
 *    growls with its head cocked (the tell), lunges for one bite, then gives its turn back and falls away.
 *  - {@link CircleGoal}: everyone without a turn circles the target at a distance, so the pack
 *    surrounds you without all biting at once.
 *  - {@link CallOfThePackGoal}: the Alpha sits back and howls; hit it hard enough to cut it off.
 *  - {@link ScatterGoal}: with the Alpha dead, run from the player and vanish.
 *  - {@link AlphaTargetGoal} / {@link FollowAlphaGoal}: pack wolves fight what the Alpha fights and keep near it.
 */
public final class PackGoals {
	/** The pack gives up on anyone further than this. */
	public static final double MAX_TARGET_DISTANCE = 48.0;

	private PackGoals() {}

	/** Someone the pack will go on fighting: alive, close enough, not in creative or spectator, and not a pack wolf. */
	public static boolean isValidTarget(PackWolfEntity wolf, LivingEntity target) {
		if (!target.isAlive() || target.isRemoved() || target.getWorld() != wolf.getWorld() || target instanceof PackWolfEntity) {
			return false;
		}
		if (target instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) {
			return false;
		}
		return wolf.squaredDistanceTo(target) <= MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE;
	}

	/** Hit and run: one lunge per turn, a short tell before it, and only a few wolves at a time. */
	public static final class LungeGoal extends Goal {
		private enum Phase { APPROACH, WINDUP, LUNGE, DONE }

		/** Within this distance a wolf stops to wind up instead of running further in. */
		private static final double WINDUP_RANGE = 3.2;
		private static final int MAX_APPROACH_TICKS = 100;
		private static final int MAX_LUNGE_TICKS = 16;

		private final PackWolfEntity wolf;
		private LivingEntity target;
		private Phase phase = Phase.DONE;
		private int phaseTicks;

		public LungeGoal(PackWolfEntity wolf) {
			this.wolf = wolf;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
		}

		@Override
		public boolean canStart() {
			LivingEntity target = this.wolf.getTarget();
			if (target == null || this.wolf.isScattering() || this.wolf.lungeCooldown > 0 || !isValidTarget(this.wolf, target)) {
				return false;
			}
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			return alpha == null || alpha.canClaimLunge(this.wolf);
		}

		@Override
		public boolean shouldContinue() {
			return this.phase != Phase.DONE && this.target != null && isValidTarget(this.wolf, this.target) && !this.wolf.isScattering();
		}

		@Override
		public void start() {
			this.target = this.wolf.getTarget();
			this.wolf.lunging = true;
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			if (alpha != null) {
				alpha.tryClaimLunge(this.wolf);
			}
			this.enter(Phase.APPROACH);
			this.wolf.getNavigation().startMovingTo(this.target, 1.35);
		}

		@Override
		public void stop() {
			this.wolf.lunging = false;
			this.wolf.setBegging(false);
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			if (alpha != null) {
				alpha.releaseLunge(this.wolf);
			}
			// Fall back and let the next wolf have a go; the Alpha waits a little longer between lunges.
			this.wolf.lungeCooldown = (this.wolf instanceof AlphaWolfEntity ? 50 : 40) + this.wolf.getRandom().nextInt(40);
			this.wolf.getNavigation().stop();
			this.phase = Phase.DONE;
			this.target = null;
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		private void enter(Phase phase) {
			this.phase = phase;
			this.phaseTicks = 0;
		}

		@Override
		public void tick() {
			this.phaseTicks++;
			this.wolf.getLookControl().lookAt(this.target, 30.0f, 30.0f);
			switch (this.phase) {
				case APPROACH -> {
					double distance = this.wolf.squaredDistanceTo(this.target);
					if (distance <= WINDUP_RANGE * WINDUP_RANGE || this.wolf.isInAttackRange(this.target)) {
						this.windUp();
					} else if (this.phaseTicks > MAX_APPROACH_TICKS) {
						this.phase = Phase.DONE;
					} else if (this.phaseTicks % 5 == 0) {
						this.wolf.getNavigation().startMovingTo(this.target, 1.35);
					}
				}
				case WINDUP -> {
					this.wolf.getNavigation().stop();
					if (this.phaseTicks >= FrostboundConfig.get().alphaWolf.lungeWindupTicks) {
						this.leap();
					}
				}
				case LUNGE -> {
					if (this.wolf.isInAttackRange(this.target)) {
						this.wolf.tryAttack(this.target);
						this.wolf.swingHand(net.minecraft.util.Hand.MAIN_HAND);
						this.phase = Phase.DONE;
					} else if (this.phaseTicks > MAX_LUNGE_TICKS) {
						this.phase = Phase.DONE; // missed: dodged or out of reach
					} else {
						this.wolf.getNavigation().startMovingTo(this.target, 1.6);
					}
				}
				case DONE -> {
				}
			}
		}

		/** The tell: stop, cock the head, growl, and dig in (snow kicks up from under its paws). */
		private void windUp() {
			this.enter(Phase.WINDUP);
			this.wolf.getNavigation().stop();
			this.wolf.setBegging(true);
			this.wolf.playSound(SoundEvents.ENTITY_WOLF_GROWL, 1.2f, this.wolf instanceof AlphaWolfEntity ? 0.6f : 1.0f);
			if (this.wolf.getWorld() instanceof ServerWorld world) {
				BlockState ground = world.getBlockState(this.wolf.getSteppingPos());
				if (!ground.isAir()) {
					world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), this.wolf.getX(), this.wolf.getY() + 0.1,
							this.wolf.getZ(), 8, this.wolf.getWidth() * 0.4, 0.05, this.wolf.getWidth() * 0.4, 0.1);
				}
			}
		}

		private void leap() {
			this.enter(Phase.LUNGE);
			this.wolf.setBegging(false);
			if (this.wolf.isOnGround()) {
				Vec3d toward = new Vec3d(this.target.getX() - this.wolf.getX(), 0.0, this.target.getZ() - this.wolf.getZ());
				if (toward.lengthSquared() > 1.0E-7) {
					toward = toward.normalize().multiply(0.55).add(this.wolf.getVelocity().multiply(0.2));
				}
				this.wolf.setVelocity(toward.x, 0.32, toward.z);
			}
			this.wolf.getNavigation().startMovingTo(this.target, 1.6);
		}
	}

	/** Without a turn to lunge, circle the target at a distance, facing it. */
	public static final class CircleGoal extends Goal {
		private final PackWolfEntity wolf;
		private LivingEntity target;
		private double angle;
		private double radius;
		private int direction;

		public CircleGoal(PackWolfEntity wolf) {
			this.wolf = wolf;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			LivingEntity target = this.wolf.getTarget();
			return target != null && !this.wolf.isScattering() && isValidTarget(this.wolf, target);
		}

		@Override
		public boolean shouldContinue() {
			return this.canStart() && this.wolf.getTarget() == this.target;
		}

		@Override
		public void start() {
			this.target = this.wolf.getTarget();
			this.angle = Math.atan2(this.wolf.getZ() - this.target.getZ(), this.wolf.getX() - this.target.getX());
			this.direction = this.wolf.getRandom().nextBoolean() ? 1 : -1;
			// The Alpha hangs back a little further than its wolves.
			this.radius = (this.wolf instanceof AlphaWolfEntity ? 6.5 : 4.5) + this.wolf.getRandom().nextDouble() * 2.0;
		}

		@Override
		public void stop() {
			this.target = null;
			this.wolf.getNavigation().stop();
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			this.wolf.getLookControl().lookAt(this.target, 30.0f, 30.0f);
			this.angle += this.direction * 0.05;
			if (this.wolf.age % 8 != 0) {
				return;
			}
			Vec3d point = this.target.getPos().add(Math.cos(this.angle) * this.radius, 0.0, Math.sin(this.angle) * this.radius);
			double speed = this.wolf.squaredDistanceTo(this.target) > 144.0 ? 1.3 : 0.95;
			if (!this.wolf.getNavigation().startMovingTo(point.x, point.y, point.z, speed)) {
				this.direction = -this.direction; // blocked: circle the other way
			}
		}
	}

	/** Pack wolves fight whatever their Alpha is fighting. */
	public static final class AlphaTargetGoal extends Goal {
		private final PackWolfEntity wolf;

		public AlphaTargetGoal(PackWolfEntity wolf) {
			this.wolf = wolf;
			this.setControls(EnumSet.of(Control.TARGET));
		}

		@Override
		public boolean canStart() {
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			if (alpha == null || alpha == this.wolf || this.wolf.isScattering()) {
				return false;
			}
			LivingEntity theirs = alpha.getTarget();
			return theirs != null && theirs != this.wolf.getTarget() && isValidTarget(this.wolf, theirs);
		}

		@Override
		public void start() {
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			if (alpha != null) {
				this.wolf.setTarget(alpha.getTarget());
			}
		}

		@Override
		public boolean shouldContinue() {
			return false;
		}
	}

	/** Out of a fight, keep near the Alpha. */
	public static final class FollowAlphaGoal extends Goal {
		private final PackWolfEntity wolf;
		private AlphaWolfEntity alpha;

		public FollowAlphaGoal(PackWolfEntity wolf) {
			this.wolf = wolf;
			this.setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			this.alpha = this.wolf.getAlpha();
			return this.alpha != null && this.alpha != this.wolf && this.wolf.getTarget() == null && !this.wolf.isScattering()
					&& this.wolf.squaredDistanceTo(this.alpha) > 64.0;
		}

		@Override
		public boolean shouldContinue() {
			return this.alpha != null && this.alpha.isAlive() && this.wolf.getTarget() == null && !this.wolf.isScattering()
					&& this.wolf.squaredDistanceTo(this.alpha) > 16.0 && !this.wolf.getNavigation().isIdle();
		}

		@Override
		public void start() {
			this.wolf.getNavigation().startMovingTo(this.alpha, 1.1);
		}

		@Override
		public void stop() {
			this.alpha = null;
		}
	}

	/** The Alpha sits back and howls; unless cut off, the pack answers ({@link AlphaWolfEntity#answerCall}). */
	public static final class CallOfThePackGoal extends Goal {
		private final AlphaWolfEntity alpha;
		private int ticks;
		private boolean answered;

		public CallOfThePackGoal(AlphaWolfEntity alpha) {
			this.alpha = alpha;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
		}

		@Override
		public boolean canStart() {
			LivingEntity target = this.alpha.getTarget();
			return target != null && this.alpha.callCooldown <= 0 && this.alpha.isOnGround() && !this.alpha.isScattering()
					&& isValidTarget(this.alpha, target);
		}

		@Override
		public boolean shouldContinue() {
			return !this.answered && !this.alpha.callInterrupted && this.alpha.isAlive();
		}

		@Override
		public void start() {
			this.ticks = 0;
			this.answered = false;
			this.alpha.beginCall();
		}

		@Override
		public void stop() {
			this.alpha.endCall(this.answered);
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			this.ticks++;
			this.alpha.getNavigation().stop();
			// Snout to the sky.
			this.alpha.getLookControl().lookAt(this.alpha.getX() + this.alpha.getRotationVector().x, this.alpha.getEyeY() + 4.0,
					this.alpha.getZ() + this.alpha.getRotationVector().z);
			if (this.alpha.getWorld() instanceof ServerWorld world && this.ticks % 5 == 0) {
				// A ring of frost spreading out from the Alpha as it howls.
				double r = 0.6 + this.ticks * 0.06;
				for (int i = 0; i < 16; i++) {
					double a = i * Math.PI / 8.0;
					world.spawnParticles(ParticleTypes.SNOWFLAKE, this.alpha.getX() + Math.cos(a) * r, this.alpha.getY() + 0.2,
							this.alpha.getZ() + Math.sin(a) * r, 1, 0.0, 0.05, 0.0, 0.0);
				}
			}
			int windup = Math.max(1, AlphaWolfEntity.ticks(FrostboundConfig.get().alphaWolf.callOfThePack.windupSeconds));
			if (this.ticks >= windup) {
				this.alpha.answerCall();
				this.answered = true;
			}
		}
	}

	/** The Alpha is gone: run from the nearest player and vanish once clear (or after {@link PackWolfEntity#MAX_SCATTER_TICKS}). */
	public static final class ScatterGoal extends Goal {
		private final PackWolfEntity wolf;

		public ScatterGoal(PackWolfEntity wolf) {
			this.wolf = wolf;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
		}

		@Override
		public boolean canStart() {
			return this.wolf.isScattering();
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			this.wolf.scatterTicks++;
			PlayerEntity player = this.wolf.getWorld().getClosestPlayer(this.wolf, PackWolfEntity.ESCAPED_DISTANCE);
			boolean escaped = player == null && this.wolf.scatterTicks >= 40;
			if (escaped || this.wolf.scatterTicks >= PackWolfEntity.MAX_SCATTER_TICKS) {
				this.wolf.vanish();
				return;
			}
			if (player != null && (this.wolf.scatterTicks % 10 == 1 || this.wolf.getNavigation().isIdle())) {
				Vec3d away = NoPenaltyTargeting.findFrom(this.wolf, 16, 7, player.getPos());
				if (away != null) {
					this.wolf.getNavigation().startMovingTo(away.x, away.y, away.z, 1.5);
				}
			}
		}
	}
}

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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * The pack's AI. The fight is built from a few readable behaviours rather than raw numbers:
 *
 *  - {@link LungeGoal}: hit and run. A wolf takes one of the pack's few turns, closes in, drops into
 *    a crouch and growls (the tell), lunges for one bite, then gives its turn back and backs off.
 *  - {@link CircleGoal}: everyone without a turn circles the target at a distance, so the pack
 *    surrounds you without all biting at once.
 *  - {@link CallOfThePackGoal}: the Alpha sits back and howls; hit it hard enough to cut it off.
 *    {@link ChorusGoal}: the rest of the pack sits and howls with it (and doesn't bite meanwhile).
 *  - {@link ScatterGoal}: with the Alpha dead, run from the player and vanish.
 *  - {@link AlphaTargetGoal} / {@link FollowAlphaGoal}: pack wolves fight what the Alpha fights and keep near it.
 *  - {@link RestGoal} / {@link GoToBedGoal}: a den pack sleeps until attacked, and walks back to bed after.
 *
 * The Alpha's own big moves (Pounce, Charge, the Bloodied roar, going home) are in {@link AlphaGoals}.
 */
public final class PackGoals {
	/** The pack gives up on anyone further than this. */
	public static final double MAX_TARGET_DISTANCE = 48.0;

	private PackGoals() {}

	/**
	 * Someone the pack will go on fighting: alive, close enough, not in creative or spectator, and not a
	 * pack wolf. A den pack also gives up on anyone who gets far enough from its den (they go home).
	 */
	public static boolean isValidTarget(PackWolfEntity wolf, LivingEntity target) {
		if (!target.isAlive() || target.isRemoved() || target.getWorld() != wolf.getWorld() || target instanceof PackWolfEntity) {
			return false;
		}
		if (target instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) {
			return false;
		}
		BlockPos den = wolf.getDen();
		if (den != null) {
			double leash = FrostboundConfig.get().alphaWolf.den.leashRange;
			if (target.squaredDistanceTo(Vec3d.ofBottomCenter(den)) > leash * leash) {
				return false;
			}
		}
		return wolf.squaredDistanceTo(target) <= MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE;
	}

	/** Asleep or getting up: hold still (and keep everything else from running). */
	public static final class RestGoal extends Goal {
		private final PackWolfEntity wolf;

		public RestGoal(PackWolfEntity wolf) {
			this.wolf = wolf;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
		}

		@Override
		public boolean canStart() {
			return this.wolf.getAction().isResting();
		}

		@Override
		public void start() {
			this.wolf.getNavigation().stop();
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			this.wolf.getNavigation().stop();
			// Lying still: don't let the head keep turning towards whatever it last looked at.
			if (this.wolf.isSleeping()) {
				this.wolf.setHeadYaw(this.wolf.bodyYaw);
				this.wolf.setPitch(0.0f);
			}
		}
	}

	/**
	 * Hit and run: one lunge per turn, a clear tell before it, and only a few wolves at a time.
	 * APPROACH (run in) → WINDUP (crouch, growl, dig in: {@link WolfAction#CROUCH}) → LUNGE (leap for one bite:
	 * {@link WolfAction#LEAPING}) → RETREAT (bound back out, so the next wolf can come in).
	 */
	public static final class LungeGoal extends Goal {
		private enum Phase { APPROACH, WINDUP, LUNGE, RETREAT, DONE }

		/** Within this distance a wolf stops to wind up instead of running further in. */
		private static final double WINDUP_RANGE = 3.2;
		private static final int MAX_APPROACH_TICKS = 100;
		private static final int MAX_LUNGE_TICKS = 16;
		private static final int RETREAT_TICKS = 14;

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
			if (alpha == this.wolf && this.wolf.squaredDistanceTo(target) > 8.0 * 8.0) {
				return false; // the Alpha closes long gaps with a Pounce instead
			}
			return alpha == null || alpha.canClaimLunge(this.wolf);
		}

		@Override
		public boolean shouldContinue() {
			if (this.phase == Phase.RETREAT) {
				return this.phaseTicks < RETREAT_TICKS && !this.wolf.isScattering();
			}
			return this.phase != Phase.DONE && this.target != null && isValidTarget(this.wolf, this.target) && !this.wolf.isScattering();
		}

		/** Mid-leap a bite can't be called off. */
		@Override
		public boolean canStop() {
			return this.phase != Phase.LUNGE;
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
			if (this.wolf.getAction() == WolfAction.CROUCH || this.wolf.getAction() == WolfAction.LEAPING) {
				this.wolf.setAction(WolfAction.NONE);
			}
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			if (alpha != null) {
				alpha.releaseLunge(this.wolf);
			}
			// Let the next wolf have a go; the Alpha waits a little longer between bites.
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
			if (this.phase != Phase.RETREAT && this.target != null) {
				this.wolf.getLookControl().lookAt(this.target, 30.0f, 30.0f);
			}
			switch (this.phase) {
				case APPROACH -> {
					double distance = this.wolf.squaredDistanceTo(this.target);
					if (distance <= WINDUP_RANGE * WINDUP_RANGE || this.wolf.isInAttackRange(this.target)) {
						this.windUp();
					} else if (this.phaseTicks > MAX_APPROACH_TICKS || this.alphaNeedsRoom()) {
						this.phase = Phase.DONE;
					} else if (this.phaseTicks % 5 == 0) {
						this.wolf.getNavigation().startMovingTo(this.target, 1.35);
					}
				}
				case WINDUP -> {
					this.wolf.getNavigation().stop();
					// Keep facing the target through the crouch so the leap goes where it looks.
					this.faceTarget();
					if (this.phaseTicks % 6 == 0) {
						this.kickUpGround(3);
					}
					if (this.phaseTicks >= FrostboundConfig.get().alphaWolf.lungeWindupTicks) {
						this.leap();
					}
				}
				case LUNGE -> {
					if (this.wolf.isInAttackRange(this.target)) {
						this.wolf.tryAttack(this.target);
						this.wolf.swingHand(net.minecraft.util.Hand.MAIN_HAND);
						this.retreat();
					} else if (this.phaseTicks > MAX_LUNGE_TICKS) {
						this.retreat(); // missed: dodged or out of reach
					} else if (this.wolf.isOnGround() && this.phaseTicks > 3) {
						this.wolf.getNavigation().startMovingTo(this.target, 1.6);
					}
				}
				case RETREAT, DONE -> {
				}
			}
		}

		/** A pack wolf still running in backs off when its Alpha starts a big move. */
		private boolean alphaNeedsRoom() {
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			return alpha != null && alpha != this.wolf && alpha.isPerformingMove();
		}

		private void faceTarget() {
			double dx = this.target.getX() - this.wolf.getX();
			double dz = this.target.getZ() - this.wolf.getZ();
			float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
			this.wolf.setYaw(yaw);
			this.wolf.setBodyYaw(yaw);
		}

		/** Snow (or whatever it stands on) kicked up from under its paws. */
		private void kickUpGround(int count) {
			if (this.wolf.getWorld() instanceof ServerWorld world) {
				BlockState ground = world.getBlockState(this.wolf.getSteppingPos());
				if (!ground.isAir()) {
					world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), this.wolf.getX(), this.wolf.getY() + 0.1,
							this.wolf.getZ(), count, this.wolf.getWidth() * 0.4, 0.05, this.wolf.getWidth() * 0.4, 0.1);
				}
			}
		}

		/** The tell: stop, drop into a crouch, growl, and dig in. */
		private void windUp() {
			this.enter(Phase.WINDUP);
			this.wolf.getNavigation().stop();
			this.wolf.setAction(WolfAction.CROUCH);
			this.wolf.playSound(SoundEvents.ENTITY_WOLF_GROWL, 1.2f, this.wolf instanceof AlphaWolfEntity ? 0.6f : 1.0f);
			this.kickUpGround(8);
		}

		private void leap() {
			this.enter(Phase.LUNGE);
			this.wolf.setAction(WolfAction.LEAPING);
			this.faceTarget();
			if (this.wolf.isOnGround()) {
				Vec3d toward = new Vec3d(this.target.getX() - this.wolf.getX(), 0.0, this.target.getZ() - this.wolf.getZ());
				if (toward.lengthSquared() > 1.0E-7) {
					toward = toward.normalize().multiply(0.55).add(this.wolf.getVelocity().multiply(0.2));
				}
				this.wolf.setVelocity(toward.x, 0.32, toward.z);
				this.wolf.velocityDirty = true;
			}
		}

		/** After the bite: bound back out of reach and give the turn up. */
		private void retreat() {
			this.enter(Phase.RETREAT);
			this.wolf.setAction(WolfAction.NONE);
			this.wolf.lunging = false;
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			if (alpha != null) {
				alpha.releaseLunge(this.wolf);
			}
			if (this.target != null) {
				Vec3d away = NoPenaltyTargeting.findFrom(this.wolf, 6, 3, this.target.getPos());
				if (away != null) {
					this.wolf.getNavigation().startMovingTo(away.x, away.y, away.z, 1.4);
				}
			}
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
			if (alpha == null || alpha == this.wolf || this.wolf.isScattering() || this.wolf.getAction().isResting()) {
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
					&& !this.alpha.isGoingHome() && !this.alpha.getAction().isResting() && this.wolf.squaredDistanceTo(this.alpha) > 64.0;
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
					&& !this.alpha.isPerformingMove() && !this.alpha.pendingRoar && isValidTarget(this.alpha, target);
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

	/** While the Alpha howls, the rest of the pack sits back and howls with it (one after another, not in unison). */
	public static final class ChorusGoal extends Goal {
		private final PackWolfEntity wolf;
		private int delay;

		public ChorusGoal(PackWolfEntity wolf) {
			this.wolf = wolf;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
		}

		@Override
		public boolean canStart() {
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			return alpha != null && alpha != this.wolf && alpha.isCalling() && !this.wolf.lunging && !this.wolf.isScattering()
					&& this.wolf.isOnGround() && !this.wolf.getAction().isResting();
		}

		@Override
		public boolean shouldContinue() {
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			return alpha != null && alpha.isCalling() && !this.wolf.isScattering();
		}

		@Override
		public void start() {
			this.delay = 4 + this.wolf.getRandom().nextInt(14);
			this.wolf.getNavigation().stop();
		}

		@Override
		public void stop() {
			this.wolf.setInSittingPose(false);
			if (this.wolf.getAction() == WolfAction.CHORUS) {
				this.wolf.setAction(WolfAction.NONE);
			}
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			this.wolf.getNavigation().stop();
			if (this.delay > 0 && --this.delay == 0) {
				this.wolf.setInSittingPose(true);
				this.wolf.setAction(WolfAction.CHORUS);
				this.wolf.playSound(SoundEvents.ENTITY_WOLF_HOWL, 1.0f, 1.0f + this.wolf.getRandom().nextFloat() * 0.3f);
			}
			if (this.wolf.getAction() == WolfAction.CHORUS) {
				this.wolf.getLookControl().lookAt(this.wolf.getX() + this.wolf.getRotationVector().x, this.wolf.getEyeY() + 4.0,
						this.wolf.getZ() + this.wolf.getRotationVector().z);
			}
		}
	}

	/** A den wolf whose Alpha has gone home (or is asleep) walks to its own spot and lies down. */
	public static final class GoToBedGoal extends Goal {
		/** Give up walking after this long and just lie down where it is. */
		private static final int MAX_TICKS = 300;

		private final PackWolfEntity wolf;
		private BlockPos spot;
		private int ticks;

		public GoToBedGoal(PackWolfEntity wolf) {
			this.wolf = wolf;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			if (this.wolf.getDen() == null || this.wolf.getTarget() != null || this.wolf.isScattering() || this.wolf.getAction().isResting()) {
				return false;
			}
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			return alpha != null && alpha != this.wolf && (alpha.isGoingHome() || alpha.isSleeping());
		}

		@Override
		public boolean shouldContinue() {
			return this.wolf.getTarget() == null && !this.wolf.isScattering() && !this.wolf.getAction().isResting();
		}

		@Override
		public void start() {
			this.ticks = 0;
			BlockPos bed = this.wolf.getBed();
			if (bed == null) {
				// A wolf that joined mid-fight finds a spot of its own near the den.
				BlockPos den = this.wolf.getDen();
				double angle = this.wolf.getRandom().nextDouble() * Math.PI * 2.0;
				double r = 3.5 + this.wolf.getRandom().nextDouble() * 2.5;
				bed = BlockPos.ofFloored(den.getX() + 0.5 + Math.cos(angle) * r, den.getY(), den.getZ() + 0.5 + Math.sin(angle) * r);
				this.wolf.setDen(den, bed);
			}
			this.spot = bed;
			this.wolf.getNavigation().startMovingTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 0.9);
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			this.ticks++;
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			double distance = this.wolf.squaredDistanceTo(Vec3d.ofBottomCenter(this.spot));
			boolean arrived = distance < 1.2 * 1.2 || (distance < 3.0 * 3.0 && this.wolf.getNavigation().isIdle());
			if ((arrived || this.ticks > MAX_TICKS) && alpha != null && alpha.isSleeping() && this.wolf.isOnGround()) {
				this.wolf.fallAsleep();
				return;
			}
			if (!arrived && this.ticks % 20 == 0 && this.wolf.getNavigation().isIdle()) {
				this.wolf.getNavigation().startMovingTo(this.spot.getX() + 0.5, this.spot.getY(), this.spot.getZ() + 0.5, 0.9);
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

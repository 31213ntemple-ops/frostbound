package com.frostbound.alpha;

import com.frostbound.config.FrostboundConfig;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * The pack's AI. The fight is built from a few readable behaviours rather than raw numbers:
 *
 *  - {@link LungeGoal}: hit and run. A wolf takes one of the pack's few turns (the Alpha hands them
 *    to whoever is furthest behind you), closes in, drops into a crouch and growls (the tell), lunges
 *    for one bite, then gives its turn back and backs off. Stare it down as it runs in and it breaks off.
 *  - {@link CircleGoal}: everyone without a turn keeps a spot in an even ring round you that turns
 *    slowly, creeping with its head low, darting in now and then with a snap (a feint), so the pack
 *    surrounds you and keeps up the pressure without all biting at once.
 *  - {@link PincerGoal}: sent in by the Alpha's bark, two wolves on opposite sides crouch, then leap
 *    one after the other (the Alpha's side is {@link AlphaGoals.PincerGoal}).
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
	 * pack wolf. A den pack isn't tied to its den: it chases you as far as any pack, and only goes home
	 * once you've got away.
	 */
	public static boolean isValidTarget(PackWolfEntity wolf, LivingEntity target) {
		if (!target.isAlive() || target.isRemoved() || target.getWorld() != wolf.getWorld() || target instanceof PackWolfEntity) {
			return false;
		}
		if (target instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) {
			return false;
		}
		return wolf.squaredDistanceTo(target) <= MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE;
	}

	/**
	 * Vanilla target goals let go of a target past the mob's follow range (24 for a pack wolf), which
	 * would shake the pack off well before it means to give up. These keep chasing out to
	 * {@link #MAX_TARGET_DISTANCE}, like {@link #isValidTarget}.
	 */
	public static final class PackRevengeGoal extends RevengeGoal {
		public PackRevengeGoal(PackWolfEntity wolf) {
			super(wolf);
		}

		@Override
		protected double getFollowRange() {
			return MAX_TARGET_DISTANCE;
		}
	}

	/** Spots players within the follow range as usual, but once on one keeps after them out to {@link #MAX_TARGET_DISTANCE}. */
	public static final class PackTargetGoal extends ActiveTargetGoal<PlayerEntity> {
		/** False while the vanilla constructor sizes the spotting range from {@link #getFollowRange}. */
		private final boolean built;

		public PackTargetGoal(PackWolfEntity wolf, Predicate<LivingEntity> predicate) {
			super(wolf, PlayerEntity.class, true, predicate);
			this.built = true;
		}

		@Override
		protected double getFollowRange() {
			return this.built ? MAX_TARGET_DISTANCE : super.getFollowRange();
		}
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

	/** Dust for the Pincer's tell: a pale line from each wolf to where it will land (brighter once it stops following you). */
	static final DustParticleEffect PINCER_LINE = new DustParticleEffect(new Vector3f(0.82f, 0.9f, 1.0f), 0.9f);
	static final DustParticleEffect PINCER_LOCKED = new DustParticleEffect(new Vector3f(0.95f, 0.98f, 1.0f), 1.3f);

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
		private static final int RETREAT_TICKS = 10;

		private final PackWolfEntity wolf;
		private LivingEntity target;
		private Phase phase = Phase.DONE;
		private int phaseTicks;
		/** It broke off its run because the player stared it down; and ticks in a row it's been looked at. */
		private boolean staredDown;
		private int watchedTicks;

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
			if (alpha == this.wolf && this.wolf.squaredDistanceTo(target) > 9.0 * 9.0) {
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
			this.staredDown = false;
			this.watchedTicks = 0;
			if (this.wolf.getAction() == WolfAction.STALKING) {
				this.wolf.setAction(WolfAction.NONE);
			}
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
			this.wolf.lungeCooldown = (this.wolf instanceof AlphaWolfEntity ? 40 : 25) + this.wolf.getRandom().nextInt(30);
			if (this.staredDown) {
				// Slinks back to the ring with a snarl, and tries again soon.
				this.wolf.gazeChecks++;
				this.wolf.lungeCooldown = 20 + this.wolf.getRandom().nextInt(20);
				this.wolf.playSound(SoundEvents.ENTITY_WOLF_GROWL, 0.8f, 1.35f);
			}
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
					if (this.phaseTicks > MAX_APPROACH_TICKS || this.alphaNeedsRoom()) {
						this.phase = Phase.DONE; // (checked first: no crouching to bite once the Alpha starts a big move)
					} else if (distance <= WINDUP_RANGE * WINDUP_RANGE || this.wolf.isInAttackRange(this.target)) {
						this.windUp();
					} else if (this.isStaredDown(distance)) {
						this.staredDown = true;
						this.phase = Phase.DONE;
					} else if (this.phaseTicks % 5 == 0) {
						this.wolf.getNavigation().startMovingTo(this.target, 1.35);
					}
				}
				case WINDUP -> {
					this.wolf.getNavigation().stop();
					if (this.alphaNeedsRoom()) {
						this.phase = Phase.DONE; // the Alpha's started something big: stand down (no leaping in mid-howl)
						return;
					}
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

		/**
		 * A pack wolf (not the Alpha) still running in at a player who holds its gaze (looks straight at it
		 * for {@link #STARE_TICKS} in a row) loses its nerve, {@code hunting.gazeHoldsOff} times in a row at
		 * most; then it calls the bluff. A glance as you turn doesn't count.
		 */
		private boolean isStaredDown(double squaredDistance) {
			FrostboundConfig.Hunting config = FrostboundConfig.get().alphaWolf.hunting;
			boolean watched = !(this.wolf instanceof AlphaWolfEntity) && this.target instanceof PlayerEntity
					&& this.wolf.gazeChecks < config.gazeHoldsOff && squaredDistance > WINDUP_RANGE * WINDUP_RANGE
					&& PackTactics.isWatching(this.target, this.wolf, config.gazeDegrees);
			this.watchedTicks = watched ? this.watchedTicks + 1 : 0;
			return this.watchedTicks >= STARE_TICKS;
		}

		/** How long (ticks) a player has to hold a wolf's gaze to stare it down. */
		public static final int STARE_TICKS = 6;

		/** A pack wolf running in, or crouched to bite, backs off when its Alpha starts a big move. */
		private boolean alphaNeedsRoom() {
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			return alpha != null && alpha != this.wolf && alpha.holdsPackBack();
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
			this.wolf.gazeChecks = 0;
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			if (alpha != null) {
				alpha.releaseLunge(this.wolf);
			}
			if (this.target != null) {
				Vec3d away = NoPenaltyTargeting.findFrom(this.wolf, 4, 3, this.target.getPos());
				if (away != null) {
					this.wolf.getNavigation().startMovingTo(away.x, away.y, away.z, 1.4);
				}
			}
		}
	}

	/**
	 * Without a turn to lunge, circle the target, facing it. A pack wolf keeps its spot in the Alpha's ring
	 * ({@link AlphaWolfEntity#ringPoint}: evenly spaced, turning slowly together); the Alpha (or a wolf
	 * without one) paces round on its own, further out. Near the prey and in place it creeps with its
	 * head low ({@link WolfAction#STALKING}); far off, it trots to catch up.
	 */
	public static final class CircleGoal extends Goal {
		private final PackWolfEntity wolf;
		private LivingEntity target;
		private double angle;
		private double radius;
		private int direction;
		/** Ticks until this wolf next darts in at its prey and back (a feint), and ticks left of one under way. */
		private int nextFeint;
		private int feintTicks;

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
			this.wolf.circling = true;
			this.angle = Math.atan2(this.wolf.getZ() - this.target.getZ(), this.wolf.getX() - this.target.getX());
			this.direction = this.wolf.getRandom().nextBoolean() ? 1 : -1;
			// The Alpha paces just outside its wolves' ring, close enough to bite.
			double ring = FrostboundConfig.get().alphaWolf.hunting.ringRadius;
			this.radius = (this.wolf instanceof AlphaWolfEntity ? ring + 1.0 : ring - 1.0) + this.wolf.getRandom().nextDouble() * 1.5;
			this.nextFeint = 20 + this.wolf.getRandom().nextInt(60);
			this.feintTicks = 0;
		}

		@Override
		public void stop() {
			this.target = null;
			this.wolf.circling = false;
			if (this.wolf.getAction() == WolfAction.STALKING) {
				this.wolf.setAction(WolfAction.NONE);
			}
			this.wolf.getNavigation().stop();
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			this.wolf.getLookControl().lookAt(this.target, 30.0f, 30.0f);
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			boolean inRing = alpha != null && alpha != this.wolf;
			Vec3d point;
			if (inRing) {
				point = alpha.ringPoint(this.wolf, this.target);
			} else {
				this.angle += this.direction * 0.05;
				point = this.target.getPos().add(Math.cos(this.angle) * this.radius, 0.0, Math.sin(this.angle) * this.radius);
			}
			double toTarget = this.wolf.squaredDistanceTo(this.target);
			double toPoint = PackTactics.horizontalSquaredDistance(this.wolf.getPos(), point);
			WolfAction action = this.wolf.getAction();
			boolean inPlace = toTarget < 14.0 * 14.0 && toPoint < 3.5 * 3.5;
			if (this.feint(inRing && inPlace && alpha.holdsPackBack())) {
				return;
			}
			if (action == WolfAction.NONE || action == WolfAction.STALKING) {
				this.wolf.setAction(inPlace ? WolfAction.STALKING : WolfAction.NONE);
			}
			if (this.wolf.age % 5 != 0) {
				return;
			}
			double speed = toTarget > 144.0 ? 1.3 : toPoint > 3.5 * 3.5 ? 1.15 : 0.7;
			if (!this.wolf.getNavigation().startMovingTo(point.x, point.y, point.z, speed) && !inRing) {
				this.direction = -this.direction; // blocked: circle the other way
			}
		}

		/**
		 * Keeping up the pressure between bites: every few seconds a wolf in the ring darts a couple of
		 * blocks in at its prey with a snap and drops back (no bite). Not the Alpha, and not while the
		 * Alpha is making room for a big move. Returns true while a feint is steering the wolf.
		 */
		private boolean feint(boolean alphaBusy) {
			if (this.feintTicks > 0) {
				if (--this.feintTicks == 0) {
					this.nextFeint = 40 + this.wolf.getRandom().nextInt(60);
				}
				return this.feintTicks > 0;
			}
			if (this.wolf instanceof AlphaWolfEntity || alphaBusy || this.wolf.getAction() != WolfAction.STALKING || --this.nextFeint > 0) {
				return false;
			}
			this.feintTicks = 10;
			this.wolf.setAction(WolfAction.NONE);
			this.wolf.playSound(SoundEvents.ENTITY_WOLF_GROWL, 1.0f, 1.3f + this.wolf.getRandom().nextFloat() * 0.2f);
			Vec3d in = this.wolf.getPos().subtract(this.target.getPos()).multiply(1.0, 0.0, 1.0);
			Vec3d point = in.lengthSquared() < 1.0E-4 ? this.target.getPos() : this.target.getPos().add(in.normalize().multiply(2.5));
			this.wolf.getNavigation().startMovingTo(point.x, point.y, point.z, 1.5);
			return true;
		}
	}

	/**
	 * One of the two wolves the Alpha's Pincer sends in ({@link AlphaWolfEntity#pincerRole}). TELL: it
	 * crouches facing the prey (the Alpha draws a pale line to where it will land, which follows the prey
	 * until {@code lockTicks} before it goes). LEAP: an arc to that spot, biting the prey if it's still
	 * there. RECOVER: it stands winded (an opening). The first wolf leaps at {@code windupTicks}, the
	 * second {@code staggerTicks} later. Once started it can't be cut off.
	 */
	public static final class PincerGoal extends Goal {
		private enum Phase { TELL, AIR, RECOVER, DONE }

		private final PackWolfEntity wolf;
		private Phase phase = Phase.DONE;
		private int ticks;
		private int role;
		private LivingEntity prey;
		private Vec3d start = Vec3d.ZERO;
		private Vec3d landing = Vec3d.ZERO;
		private int airTicks;
		private boolean bit;

		public PincerGoal(PackWolfEntity wolf) {
			this.wolf = wolf;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
		}

		@Override
		public boolean canStart() {
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			if (alpha == null || alpha == this.wolf || this.wolf.isScattering()) {
				return false;
			}
			int role = alpha.pincerRole(this.wolf);
			return role >= 0 && alpha.pincer().ticks() < AlphaWolfEntity.Pincer.leapAt(role);
		}

		@Override
		public boolean shouldContinue() {
			if (this.phase == Phase.DONE || this.wolf.isScattering() || !this.wolf.isAlive()) {
				return false;
			}
			// Until it leaps it's following the Alpha's order; called off, it stands down.
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			return this.phase != Phase.TELL || (alpha != null && alpha.pincerRole(this.wolf) == this.role);
		}

		@Override
		public boolean canStop() {
			return false;
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void start() {
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			this.role = alpha.pincerRole(this.wolf);
			this.prey = alpha.pincer().prey;
			this.phase = Phase.TELL;
			this.ticks = 0;
			this.bit = false;
			this.wolf.getNavigation().stop();
			this.wolf.setAction(WolfAction.CROUCH);
			this.wolf.playSound(SoundEvents.ENTITY_WOLF_GROWL, 1.2f, 1.0f);
		}

		@Override
		public void stop() {
			WolfAction action = this.wolf.getAction();
			if (action == WolfAction.CROUCH || action == WolfAction.LEAPING || action == WolfAction.RECOVERING) {
				this.wolf.setAction(WolfAction.NONE);
			}
			this.wolf.fallDistance = 0.0f;
			this.wolf.lungeCooldown = Math.max(this.wolf.lungeCooldown, 40 + this.wolf.getRandom().nextInt(30));
			if (this.bit) {
				this.wolf.gazeChecks = 0;
			}
			AlphaWolfEntity alpha = this.wolf.getAlpha();
			if (alpha != null && alpha.pincer() != null && alpha.pincerRole(this.wolf) == this.role) {
				alpha.pincer().done[this.role] = true;
			}
			this.phase = Phase.DONE;
			this.prey = null;
		}

		@Override
		public void tick() {
			this.ticks++;
			switch (this.phase) {
				case TELL -> {
					this.wolf.getNavigation().stop();
					AlphaWolfEntity alpha = this.wolf.getAlpha();
					if (alpha == null || alpha.pincer() == null) {
						this.phase = Phase.DONE;
						return;
					}
					AlphaWolfEntity.Pincer pincer = alpha.pincer();
					face(this.wolf, pincer.landing[this.role]);
					if (this.ticks % 6 == 0) {
						kickUpGround(this.wolf, 2);
					}
					if (pincer.ticks() >= AlphaWolfEntity.Pincer.leapAt(this.role)) {
						this.leap(pincer.landing[this.role]);
					}
				}
				case AIR -> {
					this.wolf.fallDistance = 0.0f;
					double t = Math.min(1.0, (double) this.ticks / this.airTicks);
					double height = 0.9 + Math.sqrt(PackTactics.horizontalSquaredDistance(this.start, this.landing)) * 0.08;
					Vec3d desired = this.start.lerp(this.landing, t).add(0.0, 4.0 * height * t * (1.0 - t), 0.0);
					this.wolf.setVelocity(desired.subtract(this.wolf.getPos()));
					this.wolf.velocityDirty = true;
					if (!this.bit && this.prey != null && this.prey.isAlive()
							&& this.wolf.getBoundingBox().expand(0.35).intersects(this.prey.getBoundingBox())) {
						this.bit = true;
						this.wolf.tryAttack(this.prey);
						this.wolf.swingHand(net.minecraft.util.Hand.MAIN_HAND);
					}
					boolean blocked = this.wolf.horizontalCollision && this.ticks > 3;
					if (t >= 1.0 || blocked || (this.wolf.isOnGround() && this.ticks > this.airTicks / 2)) {
						this.phase = Phase.RECOVER;
						this.ticks = 0;
						this.wolf.setVelocity(Vec3d.ZERO);
						this.wolf.velocityDirty = true;
						this.wolf.fallDistance = 0.0f;
						this.wolf.setAction(WolfAction.RECOVERING);
						kickUpGround(this.wolf, 8);
					}
				}
				case RECOVER -> {
					this.wolf.getNavigation().stop();
					if (this.ticks == 1) {
						this.wolf.playSound(SoundEvents.ENTITY_WOLF_PANT, 1.0f, 1.0f);
					}
					if (this.ticks >= FrostboundConfig.get().alphaWolf.pincer.recoverTicks) {
						this.phase = Phase.DONE;
					}
				}
				case DONE -> {
				}
			}
		}

		private void leap(Vec3d landing) {
			this.phase = Phase.AIR;
			this.ticks = 0;
			this.start = this.wolf.getPos();
			this.landing = landing;
			double distance = Math.sqrt(PackTactics.horizontalSquaredDistance(this.start, landing));
			this.airTicks = MathHelper.clamp((int) Math.round(distance * 1.4), 8, 16);
			this.wolf.setAction(WolfAction.LEAPING);
			face(this.wolf, landing);
			this.wolf.playSound(SoundEvents.ENTITY_GOAT_LONG_JUMP, 1.2f, 1.2f);
		}
	}

	static void face(LivingEntity entity, Vec3d point) {
		float yaw = (float) (MathHelper.atan2(point.z - entity.getZ(), point.x - entity.getX()) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
		entity.setYaw(yaw);
		entity.setBodyYaw(yaw);
		entity.setHeadYaw(yaw);
	}

	/** Snow (or whatever it stands on) kicked up from under a wolf's paws. */
	static void kickUpGround(PackWolfEntity wolf, int count) {
		if (wolf.getWorld() instanceof ServerWorld world) {
			BlockState ground = world.getBlockState(wolf.getSteppingPos());
			if (!ground.isAir()) {
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), wolf.getX(), wolf.getY() + 0.1,
						wolf.getZ(), count, wolf.getWidth() * 0.4, 0.05, wolf.getWidth() * 0.4, 0.1);
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

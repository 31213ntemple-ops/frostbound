package com.frostbound.alpha;

import com.frostbound.config.FrostboundConfig;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.List;

/**
 * The Alpha's big moves. Each one is choreographed the same way, so a player can learn to read it:
 * a <b>tell</b> long enough to react to (a pose, a sound, and a marker on the ground), the
 * <b>action</b>, then a <b>recovery</b> where the Alpha is open to a counter-attack. None of them
 * can be cut off halfway by another goal, and while one runs the pack holds back
 * ({@link AlphaWolfEntity#canClaimLunge}).
 *
 * <pre>
 *  Pounce   POUNCE_WINDUP (24 t: red ring follows you, then stops)   → LEAPING (arc) → slam → RECOVERING (20 t)
 *  Charge   CHARGE_WINDUP (36 t: paws the ground; pale line shows the path, locked for the last 10 t)
 *                                                                     → CHARGING → hit you → RECOVERING
 *                                                                                → hit a wall → STUNNED (3 s, takes 1.5× damage)
 *  Roar     ROARING (30 t, once, on becoming Bloodied: rears, howls, shockwave at t 12)
 * </pre>
 */
public final class AlphaGoals {
	/** Ground marker colours: the Pounce's landing ring (red) and the Charge's path (pale frost). */
	private static final DustParticleEffect POUNCE_MARK = new DustParticleEffect(new Vector3f(0.85f, 0.1f, 0.08f), 1.1f);
	private static final DustParticleEffect POUNCE_LOCKED = new DustParticleEffect(new Vector3f(1.0f, 0.25f, 0.15f), 1.5f);
	private static final DustParticleEffect CHARGE_PATH = new DustParticleEffect(new Vector3f(0.82f, 0.9f, 1.0f), 1.3f);

	private AlphaGoals() {}

	/** Common checks for starting a big move. */
	private static boolean ready(AlphaWolfEntity alpha) {
		LivingEntity target = alpha.getTarget();
		return target != null && alpha.moveCooldown <= 0 && alpha.isOnGround() && !alpha.isScattering() && !alpha.isCalling()
				&& !alpha.pendingRoar && !alpha.getAction().isResting() && !alpha.isPerformingMove()
				&& PackGoals.isValidTarget(alpha, target);
	}

	private static double horizontalDistance(LivingEntity a, Vec3d b) {
		double dx = a.getX() - b.x;
		double dz = a.getZ() - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static void face(LivingEntity entity, Vec3d point) {
		float yaw = (float) (MathHelper.atan2(point.z - entity.getZ(), point.x - entity.getX()) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
		entity.setYaw(yaw);
		entity.setBodyYaw(yaw);
		entity.setHeadYaw(yaw);
	}

	/** A ring of {@code particle} on the ground around {@code center}. */
	private static void ring(ServerWorld world, ParticleEffect particle, Vec3d center, double radius, int points, double phase) {
		for (int i = 0; i < points; i++) {
			double a = phase + i * Math.PI * 2.0 / points;
			world.spawnParticles(particle, center.x + Math.cos(a) * radius, center.y + 0.15, center.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** Hits every non-pack living thing within {@code radius} of {@code center} (and 2 blocks up or down). */
	private static List<LivingEntity> victims(AlphaWolfEntity alpha, Vec3d center, double radius) {
		Box box = new Box(center.x - radius, center.y - 1.0, center.z - radius, center.x + radius, center.y + 2.5, center.z + radius);
		return alpha.getWorld().getEntitiesByClass(LivingEntity.class, box, e -> e != alpha && e.isAlive() && !(e instanceof PackWolfEntity)
				&& !e.isSpectator() && horizontalDistance(e, center) <= radius);
	}

	private static void tellBarPlayers(AlphaWolfEntity alpha, String key) {
		if (alpha.getWorld() instanceof ServerWorld world) {
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (alpha.isBarShownTo(player)) {
					player.sendMessage(Text.translatable(key).formatted(Formatting.GRAY), true);
				}
			}
		}
	}

	/**
	 * Pounce, for closing a gap of 4.5–13 blocks: the Alpha crouches low with its rear up and a red ring
	 * marks where it will land. For most of the windup the ring follows the target; for the last
	 * {@link #LOCK_TICKS} it stops (brighter, with a snarl), and stepping out of it dodges the slam.
	 */
	public static final class PounceGoal extends Goal {
		private enum Phase { WINDUP, AIR, RECOVER, DONE }

		public static final double MIN_RANGE = 4.5;
		public static final double MAX_RANGE = 13.0;
		public static final int LOCK_TICKS = 10;

		private final AlphaWolfEntity alpha;
		private Phase phase = Phase.DONE;
		private int ticks;
		private Vec3d landing = Vec3d.ZERO;
		private Vec3d start = Vec3d.ZERO;
		private int airTicks;
		private double height;

		public PounceGoal(AlphaWolfEntity alpha) {
			this.alpha = alpha;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
		}

		@Override
		public boolean canStart() {
			if (!ready(this.alpha)) {
				return false;
			}
			LivingEntity target = this.alpha.getTarget();
			double distance = this.alpha.distanceTo(target);
			// When the pick is a Charge and one is possible, leave it to the Charge.
			if (this.alpha.nextMoveIsCharge && ChargeGoal.possible(this.alpha, target)) {
				return false;
			}
			return distance >= MIN_RANGE && distance <= MAX_RANGE && Math.abs(target.getY() - this.alpha.getY()) < 4.0;
		}

		@Override
		public boolean shouldContinue() {
			return this.phase != Phase.DONE && this.alpha.isAlive();
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
			this.phase = Phase.WINDUP;
			this.ticks = 0;
			this.landing = this.alpha.getTarget().getPos();
			this.alpha.getNavigation().stop();
			this.alpha.setAction(WolfAction.POUNCE_WINDUP);
			this.alpha.playSound(SoundEvents.ENTITY_WOLF_GROWL, 2.0f, 0.5f);
		}

		@Override
		public void stop() {
			WolfAction action = this.alpha.getAction();
			if (action == WolfAction.POUNCE_WINDUP || action == WolfAction.LEAPING || action == WolfAction.RECOVERING) {
				this.alpha.setAction(WolfAction.NONE);
			}
			this.alpha.fallDistance = 0.0f;
			this.alpha.setMoveCooldown(this.alpha.moveInterval());
			this.phase = Phase.DONE;
		}

		@Override
		public void tick() {
			this.ticks++;
			if (!(this.alpha.getWorld() instanceof ServerWorld world)) {
				return;
			}
			FrostboundConfig.Pounce config = FrostboundConfig.get().alphaWolf.pounce;
			switch (this.phase) {
				case WINDUP -> {
					this.alpha.getNavigation().stop();
					int lockAt = Math.max(1, config.windupTicks - LOCK_TICKS);
					LivingEntity target = this.alpha.getTarget();
					if (this.ticks < lockAt && target != null && target.isAlive()) {
						this.landing = target.getPos();
					} else if (this.ticks == lockAt) {
						// The ring stops: this is the moment to move.
						this.alpha.playSound(SoundEvents.ENTITY_WOLF_GROWL, 2.2f, 0.75f);
						ring(world, ParticleTypes.CRIT, this.landing, config.radius, 20, 0.0);
					}
					face(this.alpha, this.landing);
					if (this.ticks % 2 == 0) {
						boolean locked = this.ticks >= lockAt;
						ring(world, locked ? POUNCE_LOCKED : POUNCE_MARK, this.landing, config.radius, locked ? 22 : 14, this.ticks * 0.08);
						if (locked) {
							ring(world, POUNCE_MARK, this.landing, config.radius * 0.5, 9, -this.ticks * 0.1);
						}
					}
					if (this.ticks >= config.windupTicks) {
						this.leap();
					}
				}
				case AIR -> {
					this.alpha.fallDistance = 0.0f;
					double t = Math.min(1.0, (double) this.ticks / this.airTicks);
					Vec3d along = this.start.lerp(this.landing, t);
					Vec3d desired = along.add(0.0, 4.0 * this.height * t * (1.0 - t), 0.0);
					this.alpha.setVelocity(desired.subtract(this.alpha.getPos()));
					this.alpha.velocityDirty = true;
					face(this.alpha, this.landing.add(this.landing.subtract(this.start)));
					if (this.ticks % 2 == 0) {
						world.spawnParticles(ParticleTypes.SNOWFLAKE, this.alpha.getX(), this.alpha.getBodyY(0.5), this.alpha.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
					}
					boolean blocked = this.alpha.horizontalCollision && this.ticks > 3;
					if (t >= 1.0 || blocked || (this.alpha.isOnGround() && this.ticks > this.airTicks / 2)) {
						this.slam(world, config);
					}
				}
				case RECOVER -> {
					this.alpha.getNavigation().stop();
					if (this.ticks % 10 == 1) {
						this.alpha.playSound(SoundEvents.ENTITY_WOLF_PANT, 1.0f, 0.6f);
					}
					if (this.ticks >= config.recoverTicks) {
						this.phase = Phase.DONE;
					}
				}
				case DONE -> {
				}
			}
		}

		private void leap() {
			this.phase = Phase.AIR;
			this.ticks = 0;
			this.start = this.alpha.getPos();
			double distance = horizontalDistance(this.alpha, this.landing);
			this.airTicks = MathHelper.clamp((int) Math.round(distance * 1.2), 10, 18);
			this.height = 1.6 + distance * 0.12;
			this.alpha.setAction(WolfAction.LEAPING);
			this.alpha.playSound(SoundEvents.ENTITY_GOAT_LONG_JUMP, 2.0f, 0.7f);
			this.alpha.playSound(SoundEvents.ENTITY_WOLF_GROWL, 2.0f, 0.55f);
		}

		private void slam(ServerWorld world, FrostboundConfig.Pounce config) {
			this.phase = Phase.RECOVER;
			this.ticks = 0;
			this.alpha.setVelocity(Vec3d.ZERO);
			this.alpha.velocityDirty = true;
			this.alpha.fallDistance = 0.0f;
			this.alpha.setAction(WolfAction.RECOVERING);
			Vec3d center = this.alpha.getPos();
			for (LivingEntity victim : victims(this.alpha, center, config.radius)) {
				if (victim.damage(world.getDamageSources().mobAttack(this.alpha), (float) config.damage)) {
					victim.takeKnockback(0.9, center.x - victim.getX(), center.z - victim.getZ());
				}
			}
			BlockState ground = world.getBlockState(this.alpha.getSteppingPos());
			if (!ground.isAir()) {
				BlockStateParticleEffect dust = new BlockStateParticleEffect(ParticleTypes.BLOCK, ground);
				ring(world, dust, center, config.radius * 0.6, 16, 0.0);
				ring(world, dust, center, config.radius, 24, 0.1);
			}
			ring(world, ParticleTypes.POOF, center, config.radius, 16, 0.0);
			world.spawnParticles(ParticleTypes.SNOWFLAKE, center.x, center.y + 0.3, center.z, 40, config.radius * 0.5, 0.2, config.radius * 0.5, 0.08);
			world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_HOSTILE_BIG_FALL, SoundCategory.HOSTILE, 1.6f, 0.7f);
			world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.4f, 0.6f);
		}
	}

	/**
	 * Charge, once Bloodied, at 6–18 blocks with a clear line: the Alpha lowers its head and paws the
	 * ground three times while a pale line on the ground shows its path. For the last
	 * {@code lockTicks} it stops turning (a snort), then runs the line at full tilt. A hit is heavy and
	 * throws you back. If the line ends at a wall or pillar, it runs into it and is stunned.
	 */
	public static final class ChargeGoal extends Goal {
		private enum Phase { WINDUP, RUN, STUNNED, RECOVER, DONE }

		public static final double MIN_RANGE = 6.0;
		private static final int RECOVER_TICKS = 16;

		private final AlphaWolfEntity alpha;
		private Phase phase = Phase.DONE;
		private int ticks;
		private Vec3d direction = Vec3d.ZERO;
		private Vec3d runStart = Vec3d.ZERO;

		public ChargeGoal(AlphaWolfEntity alpha) {
			this.alpha = alpha;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
		}

		@Override
		public boolean canStart() {
			return this.alpha.nextMoveIsCharge && ready(this.alpha) && possible(this.alpha, this.alpha.getTarget());
		}

		/** Bloodied, 6 to (maxDistance − 2) blocks away, about level, and in sight. */
		public static boolean possible(AlphaWolfEntity alpha, LivingEntity target) {
			double distance = alpha.distanceTo(target);
			return alpha.isBloodied() && distance >= MIN_RANGE && distance <= FrostboundConfig.get().alphaWolf.charge.maxDistance - 2.0
					&& Math.abs(target.getY() - alpha.getY()) < 2.0 && alpha.canSee(target);
		}

		@Override
		public boolean shouldContinue() {
			return this.phase != Phase.DONE && this.alpha.isAlive();
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
			this.phase = Phase.WINDUP;
			this.ticks = 0;
			this.aimAt(this.alpha.getTarget());
			this.alpha.getNavigation().stop();
			this.alpha.setAction(WolfAction.CHARGE_WINDUP);
			this.alpha.playSound(SoundEvents.ENTITY_WOLF_GROWL, 2.5f, 0.4f);
		}

		@Override
		public void stop() {
			WolfAction action = this.alpha.getAction();
			if (action == WolfAction.CHARGE_WINDUP || action == WolfAction.CHARGING || action == WolfAction.STUNNED
					|| action == WolfAction.RECOVERING) {
				this.alpha.setAction(WolfAction.NONE);
			}
			this.alpha.setInSittingPose(false);
			this.alpha.setMoveCooldown(this.alpha.moveInterval());
			this.phase = Phase.DONE;
		}

		private void aimAt(LivingEntity target) {
			Vec3d toward = new Vec3d(target.getX() - this.alpha.getX(), 0.0, target.getZ() - this.alpha.getZ());
			if (toward.lengthSquared() > 1.0E-6) {
				this.direction = toward.normalize();
			}
		}

		/** Where the charge would stop: the first block in its way at chest height, or its full distance. */
		public Vec3d pathEnd() {
			double max = FrostboundConfig.get().alphaWolf.charge.maxDistance;
			Vec3d from = this.alpha.getPos().add(0.0, 0.6, 0.0);
			Vec3d to = from.add(this.direction.multiply(max));
			BlockHitResult hit = this.alpha.getWorld().raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
					RaycastContext.FluidHandling.NONE, this.alpha));
			return hit.getType() == HitResult.Type.MISS ? to : hit.getPos();
		}

		@Override
		public void tick() {
			this.ticks++;
			if (!(this.alpha.getWorld() instanceof ServerWorld world)) {
				return;
			}
			FrostboundConfig.Charge config = FrostboundConfig.get().alphaWolf.charge;
			switch (this.phase) {
				case WINDUP -> {
					this.alpha.getNavigation().stop();
					int lockAt = Math.max(1, config.windupTicks - config.lockTicks);
					LivingEntity target = this.alpha.getTarget();
					if (this.ticks < lockAt && target != null && target.isAlive()) {
						this.aimAt(target);
					} else if (this.ticks == lockAt) {
						this.alpha.playSound(SoundEvents.ENTITY_HORSE_BREATHE, 2.0f, 0.5f);
						this.alpha.playSound(SoundEvents.ENTITY_WOLF_GROWL, 2.5f, 0.35f);
					}
					face(this.alpha, this.alpha.getPos().add(this.direction));
					if (this.ticks == 6 || this.ticks == 16 || this.ticks == 26) {
						this.pawTheGround(world);
					}
					// The path, from a little before it locks: you can see where it'll go, and where it'll stop.
					if (this.ticks >= lockAt - 8 && this.ticks % 2 == 0) {
						this.drawPath(world, this.ticks >= lockAt);
					}
					if (this.ticks >= config.windupTicks) {
						this.phase = Phase.RUN;
						this.ticks = 0;
						this.runStart = this.alpha.getPos();
						this.alpha.setAction(WolfAction.CHARGING);
						world.playSound(null, this.alpha.getX(), this.alpha.getY(), this.alpha.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR,
								SoundCategory.HOSTILE, 1.4f, 1.5f);
					}
				}
				case RUN -> this.run(world, config);
				case STUNNED -> {
					this.alpha.getNavigation().stop();
					if (this.ticks % 3 == 0) {
						// Stars circling its head.
						double a = this.ticks * 0.35;
						for (int i = 0; i < 3; i++) {
							double b = a + i * Math.PI * 2.0 / 3.0;
							world.spawnParticles(ParticleTypes.CRIT, this.alpha.getX() + Math.cos(b) * 0.6, this.alpha.getEyeY() + 0.55,
									this.alpha.getZ() + Math.sin(b) * 0.6, 1, 0.0, 0.0, 0.0, 0.0);
						}
					}
					if (this.ticks >= AlphaWolfEntity.ticks(config.stunSeconds)) {
						this.alpha.setInSittingPose(false);
						this.alpha.playSound(SoundEvents.ENTITY_WOLF_SHAKE, 1.2f, 0.7f);
						this.recover();
					}
				}
				case RECOVER -> {
					this.alpha.getNavigation().stop();
					if (this.ticks >= RECOVER_TICKS) {
						this.phase = Phase.DONE;
					}
				}
				case DONE -> {
				}
			}
		}

		private void pawTheGround(ServerWorld world) {
			BlockState ground = world.getBlockState(this.alpha.getSteppingPos());
			Vec3d paw = this.alpha.getPos().add(this.direction.multiply(0.5));
			if (!ground.isAir()) {
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), paw.x, paw.y + 0.1, paw.z, 10,
						0.25, 0.05, 0.25, 0.15);
				world.playSound(null, paw.x, paw.y, paw.z, ground.getSoundGroup().getHitSound(), SoundCategory.HOSTILE, 1.5f, 0.6f);
			}
			world.spawnParticles(ParticleTypes.CLOUD, paw.x, paw.y + 0.6, paw.z, 2, 0.1, 0.05, 0.1, 0.01);
		}

		private void drawPath(ServerWorld world, boolean locked) {
			Vec3d from = this.alpha.getPos();
			Vec3d end = this.pathEnd();
			double length = horizontalDistance(this.alpha, end);
			for (double d = 1.0; d < length; d += 0.7) {
				Vec3d p = from.add(this.direction.multiply(d));
				world.spawnParticles(CHARGE_PATH, p.x, p.y + 0.12, p.z, 1, 0.05, 0.0, 0.05, 0.0);
			}
			if (locked && length < FrostboundConfig.get().alphaWolf.charge.maxDistance - 0.5) {
				// It will hit something there: mark the impact.
				world.spawnParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 3, 0.2, 0.3, 0.2, 0.05);
			}
		}

		private void run(ServerWorld world, FrostboundConfig.Charge config) {
			// It ran into something solid last tick: stunned.
			if (this.alpha.horizontalCollision && this.ticks > 1) {
				this.stun(world);
				return;
			}
			for (LivingEntity victim : victims(this.alpha, this.alpha.getPos(), this.alpha.getWidth() * 0.5 + 0.9)) {
				if (victim.damage(world.getDamageSources().mobAttack(this.alpha), (float) config.damage)) {
					victim.takeKnockback(1.6, -this.direction.x, -this.direction.z);
					victim.addVelocity(0.0, 0.3, 0.0);
					victim.velocityModified = true;
					world.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK,
							SoundCategory.HOSTILE, 1.5f, 0.6f);
				}
				this.skidToAStop();
				return;
			}
			double travelled = horizontalDistance(this.alpha, this.runStart);
			if (travelled >= config.maxDistance || this.ticks > config.maxDistance / config.speed + 10) {
				this.skidToAStop();
				return;
			}
			face(this.alpha, this.alpha.getPos().add(this.direction));
			Vec3d velocity = this.alpha.getVelocity();
			this.alpha.setVelocity(this.direction.x * config.speed, velocity.y, this.direction.z * config.speed);
			this.alpha.velocityDirty = true;
			BlockState ground = world.getBlockState(this.alpha.getSteppingPos());
			if (!ground.isAir()) {
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), this.alpha.getX(), this.alpha.getY() + 0.1,
						this.alpha.getZ(), 4, 0.4, 0.05, 0.4, 0.1);
			}
			world.spawnParticles(ParticleTypes.SNOWFLAKE, this.alpha.getX(), this.alpha.getBodyY(0.4), this.alpha.getZ(), 2, 0.3, 0.2, 0.3, 0.02);
		}

		private void skidToAStop() {
			this.alpha.setVelocity(this.alpha.getVelocity().multiply(0.2, 1.0, 0.2));
			this.alpha.velocityDirty = true;
			this.recover();
		}

		private void recover() {
			this.phase = Phase.RECOVER;
			this.ticks = 0;
			this.alpha.setAction(WolfAction.RECOVERING);
		}

		/** Ran into a wall: knocked silly, sitting with its head swaying, and easier to hurt. */
		private void stun(ServerWorld world) {
			this.phase = Phase.STUNNED;
			this.ticks = 0;
			this.alpha.setVelocity(this.direction.multiply(-0.25).add(0.0, 0.2, 0.0));
			this.alpha.velocityDirty = true;
			this.alpha.setAction(WolfAction.STUNNED);
			this.alpha.setInSittingPose(true);
			Vec3d front = this.alpha.getPos().add(this.direction.multiply(this.alpha.getWidth() * 0.5 + 0.3)).add(0.0, 0.6, 0.0);
			BlockState wall = world.getBlockState(BlockPos.ofFloored(front));
			if (!wall.isAir()) {
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, wall), front.x, front.y, front.z, 30, 0.3, 0.4, 0.3, 0.2);
				world.playSound(null, front.x, front.y, front.z, wall.getSoundGroup().getBreakSound(), SoundCategory.HOSTILE, 2.0f, 0.5f);
			}
			world.spawnParticles(ParticleTypes.CRIT, front.x, front.y, front.z, 15, 0.3, 0.3, 0.3, 0.3);
			world.playSound(null, this.alpha.getX(), this.alpha.getY(), this.alpha.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
					SoundCategory.HOSTILE, 0.5f, 1.7f);
			world.playSound(null, this.alpha.getX(), this.alpha.getY(), this.alpha.getZ(), SoundEvents.ENTITY_WOLF_HURT, SoundCategory.HOSTILE,
					2.0f, 0.6f);
			tellBarPlayers(this.alpha, "entity.frostbound.alpha_wolf.stunned");
		}

		/** Test hook: whether it's stunned right now. */
		public boolean isStunned() {
			return this.phase == Phase.STUNNED;
		}
	}

	/**
	 * Bloodied: the first time the Alpha drops below half health it rears back and roars. The pack
	 * answers with a growl, anyone within {@link #SHOCKWAVE_RADIUS} blocks is thrown back by a ring of
	 * snow, and from then on its eyes flare, it's faster, and it can Charge.
	 */
	public static final class RoarGoal extends Goal {
		public static final int TICKS = 30;
		public static final int SHOCKWAVE_AT = 12;
		public static final double SHOCKWAVE_RADIUS = 5.0;

		private final AlphaWolfEntity alpha;
		private int ticks;

		public RoarGoal(AlphaWolfEntity alpha) {
			this.alpha = alpha;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
		}

		@Override
		public boolean canStart() {
			return this.alpha.pendingRoar && this.alpha.isOnGround() && !this.alpha.getAction().isResting() && !this.alpha.isPerformingMove();
		}

		@Override
		public boolean shouldContinue() {
			return this.ticks < TICKS && this.alpha.isAlive();
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
			this.ticks = 0;
			this.alpha.pendingRoar = false;
			this.alpha.getNavigation().stop();
			this.alpha.setAction(WolfAction.ROARING);
			this.alpha.playSound(SoundEvents.ENTITY_WOLF_GROWL, 2.5f, 0.4f);
		}

		@Override
		public void stop() {
			if (this.alpha.getAction() == WolfAction.ROARING) {
				this.alpha.setAction(WolfAction.NONE);
			}
			// Straight into its first Charge.
			this.alpha.setMoveCooldown(10);
			this.alpha.nextMoveIsCharge = true;
		}

		@Override
		public void tick() {
			this.ticks++;
			this.alpha.getNavigation().stop();
			if (!(this.alpha.getWorld() instanceof ServerWorld world)) {
				return;
			}
			if (this.ticks == SHOCKWAVE_AT) {
				world.playSound(null, this.alpha.getX(), this.alpha.getY(), this.alpha.getZ(), SoundEvents.ENTITY_WOLF_HOWL,
						SoundCategory.HOSTILE, 4.0f, 0.5f);
				world.playSound(null, this.alpha.getX(), this.alpha.getY(), this.alpha.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR,
						SoundCategory.HOSTILE, 1.5f, 1.1f);
				Vec3d center = this.alpha.getPos();
				for (LivingEntity victim : victims(this.alpha, center, SHOCKWAVE_RADIUS)) {
					victim.takeKnockback(1.2, center.x - victim.getX(), center.z - victim.getZ());
					victim.addVelocity(0.0, 0.25, 0.0);
					victim.velocityModified = true;
				}
				for (PackWolfEntity wolf : this.alpha.getPack()) {
					if (wolf != this.alpha) {
						world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, wolf.getX(), wolf.getBodyY(1.0) + 0.3, wolf.getZ(), 2, 0.25, 0.1, 0.25, 0.0);
						wolf.playSound(SoundEvents.ENTITY_WOLF_GROWL, 1.0f, wolf.getSoundPitch());
					}
				}
				tellBarPlayers(this.alpha, "entity.frostbound.alpha_wolf.bloodied");
			}
			if (this.ticks >= SHOCKWAVE_AT && this.ticks <= SHOCKWAVE_AT + 8) {
				// The shockwave: a ring of snow rolling outwards.
				double r = 0.8 + (this.ticks - SHOCKWAVE_AT) * (SHOCKWAVE_RADIUS / 8.0);
				ring(world, ParticleTypes.SNOWFLAKE, this.alpha.getPos(), r, 28, this.ticks);
				ring(world, ParticleTypes.CLOUD, this.alpha.getPos(), r, 12, -this.ticks);
			}
		}
	}

	/**
	 * After a fight (nothing to fight for {@code giveUpSeconds}), a den Alpha walks home and lies back
	 * down, and its pack follows it to bed ({@link PackGoals.GoToBedGoal}).
	 */
	public static final class ReturnHomeGoal extends Goal {
		private static final int MAX_TICKS = 600;

		private final AlphaWolfEntity alpha;
		private int ticks;

		public ReturnHomeGoal(AlphaWolfEntity alpha) {
			this.alpha = alpha;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		private BlockPos home() {
			return this.alpha.getBed() != null ? this.alpha.getBed() : this.alpha.getDen();
		}

		@Override
		public boolean canStart() {
			return this.alpha.getDen() != null && this.alpha.getTarget() == null && !this.alpha.getAction().isResting()
					&& !this.alpha.isScattering() && !this.alpha.isPerformingMove()
					&& this.alpha.calmTicks > AlphaWolfEntity.ticks(FrostboundConfig.get().alphaWolf.den.giveUpSeconds);
		}

		@Override
		public boolean shouldContinue() {
			return this.alpha.getTarget() == null && !this.alpha.getAction().isResting() && !this.alpha.isScattering();
		}

		@Override
		public void start() {
			this.ticks = 0;
			this.alpha.goingHome = true;
			BlockPos home = this.home();
			this.alpha.getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.8);
		}

		@Override
		public void stop() {
			if (!this.alpha.isSleeping()) {
				this.alpha.goingHome = false;
			}
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			this.ticks++;
			BlockPos home = this.home();
			double distance = this.alpha.squaredDistanceTo(Vec3d.ofBottomCenter(home));
			boolean arrived = distance < 1.0 || (distance < 2.5 * 2.5 && this.alpha.getNavigation().isIdle());
			if ((arrived || this.ticks > MAX_TICKS) && this.alpha.isOnGround()) {
				this.alpha.goToSleep();
				return;
			}
			if (this.ticks % 20 == 0 && this.alpha.getNavigation().isIdle()) {
				this.alpha.getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.8);
			}
		}
	}
}

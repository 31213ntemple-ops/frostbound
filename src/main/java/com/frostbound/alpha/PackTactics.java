package com.frostbound.alpha;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The geometry behind the pack hunting as one (used by {@link PackGoals} and {@link AlphaWolfEntity}):
 *
 *  - {@link #ringSlots}: where each circling wolf should be, so the pack spreads out evenly round its prey
 *    and turns round it together, instead of each wolf circling on its own.
 *  - {@link #behindness}: how far behind its prey a wolf is; the Alpha gives the next bite to the wolf
 *    you can't see.
 *  - {@link #isWatching}: whether a player is staring a wolf down (it breaks off its run).
 *  - {@link #pincerPair}: the two wolves most nearly opposite each other, for the Alpha's Pincer.
 */
public final class PackTactics {
	private static final double TAU = Math.PI * 2.0;

	private PackTactics() {}

	/**
	 * Evenly spaced angles (radians, round the prey) for wolves now at {@code angles}: each keeps its place
	 * in the order round the ring, the ring as a whole moves as little as it can, and then it's turned by
	 * {@code spin} (so, asked again and again, the pack keeps circling). {@code result[i]} is wolf i's spot.
	 */
	public static double[] ringSlots(double[] angles, double spin) {
		int n = angles.length;
		double[] slots = new double[n];
		if (n == 0) {
			return slots;
		}
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) {
			order[i] = i;
		}
		java.util.Arrays.sort(order, (a, b) -> Double.compare(wrap(angles[a]), wrap(angles[b])));
		double step = TAU / n;
		// The offset that moves everyone least: the circular mean of where each wolf is minus its even spot.
		double sin = 0.0, cos = 0.0;
		for (int k = 0; k < n; k++) {
			double off = wrap(angles[order[k]]) - k * step;
			sin += Math.sin(off);
			cos += Math.cos(off);
		}
		double offset = Math.atan2(sin, cos);
		for (int k = 0; k < n; k++) {
			slots[order[k]] = wrap(offset + k * step + spin);
		}
		return slots;
	}

	/** {@code angle} in [0, 2π). */
	public static double wrap(double angle) {
		double a = angle % TAU;
		return a < 0 ? a + TAU : a;
	}

	/** The angle of {@code point} round {@code center}, as {@link #ringSlots} uses (0 along +x, π/2 along +z). */
	public static double angleAround(Vec3d center, Vec3d point) {
		return wrap(Math.atan2(point.z - center.z, point.x - center.x));
	}

	public static double horizontalSquaredDistance(Vec3d a, Vec3d b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	/** The smallest angle between two directions round the ring, in radians (0 to π). */
	public static double separation(double a, double b) {
		double d = Math.abs(wrap(a) - wrap(b));
		return d > Math.PI ? TAU - d : d;
	}

	/**
	 * How far behind {@code target} the point is, from 0 (dead ahead of where it's looking) through 0.5
	 * (to its side) to 1 (right behind it). Only the horizontal direction counts.
	 */
	public static double behindness(LivingEntity target, Vec3d point) {
		// A player looks where it faces; a mob where its head is turned.
		Vec3d look = Vec3d.fromPolar(0.0f, target instanceof PlayerEntity ? target.getYaw() : target.getHeadYaw());
		Vec3d to = new Vec3d(point.x - target.getX(), 0.0, point.z - target.getZ());
		if (to.lengthSquared() < 1.0E-6) {
			return 0.0;
		}
		double cos = MathHelper.clamp(look.dotProduct(to.normalize()), -1.0, 1.0);
		return (1.0 - cos) / 2.0;
	}

	/**
	 * Whether {@code watcher} is looking straight at {@code wolf}: within {@code degrees} of the middle of
	 * its view, within 16 blocks, with nothing in the way.
	 */
	public static boolean isWatching(LivingEntity watcher, Entity wolf, double degrees) {
		if (degrees <= 0.0) {
			return false;
		}
		Vec3d eye = watcher.getEyePos();
		Vec3d to = wolf.getPos().add(0.0, wolf.getHeight() * 0.5, 0.0).subtract(eye);
		double distance = to.length();
		if (distance > 16.0 || distance < 1.0E-4) {
			return false;
		}
		Vec3d look = watcher.getRotationVec(1.0f);
		double cos = look.dotProduct(to.multiply(1.0 / distance));
		return cos >= Math.cos(Math.toRadians(degrees)) && watcher.canSee(wolf);
	}

	/**
	 * Of {@code wolves}, the two most nearly opposite each other round {@code center}, if they're at
	 * least {@code minDegrees} apart; otherwise null.
	 */
	@Nullable
	public static PackWolfEntity[] pincerPair(List<PackWolfEntity> wolves, Vec3d center, double minDegrees) {
		PackWolfEntity[] best = null;
		double widest = Math.toRadians(minDegrees) - 1.0E-6;
		for (int i = 0; i < wolves.size(); i++) {
			double a = angleAround(center, wolves.get(i).getPos());
			for (int j = i + 1; j < wolves.size(); j++) {
				double gap = separation(a, angleAround(center, wolves.get(j).getPos()));
				if (gap > widest) {
					widest = gap;
					best = new PackWolfEntity[] {wolves.get(i), wolves.get(j)};
				}
			}
		}
		return best;
	}
}

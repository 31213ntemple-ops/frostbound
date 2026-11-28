package com.frostbound.mushrooms;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;

/**
 * The three giant mushrooms, as block positions relative to the middle of the stem's bottom layer.
 *
 * Each is worked out once from a few numbers:
 *  - the **stem** is a stack of round layers; its middle follows a curve (x and z as functions of
 *    t = height / visible stem height, 0 at the ground, 1 at the cap's rim) and its radius changes with t
 *    (a flared foot, a slight swelling under the cap);
 *  - the **cap** is a hollow shell between two half-ellipsoids (horizontal radius {@code a}, height
 *    {@code b}, {@code thickness} apart; with {@code power} above 2 they're squarer: a flatter top and a
 *    rounder edge), cut off a little below its widest point so the rim curls in,
 *    and tilted by {@code tilt} degrees towards {@code tiltDirection}. Its rim sits at the top of the
 *    visible stem; the stem carries on up inside the hollow until it meets the shell, which holds the
 *    cap up and is the first stem layer you reach once the cap is gone.
 *
 * The shapes follow real mushrooms: a fly agaric's dome, a parasol-like brown cap, and a curving
 * stem with a nodding bell.
 */
public enum GiantMushroomShape {
	/** Red fly agaric: a round dome on a thick stem with a flared foot. 15 tall, 13 across. */
	TOADSTOOL(true, 9, t -> 0, t -> 0,
			t -> 1.6 + 1.2 * Math.exp(-t * 5) + 0.3 * Math.max(0, (t - 0.85) / 0.15),
			6.2, 4.8, 2.0, 1.2, 0.15, 0, 0),
	/** Brown: a broad cap with a flat top and rounded edge on a short, fat stem with a slight bend. 11 tall, 11 across. */
	FLATCAP(false, 7, t -> 1.0 * Math.sin(t * Math.PI * 0.5), t -> 0.5 * Math.sin(t * Math.PI),
			t -> 1.8 + 0.9 * Math.exp(-t * 5),
			5.8, 2.8, 3.0, 1.2, 0.3, 0, 0),
	/** Red: a slimmer stem that curves a few blocks over as it climbs, with a bell tipped back on top. 15 tall. */
	LEANING(true, 10, t -> 3.5 * Math.pow(t, 1.5), t -> 1.0 * Math.sin(t * Math.PI),
			t -> 1.5 + 1.0 * Math.exp(-t * 5),
			5.0, 3.8, 2.0, 1.2, 0.25, 22, 180);

	public final boolean red;
	/** Relative positions, bottom up. */
	public final Set<BlockPos> stem;
	public final Set<BlockPos> cap;
	/** Number of stem layers (0 to layers - 1), including the part hidden inside the cap. */
	public final int layers;
	/** The middle of the cap's rim, relative to the origin: where the stem goes into the cap. */
	public final BlockPos capCenter;
	/** Covers every position, relative to the origin. */
	public final BlockBox bounds;

	GiantMushroomShape(boolean red, int stemHeight, DoubleUnaryOperator stemX, DoubleUnaryOperator stemZ,
			DoubleUnaryOperator stemRadius, double a, double b, double power, double thickness, double cut, double tilt, double tiltDirection) {
		this.red = red;
		// The cap: a tilted, hollow half-ellipsoid whose rim (cut plane) is centred on the top of the visible stem.
		double tiltRad = Math.toRadians(tilt), dirRad = Math.toRadians(tiltDirection);
		double nx = Math.sin(tiltRad) * Math.cos(dirRad), ny = Math.cos(tiltRad), nz = Math.sin(tiltRad) * Math.sin(dirRad);
		double rimX = stemX.applyAsDouble(1.0), rimZ = stemZ.applyAsDouble(1.0);
		double ex = rimX + nx * cut * b, ey = stemHeight + ny * cut * b, ez = rimZ + nz * cut * b;
		double ai = a - thickness, bi = b - thickness;
		Set<BlockPos> cap = new LinkedHashSet<>();
		int reach = (int) Math.ceil(a + b) + 2;
		for (int y = (int) ey - reach; y <= (int) ey + reach; y++) {
			for (int x = (int) ex - reach; x <= (int) ex + reach; x++) {
				for (int z = (int) ez - reach; z <= (int) ez + reach; z++) {
					double dx = x - ex, dy = y - ey, dz = z - ez;
					double w = dx * nx + dy * ny + dz * nz;
					if (w < -cut * b) {
						continue;
					}
					double rx = dx - w * nx, ry = dy - w * ny, rz = dz - w * nz;
					double rho = Math.sqrt(rx * rx + ry * ry + rz * rz);
					if (shell(rho, w, a, b, power) <= 1.0 && shell(rho, w, ai, bi, power) >= 1.0) {
						cap.add(new BlockPos(x, y, z));
					}
				}
			}
		}
		// The stem: round layers up the curve until one would run into the cap.
		Set<BlockPos> stem = new LinkedHashSet<>();
		int layers = 0;
		for (int y = 0; y < stemHeight + 2 * b; y++) {
			double t = Math.min(1.0, (double) y / stemHeight);
			double cx = stemX.applyAsDouble(t), cz = stemZ.applyAsDouble(t), r = stemRadius.applyAsDouble(t);
			List<BlockPos> layer = new ArrayList<>();
			for (int x = MathHelper.floor(cx - r) - 1; x <= MathHelper.ceil(cx + r) + 1; x++) {
				for (int z = MathHelper.floor(cz - r) - 1; z <= MathHelper.ceil(cz + r) + 1; z++) {
					if ((x - cx) * (x - cx) + (z - cz) * (z - cz) <= r * r) {
						layer.add(new BlockPos(x, y, z));
					}
				}
			}
			if (layer.stream().anyMatch(cap::contains)) {
				break;
			}
			stem.addAll(layer);
			layers++;
		}
		this.layers = layers;
		this.capCenter = new BlockPos(MathHelper.floor(rimX + 0.5), stemHeight, MathHelper.floor(rimZ + 0.5));
		this.stem = Collections.unmodifiableSet(stem);
		this.cap = Collections.unmodifiableSet(cap);
		List<BlockPos> all = new ArrayList<>(stem);
		all.addAll(cap);
		this.bounds = BlockBox.encompassPositions(all).orElseThrow();
	}

	private static double shell(double rho, double w, double a, double b, double power) {
		return Math.pow(rho / a, power) + Math.pow(Math.abs(w) / b, power);
	}

	/** The name used in commands and saved data: "toadstool", "flatcap", "leaning". */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static GiantMushroomShape byId(String id) {
		for (GiantMushroomShape shape : values()) {
			if (shape.id().equals(id)) {
				return shape;
			}
		}
		return null;
	}
}

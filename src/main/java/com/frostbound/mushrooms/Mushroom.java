package com.frostbound.mushrooms;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One placed giant mushroom. Its origin and shape are saved ({@link MushroomStore}); the rest only matters
 * while someone's mining it. Positions come from the shape's packed arrays plus the origin, so a mushroom
 * holds no position lists of its own: loops use {@link #cap(int, BlockPos.Mutable)} /
 * {@link #stem(int, BlockPos.Mutable)} with a reused mutable position.
 */
public final class Mushroom {
	/** {@link #activeLayer} before the first refresh: the stem's ACTIVE flags haven't been checked yet. */
	static final int UNKNOWN = Integer.MIN_VALUE;
	/** {@link #activeLayer} while no layer is active (cap still there, or no stem left). */
	static final int NONE = -1;

	public final BlockPos origin;
	public final GiantMushroomShape shape;
	/** Covers every position, in world coordinates. */
	public final BlockBox box;

	long engagedUntil = -1;
	long nextSprout;
	Entity miner;
	BlockPos lastHit;
	final Set<BlockPos> sprouts = new LinkedHashSet<>();
	/** The stem layer whose blocks are ACTIVE, as of the last refresh. */
	int activeLayer = UNKNOWN;
	/** Taken out of the store (forgotten or removed). */
	boolean removed;

	Mushroom(BlockPos origin, GiantMushroomShape shape) {
		this.origin = origin.toImmutable();
		this.shape = shape;
		this.box = shape.bounds.offset(origin.getX(), origin.getY(), origin.getZ());
	}

	// ---- Membership (no allocation) ----

	public boolean contains(BlockPos pos) {
		long rel = relative(pos);
		return rel != Long.MIN_VALUE && (this.shape.capSet.contains(rel) || this.shape.stemSet.contains(rel));
	}

	public boolean isCap(BlockPos pos) {
		long rel = relative(pos);
		return rel != Long.MIN_VALUE && this.shape.capSet.contains(rel);
	}

	/** {@code pos} relative to the origin, packed, or Long.MIN_VALUE if it's outside the box. */
	private long relative(BlockPos pos) {
		int x = pos.getX(), y = pos.getY(), z = pos.getZ();
		if (x < this.box.getMinX() || x > this.box.getMaxX() || y < this.box.getMinY() || y > this.box.getMaxY()
				|| z < this.box.getMinZ() || z > this.box.getMaxZ()) {
			return Long.MIN_VALUE;
		}
		return BlockPos.asLong(x - this.origin.getX(), y - this.origin.getY(), z - this.origin.getZ());
	}

	// ---- Iteration (no allocation) ----

	public int capCount() {
		return this.shape.capPacked.length;
	}

	/** Sets {@code out} to the {@code i}-th cap position and returns it. */
	public BlockPos.Mutable cap(int i, BlockPos.Mutable out) {
		return set(out, this.shape.capPacked[i]);
	}

	public int stemCount() {
		return this.shape.stemPacked.length;
	}

	public BlockPos.Mutable stem(int i, BlockPos.Mutable out) {
		return set(out, this.shape.stemPacked[i]);
	}

	/** Stem layer {@code layer} (0 = bottom) is stem indices {@code layerStart(layer)} to {@code layerStart(layer + 1) - 1}. */
	public int layerStart(int layer) {
		return this.shape.layerStart[layer];
	}

	public int layers() {
		return this.shape.layers;
	}

	private BlockPos.Mutable set(BlockPos.Mutable out, long rel) {
		return out.set(this.origin.getX() + BlockPos.unpackLongX(rel), this.origin.getY() + BlockPos.unpackLongY(rel),
				this.origin.getZ() + BlockPos.unpackLongZ(rel));
	}

	// ---- Convenience (allocates: for commands and tests, not per-tick code) ----

	public List<BlockPos> capPositions() {
		List<BlockPos> out = new ArrayList<>(capCount());
		BlockPos.Mutable p = new BlockPos.Mutable();
		for (int i = 0; i < capCount(); i++) {
			out.add(cap(i, p).toImmutable());
		}
		return out;
	}

	public List<BlockPos> stemPositions() {
		List<BlockPos> out = new ArrayList<>(stemCount());
		BlockPos.Mutable p = new BlockPos.Mutable();
		for (int i = 0; i < stemCount(); i++) {
			out.add(stem(i, p).toImmutable());
		}
		return out;
	}

	public Block capBlock() {
		return this.shape.red ? GiantMushrooms.RED_CAP : GiantMushrooms.BROWN_CAP;
	}

	/** The middle of the stem's top, where bursts push away from. */
	public Vec3d axis() {
		return Vec3d.ofBottomCenter(this.origin.add(this.shape.capCenter));
	}

	public boolean isEngaged(long now) {
		return this.engagedUntil >= now;
	}

	/** Sprouts this mushroom grew that are still there. */
	public Set<BlockPos> sprouts() {
		return Collections.unmodifiableSet(this.sprouts);
	}
}

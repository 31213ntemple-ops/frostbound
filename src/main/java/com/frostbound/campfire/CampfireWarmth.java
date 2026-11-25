package com.frostbound.campfire;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldView;

import java.util.Optional;

/**
 * Where a lit campfire warms you: a circle {@code campfire.warmthRadius} blocks round it (7 by default),
 * up to {@code warmthHeight} blocks above or below. Tough As Nails does the temperature; on its own it
 * finds heat by flood-filling the air round you a few steps, which makes a diamond (7 blocks straight
 * out, only 3 or 4 on the diagonals). {@code mixin.campfire.TemperatureHelperImplMixin} swaps that out
 * for campfires, and {@code client.campfire.WarmthRing} draws the edge of the circle.
 *
 * A block counts as inside when its centre is within {@link #ringRadius()} of the campfire's centre,
 * so standing on the 7th block straight out still counts and the ring sits on that block's outer edge.
 */
public final class CampfireWarmth {
	/** Blocks that warm like this while lit (minecraft:campfire; a data pack can add more). */
	public static final TagKey<Block> WARMING_CAMPFIRES = TagKey.of(RegistryKeys.BLOCK, Frostbound.id("warming_campfires"));

	private CampfireWarmth() {}

	public static double radius() {
		return Math.max(0.0, FrostboundConfig.get().campfire.warmthRadius);
	}

	/** Radius of the ring, from the campfire's centre: the outer edge of the furthest block straight out. */
	public static double ringRadius() {
		return radius() + 0.5;
	}

	public static int height() {
		return Math.max(0, FrostboundConfig.get().campfire.warmthHeight);
	}

	/** A lit campfire (or anything in the tag, lit if it can be). */
	public static boolean isWarming(BlockState state) {
		return state.isIn(WARMING_CAMPFIRES) && (!state.contains(Properties.LIT) || state.get(Properties.LIT));
	}

	/** Whether feet in this block are warmed by a campfire at {@code campfire} (lit or not). */
	public static boolean warms(BlockPos campfire, BlockPos feet) {
		int dy = feet.getY() - campfire.getY();
		if (Math.abs(dy) > height()) {
			return false;
		}
		double dx = feet.getX() - campfire.getX();
		double dz = feet.getZ() - campfire.getZ();
		double r = ringRadius();
		return dx * dx + dz * dz <= r * r;
	}

	/** The nearest lit campfire whose warmth reaches feet in this block, if any. Only looks in loaded chunks. */
	public static Optional<BlockPos> findWarmingCampfire(WorldView world, BlockPos feet) {
		int reach = MathHelper.floor(ringRadius());
		int height = height();
		double r = ringRadius();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				double horizontal = dx * dx + dz * dz;
				if (horizontal > r * r) {
					continue;
				}
				int x = feet.getX() + dx;
				int z = feet.getZ() + dz;
				if (!world.isChunkLoaded(x >> 4, z >> 4)) {
					continue;
				}
				for (int dy = -height; dy <= height; dy++) {
					pos.set(x, feet.getY() + dy, z);
					double distance = horizontal + dy * dy;
					if (distance < bestDistance && isWarming(world.getBlockState(pos))) {
						best = pos.toImmutable();
						bestDistance = distance;
					}
				}
			}
		}
		return Optional.ofNullable(best);
	}
}

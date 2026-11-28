package com.frostbound.mushrooms;

import com.frostbound.config.FrostboundConfig;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.List;

/**
 * Sprouts: while someone mines a cap, vanilla mushrooms grow on top of it near them, each with a countdown;
 * one left alone bursts, throwing everything near it off the cap and growing some of the cap back
 * (blighted). Only mushrooms someone is mining tick ({@link MushroomStore#ticking()}).
 */
public final class MushroomSprouts {
	/** Ticks before bursting that a sprout hisses. */
	private static final int HISS_TICKS = 10;
	private static final DustParticleEffect BURST_DUST = new DustParticleEffect(new Vector3f(0.85f, 0.15f, 0.12f), 1.6f);

	private MushroomSprouts() {}

	/** Someone hit or broke a cap block at {@code pos}: sprouts start (or keep) growing on that cap. */
	public static void engage(ServerWorld world, BlockPos pos, Entity miner) {
		Mushroom m = GiantMushrooms.find(world, pos);
		if (m == null || !m.isCap(pos)) {
			return;
		}
		FrostboundConfig.GiantMushroomOptions options = GiantMushrooms.options();
		long now = world.getTime();
		if (!m.isEngaged(now)) {
			m.nextSprout = now + options.firstSproutTicks;
		}
		m.engagedUntil = now + options.engagedTicks;
		m.miner = miner;
		m.lastHit = pos.toImmutable();
		MushroomStore.get(world).startTicking(m);
	}

	/** The world tick: grows a sprout on each mushroom being mined whose turn it is; drops the ones nobody's mining any more. */
	static void tick(ServerWorld world, MushroomStore store) {
		if (store.ticking().isEmpty()) {
			return;
		}
		FrostboundConfig.GiantMushroomOptions options = GiantMushrooms.options();
		long now = world.getTime();
		for (Iterator<Mushroom> it = store.ticking().iterator(); it.hasNext(); ) {
			Mushroom m = it.next();
			if (!m.isEngaged(now)) {
				it.remove();
				continue;
			}
			if (now >= m.nextSprout && world.getChunkManager().isChunkLoaded(
					ChunkSectionPos.getSectionCoord(m.origin.getX()), ChunkSectionPos.getSectionCoord(m.origin.getZ()))) {
				growSprout(world, m);
				int min = Math.max(1, options.sproutIntervalMinTicks);
				int max = Math.max(min, options.sproutIntervalMaxTicks);
				m.nextSprout = now + min + world.random.nextInt(max - min + 1);
			}
		}
	}

	/**
	 * Grows one sprout on the top of the cap (a cap block with nothing of the cap over it, so never inside
	 * the hollow) near whoever is mining, not where anyone stands, unless the cap already has
	 * {@code maxSprouts}. Each qualifying spot is equally likely (picked as they're found, without listing
	 * them). Returns where, or null.
	 */
	public static BlockPos growSprout(ServerWorld world, Mushroom m) {
		for (Iterator<BlockPos> it = m.sprouts.iterator(); it.hasNext(); ) {
			if (!world.getBlockState(it.next()).isOf(GiantMushrooms.SPROUT)) {
				it.remove();
			}
		}
		FrostboundConfig.GiantMushroomOptions options = GiantMushrooms.options();
		if (m.sprouts.size() >= options.maxSprouts) {
			return null;
		}
		Vec3d near = m.miner != null && !m.miner.isRemoved() ? m.miner.getPos()
				: m.lastHit != null ? m.lastHit.toCenterPos() : m.axis();
		double radius = options.sproutRadius, r2 = radius * radius;
		// Everything that could be in the way, fetched once.
		List<LivingEntity> crowd = world.getEntitiesByClass(LivingEntity.class, new Box(near, near).expand(radius + 1.0),
				EntityPredicates.EXCEPT_SPECTATOR);
		BlockPos.Mutable p = new BlockPos.Mutable(), q = new BlockPos.Mutable();
		long chosen = 0;
		int seen = 0;
		for (int i = 0, n = m.capCount(); i < n; i++) {
			m.cap(i, p);
			int x = p.getX(), y = p.getY() + 1, z = p.getZ();
			double dx = x + 0.5 - near.x, dy = y + 0.5 - near.y, dz = z + 0.5 - near.z;
			if (dx * dx + dy * dy + dz * dz > r2 || !(world.getBlockState(p).getBlock() instanceof GiantCapBlock)
					|| !world.getBlockState(q.set(x, y, z)).isAir() || capAbove(world, m, i, q) || occupied(crowd, x, y, z)) {
				continue;
			}
			seen++;
			if (world.random.nextInt(seen) == 0) {
				chosen = BlockPos.asLong(x, y, z);
			}
		}
		if (seen == 0) {
			return null;
		}
		BlockPos spot = BlockPos.fromLong(chosen);
		world.setBlockState(spot, GiantMushrooms.SPROUT.getDefaultState().with(MushroomSproutBlock.BROWN, !m.shape.red));
		if (world.getBlockEntity(spot) instanceof MushroomSproutBlockEntity sprout) {
			sprout.light(world.getTime(), options.sproutFuseTicks);
		}
		m.sprouts.add(spot);
		world.playSound(null, spot, SoundEvents.BLOCK_FUNGUS_PLACE, SoundCategory.BLOCKS, 1.0f, 1.4f);
		world.spawnParticles(ParticleTypes.CRIMSON_SPORE, spot.getX() + 0.5, spot.getY() + 0.3, spot.getZ() + 0.5, 8, 0.25, 0.2, 0.25, 0.0);
		return spot;
	}

	/** Whether any of the cap is still over cap block {@code index} (its column, up to the top of the mushroom). */
	private static boolean capAbove(ServerWorld world, Mushroom m, int index, BlockPos.Mutable scratch) {
		for (int j : m.shape.capAbove[index]) {
			if (world.getBlockState(m.cap(j, scratch)).getBlock() instanceof GiantCapBlock) {
				return true;
			}
		}
		return false;
	}

	private static boolean occupied(List<LivingEntity> entities, int x, int y, int z) {
		for (int i = 0, n = entities.size(); i < n; i++) {
			if (entities.get(i).getBoundingBox().intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0)) {
				return true;
			}
		}
		return false;
	}

	/** Each tick of a sprout (server): the hiss just before it goes, then the burst. */
	static void sproutTick(ServerWorld world, BlockPos pos, MushroomSproutBlockEntity sprout) {
		long left = sprout.ticksLeft();
		if (left == HISS_TICKS) {
			world.playSound(null, pos, SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.BLOCKS, 0.6f, 1.8f);
		}
		if (left <= 0) {
			burst(world, pos);
		}
	}

	/**
	 * A sprout bursts: gone, with a bang, everything alive within {@code burstRadius} thrown outward away
	 * from the stem (off the cap) and up, and the missing cap blocks nearest it (up to
	 * {@code burstRegrowBlocks}, never where something stands) grown back.
	 */
	public static void burst(ServerWorld world, BlockPos pos) {
		Mushroom m = GiantMushrooms.find(world, pos.down());
		if (m == null) {
			m = MushroomStore.get(world).nearest(pos.toCenterPos(), 16.0);
		}
		world.setBlockState(pos, Blocks.AIR.getDefaultState());
		Vec3d center = pos.toCenterPos();
		world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 0.8f, 1.5f);
		world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
		world.spawnParticles(BURST_DUST, center.x, center.y, center.z, 40, 0.8, 0.5, 0.8, 0.0);

		FrostboundConfig.GiantMushroomOptions options = GiantMushrooms.options();
		Vec3d axis = m != null ? m.axis() : center;
		double radius = options.burstRadius;
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, new Box(pos).expand(radius),
				e -> e.isAlive() && !e.isSpectator() && e.squaredDistanceTo(center) <= radius * radius)) {
			e.setVelocity(pushFor(e.getPos(), axis, center, world.random.nextDouble()));
			e.velocityModified = true;
			e.fallDistance = 0.0f;
		}

		if (m != null) {
			m.sprouts.remove(pos);
			regrowNear(world, m, pos, options.burstRegrowBlocks);
		}
	}

	/**
	 * Where a burst throws something at {@code at}: outward from the stem's axis (from the sprout if it's
	 * right on the axis, a random way if it's on both), at {@code burstPush}, and up at {@code burstLift}.
	 */
	public static Vec3d pushFor(Vec3d at, Vec3d axis, Vec3d sprout, double random) {
		FrostboundConfig.GiantMushroomOptions options = GiantMushrooms.options();
		double dx = at.x - axis.x, dz = at.z - axis.z;
		if (dx * dx + dz * dz < 0.25) {
			dx = at.x - sprout.x;
			dz = at.z - sprout.z;
		}
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0e-3) {
			double angle = random * Math.PI * 2.0;
			dx = Math.cos(angle);
			dz = Math.sin(angle);
			len = 1.0;
		}
		return new Vec3d(dx / len * options.burstPush, options.burstLift, dz / len * options.burstPush);
	}

	/**
	 * Grows back up to {@code count} missing cap blocks, nearest {@code pos} first, skipping spots something
	 * stands in. They come back {@link GiantCapBlock#REGROWN}: blighted, no drops, no spores. Picks the
	 * nearest missing block {@code count} times rather than sorting them all.
	 */
	public static int regrowNear(ServerWorld world, Mushroom m, BlockPos pos, int count) {
		BlockPos.Mutable p = new BlockPos.Mutable();
		LongArrayList skipped = null;
		int grown = 0;
		while (grown < count) {
			long best = 0;
			double bestDistance = Double.MAX_VALUE;
			for (int i = 0, n = m.capCount(); i < n; i++) {
				m.cap(i, p);
				double distance = p.getSquaredDistance(pos);
				if (distance < bestDistance && world.getBlockState(p).isAir() && (skipped == null || !skipped.contains(p.asLong()))) {
					best = p.asLong();
					bestDistance = distance;
				}
			}
			if (bestDistance == Double.MAX_VALUE) {
				break;
			}
			BlockPos at = BlockPos.fromLong(best);
			if (!world.getEntitiesByClass(LivingEntity.class, new Box(at), EntityPredicates.EXCEPT_SPECTATOR).isEmpty()) {
				if (skipped == null) {
					skipped = new LongArrayList();
				}
				skipped.add(best);
				continue;
			}
			world.setBlockState(at, m.capBlock().getDefaultState().with(GiantCapBlock.REGROWN, true));
			world.spawnParticles(ParticleTypes.CRIMSON_SPORE, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.0);
			grown++;
		}
		return grown;
	}
}

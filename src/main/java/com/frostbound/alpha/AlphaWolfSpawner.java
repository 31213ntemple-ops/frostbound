package com.frostbound.alpha;

import com.frostbound.config.FrostboundConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * Natural spawning: at night, a player out under the open sky in a snowy biome now and then has a pack
 * turn up nearby, announced by a long howl. Not before {@code firstDay}, never within {@code minSpacing}
 * blocks of another Alpha, and never in Peaceful or with mob spawning off.
 */
public final class AlphaWolfSpawner {
	private AlphaWolfSpawner() {}

	static void init() {
		ServerTickEvents.END_WORLD_TICK.register(AlphaWolfSpawner::tick);
	}

	private static void tick(ServerWorld world) {
		FrostboundConfig.Spawning config = FrostboundConfig.get().alphaWolf.spawning;
		int interval = Math.max(20, AlphaWolfEntity.ticks(config.checkSeconds));
		if (!config.enabled || world.getTime() % interval != 0 || world.getRegistryKey() != World.OVERWORLD
				|| world.getDifficulty() == Difficulty.PEACEFUL || !world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)
				|| !world.isNight() || world.getTimeOfDay() / 24000L + 1 < config.firstDay) {
			return;
		}
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!player.isSpectator() && !player.isCreative() && world.isSkyVisible(player.getBlockPos())
					&& world.random.nextFloat() < config.chance) {
				trySpawnNear(world, player.getBlockPos(), world.random);
			}
		}
	}

	/** Tries a few spots around {@code center} for a pack. Returns the Alpha, or null if nowhere fit. */
	@Nullable
	public static AlphaWolfEntity trySpawnNear(ServerWorld world, BlockPos center, Random random) {
		FrostboundConfig.Spawning config = FrostboundConfig.get().alphaWolf.spawning;
		double spacing = config.minSpacing;
		if (!world.getEntitiesByClass(AlphaWolfEntity.class, new Box(center).expand(spacing), alpha -> true).isEmpty()) {
			return null;
		}
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int distance = config.minDistance + random.nextInt(Math.max(1, config.maxDistance - config.minDistance + 1));
			int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
			if (!world.isChunkLoaded(x >> 4, z >> 4)) {
				continue;
			}
			BlockPos pos = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (canSpawnAt(world, pos)) {
				AlphaWolfEntity alpha = AlphaWolves.ALPHA_WOLF.create(world);
				if (alpha == null) {
					return null;
				}
				alpha.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0f, 0.0f);
				if (!world.isSpaceEmpty(alpha)) {
					continue;
				}
				alpha.initialize(world, world.getLocalDifficulty(pos), SpawnReason.EVENT, null);
				world.spawnEntity(alpha);
				// Heard from 64 blocks: the pack is out there.
				world.playSound(null, pos, SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.HOSTILE, 4.0f, 0.6f);
				return alpha;
			}
		}
		return null;
	}

	/** Open sky, a snowy biome, and solid, dry ground (snow, ice and grass all count). */
	public static boolean canSpawnAt(ServerWorld world, BlockPos pos) {
		BlockPos below = pos.down();
		return world.isSkyVisible(pos)
				&& world.getBiome(pos).value().getPrecipitation(pos) == Biome.Precipitation.SNOW
				&& world.getBlockState(below).isSolidBlock(world, below)
				&& world.getFluidState(pos).isEmpty() && world.getFluidState(below).isEmpty();
	}
}

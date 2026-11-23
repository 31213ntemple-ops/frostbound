package com.frostbound.alpha.den;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.Optional;

/**
 * The den's structure type ({@code frostbound:wolf_den}): one {@link WolfDenPiece} in the middle of
 * the chunk, its floor at the ground's average height there. Skipped on steep ground and below sea
 * level (lakes, marsh), so a den always has an arena to fight in.
 */
public class WolfDenStructure extends Structure {
	public static final MapCodec<WolfDenStructure> CODEC = createCodec(WolfDenStructure::new);
	/** Most the ground may rise or fall across the den before it isn't built there. */
	private static final int MAX_SLOPE = 7;

	public WolfDenStructure(Config config) {
		super(config);
	}

	@Override
	protected Optional<StructurePosition> getStructurePosition(Context context) {
		ChunkPos chunk = context.chunkPos();
		int x = chunk.getCenterX();
		int z = chunk.getCenterZ();
		int r = 8;
		int[] heights = {
				height(context, x, z), height(context, x - r, z - r), height(context, x + r, z - r),
				height(context, x - r, z + r), height(context, x + r, z + r)};
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		int sum = 0;
		for (int h : heights) {
			min = Math.min(min, h);
			max = Math.max(max, h);
			sum += h;
		}
		int floor = Math.round(sum / (float) heights.length);
		if (max - min > MAX_SLOPE || floor < context.chunkGenerator().getSeaLevel()) {
			return Optional.empty();
		}
		BlockPos center = new BlockPos(x, floor, z);
		long seed = context.random().nextLong();
		return Optional.of(new StructurePosition(center, collector -> collector.addPiece(new WolfDenPiece(center, seed))));
	}

	private static int height(Context context, int x, int z) {
		return context.chunkGenerator().getHeightInGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
	}

	@Override
	public StructureType<?> getType() {
		return WolfDens.STRUCTURE_TYPE;
	}
}

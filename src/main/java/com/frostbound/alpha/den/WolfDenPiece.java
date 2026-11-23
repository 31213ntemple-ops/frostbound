package com.frostbound.alpha.den;

import com.frostbound.Frostbound;
import com.frostbound.alpha.AlphaWolfEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The den as one structure piece: its blocks come from {@link WolfDenLayout} (the same seed gives
 * the same den in every chunk it spans), with foundations down to the ground and the space above
 * cleared. The chunk holding the centre also brings the sleeping pack.
 */
public class WolfDenPiece extends StructurePiece {
	public static final RegistryKey<LootTable> CHEST_LOOT = RegistryKey.of(RegistryKeys.LOOT_TABLE, Frostbound.id("chests/wolf_den"));

	private final BlockPos center;
	private final long seed;
	@Nullable
	private WolfDenLayout layout;

	public WolfDenPiece(BlockPos center, long seed) {
		super(WolfDens.PIECE, 0, box(center));
		this.center = center.toImmutable();
		this.seed = seed;
		this.setOrientation(null);
	}

	public WolfDenPiece(StructureContext context, NbtCompound nbt) {
		super(WolfDens.PIECE, nbt);
		this.center = NbtHelper.toBlockPos(nbt, "Center").orElse(BlockPos.ORIGIN);
		this.seed = nbt.getLong("Seed");
	}

	/**
	 * From the floor up: terrain adaptation flattens the ground up to the floor and clears above it, so
	 * the den sits in a natural clearing.
	 */
	private static BlockBox box(BlockPos center) {
		int r = WolfDenLayout.RADIUS;
		return new BlockBox(center.getX() - r, center.getY(), center.getZ() - r,
				center.getX() + r, center.getY() + WolfDenLayout.HEIGHT, center.getZ() + r);
	}

	@Override
	protected void writeNbt(StructureContext context, NbtCompound nbt) {
		nbt.put("Center", NbtHelper.fromBlockPos(this.center));
		nbt.putLong("Seed", this.seed);
	}

	public BlockPos center() {
		return this.center;
	}

	public WolfDenLayout layout() {
		if (this.layout == null) {
			this.layout = WolfDenLayout.generate(this.seed);
		}
		return this.layout;
	}

	@Override
	public void generate(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random,
			BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		place(world, this.center, this.layout(), chunkBox, true);
	}

	/**
	 * Builds the den at {@code center} (the floor's centre), only within {@code clip}; with
	 * {@code wolves}, and if the centre is inside {@code clip}, also the sleeping pack. Returns the
	 * Alpha if it brought one.
	 */
	@Nullable
	public static AlphaWolfEntity place(ServerWorldAccess world, BlockPos center, WolfDenLayout layout, BlockBox clip, boolean wolves) {
		// Clear the air above the floor (snow, grass, the odd log).
		for (BlockPos rel : layout.floor) {
			for (int y = 1; y <= WolfDenLayout.HEIGHT; y++) {
				BlockPos pos = center.add(rel.getX(), y, rel.getZ());
				if (clip.contains(pos) && !world.getBlockState(pos).isAir()) {
					world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
		// Foundations: stone down to whatever solid ground is under each floor block.
		for (BlockPos rel : layout.floor) {
			for (int y = -1; y >= -WolfDenLayout.FOUNDATION_DEPTH; y--) {
				BlockPos pos = center.add(rel.getX(), y, rel.getZ());
				if (!clip.contains(pos)) {
					break;
				}
				BlockState there = world.getBlockState(pos);
				if (there.isSolidBlock(world, pos) && !there.isIn(BlockTags.LEAVES) && !there.isIn(BlockTags.LOGS)) {
					break;
				}
				world.setBlockState(pos, ((y + rel.getX() + rel.getZ()) & 3) == 0 ? Blocks.MOSSY_COBBLESTONE.getDefaultState()
						: Blocks.COBBLESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
		for (Map.Entry<BlockPos, BlockState> entry : layout.blocks.entrySet()) {
			BlockPos pos = center.add(entry.getKey());
			if (clip.contains(pos)) {
				world.setBlockState(pos, entry.getValue(), Block.NOTIFY_LISTENERS);
			}
		}
		if (layout.chest != null) {
			BlockPos pos = center.add(layout.chest);
			if (clip.contains(pos) && world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
				chest.setLootTable(CHEST_LOOT, world.getRandom().nextLong() ^ pos.asLong());
			}
		}
		if (wolves && clip.contains(center)) {
			List<BlockPos> beds = layout.beds.stream().map(center::add).toList();
			return AlphaWolfEntity.populateDen(world, center, center.add(layout.alphaBed), beds, world.getRandom());
		}
		return null;
	}
}

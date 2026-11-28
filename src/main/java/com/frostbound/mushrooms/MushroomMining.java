package com.frostbound.mushrooms;

import com.frostbound.config.FrostboundConfig;
import com.frostbound.copper.CopperGear;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Harvesting a giant mushroom: carving the cap with a hoe, sweeping the stem's active layer with an axe,
 * working out which stem layer is active, and the cap falling apart. None of the per-break or per-tick
 * paths allocate position lists: they walk the shape's packed positions with a reused mutable position.
 */
public final class MushroomMining {
	/**
	 * The 26 blocks around a carved one (all within the 1.8-block carve reach), nearest first: by squared
	 * distance plus half the height difference (along the cap's surface before through it), then bottom-up
	 * and west to east, north to south, the order the cap's blocks are listed in. Each entry is {dx, dy, dz}.
	 */
	private static final int[][] CARVE_ORDER = carveOrder();

	private MushroomMining() {}

	private static int[][] carveOrder() {
		List<int[]> offsets = new ArrayList<>();
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx != 0 || dy != 0 || dz != 0) {
						offsets.add(new int[] {dx, dy, dz});
					}
				}
			}
		}
		offsets.sort(Comparator.comparingDouble(o -> o[0] * o[0] + o[1] * o[1] + o[2] * o[2] + 0.5 * Math.abs(o[1])));
		return offsets.toArray(int[][]::new);
	}

	/** The breaking progress per tick that takes {@code minTicks} (never one hit: at least 2 ticks). */
	public static float maxDelta(int minTicks) {
		return 1.0f / Math.max(2, minTicks);
	}

	// ---- Hoes ----

	/**
	 * How many cap blocks one break with this hoe takes ({@code hoeBlocks}); 1 for anything not in the hoes
	 * tag. A hoe that isn't a ToolItem (some mods) goes by its mining speed on a cap block.
	 */
	public static int hoeBlocks(ItemStack stack) {
		if (!stack.isIn(ItemTags.HOES)) {
			return 1;
		}
		if (stack.getItem() instanceof ToolItem tool) {
			return hoeBlocks(tool.getMaterial());
		}
		return Math.max(1, bySpeed(stack.getMiningSpeedMultiplier(GiantMushrooms.RED_CAP.getDefaultState()), options().hoeBlocks));
	}

	/**
	 * Cap blocks a hoe of this material takes. Vanilla's materials and copper by name; any other material
	 * by its tier, which mods set through the "incorrect for" block tag ({@link ToolMaterial#getInverseTag}),
	 * the same tag that decides what it can mine; a material with a tag of its own by its mining speed.
	 */
	public static int hoeBlocks(ToolMaterial material) {
		FrostboundConfig.HoeBlocks blocks = options().hoeBlocks;
		int count;
		if (material == ToolMaterials.WOOD) {
			count = blocks.wood;
		} else if (material == ToolMaterials.GOLD) {
			count = blocks.gold;
		} else if (material == ToolMaterials.STONE) {
			count = blocks.stone;
		} else if (material == CopperGear.TOOL_MATERIAL) {
			count = blocks.copper;
		} else if (material == ToolMaterials.IRON) {
			count = blocks.iron;
		} else if (material == ToolMaterials.DIAMOND) {
			count = blocks.diamond;
		} else if (material == ToolMaterials.NETHERITE) {
			count = blocks.netherite;
		} else {
			count = byTier(material.getInverseTag(), blocks);
			if (count < 0) {
				count = bySpeed(material.getMiningSpeedMultiplier(), blocks);
			}
		}
		return Math.max(1, count);
	}

	/** By the material's "incorrect for" tag, or -1 if it's not one of vanilla's (or copper's). */
	private static int byTier(TagKey<Block> tier, FrostboundConfig.HoeBlocks blocks) {
		if (tier == null) {
			return -1;
		} else if (tier.equals(BlockTags.INCORRECT_FOR_NETHERITE_TOOL)) {
			return blocks.netherite;
		} else if (tier.equals(BlockTags.INCORRECT_FOR_DIAMOND_TOOL)) {
			return blocks.diamond;
		} else if (tier.equals(BlockTags.INCORRECT_FOR_IRON_TOOL)) {
			return blocks.iron;
		} else if (tier.equals(CopperGear.INCORRECT_FOR_COPPER_TOOL)) {
			return blocks.copper;
		} else if (tier.equals(BlockTags.INCORRECT_FOR_STONE_TOOL)) {
			return blocks.stone;
		} else if (tier.equals(BlockTags.INCORRECT_FOR_GOLD_TOOL)) {
			return blocks.gold;
		} else if (tier.equals(BlockTags.INCORRECT_FOR_WOODEN_TOOL)) {
			return blocks.wood;
		}
		return -1;
	}

	/** By mining speed: under 4 → wood, under 6 → stone, under 8 → iron, under 9 → diamond, else netherite. */
	private static int bySpeed(float speed, FrostboundConfig.HoeBlocks blocks) {
		return speed < 4 ? blocks.wood : speed < 6 ? blocks.stone : speed < 8 ? blocks.iron : speed < 9 ? blocks.diamond : blocks.netherite;
	}

	// ---- Carving the cap, sweeping the stem ----

	/**
	 * A hoe broke the cap block at {@code pos}: {@code count - 1} more of the mushroom's cap blocks next to
	 * it break too, nearest first ({@link #CARVE_ORDER}), but never one under a sprout (break the sprout) or
	 * one something stands on (no falling through your own floor). Returns how many more broke.
	 */
	public static int carve(ServerWorld world, BlockPos pos, Entity breaker, int count) {
		Mushroom m = GiantMushrooms.find(world, pos);
		if (m == null || count <= 1) {
			return 0;
		}
		// Everything that could be standing on one of the blocks around, fetched once.
		List<LivingEntity> standing = world.getEntitiesByClass(LivingEntity.class,
				new Box(pos.getX() - 1, pos.getY(), pos.getZ() - 1, pos.getX() + 2, pos.getY() + 2.25, pos.getZ() + 2),
				e -> !e.isSpectator() && e.isOnGround());
		long[] targets = new long[count - 1];
		int found = 0;
		BlockPos.Mutable p = new BlockPos.Mutable(), above = new BlockPos.Mutable();
		for (int[] offset : CARVE_ORDER) {
			if (found == targets.length) {
				break;
			}
			p.set(pos.getX() + offset[0], pos.getY() + offset[1], pos.getZ() + offset[2]);
			if (m.isCap(p) && world.getBlockState(p).getBlock() instanceof GiantCapBlock
					&& !world.getBlockState(above.set(p.getX(), p.getY() + 1, p.getZ())).isOf(GiantMushrooms.SPROUT)
					&& !standsOn(standing, p)) {
				targets[found++] = p.asLong();
			}
		}
		// Picked first, broken after, so breaking one doesn't change which others qualify.
		harvest(world, targets, found, breaker);
		return found;
	}

	/** An axe broke an active stem block at {@code pos}: the rest of that layer within {@code radius} (across) breaks too. */
	public static int sweep(ServerWorld world, BlockPos pos, Entity breaker, double radius) {
		Mushroom m = GiantMushrooms.find(world, pos);
		int layer = m == null ? -1 : pos.getY() - m.origin.getY();
		if (m == null || radius <= 0 || layer < 0 || layer >= m.layers()) {
			return 0;
		}
		double r2 = radius * radius;
		int start = m.layerStart(layer), end = m.layerStart(layer + 1);
		long[] targets = new long[end - start];
		int found = 0;
		BlockPos.Mutable p = new BlockPos.Mutable();
		for (int i = start; i < end; i++) {
			m.stem(i, p);
			if (!p.equals(pos) && p.getSquaredDistance(pos) <= r2) {
				BlockState state = world.getBlockState(p);
				if (state.isOf(GiantMushrooms.STEM) && state.get(GiantStemBlock.ACTIVE)) {
					targets[found++] = p.asLong();
				}
			}
		}
		harvest(world, targets, found, breaker);
		return found;
	}

	/** Breaks the first {@code count} blocks (dropping their loot unless {@code breaker} is in creative) and gives {@code breaker} their spores. */
	private static void harvest(ServerWorld world, long[] targets, int count, Entity breaker) {
		boolean drop = !(breaker instanceof PlayerEntity player && player.isCreative());
		for (int i = 0; i < count; i++) {
			BlockPos p = BlockPos.fromLong(targets[i]);
			BlockState state = world.getBlockState(p);
			if (world.breakBlock(p, drop, breaker)) {
				Spores.harvest(world, p, state, breaker);
			}
		}
	}

	/** Whether one of {@code entities} stands on the block at {@code pos}. */
	private static boolean standsOn(List<LivingEntity> entities, BlockPos pos) {
		for (int i = 0, n = entities.size(); i < n; i++) {
			if (entities.get(i).getBoundingBox().intersects(pos.getX(), pos.getY() + 1.0, pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.25, pos.getZ() + 1.0)) {
				return true;
			}
		}
		return false;
	}

	// ---- The stem's active layer, the cap falling apart ----

	/**
	 * Lets the cap fall apart once only {@code capCollapseBelow} of it is left, then works out the stem's
	 * active layer: none while any cap block is left, otherwise the highest layer that still has a block.
	 * Only the layers that change are touched (the whole stem the first time). Forgets the mushroom once
	 * it's all gone.
	 */
	public static void refresh(ServerWorld world, Mushroom m) {
		BlockPos.Mutable p = new BlockPos.Mutable();
		int left = 0;
		for (int i = 0, n = m.capCount(); i < n; i++) {
			if (world.getBlockState(m.cap(i, p)).getBlock() instanceof GiantCapBlock) {
				left++;
			}
		}
		if (left > 0 && left <= Math.floor(m.capCount() * options().capCollapseBelow)) {
			collapse(world, m);
			left = 0;
		}
		int top = topLayer(world, m, p);
		int active = left > 0 ? Mushroom.NONE : top;
		if (m.activeLayer == Mushroom.UNKNOWN) {
			for (int layer = 0; layer < m.layers(); layer++) {
				setActive(world, m, layer, layer == active, p);
			}
		} else if (m.activeLayer != active) {
			if (m.activeLayer != Mushroom.NONE) {
				setActive(world, m, m.activeLayer, false, p);
			}
			if (active != Mushroom.NONE) {
				setActive(world, m, active, true, p);
			}
		}
		m.activeLayer = active;
		if (left == 0 && top == Mushroom.NONE) {
			MushroomStore.get(world).remove(m);
		}
	}

	/** The highest stem layer with a stem block left, or NONE. */
	private static int topLayer(ServerWorld world, Mushroom m, BlockPos.Mutable p) {
		for (int layer = m.layers() - 1; layer >= 0; layer--) {
			for (int i = m.layerStart(layer), end = m.layerStart(layer + 1); i < end; i++) {
				if (world.getBlockState(m.stem(i, p)).isOf(GiantMushrooms.STEM)) {
					return layer;
				}
			}
		}
		return Mushroom.NONE;
	}

	private static void setActive(ServerWorld world, Mushroom m, int layer, boolean active, BlockPos.Mutable p) {
		for (int i = m.layerStart(layer), end = m.layerStart(layer + 1); i < end; i++) {
			BlockState state = world.getBlockState(m.stem(i, p));
			if (state.isOf(GiantMushrooms.STEM) && state.get(GiantStemBlock.ACTIVE) != active) {
				world.setBlockState(p, state.with(GiantStemBlock.ACTIVE, active), Block.NOTIFY_LISTENERS);
			}
		}
	}

	/**
	 * What's left of the cap falls apart: each block drops its loot (and gives whoever was mining it its
	 * spores) and tumbles down as a falling block that crumbles where it lands (it never lands as a block).
	 * Returns how many fell.
	 */
	public static int collapse(ServerWorld world, Mushroom m) {
		boolean creative = m.miner instanceof PlayerEntity player && player.isCreative();
		BlockPos.Mutable p = new BlockPos.Mutable();
		int fell = 0;
		for (int i = 0, n = m.capCount(); i < n; i++) {
			BlockState state = world.getBlockState(m.cap(i, p));
			if (state.getBlock() instanceof GiantCapBlock) {
				BlockPos at = p.toImmutable();
				if (!creative) {
					Block.dropStacks(state, world, at);
				}
				Spores.harvest(world, at, state, m.miner);
				FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world, at, state);
				falling.setDestroyedOnLanding();
				falling.dropItem = false;
				fell++;
			}
		}
		if (fell > 0) {
			BlockPos top = m.origin.add(m.shape.capCenter);
			world.playSound(null, top, SoundEvents.BLOCK_WART_BLOCK_BREAK, SoundCategory.BLOCKS, 2.0f, 0.6f);
			world.playSound(null, top, SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.BLOCKS, 0.7f, 0.7f);
		}
		return fell;
	}

	private static FrostboundConfig.GiantMushroomOptions options() {
		return GiantMushrooms.options();
	}
}

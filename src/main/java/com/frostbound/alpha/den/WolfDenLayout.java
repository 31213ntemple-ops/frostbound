package com.frostbound.alpha.den;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationPropertyHelper;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Alpha's den: an open, roofless ring of old mossy stone brick, laid out from a seed so every
 * den is a little different but each one is the same arena to fight in. Coordinates are relative to
 * the centre of the floor (y = 0 is the floor itself; things stand at y = 1).
 *
 * <pre>
 *                 ▓▓ ░  ▓▓▓   ░ ▓▓          ▓ low broken wall (1–2 high), with gaps to walk in
 *             ▓      ██    ██      ▓        █ pillars (2×2): some standing, some broken, one may lie fallen
 *           ▓    w        w     w    ▓      w a pack wolf's spot (moss to lie on)
 *           ░  ██     ┌─────┐    ██  ░      ┌┐ the dais: stepped, chiseled ring, moss on top — the Alpha sleeps here
 *           ▓     w   │  A  │   w    ▓
 *             ▓  ██   └─────┘  ██  ▓        bones, a skull or two, and a chest at a pillar's foot
 *                 ▓▓   w   ░ ▓▓▓
 * </pre>
 *
 * About 21 blocks across. The pillars and walls matter in the fight: the Alpha's Charge stops dead
 * (and stuns it) against anything solid, and the den is lit by a couple of lanterns so its tells
 * can be seen at night.
 */
public final class WolfDenLayout {
	/** Furthest any block reaches from the centre, and how high anything stands above the floor. */
	public static final int RADIUS = 12;
	public static final int HEIGHT = 8;
	/** How far below the floor foundations may reach to meet the ground. */
	public static final int FOUNDATION_DEPTH = 8;
	public static final int PILLARS = 6;
	public static final double PILLAR_RING = 7.5;
	public static final double BED_RING = 5.0;

	/** Every block of the den (floor first, then what stands on it), relative to the centre. */
	public final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
	/** Floor columns that get a foundation down to the ground. */
	public final Set<BlockPos> floor = new HashSet<>();
	/** Where the Alpha sleeps (on the dais) and where each pack wolf does. */
	public BlockPos alphaBed = BlockPos.ORIGIN;
	public final List<BlockPos> beds = new ArrayList<>();
	/** The chest, if any (its loot table is set when placed). */
	public BlockPos chest;
	/** Centres of the standing pillars (bases at y = 1), for tests and the Charge. */
	public final List<BlockPos> pillars = new ArrayList<>();

	private final Random random;
	/** Cells kept clear (beds and the way onto the dais). */
	private final Set<BlockPos> reserved = new HashSet<>();

	private WolfDenLayout(long seed) {
		this.random = Random.create(seed);
	}

	public static WolfDenLayout generate(long seed) {
		WolfDenLayout layout = new WolfDenLayout(seed);
		layout.build();
		return layout;
	}

	private void build() {
		double phase1 = this.random.nextDouble() * Math.PI * 2.0;
		double phase2 = this.random.nextDouble() * Math.PI * 2.0;
		double pillarStart = this.random.nextDouble() * Math.PI * 2.0;

		// The Alpha's spot and the pack's, kept clear of rubble.
		this.alphaBed = new BlockPos(0, 2, 0);
		for (int i = 0; i < PILLARS; i++) {
			double a = pillarStart + (i + 0.5) * Math.PI * 2.0 / PILLARS + (this.random.nextDouble() - 0.5) * 0.3;
			double r = BED_RING + (this.random.nextDouble() - 0.5) * 0.8;
			BlockPos bed = new BlockPos(MathHelper.floor(Math.cos(a) * r + 0.5), 1, MathHelper.floor(Math.sin(a) * r + 0.5));
			this.beds.add(bed);
			this.reserve(bed, 1);
		}

		this.buildFloor(phase1, phase2);
		this.buildDais();
		this.buildWall(phase1, phase2);
		this.buildPillars(pillarStart);
		this.scatterBones();
		this.scatterMoss();
		for (BlockPos bed : this.beds) {
			// A patch of moss to curl up on.
			this.set(bed, Blocks.MOSS_CARPET.getDefaultState());
		}
	}

	// --- Pieces ---

	private void buildFloor(double phase1, double phase2) {
		for (int x = -RADIUS; x <= RADIUS; x++) {
			for (int z = -RADIUS; z <= RADIUS; z++) {
				double d = Math.sqrt(x * x + z * z);
				double angle = Math.atan2(z, x);
				double edge = 9.8 + 0.8 * Math.sin(angle * 3.0 + phase1) + 0.5 * Math.sin(angle * 7.0 + phase2);
				boolean inside = d <= edge;
				boolean crumbled = !inside && d <= edge + 1.8 && this.random.nextFloat() < 0.3f;
				if (!inside && !crumbled) {
					continue;
				}
				BlockState state;
				if (crumbled) {
					state = this.pick(Blocks.MOSSY_COBBLESTONE, 3, Blocks.COBBLESTONE, 2, Blocks.CRACKED_STONE_BRICKS, 2, Blocks.MOSSY_STONE_BRICKS, 2);
				} else if (d > 5.6 && d < 6.4) {
					// A ring of plainer brick around the middle, like an old processional path.
					state = this.pick(Blocks.STONE_BRICKS, 6, Blocks.CRACKED_STONE_BRICKS, 2, Blocks.MOSSY_STONE_BRICKS, 2);
				} else {
					state = this.pick(Blocks.MOSSY_STONE_BRICKS, 40, Blocks.STONE_BRICKS, 22, Blocks.CRACKED_STONE_BRICKS, 16,
							Blocks.MOSSY_COBBLESTONE, 8, Blocks.COBBLESTONE, 5, Blocks.ANDESITE, 4);
				}
				BlockPos pos = new BlockPos(x, 0, z);
				this.blocks.put(pos, state);
				this.floor.add(pos);
			}
		}
	}

	private void buildDais() {
		for (int x = -5; x <= 5; x++) {
			for (int z = -5; z <= 5; z++) {
				double d = Math.sqrt(x * x + z * z);
				BlockPos pos = new BlockPos(x, 1, z);
				if (d <= 2.3) {
					this.set(pos, this.pick(Blocks.MOSSY_STONE_BRICKS, 5, Blocks.STONE_BRICKS, 2, Blocks.CRACKED_STONE_BRICKS, 1));
					if (d <= 1.6 || this.random.nextFloat() < 0.4f) {
						this.set(pos.up(), Blocks.MOSS_CARPET.getDefaultState());
					}
				} else if (d <= 3.3) {
					// The rim: chiseled stones at the eight points, worn brick between.
					double angle = Math.atan2(z, x) / (Math.PI / 4.0);
					boolean point = Math.abs(angle - Math.round(angle)) < 0.18;
					this.set(pos, point ? Blocks.CHISELED_STONE_BRICKS.getDefaultState()
							: this.pick(Blocks.STONE_BRICKS, 3, Blocks.MOSSY_STONE_BRICKS, 3, Blocks.CRACKED_STONE_BRICKS, 2));
				} else if (d <= 4.3 && this.random.nextFloat() < 0.85f) {
					// A half step up all round.
					Block slab = this.random.nextFloat() < 0.4f ? Blocks.MOSSY_STONE_BRICK_SLAB : Blocks.STONE_BRICK_SLAB;
					this.set(pos, slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
				}
			}
		}
	}

	private void buildWall(double phase1, double phase2) {
		for (BlockPos floorPos : List.copyOf(this.floor)) {
			int x = floorPos.getX();
			int z = floorPos.getZ();
			double d = Math.sqrt(x * x + z * z);
			if (d < 9.2 || d > 10.4) {
				continue;
			}
			double angle = Math.atan2(z, x);
			double level = Math.sin(angle * 4.0 + phase2) + 0.6 * Math.sin(angle * 9.0 + phase1);
			if (level < 0.15) {
				continue; // a gap
			}
			BlockPos pos = floorPos.up();
			this.set(pos, this.pick(Blocks.MOSSY_STONE_BRICKS, 5, Blocks.CRACKED_STONE_BRICKS, 3, Blocks.STONE_BRICKS, 2));
			if (level > 0.95) {
				this.set(pos.up(), this.random.nextFloat() < 0.55f
						? this.pick(Blocks.MOSSY_STONE_BRICKS, 3, Blocks.CRACKED_STONE_BRICKS, 2)
						: Blocks.MOSSY_STONE_BRICK_SLAB.getDefaultState());
			} else if (level > 0.6 && this.random.nextFloat() < 0.5f) {
				this.set(pos.up(), Blocks.STONE_BRICK_SLAB.getDefaultState());
			}
		}
	}

	private void buildPillars(double start) {
		// Maybe one of the broken ones (odd) has toppled right over; the even ones always stand.
		int fallen = this.random.nextFloat() < 0.6f ? 1 + 2 * this.random.nextInt(PILLARS / 2) : -1;
		int lanterns = 0;
		boolean chestPlaced = false;
		for (int i = 0; i < PILLARS; i++) {
			double a = start + i * Math.PI * 2.0 / PILLARS;
			int px = MathHelper.floor(Math.cos(a) * PILLAR_RING);
			int pz = MathHelper.floor(Math.sin(a) * PILLAR_RING);
			// Every other pillar still stands tall; the rest are broken stumps.
			boolean standing = i % 2 == 0;
			int height = i == fallen ? 1 + this.random.nextInt(2) : standing ? 5 + this.random.nextInt(2) : 2 + this.random.nextInt(2);
			for (int dx = 0; dx <= 1; dx++) {
				for (int dz = 0; dz <= 1; dz++) {
					int h = height;
					if (!standing && i != fallen && this.random.nextFloat() < 0.5f) {
						h -= 1; // jagged, broken top
					}
					for (int y = 1; y <= h; y++) {
						BlockState state = y == 1 || (standing && y == h)
								? Blocks.CHISELED_STONE_BRICKS.getDefaultState()
								: this.pick(Blocks.MOSSY_STONE_BRICKS, 4, Blocks.STONE_BRICKS, 3, Blocks.CRACKED_STONE_BRICKS, 3);
						this.set(new BlockPos(px + dx, y, pz + dz), state);
					}
				}
			}
			this.pillars.add(new BlockPos(px, 1, pz));
			Direction out = Direction.fromRotation(Math.toDegrees(a) - 90.0);

			if (standing) {
				// Moss creeping down the outer faces, and a lantern on two of them so the fight can be seen at night.
				this.vines(px, pz, height);
				if (lanterns < 2) {
					this.set(new BlockPos(px + this.random.nextInt(2), height + 1, pz + this.random.nextInt(2)),
							Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false));
					lanterns++;
				}
				if (!chestPlaced) {
					// Tucked against the pillar's inner face.
					BlockPos chestPos = this.innerFoot(px, pz, a);
					if (!this.reserved.contains(chestPos)) {
						Direction facing = Direction.fromRotation(Math.toDegrees(a) + 90.0);
						this.set(chestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing));
						this.chest = chestPos;
						chestPlaced = true;
					}
				}
			} else {
				// Rubble: what fell off the top, scattered outwards.
				int pieces = 2 + this.random.nextInt(3);
				for (int k = 0; k < pieces; k++) {
					double ra = a + (this.random.nextDouble() - 0.5) * 0.9;
					double rr = PILLAR_RING + 1.5 + this.random.nextDouble() * 2.0;
					BlockPos pos = new BlockPos(MathHelper.floor(Math.cos(ra) * rr), 1, MathHelper.floor(Math.sin(ra) * rr));
					if (this.free(pos)) {
						this.set(pos, this.random.nextFloat() < 0.5f
								? Blocks.MOSSY_STONE_BRICK_SLAB.getDefaultState()
								: this.pick(Blocks.CRACKED_STONE_BRICKS, 2, Blocks.MOSSY_STONE_BRICKS, 2,
										Blocks.STONE_BRICK_STAIRS, 1));
					}
				}
			}
			if (i == fallen) {
				// The shaft lies where it fell, across the floor.
				Direction along = this.random.nextBoolean() ? out.rotateYClockwise() : out.rotateYCounterclockwise();
				BlockPos base = new BlockPos(px, 1, pz).offset(along, along.getDirection() == Direction.AxisDirection.POSITIVE ? 2 : 1);
				int length = 3 + this.random.nextInt(2);
				for (int k = 0; k < length; k++) {
					BlockPos pos = base.offset(along, k);
					if (this.free(pos)) {
						this.set(pos, k == length - 1 ? Blocks.CHISELED_STONE_BRICKS.getDefaultState()
								: this.pick(Blocks.MOSSY_STONE_BRICKS, 3, Blocks.CRACKED_STONE_BRICKS, 2));
					}
				}
			}
		}
	}

	/** The floor cell just inside a pillar, towards the centre. */
	private BlockPos innerFoot(int px, int pz, double angle) {
		int ix = Math.cos(angle) > 0 ? px - 1 : px + 2;
		int iz = Math.sin(angle) > 0 ? pz - 1 : pz + 2;
		return Math.abs(Math.cos(angle)) > Math.abs(Math.sin(angle)) ? new BlockPos(ix, 1, pz) : new BlockPos(px, 1, iz);
	}

	private void vines(int px, int pz, int height) {
		for (int dx = -1; dx <= 2; dx++) {
			for (int dz = -1; dz <= 2; dz++) {
				boolean side = (dx == -1 || dx == 2) != (dz == -1 || dz == 2);
				if (!side || this.random.nextFloat() > 0.35f) {
					continue;
				}
				Direction toPillar = dx == -1 ? Direction.EAST : dx == 2 ? Direction.WEST : dz == -1 ? Direction.SOUTH : Direction.NORTH;
				int length = 1 + this.random.nextInt(Math.max(1, height - 1));
				for (int k = 0; k < length; k++) {
					BlockPos pos = new BlockPos(px + dx, height - k, pz + dz);
					if (!this.free(pos)) {
						break;
					}
					this.set(pos, Blocks.VINE.getDefaultState().with(VineBlock.getFacingProperty(toPillar), true));
				}
			}
		}
	}

	private void scatterBones() {
		int remains = 3 + this.random.nextInt(2);
		for (int i = 0; i < remains; i++) {
			double a = this.random.nextDouble() * Math.PI * 2.0;
			double r = 3.8 + this.random.nextDouble() * 4.5;
			BlockPos pos = new BlockPos(MathHelper.floor(Math.cos(a) * r), 1, MathHelper.floor(Math.sin(a) * r));
			Direction.Axis axis = this.random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
			if (this.random.nextFloat() < 0.5f) {
				// Half sunk into the floor.
				if (this.blocks.containsKey(pos.down())) {
					this.set(pos.down(), Blocks.BONE_BLOCK.getDefaultState().with(PillarBlock.AXIS, axis));
				}
			} else if (this.free(pos)) {
				this.set(pos, Blocks.BONE_BLOCK.getDefaultState().with(PillarBlock.AXIS, axis));
			}
			BlockPos skull = pos.offset(axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST);
			if (this.random.nextFloat() < 0.45f && this.free(skull) && this.blocks.containsKey(skull.down())) {
				this.set(skull, Blocks.SKELETON_SKULL.getDefaultState().with(SkullBlock.ROTATION,
						this.random.nextInt(RotationPropertyHelper.getMax() + 1)));
			}
		}
	}

	private void scatterMoss() {
		for (BlockPos floorPos : List.copyOf(this.floor)) {
			BlockPos above = floorPos.up();
			if (this.free(above) && this.random.nextFloat() < 0.22f) {
				this.set(above, Blocks.MOSS_CARPET.getDefaultState());
			}
		}
	}

	// --- Helpers ---

	private void reserve(BlockPos pos, int radius) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				this.reserved.add(pos.add(dx, 0, dz));
				this.reserved.add(pos.add(dx, 1, dz));
			}
		}
	}

	/** Nothing placed there yet, and not kept clear. */
	private boolean free(BlockPos pos) {
		return !this.blocks.containsKey(pos) && !this.reserved.contains(pos);
	}

	private void set(BlockPos pos, BlockState state) {
		if (this.reserved.contains(pos) && pos.getY() >= 1 && !state.isOf(Blocks.MOSS_CARPET)) {
			return;
		}
		this.blocks.put(pos, state);
	}

	/** A weighted random pick: block, weight, block, weight, ... */
	private BlockState pick(Object... options) {
		int total = 0;
		for (int i = 1; i < options.length; i += 2) {
			total += (Integer) options[i];
		}
		int roll = this.random.nextInt(total);
		for (int i = 0; i < options.length; i += 2) {
			roll -= (Integer) options[i + 1];
			if (roll < 0) {
				return this.oriented((Block) options[i]);
			}
		}
		return this.oriented((Block) options[0]);
	}

	private BlockState oriented(Block block) {
		BlockState state = block.getDefaultState();
		if (state.contains(StairsBlock.FACING)) {
			state = state.with(StairsBlock.FACING, Direction.Type.HORIZONTAL.random(this.random));
		}
		return state;
	}
}

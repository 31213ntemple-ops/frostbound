package com.frostbound.mushrooms;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ToolItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import com.frostbound.copper.CopperGear;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Giant mushrooms: big red and brown mushrooms you harvest in two parts.
 *
 *  1. The cap, with a hoe. Cap blocks never break in one hit, and a hoe carves a few blocks with each
 *     break, more with a better hoe (1 for wood up to 5 for netherite, {@code hoeBlocks}). Once only
 *     {@code capCollapseBelow} of the cap is left, the rest falls apart. While you mine, sprouts (vanilla mushrooms) grow on
 *     the cap near you, each with a countdown (2 s); break it in time or it bursts, throwing everything
 *     near it outward off the cap and growing some of the cap back.
 *  2. The stem, with an axe, once the whole cap is gone: from the top down, a layer at a time. Only the
 *     top (active) layer mines quickly (an axe sweeps a patch of it, {@code stemSweepRadius}); the rest
 *     are very slow.
 *
 * They don't grow in the world yet: place them with {@code /frostbound mushroom place <shape>}
 * (shapes in {@link GiantMushroomShape}). Each placed mushroom is remembered (per world, in saved
 * data) so its blocks know which mushroom they belong to: what's still cap, which stem layer is on top,
 * and what a burst may grow back. Balance numbers are in {@code giantMushrooms} in the config.
 */
public final class GiantMushrooms {
	private static final FrostboundConfig.GiantMushroomOptions OPTIONS = FrostboundConfig.get().giantMushrooms;

	public static final Block RED_CAP = Registry.register(Registries.BLOCK, Frostbound.id("giant_red_mushroom_cap"),
			new GiantCapBlock(AbstractBlock.Settings.copy(Blocks.RED_MUSHROOM_BLOCK)
					.strength(OPTIONS.capHardness).pistonBehavior(PistonBehavior.BLOCK).nonOpaque()));
	public static final Block BROWN_CAP = Registry.register(Registries.BLOCK, Frostbound.id("giant_brown_mushroom_cap"),
			new GiantCapBlock(AbstractBlock.Settings.copy(Blocks.BROWN_MUSHROOM_BLOCK)
					.strength(OPTIONS.capHardness).pistonBehavior(PistonBehavior.BLOCK).nonOpaque()));
	public static final Block STEM = Registry.register(Registries.BLOCK, Frostbound.id("giant_mushroom_stem"),
			new GiantStemBlock(AbstractBlock.Settings.copy(Blocks.MUSHROOM_STEM)
					.strength(OPTIONS.stemHardness).pistonBehavior(PistonBehavior.BLOCK)));
	public static final Block SPROUT = Registry.register(Registries.BLOCK, Frostbound.id("mushroom_sprout"),
			new MushroomSproutBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.RED).noCollision().breakInstantly().nonOpaque().dropsNothing()
					.luminance(state -> 5).sounds(BlockSoundGroup.FUNGUS).pistonBehavior(PistonBehavior.DESTROY)));
	public static final BlockEntityType<MushroomSproutBlockEntity> SPROUT_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE, Frostbound.id("mushroom_sprout"),
			BlockEntityType.Builder.create(MushroomSproutBlockEntity::new, SPROUT).build(null));

	/**
	 * What a cap or stem block drops, 10% of the time (the loot tables in
	 * data/frostbound/loot_table/blocks/giant_*.json); never a cap block a burst grew back. The player also
	 * gets 5–7 {@link Spores} (a count, not an item). No use yet.
	 */
	public static final Item MUSHROOM_SAMPLE = Registry.register(Registries.ITEM, Frostbound.id("mushroom_sample"), new Item(new Item.Settings()));

	/** Ticks before bursting that a sprout hisses. */
	private static final int HISS_TICKS = 10;
	/** Farthest a carved block can be from the one hit: next to it, diagonals included. */
	private static final double CARVE_REACH = 1.8;
	/** How far (blocks, from the mushroom's origin) the regrow/remove commands look for one. */
	private static final double COMMAND_REACH = 48.0;

	private GiantMushrooms() {}

	// ---- One mushroom -------------------------------------------------------------------------

	/** A placed mushroom. Origin and shape are saved; the rest only matters while someone's mining. */
	public static final class Mushroom {
		public final BlockPos origin;
		public final GiantMushroomShape shape;
		long engagedUntil = -1;
		long nextSprout;
		Entity miner;
		BlockPos lastHit;
		final Set<BlockPos> sprouts = new LinkedHashSet<>();

		Mushroom(BlockPos origin, GiantMushroomShape shape) {
			this.origin = origin.toImmutable();
			this.shape = shape;
		}

		public boolean contains(BlockPos pos) {
			BlockPos rel = pos.subtract(origin);
			return shape.bounds.contains(rel) && (shape.cap.contains(rel) || shape.stem.contains(rel));
		}

		public boolean isCap(BlockPos pos) {
			return shape.cap.contains(pos.subtract(origin));
		}

		public List<BlockPos> capPositions() {
			return shape.cap.stream().map(origin::add).toList();
		}

		public List<BlockPos> stemPositions() {
			return shape.stem.stream().map(origin::add).toList();
		}

		public Block capBlock() {
			return shape.red ? RED_CAP : BROWN_CAP;
		}

		/** The middle of the stem's top, where bursts push away from. */
		public Vec3d axis() {
			return Vec3d.ofBottomCenter(origin.add(shape.capCenter));
		}

		public boolean isEngaged(long now) {
			return engagedUntil >= now;
		}

		/** Sprouts this mushroom grew that are still there. */
		public Set<BlockPos> sprouts() {
			return sprouts;
		}
	}

	/** Every placed mushroom in one world. */
	static final class Store extends PersistentState {
		final List<Mushroom> mushrooms = new ArrayList<>();
		/** Mushrooms with a block changed this tick; their active layer is worked out again at the end of the tick. */
		final Set<Mushroom> changed = new LinkedHashSet<>();

		@Override
		public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			NbtList list = new NbtList();
			for (Mushroom m : mushrooms) {
				NbtCompound entry = new NbtCompound();
				entry.putInt("X", m.origin.getX());
				entry.putInt("Y", m.origin.getY());
				entry.putInt("Z", m.origin.getZ());
				entry.putString("Shape", m.shape.id());
				list.add(entry);
			}
			nbt.put("Mushrooms", list);
			return nbt;
		}

		static Store read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			Store store = new Store();
			for (NbtElement element : nbt.getList("Mushrooms", NbtElement.COMPOUND_TYPE)) {
				NbtCompound entry = (NbtCompound) element;
				GiantMushroomShape shape = GiantMushroomShape.byId(entry.getString("Shape"));
				if (shape != null) {
					store.mushrooms.add(new Mushroom(new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")), shape));
				}
			}
			return store;
		}
	}

	private static final PersistentState.Type<Store> STORE_TYPE = new PersistentState.Type<>(Store::new, Store::read, null);

	static Store store(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(STORE_TYPE, Frostbound.MOD_ID + "_giant_mushrooms");
	}

	/** Every mushroom placed in this world. */
	public static List<Mushroom> all(ServerWorld world) {
		return List.copyOf(store(world).mushrooms);
	}

	/** The mushroom this position is part of (a cap or stem spot, whatever's there now), or null. */
	public static Mushroom find(ServerWorld world, BlockPos pos) {
		for (Mushroom m : store(world).mushrooms) {
			if (m.contains(pos)) {
				return m;
			}
		}
		return null;
	}

	/** Nearest mushroom whose origin is within {@code reach} of {@code pos}, or null. */
	public static Mushroom nearest(ServerWorld world, Vec3d pos, double reach) {
		return store(world).mushrooms.stream()
				.filter(m -> Vec3d.ofBottomCenter(m.origin).squaredDistanceTo(pos) <= reach * reach)
				.min(Comparator.comparingDouble(m -> Vec3d.ofBottomCenter(m.origin).squaredDistanceTo(pos)))
				.orElse(null);
	}

	// ---- Placing, regrowing, removing ---------------------------------------------------------

	/** Builds a whole mushroom with its stem's bottom middle at {@code origin}, over whatever is there. */
	public static Mushroom place(ServerWorld world, BlockPos origin, GiantMushroomShape shape) {
		Mushroom m = new Mushroom(origin, shape);
		Store store = store(world);
		store.mushrooms.add(m);
		store.markDirty();
		regrow(world, m);
		return m;
	}

	/** Puts back every missing block of the mushroom (and clears its sprouts). */
	public static void regrow(ServerWorld world, Mushroom m) {
		for (BlockPos p : m.sprouts) {
			if (world.getBlockState(p).isOf(SPROUT)) {
				world.setBlockState(p, Blocks.AIR.getDefaultState());
			}
		}
		m.sprouts.clear();
		for (BlockPos p : m.stemPositions()) {
			world.setBlockState(p, STEM.getDefaultState());
		}
		for (BlockPos p : m.capPositions()) {
			world.setBlockState(p, m.capBlock().getDefaultState());
		}
		m.engagedUntil = -1;
		refresh(world, m);
	}

	/** Takes the whole mushroom away and forgets it. */
	public static void remove(ServerWorld world, Mushroom m) {
		Store store = store(world);
		store.mushrooms.remove(m);
		store.markDirty();
		for (BlockPos p : m.sprouts) {
			if (world.getBlockState(p).isOf(SPROUT)) {
				world.setBlockState(p, Blocks.AIR.getDefaultState());
			}
		}
		for (BlockPos p : m.capPositions()) {
			if (world.getBlockState(p).getBlock() instanceof GiantCapBlock) {
				world.setBlockState(p, Blocks.AIR.getDefaultState());
			}
		}
		for (BlockPos p : m.stemPositions()) {
			if (world.getBlockState(p).isOf(STEM)) {
				world.setBlockState(p, Blocks.AIR.getDefaultState());
			}
		}
		store.changed.remove(m);
	}

	// ---- The stem's active layer ----------------------------------------------------------------

	/** A cap or stem block went away (or came back): look at its mushroom again at the end of the tick. */
	static void blockChanged(ServerWorld world, BlockPos pos) {
		Mushroom m = find(world, pos);
		if (m != null) {
			store(world).changed.add(m);
		}
	}

	/**
	 * Lets the cap fall apart once only {@code capCollapseBelow} of it is left, then works out the stem's
	 * active layer: none while any cap block is left, otherwise the highest layer that still has a block.
	 * Forgets the mushroom once it's all gone.
	 */
	public static void refresh(ServerWorld world, Mushroom m) {
		long left = m.capPositions().stream().filter(p -> world.getBlockState(p).getBlock() instanceof GiantCapBlock).count();
		if (left > 0 && left <= Math.floor(m.shape.cap.size() * OPTIONS.capCollapseBelow)) {
			collapse(world, m);
			left = 0;
		}
		boolean capLeft = left > 0;
		int top = Integer.MIN_VALUE;
		for (BlockPos p : m.stemPositions()) {
			if (world.getBlockState(p).isOf(STEM)) {
				top = Math.max(top, p.getY());
			}
		}
		for (BlockPos p : m.stemPositions()) {
			BlockState state = world.getBlockState(p);
			if (state.isOf(STEM)) {
				boolean active = !capLeft && p.getY() == top;
				if (state.get(GiantStemBlock.ACTIVE) != active) {
					world.setBlockState(p, state.with(GiantStemBlock.ACTIVE, active), Block.NOTIFY_LISTENERS);
				}
			}
		}
		if (!capLeft && top == Integer.MIN_VALUE) {
			Store store = store(world);
			store.mushrooms.remove(m);
			store.markDirty();
		}
	}

	/**
	 * What's left of the cap falls apart: each block drops its loot (and gives whoever was mining it its
	 * spores) and tumbles down as a falling block that crumbles where it lands (it never lands as a block).
	 * Returns how many fell.
	 */
	public static int collapse(ServerWorld world, Mushroom m) {
		int fell = 0;
		for (BlockPos p : m.capPositions()) {
			BlockState state = world.getBlockState(p);
			if (state.getBlock() instanceof GiantCapBlock) {
				boolean creative = m.miner instanceof net.minecraft.entity.player.PlayerEntity player && player.isCreative();
				if (!creative) {
					Block.dropStacks(state, world, p);
				}
				Spores.harvest(world, p, state, m.miner);
				FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world, p, state);
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

	/** The breaking progress per tick that takes {@code minTicks} (never one hit: at least 2 ticks). */
	static float maxDelta(int minTicks) {
		return 1.0f / Math.max(2, minTicks);
	}

	// ---- Sprouts -------------------------------------------------------------------------------

	/** Someone hit or broke a cap block at {@code pos}: sprouts start (or keep) growing on that cap. */
	public static void engage(ServerWorld world, BlockPos pos, Entity miner) {
		Mushroom m = find(world, pos);
		if (m == null || !m.isCap(pos)) {
			return;
		}
		long now = world.getTime();
		if (!m.isEngaged(now)) {
			m.nextSprout = now + OPTIONS.firstSproutTicks;
		}
		m.engagedUntil = now + OPTIONS.engagedTicks;
		m.miner = miner;
		m.lastHit = pos.toImmutable();
	}

	private static void tick(ServerWorld world) {
		Store store = store(world);
		if (store.mushrooms.isEmpty()) {
			return;
		}
		if (!store.changed.isEmpty()) {
			List<Mushroom> changed = new ArrayList<>(store.changed);
			store.changed.clear();
			for (Mushroom m : changed) {
				if (store.mushrooms.contains(m)) {
					refresh(world, m);
				}
			}
		}
		long now = world.getTime();
		for (Mushroom m : List.copyOf(store.mushrooms)) {
			if (m.isEngaged(now) && now >= m.nextSprout) {
				growSprout(world, m);
				int min = Math.max(1, OPTIONS.sproutIntervalMinTicks);
				int max = Math.max(min, OPTIONS.sproutIntervalMaxTicks);
				m.nextSprout = now + min + world.random.nextInt(max - min + 1);
			}
		}
	}

	/**
	 * Grows one sprout on the top of the cap (a cap block with nothing of the cap over it, so never inside
	 * the hollow) near whoever is mining, not where anyone stands, unless the cap already has
	 * {@code maxSprouts}. Returns where, or null.
	 */
	public static BlockPos growSprout(ServerWorld world, Mushroom m) {
		m.sprouts.removeIf(p -> !world.getBlockState(p).isOf(SPROUT));
		if (m.sprouts.size() >= OPTIONS.maxSprouts) {
			return null;
		}
		Vec3d near = m.miner != null && !m.miner.isRemoved() ? m.miner.getPos()
				: m.lastHit != null ? m.lastHit.toCenterPos() : m.axis();
		double r2 = OPTIONS.sproutRadius * OPTIONS.sproutRadius;
		List<BlockPos> spots = new ArrayList<>();
		for (BlockPos p : m.capPositions()) {
			BlockPos above = p.up();
			if (world.getBlockState(p).getBlock() instanceof GiantCapBlock && world.getBlockState(above).isAir()
					&& above.toCenterPos().squaredDistanceTo(near) <= r2 && !capAbove(world, m, p)
					&& world.getEntitiesByClass(LivingEntity.class, new Box(above), e -> !e.isSpectator()).isEmpty()) {
				spots.add(above);
			}
		}
		if (spots.isEmpty()) {
			return null;
		}
		BlockPos spot = spots.get(world.random.nextInt(spots.size()));
		world.setBlockState(spot, SPROUT.getDefaultState().with(MushroomSproutBlock.BROWN, !m.shape.red));
		if (world.getBlockEntity(spot) instanceof MushroomSproutBlockEntity sprout) {
			sprout.light(world.getTime(), OPTIONS.sproutFuseTicks);
		}
		m.sprouts.add(spot);
		world.playSound(null, spot, SoundEvents.BLOCK_FUNGUS_PLACE, SoundCategory.BLOCKS, 1.0f, 1.4f);
		world.spawnParticles(ParticleTypes.CRIMSON_SPORE, spot.getX() + 0.5, spot.getY() + 0.3, spot.getZ() + 0.5, 8, 0.25, 0.2, 0.25, 0.0);
		return spot;
	}

	/** Whether any of the cap is still over {@code pos} (its column, up to the top of the mushroom). */
	private static boolean capAbove(ServerWorld world, Mushroom m, BlockPos pos) {
		int top = m.origin.getY() + m.shape.bounds.getMaxY();
		for (BlockPos p = pos.up(); p.getY() <= top; p = p.up()) {
			if (m.isCap(p) && world.getBlockState(p).getBlock() instanceof GiantCapBlock) {
				return true;
			}
		}
		return false;
	}

	// ---- Carving the cap, sweeping the stem ---------------------------------------------------------

	/** How many cap blocks one break with this hoe takes ({@code hoeBlocks}); 1 for anything that isn't a hoe. */
	public static int hoeBlocks(ItemStack stack) {
		if (!stack.isIn(ItemTags.HOES) || !(stack.getItem() instanceof ToolItem tool)) {
			return 1;
		}
		FrostboundConfig.HoeBlocks blocks = OPTIONS.hoeBlocks;
		ToolMaterial material = tool.getMaterial();
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
			float speed = material.getMiningSpeedMultiplier();
			count = speed < 4 ? blocks.wood : speed < 6 ? blocks.stone : speed < 8 ? blocks.iron : speed < 9 ? blocks.diamond : blocks.netherite;
		}
		return Math.max(1, count);
	}

	/**
	 * A hoe broke the cap block at {@code pos}: {@code count - 1} more of the mushroom's cap blocks next
	 * to it break too, nearest first (along the cap's surface before through it), but never one under a
	 * sprout (break the sprout) or one something stands on (no falling through your own floor). Returns
	 * how many more broke.
	 */
	public static int carve(ServerWorld world, BlockPos pos, Entity breaker, int count) {
		Mushroom m = find(world, pos);
		if (m == null || count <= 1) {
			return 0;
		}
		List<BlockPos> targets = m.capPositions().stream()
				.filter(p -> !p.equals(pos) && p.getSquaredDistance(pos) <= CARVE_REACH * CARVE_REACH)
				.filter(p -> world.getBlockState(p).getBlock() instanceof GiantCapBlock)
				.filter(p -> !world.getBlockState(p.up()).isOf(SPROUT) && !standsOn(world, p))
				.sorted(Comparator.comparingDouble(p -> p.getSquaredDistance(pos) + 0.5 * Math.abs(p.getY() - pos.getY())))
				.limit(count - 1)
				.toList();
		harvestAll(world, targets, breaker);
		return targets.size();
	}

	/** Breaks each block (dropping its loot unless {@code breaker} is in creative) and gives {@code breaker} its spores. */
	private static void harvestAll(ServerWorld world, List<BlockPos> targets, Entity breaker) {
		boolean drop = !(breaker instanceof net.minecraft.entity.player.PlayerEntity player && player.isCreative());
		for (BlockPos p : targets) {
			BlockState state = world.getBlockState(p);
			if (world.breakBlock(p, drop, breaker)) {
				Spores.harvest(world, p, state, breaker);
			}
		}
	}

	/** An axe broke an active stem block at {@code pos}: the rest of that layer within {@code radius} (across) breaks too. */
	public static int sweep(ServerWorld world, BlockPos pos, Entity breaker, double radius) {
		Mushroom m = find(world, pos);
		if (m == null || radius <= 0) {
			return 0;
		}
		double r2 = radius * radius;
		List<BlockPos> targets = m.stemPositions().stream()
				.filter(p -> p.getY() == pos.getY() && !p.equals(pos) && p.getSquaredDistance(pos) <= r2)
				.filter(p -> world.getBlockState(p).isOf(STEM) && world.getBlockState(p).get(GiantStemBlock.ACTIVE))
				.toList();
		harvestAll(world, targets, breaker);
		return targets.size();
	}

	/** Whether something alive is standing on the block at {@code pos}. */
	private static boolean standsOn(ServerWorld world, BlockPos pos) {
		Box feet = new Box(pos.getX(), pos.getY() + 1.0, pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.25, pos.getZ() + 1.0);
		return !world.getEntitiesByClass(LivingEntity.class, feet, e -> !e.isSpectator() && e.isOnGround()).isEmpty();
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
		Mushroom m = find(world, pos.down());
		if (m == null) {
			m = nearest(world, pos.toCenterPos(), 16.0);
		}
		world.setBlockState(pos, Blocks.AIR.getDefaultState());
		Vec3d center = pos.toCenterPos();
		world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 0.8f, 1.5f);
		world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
		world.spawnParticles(new DustParticleEffect(new Vector3f(0.85f, 0.15f, 0.12f), 1.6f),
				center.x, center.y, center.z, 40, 0.8, 0.5, 0.8, 0.0);

		Vec3d axis = m != null ? m.axis() : center;
		double radius = OPTIONS.burstRadius;
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, new Box(pos).expand(radius),
				e -> e.isAlive() && !e.isSpectator() && e.squaredDistanceTo(center) <= radius * radius)) {
			e.setVelocity(pushFor(e.getPos(), axis, center, world.random.nextDouble()));
			e.velocityModified = true;
			e.fallDistance = 0.0f;
		}

		if (m != null) {
			m.sprouts.remove(pos);
			regrowNear(world, m, pos, OPTIONS.burstRegrowBlocks);
		}
	}

	/**
	 * Where a burst throws something at {@code at}: outward from the stem's axis (from the sprout if it's
	 * right on the axis, a random way if it's on both), at {@code burstPush}, and up at {@code burstLift}.
	 */
	public static Vec3d pushFor(Vec3d at, Vec3d axis, Vec3d sprout, double random) {
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
		return new Vec3d(dx / len * OPTIONS.burstPush, OPTIONS.burstLift, dz / len * OPTIONS.burstPush);
	}

	/**
	 * Grows back up to {@code count} missing cap blocks, nearest {@code pos} first, skipping spots something
	 * stands in. They come back {@link GiantCapBlock#REGROWN}: blighted, no drops, no spores.
	 */
	public static int regrowNear(ServerWorld world, Mushroom m, BlockPos pos, int count) {
		List<BlockPos> missing = m.capPositions().stream()
				.filter(p -> world.getBlockState(p).isAir())
				.sorted(Comparator.comparingDouble(p -> p.getSquaredDistance(pos)))
				.toList();
		int grown = 0;
		for (BlockPos p : missing) {
			if (grown >= count) {
				break;
			}
			if (!world.getEntitiesByClass(LivingEntity.class, new Box(p), e -> !e.isSpectator()).isEmpty()) {
				continue;
			}
			world.setBlockState(p, m.capBlock().getDefaultState().with(GiantCapBlock.REGROWN, true));
			world.spawnParticles(ParticleTypes.CRIMSON_SPORE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.0);
			grown++;
		}
		return grown;
	}

	// ---- Commands ------------------------------------------------------------------------------

	private static final SimpleCommandExceptionType NONE_NEAR = new SimpleCommandExceptionType(
			Text.literal("No giant mushroom within " + (int) COMMAND_REACH + " blocks"));
	private static final SimpleCommandExceptionType UNKNOWN_SHAPE = new SimpleCommandExceptionType(
			Text.literal("Unknown mushroom shape"));

	/**
	 * {@code /frostbound mushroom place <shape> [pos]}, {@code /frostbound mushroom regrow} (puts the
	 * nearest one back whole), {@code /frostbound mushroom remove}, and {@code /frostbound spores [set <count>]}
	 * (your spores). Operators only.
	 */
	private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal(Frostbound.MOD_ID)
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("mushroom")
						.then(CommandManager.literal("place")
								.then(CommandManager.argument("shape", StringArgumentType.word())
										.suggests((ctx, builder) -> CommandSource.suggestMatching(
												java.util.Arrays.stream(GiantMushroomShape.values()).map(GiantMushroomShape::id), builder))
										.executes(ctx -> placeCommand(ctx, BlockPos.ofFloored(ctx.getSource().getPosition())))
										.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
												.executes(ctx -> placeCommand(ctx, BlockPosArgumentType.getBlockPos(ctx, "pos"))))))
						.then(CommandManager.literal("regrow").executes(ctx -> {
							Mushroom m = nearestFor(ctx.getSource());
							regrow(ctx.getSource().getWorld(), m);
							ctx.getSource().sendFeedback(() -> Text.literal("Regrew the " + m.shape.id() + " at " + m.origin.toShortString()), true);
							return 1;
						}))
						.then(CommandManager.literal("remove").executes(ctx -> {
							Mushroom m = nearestFor(ctx.getSource());
							remove(ctx.getSource().getWorld(), m);
							ctx.getSource().sendFeedback(() -> Text.literal("Removed the " + m.shape.id() + " at " + m.origin.toShortString()), true);
							return 1;
						})))
				.then(CommandManager.literal("spores")
						.executes(ctx -> {
							int count = Spores.get(ctx.getSource().getPlayerOrThrow());
							ctx.getSource().sendFeedback(() -> Text.translatable("frostbound.spores.count", count), false);
							return count;
						})
						.then(CommandManager.literal("set")
								.then(CommandManager.argument("count", IntegerArgumentType.integer(0))
										.executes(ctx -> {
											int count = IntegerArgumentType.getInteger(ctx, "count");
											Spores.set(ctx.getSource().getPlayerOrThrow(), count);
											ctx.getSource().sendFeedback(() -> Text.translatable("frostbound.spores.count", count), true);
											return count;
										})))));
	}

	private static int placeCommand(CommandContext<ServerCommandSource> ctx, BlockPos pos) throws CommandSyntaxException {
		GiantMushroomShape shape = GiantMushroomShape.byId(StringArgumentType.getString(ctx, "shape"));
		if (shape == null) {
			throw UNKNOWN_SHAPE.create();
		}
		place(ctx.getSource().getWorld(), pos, shape);
		ctx.getSource().sendFeedback(() -> Text.literal("Placed a giant " + shape.id() + " at " + pos.toShortString()), true);
		return 1;
	}

	private static Mushroom nearestFor(ServerCommandSource source) throws CommandSyntaxException {
		Mushroom m = nearest(source.getWorld(), source.getPosition(), COMMAND_REACH);
		if (m == null) {
			throw NONE_NEAR.create();
		}
		return m;
	}

	public static void init() {
		ServerTickEvents.END_WORLD_TICK.register(GiantMushrooms::tick);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (world instanceof ServerWorld serverWorld && !player.isSpectator()
					&& world.getBlockState(pos).getBlock() instanceof GiantCapBlock) {
				engage(serverWorld, pos, player);
			}
			return ActionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!(world instanceof ServerWorld serverWorld)) {
				return;
			}
			if (state.getBlock() instanceof GiantCapBlock || state.isOf(STEM)) {
				Spores.harvest(serverWorld, pos, state, player);
			}
			if (state.getBlock() instanceof GiantCapBlock) {
				engage(serverWorld, pos, player);
				carve(serverWorld, pos, player, hoeBlocks(player.getMainHandStack()));
			} else if (state.isOf(STEM) && state.get(GiantStemBlock.ACTIVE) && player.getMainHandStack().isIn(ItemTags.AXES)) {
				sweep(serverWorld, pos, player, OPTIONS.stemSweepRadius);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> registerCommands(dispatcher));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.addAfter(Items.GLOWSTONE_DUST, MUSHROOM_SAMPLE));
		Spores.init();
	}
}

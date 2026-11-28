package com.frostbound.mushrooms;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Giant mushrooms: big red and brown mushrooms you harvest in two parts.
 *
 *  1. The cap, with a hoe. Cap blocks never break in one hit, and a hoe carves a few blocks with each
 *     break, more with a better hoe (1 for wood up to 5 for netherite, {@code hoeBlocks}). Once only
 *     {@code capCollapseBelow} of the cap is left, the rest falls apart. While you mine, sprouts (vanilla
 *     mushrooms) grow on the cap near you, each with a countdown (2 s); break it in time or it bursts,
 *     throwing everything near it outward off the cap and growing some of the cap back.
 *  2. The stem, with an axe, once the whole cap is gone: from the top down, a layer at a time. Only the
 *     top (active) layer mines quickly (an axe sweeps a patch of it, {@code stemSweepRadius}); the rest
 *     are very slow.
 *
 * They don't grow in the world yet: place them with {@code /frostbound mushroom place <shape>}
 * (shapes in {@link GiantMushroomShape}). Balance numbers are in {@code giantMushrooms} in the config,
 * read each time they're needed ({@link #options()}).
 *
 * This class registers the blocks and items, places and removes mushrooms, and wires up the events. The
 * rest is split by job:
 *  - {@link Mushroom}: one placed mushroom; {@link MushroomStore}: the saved list and its chunk index;
 *  - {@link MushroomMining}: hoe carving, axe sweeping, the stem's active layer, the cap falling apart;
 *  - {@link MushroomSprouts}: sprouts and bursts; {@link Spores}: the spore counter;
 *  - {@link MushroomCommands}: the commands.
 */
public final class GiantMushrooms {
	// Block hardness is fixed when blocks are registered, so capHardness and stemHardness need a restart
	// (every other option is read live).
	public static final Block RED_CAP = Registry.register(Registries.BLOCK, Frostbound.id("giant_red_mushroom_cap"),
			new GiantCapBlock(AbstractBlock.Settings.copy(Blocks.RED_MUSHROOM_BLOCK)
					.strength(options().capHardness).pistonBehavior(PistonBehavior.BLOCK).nonOpaque()));
	public static final Block BROWN_CAP = Registry.register(Registries.BLOCK, Frostbound.id("giant_brown_mushroom_cap"),
			new GiantCapBlock(AbstractBlock.Settings.copy(Blocks.BROWN_MUSHROOM_BLOCK)
					.strength(options().capHardness).pistonBehavior(PistonBehavior.BLOCK).nonOpaque()));
	public static final Block STEM = Registry.register(Registries.BLOCK, Frostbound.id("giant_mushroom_stem"),
			new GiantStemBlock(AbstractBlock.Settings.copy(Blocks.MUSHROOM_STEM)
					.strength(options().stemHardness).pistonBehavior(PistonBehavior.BLOCK)));
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

	private GiantMushrooms() {}

	/** The current balance numbers (not cached, so a reloaded config takes effect). */
	public static FrostboundConfig.GiantMushroomOptions options() {
		return FrostboundConfig.get().giantMushrooms;
	}

	// ---- Lookups ----

	/** Every mushroom placed in this world. */
	public static List<Mushroom> all(ServerWorld world) {
		return MushroomStore.get(world).all();
	}

	/** The mushroom this position is part of (a cap or stem spot, whatever's there now), or null. */
	public static Mushroom find(ServerWorld world, BlockPos pos) {
		return MushroomStore.get(world).find(pos);
	}

	/** Nearest mushroom whose origin is within {@code reach} of {@code pos}, or null. */
	public static Mushroom nearest(ServerWorld world, Vec3d pos, double reach) {
		return MushroomStore.get(world).nearest(pos, reach);
	}

	// ---- Placing, regrowing, removing ----

	/** Builds a whole mushroom with its stem's bottom middle at {@code origin}, over whatever is there. */
	public static Mushroom place(ServerWorld world, BlockPos origin, GiantMushroomShape shape) {
		Mushroom m = new Mushroom(origin, shape);
		MushroomStore.get(world).add(m);
		regrow(world, m);
		return m;
	}

	/** Puts back every missing block of the mushroom (and clears its sprouts). */
	public static void regrow(ServerWorld world, Mushroom m) {
		clearSprouts(world, m);
		BlockPos.Mutable p = new BlockPos.Mutable();
		BlockState stem = STEM.getDefaultState(), cap = m.capBlock().getDefaultState();
		for (int i = 0, n = m.stemCount(); i < n; i++) {
			world.setBlockState(m.stem(i, p), stem);
		}
		for (int i = 0, n = m.capCount(); i < n; i++) {
			world.setBlockState(m.cap(i, p), cap);
		}
		m.engagedUntil = -1;
		m.activeLayer = Mushroom.UNKNOWN;
		MushroomMining.refresh(world, m);
	}

	/** Takes the whole mushroom away and forgets it. */
	public static void remove(ServerWorld world, Mushroom m) {
		MushroomStore.get(world).remove(m);
		clearSprouts(world, m);
		BlockPos.Mutable p = new BlockPos.Mutable();
		BlockState air = Blocks.AIR.getDefaultState();
		for (int i = 0, n = m.capCount(); i < n; i++) {
			if (world.getBlockState(m.cap(i, p)).getBlock() instanceof GiantCapBlock) {
				world.setBlockState(p, air);
			}
		}
		for (int i = 0, n = m.stemCount(); i < n; i++) {
			if (world.getBlockState(m.stem(i, p)).isOf(STEM)) {
				world.setBlockState(p, air);
			}
		}
	}

	private static void clearSprouts(ServerWorld world, Mushroom m) {
		for (BlockPos p : m.sprouts) {
			if (world.getBlockState(p).isOf(SPROUT)) {
				world.setBlockState(p, Blocks.AIR.getDefaultState());
			}
		}
		m.sprouts.clear();
	}

	/** A cap or stem block went away (or came back): look at its mushroom again at the end of the tick. */
	static void blockChanged(ServerWorld world, BlockPos pos) {
		MushroomStore store = MushroomStore.get(world);
		Mushroom m = store.find(pos);
		if (m != null) {
			store.markChanged(m);
		}
	}

	// ---- Events ----

	private static void tick(ServerWorld world) {
		MushroomStore store = MushroomStore.get(world);
		if (store.isEmpty()) {
			return;
		}
		store.drainChanged(m -> MushroomMining.refresh(world, m));
		MushroomSprouts.tick(world, store);
	}

	public static void init() {
		ServerTickEvents.END_WORLD_TICK.register(GiantMushrooms::tick);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (world instanceof ServerWorld serverWorld && !player.isSpectator()
					&& world.getBlockState(pos).getBlock() instanceof GiantCapBlock) {
				MushroomSprouts.engage(serverWorld, pos, player);
			}
			return ActionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!(world instanceof ServerWorld serverWorld)) {
				return;
			}
			if (state.getBlock() instanceof GiantCapBlock) {
				Spores.harvest(serverWorld, pos, state, player);
				MushroomSprouts.engage(serverWorld, pos, player);
				MushroomMining.carve(serverWorld, pos, player, MushroomMining.hoeBlocks(player.getMainHandStack()));
			} else if (state.isOf(STEM)) {
				Spores.harvest(serverWorld, pos, state, player);
				if (state.get(GiantStemBlock.ACTIVE) && player.getMainHandStack().isIn(ItemTags.AXES)) {
					MushroomMining.sweep(serverWorld, pos, player, options().stemSweepRadius);
				}
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> MushroomCommands.register(dispatcher));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.addAfter(Items.GLOWSTONE_DUST, MUSHROOM_SAMPLE));
		Spores.init();
	}
}

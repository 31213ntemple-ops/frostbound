package com.frostbound.test;

import com.frostbound.Frostbound;
import com.frostbound.christmas.Christmas;
import com.frostbound.christmas.ChristmasPresents;
import com.frostbound.mobs.FrozenMob;
import com.frostbound.environment.WinterBiomePool;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.inventory.LootableInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.Resource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks for the Christmas module. They need DecorationX: Christmas (in the dev runs' mods
 * folder via syncDevMods); without it they only check that nothing was registered.
 */
public class ChristmasTests implements FabricGameTest {
	private static final List<String> STYLES = List.of("classic", "multicolour", "silver_blue", "white_frost");
	private static final Identifier STRUCTURE = Frostbound.id("christmas_tree");
	private static final RegistryKey<LootTable> PRESENT_LOOT = ChristmasPresents.PRESENT_LOOT;

	private static boolean hasDecorationX() {
		return FabricLoader.getInstance().isModLoaded(Christmas.DECORATIONX);
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void christmasTreeStructureIsRegistered(TestContext ctx) {
		var registries = ctx.getWorld().getRegistryManager();
		boolean registered = registries.get(RegistryKeys.STRUCTURE).containsId(STRUCTURE);
		if (!hasDecorationX()) {
			check(!registered, "Christmas structures must not load without DecorationX");
			ctx.complete();
			return;
		}
		check(registered, "structure frostbound:christmas_tree missing");
		check(registries.get(RegistryKeys.STRUCTURE_SET).containsId(Frostbound.id("christmas_trees")), "structure set missing");
		check(registries.get(RegistryKeys.TEMPLATE_POOL).containsId(STRUCTURE), "template pool missing");

		TagKey<Biome> biomes = TagKey.of(RegistryKeys.BIOME, Frostbound.id("has_structure/christmas_tree"));
		var biomeRegistry = registries.get(RegistryKeys.BIOME);
		check(biomeRegistry.entryOf(WinterBiomePool.SPARSE_FOREST).isIn(biomes), "trees should spawn in sparse forest");
		check(biomeRegistry.entryOf(BiomeKeys.TAIGA).isIn(biomes), "trees should spawn in taiga");
		check(!biomeRegistry.entryOf(BiomeKeys.FROZEN_PEAKS).isIn(biomes), "no trees on frozen peaks");
		ctx.complete();
	}

	/** Every style has the tree, the snow floor and all five Supplementaries presents with their loot. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void christmasTreeTemplatesHaveTreeAndPresents(TestContext ctx) {
		if (!hasDecorationX()) {
			ctx.complete();
			return;
		}
		for (String style : STYLES) {
			Identifier id = Frostbound.id("christmas_tree/" + style);
			NbtCompound nbt = readTemplateFile(ctx.getWorld(), id);
			Set<String> blocks = new HashSet<>();
			NbtList palette = nbt.getList("palette", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < palette.size(); i++) {
				blocks.add(palette.getCompound(i).getString("Name"));
			}
			check(blocks.stream().anyMatch(b -> b.startsWith("decorationx_christmas:grand_tree")), style + ": no grand tree");
			check(blocks.contains("minecraft:snow_block"), style + ": no snow floor");
			check(blocks.stream().noneMatch(b -> b.endsWith("_glazed_terracotta")), style + ": stand-in blocks left over");

			int presents = 0;
			NbtList placed = nbt.getList("blocks", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < placed.size(); i++) {
				NbtCompound block = placed.getCompound(i);
				String name = palette.getCompound(block.getInt("state")).getString("Name");
				if (name.startsWith("supplementaries:present_")) {
					presents++;
					check(block.getCompound("nbt").getString("LootTable").equals(PRESENT_LOOT.getValue().toString()),
							style + ": present without gift loot");
				}
			}
			check(presents == 5, style + ": expected 5 gift presents, found " + presents);
		}
		ctx.complete();
	}

	/** Places each style for real and checks the tree assembled. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void christmasTreesPlace(TestContext ctx) {
		if (!hasDecorationX()) {
			ctx.complete();
			return;
		}
		ServerWorld world = ctx.getWorld();
		int i = 0;
		for (String style : STYLES) {
			Optional<StructureTemplate> template = world.getStructureTemplateManager().getTemplate(Frostbound.id("christmas_tree/" + style));
			check(template.isPresent(), style + ": template didn't load");
			BlockPos at = ctx.getAbsolutePos(BlockPos.ORIGIN).add(-100 - 30 * i++, 0, -100);
			template.get().place(world, at, at, new StructurePlacementData(), Random.create(1), 2);

			int treeBlocks = 0, snow = 0;
			var size = template.get().getSize();
			for (BlockPos p : BlockPos.iterate(at, at.add(size).add(-1, -1, -1))) {
				var state = world.getBlockState(p);
				if (Registries.BLOCK.getId(state.getBlock()).getPath().startsWith("grand_tree")) treeBlocks++;
				if (state.isOf(Blocks.SNOW_BLOCK)) snow++;
			}
			check(treeBlocks > 10, style + ": tree didn't assemble (" + treeBlocks + " tree blocks)");
			check(snow > 50, style + ": snow floor missing (" + snow + ")");
		}
		ctx.complete();
	}

	/** Goes through worldgen's own path (structure -> pool -> template, projected to the surface). */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void placeStructureCommandBuildsATree(TestContext ctx) {
		if (!hasDecorationX()) {
			ctx.complete();
			return;
		}
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(BlockPos.ORIGIN).add(300, 0, -300);
		var server = world.getServer();
		// The command only places in loaded chunks; load the area around the spot.
		int cx = at.getX() >> 4, cz = at.getZ() >> 4;
		for (int x = cx - 3; x <= cx + 3; x++) {
			for (int z = cz - 3; z <= cz + 3; z++) {
				world.setChunkForced(x, z, true);
				world.getChunk(x, z);
			}
		}
		String command = "place structure frostbound:christmas_tree " + at.getX() + " " + at.getY() + " " + at.getZ();
		try {
			int result = server.getCommandManager().getDispatcher().execute(command, server.getCommandSource().withWorld(world).withSilent());
			check(result > 0, "/" + command + " returned " + result);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new GameTestException("/" + command + " failed: " + e.getMessage());
		}

		int treeBlocks = 0, floorY = Integer.MAX_VALUE;
		for (BlockPos p : BlockPos.iterate(at.add(-24, -8, -24), at.add(24, 16, 24))) {
			var state = world.getBlockState(p);
			if (Registries.BLOCK.getId(state.getBlock()).getPath().startsWith("grand_tree")) treeBlocks++;
			if (state.isOf(Blocks.SNOW_BLOCK)) floorY = Math.min(floorY, p.getY());
		}
		check(treeBlocks == 37, "expected a whole grand tree (37 blocks), found " + treeBlocks);
		int surface = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE_WG, at.getX(), at.getZ());
		check(floorY == surface - 1, "snow floor should replace the top ground layer (y " + (surface - 1) + "), was at y " + floorY);
		for (int x = cx - 3; x <= cx + 3; x++) {
			for (int z = cz - 3; z <= cz + 3; z++) {
				world.setChunkForced(x, z, false);
			}
		}
		ctx.complete();
	}

	/** A gift is one decent stack: never coal (that's its own outcome) and it varies. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void giftsAreOneDecentStack(TestContext ctx) {
		if (!hasDecorationX()) {
			ctx.complete();
			return;
		}
		ServerWorld world = ctx.getWorld();
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(PRESENT_LOOT);
		check(table != LootTable.EMPTY, "loot table frostbound:chests/christmas_present missing");
		LootContextParameterSet params = new LootContextParameterSet.Builder(world)
				.add(LootContextParameters.ORIGIN, Vec3d.ZERO).build(LootContextTypes.CHEST);
		Set<String> seen = new HashSet<>();
		for (long seed = 0; seed < 300; seed++) {
			List<ItemStack> gift = table.generateLoot(params, seed);
			check(gift.size() == 1 && !gift.get(0).isEmpty(), "a gift should be exactly one stack, got " + gift);
			check(!gift.get(0).isOf(Items.COAL), "gifts must not be coal");
			seen.add(Registries.ITEM.getId(gift.get(0).getItem()).toString());
		}
		check(seen.contains("minecraft:diamond") && seen.contains("minecraft:golden_apple"), "decent gifts should turn up: " + seen);
		check(seen.size() > 12, "gifts should vary, only saw " + seen);
		ctx.complete();
	}

	/** Snow layers can't sit on the tree, the presents or the lanterns (it still lands on the floor). */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void snowCantSettleOnTheTree(TestContext ctx) {
		if (!hasDecorationX()) {
			ctx.complete();
			return;
		}
		ServerWorld world = ctx.getWorld();
		StructureTemplate template = world.getStructureTemplateManager().getTemplate(Frostbound.id("christmas_tree/classic")).orElseThrow();
		BlockPos at = ctx.getAbsolutePos(BlockPos.ORIGIN).add(-100, 0, 100);
		template.place(world, at, at, new StructurePlacementData(), Random.create(1), 2);

		int treeTops = 0, floorTops = 0;
		var size = template.getSize();
		for (int x = 0; x < size.getX(); x++) {
			for (int z = 0; z < size.getZ(); z++) {
				BlockPos top = null;
				for (int y = size.getY() - 1; y >= 0 && top == null; y--) {
					BlockPos p = at.add(x, y, z);
					if (!world.getBlockState(p).isAir()) top = p;
				}
				if (top == null) continue;
				String block = Registries.BLOCK.getId(world.getBlockState(top).getBlock()).toString();
				boolean canSnow = Blocks.SNOW.getDefaultState().canPlaceAt(world, top.up());
				if (block.startsWith("decorationx_christmas:grand_tree") || block.contains("presents") || block.contains("lantern")) {
					treeTops++;
					check(!canSnow, "snow could settle on " + block + " at " + top);
				} else if (block.equals("minecraft:snow_block")) {
					floorTops++;
					check(canSnow, "snow should still settle on the floor at " + top);
				}
			}
		}
		check(treeTops >= 9 && floorTops > 40, "checked " + treeTops + " tree/present tops and " + floorTops + " floor tops");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void presentOutcomesFollowConfigWeights(TestContext ctx) {
		Map<ChristmasPresents.Outcome, Integer> counts = new EnumMap<>(ChristmasPresents.Outcome.class);
		Random random = Random.create(7);
		for (int i = 0; i < 4000; i++) {
			counts.merge(ChristmasPresents.roll(random), 1, Integer::sum);
		}
		// Defaults 30 / 35 / 20 / 15 of 100: allow a few percent either way.
		check(near(counts.get(ChristmasPresents.Outcome.FROZEN_MOBS), 1200), "frozen mobs ~30%: " + counts);
		check(near(counts.get(ChristmasPresents.Outcome.GIFT), 1400), "gift ~35%: " + counts);
		check(near(counts.get(ChristmasPresents.Outcome.COAL), 800), "coal ~20%: " + counts);
		check(near(counts.get(ChristmasPresents.Outcome.EXPLODE), 600), "explode ~15%: " + counts);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void presentGiftDropsOneStack(TestContext ctx) {
		if (!hasDecorationX()) {
			ctx.complete();
			return;
		}
		BlockPos pos = standInPresent(ctx);
		check(ChristmasPresents.isChristmasPresent(ctx.getWorld(), pos) == false, "a chest isn't a Supplementaries present");
		ChristmasPresents.open(ctx.getWorld(), pos, null, ChristmasPresents.Outcome.GIFT);
		check(ctx.getWorld().getBlockState(pos).isAir(), "present should be gone");
		List<ItemEntity> items = itemsNear(ctx, pos);
		check(items.size() == 1 && !items.get(0).getStack().isOf(Items.COAL), "expected one gift, got " + stacks(items));
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void presentCoalDropsOneCoal(TestContext ctx) {
		BlockPos pos = standInPresent(ctx);
		ChristmasPresents.open(ctx.getWorld(), pos, null, ChristmasPresents.Outcome.COAL);
		List<ItemEntity> items = itemsNear(ctx, pos);
		check(items.size() == 1 && items.get(0).getStack().isOf(Items.COAL) && items.get(0).getStack().getCount() == 1,
				"expected exactly one coal (and not the chest's contents), got " + stacks(items));
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void presentReleasesFrozenMobs(TestContext ctx) {
		BlockPos pos = standInPresent(ctx);
		ChristmasPresents.open(ctx.getWorld(), pos, null, ChristmasPresents.Outcome.FROZEN_MOBS);
		List<MobEntity> mobs = ctx.getWorld().getEntitiesByClass(MobEntity.class, new Box(pos).expand(3), m -> true);
		check(mobs.size() >= 1 && mobs.size() <= 3, "expected 1-3 mobs, got " + mobs.size());
		check(mobs.stream().allMatch(FrozenMob::isFrozen), "every mob from a present should be Frozen");
		mobs.forEach(m -> m.discard());
		ctx.complete();
	}

	/** Own batch: the blast shouldn't reach other tests. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "present_explosion", tickLimit = 120)
	public void presentExplodesAfterFuse(TestContext ctx) {
		BlockPos pos = standInPresent(ctx);
		ServerWorld world = ctx.getWorld();
		ChristmasPresents.open(world, pos, null, ChristmasPresents.Outcome.EXPLODE);
		check(!world.getBlockState(pos).isAir(), "present should stay while the fuse burns");
		check(ChristmasPresents.isFusing(world, pos), "fuse should be lit");
		ctx.runAtTick(20, () -> check(ChristmasPresents.isFusing(world, pos), "should still be ticking after 1 second"));
		ctx.runAtTick(60, () -> {
			check(!ChristmasPresents.isFusing(world, pos), "fuse should have gone off after 2 seconds");
			check(world.getBlockState(pos).isAir(), "present should be gone after the explosion");
			ctx.complete();
		});
	}

	/** A chest with the Christmas-present loot table, standing in for a Supplementaries present. */
	private static BlockPos standInPresent(TestContext ctx) {
		BlockPos pos = ctx.getAbsolutePos(new BlockPos(0, 1, 0));
		ctx.getWorld().setBlockState(pos, Blocks.CHEST.getDefaultState());
		((LootableInventory) ctx.getWorld().getBlockEntity(pos)).setLootTable(PRESENT_LOOT);
		return pos;
	}

	private static List<ItemEntity> itemsNear(TestContext ctx, BlockPos pos) {
		return ctx.getWorld().getEntitiesByClass(ItemEntity.class, new Box(pos).expand(2), e -> true);
	}

	private static List<String> stacks(List<ItemEntity> items) {
		return items.stream().map(e -> e.getStack().getCount() + "x " + Registries.ITEM.getId(e.getStack().getItem())).toList();
	}

	private static boolean near(Integer actual, int expected) {
		return actual != null && Math.abs(actual - expected) < 120;
	}

	private static NbtCompound readTemplateFile(ServerWorld world, Identifier id) {
		Identifier file = id.withPath(p -> "structure/" + p + ".nbt");
		Optional<Resource> resource = world.getServer().getResourceManager().getResource(file);
		check(resource.isPresent(), "missing " + file + " (run gradlew runGametest -PbuildStructures)");
		try (InputStream in = resource.get().getInputStream()) {
			return NbtIo.readCompressed(in, NbtSizeTracker.ofUnlimitedBytes());
		} catch (Exception e) {
			throw new GameTestException("couldn't read " + file + ": " + e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}

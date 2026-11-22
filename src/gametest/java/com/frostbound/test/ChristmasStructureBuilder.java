package com.frostbound.test;

import com.frostbound.Frostbound;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dev tool, not a real test: builds the Christmas tree structure templates in the test
 * world (placing DecorationX blocks the way a player would, so multi-block pieces like the
 * Grand Christmas Tree assemble themselves) and saves them into src/main/resources.
 *
 * Only does anything with {@code gradlew runGametest -PbuildStructures}; otherwise it passes
 * straight away. Commit the .nbt files it writes.
 *
 * Supplementaries presents can't be placed here (Supplementaries doesn't run in dev), so
 * glazed terracotta stands in for them and is swapped for packed presents with a loot table
 * when the file is written. In a pack without Supplementaries those blocks load as air.
 */
public class ChristmasStructureBuilder implements FabricGameTest {
	private static final String DX = "decorationx_christmas";
	private static final Identifier PRESENT_LOOT = Frostbound.id("chests/christmas_present");

	/** Template name -> DecorationX tree block. */
	private static final Map<String, String> TREES = Map.of(
			"classic", "grand_tree",
			"multicolour", "grand_tree_multicolour",
			"silver_blue", "grand_tree_silver_blue",
			"white_frost", "grand_tree_white_frost");

	/** Stand-in block -> Supplementaries present colour. */
	private static final Map<Block, String> PRESENT_MARKERS = Map.of(
			Blocks.RED_GLAZED_TERRACOTTA, "red",
			Blocks.GREEN_GLAZED_TERRACOTTA, "green",
			Blocks.WHITE_GLAZED_TERRACOTTA, "white",
			Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA, "light_blue",
			Blocks.YELLOW_GLAZED_TERRACOTTA, "yellow");

	/** Floor radius: a round patch of snow blocks, so no wild trees root among the presents. */
	private static final int R = 5;

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
	public void buildChristmasTreeTemplates(TestContext ctx) {
		if (!Boolean.getBoolean("frostbound.buildStructures")) {
			ctx.complete();
			return;
		}
		Path out = Path.of(System.getProperty("frostbound.projectDir"),
				"src/main/resources/resourcepacks/christmas/data/frostbound/structure/christmas_tree");
		int i = 0;
		for (Map.Entry<String, String> tree : TREES.entrySet()) {
			BlockPos center = ctx.getAbsolutePos(BlockPos.ORIGIN).add(200 + 40 * i++, 0, 200);
			build(ctx, center, tree.getValue(), out.resolve(tree.getKey() + ".nbt"));
		}
		ctx.complete();
	}

	private static void build(TestContext ctx, BlockPos center, String treeBlock, Path file) {
		ServerWorld world = ctx.getWorld();
		int height = 32;

		// Clean slate: structure void everywhere (left out of the template, so it keeps the terrain).
		for (BlockPos p : BlockPos.iterate(center.add(-R - 1, -1, -R - 1), center.add(R + 1, height, R + 1))) {
			world.setBlockState(p, Blocks.STRUCTURE_VOID.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		}
		// Round snow floor at y = 0.
		for (int x = -R; x <= R; x++) {
			for (int z = -R; z <= R; z++) {
				if (x * x + z * z <= R * R + 1) {
					world.setBlockState(center.add(x, 0, z), Blocks.SNOW_BLOCK.getDefaultState());
				}
			}
		}

		// The tree, centred. Placed like a player facing north would.
		place(ctx, center, dx(treeBlock), Direction.SOUTH);
		BlockBox tree = boxOf(world, center.add(-R, 1, -R), center.add(R, height, R));
		if (tree == null) {
			throw new GameTestException(treeBlock + " didn't place");
		}

		// Presents just outside the tree's base: Supplementaries ones (loot) and DecorationX piles (decoration).
		int north = tree.getMinZ() - center.getZ() - 1, south = tree.getMaxZ() - center.getZ() + 1;
		int west = tree.getMinX() - center.getX() - 1, east = tree.getMaxX() - center.getX() + 1;
		List<Object[]> presents = List.of(
				new Object[]{0, south, Blocks.RED_GLAZED_TERRACOTTA},
				new Object[]{-1, south, Blocks.GREEN_GLAZED_TERRACOTTA},
				new Object[]{east, 1, Blocks.WHITE_GLAZED_TERRACOTTA},
				new Object[]{west, 0, Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA},
				new Object[]{1, north, Blocks.YELLOW_GLAZED_TERRACOTTA});
		for (Object[] p : presents) {
			world.setBlockState(center.add((int) p[0], 1, (int) p[1]), ((Block) p[2]).getDefaultState());
		}
		place(ctx, center.add(1, 0, south), dx("presents"), Direction.SOUTH);
		place(ctx, center.add(west, 0, -1), dx("presents_blue"), Direction.WEST);
		place(ctx, center.add(east, 0, -1), dx("presents_gold"), Direction.EAST);

		// Lanterns on the edge of the clearing, and a snowman keeping watch.
		place(ctx, center.add(-3, 0, 3), dx("christmas_lantern_red"), Direction.SOUTH);
		place(ctx, center.add(3, 0, -3), dx("christmas_lantern_green"), Direction.NORTH);
		place(ctx, center.add(3, 0, 3), dx("snowman"), Direction.SOUTH);

		BlockBox all = boxOf(world, center.add(-R - 1, 0, -R - 1), center.add(R + 1, height, R + 1));
		StructureTemplate template = new StructureTemplate();
		BlockPos min = new BlockPos(all.getMinX(), all.getMinY(), all.getMinZ());
		template.saveFromWorld(world, min, all.getDimensions().add(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		NbtCompound nbt = template.writeNbt(new NbtCompound());
		swapInPresents(nbt);
		try {
			Files.createDirectories(file.getParent());
			NbtIo.writeCompressed(nbt, file);
		} catch (IOException e) {
			throw new GameTestException("couldn't write " + file + ": " + e);
		}
		Frostbound.LOGGER.info("Wrote {} ({} x {} x {})", file, all.getBlockCountX(), all.getBlockCountY(), all.getBlockCountZ());
	}

	/** Uses a block item on the top face of {@code ground}, as a player looking {@code facing} would. */
	private static void place(TestContext ctx, BlockPos ground, Item item, Direction facing) {
		if (!(item instanceof BlockItem blockItem)) {
			throw new GameTestException("not a block item: " + Registries.ITEM.getId(item));
		}
		PlayerEntity player = ctx.createMockPlayer(GameMode.CREATIVE);
		player.setYaw(facing.getOpposite().asRotation());
		ItemStack stack = new ItemStack(item);
		player.setStackInHand(Hand.MAIN_HAND, stack);
		BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(ground).add(0, 0.5, 0), Direction.UP, ground, false);
		ActionResult result = blockItem.place(new ItemPlacementContext(player, Hand.MAIN_HAND, stack, hit));
		if (!result.isAccepted()) {
			throw new GameTestException("couldn't place " + Registries.ITEM.getId(item) + " at " + ground.up());
		}
	}

	private static Item dx(String path) {
		Identifier id = Identifier.of(DX, path);
		if (!Registries.ITEM.containsId(id)) {
			throw new GameTestException("missing item " + id + " (is DecorationX: Christmas in run/mods?)");
		}
		return Registries.ITEM.get(id);
	}

	/** Bounding box of everything that isn't air or structure void, or null if there's nothing. */
	private static BlockBox boxOf(ServerWorld world, BlockPos from, BlockPos to) {
		BlockBox box = null;
		for (BlockPos p : BlockPos.iterate(from, to)) {
			BlockState state = world.getBlockState(p);
			if (!state.isAir() && !state.isOf(Blocks.STRUCTURE_VOID)) {
				box = box == null ? new BlockBox(p) : box.encompass(p);
			}
		}
		return box;
	}

	/** Swaps the stand-in blocks in the template for packed Supplementaries presents holding loot. */
	private static void swapInPresents(NbtCompound template) {
		NbtList palette = template.getList("palette", NbtElement.COMPOUND_TYPE);
		Map<Integer, String> markerColours = new HashMap<>();
		for (int i = 0; i < palette.size(); i++) {
			NbtCompound state = palette.getCompound(i);
			for (Map.Entry<Block, String> marker : PRESENT_MARKERS.entrySet()) {
				if (state.getString("Name").equals(Registries.BLOCK.getId(marker.getKey()).toString())) {
					markerColours.put(i, marker.getValue());
					NbtCompound properties = new NbtCompound();
					properties.putString("packed", "true");
					state.putString("Name", "supplementaries:present_" + marker.getValue());
					state.put("Properties", properties);
				}
			}
		}
		if (markerColours.size() != PRESENT_MARKERS.size()) {
			throw new GameTestException("expected all " + PRESENT_MARKERS.size() + " present colours, found " + markerColours.values());
		}
		NbtList blocks = template.getList("blocks", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < blocks.size(); i++) {
			NbtCompound block = blocks.getCompound(i);
			if (markerColours.containsKey(block.getInt("state"))) {
				NbtCompound present = new NbtCompound();
				present.putString("id", "supplementaries:present");
				present.putString("LootTable", PRESENT_LOOT.toString());
				present.putString("Recipient", "@e"); // anyone may open it
				present.putString("Sender", "Santa");
				block.put("nbt", present);
			}
		}
	}
}

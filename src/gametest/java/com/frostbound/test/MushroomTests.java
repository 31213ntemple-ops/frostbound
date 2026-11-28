package com.frostbound.test;

import com.frostbound.config.FrostboundConfig;
import com.frostbound.mushrooms.GiantCapBlock;
import com.frostbound.mushrooms.GiantMushroomShape;
import com.frostbound.mushrooms.GiantMushrooms;
import com.frostbound.mushrooms.Mushroom;
import com.frostbound.mushrooms.MushroomMining;
import com.frostbound.mushrooms.MushroomSprouts;
import com.frostbound.mushrooms.MushroomStore;
import com.frostbound.mushrooms.GiantStemBlock;
import com.frostbound.mushrooms.MushroomSproutBlock;
import com.frostbound.mushrooms.MushroomSproutBlockEntity;
import com.frostbound.mushrooms.Spores;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Giant mushrooms ({@link GiantMushrooms}): the three shapes, the place command, the cap (hoe speed, never
 * one hit, sprouts while you mine, bursts that throw you off and grow the cap back) and the stem (top
 * layer first). Each mushroom goes up well away from the other tests, its chunks force-loaded.
 */
public class MushroomTests implements FabricGameTest {
	private static final FrostboundConfig.GiantMushroomOptions OPTIONS = FrostboundConfig.get().giantMushrooms;

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void shapesAreWholeMushrooms(TestContext ctx) {
		Set<Integer> sizes = new HashSet<>();
		for (GiantMushroomShape shape : GiantMushroomShape.values()) {
			String name = shape.id();
			for (int y = 0; y < shape.layers; y++) {
				int layer = y;
				long blocks = shape.stem.stream().filter(p -> p.getY() == layer).count();
				check(blocks >= 6, name + ": every stem layer should be thicker than a block (6+), layer " + y + " has " + blocks);
			}
			check(shape.stem.stream().filter(p -> p.getY() == 0).count() > shape.stem.stream().filter(p -> p.getY() == shape.layers / 2).count(),
					name + ": the stem should flare out at the foot");
			check(connected(shape.stem), name + ": the stem should be one piece");
			check(connected(shape.cap), name + ": the cap should be one piece");
			check(shape.cap.stream().noneMatch(shape.stem::contains), name + ": cap and stem shouldn't overlap");
			check(shape.stem.stream().anyMatch(p -> p.getY() == shape.layers - 1 && shape.cap.contains(p.up())),
					name + ": the stem should hold the cap up");
			check(shape.cap.size() >= 100 && shape.cap.size() <= 300, name + ": cap of 100-300 blocks, has " + shape.cap.size());
			check(shape.bounds.getBlockCountY() >= 11 && shape.bounds.getBlockCountY() <= 16 && shape.bounds.getBlockCountX() >= 10 && shape.bounds.getBlockCountX() <= 14,
					name + ": bigger than a vanilla mushroom but not huge (10-14 across, 11-16 tall), is "
							+ shape.bounds.getBlockCountX() + " across, " + shape.bounds.getBlockCountY() + " tall");
			check(shape.cap.stream().anyMatch(p -> p.getY() < shape.capCenter.getY() + 2 && Math.abs(p.getX() - shape.capCenter.getX()) > 3),
					name + ": the cap's rim should come down around the top of the stem");
			sizes.add(shape.cap.size() * 1000 + shape.stem.size());
		}
		check(sizes.size() == GiantMushroomShape.values().length, "the shapes should all differ");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void placeCommandBuildsTheMushroom(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = site(ctx, 3000, GiantMushroomShape.FLATCAP);
		String command = "frostbound mushroom place flatcap " + at.getX() + " " + at.getY() + " " + at.getZ();
		try {
			int result = world.getServer().getCommandManager().getDispatcher().execute(command,
					world.getServer().getCommandSource().withWorld(world).withSilent());
			check(result == 1, "/" + command + " returned " + result);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new GameTestException("/" + command + " failed: " + e.getMessage());
		}
		Mushroom m = GiantMushrooms.find(world, at);
		check(m != null && m.shape == GiantMushroomShape.FLATCAP && m.origin.equals(at), "the command should place a flatcap at " + at);
		check(m.stemPositions().stream().allMatch(p -> world.getBlockState(p).isOf(GiantMushrooms.STEM)), "every stem block placed");
		check(m.capPositions().stream().allMatch(p -> world.getBlockState(p).isOf(GiantMushrooms.BROWN_CAP)), "a flatcap's cap is brown");
		GiantMushrooms.remove(world, m);
		check(GiantMushrooms.find(world, at) == null && world.getBlockState(at).isAir(), "remove should take it all away");
		ctx.complete();
	}

	/** A hoe is the cap's tool, and nothing (netherite, Efficiency V) breaks a cap block in one hit. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void capNeedsAHoeAndNeverBreaksInOneHit(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = site(ctx, 3100, GiantMushroomShape.TOADSTOOL);
		Mushroom m = GiantMushrooms.place(world, at, GiantMushroomShape.TOADSTOOL);
		BlockPos cap = m.capPositions().getFirst();
		BlockState state = world.getBlockState(cap);
		ServerPlayerEntity player = ImbuementTests.serverPlayer(ctx);
		player.setOnGround(true);

		float hand = state.calcBlockBreakingDelta(player, world, cap);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
		float ironHoe = state.calcBlockBreakingDelta(player, world, cap);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
		float axe = state.calcBlockBreakingDelta(player, world, cap);
		ItemStack best = new ItemStack(Items.NETHERITE_HOE);
		best.addEnchantment(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), 5);
		player.setStackInHand(Hand.MAIN_HAND, best);
		// Haste V on top (a test player never ticks, so Efficiency's attribute may not be applied yet).
		player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.HASTE, 200, 4));
		float netherite = state.calcBlockBreakingDelta(player, world, cap);

		check(Math.abs(1.0f / ironHoe - 30.0f * OPTIONS.capHardness / 6.0f) < 0.5f, "an iron hoe should take 30 × hardness / 6 ticks, took " + 1.0f / ironHoe);
		check(ironHoe > hand * 4.0f, "a hoe should be much faster than a bare hand (" + ironHoe + " vs " + hand + ")");
		check(axe <= hand * 1.01f, "an axe is no better than a hand on the cap (" + axe + " vs " + hand + ")");
		check(netherite < 1.0f && Math.abs(netherite - 1.0f / OPTIONS.capMinBreakTicks) < 1.0e-4f,
				"Efficiency V netherite with Haste V should be held to " + OPTIONS.capMinBreakTicks + " ticks, delta was " + netherite);
		GiantMushrooms.remove(world, m);
		ctx.complete();
	}

	/** The stem: nothing active while cap is left, then the top layer, then the one under it; the whole mushroom forgotten once gone. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void stemMinesTopLayerFirst(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = site(ctx, 3200, GiantMushroomShape.LEANING);
		Mushroom m = GiantMushrooms.place(world, at, GiantMushroomShape.LEANING);
		int top = at.getY() + m.shape.layers - 1;
		check(activeLayers(world, m).isEmpty(), "no stem layer is active while the cap is there");
		// Mined down to just above the collapse: the stem stays inactive.
		List<BlockPos> cap = m.capPositions();
		int keep = (int) Math.floor(cap.size() * OPTIONS.capCollapseBelow) + 1;
		for (int i = keep; i < cap.size(); i++) {
			world.setBlockState(cap.get(i), Blocks.AIR.getDefaultState());
		}
		ctx.runAtTick(3, () -> {
			check(activeLayers(world, m).isEmpty(), keep + " cap blocks left should still keep the stem inactive");
			check(capLeft(world, m) == keep, "and the cap shouldn't fall apart yet");
			world.setBlockState(cap.getFirst(), Blocks.AIR.getDefaultState());
		});
		// (Not counted as falling block entities: new entities take a few ticks to show up in lookups, and these land fast.)
		ctx.runAtTick(6, () -> {
			check(capLeft(world, m) == 0, "one more block and the rest of the cap falls apart");
			check(activeLayers(world, m).equals(Set.of(top)), "with the cap gone the top layer (y " + top + ") is active, active: " + activeLayers(world, m));
			ServerPlayerEntity player = ImbuementTests.serverPlayer(ctx);
			player.setOnGround(true);
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
			BlockPos active = m.stemPositions().stream().filter(p -> p.getY() == top).findFirst().orElseThrow();
			BlockPos below = active.down();
			float fast = world.getBlockState(active).calcBlockBreakingDelta(player, world, active);
			float slow = world.getBlockState(below).calcBlockBreakingDelta(player, world, below);
			check(Math.abs(slow / fast - OPTIONS.inactiveLayerSpeed) < 1.0e-3, "a lower layer should mine at " + OPTIONS.inactiveLayerSpeed
					+ " of the active one (" + slow + " vs " + fast + ")");
			check(fast < 1.0f, "even the active layer shouldn't break in one hit");
			// Most of the top layer gone: it stays the active one until the last block goes.
			m.stemPositions().stream().filter(p -> p.getY() == top).skip(1)
					.forEach(p -> world.setBlockState(p, Blocks.AIR.getDefaultState()));
		});
		ctx.runAtTick(9, () -> {
			check(activeLayers(world, m).equals(Set.of(top)), "a layer stays active until its last block goes, active: " + activeLayers(world, m));
			m.stemPositions().stream().filter(p -> p.getY() == top).forEach(p -> world.setBlockState(p, Blocks.AIR.getDefaultState()));
		});
		ctx.runAtTick(12, () -> {
			check(activeLayers(world, m).equals(Set.of(top - 1)), "then the layer under it, active: " + activeLayers(world, m));
			m.stemPositions().forEach(p -> world.setBlockState(p, Blocks.AIR.getDefaultState()));
		});
		ctx.runAtTick(15, () -> {
			check(GiantMushrooms.find(world, at) == null, "a mushroom mined away completely is forgotten");
			ctx.complete();
		});
	}

	/** Hitting the cap starts sprouts (each lit for the fuse); they stop a while after the last hit, and each one bursts. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 220)
	public void miningTheCapGrowsSprouts(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = site(ctx, 3300, GiantMushroomShape.TOADSTOOL);
		Mushroom m = GiantMushrooms.place(world, at, GiantMushroomShape.TOADSTOOL);
		BlockPos hit = m.capPositions().getLast();
		long[] start = new long[1];
		ctx.runAtTick(1, () -> {
			start[0] = world.getTime();
			MushroomSprouts.engage(world, hit, null);
			check(MushroomStore.get(world).ticking().contains(m), "a mushroom being mined ticks");
		});
		ctx.runAtTick(OPTIONS.firstSproutTicks + 4, () -> {
			check(m.sprouts().size() == 1, "the first sprout should come " + OPTIONS.firstSproutTicks + " ticks after the first hit, near it, have " + m.sprouts().size());
			BlockPos sprout = m.sprouts().iterator().next();
			check(world.getBlockState(sprout).isOf(GiantMushrooms.SPROUT), "the sprout should be in the world");
			check(m.isCap(sprout.down()), "sprouts grow on the cap");
			check(m.capPositions().stream().noneMatch(p -> p.getX() == sprout.getX() && p.getZ() == sprout.getZ() && p.getY() > sprout.getY()),
					"sprouts grow on the top of the cap, never inside it");
			check(!world.getBlockState(sprout).get(MushroomSproutBlock.BROWN), "a red cap grows red mushrooms");
			MushroomSproutBlockEntity be = (MushroomSproutBlockEntity) world.getBlockEntity(sprout);
			long left = be.ticksLeft();
			check(left > OPTIONS.sproutFuseTicks - 5 && left <= OPTIONS.sproutFuseTicks, "a new sprout should have about the full fuse, has " + left);
		});
		// Engaged for engagedTicks after the hit; the last sprout then bursts within the fuse.
		int quiet = 1 + OPTIONS.engagedTicks + OPTIONS.sproutFuseTicks + 5;
		ctx.runAtTick(quiet, () -> {
			check(!m.isEngaged(world.getTime()), "sprouts should stop " + OPTIONS.engagedTicks + " ticks after the last hit");
			check(!MushroomStore.get(world).ticking().contains(m), "and the mushroom stops ticking");
			check(m.sprouts().stream().noneMatch(p -> world.getBlockState(p).isOf(GiantMushrooms.SPROUT)),
					"every sprout should have burst by now");
		});
		ctx.runAtTick(quiet + 40, () -> {
			check(m.sprouts().stream().noneMatch(p -> world.getBlockState(p).isOf(GiantMushrooms.SPROUT)), "and no more come");
			GiantMushrooms.remove(world, m);
			ctx.complete();
		});
	}

	/** A burst throws whatever's near outward off the cap and up, and grows back the missing cap blocks nearest it. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
	public void burstThrowsYouOffAndRegrowsTheCap(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = site(ctx, 3400, GiantMushroomShape.TOADSTOOL);
		Mushroom m = GiantMushrooms.place(world, at, GiantMushroomShape.TOADSTOOL);
		// A hole dug in the top of the cap, on the far (west) side.
		List<BlockPos> hole = new java.util.ArrayList<>();
		for (int dz = -2; dz <= 2; dz++) {
			for (int dx = -4; dx <= -3; dx++) {
				hole.add(surface(m, dx, dz));
			}
		}
		hole.forEach(p -> world.setBlockState(p, Blocks.AIR.getDefaultState()));
		// Something standing on the cap, east of the middle, by a sprout.
		BlockPos stand = surface(m, 2, 0).up();
		PigEntity pig = EntityType.PIG.create(world);
		pig.setPersistent();
		pig.setAiDisabled(true);
		pig.refreshPositionAndAngles(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0, 0);
		world.spawnEntity(pig);
		BlockPos sprout = surface(m, 1, 0).up();
		ctx.runAtTick(10, () -> {
			world.setBlockState(sprout, GiantMushrooms.SPROUT.getDefaultState());
			check(world.getBlockState(sprout).isOf(GiantMushrooms.SPROUT), "a sprout should stand on the cap");
			MushroomSprouts.burst(world, sprout);
			Vec3d v = pig.getVelocity();
			check(world.getBlockState(sprout).isAir(), "the sprout is gone after bursting");
			check(v.x > OPTIONS.burstPush * 0.9 && Math.abs(v.z) < 0.1, "the pig east of the stem should be thrown east, off the cap: " + v);
			check(v.y > 0.0, "and up: " + v);
			long regrown = hole.stream().filter(p -> world.getBlockState(p).isOf(GiantMushrooms.RED_CAP)).count();
			check(regrown == OPTIONS.burstRegrowBlocks, "a burst regrows " + OPTIONS.burstRegrowBlocks + " cap blocks, regrew " + regrown);
			check(hole.stream().filter(p -> world.getBlockState(p).isOf(GiantMushrooms.RED_CAP))
					.allMatch(p -> world.getBlockState(p).get(GiantCapBlock.REGROWN)), "and they come back blighted (regrown)");
			check(m.capPositions().stream().filter(p -> !hole.contains(p) && world.getBlockState(p).isOf(GiantMushrooms.RED_CAP))
					.noneMatch(p -> world.getBlockState(p).get(GiantCapBlock.REGROWN)), "the rest of the cap isn't");
			pig.discard();
			GiantMushrooms.remove(world, m);
			ctx.complete();
		});
	}

	/** A hoe breaks more cap blocks per hit the better it is (wood 1 to netherite 5), but never the block under a sprout; other tools one. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void betterHoesCarveMore(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = site(ctx, 3600, GiantMushroomShape.TOADSTOOL);
		Mushroom m = GiantMushrooms.place(world, at, GiantMushroomShape.TOADSTOOL);
		FrostboundConfig.HoeBlocks blocks = OPTIONS.hoeBlocks;
		check(MushroomMining.hoeBlocks(new ItemStack(Items.WOODEN_HOE)) == blocks.wood && blocks.wood == 1, "a wooden hoe breaks 1");
		check(MushroomMining.hoeBlocks(new ItemStack(Items.IRON_HOE)) == blocks.iron, "an iron hoe breaks " + blocks.iron);
		check(MushroomMining.hoeBlocks(new ItemStack(Items.NETHERITE_HOE)) == blocks.netherite && blocks.netherite <= 5, "a netherite hoe breaks at most 5");
		check(MushroomMining.hoeBlocks(new ItemStack(com.frostbound.copper.CopperGear.COPPER_HOE)) == blocks.copper, "a copper hoe breaks " + blocks.copper);
		check(MushroomMining.hoeBlocks(new ItemStack(Items.IRON_AXE)) == 1, "anything else breaks 1");
		check(blocks.wood <= blocks.stone && blocks.stone <= blocks.iron && blocks.iron <= blocks.diamond && blocks.diamond <= blocks.netherite,
				"better hoes never break fewer");

		ServerPlayerEntity player = ImbuementTests.serverPlayer(ctx);
		BlockPos guarded = surface(m, 1, 0);
		ctx.runAtTick(2, () -> {
			// Each hoe on its own fresh patch of the cap's top.
			int[][] spots = {{-3, 0}, {3, 0}, {0, -3}, {0, 3}};
			ItemStack[] tools = {new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.IRON_HOE), new ItemStack(Items.NETHERITE_HOE), new ItemStack(Items.IRON_AXE)};
			int[] expected = {blocks.wood, blocks.iron, blocks.netherite, 1};
			for (int i = 0; i < tools.length; i++) {
				player.setStackInHand(Hand.MAIN_HAND, tools[i]);
				List<BlockPos> present = m.capPositions().stream()
						.filter(p -> world.getBlockState(p).getBlock() instanceof com.frostbound.mushrooms.GiantCapBlock).toList();
				BlockPos target = surface(m, spots[i][0], spots[i][1]);
				check(player.interactionManager.tryBreakBlock(target), "the player should break the cap block with " + tools[i]);
				List<BlockPos> broke = present.stream().filter(p -> world.getBlockState(p).isAir()).toList();
				check(broke.size() == expected[i], tools[i] + " should break " + expected[i] + " cap blocks, broke " + broke.size());
				check(broke.stream().allMatch(p -> p.getSquaredDistance(target) <= 1.8 * 1.8), "only blocks next to the one hit go");
			}
			// The block under a sprout stays, even right next to the hit.
			world.setBlockState(guarded.up(), GiantMushrooms.SPROUT.getDefaultState());
			((MushroomSproutBlockEntity) world.getBlockEntity(guarded.up())).light(world.getTime(), 200);
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.NETHERITE_HOE));
			check(player.interactionManager.tryBreakBlock(surface(m, 0, 0)), "the player should break the middle of the cap");
			check(world.getBlockState(guarded).isOf(GiantMushrooms.RED_CAP) && world.getBlockState(guarded.up()).isOf(GiantMushrooms.SPROUT),
					"the block under a sprout stays (break the sprout)");
			GiantMushrooms.remove(world, m);
			ctx.complete();
		});
	}

	/** An axe on the active stem layer sweeps a patch of that layer, and nothing of the layer below. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void axeSweepsTheActiveLayer(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = site(ctx, 3700, GiantMushroomShape.TOADSTOOL);
		Mushroom m = GiantMushrooms.place(world, at, GiantMushroomShape.TOADSTOOL);
		m.capPositions().forEach(p -> world.setBlockState(p, Blocks.AIR.getDefaultState()));
		int top = at.getY() + m.shape.layers - 1;
		ctx.runAtTick(3, () -> {
			ServerPlayerEntity player = ImbuementTests.serverPlayer(ctx);
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
			List<BlockPos> layer = m.stemPositions().stream().filter(p -> p.getY() == top).toList();
			List<BlockPos> below = m.stemPositions().stream().filter(p -> p.getY() == top - 1).toList();
			BlockPos middle = new BlockPos(at.getX(), top, at.getZ());
			check(world.getBlockState(middle).get(GiantStemBlock.ACTIVE), "the top layer should be active");
			check(player.interactionManager.tryBreakBlock(middle), "the player should break the stem block");
			long gone = layer.stream().filter(p -> world.getBlockState(p).isAir()).count();
			check(gone == 5 && gone < layer.size(), "an axe should sweep the block hit and the four beside it (of " + layer.size() + "), swept " + gone);
			check(below.stream().allMatch(p -> world.getBlockState(p).isOf(GiantMushrooms.STEM)), "the layer below is untouched");
			GiantMushrooms.remove(world, m);
			ctx.complete();
		});
	}

	/** Broken in time, a sprout does nothing; and one whose cap block is mined away goes quietly. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
	public void breakingASproutDefusesIt(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = site(ctx, 3500, GiantMushroomShape.FLATCAP);
		Mushroom m = GiantMushrooms.place(world, at, GiantMushroomShape.FLATCAP);
		List<BlockPos> hole = List.of(surface(m, -5, 0), surface(m, -5, 1), surface(m, -5, -1));
		BlockPos first = surface(m, -2, 0).up(), second = surface(m, 2, 0).up();
		ctx.runAtTick(2, () -> {
			hole.forEach(p -> world.setBlockState(p, Blocks.AIR.getDefaultState()));
			for (BlockPos p : List.of(first, second)) {
				world.setBlockState(p, GiantMushrooms.SPROUT.getDefaultState());
				((MushroomSproutBlockEntity) world.getBlockEntity(p)).light(world.getTime(), OPTIONS.sproutFuseTicks);
			}
		});
		ctx.runAtTick(5, () -> {
			world.breakBlock(first, false);
			world.breakBlock(second.down(), false);
			check(world.getBlockState(first).isAir(), "a broken sprout is gone");
			check(world.getBlockState(second).isAir(), "a sprout goes with the cap block under it");
		});
		ctx.runAtTick(5 + OPTIONS.sproutFuseTicks + 10, () -> {
			check(hole.stream().allMatch(p -> world.getBlockState(p).isAir()), "a defused sprout shouldn't regrow the cap");
			GiantMushrooms.remove(world, m);
			ctx.complete();
		});
	}

	/**
	 * Every cap and stem block has a 10% chance of dropping one mushroom sample, and drops nothing else,
	 * whatever breaks it (hand, hoe, axe, an explosion). A cap block a burst grew back drops nothing.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void mushroomBlocksSometimesDropASample(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		Vec3d origin = Vec3d.ofCenter(pos);
		List<ItemStack> tools = List.of(ItemStack.EMPTY, new ItemStack(Items.NETHERITE_HOE), new ItemStack(Items.IRON_AXE));
		List<BlockState> whole = List.of(GiantMushrooms.RED_CAP.getDefaultState(), GiantMushrooms.BROWN_CAP.getDefaultState(),
				GiantMushrooms.STEM.getDefaultState(), GiantMushrooms.STEM.getDefaultState().with(GiantStemBlock.ACTIVE, true));
		List<BlockState> regrown = List.of(GiantMushrooms.RED_CAP.getDefaultState().with(GiantCapBlock.REGROWN, true),
				GiantMushrooms.BROWN_CAP.getDefaultState().with(GiantCapBlock.REGROWN, true));
		int trials = 3000;
		for (BlockState state : whole) {
			int samples = 0;
			for (int i = 0; i < trials; i++) {
				List<ItemStack> drops = state.getDroppedStacks(lootParams(world, origin, tools.get(i % tools.size()), i % 4 == 3));
				check(drops.stream().allMatch(d -> d.isOf(GiantMushrooms.MUSHROOM_SAMPLE) && d.getCount() == 1) && drops.size() <= 1,
						state + " should drop at most one mushroom sample, dropped " + drops);
				samples += drops.size();
			}
			// 10% of 3000 is 300; 240-360 is more than five standard deviations (16.4) either side.
			check(samples >= 240 && samples <= 360, state + " should drop a sample about 10% of the time, did " + samples + " in " + trials);
		}
		for (BlockState state : regrown) {
			for (int i = 0; i < 300; i++) {
				List<ItemStack> drops = state.getDroppedStacks(lootParams(world, origin, tools.get(i % tools.size()), i % 4 == 3));
				check(drops.isEmpty(), state + " (grown back by a burst) should drop nothing, dropped " + drops);
			}
		}
		check(GiantMushrooms.MUSHROOM_SAMPLE.getMaxCount() == 64, "samples stack to 64");
		ctx.complete();
	}

	private static net.minecraft.loot.context.LootContextParameterSet.Builder lootParams(ServerWorld world, Vec3d origin, ItemStack tool, boolean explosion) {
		var params = new net.minecraft.loot.context.LootContextParameterSet.Builder(world)
				.add(net.minecraft.loot.context.LootContextParameters.ORIGIN, origin)
				.add(net.minecraft.loot.context.LootContextParameters.TOOL, tool);
		if (explosion) {
			params.add(net.minecraft.loot.context.LootContextParameters.EXPLOSION_RADIUS, 4.0f);
		}
		return params;
	}

	/**
	 * Spores are a count on the player, not an item: 5–7 for each cap or stem block they harvest (every
	 * count turns up), also for each block a hoe carves with it; none for a block a burst grew back, none
	 * in creative, none when nobody's mining.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void harvestingGivesSpores(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = ImbuementTests.serverPlayer(ctx);
		player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
		BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		check(Spores.get(player) == 0, "a new player has no spores");
		for (BlockState state : List.of(GiantMushrooms.RED_CAP.getDefaultState(), GiantMushrooms.BROWN_CAP.getDefaultState(),
				GiantMushrooms.STEM.getDefaultState().with(GiantStemBlock.ACTIVE, true))) {
			Set<Integer> counts = new HashSet<>();
			for (int i = 0; i < 200; i++) {
				int before = Spores.get(player);
				int given = Spores.harvest(world, pos, state, player);
				check(given >= 5 && given <= 7 && Spores.get(player) == before + given, state + " should give 5-7 spores, gave " + given);
				counts.add(given);
			}
			check(counts.equals(Set.of(5, 6, 7)), state + ": every count from 5 to 7 should turn up, saw " + counts);
		}
		int before = Spores.get(player);
		check(Spores.harvest(world, pos, GiantMushrooms.RED_CAP.getDefaultState().with(GiantCapBlock.REGROWN, true), player) == 0,
				"a block grown back by a burst gives no spores");
		check(Spores.harvest(world, pos, Blocks.RED_MUSHROOM_BLOCK.getDefaultState(), player) == 0, "a vanilla mushroom block gives none");
		check(Spores.harvest(world, pos, GiantMushrooms.STEM.getDefaultState(), null) == 0, "nobody mining, no spores");
		check(Spores.get(player) == before, "and the count didn't move");

		// A netherite hoe carving the cap: the spores of every block it takes.
		BlockPos at = site(ctx, 4400, GiantMushroomShape.TOADSTOOL);
		Mushroom m = GiantMushrooms.place(world, at, GiantMushroomShape.TOADSTOOL);
		BlockPos hit = surface(m, 0, 0);
		world.setBlockState(hit, Blocks.AIR.getDefaultState());
		int start = Spores.get(player);
		int carved = MushroomMining.carve(world, hit, player, 5);
		int got = Spores.get(player) - start;
		check(carved == 4 && got >= 5 * carved && got <= 7 * carved, "carving " + carved + " blocks should give 5-7 spores each, gave " + got);

		player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
		check(Spores.harvest(world, pos, GiantMushrooms.STEM.getDefaultState(), player) == 0, "none in creative");
		GiantMushrooms.remove(world, m);
		ctx.complete();
	}

	/**
	 * The chunk index: a mushroom on a chunk corner (in four chunks) is found from every one of its blocks,
	 * next to another mushroom sharing a chunk with it, and nowhere once removed; nearest() finds it from
	 * any side within reach.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void chunkIndexFindsMushroomsAcrossChunks(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos base = ctx.getAbsolutePos(new BlockPos(0, 1, 0)).add(0, 0, 4600);
		BlockPos corner = siteAt(ctx, new BlockPos(base.getX() & ~15, base.getY(), base.getZ() & ~15), GiantMushroomShape.TOADSTOOL);
		BlockPos beside = siteAt(ctx, corner.add(20, 0, 0), GiantMushroomShape.FLATCAP);
		Mushroom m = GiantMushrooms.place(world, corner, GiantMushroomShape.TOADSTOOL);
		Mushroom other = GiantMushrooms.place(world, beside, GiantMushroomShape.FLATCAP);
		Set<Long> chunks = new HashSet<>();
		for (BlockPos p : m.capPositions()) {
			check(GiantMushrooms.find(world, p) == m, "cap block " + p.toShortString() + " should belong to the toadstool");
			chunks.add(net.minecraft.util.math.ChunkPos.toLong(p.getX() >> 4, p.getZ() >> 4));
		}
		for (BlockPos p : m.stemPositions()) {
			check(GiantMushrooms.find(world, p) == m, "stem block " + p.toShortString() + " should belong to the toadstool");
		}
		for (BlockPos p : other.capPositions()) {
			check(GiantMushrooms.find(world, p) == other, "cap block " + p.toShortString() + " should belong to the flatcap");
		}
		check(chunks.size() == 4, "a mushroom on a chunk corner spans four chunks, this one " + chunks.size());
		check(GiantMushrooms.find(world, corner.up(m.shape.bounds.getMaxY() + 2)) == null, "nothing above the mushroom");
		check(GiantMushrooms.find(world, corner.add(10, 0, 0)) == null, "nothing in the gap between them");
		for (int[] side : new int[][] {{-30, 0}, {0, -30}, {-20, 25}, {0, 30}}) {
			Vec3d from = Vec3d.ofCenter(corner.add(side[0], 0, side[1]));
			check(GiantMushrooms.nearest(world, from, 48) == m, "the toadstool is nearest from " + side[0] + ", " + side[1]);
		}
		check(GiantMushrooms.nearest(world, Vec3d.ofCenter(beside.add(5, 0, 0)), 48) == other, "the flatcap is nearest from its side");
		check(GiantMushrooms.nearest(world, Vec3d.ofCenter(corner.add(-100, 0, 0)), 48) == null, "nothing in reach far away");
		GiantMushrooms.remove(world, m);
		check(m.capPositions().stream().allMatch(p -> GiantMushrooms.find(world, p) == null), "a removed mushroom is gone from every chunk");
		check(GiantMushrooms.find(world, other.capPositions().get(0)) == other, "and its neighbour is still there");
		GiantMushrooms.remove(world, other);
		ctx.complete();
	}

	/**
	 * Hoes from other mods: a material goes by the tier it declares (its "incorrect for" tag), whatever its
	 * speed; a material with a tag of its own goes by its speed. Vanilla's and copper's go by name (gold
	 * stays gold although it's fast).
	 */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void moddedHoesGoByTier(TestContext ctx) {
		FrostboundConfig.HoeBlocks blocks = OPTIONS.hoeBlocks;
		net.minecraft.registry.tag.TagKey<net.minecraft.block.Block> own = net.minecraft.registry.tag.TagKey.of(RegistryKeys.BLOCK,
				com.frostbound.Frostbound.id("incorrect_for_test_tool"));
		check(MushroomMining.hoeBlocks(material(net.minecraft.registry.tag.BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 2.0f)) == blocks.diamond,
				"a slow diamond-tier hoe carves like diamond");
		check(MushroomMining.hoeBlocks(material(net.minecraft.registry.tag.BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 1.0f)) == blocks.netherite,
				"a netherite-tier hoe carves like netherite");
		check(MushroomMining.hoeBlocks(material(net.minecraft.registry.tag.BlockTags.INCORRECT_FOR_WOODEN_TOOL, 20.0f)) == blocks.wood,
				"a fast wooden-tier hoe carves like wood");
		check(MushroomMining.hoeBlocks(material(com.frostbound.copper.CopperGear.INCORRECT_FOR_COPPER_TOOL, 9.0f)) == blocks.copper,
				"a copper-tier hoe carves like copper");
		check(MushroomMining.hoeBlocks(material(own, 7.0f)) == blocks.iron, "a tier of its own at speed 7 carves like iron");
		check(MushroomMining.hoeBlocks(material(own, 12.0f)) == blocks.netherite, "at speed 12 like netherite");
		check(MushroomMining.hoeBlocks(material(own, 1.0f)) == blocks.wood, "at speed 1 like wood");
		check(MushroomMining.hoeBlocks(net.minecraft.item.ToolMaterials.GOLD) == blocks.gold, "gold is gold");
		check(MushroomMining.hoeBlocks(com.frostbound.copper.CopperGear.TOOL_MATERIAL) == blocks.copper, "copper is copper");
		ctx.complete();
	}

	private static net.minecraft.item.ToolMaterial material(net.minecraft.registry.tag.TagKey<net.minecraft.block.Block> tier, float speed) {
		return new net.minecraft.item.ToolMaterial() {
			@Override public int getDurability() { return 100; }
			@Override public float getMiningSpeedMultiplier() { return speed; }
			@Override public float getAttackDamage() { return 1.0f; }
			@Override public net.minecraft.registry.tag.TagKey<net.minecraft.block.Block> getInverseTag() { return tier; }
			@Override public int getEnchantability() { return 10; }
			@Override public net.minecraft.recipe.Ingredient getRepairIngredient() { return net.minecraft.recipe.Ingredient.EMPTY; }
		};
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void countdownAndPushMaths(TestContext ctx) {
		check(MushroomSproutBlockEntity.countdownText(40).equals("2.0"), "40 ticks reads 2.0");
		check(MushroomSproutBlockEntity.countdownText(30).equals("1.5"), "30 ticks reads 1.5");
		check(MushroomSproutBlockEntity.countdownText(-3).equals("0.0"), "never below 0.0");
		Vec3d axis = new Vec3d(0, 10, 0), sprout = new Vec3d(3, 11, 0);
		Vec3d north = MushroomSprouts.pushFor(new Vec3d(0, 11, -2), axis, sprout, 0);
		check(north.z < 0 && Math.abs(north.x) < 1.0e-6, "pushed away from the stem, not from the sprout: " + north);
		Vec3d onAxis = MushroomSprouts.pushFor(new Vec3d(0.1, 11, 0), axis, sprout, 0);
		check(onAxis.x < 0, "right over the stem, pushed away from the sprout: " + onAxis);
		ctx.complete();
	}

	// ---- Helpers -------------------------------------------------------------------------------

	/** A cleared, force-loaded spot for {@code shape}, {@code dz} blocks from the test, with a stone floor. Returns the origin. */
	private static BlockPos site(TestContext ctx, int dz, GiantMushroomShape shape) {
		return siteAt(ctx, ctx.getAbsolutePos(new BlockPos(0, 1, 0)).add(0, 0, dz), shape);
	}

	/** A cleared, force-loaded spot for {@code shape} with its origin at {@code at}, with a stone floor. Returns {@code at}. */
	private static BlockPos siteAt(TestContext ctx, BlockPos at, GiantMushroomShape shape) {
		ServerWorld world = ctx.getWorld();
		WolfDenTests.forceLoad(world, at, 24);
		net.minecraft.util.math.BlockBox box = shape.bounds;
		for (BlockPos p : BlockPos.iterate(at.add(box.getMinX() - 3, -1, box.getMinZ() - 3), at.add(box.getMaxX() + 3, box.getMaxY() + 3, box.getMaxZ() + 3))) {
			world.setBlockState(p, p.getY() == at.getY() - 1 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
		}
		return at;
	}

	/** The top cap block in the column {@code dx}, {@code dz} from the middle of the cap. */
	private static BlockPos surface(Mushroom m, int dx, int dz) {
		BlockPos center = m.origin.add(m.shape.capCenter);
		return m.capPositions().stream().filter(p -> p.getX() == center.getX() + dx && p.getZ() == center.getZ() + dz)
				.max(java.util.Comparator.comparingInt(BlockPos::getY))
				.orElseThrow(() -> new GameTestException("no cap at " + dx + ", " + dz));
	}

	private static long capLeft(ServerWorld world, Mushroom m) {
		return m.capPositions().stream().filter(p -> world.getBlockState(p).getBlock() instanceof com.frostbound.mushrooms.GiantCapBlock).count();
	}

	private static Set<Integer> activeLayers(ServerWorld world, Mushroom m) {
		Set<Integer> layers = new HashSet<>();
		for (BlockPos p : m.stemPositions()) {
			BlockState state = world.getBlockState(p);
			if (state.isOf(GiantMushrooms.STEM) && state.get(GiantStemBlock.ACTIVE)) {
				layers.add(p.getY());
			}
		}
		return layers;
	}

	/** Whether the positions are one piece, counting diagonal neighbours. */
	private static boolean connected(Set<BlockPos> positions) {
		if (positions.isEmpty()) {
			return false;
		}
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		BlockPos start = positions.iterator().next();
		queue.add(start);
		seen.add(start);
		while (!queue.isEmpty()) {
			BlockPos p = queue.poll();
			for (BlockPos n : BlockPos.iterate(p.add(-1, -1, -1), p.add(1, 1, 1))) {
				if (positions.contains(n) && seen.add(n.toImmutable())) {
					queue.add(n.toImmutable());
				}
			}
		}
		return seen.size() == positions.size();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}

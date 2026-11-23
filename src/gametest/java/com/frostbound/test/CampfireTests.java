package com.frostbound.test;

import com.frostbound.campfire.CampfireBaseBlock;
import com.frostbound.campfire.Campfires;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;

/**
 * Fire starter and campfire base: recipes, and the server side of the minigame (the client's
 * judging of each click is stood in for by calling {@link Campfires#attempt} directly).
 */
public class CampfireTests implements FabricGameTest {
	private static final BlockPos FIRE = new BlockPos(1, 2, 1);

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void twoSticksMakeAFireStarter(TestContext ctx) {
		ItemStack result = craft(ctx, 2, 2, List.of(
				new ItemStack(Items.STICK), ItemStack.EMPTY,
				ItemStack.EMPTY, new ItemStack(Items.STICK)));
		check(result.isOf(Campfires.FIRE_STARTER), "two sticks should make a fire starter, made " + result);
		check(result.getMaxDamage() == 5, "fire starter should have 5 durability, had " + result.getMaxDamage());
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void fourLogsInTheInventoryGridMakeACampfireBase(TestContext ctx) {
		// Four matching logs: vanilla's Wood recipe is switched off, so this can't make bark blocks.
		ItemStack same = craft(ctx, 2, 2, List.of(
				new ItemStack(Items.OAK_LOG), new ItemStack(Items.OAK_LOG),
				new ItemStack(Items.OAK_LOG), new ItemStack(Items.OAK_LOG)));
		check(same.isOf(Campfires.CAMPFIRE_BASE_ITEM), "four oak logs should make a campfire base, made " + same);

		ItemStack mixed = craft(ctx, 2, 2, List.of(
				new ItemStack(Items.SPRUCE_LOG), new ItemStack(Items.BIRCH_LOG),
				new ItemStack(Items.STRIPPED_OAK_LOG), new ItemStack(Items.CHERRY_WOOD)));
		check(mixed.isOf(Campfires.CAMPFIRE_BASE_ITEM), "any four logs should make a campfire base, made " + mixed);

		ItemStack stems = craft(ctx, 2, 2, List.of(
				new ItemStack(Items.CRIMSON_STEM), new ItemStack(Items.CRIMSON_STEM),
				new ItemStack(Items.CRIMSON_STEM), new ItemStack(Items.CRIMSON_STEM)));
		check(stems.isOf(Items.CRIMSON_HYPHAE), "nether stems don't burn, so they should still make hyphae, made " + stems);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void woodComesFromTheStonecutter(TestContext ctx) {
		var recipes = ctx.getWorld().getRecipeManager();
		check(recipes.get(Identifier.ofVanilla("oak_wood")).isEmpty(), "vanilla oak wood recipe should be off");
		check(recipes.get(Identifier.ofVanilla("stripped_cherry_wood")).isEmpty(), "vanilla stripped wood recipe should be off");
		SingleStackRecipeInput input = new SingleStackRecipeInput(new ItemStack(Items.SPRUCE_LOG));
		boolean cutsToWood = recipes.getAllMatches(RecipeType.STONECUTTING, input, ctx.getWorld()).stream()
				.anyMatch(entry -> entry.value().getResult(ctx.getWorld().getRegistryManager()).isOf(Items.SPRUCE_WOOD));
		check(cutsToWood, "a spruce log should cut into spruce wood");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void threeHitsLightTheFire(TestContext ctx) {
		ctx.setBlockState(FIRE, Campfires.CAMPFIRE_BASE.getDefaultState().with(CampfireBaseBlock.FACING, Direction.EAST));
		PlayerEntity player = playerWithStarter(ctx);
		startLighting(ctx, player);

		check(Campfires.attempt(player, true), "first hit should count");
		check(Campfires.attempt(player, true), "second hit should count");
		check(ctx.getBlockState(FIRE).isOf(Campfires.CAMPFIRE_BASE), "two hits shouldn't light it yet");
		check(Campfires.attempt(player, true), "third hit should count");

		BlockState lit = ctx.getBlockState(FIRE);
		check(lit.isOf(Blocks.CAMPFIRE), "campfire base should become a campfire, was " + lit);
		check(lit.get(CampfireBlock.LIT), "the campfire should be lit");
		check(lit.get(CampfireBlock.FACING) == Direction.EAST, "the campfire should keep its facing");
		check(damage(player) == 1, "lighting should cost 1 durability, cost " + damage(player));
		check(!player.isUsingItem(), "the player should stop using the fire starter");
		ctx.complete();
	}

	/** Through the same path as the client's packets, which also drops clicks that come too fast. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void clientAttemptsLightTheFire(TestContext ctx) {
		ctx.setBlockState(FIRE, Campfires.CAMPFIRE_BASE.getDefaultState());
		PlayerEntity player = playerWithStarter(ctx);
		startLighting(ctx, player);

		check(Campfires.receive(player, true), "the first click should count");
		check(!Campfires.receive(player, true), "a second click in the same tick should be dropped");
		ctx.runAtTick(5, () -> check(Campfires.receive(player, true), "a click 5 ticks later should count"));
		ctx.runAtTick(10, () -> check(Campfires.receive(player, false), "a miss should count"));
		ctx.runAtTick(15, () -> check(Campfires.receive(player, true), "hit 1 should count"));
		ctx.runAtTick(20, () -> check(Campfires.receive(player, true), "hit 2 should count"));
		ctx.runAtTick(25, () -> {
			check(Campfires.receive(player, true), "hit 3 should count");
			check(ctx.getBlockState(FIRE).isOf(Blocks.CAMPFIRE), "three hits in a row should light the fire");
			check(damage(player) == 2, "a miss and a light should cost 2 durability, cost " + damage(player));
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void aMissCostsDurabilityAndResetsTheStreak(TestContext ctx) {
		ctx.setBlockState(FIRE, Campfires.CAMPFIRE_BASE.getDefaultState());
		PlayerEntity player = playerWithStarter(ctx);
		startLighting(ctx, player);

		Campfires.attempt(player, true);
		Campfires.attempt(player, true);
		check(Campfires.attempt(player, false), "a miss should count");
		check(Campfires.streak(player) == 0, "a miss should reset the streak, was " + Campfires.streak(player));
		check(damage(player) == 1, "a miss should cost 1 durability, cost " + damage(player));

		Campfires.attempt(player, true);
		Campfires.attempt(player, true);
		check(ctx.getBlockState(FIRE).isOf(Campfires.CAMPFIRE_BASE), "two hits after a miss shouldn't light it");
		Campfires.attempt(player, true);
		check(ctx.getBlockState(FIRE).isOf(Blocks.CAMPFIRE), "three in a row after a miss should light it");
		check(damage(player) == 2, "a miss and a light should cost 2 durability, cost " + damage(player));
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void missesCanBreakTheFireStarter(TestContext ctx) {
		ctx.setBlockState(FIRE, Campfires.CAMPFIRE_BASE.getDefaultState());
		PlayerEntity player = playerWithStarter(ctx);
		startLighting(ctx, player);
		for (int i = 0; i < 5 && player.isUsingItem(); i++) {
			Campfires.attempt(player, false);
		}
		check(player.getStackInHand(Hand.MAIN_HAND).isEmpty(), "five misses should break the fire starter");
		check(ctx.getBlockState(FIRE).isOf(Campfires.CAMPFIRE_BASE), "the fire shouldn't be lit");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void relightsAnUnlitCampfire(TestContext ctx) {
		ctx.setBlockState(FIRE, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, false));
		PlayerEntity player = playerWithStarter(ctx);
		startLighting(ctx, player);
		for (int i = 0; i < 3; i++) {
			Campfires.attempt(player, true);
		}
		check(ctx.getBlockState(FIRE).get(CampfireBlock.LIT), "an unlit campfire should be relit");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void doesNothingOnOtherBlocks(TestContext ctx) {
		ctx.setBlockState(FIRE, Blocks.OAK_PLANKS);
		PlayerEntity player = playerWithStarter(ctx);
		check(use(ctx, player) == ActionResult.PASS, "the fire starter shouldn't start on planks");
		check(!player.isUsingItem(), "the player shouldn't be using it");
		check(!Campfires.attempt(player, true), "an attempt without a game shouldn't count");

		ctx.setBlockState(FIRE, Blocks.CAMPFIRE.getDefaultState());
		check(use(ctx, player) == ActionResult.PASS, "a lit campfire can't be lit again");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void campfireBaseDropsItself(TestContext ctx) {
		ctx.setBlockState(FIRE, Campfires.CAMPFIRE_BASE.getDefaultState());
		ctx.getWorld().breakBlock(ctx.getAbsolutePos(FIRE), true);
		ctx.expectItemAt(Campfires.CAMPFIRE_BASE_ITEM, FIRE, 2.0);
		ctx.complete();
	}

	private static PlayerEntity playerWithStarter(TestContext ctx) {
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		Vec3d eye = ctx.getAbsolute(new Vec3d(1.5, 3.5, 3.5));
		player.refreshPositionAndAngles(eye.x, eye.y - player.getStandingEyeHeight(), eye.z, 180.0f, 45.0f);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Campfires.FIRE_STARTER));
		return player;
	}

	private static ActionResult use(TestContext ctx, PlayerEntity player) {
		BlockPos pos = ctx.getAbsolutePos(FIRE);
		BlockHitResult hit = new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false);
		return player.getStackInHand(Hand.MAIN_HAND).useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
	}

	private static void startLighting(TestContext ctx, PlayerEntity player) {
		check(use(ctx, player) == ActionResult.CONSUME, "the fire starter should start on " + ctx.getBlockState(FIRE));
		check(player.isUsingItem(), "the player should be using the fire starter");
	}

	private static int damage(PlayerEntity player) {
		return player.getStackInHand(Hand.MAIN_HAND).getDamage();
	}

	private static ItemStack craft(TestContext ctx, int width, int height, List<ItemStack> stacks) {
		CraftingRecipeInput input = CraftingRecipeInput.create(width, height, stacks);
		return ctx.getWorld().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, ctx.getWorld())
				.map(entry -> entry.value().craft(input, ctx.getWorld().getRegistryManager()))
				.orElse(ItemStack.EMPTY);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}

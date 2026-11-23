package com.frostbound.test;

import com.frostbound.Frostbound;
import com.frostbound.mobs.FrozenMobs;
import com.frostbound.vessels.FrozenHeart;
import com.frostbound.vessels.VesselItem;
import com.frostbound.vessels.Vessels;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Frozen and revitalized vessels: recipes, the heart rules, the frozen heart, and frozen flesh drops. */
public class VesselTests implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void recipesExist(TestContext ctx) {
		var recipes = ctx.getWorld().getRecipeManager();
		check(recipes.get(Frostbound.id("frozen_flesh")).isPresent(), "frozen flesh recipe missing");
		check(recipes.get(Frostbound.id("frozen_vessel")).isPresent(), "frozen vessel recipe missing");
		for (int heart = 6; heart <= 20; heart++) {
			check(recipes.get(Frostbound.id("revitalized_vessel_" + heart)).isPresent(),
					"recipe for the " + heart + "th heart missing");
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void startsWithFiveHearts(TestContext ctx) {
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		Vessels.applyHearts(player);
		check(player.getMaxHealth() == 10.0f, "max health should be 10, was " + player.getMaxHealth());
		check(player.getHealth() <= 10.0f, "health should be trimmed to 10, was " + player.getHealth());
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void frozenVesselGivesOneFrozenHeart(TestContext ctx) {
		PlayerEntity player = freshPlayer(ctx);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Vessels.FROZEN_VESSEL, 2));

		check(use(ctx, player), "first frozen vessel should be usable");
		check(FrozenHeart.get(player) == FrozenHeart.FULL, "frozen heart should be whole, was " + FrozenHeart.get(player));
		check(player.getStackInHand(Hand.MAIN_HAND).getCount() == 1, "one vessel should be used up");
		check(player.getAbsorptionAmount() == 0.0f, "the frozen heart isn't absorption");

		check(!use(ctx, player), "a whole frozen heart should refuse another vessel");
		check(player.getStackInHand(Hand.MAIN_HAND).getCount() == 1, "a refused vessel should not be used up");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void frozenHeartTakesDamageFirst(TestContext ctx) {
		PlayerEntity player = freshPlayer(ctx);
		FrozenHeart.set(player, FrozenHeart.FULL);
		// Golden-apple absorption, as the effect would grant it.
		player.getAttributeInstance(EntityAttributes.GENERIC_MAX_ABSORPTION).setBaseValue(4.0);
		player.setAbsorptionAmount(4.0f);

		player.damage(ctx.getWorld().getDamageSources().generic(), 3.0f);
		check(FrozenHeart.get(player) == 0.0f, "frozen heart should shatter first, was " + FrozenHeart.get(player));
		check(player.getAbsorptionAmount() == 3.0f, "absorption should take the rest, was " + player.getAbsorptionAmount());
		check(player.getHealth() == 10.0f, "health shouldn't be touched, was " + player.getHealth());
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void crackedFrozenHeartCanBeMended(TestContext ctx) {
		PlayerEntity player = freshPlayer(ctx);
		FrozenHeart.set(player, FrozenHeart.FULL);
		player.damage(ctx.getWorld().getDamageSources().generic(), 1.0f);
		check(FrozenHeart.get(player) == 1.0f, "frozen heart should be cracked to 1, was " + FrozenHeart.get(player));
		check(player.getHealth() == 10.0f, "health shouldn't be touched, was " + player.getHealth());

		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Vessels.FROZEN_VESSEL));
		check(use(ctx, player), "a cracked frozen heart can take another vessel");
		check(FrozenHeart.get(player) == FrozenHeart.FULL, "frozen heart should be whole again");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void frozenHeartIgnoresVoidAndKill(TestContext ctx) {
		PlayerEntity player = freshPlayer(ctx);
		FrozenHeart.set(player, FrozenHeart.FULL);
		player.damage(ctx.getWorld().getDamageSources().outOfWorld(), 1.0f);
		check(FrozenHeart.get(player) == FrozenHeart.FULL, "void damage should go straight past the frozen heart");
		check(player.getHealth() == 9.0f, "void damage should hit health, was " + player.getHealth());
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void noBaseAbsorptionForPlayers(TestContext ctx) {
		// Golden-apple absorption that outlives the effect would otherwise stick around.
		PlayerEntity player = freshPlayer(ctx);
		double base = player.getAttributeBaseValue(EntityAttributes.GENERIC_MAX_ABSORPTION);
		check(base == 0.0, "players should keep vanilla's max absorption of 0, was " + base);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void revitalizedVesselsWorkInOrder(TestContext ctx) {
		PlayerEntity player = freshPlayer(ctx);

		hold(player, Vessels.revitalized(7));
		check(!use(ctx, player), "the VII vessel should be refused before the VI");
		check(Vessels.hearts(player) == 5, "hearts shouldn't change on a refusal");
		check(player.getStackInHand(Hand.MAIN_HAND).getCount() == 1, "refused vessel should not be used up");

		hold(player, Vessels.revitalized(6));
		check(use(ctx, player), "the VI vessel should be usable");
		check(Vessels.hearts(player) == 6, "6th heart should be gained");
		check(player.getMaxHealth() == 12.0f, "max health should be 12, was " + player.getMaxHealth());
		check(player.getHealth() == 12.0f, "the new heart should arrive full, health was " + player.getHealth());
		check(player.getStackInHand(Hand.MAIN_HAND).isEmpty(), "vessel should be used up");

		hold(player, Vessels.revitalized(6));
		check(!use(ctx, player), "the 6th heart can't be gained twice");

		for (int heart = 7; heart <= 20; heart++) {
			hold(player, Vessels.revitalized(heart));
			check(use(ctx, player), "vessel " + heart + " should be usable in order");
		}
		check(Vessels.hearts(player) == 20, "should end on 20 hearts");
		check(player.getMaxHealth() == 40.0f, "max health should be 40, was " + player.getMaxHealth());
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void vesselNamesAreNumbered(TestContext ctx) {
		check(Vessels.roman(6).equals("VI") && Vessels.roman(14).equals("XIV") && Vessels.roman(19).equals("XIX")
				&& Vessels.roman(20).equals("XX"), "roman numerals are off");
		String key = Vessels.revitalized(9).getTranslationKey();
		check(key.equals("item.frostbound.revitalized_vessel_9"), "unexpected translation key " + key);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void frozenZombiesDropFrozenFlesh(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		PlayerEntity killer = ctx.createMockPlayer(GameMode.SURVIVAL);
		int zombieFlesh = rollFlesh(world, ctx, EntityType.ZOMBIE, killer, 200);
		int skeletonFlesh = rollFlesh(world, ctx, EntityType.SKELETON, killer, 200);
		// 35% each: well over 30 in 200 unless something's wrong.
		check(zombieFlesh > 30, "frozen zombies should often drop frozen flesh, got " + zombieFlesh + "/200");
		check(skeletonFlesh == 0, "frozen skeletons shouldn't drop frozen flesh, got " + skeletonFlesh);
		ctx.complete();
	}

	private static int rollFlesh(ServerWorld world, TestContext ctx, EntityType<?> type, PlayerEntity killer, int rolls) {
		Entity mob = ctx.spawnEntity(type, 1, 2, 1);
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(FrozenMobs.FROZEN_LOOT);
		int found = 0;
		for (int i = 0; i < rolls; i++) {
			LootContextParameterSet params = new LootContextParameterSet.Builder(world)
					.add(LootContextParameters.THIS_ENTITY, mob)
					.add(LootContextParameters.ORIGIN, Vec3d.ofCenter(mob.getBlockPos()))
					.add(LootContextParameters.DAMAGE_SOURCE, world.getDamageSources().playerAttack(killer))
					.add(LootContextParameters.LAST_DAMAGE_PLAYER, killer)
					.build(LootContextTypes.ENTITY);
			for (ItemStack stack : table.generateLoot(params)) {
				if (stack.isOf(Vessels.FROZEN_FLESH)) {
					found++;
				}
			}
		}
		mob.discard();
		return found;
	}

	private static PlayerEntity freshPlayer(TestContext ctx) {
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		Vessels.setHearts(player, Vessels.START_HEARTS);
		player.setHealth(player.getMaxHealth());
		return player;
	}

	private static void hold(PlayerEntity player, Item item) {
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(item));
	}

	/** Right click and hold until the vessel finishes. Returns whether it was accepted. */
	private static boolean use(TestContext ctx, PlayerEntity player) {
		ItemStack stack = player.getStackInHand(Hand.MAIN_HAND);
		VesselItem vessel = (VesselItem) stack.getItem();
		ActionResult result = vessel.use(ctx.getWorld(), player, Hand.MAIN_HAND).getResult();
		if (!result.isAccepted()) {
			return false;
		}
		player.setStackInHand(Hand.MAIN_HAND, vessel.finishUsing(stack, ctx.getWorld(), player));
		return true;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}

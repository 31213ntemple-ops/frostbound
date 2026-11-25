package com.frostbound.test;

import com.frostbound.config.FrostboundConfig;
import com.frostbound.copper.Conductivity;
import com.frostbound.copper.CopperGear;
import com.frostbound.imbuement.FrostBonuses;
import com.frostbound.imbuement.FrostTier;
import com.frostbound.imbuement.FrostTiers;
import com.frostbound.imbuement.Imbuement;
import com.frostbound.imbuement.ImbuementScreenHandler;
import com.frostbound.client.imbuement.FrostStars;
import com.frostbound.passives.Passive;
import com.frostbound.passives.Passives;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The imbuement table, Cold/Icy/Frozen armour and the material frost bonuses. */
public class ImbuementTests implements FabricGameTest {

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void tableRecipeAndBlock(TestContext ctx) {
		ItemStack ice = new ItemStack(Items.ICE);
		ItemStack iron = new ItemStack(Items.IRON_INGOT);
		CraftingRecipeInput input = CraftingRecipeInput.create(3, 3,
				List.of(iron, ice, iron, ice, new ItemStack(Items.SMITHING_TABLE), ice, iron, ice, iron));
		ItemStack out = ctx.getWorld().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, ctx.getWorld())
				.map(e -> e.value().craft(input, ctx.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
		check(out.isOf(Imbuement.TABLE_ITEM), "a smithing table with ice on its sides and iron in the corners should make an imbuement table, got " + out);
		ctx.setBlockState(1, 2, 1, Imbuement.TABLE);
		check(ctx.getBlockState(new BlockPos(1, 2, 1)).isOf(Imbuement.TABLE), "the table should place");
		check(Imbuement.TABLE.getDefaultState().isIn(net.minecraft.registry.tag.BlockTags.AXE_MINEABLE), "the table is mined with an axe");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void upgradeStepsNeedTheRightRing(TestContext ctx) {
		ItemStack plate = new ItemStack(Items.IRON_CHESTPLATE);
		ItemStack cold = FrostTiers.upgrade(plate, ring(Items.ICE, 1));
		check(FrostTiers.tier(cold) == FrostTier.COLD, "iron chestplate + 8 ice should be Cold, got " + FrostTiers.tier(cold));
		check(FrostTiers.tier(plate) == FrostTier.NONE, "upgrade() shouldn't change its input");
		check(FrostTiers.upgrade(plate, ring(Items.PACKED_ICE, 4)).isEmpty(), "packed ice doesn't work on plain armour");
		check(FrostTiers.upgrade(cold, ring(Items.ICE, 1)).isEmpty(), "ice doesn't upgrade Cold armour again");

		check(FrostTiers.upgrade(cold, ring(Items.PACKED_ICE, 3)).isEmpty(), "3 packed ice per slot isn't enough");
		ItemStack icy = FrostTiers.upgrade(cold, ring(Items.PACKED_ICE, 4));
		check(FrostTiers.tier(icy) == FrostTier.ICY, "Cold + 8 × 4 packed ice should be Icy");

		check(FrostTiers.upgrade(icy, ring(Items.PACKED_ICE, 4)).isEmpty(), "packed ice doesn't upgrade Icy armour");
		ItemStack frozen = FrostTiers.upgrade(icy, ring(Items.BLUE_ICE, 1));
		check(FrostTiers.tier(frozen) == FrostTier.FROZEN, "Icy + 8 blue ice should be Frozen");
		check(FrostTiers.upgrade(frozen, ring(Items.BLUE_ICE, 1)).isEmpty(), "Frozen is the last tier");

		List<ItemStack> gap = ring(Items.ICE, 1);
		gap.set(3, ItemStack.EMPTY);
		check(FrostTiers.upgrade(plate, gap).isEmpty(), "every outer slot needs ice");
		List<ItemStack> mixed = ring(Items.ICE, 1);
		mixed.set(0, new ItemStack(Items.SNOWBALL));
		check(FrostTiers.upgrade(plate, mixed).isEmpty(), "anything else in the ring stops it");

		check(FrostTiers.upgrade(new ItemStack(Items.IRON_PICKAXE), ring(Items.ICE, 1)).isEmpty(), "tools can't be imbued");
		check(!FrostTiers.canImbue(new ItemStack(Items.IRON_HORSE_ARMOR)), "horse armour can't be imbued");
		check(!FrostTiers.canImbue(new ItemStack(Items.ELYTRA)), "an elytra isn't armour");
		check(FrostTiers.canImbue(new ItemStack(Items.TURTLE_HELMET)) && FrostTiers.canImbue(new ItemStack(CopperGear.COPPER_BOOTS)),
				"turtle shells and copper armour can be imbued");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void tiersBoostArmourAndDurability(TestContext ctx) {
		FrostboundConfig.FrostTierOptions c = FrostboundConfig.get().frostTiers;
		Item item = Items.DIAMOND_CHESTPLATE;
		int base = new ItemStack(item).getMaxDamage();
		double[] armor = new double[4];
		double[] toughness = new double[4];
		int[] durability = new int[4];
		for (FrostTier tier : FrostTier.values()) {
			ItemStack stack = new ItemStack(item);
			FrostTiers.setTier(stack, tier);
			armor[tier.level()] = sum(stack, EquipmentSlot.CHEST, EntityAttributes.GENERIC_ARMOR);
			toughness[tier.level()] = sum(stack, EquipmentSlot.CHEST, EntityAttributes.GENERIC_ARMOR_TOUGHNESS);
			durability[tier.level()] = stack.getMaxDamage();
		}
		check(armor[0] == 8.0 && toughness[0] == 2.0 && durability[0] == base, "plain diamond chestplate unchanged");
		close(armor[1], 8.0 + c.coldArmor, "Cold armour");
		close(armor[2], armor[1], "Icy adds no armour");
		close(armor[3], 8.0 + c.coldArmor + c.frozenArmor, "Frozen armour");
		close(toughness[1], 2.0, "Cold toughness");
		close(toughness[3], 2.0 + c.frozenToughness, "Frozen toughness");
		check(durability[1] == Math.round(base * (1 + c.coldDurability)), "Cold durability should be " + Math.round(base * (1 + c.coldDurability)) + ", is " + durability[1]);
		check(durability[2] == Math.round(base * (1 + c.coldDurability + c.icyDurability)), "Icy durability, is " + durability[2]);
		check(durability[3] == durability[2], "Frozen adds no durability");

		// Worn: the player's armour attribute picks it up.
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		ItemStack frozen = new ItemStack(item);
		FrostTiers.setTier(frozen, FrostTier.FROZEN);
		player.equipStack(EquipmentSlot.CHEST, frozen);
		player.tick();
		close(player.getAttributeValue(EntityAttributes.GENERIC_ARMOR), armor[3], "a player wearing Frozen diamond");

		// The tier survives smithing into netherite and keeps working on the new item's stats.
		ItemStack netherite = frozen.copyComponentsToNewStack(Items.NETHERITE_CHESTPLATE, 1);
		check(FrostTiers.tier(netherite) == FrostTier.FROZEN, "smithing keeps the tier");
		close(sum(netherite, EquipmentSlot.CHEST, EntityAttributes.GENERIC_ARMOR), 8.0 + c.coldArmor + c.frozenArmor, "Frozen netherite armour");
		check(netherite.getMaxDamage() == Math.round(new ItemStack(Items.NETHERITE_CHESTPLATE).getMaxDamage() * (1 + c.coldDurability + c.icyDurability)),
				"Frozen netherite durability follows netherite's");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void tableMenuUpgradesAndConsumes(TestContext ctx) {
		BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		ctx.setBlockState(1, 2, 1, Imbuement.TABLE);
		PlayerEntity player = serverPlayer(ctx); // a server player, so closing hands items back
		ImbuementScreenHandler menu = new ImbuementScreenHandler(0, player.getInventory(), ScreenHandlerContext.create(ctx.getWorld(), pos));

		Slot center = menu.getSlot(ImbuementScreenHandler.CENTER_SLOT);
		check(!center.canInsert(new ItemStack(Items.PACKED_ICE)), "the centre slot only takes armour");
		check(!center.canInsert(new ItemStack(Items.DIAMOND_HORSE_ARMOR)), "the centre slot doesn't take horse armour");
		check(center.canInsert(new ItemStack(Items.LEATHER_BOOTS)), "the centre slot takes armour");
		check(center.canInsert(new ItemStack(Items.IRON_SWORD)), "the centre slot takes weapons");
		check(center.getMaxItemCount() == 1, "one piece at a time");

		ItemStack cold = new ItemStack(Items.GOLDEN_HELMET);
		FrostTiers.setTier(cold, FrostTier.COLD);
		center.setStack(cold);
		for (int i = 0; i < 9; i++) {
			if (i != ImbuementScreenHandler.CENTER) {
				menu.getSlot(ImbuementScreenHandler.GRID_START + i).setStack(new ItemStack(Items.PACKED_ICE, 5));
			}
		}
		Slot result = menu.getSlot(ImbuementScreenHandler.RESULT);
		check(result.getStack().isOf(Items.GOLDEN_HELMET) && FrostTiers.tier(result.getStack()) == FrostTier.ICY,
				"the result should be an Icy golden helmet, is " + result.getStack());

		ItemStack taken = menu.quickMove(player, ImbuementScreenHandler.RESULT);
		check(FrostTiers.tier(taken) == FrostTier.ICY, "shift-clicking the result gives the Icy helmet");
		check(player.getInventory().count(Items.GOLDEN_HELMET) == 1, "the helmet should be in the player's inventory");
		check(center.getStack().isEmpty(), "the centre armour is used up");
		for (int i = 0; i < 9; i++) {
			if (i != ImbuementScreenHandler.CENTER) {
				ItemStack left = menu.getSlot(ImbuementScreenHandler.GRID_START + i).getStack();
				check(left.isOf(Items.PACKED_ICE) && left.getCount() == 1, "4 packed ice should be taken from each slot, left " + left);
			}
		}
		check(result.getStack().isEmpty(), "nothing more to make");

		// Closing the menu gives back what's left in the grid.
		menu.onClosed(player);
		check(player.getInventory().count(Items.PACKED_ICE) == 8, "the leftover packed ice should go back to the player");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void starsAndPassivesInTooltips(TestContext ctx) {
		check(FrostTiers.stars(FrostTier.NONE).getString().isEmpty(), "no stars on plain armour");
		check(FrostTiers.stars(FrostTier.FROZEN).getString().equals(" " + FrostTiers.STAR.repeat(3)), "Frozen shows three stars");
		Text stars = FrostTiers.stars(FrostTier.FROZEN);
		for (int level = 1; level <= 3; level++) {
			int rgb = stars.getSiblings().get(level - 1).getStyle().getColor().getRgb();
			check(rgb == FrostTier.of(level).starColor, "star " + level + " should be its tier's colour");
			float[] hsb = Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
			check(hsb[1] < 0.4f && hsb[0] > 0.5f && hsb[0] < 0.6f, "star " + level + " should be a soft, unsaturated blue");
			if (level > 1) {
				check(hsb[2] < Color.RGBtoHSB(FrostTier.of(level - 1).starColor >> 16 & 0xFF, FrostTier.of(level - 1).starColor >> 8 & 0xFF,
						FrostTier.of(level - 1).starColor & 0xFF, null)[2], "each star is darker than the one before");
			}
		}

		// The title: "Frozen Leather Tunic ★★★", the tier name in its star colour, the item name untouched.
		Text name = Text.literal("Leather Tunic");
		check(FrostTiers.title(FrostTier.NONE, name) == name, "plain armour keeps its name");
		for (FrostTier tier : List.of(FrostTier.COLD, FrostTier.ICY, FrostTier.FROZEN)) {
			Text title = FrostTiers.title(tier, name);
			String word = switch (tier) { case COLD -> "Cold"; case ICY -> "Icy"; default -> "Frozen"; };
			String expected = word + " Leather Tunic " + FrostTiers.STAR.repeat(tier.level());
			check(title.getString().equals(expected), "title should read \"" + expected + "\", got \"" + title.getString() + "\"");
			Text prefix = FrostTiers.prefix(tier);
			check(prefix.getStyle().getColor().getRgb() == tier.starColor, word + " should be in its star's colour");
		}

		ItemStack icy = new ItemStack(Items.LEATHER_CHESTPLATE);
		check(Passives.of(icy).isEmpty(), "plain leather has no passive");
		FrostTiers.setTier(icy, FrostTier.COLD);
		check(Passives.of(icy).isEmpty(), "Cold leather has no passive yet");
		FrostTiers.setTier(icy, FrostTier.ICY);
		check(Passives.of(icy).equals(List.of(FrostBonuses.SNOWSTRIDE.passive)), "Icy leather has Snowstride");
		Passive snowstride = FrostBonuses.SNOWSTRIDE.passive;
		check(snowstride.title().getString().equals("Snowstride") && !snowstride.title().getStyle().isBold()
				&& Formatting.WHITE.getColorValue().equals(snowstride.title().getStyle().getColor().getRgb()), "Snowstride in white, not bold or all caps");
		check(FrostBonuses.HEARTHGOLD.passive.color() == Formatting.LIGHT_PURPLE && FrostBonuses.QUENCHED.passive.color() == Formatting.GOLD
				&& FrostBonuses.SUPERCONDUCTOR.passive.color() == Formatting.YELLOW && FrostBonuses.TIDEFROST.passive.color() == Formatting.DARK_AQUA,
				"each bonus is coloured after what it does");
		FrostboundConfig.FrostBonusOptions o = FrostboundConfig.get().frostBonuses;
		String icyText = "+" + Passive.number(o.snowstride.icy * 100) + "% movement speed on snow and ice for each piece worn.";
		check(snowstride.description(icy).getString().equals(icyText), "Icy text should be \"" + icyText + "\", got \"" + snowstride.description(icy).getString() + "\"");
		ItemStack frozen = icy.copy();
		FrostTiers.setTier(frozen, FrostTier.FROZEN);
		String frozenText = "+" + Passive.number(o.snowstride.frozen * 100) + "% movement speed on snow and ice for each piece worn.";
		check(snowstride.description(frozen).getString().equals(frozenText), "Frozen shows the stronger number, got " + snowstride.description(frozen).getString());

		// Every armour material has its own bonus.
		for (Item item : List.of(Items.LEATHER_BOOTS, CopperGear.COPPER_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.GOLDEN_BOOTS,
				Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS, Items.TURTLE_HELMET)) {
			ItemStack stack = new ItemStack(item);
			FrostTiers.setTier(stack, FrostTier.ICY);
			check(Passives.of(stack).size() == 1, item + " should have exactly one frost bonus when Icy, has " + Passives.of(stack).size());
			check(!Passives.of(stack).get(0).description(stack).getString().contains("%s"), item + "'s text should fill in its numbers");
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void coldIronHitsUndeadHarder(TestContext ctx) {
		FrostboundConfig.Tiered n = FrostboundConfig.get().frostBonuses.coldIron;
		PlayerEntity plain = ctx.createMockPlayer(GameMode.SURVIVAL);
		PlayerEntity iron = ctx.createMockPlayer(GameMode.SURVIVAL);
		wear(iron, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, FrostTier.FROZEN);
		SkeletonEntity a = ctx.spawnEntity(EntityType.SKELETON, 1, 2, 1);
		SkeletonEntity b = ctx.spawnEntity(EntityType.SKELETON, 3, 2, 1);
		a.damage(ctx.getWorld().getDamageSources().playerAttack(plain), 4.0f);
		b.damage(ctx.getWorld().getDamageSources().playerAttack(iron), 4.0f);
		float plainLoss = a.getMaxHealth() - a.getHealth();
		float ironLoss = b.getMaxHealth() - b.getHealth();
		close(ironLoss, plainLoss * (1 + 4 * n.frozen), "full Frozen iron vs a skeleton");
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, 5, 2, 1);
		close(FrostBonuses.modifyDamage(pig, ctx.getWorld().getDamageSources().playerAttack(iron), 4.0f), 4.0f, "Cold Iron does nothing to a pig");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void frostmailAndQuenchedReduceDamage(TestContext ctx) {
		FrostboundConfig.FrostBonusOptions o = FrostboundConfig.get().frostBonuses;
		ServerWorld world = ctx.getWorld();
		ZombieEntity chain = ctx.spawnEntity(EntityType.ZOMBIE, 1, 2, 1);
		wear(chain, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS, FrostTier.ICY);
		SkeletonEntity archer = ctx.spawnEntity(EntityType.SKELETON, 3, 2, 1);
		DamageSource arrow = world.getDamageSources().arrow(EntityType.ARROW.create(world), archer);
		close(FrostBonuses.modifyDamage(chain, arrow, 10.0f), (float) (10.0 * (1 - 4 * o.frostmail.icy)), "Icy chainmail vs an arrow");
		close(FrostBonuses.modifyDamage(chain, world.getDamageSources().mobAttack(archer), 10.0f), 10.0f, "Frostmail ignores melee");

		ZombieEntity netherite = ctx.spawnEntity(EntityType.ZOMBIE, 5, 2, 1);
		ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET);
		FrostTiers.setTier(helmet, FrostTier.FROZEN);
		netherite.equipStack(EquipmentSlot.HEAD, helmet);
		close(FrostBonuses.modifyDamage(netherite, world.getDamageSources().lava(), 10.0f), (float) (10.0 * (1 - o.quenched.frozen)), "one Frozen netherite piece vs lava");
		close(FrostBonuses.modifyDamage(netherite, world.getDamageSources().onFire(), 10.0f), (float) (10.0 * (1 - o.quenched.frozen)), "Quenched vs burning");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void diamondDustHurtsAttackers(TestContext ctx) {
		FrostboundConfig.Tiered n = FrostboundConfig.get().frostBonuses.diamondDust;
		ZombieEntity defender = ctx.spawnEntity(EntityType.ZOMBIE, 1, 2, 1);
		wear(defender, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, FrostTier.FROZEN);
		PigEntity attacker = ctx.spawnEntity(EntityType.PIG, 2, 2, 1);
		float before = defender.getHealth();
		defender.damage(ctx.getWorld().getDamageSources().mobAttack(attacker), 6.0f);
		float taken = before - defender.getHealth();
		check(taken > 0, "the defender should take some damage");
		// Fabric reports the hit before the zombie's own 2 armour, so allow a little either way.
		float back = attacker.getMaxHealth() - attacker.getHealth();
		check(Math.abs(back - taken * 4 * n.frozen) < 0.1f, "the attacker takes back 4 × " + n.frozen + " of " + taken + ", took " + back);

		PigEntity archer = ctx.spawnEntity(EntityType.PIG, 4, 2, 1);
		defender.damage(ctx.getWorld().getDamageSources().arrow(EntityType.ARROW.create(ctx.getWorld()), archer), 3.0f);
		check(archer.getHealth() == archer.getMaxHealth(), "Diamond Dust only answers melee");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void superconductorArcsWithAnyWeapon(TestContext ctx) {
		FrostboundConfig.FrostBonusOptions o = FrostboundConfig.get().frostBonuses;
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		check(Conductivity.chanceFor(player) == 0.0f, "an iron sword with no copper armour never arcs");
		wear(player, CopperGear.COPPER_HELMET, CopperGear.COPPER_CHESTPLATE, CopperGear.COPPER_LEGGINGS, CopperGear.COPPER_BOOTS, FrostTier.ICY);
		close(Conductivity.chanceFor(player), (float) (4 * o.superconductor.icy), "full Icy copper with an iron sword");
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(CopperGear.COPPER_SWORD));
		close(Conductivity.chanceFor(player), FrostboundConfig.get().conductivity.chance + (float) (4 * o.superconductor.icy), "stacks with a copper sword");

		double saved = o.superconductor.icy;
		try {
			o.superconductor.icy = 0.25; // 4 pieces: a sure arc
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			player.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(1, 2, 0))));
			PigEntity target = ctx.spawnEntity(EntityType.PIG, 1, 2, 1);
			PigEntity near = ctx.spawnEntity(EntityType.PIG, 2, 2, 1);
			target.damage(ctx.getWorld().getDamageSources().playerAttack(player), 2.0f);
			check(near.getHealth() < near.getMaxHealth(), "a sure Superconductor arc should shock the nearby pig");
		} finally {
			o.superconductor.icy = saved;
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void snowstrideHearthgoldAndTidefrost(TestContext ctx) {
		FrostboundConfig.FrostBonusOptions o = FrostboundConfig.get().frostBonuses;
		// Snowstride: faster on a snow block, not on stone.
		PlayerEntity runner = ctx.createMockPlayer(GameMode.SURVIVAL);
		wear(runner, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS, FrostTier.ICY);
		ctx.setBlockState(1, 1, 1, Blocks.SNOW_BLOCK);
		ctx.setBlockState(3, 1, 1, Blocks.STONE);
		runner.setPosition(Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(1, 2, 1))));
		runner.setOnGround(true, Vec3d.ZERO); // also finds the block underfoot, as moving would
		FrostBonuses.tick(runner);
		EntityAttributeModifier boost = runner.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).getModifier(com.frostbound.Frostbound.id("snowstride"));
		check(boost != null && Math.abs(boost.value() - 4 * o.snowstride.icy) < 1e-6, "full Icy leather on snow: +" + 4 * o.snowstride.icy + " speed, got " + boost);
		runner.setPosition(Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(3, 2, 1))));
		runner.setOnGround(true, Vec3d.ZERO);
		FrostBonuses.tick(runner);
		check(runner.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).getModifier(com.frostbound.Frostbound.id("snowstride")) == null,
				"no Snowstride on stone (standing in " + runner.getWorld().getBlockState(runner.getBlockPos()) + " on " + runner.getWorld().getBlockState(runner.getSteppingPos()) + " at " + runner.getSteppingPos() + ")");

		// Hearthgold: one Icy piece heals half a heart every N seconds.
		PlayerEntity warm = ctx.createMockPlayer(GameMode.SURVIVAL);
		ItemStack gold = new ItemStack(Items.GOLDEN_CHESTPLATE);
		FrostTiers.setTier(gold, FrostTier.ICY);
		warm.equipStack(EquipmentSlot.CHEST, gold);
		warm.setHealth(10.0f);
		int ticks = (int) Math.round(o.hearthgold.icy * 20);
		for (int i = 0; i < ticks - 1; i++) {
			FrostBonuses.tick(warm);
		}
		check(warm.getHealth() == 10.0f, "not healed before " + o.hearthgold.icy + " s");
		FrostBonuses.tick(warm);
		check(warm.getHealth() == 11.0f, "healed half a heart after " + o.hearthgold.icy + " s, health " + warm.getHealth());

		// Tidefrost: a Frozen turtle shell gives longer Water Breathing.
		PlayerEntity diver = ctx.createMockPlayer(GameMode.SURVIVAL);
		ItemStack shell = new ItemStack(Items.TURTLE_HELMET);
		FrostTiers.setTier(shell, FrostTier.FROZEN);
		diver.equipStack(EquipmentSlot.HEAD, shell);
		FrostBonuses.tick(diver);
		StatusEffectInstance breathing = diver.getStatusEffect(StatusEffects.WATER_BREATHING);
		check(breathing != null && breathing.getDuration() == 200 + (int) Math.round(o.tidefrost.frozen * 20),
				"Frozen turtle shell: Water Breathing should last " + (10 + o.tidefrost.frozen) + " s, is " + breathing);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void weaponsCanBeImbued(TestContext ctx) {
		FrostboundConfig.FrostTierOptions c = FrostboundConfig.get().frostTiers;
		for (Item item : List.of(Items.IRON_SWORD, Items.DIAMOND_AXE, CopperGear.COPPER_SWORD, Items.MACE, Items.TRIDENT)) {
			check(FrostTiers.canImbue(new ItemStack(item)), item + " should be imbuable");
		}
		for (Item item : List.of(Items.IRON_PICKAXE, Items.BOW, Items.SHIELD, Items.STICK)) {
			check(!FrostTiers.canImbue(new ItemStack(item)), item + " shouldn't be imbuable");
		}
		ItemStack sword = FrostTiers.upgrade(new ItemStack(Items.IRON_SWORD), ring(Items.ICE, 1));
		check(FrostTiers.tier(sword) == FrostTier.COLD, "iron sword + 8 ice should be Cold");

		// Attack damage: +cold, then +frozen on top; durability like armour.
		int base = new ItemStack(Items.IRON_SWORD).getMaxDamage();
		double[] damage = new double[4];
		for (FrostTier tier : FrostTier.values()) {
			ItemStack stack = new ItemStack(Items.IRON_SWORD);
			FrostTiers.setTier(stack, tier);
			damage[tier.level()] = sum(stack, EquipmentSlot.MAINHAND, EntityAttributes.GENERIC_ATTACK_DAMAGE);
			check(stack.getMaxDamage() == Math.round(base * FrostTiers.durabilityMultiplier(tier)), tier + " sword durability, is " + stack.getMaxDamage());
			close(sum(stack, EquipmentSlot.MAINHAND, EntityAttributes.GENERIC_ARMOR), 0.0, tier + " sword gets no armour");
		}
		close(damage[0], 5.0, "plain iron sword");
		close(damage[1], 5.0 + c.coldAttackDamage, "Cold iron sword");
		close(damage[2], damage[1], "Icy adds no damage");
		close(damage[3], 5.0 + c.coldAttackDamage + c.frozenAttackDamage, "Frozen iron sword");

		// Held, the player's attack damage picks it up; in the off hand it doesn't.
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		ItemStack frozen = new ItemStack(Items.IRON_SWORD);
		FrostTiers.setTier(frozen, FrostTier.FROZEN);
		player.setStackInHand(Hand.MAIN_HAND, frozen);
		player.tick();
		close(player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE), 1.0 + damage[3], "a player holding a Frozen iron sword");

		// Frostbite: from Icy, the stronger number when Frozen.
		FrostboundConfig.Tiered o = FrostboundConfig.get().frostBonuses.frostbite;
		check(Passives.of(sword).isEmpty(), "a Cold sword has no passive yet");
		ItemStack icy = sword.copy();
		FrostTiers.setTier(icy, FrostTier.ICY);
		check(Passives.of(icy).equals(List.of(FrostBonuses.FROSTBITE.passive)), "an Icy sword has Frostbite");
		Passive frostbite = FrostBonuses.FROSTBITE.passive;
		check(frostbite.title().getString().equals("Frostbite") && frostbite.color() == Formatting.AQUA, "Frostbite in aqua");
		String text = "Melee hits chill the target, slowing it for " + Passive.number(o.icy) + " seconds.";
		check(frostbite.description(icy).getString().equals(text), "Icy text should be \"" + text + "\", got \"" + frostbite.description(icy).getString() + "\"");
		check(frostbite.description(frozen).getString().contains(Passive.number(o.frozen) + " seconds"), "Frozen shows the stronger number");
		ItemStack copper = new ItemStack(CopperGear.COPPER_SWORD);
		FrostTiers.setTier(copper, FrostTier.ICY);
		check(Passives.of(copper).equals(List.of(Conductivity.PASSIVE, FrostBonuses.FROSTBITE.passive)), "an Icy copper sword has Conductivity, then Frostbite");
		ItemStack boots = new ItemStack(Items.IRON_BOOTS);
		FrostTiers.setTier(boots, FrostTier.FROZEN);
		check(!Passives.of(boots).contains(FrostBonuses.FROSTBITE.passive), "armour doesn't get Frostbite");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void frostbiteSlowsWhatYouHit(TestContext ctx) {
		FrostboundConfig.Tiered o = FrostboundConfig.get().frostBonuses.frostbite;
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		ItemStack icy = new ItemStack(Items.IRON_SWORD);
		FrostTiers.setTier(icy, FrostTier.ICY);
		player.setStackInHand(Hand.MAIN_HAND, icy);
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, 1, 2, 1);
		pig.damage(ctx.getWorld().getDamageSources().playerAttack(player), 1.0f);
		StatusEffectInstance slow = pig.getStatusEffect(StatusEffects.SLOWNESS);
		check(slow != null && slow.getDuration() == Math.round(o.icy * 20), "an Icy sword should slow for " + o.icy + " s, got " + slow);

		ItemStack frozen = icy.copy();
		FrostTiers.setTier(frozen, FrostTier.FROZEN);
		player.setStackInHand(Hand.MAIN_HAND, frozen);
		PigEntity second = ctx.spawnEntity(EntityType.PIG, 3, 2, 1);
		second.damage(ctx.getWorld().getDamageSources().playerAttack(player), 1.0f);
		slow = second.getStatusEffect(StatusEffects.SLOWNESS);
		check(slow != null && slow.getDuration() == Math.round(o.frozen * 20), "a Frozen sword should slow for " + o.frozen + " s, got " + slow);

		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		PigEntity plain = ctx.spawnEntity(EntityType.PIG, 5, 2, 1);
		plain.damage(ctx.getWorld().getDamageSources().playerAttack(player), 1.0f);
		check(!plain.hasStatusEffect(StatusEffects.SLOWNESS), "a plain sword doesn't slow");

		player.setStackInHand(Hand.MAIN_HAND, frozen);
		PigEntity shot = ctx.spawnEntity(EntityType.PIG, 5, 2, 3);
		shot.damage(ctx.getWorld().getDamageSources().arrow(EntityType.ARROW.create(ctx.getWorld()), player), 1.0f);
		check(!shot.hasStatusEffect(StatusEffects.SLOWNESS), "Frostbite is melee only");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void bonusesShowNextToVanillaLines(TestContext ctx) {
		FrostboundConfig.FrostTierOptions c = FrostboundConfig.get().frostTiers;
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		Item.TooltipContext context = Item.TooltipContext.create(ctx.getWorld());

		List<String> plain = strings(new ItemStack(Items.DIAMOND_CHESTPLATE).getTooltip(context, player, TooltipType.BASIC));
		check(plain.contains("+8 Armor") && plain.contains("+2 Armor Toughness"), "plain diamond keeps vanilla's lines: " + plain);

		ItemStack plate = new ItemStack(Items.DIAMOND_CHESTPLATE);
		FrostTiers.setTier(plate, FrostTier.FROZEN);
		List<Text> lines = plate.getTooltip(context, player, TooltipType.BASIC);
		String armor = "+8 Armor (+" + fmt(c.coldArmor + c.frozenArmor) + ")";
		String toughness = "+2 Armor Toughness (+" + fmt(c.frozenToughness) + ")";
		check(strings(lines).contains(armor) && strings(lines).contains(toughness), "Frozen diamond should show \"" + armor + "\" and \""
				+ toughness + "\": " + strings(lines));
		Text armorLine = lines.stream().filter(l -> l.getString().equals(armor)).findFirst().orElseThrow();
		Text bonus = armorLine.getSiblings().get(armorLine.getSiblings().size() - 1);
		check(bonus.getStyle().getColor() != null && bonus.getStyle().getColor().getRgb() == FrostTier.FROZEN.starColor,
				"the (+x) should be in the tier's colour");

		ItemStack cold = new ItemStack(Items.LEATHER_HELMET);
		FrostTiers.setTier(cold, FrostTier.COLD);
		check(strings(cold.getTooltip(context, player, TooltipType.BASIC)).contains("+1 Armor (+" + fmt(c.coldArmor) + ")"), "Cold leather cap");

		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		FrostTiers.setTier(sword, FrostTier.COLD);
		List<String> swordLines = strings(sword.getTooltip(context, player, TooltipType.BASIC));
		String attack = " 6 Attack Damage (+" + fmt(c.coldAttackDamage) + ")";
		check(swordLines.contains(attack) && swordLines.contains(" 1.6 Attack Speed"), "Cold iron sword should show \"" + attack + "\": " + swordLines);

		// Durability: added with its (+x) when vanilla doesn't show it, appended to vanilla's line when it does.
		int extra = FrostTiers.durabilityBonus(plate);
		check(extra == plate.getMaxDamage() - new ItemStack(Items.DIAMOND_CHESTPLATE).getMaxDamage() && extra > 0, "durability bonus " + extra);
		List<Text> basic = new ArrayList<>(lines);
		FrostStars.addDurabilityBonus(plate, FrostTier.FROZEN, basic, false);
		String durability = "Durability: " + plate.getMaxDamage() + " / " + plate.getMaxDamage() + " (+" + extra + ")";
		check(basic.get(basic.size() - 1).getString().equals(durability), "should end with \"" + durability + "\", got " + strings(basic));

		plate.setDamage(10);
		List<Text> advanced = new ArrayList<>(plate.getTooltip(context, player, TooltipType.ADVANCED));
		int before = advanced.size();
		FrostStars.addDurabilityBonus(plate, FrostTier.FROZEN, advanced, true);
		String damaged = "Durability: " + (plate.getMaxDamage() - 10) + " / " + plate.getMaxDamage() + " (+" + extra + ")";
		check(advanced.size() == before && strings(advanced).contains(damaged), "advanced tooltips get the (+x) on their own line: " + strings(advanced));

		List<Text> none = new ArrayList<>(new ItemStack(Items.DIAMOND_CHESTPLATE).getTooltip(context, player, TooltipType.BASIC));
		int size = none.size();
		FrostStars.addDurabilityBonus(new ItemStack(Items.DIAMOND_CHESTPLATE), FrostTier.NONE, none, false);
		check(none.size() == size, "plain gear gets no durability line");
		ctx.complete();
	}

	private static List<String> strings(List<Text> lines) {
		return lines.stream().map(Text::getString).toList();
	}

	private static String fmt(double value) {
		return AttributeModifiersComponent.DECIMAL_FORMAT.format(value);
	}

	private static void wear(net.minecraft.entity.LivingEntity entity, Item head, Item chest, Item legs, Item feet, FrostTier tier) {
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		Item[] items = {head, chest, legs, feet};
		for (int i = 0; i < 4; i++) {
			ItemStack stack = new ItemStack(items[i]);
			FrostTiers.setTier(stack, tier);
			entity.equipStack(slots[i], stack);
		}
	}

	private static List<ItemStack> ring(Item item, int count) {
		return new ArrayList<>(Collections.nCopies(8, new ItemStack(item, count)));
	}

	private static double sum(ItemStack stack, EquipmentSlot slot, RegistryEntry<EntityAttribute> attribute) {
		double[] total = {0.0};
		stack.applyAttributeModifiers(slot, (a, m) -> {
			if (a.equals(attribute) && m.operation() == EntityAttributeModifier.Operation.ADD_VALUE) {
				total[0] += m.value();
			}
		});
		return total[0];
	}

	private static void close(double actual, double expected, String what) {
		check(Math.abs(actual - expected) < 0.01, what + ": expected " + expected + ", got " + actual);
	}

	/**
	 * A server player with a (fake) connection, like TestContext.createMockCreativeServerPlayerInWorld but
	 * without joining the player list: when it joins, Cardinal Components (from Trinkets) syncs to it over the
	 * fake connection, which gets it kicked, and a kicked player's items are dropped instead of handed back.
	 */
	static ServerPlayerEntity serverPlayer(TestContext ctx) {
		com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player");
		ServerPlayerEntity player = new ServerPlayerEntity(ctx.getWorld().getServer(), ctx.getWorld(), profile,
				net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());
		net.minecraft.network.ClientConnection connection = new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		new net.minecraft.server.network.ServerPlayNetworkHandler(ctx.getWorld().getServer(), connection, player,
				net.minecraft.server.network.ConnectedClientData.createDefault(profile, false));
		return player;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}

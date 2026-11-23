package com.frostbound.test;

import com.frostbound.config.FrostboundConfig;
import com.frostbound.copper.Conductivity;
import com.frostbound.copper.CopperGear;
import com.frostbound.passives.Passive;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.Collections;
import java.util.List;

/** Copper gear backported from 1.21.9 (stats, recipes, loot) and the Conductivity passive. */
public class CopperTests implements FabricGameTest {

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void copperToolsHave1219Stats(TestContext ctx) {
		checkTool(CopperGear.COPPER_SWORD, 5.0, 1.6);
		checkTool(CopperGear.COPPER_AXE, 9.0, 0.8);
		checkTool(CopperGear.COPPER_PICKAXE, 3.0, 1.2);
		checkTool(CopperGear.COPPER_SHOVEL, 3.5, 1.0);
		checkTool(CopperGear.COPPER_HOE, 1.0, 2.0);
		for (Item tool : List.of(CopperGear.COPPER_SWORD, CopperGear.COPPER_AXE, CopperGear.COPPER_PICKAXE,
				CopperGear.COPPER_SHOVEL, CopperGear.COPPER_HOE)) {
			check(new ItemStack(tool).getMaxDamage() == 190, tool + " should have 190 durability");
			check(new ItemStack(tool).isIn(Conductivity.CONDUCTIVE), tool + " should be conductive");
		}
		check(new ItemStack(CopperGear.COPPER_SWORD).isIn(ItemTags.SWORDS), "copper sword should be a sword (enchantable)");
		check(new ItemStack(CopperGear.COPPER_PICKAXE).isIn(ItemTags.PICKAXES), "copper pickaxe should be a pickaxe");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void copperArmorHas1219Stats(TestContext ctx) {
		checkArmor(CopperGear.COPPER_HELMET, 2, 121);
		checkArmor(CopperGear.COPPER_CHESTPLATE, 4, 176);
		checkArmor(CopperGear.COPPER_LEGGINGS, 3, 165);
		checkArmor(CopperGear.COPPER_BOOTS, 1, 143);
		check(new ItemStack(CopperGear.COPPER_HELMET).isIn(ItemTags.TRIMMABLE_ARMOR), "copper armour should be trimmable");
		check(new ItemStack(CopperGear.COPPER_HELMET).isIn(ItemTags.HEAD_ARMOR), "copper helmet should be head armour");
		check(!new ItemStack(CopperGear.COPPER_HELMET).isIn(Conductivity.CONDUCTIVE), "armour isn't conductive");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void copperPickaxeMinesLikeStone(TestContext ctx) {
		ItemStack pickaxe = new ItemStack(CopperGear.COPPER_PICKAXE);
		// Vanilla ore loot tables don't check the tool tier; harvesting does (isSuitableFor), like stone.
		for (Block block : List.of(Blocks.STONE, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.LAPIS_ORE)) {
			check(pickaxe.isSuitableFor(block.getDefaultState()), "copper pickaxe should harvest " + block);
		}
		for (Block block : List.of(Blocks.DIAMOND_ORE, Blocks.GOLD_ORE, Blocks.REDSTONE_ORE, Blocks.OBSIDIAN)) {
			check(!pickaxe.isSuitableFor(block.getDefaultState()), "copper pickaxe shouldn't harvest " + block);
		}
		check(drops(ctx, Blocks.IRON_ORE, pickaxe).stream().anyMatch(s -> s.isOf(Items.RAW_IRON)), "iron ore should drop raw iron");
		check(pickaxe.getMiningSpeedMultiplier(Blocks.STONE.getDefaultState()) == 5.0f, "copper digs at speed 5");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void copperRecipesWork(TestContext ctx) {
		ItemStack ingot = new ItemStack(Items.COPPER_INGOT);
		ItemStack stick = new ItemStack(Items.STICK);
		check(craft(ctx, 1, 3, List.of(ingot, ingot, stick)).isOf(CopperGear.COPPER_SWORD), "2 copper ingots and a stick make a copper sword");
		check(craft(ctx, 3, 2, List.of(ingot, ingot, ingot, ingot, ItemStack.EMPTY, ingot)).isOf(CopperGear.COPPER_HELMET), "copper helmet recipe");
		ItemStack nuggets = craft(ctx, 1, 1, List.of(ingot));
		check(nuggets.isOf(CopperGear.COPPER_NUGGET) && nuggets.getCount() == 9, "an ingot makes 9 copper nuggets, got " + nuggets);
		ItemStack nugget = new ItemStack(CopperGear.COPPER_NUGGET);
		check(craft(ctx, 3, 3, Collections.nCopies(9, nugget)).isOf(Items.COPPER_INGOT), "9 copper nuggets make an ingot");
		for (RecipeType<?> type : List.of(RecipeType.SMELTING, RecipeType.BLASTING)) {
			@SuppressWarnings("unchecked")
			var cooking = (RecipeType<net.minecraft.recipe.AbstractCookingRecipe>) type;
			ItemStack out = ctx.getWorld().getRecipeManager()
					.getFirstMatch(cooking, new SingleStackRecipeInput(new ItemStack(CopperGear.COPPER_CHESTPLATE)), ctx.getWorld())
					.map(e -> e.value().getResult(ctx.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
			check(out.isOf(CopperGear.COPPER_NUGGET), type + " a copper chestplate should give a copper nugget, got " + out);
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void copperHorseArmorIsInDungeonChests(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(
				RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.ofVanilla("chests/simple_dungeon")));
		LootContextParameterSet params = new LootContextParameterSet.Builder(world)
				.add(LootContextParameters.ORIGIN, Vec3d.ZERO).build(LootContextTypes.CHEST);
		for (long seed = 0; seed < 300; seed++) {
			if (table.generateLoot(params, seed).stream().anyMatch(s -> s.isOf(CopperGear.COPPER_HORSE_ARMOR))) {
				ctx.complete();
				return;
			}
		}
		throw new GameTestException("copper horse armour never showed up in 300 dungeon chests");
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void conductivityArcsToNearbyMobs(TestContext ctx) {
		FrostboundConfig.ConductivityOptions config = FrostboundConfig.get().conductivity;
		float chance = config.chance;
		try {
			// A guaranteed proc: the pig next to the target takes 150%; the far pig, the player and the player's wolf don't.
			config.chance = 1.0f;
			PlayerEntity player = swordsman(ctx);
			PigEntity target = ctx.spawnEntity(EntityType.PIG, 1, 2, 1);
			PigEntity near = ctx.spawnEntity(EntityType.PIG, 2, 2, 1);
			PigEntity far = ctx.spawnEntity(EntityType.PIG, 6, 2, 1);
			WolfEntity pet = ctx.spawnEntity(EntityType.WOLF, 1, 2, 2);
			pet.setOwnerUuid(player.getUuid());
			pet.setTamed(true, true);
			player.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new net.minecraft.util.math.BlockPos(1, 2, 0))));
			float petHealth = pet.getHealth();

			target.damage(ctx.getWorld().getDamageSources().playerAttack(player), 4.0f);
			float expected = near.getMaxHealth() - 4.0f * config.damageMultiplier;
			check(Math.abs(near.getHealth() - expected) < 0.01f, "the nearby pig should take 150% (" + expected + " health left), has " + near.getHealth());
			check(far.getHealth() == far.getMaxHealth(), "a pig 5 blocks away shouldn't be shocked");
			check(pet.getHealth() == petHealth, "the attacker's own wolf shouldn't be shocked");
			check(player.getHealth() == player.getMaxHealth(), "the attacker shouldn't be shocked");
			check(!Conductivity.wouldShock(player, ctx.createMockPlayer(GameMode.SURVIVAL)), "a player's arc shouldn't hit other players");

			// Never procs at 0%, and only melee hits with a conductive weapon count.
			config.chance = 0.0f;
			PlayerEntity other = swordsman(ctx);
			PigEntity target2 = ctx.spawnEntity(EntityType.PIG, 1, 2, 5);
			PigEntity near2 = ctx.spawnEntity(EntityType.PIG, 2, 2, 5);
			target2.damage(ctx.getWorld().getDamageSources().playerAttack(other), 4.0f);
			check(near2.getHealth() == near2.getMaxHealth(), "at 0% chance nothing should arc");

			config.chance = 1.0f;
			PlayerEntity unarmed = ctx.createMockPlayer(GameMode.SURVIVAL);
			unarmed.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			target2.damage(ctx.getWorld().getDamageSources().playerAttack(unarmed), 1.0f);
			check(near2.getHealth() == near2.getMaxHealth(), "an iron sword shouldn't conduct");
		} finally {
			config.chance = chance;
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void conductivityChainsThroughAWholeLine(TestContext ctx) {
		// Pigs 1 block apart in a row: only the first is within reach of the target, the rest only by chaining.
		PlayerEntity player = swordsman(ctx);
		PigEntity target = ctx.spawnEntity(EntityType.PIG, 0, 2, 1);
		List<PigEntity> line = new java.util.ArrayList<>();
		for (int x = 1; x <= 6; x++) {
			line.add(ctx.spawnEntity(EntityType.PIG, x * 2, 2, 1));
		}
		// Every roll passes: the chain runs the whole line, each pig shocked once, each for 150% of the hit (no compounding).
		List<net.minecraft.entity.LivingEntity> hit = Conductivity.arc(player, target, 2.0f, () -> 0.0f);
		check(hit.size() == line.size() && hit.containsAll(line), "the chain should reach all " + line.size() + " pigs, hit " + hit.size());
		float expected = line.get(0).getMaxHealth() - 2.0f * FrostboundConfig.get().conductivity.damageMultiplier;
		for (PigEntity pig : line) {
			check(Math.abs(pig.getHealth() - expected) < 0.01f, "every pig should take exactly one 150% shock, one has " + pig.getHealth());
		}
		check(target.getHealth() == target.getMaxHealth(), "the struck mob isn't shocked by its own arc");

		// Every later roll fails: only the first link happens.
		PigEntity target2 = ctx.spawnEntity(EntityType.PIG, 0, 2, 6);
		PigEntity first = ctx.spawnEntity(EntityType.PIG, 2, 2, 6);
		PigEntity second = ctx.spawnEntity(EntityType.PIG, 4, 2, 6);
		List<net.minecraft.entity.LivingEntity> short_ = Conductivity.arc(swordsman(ctx), target2, 2.0f, () -> 0.99f);
		check(short_.equals(List.of(first)), "with failed rolls only the first pig should be shocked, got " + short_.size());
		check(second.getHealth() == second.getMaxHealth(), "the second pig shouldn't be shocked without a chain");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void conductivityTooltipText(TestContext ctx) {
		Passive passive = Conductivity.PASSIVE;
		Text title = passive.title();
		check(title.getString().equals("CONDUCTIVITY"), "title should read CONDUCTIVITY, got " + title.getString());
		check(title.getStyle().isBold() && Formatting.GOLD.getColorValue().equals(title.getStyle().getColor().getRgb()),
				"CONDUCTIVITY should be bold gold");
		FrostboundConfig.ConductivityOptions c = FrostboundConfig.get().conductivity;
		String expected = Passive.number(c.chance * 100) + "% chance to deal " + Passive.number(c.damageMultiplier * 100)
				+ "% damage to any mobs within " + Passive.number(c.radius) + " blocks of the targeted mob. Can chain endlessly.";
		check(passive.description().getString().equals(expected), "description should be \"" + expected + "\", got \"" + passive.description().getString() + "\"");
		check(Passive.number(0.05f * 100).equals("5") && Passive.number(1.5).equals("1.5"), "numbers should print as 5 and 1.5");
		ctx.complete();
	}

	private static PlayerEntity swordsman(TestContext ctx) {
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(CopperGear.COPPER_SWORD));
		return player;
	}

	private static void checkTool(Item tool, double damage, double speed) {
		AttributeModifiersComponent mods = new ItemStack(tool).getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
		double attack = 1.0, attackSpeed = 4.0;
		for (AttributeModifiersComponent.Entry entry : mods.modifiers()) {
			if (entry.attribute().equals(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
				attack += entry.modifier().value();
			} else if (entry.attribute().equals(EntityAttributes.GENERIC_ATTACK_SPEED)) {
				attackSpeed += entry.modifier().value();
			}
		}
		check(Math.abs(attack - damage) < 0.01, tool + " should deal " + damage + ", deals " + attack);
		check(Math.abs(attackSpeed - speed) < 0.01, tool + " should have speed " + speed + ", has " + attackSpeed);
	}

	private static void checkArmor(Item item, int protection, int durability) {
		check(((ArmorItem) item).getProtection() == protection, item + " should give " + protection + " armour");
		check(new ItemStack(item).getMaxDamage() == durability, item + " should have " + durability + " durability, has " + new ItemStack(item).getMaxDamage());
	}

	private static List<ItemStack> drops(TestContext ctx, Block block, ItemStack tool) {
		LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(ctx.getWorld())
				.add(LootContextParameters.ORIGIN, Vec3d.ZERO)
				.add(LootContextParameters.TOOL, tool);
		return block.getDefaultState().getDroppedStacks(builder);
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

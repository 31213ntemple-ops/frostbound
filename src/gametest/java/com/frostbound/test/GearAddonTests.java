package com.frostbound.test;

import com.frostbound.addons.FlameExtract;
import com.frostbound.addons.GearAddons;
import com.frostbound.client.addons.AddonSlotTooltips;
import com.frostbound.compat.TanTemperature;
import com.frostbound.config.FrostboundConfig;
import com.frostbound.habanero.Habaneros;
import com.frostbound.imbuement.FrostTier;
import com.frostbound.imbuement.FrostTiers;
import com.frostbound.imbuement.Imbuement;
import com.frostbound.imbuement.ImbuementScreenHandler;
import com.frostbound.passives.Passive;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** The two add-on slots on gear, the imbuement table putting flame extract in them, and flame extract's insulation. */
public class GearAddonTests implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void gearHasTwoAddonSlots(TestContext ctx) {
		check(GearAddons.SLOTS == 2, "gear has two add-on slots");
		check(GearAddons.hasSlots(new ItemStack(Items.IRON_CHESTPLATE)) && GearAddons.hasSlots(new ItemStack(Items.DIAMOND_SWORD)),
				"armour and weapons have slots");
		check(!GearAddons.hasSlots(new ItemStack(Items.IRON_PICKAXE)) && !GearAddons.hasSlots(new ItemStack(Items.COOKED_BEEF)),
				"tools and food don't");

		ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
		ItemStack one = GearAddons.withAdded(chest, FlameExtract.ADDON);
		ItemStack two = GearAddons.withAdded(one, FlameExtract.ADDON);
		check(GearAddons.count(one, FlameExtract.ADDON) == 1 && GearAddons.count(two, FlameExtract.ADDON) == 2,
				"flame extract can fill one slot, or both");
		check(GearAddons.withAdded(two, FlameExtract.ADDON).isEmpty() && GearAddons.freeSlots(two) == 0, "a third doesn't fit");
		check(!chest.contains(GearAddons.COMPONENT), "adding doesn't change the original stack");
		check(GearAddons.withAdded(new ItemStack(Items.IRON_SWORD), FlameExtract.ADDON).isEmpty(), "flame extract only goes on armour");
		check(GearAddons.byItem(new ItemStack(Habaneros.FLAME_EXTRACT)) == FlameExtract.ADDON, "the flame extract item is the add-on");

		// Survives a frost tier and smithing, like the tier itself.
		FrostTiers.setTier(two, FrostTier.FROZEN);
		ItemStack netherite = two.copyComponentsToNewStack(Items.NETHERITE_CHESTPLATE, 1);
		check(GearAddons.count(netherite, FlameExtract.ADDON) == 2, "smithing keeps the add-ons");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void slotsLineShowsBracketsAndIcons(TestContext ctx) {
		ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
		Text empty = GearAddons.slotsLine(chest);
		check(empty.getString().equals("[" + GearAddons.EMPTY_ICON + "] [" + GearAddons.EMPTY_ICON + "]"),
				"two empty slots, got " + empty.getString());
		check(bracketColors(empty).equals(List.of(Formatting.GRAY, Formatting.GRAY, Formatting.GRAY, Formatting.GRAY)), "empty brackets are grey");

		ItemStack one = GearAddons.withAdded(chest, FlameExtract.ADDON);
		Text half = GearAddons.slotsLine(one);
		check(half.getString().equals("[" + FlameExtract.ADDON.icon() + "] [" + GearAddons.EMPTY_ICON + "]"), "a fire in the first slot, got " + half.getString());
		check(bracketColors(half).equals(List.of(Formatting.GOLD, Formatting.GOLD, Formatting.GRAY, Formatting.GRAY)),
				"a filled slot's brackets turn gold, got " + bracketColors(half));
		check(GearAddons.FONT.equals(half.getSiblings().get(1).getStyle().getFont()), "the icon is drawn in the add-on font");

		Text full = GearAddons.slotsLine(GearAddons.withAdded(one, FlameExtract.ADDON));
		check(bracketColors(full).equals(List.of(Formatting.GOLD, Formatting.GOLD, Formatting.GOLD, Formatting.GOLD)), "both slots gold");

		// Where it goes: under the stats (after the durability line), before the advanced tooltip's item id.
		List<Text> lines = new ArrayList<>(List.of(Text.literal("Iron Chestplate"), Text.literal("+6 Armor"), Text.literal("Durability: 240 / 240")));
		AddonSlotTooltips.addSlots(chest, lines, false);
		check(lines.size() == 4 && lines.get(3).getString().startsWith("["), "the slots come last in a normal tooltip");
		List<Text> advanced = new ArrayList<>(List.of(Text.literal("Iron Chestplate"), Text.literal("+6 Armor"),
				Text.literal("minecraft:iron_chestplate"), Text.literal("1 component(s)")));
		AddonSlotTooltips.addSlots(chest, advanced, true);
		check(advanced.get(2).getString().startsWith("[") && advanced.get(3).getString().equals("minecraft:iron_chestplate"),
				"the slots come before the item id in an advanced tooltip");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void imbuementTableAddsFlameExtract(TestContext ctx) {
		BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		ctx.setBlockState(1, 2, 1, Imbuement.TABLE);
		PlayerEntity player = ImbuementTests.serverPlayer(ctx);
		ImbuementScreenHandler menu = new ImbuementScreenHandler(0, player.getInventory(), ScreenHandlerContext.create(ctx.getWorld(), pos));
		Slot center = menu.getSlot(ImbuementScreenHandler.CENTER_SLOT);
		Slot result = menu.getSlot(ImbuementScreenHandler.RESULT);
		Slot top = menu.getSlot(ImbuementScreenHandler.GRID_START + 1);
		Slot corner = menu.getSlot(ImbuementScreenHandler.GRID_START + 8);

		ItemStack icy = new ItemStack(Items.IRON_HELMET);
		FrostTiers.setTier(icy, FrostTier.ICY);
		center.setStack(icy);
		top.setStack(new ItemStack(Habaneros.FLAME_EXTRACT, 3));
		check(GearAddons.count(result.getStack(), FlameExtract.ADDON) == 1 && FrostTiers.tier(result.getStack()) == FrostTier.ICY,
				"one flame extract next to the helmet adds it and keeps the tier, got " + result.getStack());

		corner.setStack(new ItemStack(Habaneros.FLAME_EXTRACT));
		check(result.getStack().isEmpty(), "two outer slots filled make nothing (one add-on at a time)");
		corner.setStack(ItemStack.EMPTY);

		ItemStack taken = menu.quickMove(player, ImbuementScreenHandler.RESULT);
		check(GearAddons.count(taken, FlameExtract.ADDON) == 1, "taking the result gives the helmet with flame extract");
		check(center.getStack().isEmpty(), "the helmet in the centre is used up");
		check(top.getStack().isOf(Habaneros.FLAME_EXTRACT) && top.getStack().getCount() == 2, "one flame extract is used, left " + top.getStack());
		check(player.getInventory().count(Items.GLASS_BOTTLE) == 1, "its bottle is handed back");

		// Its second slot, then it's full.
		center.setStack(taken);
		ItemStack twice = menu.quickMove(player, ImbuementScreenHandler.RESULT);
		check(GearAddons.count(twice, FlameExtract.ADDON) == 2, "a second flame extract fills the second slot");
		center.setStack(twice);
		check(result.getStack().isEmpty(), "a piece with both slots full takes no more");
		center.setStack(new ItemStack(Items.IRON_SWORD));
		check(result.getStack().isEmpty(), "flame extract doesn't go on weapons");

		// Ice still imbues as before.
		center.setStack(new ItemStack(Items.IRON_BOOTS));
		top.setStack(ItemStack.EMPTY);
		for (int i = 0; i < 9; i++) {
			if (i != ImbuementScreenHandler.CENTER) {
				menu.getSlot(ImbuementScreenHandler.GRID_START + i).setStack(new ItemStack(Items.ICE));
			}
		}
		check(FrostTiers.tier(result.getStack()) == FrostTier.COLD && !result.getStack().contains(GearAddons.COMPONENT), "8 ice still makes Cold");
		menu.onClosed(player);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void flameExtractInsulates(TestContext ctx) {
		int full = FlameExtract.forFull();
		check(full == 4, "four flame extracts make full insulation by default");
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, 1, 2, 1);
		check(FlameExtract.insulation(pig) == 0.0 && pig.canFreeze(), "no insulation without flame extract");

		wear(pig, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1);
		check(Math.abs(FlameExtract.insulation(pig) - 0.25) < 1e-9 && pig.canFreeze(), "one flame extract is a quarter insulated");
		wear(pig, EquipmentSlot.HEAD, Items.CHAINMAIL_HELMET, 1); // not leather: vanilla already makes that freeze-proof
		wear(pig, EquipmentSlot.LEGS, Items.GOLDEN_LEGGINGS, 1);
		check(!FlameExtract.isFullyInsulated(pig) && pig.canFreeze(), "three is still not enough");
		wear(pig, EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1);
		check(FlameExtract.isFullyInsulated(pig) && !pig.canFreeze(), "a full set of one each is fully insulated: no freezing");

		PigEntity pair = ctx.spawnEntity(EntityType.PIG, 3, 2, 1);
		wear(pair, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 2);
		check(FlameExtract.insulation(pair) == 0.5, "two in one piece count twice");
		wear(pair, EquipmentSlot.LEGS, Items.IRON_LEGGINGS, 2);
		check(FlameExtract.isFullyInsulated(pair) && !pair.canFreeze(), "two pieces with both slots filled are fully insulated");

		ItemStack held = GearAddons.withAdded(new ItemStack(Items.IRON_CHESTPLATE), FlameExtract.ADDON);
		PigEntity holder = ctx.spawnEntity(EntityType.PIG, 5, 2, 1);
		holder.equipStack(EquipmentSlot.MAINHAND, held);
		check(FlameExtract.worn(holder) == 0, "only armour that's worn counts");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void partialInsulationSlowsFreezing(TestContext ctx) {
		// Three pigs each in a pit of powder snow: bare, half insulated (2 extracts), fully insulated (4).
		PigEntity bare = pigInPowderSnow(ctx, 1);
		PigEntity half = pigInPowderSnow(ctx, 3);
		wear(half, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 2);
		PigEntity fully = pigInPowderSnow(ctx, 5);
		wear(fully, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 2);
		wear(fully, EquipmentSlot.FEET, Items.IRON_BOOTS, 2);
		ctx.runAtTick(110, () -> {
			int b = bare.getFrozenTicks(), h = half.getFrozenTicks(), f = fully.getFrozenTicks();
			check(b >= 60, "a bare pig in powder snow should freeze, frozen ticks " + b);
			check(h > b * 0.25 && h < b * 0.75, "half insulation should freeze about half as fast: " + h + " against " + b);
			check(f == 0, "a fully insulated pig doesn't freeze, frozen ticks " + f);
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void flameLinedTooltipText(TestContext ctx) {
		Passive passive = FlameExtract.PASSIVE;
		check(passive.title().getString().equals("Flame Lined"), "title should read Flame Lined, got " + passive.title().getString());
		check(Formatting.GOLD.getColorValue().equals(passive.title().getStyle().getColor().getRgb()), "Flame Lined is gold");
		ItemStack two = GearAddons.withAdded(GearAddons.withAdded(new ItemStack(Items.IRON_CHESTPLATE), FlameExtract.ADDON), FlameExtract.ADDON);
		check(!passive.appliesTo(new ItemStack(Items.IRON_CHESTPLATE)) && passive.appliesTo(two), "only armour with flame extract shows it");
		String expected = "50% insulation: slows freezing. At 100% across your armour you can't freeze and stay a step warmer.";
		check(passive.description(two).getString().equals(expected), "description should be \"" + expected + "\", got \"" + passive.description(two).getString() + "\"");
		List<Text> lines = new ArrayList<>();
		Habaneros.FLAME_EXTRACT.appendTooltip(new ItemStack(Habaneros.FLAME_EXTRACT), Item.TooltipContext.DEFAULT, lines,
				net.minecraft.item.tooltip.TooltipType.BASIC);
		check(lines.size() == 2 && lines.get(1).getString().equals("25% insulation each, 4 for full"),
				"the extract's tooltip gives its numbers, got " + lines.stream().map(Text::getString).toList());
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void warmthStopsShortOfHot(TestContext ctx) {
		check(TanTemperature.warmer(Level.COLD, 1) == Level.NEUTRAL, "cold, one step warmer, is neutral");
		check(TanTemperature.warmer(Level.ICY, 2) == Level.NEUTRAL, "two steps from icy");
		check(TanTemperature.warmer(Level.NEUTRAL, 2) == Level.WARM, "never pushed into hot");
		check(TanTemperature.warmer(Level.WARM, 1) == Level.WARM, "warm stays warm");
		check(TanTemperature.warmer(Level.HOT, 1) == Level.HOT, "hot stays hot");

		PlayerEntity player = ctx.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
		check(TanTemperature.warmth(player) == 0, "no warmth by default");
		player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(Habaneros.SPICY_EFFECT, 100));
		check(TanTemperature.warmth(player) == 1, "Spicy is a step warmer");
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST}) {
			ItemStack piece = new ItemStack(slot == EquipmentSlot.HEAD ? Items.IRON_HELMET : Items.IRON_CHESTPLATE);
			piece = GearAddons.withAdded(GearAddons.withAdded(piece, FlameExtract.ADDON), FlameExtract.ADDON);
			player.equipStack(slot, piece);
		}
		check(TanTemperature.warmth(player) == 2, "and full insulation another");

		if (FabricLoader.getInstance().isModLoaded("toughasnails")) {
			try {
				java.lang.reflect.Field field = Class.forName("toughasnails.temperature.TemperatureHelperImpl").getDeclaredField("playerModifiers");
				field.setAccessible(true);
				List<?> modifiers = (List<?>) field.get(null);
				check(modifiers.stream().anyMatch(m -> m.toString().equals("Frostbound warmth")), "Frostbound's warmth should be hooked into Tough As Nails");
			} catch (ReflectiveOperationException e) {
				throw new GameTestException("can't read Tough As Nails' modifiers: " + e);
			}
		}
		ctx.complete();
	}

	/** Stands in for Tough As Nails' TemperatureLevel (same order). */
	private enum Level { ICY, COLD, NEUTRAL, WARM, HOT }

	// --- Helpers ---

	private static void wear(MobEntity mob, EquipmentSlot slot, Item item, int extracts) {
		ItemStack piece = new ItemStack(item);
		for (int i = 0; i < extracts; i++) {
			piece = GearAddons.withAdded(piece, FlameExtract.ADDON);
		}
		if (piece.isEmpty()) {
			throw new GameTestException(item + " can't take " + extracts + " flame extracts");
		}
		mob.equipStack(slot, piece);
	}

	/** A pig in a 1 × 1 pit of powder snow two deep, walled with stone, at x. */
	private static PigEntity pigInPowderSnow(TestContext ctx, int x) {
		for (int y = 1; y <= 3; y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = 0; dz <= 2; dz++) {
					ctx.setBlockState(x + dx, y, dz, dx == 0 && dz == 1 && y >= 2 ? Blocks.POWDER_SNOW : Blocks.STONE);
				}
			}
		}
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, x, 2, 1);
		pig.setPersistent();
		return pig;
	}

	private static List<Formatting> bracketColors(Text line) {
		List<Formatting> colors = new ArrayList<>();
		for (Text part : line.getSiblings()) {
			String s = part.getString();
			if (s.equals("[") || s.equals("]")) {
				TextColor color = part.getStyle().getColor();
				Formatting match = null;
				for (Formatting f : List.of(Formatting.GOLD, Formatting.GRAY)) {
					if (color != null && f.getColorValue().equals(color.getRgb())) {
						match = f;
					}
				}
				colors.add(match);
			}
		}
		return colors;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}

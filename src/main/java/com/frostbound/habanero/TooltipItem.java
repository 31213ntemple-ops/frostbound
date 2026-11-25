package com.frostbound.habanero;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.function.Supplier;

/**
 * An item with a few grey lines under its name saying what it's for: lang keys
 * {@code item.frostbound.<id>.tooltip.1}, {@code .2}, ..., their %s filled by {@code args} (read from the
 * config each time, so they follow it).
 */
public class TooltipItem extends Item {
	private final int lines;
	private final Supplier<Object[]> args;

	public TooltipItem(Settings settings, int lines, Supplier<Object[]> args) {
		super(settings);
		this.lines = lines;
		this.args = args;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		Object[] values = args.get();
		for (int i = 1; i <= lines; i++) {
			tooltip.add(Text.translatable(getTranslationKey() + ".tooltip." + i, values).formatted(Formatting.GRAY));
		}
	}
}

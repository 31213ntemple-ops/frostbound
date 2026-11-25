package com.frostbound.addons;

import com.frostbound.Frostbound;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Something that can be put into one of a piece of gear's add-on slots at the imbuement table, such as
 * flame extract (insulation). Each piece of gear has {@link GearAddons#SLOTS} slots; the same add-on can
 * fill both, and then counts twice.
 *
 * In the tooltip a filled slot shows {@link #icon} between gold brackets, "[🔥]" (a glyph in the
 * {@code frostbound:addons} font, {@code assets/frostbound/font/addons.json}).
 *
 * Lang key: {@code addon.frostbound.<id>} (the name, for the imbuement table).
 *
 * @param id    short name, stored on the gear ({@code frostbound:addons}) and used for the lang key
 * @param item  the item used up at the table to add it (it gives back its recipe remainder, e.g. the bottle)
 * @param icon  the character of its icon in the {@code frostbound:addons} font
 * @param color RGB the imbuement table glows in while it adds this one
 * @param fits  which gear takes it (flame extract: armour only)
 */
public record GearAddon(String id, Supplier<Item> item, String icon, int color, Predicate<ItemStack> fits) {
	public Text name() {
		return Text.translatable("addon." + Frostbound.MOD_ID + "." + id);
	}

	public boolean isItem(ItemStack stack) {
		return stack.isOf(item.get());
	}
}

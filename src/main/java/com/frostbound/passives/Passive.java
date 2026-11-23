package com.frostbound.passives;

import com.frostbound.Frostbound;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Supplier;

/**
 * A named bonus an item has, such as copper's Conductivity. Every item in {@link #items} gets it, and
 * its tooltip shows it right under the item's name, before the vanilla lines:
 *
 * <pre>
 * Copper Sword
 *
 * CONDUCTIVITY                          (name, bold in {@link #color})
 * 5% chance to deal 150% damage to ...  (description, light grey, wrapped)
 *
 * When in Main Hand: ...                (vanilla)
 * </pre>
 *
 * Lang keys: {@code passive.frostbound.<id>} (the name, written in capitals) and
 * {@code passive.frostbound.<id>.desc} (the description; {@link #descriptionArgs} fill its %s, so
 * numbers come from the config). The effect itself lives with the feature; this only describes it.
 *
 * @param id              short name, used for the lang keys
 * @param color           colour of the name (pick one that suits the material: copper is gold/orange)
 * @param items           item tag of everything that has this passive (a data pack can add more)
 * @param descriptionArgs values for the description's %s, read each time the tooltip is drawn
 */
public record Passive(String id, Formatting color, TagKey<Item> items, Supplier<Object[]> descriptionArgs) {
	public boolean appliesTo(ItemStack stack) {
		return stack.isIn(items);
	}

	/** "CONDUCTIVITY": the name, bold in its colour. */
	public Text title() {
		return Text.translatable("passive." + Frostbound.MOD_ID + "." + id).formatted(color, Formatting.BOLD);
	}

	/** The description, unwrapped and unstyled (the tooltip wraps it and colours it light grey). */
	public Text description() {
		return Text.translatable("passive." + Frostbound.MOD_ID + "." + id + ".desc", descriptionArgs.get());
	}

	/** 1.5 → "1.5", 150.0 → "150": for numbers in descriptions. */
	public static String number(double value) {
		return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(Math.round(value * 100) / 100.0);
	}
}

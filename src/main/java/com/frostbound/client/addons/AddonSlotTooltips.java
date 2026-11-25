package com.frostbound.client.addons;

import com.frostbound.addons.GearAddons;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The add-on slots in gear tooltips ({@link GearAddons#slotsLine}): "[🔥] [ ]", right under the stats (the
 * attribute lines and the durability line), before the advanced tooltip's item id. Registered after FrostStars,
 * so a frosty piece's durability line is already there and the slots come after it.
 */
public final class AddonSlotTooltips {
	private AddonSlotTooltips() {}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (GearAddons.hasSlots(stack) && !lines.isEmpty()) {
				addSlots(stack, lines, type.isAdvanced());
			}
		});
	}

	public static void addSlots(ItemStack stack, List<Text> lines, boolean advanced) {
		int at = lines.size();
		if (advanced) {
			String id = Registries.ITEM.getId(stack.getItem()).toString();
			for (int i = 0; i < lines.size(); i++) {
				if (lines.get(i).getString().equals(id)) {
					at = i;
					break;
				}
			}
		}
		lines.add(at, GearAddons.slotsLine(stack));
	}
}

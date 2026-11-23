package com.frostbound.client.imbuement;

import com.frostbound.imbuement.FrostTier;
import com.frostbound.imbuement.FrostTiers;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

/**
 * Cold, Icy and Frozen armour's tooltip title: the tier's name before the item's and one, two or three
 * stars after it, each in its tier's soft blue: "Frozen Iron Chestplate ★★★", stars pale to deep.
 */
public final class FrostStars {
	private FrostStars() {}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			FrostTier tier = FrostTiers.tier(stack);
			if (tier != FrostTier.NONE && !lines.isEmpty()) {
				lines.set(0, FrostTiers.title(tier, lines.get(0)));
			}
		});
	}
}

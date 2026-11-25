package com.frostbound.addons;

import com.frostbound.config.FrostboundConfig;
import com.frostbound.habanero.Habaneros;
import com.frostbound.imbuement.FrostTiers;
import com.frostbound.passives.Passive;
import com.frostbound.passives.Passives;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Formatting;

/**
 * Flame extract as an armour add-on: <b>insulation</b>. Each flame extract on the armour you wear is one share of
 * it, and {@code insulation.flameExtractsForFull} (4) make you fully insulated: one in each piece of a full set,
 * or two in each of two pieces (both slots).
 *
 * <ul>
 *   <li><b>Partly insulated</b>: powder snow freezes you that much slower (25% slower per extract; mixin/addons/InsulationMixin).</li>
 *   <li><b>Fully insulated</b>: you can't freeze at all (powder snow, a Frozen mob's Frostbite), and with Tough As
 *       Nails you're kept one step warmer (compat/TanTemperature), like wearing two pieces of warm clothing.</li>
 * </ul>
 */
public final class FlameExtract {
	public static final GearAddon ADDON = GearAddons.register(new GearAddon("flame_extract", () -> Habaneros.FLAME_EXTRACT,
			"", 0xF7861E, FrostTiers::isArmor));

	/** On armour with flame extract in it: how much this piece insulates and how much makes a full set. */
	public static final Passive PASSIVE = Passives.register(new Passive("flame_lined", Formatting.GOLD,
			stack -> GearAddons.count(stack, ADDON) > 0,
			stack -> new Object[] {Passive.number(100.0 * GearAddons.count(stack, ADDON) / forFull())}));

	private FlameExtract() {}

	/** Flame extracts worn for full insulation (at least 1). */
	public static int forFull() {
		return Math.max(1, FrostboundConfig.get().insulation.flameExtractsForFull);
	}

	/** Flame extracts in the armour {@code entity} wears. */
	public static int worn(LivingEntity entity) {
		return GearAddons.countWorn(entity, ADDON);
	}

	/** How insulated {@code entity} is, from 0 (none) to 1 (fully). */
	public static double insulation(LivingEntity entity) {
		return Math.min(1.0, worn(entity) / (double) forFull());
	}

	public static boolean isFullyInsulated(LivingEntity entity) {
		return worn(entity) >= forFull();
	}

	public static void init() {
		// Loading the class registers the add-on and its passive.
	}
}

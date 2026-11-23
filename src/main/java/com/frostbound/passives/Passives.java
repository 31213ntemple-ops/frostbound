package com.frostbound.passives;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Every {@link Passive} in the mod, in the order their tooltips list them. */
public final class Passives {
	private static final List<Passive> ALL = new ArrayList<>();

	private Passives() {}

	public static Passive register(Passive passive) {
		ALL.add(passive);
		return passive;
	}

	public static List<Passive> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static List<Passive> of(ItemStack stack) {
		List<Passive> found = new ArrayList<>();
		for (Passive passive : ALL) {
			if (passive.appliesTo(stack)) {
				found.add(passive);
			}
		}
		return found;
	}
}

package com.frostbound.addons;

import com.frostbound.Frostbound;
import com.frostbound.imbuement.FrostTiers;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Add-on slots: every piece of gear the imbuement table takes (armour and weapons, {@link FrostTiers#canImbue})
 * has {@link #SLOTS} of them, so you choose what each piece specialises in. The add-ons on a stack are kept in
 * {@link #COMPONENT} ({@code frostbound:addons}, a list of add-on ids in the order they were added).
 *
 * At the table: the gear in the centre and one add-on item in any outer slot (the rest empty) makes the gear
 * with that add-on in its next free slot.
 *
 * Tooltip (client/addons/AddonSlotTooltips), right under the stats: "[🔥] [ ]", filled slots with gold
 * brackets round the add-on's icon, empty ones with grey brackets ({@link #slotsLine}).
 */
public final class GearAddons {
	public static final int SLOTS = 2;

	public static final ComponentType<List<String>> COMPONENT = Registry.register(Registries.DATA_COMPONENT_TYPE, Frostbound.id("addons"),
			ComponentType.<List<String>>builder()
					.codec(Codec.STRING.listOf(0, SLOTS))
					.packetCodec(PacketCodecs.STRING.collect(PacketCodecs.toList(SLOTS)))
					.build());

	/** The font the add-on icons are drawn in (assets/frostbound/font/addons.json). */
	public static final net.minecraft.util.Identifier FONT = Frostbound.id("addons");
	/** In {@link #FONT}: a blank as wide as an icon, so an empty slot "[ ]" is as wide as a filled one. */
	public static final String EMPTY_ICON = "";

	private static final Map<String, GearAddon> ALL = new LinkedHashMap<>();

	private GearAddons() {}

	public static GearAddon register(GearAddon addon) {
		if (ALL.putIfAbsent(addon.id(), addon) != null) {
			throw new IllegalArgumentException("Duplicate gear add-on " + addon.id());
		}
		return addon;
	}

	public static List<GearAddon> all() {
		return List.copyOf(ALL.values());
	}

	@Nullable
	public static GearAddon byId(String id) {
		return ALL.get(id);
	}

	/** The add-on {@code stack} is the item of, or null. */
	@Nullable
	public static GearAddon byItem(ItemStack stack) {
		for (GearAddon addon : ALL.values()) {
			if (addon.isItem(stack)) {
				return addon;
			}
		}
		return null;
	}

	/** Gear with add-on slots: what the imbuement table's centre takes (armour and weapons). */
	public static boolean hasSlots(ItemStack stack) {
		return FrostTiers.canImbue(stack);
	}

	/** The add-ons in the stack's slots, in order (unknown ids, e.g. from a removed data pack, are skipped). */
	public static List<GearAddon> of(ItemStack stack) {
		List<String> ids = stack.getOrDefault(COMPONENT, Collections.emptyList());
		List<GearAddon> found = new ArrayList<>(ids.size());
		for (String id : ids) {
			GearAddon addon = ALL.get(id);
			if (addon != null) {
				found.add(addon);
			}
		}
		return found;
	}

	/** How many of the stack's slots hold {@code addon} (0, 1 or 2). */
	public static int count(ItemStack stack, GearAddon addon) {
		int n = 0;
		for (String id : stack.getOrDefault(COMPONENT, Collections.<String>emptyList())) {
			if (id.equals(addon.id())) {
				n++;
			}
		}
		return n;
	}

	/** How many slots across the armour {@code entity} wears hold {@code addon}. */
	public static int countWorn(LivingEntity entity, GearAddon addon) {
		int n = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
				n += count(entity.getEquippedStack(slot), addon);
			}
		}
		return n;
	}

	public static int freeSlots(ItemStack stack) {
		return hasSlots(stack) ? SLOTS - stack.getOrDefault(COMPONENT, Collections.<String>emptyList()).size() : 0;
	}

	public static boolean canAdd(ItemStack gear, GearAddon addon) {
		return !gear.isEmpty() && gear.getCount() == 1 && freeSlots(gear) > 0 && addon.fits().test(gear);
	}

	/** A copy of {@code gear} with {@code addon} in its next free slot, or {@link ItemStack#EMPTY} if it can't take it. */
	public static ItemStack withAdded(ItemStack gear, GearAddon addon) {
		if (!canAdd(gear, addon)) {
			return ItemStack.EMPTY;
		}
		ItemStack result = gear.copyWithCount(1);
		List<String> ids = new ArrayList<>(gear.getOrDefault(COMPONENT, Collections.<String>emptyList()));
		ids.add(addon.id());
		result.set(COMPONENT, List.copyOf(ids));
		return result;
	}

	/**
	 * What the imbuement table makes from {@code gear} in the centre and {@code ring} (the eight outer slots):
	 * the add-on of the one non-empty outer slot, if the others are empty and the gear takes it. Null otherwise.
	 */
	@Nullable
	public static Match match(ItemStack gear, List<ItemStack> ring) {
		int found = -1;
		for (int i = 0; i < ring.size(); i++) {
			if (!ring.get(i).isEmpty()) {
				if (found >= 0) {
					return null;
				}
				found = i;
			}
		}
		if (found < 0) {
			return null;
		}
		GearAddon addon = byItem(ring.get(found));
		return addon != null && canAdd(gear, addon) ? new Match(addon, found) : null;
	}

	/** @param ringIndex which of the eight outer slots (0–7, grid order skipping the centre) holds the add-on */
	public record Match(GearAddon addon, int ringIndex) {}

	/** "[🔥] [ ]": one bracketed slot per add-on slot, gold round an add-on's icon, grey round an empty one. */
	public static Text slotsLine(ItemStack stack) {
		List<GearAddon> addons = of(stack);
		MutableText line = Text.empty().setStyle(Style.EMPTY.withItalic(false));
		for (int i = 0; i < SLOTS; i++) {
			if (i > 0) {
				line.append(Text.literal(" "));
			}
			GearAddon addon = i < addons.size() ? addons.get(i) : null;
			Formatting brackets = addon != null ? Formatting.GOLD : Formatting.GRAY;
			line.append(Text.literal("[").formatted(brackets));
			line.append(Text.literal(addon != null ? addon.icon() : EMPTY_ICON).setStyle(Style.EMPTY.withFont(FONT).withColor(Formatting.WHITE)));
			line.append(Text.literal("]").formatted(brackets));
		}
		return line;
	}

	public static void init() {
		// Loading the class registers COMPONENT; add-ons register themselves (e.g. FlameExtract.ADDON).
	}
}

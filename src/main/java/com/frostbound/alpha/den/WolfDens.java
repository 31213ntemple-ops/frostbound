package com.frostbound.alpha.den;

import com.frostbound.Frostbound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

/**
 * Where Alphas live: the Wolf Den structure, an open ring of mossy stone brick in cold forests and
 * plains where a pack sleeps around its Alpha until something attacks one of them.
 *
 * Code registers the structure type and piece; the rest is data: worldgen/structure/wolf_den.json,
 * worldgen/structure_set/wolf_dens.json (how rare), the biome tag
 * {@code frostbound:has_structure/wolf_den} (where), and loot_table/chests/wolf_den.json.
 */
public final class WolfDens {
	public static final StructureType<WolfDenStructure> STRUCTURE_TYPE = Registry.register(Registries.STRUCTURE_TYPE,
			Frostbound.id("wolf_den"), () -> WolfDenStructure.CODEC);
	public static final StructurePieceType PIECE = Registry.register(Registries.STRUCTURE_PIECE, Frostbound.id("wolf_den"),
			(StructurePieceType) WolfDenPiece::new);
	public static final RegistryKey<Structure> WOLF_DEN = RegistryKey.of(RegistryKeys.STRUCTURE, Frostbound.id("wolf_den"));

	private WolfDens() {}

	public static void init() {
		// Static fields register everything.
	}
}

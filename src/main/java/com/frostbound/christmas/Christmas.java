package com.frostbound.christmas;

import com.frostbound.Frostbound;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.text.Text;

/**
 * Module: Christmas. Festive structures built from other mods' blocks:
 *
 *  - Christmas tree clearings: a DecorationX Grand Christmas Tree (four styles) on a patch of
 *    snow, with presents, lanterns and a snowman. The Supplementaries presents under the tree
 *    do something when right-clicked (see ChristmasPresents). Snow can't settle on the
 *    tree or presents (tag minecraft:snow_layer_cannot_survive_on).
 *
 * Everything is data in the built-in pack resourcepacks/christmas, which is only registered
 * when DecorationX: Christmas is installed. Without Supplementaries the trees still generate,
 * just without the gift presents. Templates are built by the dev tool ChristmasStructureBuilder
 * (src/gametest).
 */
public final class Christmas {
	public static final String DECORATIONX = "decorationx_christmas";
	public static final String SUPPLEMENTARIES = "supplementaries";

	private Christmas() {}

	public static void init() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded(DECORATIONX)) {
			Frostbound.LOGGER.info("Christmas module: DecorationX: Christmas isn't installed, so no Christmas structures");
			return;
		}
		ModContainer mod = loader.getModContainer(Frostbound.MOD_ID).orElseThrow();
		ResourceManagerHelper.registerBuiltinResourcePack(Frostbound.id("christmas"), mod,
				Text.literal("Frostbound: Christmas"), ResourcePackActivationType.ALWAYS_ENABLED);
		ChristmasPresents.init(); // harmless without Supplementaries: there are just no presents to open
		if (!loader.isModLoaded(SUPPLEMENTARIES)) {
			Frostbound.LOGGER.info("Christmas module: Supplementaries isn't installed, so trees come without presents to open");
		}
	}
}

package com.frostbound;

import com.frostbound.addons.GearAddons;
import com.frostbound.alpha.AlphaWolves;
import com.frostbound.compat.TanTemperature;
import com.frostbound.habanero.Habaneros;
import com.frostbound.campfire.Campfires;
import com.frostbound.christmas.Christmas;
import com.frostbound.config.FrostboundConfig;
import com.frostbound.copper.CopperGear;
import com.frostbound.environment.WinterEnvironment;
import com.frostbound.ice.IceChanges;
import com.frostbound.imbuement.Imbuement;
import com.frostbound.items.FrostboundItems;
import com.frostbound.mobs.FrozenMobs;
import com.frostbound.mushrooms.GiantMushrooms;
import com.frostbound.nothirst.NoThirst;
import com.frostbound.vessels.Vessels;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entrypoint. Frostbound is a collection of winter features, each a module
 * that can be switched off in config/frostbound.json:
 *
 *  - environment (com.frostbound.environment): the winter Overworld
 *  - ice          (com.frostbound.ice):         breakable ice, no crafted/blue ice, new textures
 *  - frozenMobs  (com.frostbound.mobs):        Frozen mob variants
 *  - christmas   (com.frostbound.christmas):   festive structures (with DecorationX: Christmas)
 *  - noThirst    (com.frostbound.nothirst):    Tough As Nails without its thirst (with TAN)
 *
 * Items (com.frostbound.items, e.g. ice balls; com.frostbound.vessels, the heart vessels;
 * com.frostbound.campfire, the fire starter and campfire base; com.frostbound.copper, copper gear
 * backported from 1.21.9 with the Conductivity passive; com.frostbound.imbuement, the imbuement table and
 * Cold/Icy/Frozen armour; com.frostbound.addons, the two add-on slots on gear and flame extract's insulation;
 * com.frostbound.habanero, habanero peppers, hot sauce and flame extract (cooked in Farmer's Delight's pot);
 * com.frostbound.alpha, the Alpha Wolf boss, its pack and its rewards; com.frostbound.mushrooms, giant mushrooms and their
 * cap/stem mining minigames) are always registered.
 */
public class Frostbound implements ModInitializer {
	public static final String MOD_ID = "frostbound";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		FrostboundConfig.Modules modules = FrostboundConfig.get().modules;

		FrostboundItems.init();
		Vessels.init();
		Campfires.init();
		CopperGear.init();
		Imbuement.init();
		GearAddons.init();
		Habaneros.init();
		TanTemperature.init();
		AlphaWolves.init();
		GiantMushrooms.init();

		if (modules.environment) {
			WinterEnvironment.init();
		}
		if (modules.ice) {
			IceChanges.init();
		}
		if (modules.frozenMobs) {
			FrozenMobs.init();
		}
		if (modules.christmas) {
			Christmas.init();
		}
		if (NoThirst.isActive()) {
			NoThirst.init();
		}
		LOGGER.info("Frostbound loaded (environment: {}, frozen mobs: {}, christmas: {}). Stay warm.",
				modules.environment, modules.frozenMobs, modules.christmas);
	}
}

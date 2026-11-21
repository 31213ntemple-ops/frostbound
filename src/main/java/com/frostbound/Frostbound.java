package com.frostbound;

import com.frostbound.config.FrostboundConfig;
import com.frostbound.environment.WinterEnvironment;
import com.frostbound.mobs.FrozenMobs;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entrypoint. Frostbound is a collection of winter features, each a module
 * that can be switched off in config/frostbound.json:
 *
 *  - environment (com.frostbound.environment): the winter Overworld
 *  - frozenMobs  (com.frostbound.mobs):        Frozen mob variants
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

		if (modules.environment) {
			WinterEnvironment.init();
		}
		if (modules.frozenMobs) {
			FrozenMobs.init();
		}
		LOGGER.info("Frostbound loaded (environment: {}, frozen mobs: {}). Stay warm.",
				modules.environment, modules.frozenMobs);
	}
}

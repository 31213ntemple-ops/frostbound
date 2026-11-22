package com.frostbound.config;

import com.frostbound.Frostbound;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * config/frostbound.json. Read once (lazily, on first use) and needs a restart to change.
 * Missing fields keep their defaults, and the file is rewritten so new options appear in it.
 */
public final class FrostboundConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static FrostboundConfig instance;

	/** Turn whole features of the mod on or off. */
	public Modules modules = new Modules();

	public static final class Modules {
		/** Winter Overworld: every biome becomes a cold, snowy variant. */
		public boolean environment = true;
		/** Some hostile mobs spawn Frozen (tougher, slower, icy). */
		public boolean frozenMobs = true;
		/** Christmas structures (needs DecorationX: Christmas; presents need Supplementaries). */
		public boolean christmas = true;
	}

	/** Options for the environment module. */
	public Environment environment = new Environment();

	public static final class Environment {
		/** New terrain only uses cold biomes (plus Sparse Forest); other biomes are swapped out. */
		public boolean coldBiomesOnly = true;
	}

	/** Options for the christmas module. */
	public ChristmasOptions christmas = new ChristmasOptions();

	public static final class ChristmasOptions {
		/** What happens when you open a present under a Christmas tree. */
		public Presents presents = new Presents();
	}

	public static final class Presents {
		/** Chance weights of the outcomes (relative to each other; 0 turns one off). */
		public int frozenMobsWeight = 30;
		public int giftWeight = 35;
		public int coalWeight = 20;
		public int explodeWeight = 15;
		/** Frozen mobs: how many, and which kinds (picked at random for each). */
		public int minMobs = 1;
		public int maxMobs = 3;
		public java.util.List<String> frozenMobs = new java.util.ArrayList<>(java.util.List.of(
				"minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:stray"));
		/** Explosion: fuse in ticks (20 = 1 second) and strength (creeper 3, TNT 4). */
		public int fuseTicks = 40;
		public float explosionPower = 2.0f;
	}

	public static synchronized FrostboundConfig get() {
		if (instance == null) {
			instance = load(FabricLoader.getInstance().getConfigDir().resolve(Frostbound.MOD_ID + ".json"));
		}
		return instance;
	}

	private static FrostboundConfig load(Path file) {
		FrostboundConfig config = new FrostboundConfig();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				FrostboundConfig read = GSON.fromJson(reader, FrostboundConfig.class);
				if (read != null) {
					config = read;
				}
				if (config.modules == null) {
					config.modules = new Modules();
				}
				if (config.environment == null) {
					config.environment = new Environment();
				}
				if (config.christmas == null) {
					config.christmas = new ChristmasOptions();
				}
				if (config.christmas.presents == null) {
					config.christmas.presents = new Presents();
				}
			} catch (IOException | JsonParseException e) {
				Frostbound.LOGGER.error("Could not read {}, using defaults", file, e);
				return config;
			}
		}
		try (Writer writer = Files.newBufferedWriter(file)) {
			GSON.toJson(config, writer);
		} catch (IOException e) {
			Frostbound.LOGGER.warn("Could not write {}", file, e);
		}
		return config;
	}
}

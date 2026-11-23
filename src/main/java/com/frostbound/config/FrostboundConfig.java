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
		/** Ice drops without Silk Touch, packed and blue ice can't be crafted, no blue ice worldgen, new textures. */
		public boolean ice = true;
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

	/** Ice balls (always available; these are balance numbers). */
	public IceBalls iceBalls = new IceBalls();

	public static final class IceBalls {
		/** Damage per hit (2 = one heart). */
		public float damage = 3.0f;
		/** Damage multiplier against frostbound:weak_to_ice mobs (blaze, magma cube, strider). */
		public float weakToIceMultiplier = 2.0f;
		/** Slowness on hit: how long and how strong (1 = Slowness I). 0 seconds turns it off. */
		public int slownessSeconds = 4;
		public int slownessLevel = 2;
		/** Radius of the frosted ice disc when one lands in water (0 turns it off). */
		public int freezeWaterRadius = 2;
		/** Ticks before you can throw another (20 = 1 second). */
		public int cooldownTicks = 10;
	}

	/** Conductivity, the copper tools' passive (always on; these are balance numbers). */
	public ConductivityOptions conductivity = new ConductivityOptions();

	public static final class ConductivityOptions {
		/** Chance per melee hit that it arcs (0.05 = 5%; 0 turns it off). */
		public float chance = 0.05f;
		/** Arc damage as a multiple of the hit's damage (1.5 = 150%). */
		public float damageMultiplier = 1.5f;
		/** How far from the struck mob the arc reaches, in blocks. */
		public double radius = 1.5;
	}

	/** Cold, Icy and Frozen armour from the imbuement table: the stat boosts of each tier. */
	public FrostTierOptions frostTiers = new FrostTierOptions();

	public static final class FrostTierOptions {
		/** Extra armour points per piece once Cold (kept at Icy and Frozen). */
		public double coldArmor = 0.5;
		/** Further armour points per piece, and armour toughness, once Frozen. */
		public double frozenArmor = 0.5;
		public double frozenToughness = 0.5;
		/** Extra durability as a share of the piece's normal durability: Cold adds this (0.25 = +25%), Icy adds icyDurability on top. */
		public double coldDurability = 0.25;
		public double icyDurability = 0.15;
	}

	/**
	 * The material bonuses of Icy and Frozen armour. Each is per piece worn and adds up: a full Icy set
	 * of leather gives 4 × the Icy number. Each pair is {icy, frozen}.
	 */
	public FrostBonusOptions frostBonuses = new FrostBonusOptions();

	public static final class FrostBonusOptions {
		/** Leather, Snowstride: movement speed on snow and ice (0.04 = +4%). */
		public Tiered snowstride = new Tiered(0.04, 0.08);
		/** Copper, Superconductor: chance for any melee hit to arc like Conductivity (0.015 = 1.5%). */
		public Tiered superconductor = new Tiered(0.015, 0.03);
		/** Chainmail, Frostmail: less damage from projectiles (0.05 = 5%). */
		public Tiered frostmail = new Tiered(0.05, 0.10);
		/** Iron, Cold Iron: more melee damage to undead (0.04 = 4%). */
		public Tiered coldIron = new Tiered(0.04, 0.08);
		/** Gold, Hearthgold: seconds to heal half a heart (one piece; two pieces heal twice as fast). */
		public Tiered hearthgold = new Tiered(20, 10);
		/** Diamond, Diamond Dust: share of melee damage taken that the attacker takes back (0.05 = 5%). */
		public Tiered diamondDust = new Tiered(0.05, 0.10);
		/** Netherite, Quenched: less fire and lava damage (0.06 = 6%). */
		public Tiered quenched = new Tiered(0.06, 0.12);
		/** Turtle shell, Tidefrost: extra seconds of the helmet's Water Breathing. */
		public Tiered tidefrost = new Tiered(10, 20);

		/** Puts back the default of any pair missing from the file. */
		void fillMissing() {
			FrostBonusOptions defaults = new FrostBonusOptions();
			for (java.lang.reflect.Field field : FrostBonusOptions.class.getFields()) {
				try {
					if (field.get(this) == null) {
						field.set(this, field.get(defaults));
					}
				} catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
			}
		}
	}

	/** A number with one value for Icy armour and a stronger one for Frozen. */
	public static final class Tiered {
		public double icy;
		public double frozen;

		public Tiered() {}

		public Tiered(double icy, double frozen) {
			this.icy = icy;
			this.frozen = frozen;
		}
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
				if (config.iceBalls == null) {
					config.iceBalls = new IceBalls();
				}
				if (config.conductivity == null) {
					config.conductivity = new ConductivityOptions();
				}
				if (config.frostTiers == null) {
					config.frostTiers = new FrostTierOptions();
				}
				if (config.frostBonuses == null) {
					config.frostBonuses = new FrostBonusOptions();
				}
				config.frostBonuses.fillMissing();
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

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
		/**
		 * With Tough As Nails: turns its thirst off and removes the canteens, water bottles, juices,
		 * water purifier, rain collector and Water Cleansing enchantment. Temperature is untouched.
		 */
		public boolean noThirst = true;
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

	/** Cold, Icy and Frozen armour and weapons from the imbuement table: the stat boosts of each tier. */
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
		/** Extra attack damage on a weapon once Cold, and further once Frozen. */
		public double coldAttackDamage = 0.5;
		public double frozenAttackDamage = 0.5;
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
		/** Weapons, Frostbite: seconds of Slowness a melee hit gives the target. */
		public Tiered frostbite = new Tiered(2, 4);

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

	/** The Alpha Wolf boss and its pack (always registered; these are balance numbers). */
	public AlphaWolfOptions alphaWolf = new AlphaWolfOptions();

	public static final class AlphaWolfOptions {
		/** The Alpha's health (90 = 45 hearts), bite damage (2 = one heart, before armour) and size. */
		public double health = 90.0;
		public double damage = 5.0;
		public double scale = 1.5;
		/** Each pack wolf's health and bite damage. */
		public double packWolfHealth = 12.0;
		public double packWolfDamage = 3.0;
		/** Wolves in a new pack, counting the Alpha, and the most the Alpha can ever lead at once. */
		public int initialPackSize = 7;
		public int maxPackSize = 9;
		/** How many pack wolves may lunge at once; the rest circle and wait their turn (the Alpha lunges on its own). */
		public int simultaneousAttackers = 3;
		/** Ticks (20 = 1 s) a wolf crouches and growls before it lunges, so the bite can be seen coming. */
		public int lungeWindupTicks = 14;
		/** Below this share of its health (0.5 = half) the Alpha is Bloodied: it roars, speeds up and starts to Charge. */
		public double bloodiedBelow = 0.5;
		/** Movement speed bonus while Bloodied (0.15 = 15% faster). */
		public double bloodiedSpeedBonus = 0.15;
		/** Seconds between the Alpha's big moves (Pounce, Charge), before and after it's Bloodied. */
		public double moveIntervalSeconds = 4.5;
		public double bloodiedMoveIntervalSeconds = 3.0;
		public CallOfThePack callOfThePack = new CallOfThePack();
		public Pounce pounce = new Pounce();
		public Charge charge = new Charge();
		public DrainingBite drainingBite = new DrainingBite();
		public Enrage enrage = new Enrage();
		public Den den = new Den();
		public Hunting hunting = new Hunting();
		public Pincer pincer = new Pincer();
	}

	/** How the pack hunts as one: surrounding you first, then biting from where you aren't looking. */
	public static final class Hunting {
		/** Seconds a pack spends spreading out round new prey before the first bite. */
		public double encircleSeconds = 1.5;
		/** Pack wolves circle this far out (blocks), evenly spaced; the Alpha paces outside the ring. */
		public double ringRadius = 5.0;
		/**
		 * A wolf running in while a player looks straight at it (within this many degrees; 0 = off) breaks
		 * off and slinks back, up to gazeHoldsOff times in a row; after that it calls the bluff.
		 */
		public double gazeDegrees = 15.0;
		public int gazeHoldsOff = 1;
	}

	public static final class Pincer {
		/** The Alpha barks an order and two wolves on opposite sides of you leap at once. Seconds into a fight before the first, and between them. */
		public double firstSeconds = 8.0;
		public double intervalSeconds = 15.0;
		/** Ticks the two crouch before the first leaps (pale lines on the ground show where each will land); each line stops following you lockTicks before its wolf leaps. */
		public int windupTicks = 26;
		public int lockTicks = 8;
		/** The second wolf leaps this many ticks after the first (more than 10, so both bites count). */
		public int staggerTicks = 12;
		/** Ticks each wolf stands winded after landing (hit them). */
		public int recoverTicks = 16;
		/** The two must be at least this many degrees apart round you. */
		public double minAngleDegrees = 120.0;
	}

	public static final class CallOfThePack {
		/** Seconds into a fight before the first howl, and between howls. */
		public double firstCallSeconds = 12.0;
		public double intervalSeconds = 30.0;
		/** Seconds the Alpha sits and howls before the pack answers. */
		public double windupSeconds = 2.0;
		/** Damage the Alpha has to take during the howl to cut it off (no wolves, no healing), and the wait before it tries again. */
		public double interruptDamage = 8.0;
		public double interruptedRetrySeconds = 10.0;
		/** Wolves each howl summons (never past maxPackSize), and the most it can summon in one fight. */
		public int wolvesPerCall = 2;
		public int maxReinforcements = 12;
		/** Health each living wolf heals for each wolf alive (0.5 = a quarter heart; 3 alive heal 1.5 each). */
		public double healPerWolf = 0.5;
	}

	public static final class Pounce {
		/** The Alpha crouches for windupTicks with the landing spot marked, leaps, and slams down: damage within radius blocks. */
		public int windupTicks = 24;
		public double damage = 6.0;
		public double radius = 2.5;
		/** Ticks it stands winded after landing (a window to hit it). */
		public int recoverTicks = 20;
	}

	public static final class Charge {
		/** Only once Bloodied. The Alpha paws the ground for windupTicks (it stops turning for the last lockTicks), then charges. */
		public int windupTicks = 36;
		public int lockTicks = 10;
		/** Blocks per tick, and how far it can go. */
		public double speed = 0.85;
		public double maxDistance = 18.0;
		public double damage = 8.0;
		/** Running into a wall or pillar stuns it for stunSeconds, taking stunnedDamageMultiplier times damage meanwhile. */
		public double stunSeconds = 3.0;
		public double stunnedDamageMultiplier = 1.5;
	}

	public static final class DrainingBite {
		/** Max health a bite takes away (in hearts) and for how long. A bite never stacks or extends it. */
		public double hearts = 2.0;
		public double seconds = 10.0;
	}

	public static final class Enrage {
		/** When a pack wolf dies, the rest get Strength (1 = Strength I) for this long. Refreshed, never stacked. */
		public double seconds = 1.0;
		public int strengthLevel = 1;
	}

	public static final class Den {
		/** Seconds without a fight (you got more than 48 blocks away, or out of sight) before a den pack walks home and lies back down. */
		public double giveUpSeconds = 10.0;
		/** Health a sleeping wolf (the Alpha too) gets back each second. */
		public double sleepHealPerSecond = 1.0;
	}

	/** Alpha Hide armour's Pack Instinct set bonus. */
	public PackInstinctOptions packInstinct = new PackInstinctOptions();

	public static final class PackInstinctOptions {
		/** Triggers when health drops below this (8 = 4 hearts) with 2 or more pieces worn. */
		public double belowHealth = 8.0;
		/** 2 pieces: Regeneration (2 = Regeneration II) for this long. 4 pieces: Strength as well. */
		public int regenerationLevel = 2;
		public double regenerationSeconds = 5.0;
		public int strengthLevel = 1;
		public double strengthSeconds = 5.0;
		/** Seconds before it can trigger again, however often health crosses the line. */
		public double cooldownSeconds = 60.0;
	}

	/** The Life Steal enchantment (crafted from wolf teeth). */
	public LifeStealOptions lifeSteal = new LifeStealOptions();

	public static final class LifeStealOptions {
		/** Share of the health a hit takes from its target that the attacker heals, per level (0.1 = 10%). */
		public double healPerLevel = 0.10;
	}

	/** The Mark of the Pack necklace. */
	public MarkOfThePackOptions markOfThePack = new MarkOfThePackOptions();

	public static final class MarkOfThePackOptions {
		public int wolves = 2;
		public double lifetimeSeconds = 30.0;
		public double cooldownSeconds = 60.0;
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
				if (config.alphaWolf == null) {
					config.alphaWolf = new AlphaWolfOptions();
				}
				if (config.alphaWolf.callOfThePack == null) {
					config.alphaWolf.callOfThePack = new CallOfThePack();
				}
				if (config.alphaWolf.drainingBite == null) {
					config.alphaWolf.drainingBite = new DrainingBite();
				}
				if (config.alphaWolf.enrage == null) {
					config.alphaWolf.enrage = new Enrage();
				}
				if (config.alphaWolf.pounce == null) {
					config.alphaWolf.pounce = new Pounce();
				}
				if (config.alphaWolf.charge == null) {
					config.alphaWolf.charge = new Charge();
				}
				if (config.alphaWolf.den == null) {
					config.alphaWolf.den = new Den();
				}
				if (config.alphaWolf.hunting == null) {
					config.alphaWolf.hunting = new Hunting();
				}
				if (config.alphaWolf.pincer == null) {
					config.alphaWolf.pincer = new Pincer();
				}
				if (config.packInstinct == null) {
					config.packInstinct = new PackInstinctOptions();
				}
				if (config.lifeSteal == null) {
					config.lifeSteal = new LifeStealOptions();
				}
				if (config.markOfThePack == null) {
					config.markOfThePack = new MarkOfThePackOptions();
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

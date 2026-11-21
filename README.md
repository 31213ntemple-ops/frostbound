# Frostbound (Fabric 1.21.1)

A collection of winter features, built as one mod split into **modules**. Each module can be switched off in `config/frostbound.json` (restart needed):

```json
{ "modules": { "environment": true, "frozenMobs": true } }
```

| Module | What it does |
|---|---|
| `environment` | Every Overworld biome becomes a winter variant with its own temperature, snow, ice and colours |
| `frozenMobs` | Some hostile mobs spawn Frozen: tougher, slower, icy |

## Winter Environment

Every Overworld biome keeps its ID and terrain but becomes a cold, snowy version of itself. The Nether, the End and cave biomes are left alone.

| Band | Biomes | Winter °C | Water |
|---|---|---|---|
| Arctic | snowy plains/taiga/slopes/beach, ice spikes, grove, frozen & jagged peaks, frozen river/oceans | −20 to −30 | frozen solid (oceans: ice floes) |
| Boreal | taigas, windswept hills/forest, stony peaks & shore, cold oceans | −13 to −18 | frozen solid (oceans: ice floes) |
| Temperate | plains, forests, birch, dark forest, meadow, cherry grove, swamp, river, beach, ocean | −7 to −12 | frozen solid (oceans: ice floes) |
| Mild | jungles, savannas, mangrove swamp, mushroom fields, lukewarm oceans | −3 to −6 | frozen solid (oceans: ice floes) |
| Dry | desert, badlands, warm ocean | −1 to −2 | frozen solid (warm ocean: stays open) |

- **Snow everywhere:** it snows instead of raining, snow builds up, and grass turns snowy. Each band has its own colour palette (grass, foliage, water, sky, fog).
- **Snow on plants:** snow settles *into* short grass, ferns, flowers, dead bushes, saplings, mushrooms, sweet berry bushes, pink petals and the bottom of tall plants, instead of leaving holes around them. These plants have a `snowy` block state, and the snowy version draws a thin snow layer around the plant. Breaking it gives the plant as normal. Modded plants that extend these vanilla plant types get it too.
- **Water:** "ice floes" uses vanilla's frozen-ocean pattern, so about half is open water (it rains over the open patches). The warm ocean never freezes, though snow still falls on it.
- **Wildlife:** snow foxes, white rabbits and cold frogs everywhere. Strays join skeletons outside the arctic. Mushroom fields stay free of monsters.
- **Modded biomes** get a winter version automatically, based on how warm they originally were.
- **Existing worlds:** snowfall, freezing and colours apply straight away. Snow builds up on old chunks during snowfall.

### Temperature mods (Tough As Nails)
- Every Overworld biome is in `toughasnails:cold_biomes`. Tough As Nails checks cold before neutral, warm and hot, so this overrides its own defaults.
- Known limitation for now: biomes Tough As Nails already lists as `icy_biomes` (the snowy ones) stay icy, because it checks icy first.
- Every biome's vanilla temperature is `0.15 + °C / 40`, so mods that read vanilla temperature get the same winter values.
- For code: `FrostboundClimate.getCelsius(biome)` and `FrostboundClimate.isArctic(biome)`.
- In Tough As Nails, being *cold* does no harm; only *icy* freezes you. Night, snowfall, being wet and high altitude each push you one step colder. Caves (below Y=50) always count as neutral.

## Frozen Mobs

| | Effect |
|---|---|
| Speed | −35% movement |
| Health | +75% max health (they spawn at full health) |
| Damage | ×1.5 on everything they deal: melee, arrows, creeper blasts |
| Frostbite | Their hits give you Slowness I (3s) and frost your screen edges |
| Weakness | Take ×2 fire/lava damage |
| Heft | +30% knockback resistance |
| Drops | 1–2 ice, 0–3 snowballs, rare packed ice (Looting boosts all of these) |
| Visuals | Shiver (the powder-snow shake), pale ice-blue tint, drifting snowflakes, shatter on death |

Spawn chance: **8%** normally, **30%** in arctic biomes (≤ −20 °C). Mobs that can roll Frozen: zombie, zombie villager, husk, drowned, skeleton, spider, cave spider, creeper, slime, witch, pillager, vindicator.

## Development workflow

You need **JDK 21** (Temurin 21 from adoptium.net). If Gradle picks up the wrong Java on a machine, point it at JDK 21 in that machine's **own** `~/.gradle/gradle.properties` (not the project's, which is shared through git):

```
org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-21.0.13.10002-hotspot
```

The Windows PC already has this set up.

**Setting up on the Mac (once):**
1. Install Temurin 21: `brew install --cask temurin@21` (or the .pkg from adoptium.net).
2. `git clone <repo url>` and open the folder in IntelliJ. It imports the Gradle project and makes the run configs.
3. In IntelliJ: *Settings → Build Tools → Gradle → Gradle JVM* → pick 21.
4. Check it works: `./gradlew runGametest`.

**Switching machines:** `git pull` before you start, then commit and `git push` when you stop.

| What | Terminal (Windows) | IntelliJ run config |
|---|---|---|
| Run the automated tests (~20s, headless) | `.\gradlew.bat runGametest` | **Game Test** |
| Play-test in a dev client | `.\gradlew.bat runClient` | **Minecraft Client** |
| Dev server | `.\gradlew.bat runServer` | **Minecraft Server** |
| Build the shippable jar | `.\gradlew.bat build` | Gradle tool window → build |

On the Mac, use `./gradlew` instead of `.\gradlew.bat`.

The jar ends up at `build/libs/frostbound-1.0.0.jar` (ignore the `-sources` jar). Drop it into the modpack's `mods/` folder next to Fabric API. Tests are never included in it.

**Typical loop:** change code → `runGametest` → if green, `runClient` and look at it in-game.

**Faster iteration in IntelliJ:** start **Minecraft Client** with *Debug* (the bug icon) instead of Run. When you edit the inside of a method, press **Ctrl+Shift+F9** (Recompile) to hot-swap it into the running game. Adding methods/fields or changing a mixin's annotations still needs a restart. For data files (tags, loot tables, functions), use *Build → Build Project* and then `/reload` in-game. Biome changes (temperature, colours) need a restart.

If you edit `build.gradle`, click the Gradle **Reload** (elephant) icon in IntelliJ.

### Automated tests

They live in `src/gametest/java/com/frostbound/test/`:
- `WinterEnvironmentTests`: every vanilla biome has a winter profile; temperatures and snowfall match the table; water freezes except in open-water biomes; the Nether and caves are untouched; the temperature-mod tags are filled; colours and strays are applied.
- `SnowyPlantTests`: which plants get the snowy state; snowfall and world generation make plants snowy (including tall plants' bottom half) and turn the grass under them snowy.
- `FrozenMobTests`: Frozen stats, damage, frostbite, fire weakness, NBT saving, ice drops, spawn tag.

A failing check fails the build with a clear message. To add a test, copy a method, rename it and change the body. Results are also written to `build/gametest/junit.xml`. The test server's own config is at `build/gametest/config/frostbound.json`.

## Testing commands

Environment (make a **new world** to see generated snow and ice):
```
/locate biome minecraft:desert           # then /tp to the coordinates
/locate biome minecraft:ocean            # ice floes
/locate biome minecraft:warm_ocean       # open water with snowfall
/weather rain                            # = snowfall everywhere
/time set night
/setblock ~ ~ ~ minecraft:poppy[snowy=true]   # stand on grass; a snowy flower
```

Frozen Mobs:
```
/function frostbound:test
```
This spawns a lineup with a frozen row in front and a normal row behind, so you can compare them side by side.

Single summons:
```
/summon minecraft:zombie ~ ~ ~ {Frostbound:1b}
/summon minecraft:skeleton ~ ~ ~ {Frostbound:1b,HandItems:[{id:"minecraft:bow",count:1},{}]}
/summon minecraft:creeper ~ ~ ~ {Frostbound:1b}
/summon minecraft:slime ~ ~ ~ {Frostbound:1b,Size:2}
```
`{Frostbound:1b}` works on any mob, even ones outside the list (try a cow).

Check the stats:
```
/attribute @e[type=zombie,limit=1,sort=nearest] minecraft:generic.max_health get
/data get entity @e[type=zombie,limit=1,sort=nearest] Frostbound
```

Test natural spawning:
```
/gamerule frostboundSpawnChance 100       # every eligible mob spawns frozen (use spawn eggs)
/gamerule frostboundColdBiomeChance 100
/gamerule frostboundSpawnChance 8         # back to default
```

## Tweaking

- **Biome temperatures, water, bands:** one line per biome in `environment/WinterClimate.java` (`TABLE`). Colour palettes are on the `Zone` enum in the same file.
- **Open water biomes:** the tag `frostbound:open_water` (keep it in step with `WaterMode.OPEN` in the table; a test checks this).
- **Frozen mob spawn chances:** use the game rules above. They're saved per world.
- **Which mobs can be Frozen:** `data/frostbound/tags/entity_type/can_be_frozen.json`. A modpack datapack can add mobs from other mods to `frostbound:can_be_frozen`.
- **Frozen mob drops:** `data/frostbound/loot_table/entities/frozen_mob.json`.
- **Frozen mob balance:** `mobs/FrostStats.java`. **Tint colour:** `ICE_TINT` in `LivingEntityRendererMixin.java`.

## How it's put together

```
com.frostbound
├── Frostbound              entrypoint; starts the enabled modules
├── config/FrostboundConfig config/frostbound.json
├── environment/            winter environment module
│   ├── WinterClimate             the climate table (single source of truth)
│   ├── WinterBiomeModifications  applies it with Fabric's biome modification API
│   ├── WinterEnvironment         module entry; registers the built-in data pack
│   ├── SnowyPlants               which plants can hold snow, and the snowy property
│   └── FrostboundClimate         public API (°C per biome)
├── client/                 client entrypoint; SnowyPlantModel draws snow on snowy plants
├── mobs/                   frozen mobs module (FrozenMobs, FrozenMob, FrostStats)
└── mixin/
    ├── environment/              BiomeMixin (open water, snow into plants), BlockMixin (adds
    │                             the snowy property), FreezeTopLayerFeature/ServerWorld (place
    │                             snowy plants instead of snow), SnowyBlockMixin (snowy grass)
    └── mobs/                     frozen flag, combat, loot, visuals
```

- The `snowy` plant property is always added (even with the environment module off), so client and server always agree on block states. With the module off, snow simply stops settling into plants.

- The environment module's data (biome tags and the Tough As Nails tag) is a built-in data pack at `resources/resourcepacks/winter_environment`. It's only registered while the module is on.
- `src/gametest` is a dev-only test mod (`frostbound-gametest`) and never ships.

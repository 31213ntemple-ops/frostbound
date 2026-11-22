# Frostbound (Fabric 1.21.1)

A collection of winter features, built as one mod split into **modules**. Each module can be switched off in `config/frostbound.json` (restart needed):

```json
{
  "modules": { "environment": true, "frozenMobs": true, "christmas": true },
  "environment": { "coldBiomesOnly": true },
  "christmas": { "presents": { "frozenMobsWeight": 30, "giftWeight": 35, "coalWeight": 20, "explodeWeight": 15, "...": "see Christmas" } }
}
```

| Module | What it does |
|---|---|
| `environment` | Only cold biomes generate, and every Overworld biome becomes a winter variant with its own temperature, snow, ice and colours |
| `frozenMobs` | Some hostile mobs spawn Frozen: tougher, slower, icy |
| `christmas` | Christmas tree clearings with surprise presents (needs DecorationX: Christmas; presents need Supplementaries) |

## Winter Environment

Every Overworld biome keeps its ID and terrain but becomes a cold, snowy version of itself. The Nether, the End and cave biomes are left alone.

### Cold biomes only

New terrain only uses cold biomes: snowy plains, ice spikes, the taigas, groves, peaks and slopes, windswept hills, frozen rivers, cold and frozen oceans, plus Frostbound's own **Sparse Forest**. Every other vanilla biome is swapped for a cold one in the same spot of vanilla's climate grid, so the terrain (vanilla or Tectonic) stays the same; only the biome on it changes. Turn it off with `"coldBiomesOnly": false`.

| Removed | Generates instead |
|---|---|
| plains, sunflower plains, flower forest, savanna, savanna plateau, meadow | Sparse Forest |
| forest, birch forest, sparse jungle | taiga |
| old growth birch forest | old growth pine taiga |
| dark forest, jungle, bamboo jungle | old growth spruce taiga |
| swamp, mangrove swamp, wooded badlands | snowy taiga |
| desert, badlands, mushroom fields | snowy plains |
| eroded badlands | ice spikes |
| windswept savanna / cherry grove | windswept hills / grove |
| beach, river | snowy beach, frozen river |
| ocean, lukewarm, warm (and deep) | cold ocean / deep cold ocean |

**Sparse Forest** (`frostbound:sparse_forest`, −12 °C): open snowy land with occasional small oaks, flowers, pumpkins and grass. Horses, donkeys and rabbits spawn here along with the usual farm animals. It gets mineshafts, pillager outposts, ruined portals, trial chambers and strongholds like plains did; no villages for now.

Roughly what a new world looks like (sampled over 40 km, vanilla terrain): taiga 19%, Sparse Forest 18%, old growth taigas 8%, snowy plains 5%, snowy taiga 3%, ice spikes and mountains about 2%. Most of the rest is ocean and frozen rivers.

- Only **newly generated chunks** change. In an existing world, explored chunks keep their biomes, so there's a visible seam at the old border.
- Mods that bring their own Overworld biome list (e.g. Terralith) aren't affected. Tectonic only reshapes terrain, so it works.
- Structures that only spawn in removed biomes are gone from new terrain: plains/desert/savanna villages, desert pyramids, jungle temples, witch huts, woodland mansions, warm ocean ruins. Taiga and snowy villages still spawn. The same goes for anything only found in those biomes, such as bamboo, cocoa, melons, cactus, pandas, parrots and camels.

| Band | Biomes | Winter °C | Water |
|---|---|---|---|
| Arctic | snowy plains/taiga/slopes/beach, ice spikes, grove, frozen & jagged peaks, frozen river/oceans | −20 to −30 | frozen solid (oceans: ice floes) |
| Boreal | taigas, windswept hills/forest, stony peaks & shore, cold oceans | −13 to −18 | frozen solid (oceans: ice floes) |
| Temperate | plains, forests, birch, dark forest, meadow, cherry grove, swamp, river, beach, ocean | −7 to −12 | frozen solid (oceans: ice floes) |
| Mild | jungles, savannas, mangrove swamp, mushroom fields, lukewarm oceans | −3 to −6 | frozen solid (oceans: ice floes) |
| Dry | desert, badlands, warm ocean | −1 to −2 | frozen solid (warm ocean: stays open) |

- **Snow everywhere:** it snows instead of raining, snow builds up, and grass turns snowy. Each band has its own colour palette (grass, foliage, water, sky, fog).
- **Snow on plants:** snow settles *into* short grass, ferns, flowers, dead bushes, saplings, mushrooms, sweet berry bushes, pink petals and the bottom of tall plants, instead of leaving holes around them. These plants have a `snowy` block state, and the snowy version draws a thin snow layer around the plant. Breaking it gives the plant as normal. Modded plants that extend these vanilla plant types get it too. **With Snow! Real Magic installed this turns itself off** and Snow! Real Magic handles snow in plants instead (plants then get no `snowy` state from Frostbound).
- **Water:** "ice floes" uses vanilla's frozen-ocean pattern, so about half is open water (it rains over the open patches). The warm ocean never freezes, though snow still falls on it (only relevant in old chunks or with `coldBiomesOnly` off).
- **Wildlife:** snow foxes, white rabbits and cold frogs everywhere. Strays join skeletons outside the arctic. Mushroom fields stay free of monsters.
- **Modded biomes** get a winter version automatically, based on how warm they originally were.
- **Existing worlds:** snowfall, freezing and colours apply straight away. Snow builds up on old chunks during snowfall.

### Temperature mods (Tough As Nails)
- Every Overworld biome is in `toughasnails:cold_biomes`. Tough As Nails checks cold before neutral, warm and hot, so this overrides its own defaults.
- Known limitation for now: biomes Tough As Nails already lists as `icy_biomes` (the snowy ones) stay icy, because it checks icy first.
- Every biome's vanilla temperature is `0.15 + °C / 40`, so mods that read vanilla temperature get the same winter values.
- For code: `FrostboundClimate.getCelsius(biome)` and `FrostboundClimate.isArctic(biome)`.
- In Tough As Nails, being *cold* does no harm; only *icy* freezes you. Night, snowfall, being wet and high altitude each push you one step colder. Caves (below Y=50) always count as neutral.

## Christmas

Built from other mods' blocks, so it only loads when they're installed (all data is in the built-in pack `resourcepacks/christmas`):

| Needs | For |
|---|---|
| **DecorationX: Christmas** | the module at all (without it nothing is added) |
| **Supplementaries** | the presents you can open (without it the trees come without them) |

**Christmas tree clearings** (`frostbound:christmas_tree`): a DecorationX Grand Christmas Tree (classic, multicolour, silver & blue or white frost, picked at random) on a round patch of snow, with five Supplementaries presents "from Santa" around it, three DecorationX present piles, two Christmas lanterns and a snowman. They're rare: at most one per 36×36 chunks (about 580 blocks), and only in Sparse Forest, taigas, snowy plains and groves. Snow never settles on the tree, the presents or the lanterns (they're in `minecraft:snow_layer_cannot_survive_on`), only on the floor around them.

**Presents:** right-click one of the tree's Supplementaries presents once and one of these happens:

| Outcome | Chance | What happens |
|---|---|---|
| Frozen mobs | 30% | the present bursts and 1–3 Frozen mobs (zombie, skeleton, spider or stray) climb out |
| Gift | 35% | one decent item pops out (see below) |
| Coal | 20% | one lump of coal |
| Explosion | 15% | it starts hissing and smoking, then explodes 2 seconds later (strength 2, a bit weaker than a creeper) |

A message above the hotbar says which it was. Presents players wrap themselves open normally.

All of it is set in `config/frostbound.json` under `christmas.presents` (restart to apply): `frozenMobsWeight`, `giftWeight`, `coalWeight`, `explodeWeight` (relative chances; 0 turns one off), `minMobs`/`maxMobs`, `frozenMobs` (list of mob IDs), `fuseTicks` (20 = 1 second) and `explosionPower` (creeper 3, TNT 4).

**Gifts** come from `data/frostbound/loot_table/chests/christmas_present.json` (override it with a data pack to change them): iron, gold, emeralds, diamonds, lapis, golden apples, ender pearls, experience bottles, an enchanted book, pickaxe or bow, a saddle, name tag or diamond horse armour, fireworks, and things a cold-only world lacks (sugar cane, melon seeds, cocoa beans). Rarely a snow globe, music box or music disc.

Try it: `/locate structure frostbound:christmas_tree`, or `/place structure frostbound:christmas_tree` where you stand.

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

**Setting up on the Mac (once):** *(done on the MacBook: repo at `~/Developer/Frostbound`)*
1. Install Temurin 21. Without Homebrew, extract the macOS aarch64 `.tar.gz` from adoptium.net into `~/Library/Java/JavaVirtualMachines/` (no admin password needed). With Homebrew: `brew install --cask temurin@21`.
2. Pin it: put `org.gradle.java.home=<output of /usr/libexec/java_home -v 21>` in `~/.gradle/gradle.properties`, and `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"` in `~/.zshrc`.
3. `git clone <repo url> ~/Developer/Frostbound` and open the folder in IntelliJ IDEA (the free edition is enough). It imports the Gradle project and makes the run configs.
4. In IntelliJ: *Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM* → pick 21 (Temurin). Also set *File → Project Structure → SDK* to 21.
5. Check it works: `./gradlew runGametest`.

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

**Faster iteration in IntelliJ:** start **Minecraft Client** with *Debug* (the bug icon) instead of Run. When you edit the inside of a method, press **Ctrl+Shift+F9** (Mac: **⌘⇧F9**, Recompile) to hot-swap it into the running game. Adding methods/fields or changing a mixin's annotations still needs a restart. For data files (tags, loot tables, functions), use *Build → Build Project* and then `/reload` in-game. Biome changes (temperature, colours) need a restart.

If you edit `build.gradle`, click the Gradle **Reload** (elephant) icon in IntelliJ.

### Automated tests

They live in `src/gametest/java/com/frostbound/test/`:
- `WinterEnvironmentTests`: every vanilla biome has a winter profile; temperatures and snowfall match the table; water freezes except in open-water biomes; the Nether and caves are untouched; the temperature-mod tags are filled; colours and strays are applied.
- `WinterEnvironmentTests` also checks cold biomes only: removed biomes never generate (it samples real Overworld noise and logs the biome share), every replacement is a cold biome, and Sparse Forest has its oaks, horses and donkeys.
- `SnowyPlantTests`: which plants get the snowy state; snowfall and world generation make plants snowy (including tall plants' bottom half) and turn the grass under them snowy.
- `ChristmasTests`: the structure only loads with DecorationX; every tree style has the tree, the snow floor and five gift presents; the templates place (directly and through `/place structure`, landing on the surface); gifts are one decent stack (never coal); snow can't settle on the tree, presents or lanterns but still lands on the floor; each present outcome works (gift, one coal, 1–3 Frozen mobs, explosion after the fuse) and the chances match the config. A chest with the present loot table stands in for the Supplementaries present, which can't run in dev.
- `FrozenMobTests`: Frozen stats, damage, frostbite, fire weakness, NBT saving, ice drops, spawn tag.

A failing check fails the build with a clear message. To add a test, copy a method, rename it and change the body. The snow-in-plants tests skip themselves when Snow! Real Magic is installed. Results are also written to `build/gametest/junit.xml`. The test server's own config is at `build/gametest/config/frostbound.json`.

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

- **Christmas tree layout:** edit `ChristmasStructureBuilder` in `src/gametest` and run `./gradlew runGametest -PbuildStructures`. It rebuilds the four `.nbt` templates in `resourcepacks/christmas/data/frostbound/structure/christmas_tree/` (run the tests again afterwards so they see the new files). Or design one by hand in the dev client with a structure block, and save it over one of those files. **How rare:** `spacing`/`separation` in `worldgen/structure_set/christmas_trees.json`. **Where:** the tag `frostbound:has_structure/christmas_tree`. **Gifts:** `loot_table/chests/christmas_present.json`. **Present outcomes:** `christmas.presents` in the config, code in `christmas/ChristmasPresents.java`.

- **Which biomes generate:** `REPLACEMENTS` in `environment/WinterBiomePool.java` (removed biome → what generates instead). **Sparse Forest:** `data/frostbound/worldgen/biome/sparse_forest.json` (spawns, features) and `worldgen/placed_feature/sparse_forest_trees.json` (how many oaks).
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
│   ├── WinterBiomePool           which biomes new terrain uses (cold only + Sparse Forest)
│   ├── WinterBiomeModifications  applies it with Fabric's biome modification API
│   ├── WinterEnvironment         module entry; registers the built-in data pack
│   ├── SnowyPlants               which plants can hold snow, and the snowy property
│   └── FrostboundClimate         public API (°C per biome)
├── client/                 client entrypoint; SnowyPlantModel draws snow on snowy plants
├── mobs/                   frozen mobs module (FrozenMobs, FrozenMob, FrostStats)
├── christmas/              christmas module: Christmas (registers resourcepacks/christmas when DecorationX is
│                           installed), ChristmasPresents (what opening a tree present does)
└── mixin/
    ├── environment/              BiomeMixin (open water, snow into plants), BlockMixin (adds
    │                             the snowy property), FreezeTopLayerFeature/ServerWorld (place
    │                             snowy plants instead of snow), SnowyBlockMixin (snowy grass)
    │                             MultiNoiseBiomeSourceParameterList (cold biomes only)
    └── mobs/                     frozen flag, combat, loot, visuals
```

- The `snowy` plant property is always added (even with the environment module off), so client and server always agree on block states. With the module off, snow simply stops settling into plants.

- The environment module's data (biome tags and the Tough As Nails tag) is a built-in data pack at `resources/resourcepacks/winter_environment`. It's only registered while the module is on.
- `mixin/FrostboundMixinPlugin` leaves out the snow-in-plants mixins when Snow! Real Magic is installed.
- `src/gametest` is a dev-only test mod (`frostbound-gametest`) and never ships.
- **Other mods in dev:** DecorationX: Christmas runs in the dev client and game tests. Gradle downloads it from Modrinth and copies it into `run/mods` and `build/gametest/mods` (`devMods` in `build.gradle`); Fabric Loader remaps it on start. Supplementaries can't run in dev (its library Moonlight crashes Fabric's dev environment), so the presents are only checked for real in a pack.

# Frostbound (Fabric 1.21.1)

A collection of winter features, built as one mod split into **modules**. Each module can be switched off in `config/frostbound.json` (restart needed):

```json
{
  "modules": { "environment": true, "frozenMobs": true, "ice": true, "christmas": true },
  "environment": { "coldBiomesOnly": true },
  "christmas": { "presents": { "frozenMobsWeight": 30, "giftWeight": 35, "coalWeight": 20, "explodeWeight": 15, "...": "see Christmas" } },
  "iceBalls": { "damage": 3.0, "weakToIceMultiplier": 2.0, "slownessSeconds": 4, "slownessLevel": 2, "freezeWaterRadius": 2, "cooldownTicks": 10 },
  "conductivity": { "chance": 0.05, "damageMultiplier": 1.5, "radius": 1.5 }
}
```

| Module | What it does |
|---|---|
| `environment` | Only cold biomes generate, and every Overworld biome becomes a winter variant with its own temperature, snow, ice and colours |
| `ice` | Ice drops when mined with a pickaxe better than stone (iron or up, plus the copper pickaxe; tag `frostbound:ice_mining_tools`), no Silk Touch needed, and it no longer melts to water, packed and blue ice can't be crafted, blue ice doesn't generate, new ice textures |
| `frozenMobs` | Some hostile mobs spawn Frozen: tougher, slower, icy |
| `christmas` | Christmas tree clearings with surprise presents (needs DecorationX: Christmas; presents need Supplementaries) |

Items like the **Ice Ball** and **copper gear** aren't a module: they're always there (so saved items never disappear), and `iceBalls` / `conductivity` in the config only set their balance.

## Hearts and vessels

You start with **5 hearts** and can grow to **20**. Vessels are always available (they aren't a module). Hold right click to use one; letting go early cancels without using it up.

- **Frozen Flesh**: ice in the centre, rotten flesh above, below and to each side (makes 2). Frozen zombies, husks and drowned also drop it (35%, more with Looting) when the Frozen Mobs module is on.
- **Frozen Vessel**: 5 frozen flesh in a plus, 4 copper ingots in the corners. Thaws into you as a **Frozen Heart**: an ice heart drawn after your other hearts (golden hearts too). It takes damage before anything else, never heals, cracks when half gone and shatters when it breaks. It's lost on death. A cracked one can be mended with another vessel; a whole one can't be stacked. Golden apples still work as usual and stay gold. Void damage and `/kill` go straight past it.
- **Revitalized Vessel VI–XX**: a Frozen Vessel in the centre, surrounded by a material. Each gives one permanent heart, in order: VI gives your 6th heart, up to XX for your 20th. While you hold it, it beats faster and faster, then pops up on screen like a totem. The tooltip says whether it's ready, already used, or which vessel you need first. The materials get harder, so they work as a quest book:

| Vessel | Surround the Frozen Vessel with | Vessel | Surround the Frozen Vessel with |
|---|---|---|---|
| VI | 8 copper ingots | XIV | 8 blaze rods |
| VII | 8 iron ingots | XV | 8 ender pearls |
| VIII | 8 packed ice | XVI | 8 netherite scrap |
| IX | 8 gold ingots | XVII | 8 shulker shells |
| X | 8 amethyst shards | XVIII | 4 totems of undying (plus shape) |
| XI | 8 diamonds | XIX | 4 netherite ingots (plus shape) |
| XII | 8 emeralds | XX | 4 nether stars (plus shape) |
| XIII | 8 nether quartz | | |

Permanent hearts are saved on the player and kept through death. Blue ice isn't used because the ice module stops it generating.

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
| Drops | 1–2 ice, 0–3 snowballs, 0–2 ice balls, rare packed ice (Looting boosts all of these) |
| Visuals | Shiver (the powder-snow shake), pale ice-blue tint, drifting snowflakes, shatter on death |

Spawn chance: **8%** normally, **30%** in arctic biomes (≤ −20 °C). Mobs that can roll Frozen: zombie, zombie villager, husk, drowned, skeleton, spider, cave spider, creeper, slime, witch, pillager, vindicator.

## Ice Balls

`frostbound:ice_ball`, in the Combat tab next to snowballs. Stacks to 16. Right-click to throw it like a snowball (same arc and speed), with a half-second cooldown. It shatters into ice chips and snowflakes on whatever it hits:

| Hits | What happens |
|---|---|
| A mob or player | 3 damage (1½ hearts), Slowness II for 4 s, frosted screen edges (no freeze damage), and puts them out if they're burning |
| Blaze, magma cube, strider | double damage (the tag `frostbound:weak_to_ice`) |
| A Frozen mob | takes the damage, but it's made of ice: no Slowness, no frost |
| Still water | freezes a disc of frosted ice (radius 2) on the surface, like Frost Walker. A quick bridge over open water; it melts again in daylight |
| Fire, a lit campfire or candle | puts it out, like a water splash potion |

- **Dispensers** fire them, so they work in traps.
- **Crafting:** an ice block surrounded by 4 snowballs (plus shape) makes 4 ice balls. Unlocks in the recipe book once you pick up ice.
- **Drops:** Frozen mobs drop 0–2.
- **Balance:** `iceBalls` in `config/frostbound.json` (restart to apply): `damage`, `weakToIceMultiplier`, `slownessSeconds` (0 turns it off), `slownessLevel` (1 = Slowness I), `freezeWaterRadius` (0 turns it off), `cooldownTicks` (20 = 1 second).

Try it: `/give @s frostbound:ice_ball 16`.

## Fire Starting

The first way to get warm, before flint and steel.

- **Fire Starter** (`frostbound:fire_starter`, Tools tab): two sticks, crafted anywhere (even the 2×2 grid). 5 durability.
- **Campfire Base** (`frostbound:campfire_base`, Functional Blocks tab): any four logs that burn, in any arrangement, so it fits the 2×2 inventory grid. An unlit campfire; breaks with an axe and drops itself.
- **Wood blocks** (bark on all sides, plain and stripped) now come from the **stonecutter**, 1 log → 1 wood. Vanilla's 2×2 Wood recipe would clash with the campfire base, so it's switched off (`data/minecraft/recipe/*_wood.json`). Nether stems still make hyphae the vanilla way, since they don't burn.

Hold right-click with the fire starter on a campfire base (or any unlit campfire, such as one an ice ball put out) and a bar appears under the crosshair. A marker sweeps back and forth; left-click while it's over the green. Each hit fills a pip, moves the green zone and speeds the marker up a little. **Three hits in a row** light the fire (the base becomes a lit vanilla campfire, facing the same way) and cost 1 durability. **A miss** costs 1 durability and resets the streak. Letting go of right-click or looking away ends the attempt (the streak resets, durability spent stays spent). A perfect run gets 5 fires out of one fire starter.

The client draws the bar and judges each click; the server keeps the streak, takes durability and lights the fire (`com.frostbound.campfire`, `client.campfire.FireStarterMinigame`).

Try it: `/give @s frostbound:fire_starter` and `/give @s frostbound:campfire_base`.

## Copper Gear

Copper tools and armour from Minecraft 1.21.9 ("The Copper Age"), brought back to 1.21.1 with the same stats, recipes and textures (the textures and the armour equip sound come straight from the 1.21.10 game files). They're `frostbound:` items, so `/give @s frostbound:copper_sword`.

| Item | Stats | Recipe |
|---|---|---|
| Copper Sword | 5 damage, 1.6 speed, 190 durability | like any sword, with copper ingots |
| Copper Axe | 9 damage, 0.8 speed | like any axe |
| Copper Pickaxe, Shovel, Hoe | mine like stone (iron ore yes, diamond/gold/redstone no), dig speed 5 | like any tool |
| Copper Helmet / Chestplate / Leggings / Boots | 2 / 4 / 3 / 1 armour, durability 121 / 176 / 165 / 143 | like any armour |
| Copper Horse Armor | 4 armour | not craftable: found in dungeons, desert and jungle temples, nether fortresses, strongholds, end cities and village weaponsmiths (as often as iron horse armour) |
| Copper Nugget | | 1 ingot ↔ 9 nuggets; smelting or blasting copper gear gives a nugget |

Copper sits between stone and iron: it mines what stone mines but lasts longer (190 vs 131) and digs faster. Tools are repaired with copper ingots (tag `frostbound:copper_tool_materials`), armour can be enchanted and trimmed, and the copper pickaxe also mines ice when the ice module is on.

### Conductivity

Every copper tool has the **Conductivity** passive, shown at the top of its tooltip:

> **CONDUCTIVITY**
> 5% chance to deal 150% damage to any mobs within 1.5 blocks of the targeted mob. Can chain endlessly.

When you hit a mob in melee with a copper tool there's a 5% chance the hit arcs: every other mob within 1.5 blocks of the one you hit takes 150% of your hit's damage, with a crackle and a line of sparks. **It chains:** every mob it shocks rolls the same 5% to arc on from itself, to the mobs within 1.5 blocks of *it*, and so on with no limit, so a packed swarm can light up end to end. Each mob is shocked at most once per chain, and every link deals 150% of your original hit (it doesn't grow along the chain). The mob you hit takes its normal damage. One roll per swing (a sword's sweep doesn't roll again). It never hits you, your pets, your teammates, armour stands or other players (a mob holding a copper weapon can shock players). Deaths read "was electrocuted by".

- **Balance:** `conductivity` in `config/frostbound.json` (restart): `chance` (0.05 = 5%, 0 turns it off), `damageMultiplier` (1.5 = 150%), `radius` (blocks). The tooltip numbers follow the config.
- **Which weapons conduct:** the item tag `frostbound:conductive` (the five copper tools); a data pack can add any item.
- **Passives** are a small framework (`com.frostbound.passives`): each is a name, a colour, an item tag and a description, and the tooltip layout is shared. See `CLAUDE.md` for how to add one.

Try it: `/give @s frostbound:copper_sword`, then `/summon pig` a few times close together and hit one (or set `chance` to 1 in the config to see it every hit).

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

**Switching machines:** `git pull` before you start, then commit and `git push` when you stop. Every push to GitHub also runs the build and the GameTests there (the **Actions** tab), so you can see if something broke on the other machine before you pull.

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
- `CopperTests`: copper tool and armour stats match 1.21.9; the pickaxe harvests like stone; recipes (tools, armour, nuggets, smelting to nuggets) work; copper horse armour turns up in dungeon chests; a Conductivity arc hits a nearby mob for 150% but not a far one; the chain runs down a whole line of mobs when every roll passes (each shocked once, for 150% of the hit) and stops after the first link when they fail, the attacker, its wolf or other players; nothing arcs at 0% or from a non-copper weapon; the tooltip text and colours.
- `IceBallTests`: real throws (a ball dropped onto the target): damage, Slowness and frost; double damage to blazes; Frozen mobs aren't slowed; burning targets go out; water freezes into a frosted-ice disc; fire and campfires go out; dispensers shoot them; the recipe and tag load.

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
- **Ice balls:** numbers in the config (`iceBalls`); which mobs take extra damage in `data/frostbound/tags/entity_type/weak_to_ice.json`; the recipe in `data/frostbound/recipe/ice_ball.json`; the texture is `assets/frostbound/textures/item/ice_ball.png` (16×16).
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
├── items/                  always-on items: FrostboundItems (registers items, entities, dispenser and
│                           creative tab), IceBallItem (throwing), IceBallEntity (what a hit does)
├── copper/                 copper gear backported from 1.21.9: CopperGear (materials, items, creative tabs),
│                           Conductivity (the copper passive)
├── passives/               Passive (a named item bonus + its tooltip text), Passives (all of them);
│                           client/PassiveTooltips draws them at the top of tooltips
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

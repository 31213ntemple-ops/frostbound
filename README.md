# Frostbound (Fabric 1.21.1)

A collection of winter features, built as one mod split into **modules**. Each module can be switched off in `config/frostbound.json` (restart needed):

```json
{
  "modules": { "environment": true, "frozenMobs": true, "ice": true, "christmas": true },
  "environment": { "coldBiomesOnly": true },
  "christmas": { "presents": { "frozenMobsWeight": 30, "giftWeight": 35, "coalWeight": 20, "explodeWeight": 15, "...": "see Christmas" } },
  "iceBalls": { "damage": 3.0, "weakToIceMultiplier": 2.0, "slownessSeconds": 4, "slownessLevel": 2, "freezeWaterRadius": 2, "cooldownTicks": 10 },
  "conductivity": { "chance": 0.05, "damageMultiplier": 1.5, "radius": 1.5 },
  "frostTiers": { "coldArmor": 0.5, "frozenArmor": 0.5, "frozenToughness": 0.5, "coldDurability": 0.25, "icyDurability": 0.15,
                  "coldAttackDamage": 0.5, "frozenAttackDamage": 0.5 },
  "frostBonuses": { "snowstride": { "icy": 0.04, "frozen": 0.08 }, "...": "see Imbuement Table" },
  "alphaWolf": { "health": 80.0, "initialPackSize": 7, "maxPackSize": 9, "...": "see Alpha Wolf" },
  "packInstinct": { "belowHealth": 8.0, "cooldownSeconds": 60.0, "...": "see Alpha Wolf" },
  "lifeSteal": { "healPerLevel": 0.1 },
  "markOfThePack": { "wolves": 2, "lifetimeSeconds": 30.0, "cooldownSeconds": 60.0 }
}
```

| Module | What it does |
|---|---|
| `environment` | Only cold biomes generate, and every Overworld biome becomes a winter variant with its own temperature, snow, ice and colours |
| `ice` | Ice drops when mined with a pickaxe better than stone (iron or up, plus the copper pickaxe; tag `frostbound:ice_mining_tools`), no Silk Touch needed, and it no longer melts to water, packed and blue ice can't be crafted, blue ice doesn't generate, new ice textures |
| `frozenMobs` | Some hostile mobs spawn Frozen: tougher, slower, icy |
| `christmas` | Christmas tree clearings with surprise presents (needs DecorationX: Christmas; presents need Supplementaries) |

Items like the **Ice Ball**, **copper gear**, the **Imbuement Table** and the **Alpha Wolf** and its drops aren't a module: they're always there (so saved items never disappear), and `iceBalls` / `conductivity` / `frostTiers` / `frostBonuses` / `alphaWolf` / `packInstinct` / `lifeSteal` / `markOfThePack` in the config only set their balance.

**Required mods:** Fabric API and **Trinkets** (3.10+, which brings Cardinal Components API with it). Trinkets gives players the necklace slot the Mark of the Pack is worn in.

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
| Copper Pickaxe, Shovel, Hoe | mine what stone mines **plus iron ore** (diamond/gold/redstone no), dig speed 5 | like any tool |
| Copper Helmet / Chestplate / Leggings / Boots | 2 / 4 / 3 / 1 armour, durability 121 / 176 / 165 / 143 | like any armour |
| Copper Horse Armor | 4 armour | not craftable: found in dungeons, desert and jungle temples, nether fortresses, strongholds, end cities and village weaponsmiths (as often as iron horse armour) |
| Copper Nugget | | 1 ingot ↔ 9 nuggets; smelting or blasting copper gear gives a nugget |

Copper sits between stone and iron, and **iron needs it**: stone (and wooden and golden) pickaxes can't mine iron ore, deepslate iron ore or raw iron blocks any more (they break them slowly and get nothing), so the early game goes wood → stone → copper → iron. The blocks are in the tag `frostbound:needs_copper_tool` (which also takes any modded ore in `c:ores/iron`), added to `minecraft:incorrect_for_stone_tool`. Copper lasts longer than stone (190 vs 131) and digs faster. Tools are repaired with copper ingots (tag `frostbound:copper_tool_materials`), armour can be enchanted and trimmed, and the copper pickaxe also mines ice when the ice module is on.

### Conductivity

Every copper tool has the **Conductivity** passive, shown at the top of its tooltip:

> <span style="color:#FFAA00">Conductivity</span>
> 5% chance to deal 150% damage to any mobs within 1.5 blocks of the targeted mob. Can chain endlessly.

When you hit a mob in melee with a copper tool there's a 5% chance the hit arcs: every other mob within 1.5 blocks of the one you hit takes 150% of your hit's damage, with a crackle and a line of sparks. **It chains:** every mob it shocks rolls the same 5% to arc on from itself, to the mobs within 1.5 blocks of *it*, and so on with no limit, so a packed swarm can light up end to end. Each mob is shocked at most once per chain, and every link deals 150% of your original hit (it doesn't grow along the chain). The mob you hit takes its normal damage. One roll per swing (a sword's sweep doesn't roll again). It never hits you, your pets, your teammates, armour stands or other players (a mob holding a copper weapon can shock players). Deaths read "was electrocuted by".

- **Balance:** `conductivity` in `config/frostbound.json` (restart): `chance` (0.05 = 5%, 0 turns it off), `damageMultiplier` (1.5 = 150%), `radius` (blocks). The tooltip numbers follow the config.
- **Which weapons conduct:** the item tag `frostbound:conductive` (the five copper tools); a data pack can add any item.
- **Passives** are a small framework (`com.frostbound.passives`): each is a name, a colour, an item tag and a description, and the tooltip layout is shared. See `CLAUDE.md` for how to add one.

Try it: `/give @s frostbound:copper_sword`, then `/summon pig` a few times close together and hit one (or set `chance` to 1 in the config to see it every hit).

## Imbuement Table

`frostbound:imbuement_table` (Functional Blocks tab, next to the smithing table): a smithing table that's been frozen over, with a snow cap and icicles, its wood turned a frozen navy, its tools turned to ice, and an ice grid set into the top with a darker centre. **Recipe:** a smithing table in the middle, ice on its four sides, iron ingots in the corners; it unlocks once you pick up ice. Mined with an axe.

Right-click it for a 3×3 grid like a crafting table's, with a result slot. The **centre slot takes armour and weapons** (helmets, chestplates, leggings, boots, turtle shells, modded armour; swords, axes, the mace and the trident; not horse armour, elytra, tools or bows): it's darker and set apart from the eight outer slots by a small gap. Shift-click puts armour or a weapon in the centre and anything else around it. Whatever's left in the grid comes back to you when you close it. Taking the result uses up the piece and the items around it.

### The menu

The screen is the table frozen over: a frosty panel with a snow cap and icicles, and a dark ice recess where snow drifts down behind the slots.

- **Working:** when the ring around the piece makes an upgrade, the eight outer slots light up in the new tier's colour, a wave of light running clockwise round them. The centre glows, the arrow fills with ice, and the tier it makes ("Icy ★★") shows over the result.
- **Imbuing:** taking the result throws a burst of frost out of the result box, and the ring flashes.
- **The tier ladder** along the bottom of the recess shows the path Cold → Icy → Frozen, each tier with its ring item (and how many per slot) and its stars. With a piece in the centre, the tiers it already has are ticked, the next one glows and the ones after are frosted over. Hover a tier to see what goes around the piece and what it gives armour and weapons (with the config's numbers), and whether this piece is ready for it.

The panel is painted by `tools/ImbuementGuiTexture.java` (`java tools/ImbuementGuiTexture.java`); edit and re-run it rather than editing the PNG. The animation is in `client/imbuement/ImbuementScreen`.

### Cold, Icy and Frozen gear

| Tier | Put around the piece | Armour gets | Weapons get | Name |
|---|---|---|---|---|
| **Cold** | 1 ice in each of the 8 slots | +0.5 armour, +25% durability | +0.5 attack damage, +25% durability | <span style="color:#CDE7F2">Cold</span> Iron Chestplate <span style="color:#CDE7F2">★</span> |
| **Icy** (from Cold) | 4 packed ice in each slot (32) | +15% more durability (+40% in all) and its material's bonus | +15% more durability and Frostbite | <span style="color:#A3C9DE">Icy</span> Iron Chestplate <span style="color:#CDE7F2">★</span><span style="color:#A3C9DE">★</span> |
| **Frozen** (from Icy) | 1 blue ice in each slot | +0.5 more armour (+1 in all), +0.5 toughness, the stronger bonus | +0.5 more attack damage (+1 in all), the stronger Frostbite | <span style="color:#7FA7C6">Frozen</span> Iron Chestplate <span style="color:#CDE7F2">★</span><span style="color:#A3C9DE">★</span><span style="color:#7FA7C6">★</span> |

**How it looks:** frosty gear shows it, in the inventory, in your hand, on the ground and worn. A still frost pattern is laid over the item and the worn armour (drawn the way the enchantment glint is, so it sits only on the item and works alongside enchantments and trims), and it glistens slowly:

- **Cold:** a light sprinkle of pale rime specks.
- **Icy:** small frost crystals branching across it; worn, it now and then sheds a snowflake.
- **Frozen:** dense crystals over a faint icy sheen, a deeper blue; worn, it sheds snowflakes more often, with the odd glint.

(The frost textures are `textures/misc/frost_{cold,icy,frozen}.png`; the colours and glisten are in `client/imbuement/FrostRenderLayers`.)

In the tooltip the tier's name comes before the item's ("Frozen Iron Chestplate") in that tier's colour, and the stars after it, each a soft blue a little deeper than the one before (`FrostTier.starColor`; names in `frostbound.frost_tier.*` in the lang file). **Every bonus shows as a little (+x) in the tier's colour after vanilla's number**, so you can see what the frost added: "+8 Armor (+1)", "+2 Armor Toughness (+0.5)", " 6 Attack Damage (+0.5)". Vanilla's number stays the item's own; the piece really gives both (a player in that Frozen chestplate has 9 armour). Frosty gear also always shows its durability, with the extra from frost: "Durability: 739 / 739 (+211)" (vanilla only shows durability with advanced tooltips, on a damaged item; with them on, the (+x) goes on that line). The tier is stored on the item (`frostbound:frost_tier`, 1–3) and the stats are worked out from the config whenever they're needed, so they follow config changes and carry over when you smith a diamond piece into netherite. Enchanting, trims, repairs and the anvil all keep it.

**Blue ice:** with the `ice` module on, blue ice doesn't generate and can't be crafted, so Frozen needs blue ice from somewhere else (a wandering trader, another mod, or with the module off).

### Material bonuses (Icy and Frozen)

Each armour material gets one bonus, and every weapon gets Frostbite, shown as a passive at the top of the tooltip (a copper sword lists Conductivity first), its name coloured after what it does: white Snowstride (snow), yellow Superconductor (electricity), blue Frostmail (protection), red Cold Iron (damage), pink Hearthgold (healing, like Regeneration), aqua Diamond Dust and Frostbite (frost), orange Quenched (fire, like Fire Resistance), dark aqua Tidefrost (water, like Water Breathing). Every bonus is **per piece worn and adds up**, and a Frozen piece counts the Frozen number, so a mixed set still works. The weaker materials get the more useful bonuses, so every set is worth imbuing:

| Material | Bonus | Icy / Frozen, per piece | Full set Icy / Frozen |
|---|---|---|---|
| Leather | **Snowstride**: faster on snow and ice (snow layers, snow blocks, powder snow, snowy grass, any ice) | +4% / +8% speed | +16% / +32% |
| Copper | **Superconductor**: any melee hit, with any weapon, can arc like Conductivity (the chain rolls the same chance) | 1.5% / 3% chance | 6% / 12%, plus the copper weapon's 5% |
| Chainmail | **Frostmail**: less damage from arrows, tridents and other projectiles | −5% / −10% | −20% / −40% |
| Iron | **Cold Iron**: more melee damage to undead (zombies, skeletons, strays, ...) | +4% / +8% | +16% / +32% |
| Gold | **Hearthgold**: heals half a heart every 20 s / 10 s, each piece on its own | | half a heart every 5 s / 2.5 s (like Regeneration I) |
| Diamond | **Diamond Dust**: melee attackers take part of the damage back as frost ("was frozen solid trying to hurt") | 5% / 10% | 20% / 40% |
| Netherite | **Quenched**: less fire and lava damage | −6% / −12% | −24% / −48% |
| Turtle shell | **Tidefrost**: the helmet's Water Breathing lasts longer | +10 s / +20 s | (helmet only) |
| Weapons | **Frostbite**: a melee hit chills the target, giving it Slowness I | 2 s / 4 s | (the weapon in your main hand) |

- **Balance:** `frostTiers` (armour, toughness, attack damage and durability per tier) and `frostBonuses` (each bonus as `{ "icy": ..., "frozen": ... }`; Hearthgold and Tidefrost are in seconds) in `config/frostbound.json`. The tooltips follow the config.
- **Which items have which bonus:** item tags `frostbound:snowstride`, `superconductor`, `frostmail`, `cold_iron`, `hearthgold`, `diamond_dust`, `quenched`, `tidefrost`, `frostbite`. A data pack can give modded armour a bonus; any armour can be imbued for the stats. **Which weapons can be imbued:** `frostbound:imbuable_weapons` (swords, axes, the mace, the trident).
- Code: `com.frostbound.imbuement` (`FrostTiers` the tiers, recipes and stats, `FrostBonuses` the bonuses, `ImbuementTableBlock` the block, `ImbuementScreenHandler` the menu), `mixin/imbuement` (stats, the (+x) on attribute lines, damage), `client/imbuement` (the animated screen, stars and the durability line, frost layers, snowflakes), `mixin/imbuement/client` (frost on items and worn armour).

Try it: `/give @s frostbound:imbuement_table`, `/give @s minecraft:iron_chestplate`, `/give @s minecraft:ice 8`, `/give @s minecraft:packed_ice 32`, `/give @s minecraft:blue_ice 8`. Or straight to a tier: `/give @s minecraft:leather_boots[frostbound:frost_tier=3]`.

## Alpha Wolf

An early-game Overworld boss: a big black wolf with a frost-tipped ruff, a scarred eye and burning red eyes, leading a pack. It's built for a player with about 7 hearts in iron (maybe Cold or Icy) armour, and it's about reading the pack's tells and using the den, not a huge health bar.

**Finding one: the Wolf Den.** An open, roofless ring of old mossy stone brick, about 21 blocks across, in cold forests and plains (taiga, snowy taiga, snowy plains, the old-growth taigas, grove, Sparse Forest). It has:

- a stepped dais in the middle where the Alpha sleeps;
- six thick pillars: some standing, with vines and a lantern on top, some broken stumps, and sometimes one fallen across the floor;
- a low broken wall with gaps to walk in through;
- scattered bones and skulls, and a chest at the foot of a pillar.

The ground is levelled into a clearing around it. Dens are about as common as Christmas tree clearings, a little rarer (`/locate structure frostbound:wolf_den`).

The pack is **asleep** when you find it: the Alpha curled up on the dais, six wolves on patches of moss around it, eyes shut, snoring softly. **Walking through doesn't wake them; hitting any of them does.** The Alpha lifts its head, gets up and howls, and the pack wakes in a ripple out from it (a growl each) and goes for whoever struck first. The boss bar appears and fills as the Alpha rises. The first hit of a sneak attack is free. Den wolves never despawn, and while asleep they slowly heal (0.5 hearts a second).

**Leaving:** the den doesn't hold the pack back: once woken it chases you wherever you go, and if it loses you mid-fight (out of sight for a moment) it picks you back up as soon as it sees you again. To get away, put **48 blocks** between you and the pack (or stay out of sight). After **10 s** with nothing to fight they walk home, each wolf to its own spot, and lie back down. The Alpha gets its reinforcements back for next time. Come back later to try again.

Or: `/summon frostbound:alpha_wolf` or the **Alpha Wolf Spawn Egg** bring an Alpha with a roaming pack, awake and without a den. It despawns when no player is within 128 blocks, unless name-tagged.

| | Alpha Wolf | Pack Wolf |
|---|---|---|
| Health | 80 (40 hearts) | 12 (6 hearts) |
| Bite | 5 (about 1¼ hearts through iron) | 3 (under 1 heart through iron) |
| Other | 1.5× size, armour 4, 60% knockback resistance, boss bar | |
| Pack | 7 wolves counting the Alpha, never more than 9 alive | |

Hostile and untameable: bones, food, sitting, breeding, collars, leads and wolf armour do nothing. Pack wolves are the local wolf's coat (never black) and look angry once awake.

### Reading the fight

Every attack has a **tell** (a pose and a sound, and for the big ones a marker on the ground), then the **action**, then a **recovery** that's your opening. Nothing cuts a move off halfway.

- **Bite** (any wolf, up close)
  - *Tell (0.7 s):* it stops and **drops into a crouch** (front down, rear up, quivering), growls and kicks up the ground.
  - *Action:* one lunge and bite, then it **bounds back out**.
  - *Answer:* shield, sidestep, or hit it while it crouches.
- **Pounce** (the Alpha, 4.5–13 blocks away)
  - *Tell (1.2 s):* it crouches low with its rear swaying. A **red ring** (2.5 blocks) on the ground under you **follows you**, then **stops** with a snarl for the last 0.5 s.
  - *Action:* it leaps in an arc and slams down inside the ring: **6 damage** and knockback to everyone there.
  - *Answer:* step out of the ring once it stops, then hit the Alpha while it stands **winded** for 1 s after landing.
- **Call of the Pack** (the Alpha)
  - *Tell:* it sits back with its snout to the sky, and **the whole pack sits and howls with it**. Nobody bites meanwhile.
  - *Action:* 2 s later every wolf heals and 2 more wolves arrive.
  - *Answer:* deal **8 damage** to the Alpha during the howl to cut it off.
- **Bloodied** (the Alpha, once, below half health)
  - *Tell:* it **rears up on its hind legs**.
  - *Action:* it roars, and a ring of snow throws everyone within 5 blocks back. The pack growls, the boss bar turns **red**, embers drift from the Alpha's eyes, and it's **15% faster** from then on.
  - *Answer:* back off and get ready for the Charge.
- **Charge** (the Alpha once Bloodied, 6–16 blocks away, in a clear line)
  - *Tell (1.8 s):* it lowers its head like a bull and **paws the ground three times**. A **pale line** on the ground shows its path and where it would hit something. For the last 0.5 s it stops turning, with a snort.
  - *Action:* it runs the line at full tilt: **8 damage** and a big knockback if it hits you.
  - *Answer:* sidestep once it's locked on. Better still, **stand in front of a pillar or wall**: it runs into it and is **stunned for 3 s** (stars, head lolling), taking **1.5× damage** meanwhile.

The pack fights as one:

- **Together:** when any wolf picks a fight, the Alpha takes it up and every wolf joins in.
- **In turns (hit and run):** only **2** pack wolves may bite at once; the rest **circle** you 4–7 blocks out, facing you.
- **It gives the Alpha room:** while the Alpha is winding up a big move, carrying it out, winded after it, or stunned, no pack wolf starts a bite, and any wolf still running in peels off. During its big moves, watch the Alpha rather than your back.
- **The boss bar** reads "Alpha Wolf · pack of 7". It's white, and turns red once the Alpha is Bloodied.

The Alpha makes a big move every **4.5 s** of fighting, or every **3 s** once Bloodied (then 60% of them are Charges). Up close it bites instead.

### Call of the Pack

12 s into a fight, and every 30 s after, the Alpha **sits back and howls** for 2 s, snout up, with a ring of frost spreading out from it, and the pack sits and howls along (one after another). Then:

- every living wolf heals **0.25 hearts for each wolf alive** (counted before reinforcements arrive: 3 alive → 0.75 hearts each);
- **2 wolves** arrive from 6–12 blocks out, in a puff of snow with a howl, never past 9 alive, and no more than **12** in a whole fight (after that, howls only heal).

**Cut it off:** deal 8 damage to the Alpha while it howls (two iron sword hits) and the howl breaks off with a yelp ("The Alpha's howl breaks off!"): no healing, no wolves, and it tries again 10 s later. In the first half of the fight, the main decision is whether to rush the howl or thin out the pack. In the second half, it's using the pillars against the Charge.

### Draining Bite

A pack wolf's bite that lands on a player (not blocked by a shield) applies **Drained**: **−2 max hearts for 10 s**. The lost hearts show as hollow, dark, slowly pulsing hearts at the end of the health bar, with a heartbeat and a crimson mist on the bite, and a dark red status effect with its own icon. When it ends (or milk cures it, or you die) the max health comes back; health you lost stays lost.

It can't pile up: a bite while Drained neither stacks nor extends it, so you're never down more than 2 hearts, and never for more than 10 s after the bite that started it. It works with vessel hearts (it's a modifier on top of them) and leaves the Frozen Heart alone.

### Enrage

When a pack wolf dies, every living wolf (reinforcements included) gets **Strength I for 1 s** and may lunge again at once, with angry sparks and a growl. Deaths close together refresh it, never stack or lengthen it. The Alpha's own death isn't an Enrage.

### When the Alpha dies

The pack breaks: every wolf stops fighting, whines and **runs from the nearest player**, and vanishes in a puff of snow once no player is within 24 blocks (or after 10 s at most). They can't be drawn back into the fight, even if hit. A pack that loses its Alpha some other way (despawned, or not found for 10 s) scatters the same way.

### Drops

| Drop | From | Chance |
|---|---|---|
| **Alpha Hide** | the Alpha | 5–7, always |
| **Mark of the Pack** | the Alpha (killed by a player) | 10% (+2% per Looting level) |
| **Wolf Tooth** | every pack wolf, the Alpha too (killed by a player) | 10%, one tooth |

25 XP from the Alpha, 3 from a pack wolf. The den's chest (`loot_table/chests/wolf_den.json`) holds what the pack dragged home: bones, leather and rabbit hide, iron nuggets and the odd ingot, arrows, berries, sometimes a Wolf Tooth, a name tag or a worn iron sword.

### Alpha Hide armour

| Piece | Armour | Durability | Recipe |
|---|---|---|---|
| Alpha Hide Hood | 2 | 165 | like any helmet, with Alpha Hide |
| Alpha Hide Tunic | 5 | 240 | like any chestplate |
| Alpha Hide Pants | 4 | 225 | like any leggings |
| Alpha Hide Boots | 2 | 195 | like any boots |

It looks the part: black wolf fur with frost-silver tips over dark tanned leather and bone. The **Hood** is the Alpha's head worn as a hood, with upright ears and the upper jaw jutting over your brow, teeth and all; the **Tunic** has a fur mantle that hangs into a short cape at the back, a strap with bone toggles across the chest, fur pauldrons and cuffs; the **Pants** a fur-trimmed belt with a bone buckle and wrapped shins; the **Boots** frost-tipped fur cuffs, laces and claws at the toes.

Beside iron, not above it: **13 armour for a set against iron's 15**, iron's durability, leather's enchantability (15), repaired with Alpha Hide. It can be imbued (Cold/Icy/Frozen stats) but has no material bonus. What it has instead:

- **Insulated** (any piece): you can't freeze (powder snow; a Frozen mob's Frostbite still slows you but can't frost you over), and Tough As Nails counts it as warm clothing (`toughasnails:heating_armor`).
- **Pack Instinct** (set bonus): when your health drops **below 4 hearts**, **2 pieces** give Regeneration II for 5 s, **4 pieces** also Strength I for 5 s. Then it **rests 60 s**, however often your health crosses the line, so it can't be farmed by bobbing around the threshold. The rest is saved on the player (survives relogging and death) and shows as a cooldown sweep on the armour icons; a message above the hotbar says when it fires.

### Life Steal

An enchantment (I–II) you can only craft, from Wolf Teeth:

- **Life Steal I:** a book in the centre, a Wolf Tooth above, below and to each side (corners empty).
- **Life Steal II:** a Life Steal I book in the centre, surrounded by 8 Wolf Teeth.

Goes on swords, axes, bows, crossbows and tridents (anvil, like any book). **The rule, the same for melee and ranged:** a hit heals you **10% per level of the health the target actually lost** (after armour, and capped at what it had left, so overkill heals nothing extra). The level comes from the weapon the hit came from: the sword or axe in your hand (sweeps too), the bow or crossbow that shot the arrow, or the thrown trident. Conductivity arcs, thorns, Diamond Dust and explosions never heal; armour stands don't count.

### Mark of the Pack

The Alpha's fang in a silver cap, on a braided cord with two red beads, worn in Trinkets' **necklace** slot, where it hangs on your chest over any armour. Press **J** (rebind under Controls → Frostbound) to call **2 wolves** that fight for you (like tame wolves) for **30 s**, then vanish in a puff of snow; it recharges for **60 s** (shown on the item; "The pack is resting (Ns)" if you press it early). The recharge is saved on the player. Called wolves can't be fed, sat, bred, leashed or armoured, and drop nothing.

- **Balance:** `alphaWolf` in `config/frostbound.json` (restart): stats, pack sizes, `simultaneousAttackers`, `lungeWindupTicks` (14), `bloodiedBelow` (0.5), `bloodiedSpeedBonus`, `moveIntervalSeconds` / `bloodiedMoveIntervalSeconds`, and `callOfThePack`, `pounce` (`windupTicks`, `damage`, `radius`, `recoverTicks`), `charge` (`windupTicks`, `lockTicks`, `speed`, `maxDistance`, `damage`, `stunSeconds`, `stunnedDamageMultiplier`), `drainingBite`, `enrage`, `den` (`giveUpSeconds`, `sleepHealPerSecond`). Also `packInstinct`, `lifeSteal`, `markOfThePack`. Tooltips follow the config. A config file written by an older version keeps its old values and drops the old `spawning` block (delete the `alphaWolf` block to get every new default, such as the 14-tick bite tell).
- **The den:** how rare in `worldgen/structure_set/wolf_dens.json` (`spacing`/`separation`); where in the biome tag `frostbound:has_structure/wolf_den`; the layout in code, `alpha/den/WolfDenLayout.java` (seeded, so every den differs a little); the chest in `loot_table/chests/wolf_den.json`.
- **Data:** loot tables `entities/alpha_wolf.json` and `entities/pack_wolf.json`; `enchantment/life_steal.json`; tags `frostbound:pack_instinct`, `insulated`, `mark_of_the_pack`, `enchantable/life_steal`; Trinkets slot data in `data/trinkets/`.
- **Code:** `com.frostbound.alpha` (`AlphaWolfEntity`, `PackWolfEntity`, `WolfAction` what each wolf is doing (synced, for the poses), `PackGoals` the pack's AI, `AlphaGoals` the Alpha's Pounce, Charge, roar and going home, `DrainingBite`, `AlphaHide`, `LifeSteal`, `MarkOfThePack`, `SummonedWolfEntity`); `alpha/den` (the structure); `client/alpha` (`PackWolfModel` the poses, the renderer, the Hood's ears and jaw, the necklace on the chest, drained hearts, the J key).
- **Textures:** generated by `tools/AlphaWolfTextures.java` (see Tweaking): the Alpha's coat and the closed-eye sleeping coats are vanilla 1.21.1 wolf textures, edited; the armour, its icons, the Hood's ears and jaw, the Alpha Hide, the tooth and the necklace are drawn pixel by pixel in one palette. The eyes, drained heart and effect icon are drawn for the mod.

Try it: `/locate structure frostbound:wolf_den` (or `/place structure frostbound:wolf_den` where you stand), `/give @s frostbound:alpha_wolf_spawn_egg`, `/give @s frostbound:mark_of_the_pack`, `/give @s frostbound:wolf_tooth 12`, `/give @s frostbound:alpha_hide 24`.

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
| Screenshot the Alpha Wolf content (opens a client for about a minute, then quits) | `.\gradlew.bat runScenes` | **Scenes** |

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
- `CopperTests`: copper tool and armour stats match 1.21.9; the pickaxe harvests like stone plus iron; wooden, stone and golden pickaxes can't harvest iron ore, deepslate iron ore or raw iron blocks, and stone still harvests copper, coal and lapis; recipes (tools, armour, nuggets, smelting to nuggets) work; copper horse armour turns up in dungeon chests; a Conductivity arc hits a nearby mob for 150% but not a far one; the chain runs down a whole line of mobs when every roll passes (each shocked once, for 150% of the hit) and stops after the first link when they fail, the attacker, its wolf or other players; nothing arcs at 0% or from a non-copper weapon; the tooltip text and colours.
- `ImbuementTests`: the table's recipe (smithing table, ice on the sides, iron in the corners) and block; each upgrade step needs its exact ring (8 ice, then 8 × 4 packed ice, then 8 blue ice) and only works on gear of the tier before; weapons can be imbued (not tools or bows) for attack damage and durability, held in the main hand, and get Frostbite from Icy, which slows what they hit in melee for the Icy or Frozen time; the tooltip shows "+8 Armor (+1)", "+2 Armor Toughness (+0.5)" and " 6 Attack Damage (+0.5)" with the (+x) in the tier's colour, and the durability line with its (+x), added or appended; Cold/Icy/Frozen armour, toughness and durability (worn and after smithing into netherite); the menu's centre slot only takes armour, taking the result uses up 4 packed ice per slot and closing hands the rest back; the title ("Frozen Leather Tunic ★★★", the tier name in its star's colour), the stars (soft blues, each darker) and the bonus tooltips (none below Icy, Frozen numbers when Frozen, one bonus per material, each coloured after what it does); Cold Iron, Frostmail, Quenched, Diamond Dust, Superconductor, Snowstride, Hearthgold and Tidefrost each do what they say.
- `AlphaWolfTests`: the Alpha's stats and size, untameable wolves; a new Alpha brings 7 (black Alpha, no black wolves); Call of the Pack heals 0.25 hearts per wolf alive and brings 2, never past 9 or the reinforcement budget; a big enough hit cuts the howl off; never more than 2 wolves lunging at once in a real fight; Enrage is Strength I for 1 s, refreshed not stacked; the Alpha's death scatters the pack and they vanish; Draining Bite takes 2 max hearts for 10 s, doesn't stack or extend, comes back when it ends, and only from a landed pack bite; drops (5–7 hides, ~10% Mark, ~10% tooth); Alpha Hide stats, insulation tags and recipes; Pack Instinct (2 vs 4 pieces, rests after triggering); the Life Steal recipes (cardinals only, I → II) and healing (melee and ranged the same, overkill capped, no heal from arcs or plain weapons); the Mark of the Pack in the necklace slot calls 2 wolves, recharges, and the wolves vanish; the tooltips.
- `WolfDenTests`: den layouts (24 seeds) are fair arenas (a spot for every wolf with floor under it, the Alpha on the dais with nothing over it, at least 3 tall pillars, a wall with gaps, lanterns, a chest); a built den holds a sleeping Alpha and 6 wolves that know their den and never despawn; walking through wakes nobody, one hit wakes the Alpha first and then the whole pack, which goes for the attacker; a woken pack chases you out past its den, and picks you back up if it loses you and you come back into sight; with the attacker gone they walk home and lie back down; the Pounce hits a target who stays in the ring and misses one who steps out once it stops; a Bloodied Alpha roars before its first Charge, runs into a wall and is stunned, taking 1.5x damage; the pack doesn't bite during the Alpha's Pounce and howls with it.
- `BlockbenchTests`: the importer's output for the fixtures in `src/gametest/blockbench`, checked against values worked out by hand from Blockbench's own exporters:
  - **Block (Blockbench 5 file):** textures embedded or vanilla, the particle, UVs scaled to 0–16, inflate, shade, rotation with rescale, cullface, tint, display slots, the default blockstate and item model.
  - **Item (Blockbench 4 file):** inline groups, faces pointing at textures by index.
  - **Entity:** bones, pivots (Y flipped, +24 at the root), negated rotations, rotated cubes split into `_r1` parts, cube corners, box UV and mirror, the same bones from a Blockbench 4 copy.
  - **Animations:** Blockbench 5's flipped axes (and not for 4), step and before/after keyframes, catmull-rom, arithmetic in values, looping.
  - Turning them into model parts and animations needs client classes, so `runScenes` checks that (`dev/BlockbenchClientCheck`).
  - The importer's error messages are unit tests in `buildSrc/src/test`, run by any build that changes the importer (`./gradlew -p buildSrc test` on their own).
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

- **Models (Blockbench):** blocks, items and entities are modelled in [Blockbench](https://www.blockbench.net/) and saved as `.bbmodel` in `src/main/blockbench/{block,item,entity}/`. The build imports them on its own (models, textures, blockstates, entity geometry and animations), with no export step. See [`src/main/blockbench/README.md`](src/main/blockbench/README.md) for the workflow and the Java side (`BlockbenchEntityModel`, `BlockbenchModels.registerLayer`).
- **Imbuement table screen:** edit `tools/ImbuementGuiTexture.java` (layout constants at the top must match `ImbuementScreenHandler` and `ImbuementScreen`), run `java tools/ImbuementGuiTexture.java`, then `runScenes` (the menu empty, mid-glow and with the ladder, and frosty tooltips).
- **Alpha Wolf art:** edit `tools/AlphaWolfTextures.java` (the icons are pixel grids, one character per pixel; the armour layers are painted in code) and run `java tools/AlphaWolfTextures.java ~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar` from the project root. Then `.\gradlew.bat runScenes` and look in `build/scenes/screenshots/` (the den by day and night, every pose, the Alpha, the armour and the necklace).

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
├── client/blockbench/      entity models made in Blockbench: BlockbenchModels (layers, animations, texture),
│                           BlockbenchEntityModel, BlockbenchEntityRenderer (see src/main/blockbench/README.md)
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
├── imbuement/              the imbuement table: Imbuement (registers it), ImbuementScreenHandler (the grid),
│                           ImbuementTableBlock (the block), FrostTiers (Cold/Icy/Frozen, recipes, stats,
│                           the (+x)), FrostBonuses (the material bonuses and Frostbite);
│                           client/imbuement has the screen, stars, frost overlay and snowflakes
├── alpha/                  the Alpha Wolf boss: AlphaWolfEntity, PackWolfEntity, WolfAction (synced, for poses),
│                           PackGoals (the pack AI), AlphaGoals (Pounce, Charge, roar, going home), DrainingBite,
│                           and its rewards: AlphaHide (armour, Pack Instinct), LifeSteal, MarkOfThePack
│                           (Trinkets necklace) with SummonedWolfEntity; den/ has the Wolf Den structure
│                           (WolfDens, WolfDenStructure, WolfDenPiece, WolfDenLayout); client/alpha has
│                           PackWolfModel (the poses), the renderer, the Hood's ears and jaw, the necklace on
│                           the chest, drained hearts and the J key
├── passives/               Passive (a named item bonus + its tooltip text), Passives (all of them);
│                           client/PassiveTooltips draws them at the top of tooltips
├── christmas/              christmas module: Christmas (registers resourcepacks/christmas when DecorationX is
│                           installed), ChristmasPresents (what opening a tree present does)
└── mixin/
    ├── environment/              BiomeMixin (open water, snow into plants), BlockMixin (adds
    │                             the snowy property), FreezeTopLayerFeature/ServerWorld (place
    │                             snowy plants instead of snow), SnowyBlockMixin (snowy grass)
    │                             MultiNoiseBiomeSourceParameterList (cold biomes only)
    ├── imbuement/                ItemStackMixin (tier stats, the (+x) on attribute lines), FrostBonusDamageMixin (Cold Iron, Frostmail, Quenched);
    │                             client/ItemRendererMixin, ArmorFeatureRendererMixin (the frost overlay)
    └── mobs/                     frozen flag, combat, loot, visuals
```

- The `snowy` plant property is always added (even with the environment module off), so client and server always agree on block states. With the module off, snow simply stops settling into plants.

- The environment module's data (biome tags and the Tough As Nails tag) is a built-in data pack at `resources/resourcepacks/winter_environment`. It's only registered while the module is on.
- `mixin/FrostboundMixinPlugin` leaves out the snow-in-plants mixins when Snow! Real Magic is installed.
- `src/gametest` is a dev-only test mod (`frostbound-gametest`) and never ships.
- **Trinkets** is a normal (required) dependency from the TerraformersMC maven; Cardinal Components comes with it from the Ladysnake maven. In GameTests, the vanilla mock server player gets kicked when Cardinal Components syncs to its fake connection, so tests that need a server player build an unlisted one (`ImbuementTests.serverPlayer`).
- **Other mods in dev:** DecorationX: Christmas runs in the dev client and game tests. Gradle downloads it from Modrinth and copies it into `run/mods` and `build/gametest/mods` (`devMods` in `build.gradle`); Fabric Loader remaps it on start. Supplementaries can't run in dev (its library Moonlight crashes Fabric's dev environment), so the presents are only checked for real in a pack.

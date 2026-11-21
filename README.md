# Frostbound (Fabric 1.21.1)

Some hostile mobs spawn **Frozen**:

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

Spawn chance: **8%** normally, **30%** in cold biomes. Mobs that can roll Frozen: zombie, zombie villager, husk, drowned, skeleton, spider, cave spider, creeper, slime, witch, pillager, vindicator.

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

**Typical loop:** change code → `runGametest` → if green, `runClient` and look at it in-game (`/function frostbound:test`).

**Faster iteration in IntelliJ:** start **Minecraft Client** with *Debug* (the bug icon) instead of Run. When you edit the inside of a method, press **Ctrl+Shift+F9** (Recompile) to hot-swap it into the running game. Adding methods/fields or changing a mixin's annotations still needs a restart. For data files (tags, loot tables, functions), use *Build → Build Project* and then `/reload` in-game.

If you edit `build.gradle`, click the Gradle **Reload** (elephant) icon in IntelliJ.

### Automated tests

They live in `src/gametest/java/com/frostbound/test/FrozenMobTests.java`. Each `@GameTest` method spawns mobs in an empty test area, freezes them, and checks the stats, damage, frostbite, fire weakness, NBT saving, ice drops and the spawn tag. A failing check fails the build with a message like `max health: expected 35.0 but was 20.0`. To add a test, copy a method, rename it and change the body. Results are also written to `build/gametest/junit.xml`.

## Testing commands

```
/function frostbound:test
```
This spawns a lineup with a frozen row in front and a normal row behind, so you can compare them side by side.

Single summons:
```
/summon minecraft:zombie ~ ~ ~ {Frostbound:1b}
/summon minecraft:skeleton ~ ~ ~ {Frostbound:1b,HandItems:[{id:"minecraft:bow",count:1},{}]}
/summon minecraft:spider ~ ~ ~ {Frostbound:1b}
/summon minecraft:creeper ~ ~ ~ {Frostbound:1b}
/summon minecraft:slime ~ ~ ~ {Frostbound:1b,Size:2}
/summon minecraft:drowned ~ ~ ~ {Frostbound:1b}
/summon minecraft:husk ~ ~ ~ {Frostbound:1b}
/summon minecraft:witch ~ ~ ~ {Frostbound:1b}
/summon minecraft:pillager ~ ~ ~ {Frostbound:1b,HandItems:[{id:"minecraft:crossbow",count:1},{}]}
/summon minecraft:vindicator ~ ~ ~ {Frostbound:1b,HandItems:[{id:"minecraft:iron_axe",count:1},{}]}
```
`{Frostbound:1b}` works on any mob, even ones outside the list (try a cow).

Check the stats:
```
/attribute @e[type=zombie,limit=1,sort=nearest] minecraft:generic.max_health get
/attribute @e[type=zombie,limit=1,sort=nearest] minecraft:generic.movement_speed get
/data get entity @e[type=zombie,limit=1,sort=nearest] Frostbound
```

Test natural spawning:
```
/gamerule frostboundSpawnChance 100       # every eligible mob spawns frozen (use spawn eggs)
/gamerule frostboundColdBiomeChance 100
/gamerule frostboundSpawnChance 8         # back to default
```

## Tweaking

- **Spawn chances:** use the two game rules above. They're saved per world.
- **Which mobs:** edit `src/main/resources/data/frostbound/tags/entity_type/can_be_frozen.json`. A modpack datapack can also add mobs from other mods to the tag `frostbound:can_be_frozen` without rebuilding.
- **Drops:** edit `data/frostbound/loot_table/entities/frozen_mob.json`. A datapack can override this file too.
- **Balance numbers:** all in `FrostStats.java`.
- **Tint colour:** `ICE_TINT` in `LivingEntityRendererMixin.java`.

## How it's put together

- `MobEntityMixin`: adds a synced `frozen` flag to every mob. It saves the flag as `Frostbound` in the mob's NBT (that's what `/summon` reads) and rolls the spawn chance.
- `LivingEntityMixin`: handles the damage multiplier, fire weakness, frostbite, the ice loot, and the death shatter.
- `client/LivingEntityRendererMixin`: makes frozen mobs shiver and tints them.
- `src/gametest`: dev-only test mod (`frostbound-gametest`) with the automated tests.
- `FrostStats`: holds the numbers and adds the attribute modifiers. Modifiers are saved with the mob automatically, so frozen mobs stay frozen across reloads.

# Frostbound: notes for Claude

Fabric 1.21.1 mod (yarn mappings). Requires Trinkets (accessory slots; the necklace slot is used). `README.md` is the full player- and developer-facing doc; keep it up to date with every feature. Run `./gradlew runGametest` after changes; every feature gets GameTests in `src/gametest/java/com/frostbound/test/` (register new classes in `src/gametest/resources/fabric.mod.json`).

## Item passives (tooltip framework)

Items can have **passives**: named bonuses such as copper's Conductivity. Every new passive uses this tooltip layout, drawn right under the item name and above all the vanilla lines:

```
Copper Sword                                 ← item name (vanilla)
                                             ← one blank line
Conductivity                                 ← name: in the passive's colour, not bold, not all caps
5% chance to deal 150% damage to any         ← description: light grey (Formatting.GRAY),
mobs within 1.5 blocks of the targeted mob.     wrapped at ~220px (PassiveTooltips.WRAP_WIDTH)
Can chain endlessly.
                                             ← one blank line
When in Main Hand: ...                       ← vanilla (enchantments, attributes, durability)
```

- **Colour of the name:** pick one that fits the material (copper: `Formatting.GOLD`, orange; green, `Formatting.GREEN`, is the other suggested colour).
- **Description:** short (a sentence, plus a brief extra one if needed, like "Can chain endlessly."), numbers filled in from the config so the tooltip never disagrees with the balance. Write it as "X% chance to ..." and let the wrapper break it.

How to add one:
1. Make an item tag `frostbound:<name>` listing the items that have it (a data pack can add more).
2. Register it: `Passives.register(new Passive("<name>", Formatting.<COLOUR>, TAG, () -> new Object[] {args...}))`, as a constant on the class that implements the effect (see `copper/Conductivity.PASSIVE`). Use `Passive.number(...)` for the numbers (5, 1.5, not 5.0).
3. Lang keys in `en_us.json`: `passive.frostbound.<name>` (the name, in title case: "Cold Iron") and `passive.frostbound.<name>.desc` (the description, `%s` for the args, `%%` for a percent sign).
4. Implement the effect itself in the feature's own package; balance numbers go in `FrostboundConfig` (with a null check in `load`).
5. GameTests: the effect, plus the title/description text (see `CopperTests.conductivityTooltipText`).

`passives/Passive` and `passives/Passives` hold the data (common code); `client/PassiveTooltips` draws them (client only), so nothing else is needed for the tooltip.

**Stat bonuses** (numbers added to a vanilla line, like the imbuement table's armour, toughness, attack damage and durability) are never merged silently: vanilla's line stays as it is and the bonus follows it as a little "(+x)" in the tier's colour, "+8 Armor (+1)" (`FrostTiers.forTooltip` / `bonusText`; durability in `client/imbuement/FrostStars`).

## Models: Blockbench

New models (blocks, items, entities) are Blockbench projects in `src/main/blockbench/{block,item,entity}/<name>.bbmodel`; the build imports them (buildSrc `BlockbenchImport`, task `importBlockbench`) into `build/generated/blockbench`, so never hand-write or commit the generated JSON/PNGs. Block/item projects use the "Java Block/Item" format, entities "Modded Entity"; entity code extends `client/blockbench/BlockbenchEntityModel` and registers with `BlockbenchModels.registerLayer`. Full workflow and limits: `src/main/blockbench/README.md`. When the user adds a `.bbmodel`, wire up the registration/renderer, add a `runScenes` shot to check it in-game, and keep the importer's checks in step with any new mistake found (unit tests in `buildSrc/src/test`).

## Backported vanilla content

Content from later Minecraft versions (e.g. copper gear from 1.21.9) is registered under the `frostbound` namespace with the later version's stats, recipes and loot weights. Textures and sounds are taken from the official Mojang client jar / asset index of that version, not redrawn. Vanilla files that get a new entry (chest loot tables) are copies of the **1.21.1** files with the entry added.

## Boss fights: choreography

The Alpha Wolf fight follows a rule every new move should keep: **tell → action → recovery**. The tell is long enough to react to (0.7–1.8 s) and has a pose (a `WolfAction`, synced to the client and drawn by `client/alpha/PackWolfModel`), a sound, and for big moves a marker on the ground (dust particles: red ring = where it lands, pale line = where it runs). A move can't be cut off by another goal halfway (`canStop() == false`), the recovery is an opening for the player, and the pack holds back during the tell and action (`AlphaWolfEntity.holdsPackBack`), not the recovery: the pack has to keep fighting, so most of the time some wolf should be attacking (`PackHuntingTests.packKeepsUpThePressure` measures it). Balance numbers go in `FrostboundConfig.alphaWolf`. Check how it looks with `gradlew runScenes` (screenshots in `build/scenes/screenshots`; add a step in `src/gametest/.../dev/SceneShots.java`).

The pack should feel like it hunts on purpose, not like mobs chasing you: it surrounds new prey before biting, keeps an even turning ring (`AlphaWolfEntity.ringPoint`), and the Alpha decides who acts (`nextBiter`, the Pincer). New pack behaviour goes through the Alpha the same way, with its geometry in `PackTactics` so GameTests can check it directly.

Models drawn over a vanilla model (the Alpha's armour, `client/alpha/AlphaArmorFeatureRenderer`) are Blockbench entity projects whose bones are named after the vanilla parts, pivot where they do, and copy their pose each frame (`PackWolfModel.copyPoseTo`). Keep the vanilla model in the project as reference cubes with Export off, and a GameTest that the pivots still match (`BlockbenchTests.alphaArmorFitsTheWolfModel`).

The imbuement table's screen texture comes from `tools/ImbuementGuiTexture.java` (same idea: edit the painter and re-run it).

Textures for the Alpha, its armour and drops come from `tools/AlphaWolfTextures.java` (pixel grids and painted layers in one palette); edit it and re-run it rather than editing the PNGs.

GameTest pitfalls met here: mock players (`createMockPlayer`) aren't in the world, so area attacks can't find them (use a still mob as the target); never schedule `runAtTick` from inside a scheduled step (it crashes the runner); an arena built far from the test needs `setChunkForced`, a few ticks before entities show up in lookups, and `setPersistent()` on mobs that would otherwise despawn.

## Trinkets accessories

Accessories (necklaces, rings, ...) are Trinkets items: a `TrinketItem`, the slot's item tag (`data/trinkets/tags/item/<group>/<slot>.json`, e.g. `chest/necklace`) and the slot assigned to players in `data/trinkets/entities/frostbound.json`. Abilities on a key go through a C2S payload and are checked on the server, with the cooldown saved in a player attachment (see `alpha/MarkOfThePack`). In GameTests don't use `createMockCreativeServerPlayerInWorld` (Cardinal Components gets it kicked); use `ImbuementTests.serverPlayer`.

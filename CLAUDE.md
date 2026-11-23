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

## Backported vanilla content

Content from later Minecraft versions (e.g. copper gear from 1.21.9) is registered under the `frostbound` namespace with the later version's stats, recipes and loot weights. Textures and sounds are taken from the official Mojang client jar / asset index of that version, not redrawn. Vanilla files that get a new entry (chest loot tables) are copies of the **1.21.1** files with the entry added.

## Trinkets accessories

Accessories (necklaces, rings, ...) are Trinkets items: a `TrinketItem`, the slot's item tag (`data/trinkets/tags/item/<group>/<slot>.json`, e.g. `chest/necklace`) and the slot assigned to players in `data/trinkets/entities/frostbound.json`. Abilities on a key go through a C2S payload and are checked on the server, with the cooldown saved in a player attachment (see `alpha/MarkOfThePack`). In GameTests don't use `createMockCreativeServerPlayerInWorld` (Cardinal Components gets it kicked); use `ImbuementTests.serverPlayer`.

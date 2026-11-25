# Blockbench models

Models for Frostbound are made in [Blockbench](https://www.blockbench.net/) (free, for Mac, Windows and Linux, or in the browser) and saved here as `.bbmodel` projects. The build turns them into the game's files on its own. There's no export step, and the `.bbmodel` is the only file to keep: its textures are saved inside it.

```
src/main/blockbench/
├── block/    Java Block/Item projects  →  a block model, its textures, a blockstate and a block item model
├── item/     Java Block/Item projects  →  an item model and its textures
└── entity/   Modded Entity projects    →  entity geometry and animations, read by the mod at runtime, and the texture
```

The file name is the id: `block/ice_lantern.bbmodel` is `frostbound:ice_lantern`. Names are lower case letters, digits, `_` and `-`, and so are texture names.

## The loop

1. Make or edit the model in Blockbench and save it here (Ctrl/Cmd+S).
2. Build or run as usual (`./gradlew runClient`, `runGametest`, `build`). The import runs first. On its own: `./gradlew importBlockbench`.
3. With the game already running: `./gradlew processResources`, then F3+T in the game to reload.

If something in a project won't work in the game, the build stops and lists every problem in every file, each with the fix. For example: "rotated 30°, but block models only allow -45, -22.5, 0, 22.5 or 45", or "texture isn't saved inside the project". The generated files go to `build/generated/blockbench/` and aren't committed.

## Blocks and items

New project: **Java Block/Item**. Save it in `block/` or `item/`.

- **Textures.** Paint them in Blockbench or import PNGs. They're saved into the project ("Embed Textures" in Blockbench's settings is on by default; leave it on). Each one becomes `textures/block/<name>.png` (`textures/item/` for items), or the texture's own *Folder* if you set one in its properties.
- **Vanilla textures.** Set the texture's *Namespace* to `minecraft` and *Folder* to `block` (or `item`). It's referred to and not copied, so `ice.png` → `minecraft:block/ice`. Blockbench does this itself when you load a texture from a Minecraft resource pack.
- **Particles.** Right-click a texture → *Use as particle texture* to pick the breaking particles. Otherwise blocks use the first texture.
- **Rotations.** Minecraft 1.21.1 limits a cube to **one axis**, at **-45, -22.5, 0, 22.5 or 45°**. Keep cubes within -16 to 32 on every axis.
- **Items.** Set how it's held in Blockbench's *Display* tab, and leave the parent (*File > Project*) empty. Parents like `item/handheld` make the game draw a flat sprite and drop the cubes, so the importer stops you.
- **In code.** Register the block or item with the same name as the file. A block also gets a plain blockstate and block item model, unless you write your own in `src/main/resources` (e.g. a block that faces different ways), which then wins.

## Entities

New project: **Modded Entity**. Save it in `entity/`. In *File > Project*, leave *Flip Y* on (the default).

- **Bones** (Blockbench's groups) become the model's parts, by name. Give each a unique name of letters, digits and `_`. Cubes outside any group go in a part called `bb_main`. A cube rotated on its own gets a part of its own called `<cube>_r1`, as in Blockbench's Java export.
- **One texture, box UV** (the format's default).
- **Animations** (the *Animate* tab) come with the model. Loop modes "loop" and "once" work, with linear, catmull-rom (smooth) and step keyframes. Bezier curves play as straight lines. Values must be plain numbers; arithmetic like `-90/2` is fine, Molang queries (`q.anim_time`, `math.sin`) aren't.

In code (client side). The model:

```java
public class IceSpriteModel extends BlockbenchEntityModel<IceSpriteEntity> {
    public static final Identifier ID = Frostbound.id("ice_sprite");   // entity/ice_sprite.bbmodel
    private final ModelPart head;

    public IceSpriteModel(ModelPart root) {
        super(root, ID);
        head = part("head");                                // a bone, by its Blockbench name
    }

    @Override
    protected void pose(IceSpriteEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        walk("walk", limbAngle, limbDistance);               // a looping animation, in step with walking
        play(e.attackState, "attack", age);                  // a one-off, started on an AnimationState in the entity
        head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;  // and posing in code on top
    }
}
```

Registering it:

```java
EntityModelLayer layer = BlockbenchModels.registerLayer(IceSpriteModel.ID);
EntityRendererRegistry.register(ICE_SPRITE, ctx -> new BlockbenchEntityRenderer<>(ctx, new IceSpriteModel(ctx.getPart(layer)), 0.4f));
```

The renderer uses the texture that came with the model.

## How it works

- `buildSrc/src/main/java/frostbound/blockbench/`: `BlockbenchImport` reads the projects and writes the files, and `ImportBlockbenchTask` is the Gradle task (wired up in `build.gradle`, for `src/main` and for the test fixtures in `src/gametest/blockbench`).
- The maths follows Blockbench's own Java exporters (`js/formats/java/java_block.ts` and `modded_entity.ts` in the Blockbench source): UVs scaled to 0–16, the entity Y flip and +24, negated x/y rotations, and the animation signs. It reads projects saved by Blockbench 4 and 5. Version 5 turned some animation axes round in its files, and the importer allows for that.
- `com.frostbound.client.blockbench`: `BlockbenchModels` turns the entity data into model parts and vanilla `Animation`s (the format the warden and camel use), and `BlockbenchEntityModel` / `BlockbenchEntityRenderer` are the bases to build on.
- Tests:
  - `buildSrc/src/test` covers every importer error.
  - `BlockbenchTests` (GameTests) checks the fixtures' output against values worked out by hand.
  - `runScenes` loads the fixtures in the client (`dev/BlockbenchClientCheck`) and screenshots them (`19_blockbench_lantern_and_wand`, `20_blockbench_critter`).

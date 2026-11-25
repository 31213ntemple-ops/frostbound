package com.frostbound.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The Blockbench pipeline, end to end: the fixtures in src/gametest/blockbench are imported by the build
 * (buildSrc: BlockbenchImport) and these check what came out, against values worked out by hand from
 * Blockbench's own exporters. (Turning the entity data into model parts and animations needs client
 * classes, which GameTests can't load; dev/BlockbenchClientCheck does that during runScenes.)
 */
public class BlockbenchTests implements FabricGameTest {
	private static final float DEG = (float) (Math.PI / 180.0);

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void blockModelMatchesBlockbenchExport(TestContext ctx) {
		JsonObject model = json("/assets/frostbound/models/block/bb_test_lantern.json");
		JsonObject textures = model.getAsJsonObject("textures");
		check(textures.get("0").getAsString().equals("frostbound:block/bb_test_lantern"), "an embedded texture becomes ours: " + textures);
		check(textures.get("1").getAsString().equals("minecraft:block/ice"), "a minecraft-namespace texture is referred to, not copied");
		check(textures.get("particle").getAsString().equals("minecraft:block/ice"), "the texture marked particle is the particle");
		check(model.get("ambientocclusion").getAsBoolean() == false, "ambient occlusion off carries over");

		JsonArray elements = model.getAsJsonArray("elements");
		check(elements.size() == 3, "three exported cubes (the one with export off is left out), got " + elements.size());
		JsonObject base = elements.get(0).getAsJsonObject();
		JsonObject baseFaces = base.getAsJsonObject("faces");
		check(!baseFaces.has("up"), "a face with no texture is left out");
		check(baseFaces.getAsJsonObject("down").get("cullface").getAsString().equals("down"), "cullface carries over");
		check(baseFaces.getAsJsonObject("north").get("rotation").getAsInt() == 90
				&& baseFaces.getAsJsonObject("north").get("tintindex").getAsInt() == 0, "face rotation and tint carry over");
		check(vec(baseFaces.getAsJsonObject("east").getAsJsonArray("uv")).equals(List.of(0.0, 0.0, 16.0, 16.0)),
				"UVs on a 32 px texture are scaled to Minecraft's 0-16");

		JsonObject body = elements.get(1).getAsJsonObject();
		check(vec(body.getAsJsonArray("from")).equals(List.of(4.5, 1.5, 4.5)) && vec(body.getAsJsonArray("to")).equals(List.of(11.5, 10.5, 11.5)),
				"inflate grows the cube on every side: " + body);
		check(!body.get("shade").getAsBoolean(), "shade off carries over");
		check(body.getAsJsonObject("faces").getAsJsonObject("up").get("texture").getAsString().equals("#1"), "faces point at their texture's id");

		JsonObject fin = elements.get(2).getAsJsonObject();
		JsonObject rotation = fin.getAsJsonObject("rotation");
		check(rotation.get("angle").getAsDouble() == 45 && rotation.get("axis").getAsString().equals("y")
				&& vec(rotation.getAsJsonArray("origin")).equals(List.of(8.0, 10.0, 8.0)) && rotation.get("rescale").getAsBoolean(),
				"element rotation: one axis, angle, origin, rescale: " + rotation);
		check(vec(fin.getAsJsonObject("faces").getAsJsonObject("north").getAsJsonArray("uv")).equals(List.of(4.0, 4.0, 8.0, 12.0)),
				"part-texture UVs scale too");

		JsonObject display = model.getAsJsonObject("display");
		check(display.has("gui") && !display.has("head"), "display slots at their defaults are left out: " + display);
		check(!display.getAsJsonObject("gui").has("translation"), "a zero translation is left out");

		// A block gets a plain blockstate and block item, and its texture file.
		JsonObject state = json("/assets/frostbound/blockstates/bb_test_lantern.json");
		check(state.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString().equals("frostbound:block/bb_test_lantern"),
				"default blockstate");
		check(json("/assets/frostbound/models/item/bb_test_lantern.json").get("parent").getAsString().equals("frostbound:block/bb_test_lantern"),
				"default block item model");
		check(BlockbenchTests.class.getResource("/assets/frostbound/textures/block/bb_test_lantern.png") != null, "the texture is written");
		check(BlockbenchTests.class.getResource("/assets/frostbound/textures/block/ice.png") == null, "vanilla textures aren't copied");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void itemModelFromAnOlderBlockbench(TestContext ctx) {
		// Saved by Blockbench 4: groups inline in the outliner, faces pointing at textures by index.
		JsonObject model = json("/assets/frostbound/models/item/bb_test_wand.json");
		check(!model.has("parent"), "an empty parent is left out");
		check(model.getAsJsonObject("display").getAsJsonObject("firstperson_righthand").has("rotation"), "how it's held carries over");
		check(model.getAsJsonObject("textures").get("0").getAsString().equals("frostbound:item/bb_test_wand"),
				"an item's texture goes in textures/item");
		JsonObject gem = model.getAsJsonArray("elements").get(1).getAsJsonObject();
		check(gem.getAsJsonObject("rotation").get("angle").getAsDouble() == -22.5 && gem.getAsJsonObject("rotation").get("axis").getAsString().equals("z"),
				"negative angles and the z axis carry over");
		check(BlockbenchTests.class.getResource("/assets/frostbound/blockstates/bb_test_wand.json") == null, "items get no blockstate");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void entityModelFollowsBlockbenchExport(TestContext ctx) {
		JsonObject data = json("/assets/frostbound/blockbench/entity/bb_test_critter.json");
		check(data.get("texture").getAsString().equals("frostbound:textures/entity/bb_test_critter.png"), "the texture id");
		check(data.get("texture_width").getAsInt() == 64 && data.get("texture_height").getAsInt() == 32, "the texture size");
		JsonObject main = bone(data, "bb_main"), body = bone(data, "body"), head = bone(data, "head"), fin = bone(data, "fin_r1");
		check(!main.has("parent") && !body.has("parent"), "loose cubes go in bb_main, at the root like body");
		check(head.get("parent").getAsString().equals("body") && fin.get("parent").getAsString().equals("body"), "head and fin_r1 sit in body");

		// Blockbench's Modded Entity maths: y flipped, root bones 24 px down, pivots relative to the parent,
		// x and y rotation negated (in radians), cube corners relative to the bone's pivot.
		check(vec(body.getAsJsonArray("pivot")).equals(List.of(0.0, 18.0, 0.0)), "body pivot (24 - 6): " + body.get("pivot"));
		check(vec(main.getAsJsonArray("pivot")).equals(List.of(0.0, 24.0, 0.0)), "bb_main pivot");
		check(vec(head.getAsJsonArray("pivot")).equals(List.of(0.0, -3.0, -4.0)), "head pivot, relative to body: " + head.get("pivot"));
		close(head.getAsJsonArray("rotation").get(0).getAsFloat(), -10 * DEG, "head pitch (Blockbench 10°, negated)");
		close(fin.getAsJsonArray("rotation").get(2).getAsFloat(), 22.5f * DEG, "a rotated cube gets its own part, with its rotation");
		check(vec(fin.getAsJsonArray("pivot")).equals(List.of(0.0, -3.0, 0.0)), "and its own pivot");

		JsonObject headCube = head.getAsJsonArray("cubes").get(0).getAsJsonObject();
		check(vec(headCube.getAsJsonArray("origin")).equals(List.of(-2.0, -3.0, -4.0)), "head cube corner: " + headCube.get("origin"));
		check(vec(headCube.getAsJsonArray("size")).equals(List.of(4.0, 4.0, 4.0)) && headCube.get("mirror").getAsBoolean()
				&& vec(headCube.getAsJsonArray("uv")).equals(List.of(0.0, 14.0)), "head cube size, mirror and UV");
		check(vec(body.getAsJsonArray("cubes").get(0).getAsJsonObject().getAsJsonArray("origin")).equals(List.of(-3.0, -3.0, -4.0)), "body cube corner");
		check(vec(main.getAsJsonArray("cubes").get(0).getAsJsonObject().getAsJsonArray("origin")).equals(List.of(-1.0, -6.0, 4.0)), "tail corner");
		check(vec(fin.getAsJsonArray("cubes").get(0).getAsJsonObject().getAsJsonArray("origin")).equals(List.of(-0.5, -2.0, -1.0)), "fin corner");
		check(body.getAsJsonArray("cubes").size() == 1, "the rotated fin isn't also in body");

		// The same model saved by Blockbench 4 (groups inline) comes out the same.
		JsonObject old = json("/assets/frostbound/blockbench/entity/bb_test_critter_old.json");
		check(old.get("bones").equals(data.get("bones")), "Blockbench 4 and 5 files give the same bones");
		check(BlockbenchTests.class.getResource("/assets/frostbound/textures/entity/bb_test_critter.png") != null, "the texture is written");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void animationsFollowBlockbenchExport(TestContext ctx) {
		JsonObject animations = json("/assets/frostbound/blockbench/entity/bb_test_critter.json").getAsJsonObject("animations");
		check(animations.keySet().equals(java.util.Set.of("idle", "wave")), "both animations, got " + animations.keySet());
		JsonObject idle = animations.getAsJsonObject("idle");
		check(idle.get("length").getAsDouble() == 2 && idle.get("loop").getAsBoolean(), "idle: 2 s, looping");
		check(!animations.getAsJsonObject("wave").get("loop").getAsBoolean(), "a 'once' animation doesn't loop");

		JsonArray head = channel(idle, "head", "rotation");
		check(head.size() == 2 && frame(head, 1).get("time").getAsDouble() == 1.0, "two head keyframes");
		check(value(head, 1).equals(List.of(-20.0, -10.0, 5.0)), "Blockbench 5 rotation: x and y negated, got " + value(head, 1));
		check(frame(head, 1).get("interpolation").getAsString().equals("catmullrom"), "catmull-rom kept (the game's CUBIC)");

		JsonArray position = channel(idle, "body", "position");
		check(position.size() == 3, "a step keyframe holds until just before the next: " + position.size());
		check(value(position, 0).equals(List.of(-2.0, 2.0, 0.0)), "position: x negated, '1+1' worked out, empty is 0: " + value(position, 0));
		check(Math.abs(frame(position, 1).get("time").getAsDouble() - 1.499) < 1e-6 && value(position, 1).equals(value(position, 0)),
				"the hold ends a moment before the next keyframe");
		check(value(channel(idle, "body", "scale"), 0).equals(List.of(1.5, 1.0, 1.0)), "scale as is");

		JsonArray wave = channel(animations.getAsJsonObject("wave"), "head", "rotation");
		check(wave.size() == 2 && Math.abs(frame(wave, 1).get("time").getAsDouble() - 0.251) < 1e-6
				&& value(wave, 0).get(2) == -10.0 && value(wave, 1).get(2) == 10.0, "before/after values become two keyframes a moment apart");

		// Blockbench 4 files already hold the game's signs.
		JsonObject oldIdle = json("/assets/frostbound/blockbench/entity/bb_test_critter_old.json").getAsJsonObject("animations").getAsJsonObject("idle");
		check(value(channel(oldIdle, "head", "rotation"), 1).equals(List.of(20.0, 10.0, 5.0)), "Blockbench 4 values aren't flipped again");
		ctx.complete();
	}

	private static JsonObject bone(JsonObject data, String name) {
		for (JsonElement bone : data.getAsJsonArray("bones")) {
			if (bone.getAsJsonObject().get("name").getAsString().equals(name)) {
				return bone.getAsJsonObject();
			}
		}
		throw new GameTestException("no bone " + name);
	}

	private static JsonArray channel(JsonObject animation, String bone, String channel) {
		JsonObject bones = animation.getAsJsonObject("bones");
		check(bones.has(bone) && bones.getAsJsonObject(bone).has(channel), "no " + channel + " on " + bone);
		return bones.getAsJsonObject(bone).getAsJsonArray(channel);
	}

	private static JsonObject frame(JsonArray frames, int i) {
		return frames.get(i).getAsJsonObject();
	}

	private static List<Double> value(JsonArray frames, int i) {
		return vec(frame(frames, i).getAsJsonArray("value"));
	}

	private static JsonObject json(String path) {
		try (InputStream stream = BlockbenchTests.class.getResourceAsStream(path)) {
			check(stream != null, path + " wasn't generated");
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new GameTestException(path + ": " + e);
		}
	}

	private static List<Double> vec(JsonArray array) {
		return array.asList().stream().map(JsonElement::getAsDouble).toList();
	}

	private static void close(float actual, float expected, String what) {
		check(Math.abs(actual - expected) < 1e-4f, what + ": expected " + expected + ", got " + actual);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}

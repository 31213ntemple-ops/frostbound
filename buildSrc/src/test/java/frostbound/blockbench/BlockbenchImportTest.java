package frostbound.blockbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The importer's checks: each mistake someone can make in Blockbench gets a message naming the file and
 * the fix. (What good projects turn into is checked end to end by the GameTests in BlockbenchTests.)
 */
class BlockbenchImportTest {
	/** A 1×1 transparent PNG. */
	private static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";
	private static final String RED = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";

	@TempDir
	Path dir;

	private Path source() {
		return dir.resolve("blockbench");
	}

	private void write(String path, JsonObject project) throws IOException {
		Path file = source().resolve(path);
		Files.createDirectories(file.getParent());
		Files.writeString(file, project.toString());
	}

	private Set<String> run() throws IOException {
		return BlockbenchImport.run(source(), dir.resolve("out"), "frostbound", dir.resolve("resources"), message -> {});
	}

	private List<String> problems() {
		return assertThrows(BlockbenchException.class, this::run).problems();
	}

	private static void assertMentions(List<String> problems, String file, String... words) {
		for (String problem : problems) {
			if (problem.contains(file) && List.of(words).stream().allMatch(problem::contains)) {
				return;
			}
		}
		throw new AssertionError("no problem about " + file + " mentioning " + List.of(words) + " in " + problems);
	}

	// --- Projects ---

	private static JsonObject project(String format, String version) {
		JsonObject meta = new JsonObject();
		meta.addProperty("format_version", version);
		meta.addProperty("model_format", format);
		JsonObject project = new JsonObject();
		project.add("meta", meta);
		JsonObject resolution = new JsonObject();
		resolution.addProperty("width", 16);
		resolution.addProperty("height", 16);
		project.add("resolution", resolution);
		project.add("elements", new JsonArray());
		project.add("outliner", new JsonArray());
		project.add("textures", new JsonArray());
		return project;
	}

	private static JsonObject texture(JsonObject project, String name, String source) {
		JsonObject texture = new JsonObject();
		texture.addProperty("name", name);
		texture.addProperty("id", Integer.toString(project.getAsJsonArray("textures").size()));
		texture.addProperty("uuid", "tex-" + name);
		if (source != null) {
			texture.addProperty("source", source);
		}
		project.getAsJsonArray("textures").add(texture);
		return texture;
	}

	private static JsonObject cube(JsonObject project, String name, String textureUuid, double... rotation) {
		JsonObject cube = JsonParser.parseString("{\"name\":\"" + name + "\",\"type\":\"cube\",\"uuid\":\"cube-" + name
				+ "\",\"from\":[0,0,0],\"to\":[16,16,16],\"origin\":[8,8,8],\"uv_offset\":[0,0],\"faces\":{}}").getAsJsonObject();
		JsonArray rot = new JsonArray();
		for (int i = 0; i < 3; i++) {
			rot.add(rotation.length > i ? rotation[i] : 0);
		}
		cube.add("rotation", rot);
		for (String face : List.of("north", "east", "south", "west", "up", "down")) {
			JsonObject f = JsonParser.parseString("{\"uv\":[0,0,16,16]}").getAsJsonObject();
			f.addProperty("texture", textureUuid);
			cube.getAsJsonObject("faces").add(face, f);
		}
		project.getAsJsonArray("elements").add(cube);
		project.getAsJsonArray("outliner").add(cube.get("uuid").getAsString());
		return cube;
	}

	private static JsonObject block() {
		JsonObject project = project("java_block", "5.0");
		texture(project, "stone.png", PNG);
		return project;
	}

	// --- Tests ---

	@Test
	void goodBlockMakesModelTextureBlockstateAndItem() throws IOException {
		JsonObject project = block();
		cube(project, "a", "tex-stone.png");
		write("block/thing.bbmodel", project);
		Set<String> written = run();
		assertTrue(written.containsAll(List.of("assets/frostbound/models/block/thing.json", "assets/frostbound/textures/block/stone.png",
				"assets/frostbound/blockstates/thing.json", "assets/frostbound/models/item/thing.json")), written.toString());
	}

	@Test
	void handWrittenBlockstateAndItemModelWin() throws IOException {
		JsonObject project = block();
		cube(project, "a", "tex-stone.png");
		write("block/thing.bbmodel", project);
		Files.createDirectories(dir.resolve("resources/assets/frostbound/blockstates"));
		Files.writeString(dir.resolve("resources/assets/frostbound/blockstates/thing.json"), "{}");
		Files.createDirectories(dir.resolve("resources/assets/frostbound/models/item"));
		Files.writeString(dir.resolve("resources/assets/frostbound/models/item/thing.json"), "{}");
		Set<String> written = run();
		assertFalse(written.contains("assets/frostbound/blockstates/thing.json"));
		assertFalse(written.contains("assets/frostbound/models/item/thing.json"));
	}

	@Test
	void blockRotationsMustBeOneAxisInJavaSteps() throws IOException {
		JsonObject project = block();
		cube(project, "twisted", "tex-stone.png", 22.5, 45, 0);
		cube(project, "tilted", "tex-stone.png", 0, 30, 0);
		write("block/thing.bbmodel", project);
		List<String> problems = problems();
		assertMentions(problems, "block/thing.bbmodel", "twisted", "more than one axis");
		assertMentions(problems, "block/thing.bbmodel", "tilted", "30", "-45, -22.5, 0, 22.5 or 45");
	}

	@Test
	void facesNeedTexturesAndTexturesNeedPixels() throws IOException {
		JsonObject project = project("java_block", "5.0");
		texture(project, "unsaved.png", null);
		cube(project, "a", "tex-unsaved.png");
		cube(project, "b", "nowhere");
		write("block/thing.bbmodel", project);
		List<String> problems = problems();
		assertMentions(problems, "block/thing.bbmodel", "unsaved.png", "Embed Textures");
		assertMentions(problems, "block/thing.bbmodel", "\"b\"", "no texture");
	}

	@Test
	void vanillaTexturesAreOnlyReferredTo() throws IOException {
		JsonObject project = project("java_block", "5.0");
		JsonObject ice = texture(project, "ice.png", null);
		ice.addProperty("namespace", "minecraft");
		ice.addProperty("folder", "block");
		cube(project, "a", "tex-ice.png");
		write("block/thing.bbmodel", project);
		Set<String> written = run();
		String model = Files.readString(dir.resolve("out/assets/frostbound/models/block/thing.json"));
		assertTrue(model.contains("\"minecraft:block/ice\""), model);
		assertFalse(written.stream().anyMatch(f -> f.endsWith(".png")), written.toString());
	}

	@Test
	void twoModelsCantShareATextureNameWithDifferentPixels() throws IOException {
		JsonObject a = block();
		cube(a, "a", "tex-stone.png");
		write("block/one.bbmodel", a);
		JsonObject b = project("java_block", "5.0");
		texture(b, "stone.png", RED);
		cube(b, "a", "tex-stone.png");
		write("block/two.bbmodel", b);
		assertMentions(problems(), "block/two.bbmodel", "stone.png", "overwrite");
	}

	@Test
	void itemsWithCubesCantUseFlatItemParents() throws IOException {
		JsonObject project = block();
		project.addProperty("parent", "minecraft:item/handheld");
		cube(project, "a", "tex-stone.png");
		write("item/wand.bbmodel", project);
		assertMentions(problems(), "item/wand.bbmodel", "flat sprite", "Display");
	}

	@Test
	void foldersAndNamesAreChecked() throws IOException {
		write("loose.bbmodel", block());
		write("block/Big Rock.bbmodel", block());
		write("entity/not_an_entity.bbmodel", block());
		List<String> problems = problems();
		assertMentions(problems, "loose.bbmodel", "block/, item/ or entity/");
		assertMentions(problems, "Big Rock.bbmodel", "lower case");
		assertMentions(problems, "not_an_entity.bbmodel", "Modded Entity", "Convert Project");
	}

	private static JsonObject entity() {
		JsonObject project = project("modded_entity", "5.0");
		project.getAsJsonObject("meta").addProperty("box_uv", true);
		texture(project, "critter.png", PNG);
		return project;
	}

	@Test
	void entitiesUseOneTextureAndBoxUv() throws IOException {
		JsonObject project = entity();
		texture(project, "extra.png", PNG);
		cube(project, "flat", "tex-critter.png").addProperty("box_uv", false);
		write("entity/critter.bbmodel", project);
		List<String> problems = problems();
		assertMentions(problems, "entity/critter.bbmodel", "2 textures", "merge");
		assertMentions(problems, "entity/critter.bbmodel", "flat", "box UV");
	}

	@Test
	void animationsMustBePlainNumbers() throws IOException {
		JsonObject project = entity();
		cube(project, "body", "tex-critter.png");
		project.add("animations", JsonParser.parseString("""
				[{"name":"spin","loop":"loop","length":1,"animators":{"x":{"name":"bb_main","type":"bone","keyframes":[
				  {"channel":"rotation","time":0,"data_points":[{"x":"math.sin(query.anim_time * 90)","y":"(1+2)*3","z":"-4/2"}]}]}}}]
				""").getAsJsonArray());
		write("entity/critter.bbmodel", project);
		assertMentions(problems(), "entity/critter.bbmodel", "spin", "math.sin", "Molang");
	}

	@Test
	void arithmeticInAnimationsIsWorkedOut() throws IOException {
		JsonObject project = entity();
		cube(project, "body", "tex-critter.png");
		project.add("animations", JsonParser.parseString("""
				[{"name":"spin","loop":"loop","length":1,"animators":{"x":{"name":"bb_main","type":"bone","keyframes":[
				  {"channel":"rotation","time":0,"data_points":[{"x":"2*-3","y":"(1+2)*3","z":"-4/2"}]}]}}}]
				""").getAsJsonArray());
		write("entity/critter.bbmodel", project);
		run();
		JsonObject out = JsonParser.parseString(Files.readString(dir.resolve("out/assets/frostbound/blockbench/entity/critter.json"))).getAsJsonObject();
		JsonArray value = out.getAsJsonObject("animations").getAsJsonObject("spin").getAsJsonObject("bones").getAsJsonObject("bb_main")
				.getAsJsonArray("rotation").get(0).getAsJsonObject().getAsJsonArray("value");
		List<Double> values = new ArrayList<>();
		value.forEach(v -> values.add(v.getAsDouble()));
		assertEquals(List.of(6.0, -9.0, -2.0), values, "Blockbench 5: x and y negated after working them out");
	}

	@Test
	void everyProblemInEveryFileIsReportedAtOnce() throws IOException {
		JsonObject a = block();
		cube(a, "a", "tex-stone.png", 10, 0, 0);
		write("block/one.bbmodel", a);
		JsonObject b = block();
		cube(b, "b", "tex-stone.png", 0, 0, 10);
		write("block/two.bbmodel", b);
		List<String> problems = problems();
		assertMentions(problems, "block/one.bbmodel", "\"a\"");
		assertMentions(problems, "block/two.bbmodel", "\"b\"");
	}
}

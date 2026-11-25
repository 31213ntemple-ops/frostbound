package frostbound.blockbench;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Turns Blockbench projects (.bbmodel) into game files. The folder a project sits in says what it is:
 *
 * <ul>
 *   <li>{@code block/<name>.bbmodel} (format "Java Block/Item"): {@code models/block/<name>.json} and its
 *       textures, plus a plain blockstate and block item model unless the mod already has its own.</li>
 *   <li>{@code item/<name>.bbmodel} (format "Java Block/Item"): {@code models/item/<name>.json} and its textures.</li>
 *   <li>{@code entity/<name>.bbmodel} (format "Modded Entity"): {@code blockbench/entity/<name>.json}, a
 *       baked description of its bones, cubes and animations that the mod reads at runtime
 *       (com.frostbound.client.blockbench), and its texture.</li>
 * </ul>
 *
 * The conversions follow Blockbench's own exporters (js/formats/java/java_block.ts and modded_entity.ts),
 * so a model comes out exactly as Blockbench's "Export" would write it, without anyone exporting by hand.
 * Textures are taken from the project (Blockbench embeds them when it saves), so the .bbmodel is the one
 * file to keep. Every problem in every file is collected and reported at once, with the file and the fix.
 */
public final class BlockbenchImport {
	static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Pattern RESOURCE_PATH = Pattern.compile("[a-z0-9_./-]+");
	private static final List<String> FACES = List.of("north", "east", "south", "west", "up", "down");
	private static final Set<Double> JAVA_ANGLES = Set.of(-45.0, -22.5, 0.0, 22.5, 45.0);
	/** Parents that end in builtin/generated: the game builds those from layer0 textures and drops any elements. */
	private static final Pattern FLAT_ITEM_PARENT = Pattern.compile("(minecraft:)?(item/(generated|handheld\\w*)|builtin/generated)");

	private final Path source;
	private final Path output;
	private final String namespace;
	private final Path existingResources;
	private final Consumer<String> warn;
	private final List<String> errors = new ArrayList<>();
	/** Texture files written so far (resource path → bytes), to catch two models fighting over one name. */
	private final Map<String, byte[]> writtenTextures = new HashMap<>();
	private final Set<String> writtenFiles = new LinkedHashSet<>();

	BlockbenchImport(Path source, Path output, String namespace, Path existingResources, Consumer<String> warn) {
		this.source = source;
		this.output = output;
		this.namespace = namespace;
		this.existingResources = existingResources;
		this.warn = warn;
	}

	/**
	 * Converts every project under {@code source} into {@code output} (a resources root, so files land in
	 * {@code output/assets/<namespace>/...}). {@code existingResources} is the hand-written resources root,
	 * checked so a generated default never shadows a real file. Returns the files written.
	 *
	 * @throws BlockbenchException listing every problem found, if there were any
	 */
	public static Set<String> run(Path source, Path output, String namespace, Path existingResources, Consumer<String> warn) throws IOException {
		BlockbenchImport importer = new BlockbenchImport(source, output, namespace, existingResources, warn);
		importer.importAll();
		return importer.writtenFiles;
	}

	private void importAll() throws IOException {
		if (!Files.isDirectory(source)) {
			return;
		}
		List<Path> projects;
		try (Stream<Path> files = Files.walk(source)) {
			projects = files.filter(p -> p.toString().endsWith(".bbmodel")).sorted().toList();
		}
		Set<String> itemProjects = new LinkedHashSet<>();
		for (Path project : projects) {
			String[] kindAndName = kindAndName(project);
			if (kindAndName != null && kindAndName[0].equals("item")) {
				itemProjects.add(kindAndName[1]);
			}
		}
		for (Path project : projects) {
			String[] kindAndName = kindAndName(project);
			if (kindAndName == null) {
				errors.add(source.relativize(project) + ": put it in block/, item/ or entity/ so the importer knows what it is");
				continue;
			}
			String kind = kindAndName[0], name = kindAndName[1];
			if (!RESOURCE_PATH.matcher(name).matches()) {
				errors.add(source.relativize(project) + ": the file name must be lower case letters, digits, _ and - (it becomes "
						+ namespace + ":" + name + ")");
				continue;
			}
			try {
				JsonObject model = JsonParser.parseString(Files.readString(project, StandardCharsets.UTF_8)).getAsJsonObject();
				Project parsed = new Project(source.relativize(project).toString(), model);
				switch (kind) {
					case "block" -> blockOrItem(parsed, "block", name, !itemProjects.contains(name));
					case "item" -> blockOrItem(parsed, "item", name, false);
					default -> entity(parsed, name);
				}
			} catch (BlockbenchException e) {
				errors.addAll(e.problems());
			} catch (RuntimeException e) {
				errors.add(source.relativize(project) + ": couldn't read it as a Blockbench project (" + e + ")");
			}
		}
		if (!errors.isEmpty()) {
			throw new BlockbenchException(errors);
		}
	}

	/** {kind, name} for {@code <source>/<kind>/<name>.bbmodel} (name may contain folders), or null. */
	private String[] kindAndName(Path project) {
		Path relative = source.relativize(project);
		if (relative.getNameCount() < 2) {
			return null;
		}
		String kind = relative.getName(0).toString();
		if (!List.of("block", "item", "entity").contains(kind)) {
			return null;
		}
		String name = relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
		return new String[] {kind, name.substring(0, name.length() - ".bbmodel".length())};
	}

	// ---------------------------------------------------------------------------------------------
	// Blocks and items (Blockbench's "Java Block/Item" format)
	// ---------------------------------------------------------------------------------------------

	private void blockOrItem(Project project, String kind, String name, boolean defaultItemModel) throws IOException {
		project.requireFormat("java_block", "Java Block/Item");
		List<String> problems = new ArrayList<>();
		JsonObject out = new JsonObject();
		String parent = project.string("parent");
		if (parent != null && !parent.isEmpty()) {
			out.addProperty("parent", parent);
		}
		if (project.model.has("ambientocclusion") && !project.model.get("ambientocclusion").getAsBoolean()) {
			out.addProperty("ambientocclusion", false);
		}
		if (project.model.has("front_gui_light") && project.model.get("front_gui_light").getAsBoolean()) {
			out.addProperty("gui_light", "front");
		}

		Set<Texture> used = new LinkedHashSet<>();
		JsonArray elements = new JsonArray();
		for (JsonObject cube : project.cubesInOrder()) {
			JsonObject element = blockElement(project, cube, used, problems);
			if (element != null) {
				elements.add(element);
			}
		}

		JsonObject textures = new JsonObject();
		Texture particle = project.textures.stream().filter(t -> t.particle).findFirst()
				.orElse(kind.equals("block") && !used.isEmpty() ? used.iterator().next() : null);
		for (Texture texture : project.textures) {
			if (used.contains(texture) || texture == particle) {
				String link = textureLink(project, texture, kind, problems);
				if (used.contains(texture)) {
					textures.addProperty(texture.id, link);
				}
				if (texture == particle) {
					textures.addProperty("particle", link);
				}
			}
		}
		if (textures.size() > 0) {
			out.add("textures", textures);
		}
		if (!elements.isEmpty()) {
			out.add("elements", elements);
		}
		if (!elements.isEmpty() && parent != null && FLAT_ITEM_PARENT.matcher(parent).matches()) {
			problems.add(project.file + ": its parent is " + parent + ", which makes Minecraft draw a flat sprite from \"layer0\" and "
					+ "ignore the cubes (the item shows as nothing). Clear the parent in Blockbench (File > Project) and set how it's held "
					+ "in Display mode instead");
		}
		JsonObject display = display(project.model.get("display"));
		if (display != null) {
			out.add("display", display);
		}
		if (!problems.isEmpty()) {
			throw new BlockbenchException(problems);
		}
		writeJson("assets/" + namespace + "/models/" + kind + "/" + name + ".json", out);

		if (kind.equals("block")) {
			// The simplest blockstate and block item, unless the mod writes its own.
			String blockstate = "assets/" + namespace + "/blockstates/" + name + ".json";
			if (!existsInResources(blockstate)) {
				JsonObject variant = new JsonObject();
				variant.addProperty("model", namespace + ":block/" + name);
				JsonObject variants = new JsonObject();
				variants.add("", variant);
				JsonObject state = new JsonObject();
				state.add("variants", variants);
				writeJson(blockstate, state);
			}
			String itemModel = "assets/" + namespace + "/models/item/" + name + ".json";
			if (defaultItemModel && !existsInResources(itemModel)) {
				JsonObject item = new JsonObject();
				item.addProperty("parent", namespace + ":block/" + name);
				writeJson(itemModel, item);
			}
		}
	}

	private JsonObject blockElement(Project project, JsonObject cube, Set<Texture> used, List<String> problems) {
		String cubeName = Project.stringOr(cube, "name", "cube");
		String where = project.file + ", cube \"" + cubeName + "\"";
		double[] from = Project.vec(cube, "from", 0);
		double[] to = Project.vec(cube, "to", 0);
		double inflate = Project.number(cube, "inflate", 0);
		for (int i = 0; i < 3; i++) {
			from[i] -= inflate;
			to[i] += inflate;
		}
		JsonObject element = new JsonObject();
		if (!cubeName.equals("cube")) {
			element.addProperty("name", cubeName);
		}
		element.add("from", array(from));
		element.add("to", array(to));
		for (int i = 0; i < 3; i++) {
			if (from[i] < -16 || to[i] > 32) {
				problems.add(where + ": reaches past -16..32 on the " + "xyz".charAt(i)
						+ " axis, which Minecraft won't load (scale the model down or split it into more blocks)");
				break;
			}
		}

		double[] rotation = Project.vec(cube, "rotation", 0);
		double[] origin = Project.vec(cube, "origin", 0);
		boolean rescale = cube.has("rescale") && cube.get("rescale").getAsBoolean();
		int rotatedAxes = 0;
		int axis = 1;
		for (int i = 0; i < 3; i++) {
			if (rotation[i] != 0) {
				rotatedAxes++;
				axis = i;
			}
		}
		if (rotatedAxes > 1) {
			problems.add(where + ": rotated on more than one axis; Minecraft 1.21.1 block models can only turn an element around one");
		} else if (rotatedAxes == 1 && !JAVA_ANGLES.contains(rotation[axis])) {
			problems.add(where + ": rotated " + num(rotation[axis]) + "°, but block models only allow -45, -22.5, 0, 22.5 or 45");
		}
		if (rotatedAxes > 0 || rescale) {
			JsonObject rot = new JsonObject();
			rot.addProperty("angle", rotation[axis]);
			rot.addProperty("axis", String.valueOf("xyz".charAt(axis)));
			rot.add("origin", array(origin));
			if (rescale) {
				rot.addProperty("rescale", true);
			}
			element.add("rotation", rot);
		}
		if (cube.has("shade") && !cube.get("shade").getAsBoolean()) {
			element.addProperty("shade", false);
		}

		JsonObject faces = new JsonObject();
		JsonObject sourceFaces = cube.has("faces") ? cube.getAsJsonObject("faces") : new JsonObject();
		for (String side : FACES) {
			if (!sourceFaces.has(side)) {
				continue;
			}
			JsonObject face = sourceFaces.getAsJsonObject(side);
			JsonElement textureRef = face.get("texture");
			if (textureRef == null || textureRef.isJsonNull()) {
				continue; // a face set to "no texture" isn't exported
			}
			Texture texture = project.texture(textureRef);
			if (texture == null) {
				problems.add(where + ": its " + side + " face has no texture (give it one, or set it to transparent)");
				continue;
			}
			used.add(texture);
			JsonObject out = new JsonObject();
			if (!(face.has("enabled") && !face.get("enabled").getAsBoolean()) && face.has("uv")) {
				double[] uv = Project.doubles(face.getAsJsonArray("uv"));
				double width = texture.uvWidth > 0 ? texture.uvWidth : project.resolutionWidth;
				double height = texture.uvHeight > 0 ? texture.uvHeight : project.resolutionHeight;
				for (int i = 0; i < 4; i++) {
					uv[i] = uv[i] * 16 / (i % 2 == 0 ? width : height);
				}
				out.add("uv", array(uv));
			}
			int faceRotation = (int) Project.number(face, "rotation", 0);
			if (faceRotation != 0) {
				out.addProperty("rotation", faceRotation);
			}
			out.addProperty("texture", "#" + texture.id);
			String cullface = Project.stringOr(face, "cullface", "");
			if (!cullface.isEmpty()) {
				out.addProperty("cullface", cullface);
			}
			int tint = (int) Project.number(face, "tint", -1);
			if (tint >= 0) {
				out.addProperty("tintindex", tint);
			}
			faces.add(side, out);
		}
		if (faces.size() == 0) {
			return null;
		}
		element.add("faces", faces);
		return element;
	}

	/** Blockbench's display settings (rotation, translation, scale per slot), leaving out what's at its default. */
	private static JsonObject display(JsonElement display) {
		if (display == null || !display.isJsonObject()) {
			return null;
		}
		JsonObject out = new JsonObject();
		for (Map.Entry<String, JsonElement> slot : display.getAsJsonObject().entrySet()) {
			if (!slot.getValue().isJsonObject()) {
				continue;
			}
			JsonObject settings = slot.getValue().getAsJsonObject();
			JsonObject copy = new JsonObject();
			for (String key : List.of("rotation", "translation", "scale")) {
				if (settings.has(key) && settings.get(key).isJsonArray()) {
					double[] values = Project.doubles(settings.getAsJsonArray(key));
					double neutral = key.equals("scale") ? 1 : 0;
					if (Arrays.stream(values).anyMatch(v -> v != neutral)) {
						copy.add(key, array(values));
					}
				}
			}
			if (copy.size() > 0) {
				out.add(slot.getKey(), copy);
			}
		}
		return out.size() > 0 ? out : null;
	}

	/**
	 * The texture's id in a model ("frostbound:block/table"), writing its image out if it's one of ours:
	 * a texture whose namespace is "minecraft" (or another mod's) is only referred to, anything else is
	 * taken from the project and saved under {@code textures/<folder>/<name>.png} (folder defaults to the kind).
	 */
	private String textureLink(Project project, Texture texture, String defaultFolder, List<String> problems) throws IOException {
		String name = texture.name.replaceAll("\\.\\w{2,8}$", "");
		String folder = texture.folder.isEmpty() ? defaultFolder : texture.folder;
		String path = folder + "/" + name;
		if (!texture.namespace.isEmpty() && !texture.namespace.equals(namespace)) {
			return texture.namespace + ":" + path;
		}
		if (!RESOURCE_PATH.matcher(path).matches()) {
			problems.add(project.file + ": texture \"" + texture.name + "\" needs a lower-case name (letters, digits, _), and so does its folder");
			return namespace + ":" + path;
		}
		byte[] png = texture.png();
		if (png == null) {
			problems.add(project.file + ": texture \"" + texture.name + "\" isn't saved inside the project. In Blockbench turn on "
					+ "Settings > Export > Embed Textures (it's on by default) and save again, or set its namespace to minecraft "
					+ "if it's a vanilla texture");
			return namespace + ":" + path;
		}
		String file = "assets/" + namespace + "/textures/" + path + ".png";
		byte[] earlier = writtenTextures.putIfAbsent(file, png);
		if (earlier != null && !Arrays.equals(earlier, png)) {
			problems.add(project.file + ": texture \"" + texture.name + "\" would overwrite a different texture with the same name ("
					+ file + ") from another model; rename one of them");
			return namespace + ":" + path;
		}
		write(file, png);
		return namespace + ":" + path;
	}

	// ---------------------------------------------------------------------------------------------
	// Entities (Blockbench's "Modded Entity" format)
	// ---------------------------------------------------------------------------------------------

	private void entity(Project project, String name) throws IOException {
		project.requireFormat("modded_entity", "Modded Entity");
		List<String> problems = new ArrayList<>();
		boolean flipY = !project.model.has("modded_entity_flip_y") || project.model.get("modded_entity_flip_y").getAsBoolean();

		// Texture: entity models use exactly one.
		String texture = null;
		if (project.textures.isEmpty()) {
			problems.add(project.file + ": has no texture; entity models need one");
		} else {
			if (project.textures.size() > 1) {
				problems.add(project.file + ": has " + project.textures.size() + " textures, but an entity model uses one; merge them");
			}
			String link = textureLink(project, project.textures.get(0), "entity", problems);
			String[] parts = link.split(":", 2);
			texture = parts[0] + ":textures/" + parts[1] + ".png";
		}

		// Bones: Blockbench's groups, plus "bb_main" for loose cubes and "<cube>_r1" parts for rotated cubes.
		List<Bone> bones = new ArrayList<>();
		List<JsonObject> looseCubes = new ArrayList<>();
		for (Node node : project.outliner) {
			if (node.group != null) {
				collectBones(project, node, null, bones, problems);
			} else if (node.cube != null && project.exported(node.cube)) {
				looseCubes.add(node.cube);
			}
		}
		if (!looseCubes.isEmpty()) {
			Group main = new Group(uniqueName("bb_main", bones), new double[3], new double[3]);
			bones.add(0, new Bone(main, null, looseCubes));
		}
		Map<String, String> boneNames = new HashMap<>();
		Set<String> seen = new LinkedHashSet<>();
		for (Bone bone : bones) {
			if (!seen.add(bone.group.name)) {
				problems.add(project.file + ": two bones are called \"" + bone.group.name + "\"; give each a different name");
			}
			if (bone.group.uuid != null) {
				boneNames.put(bone.group.uuid, bone.group.name);
			}
		}

		JsonArray boneArray = new JsonArray();
		for (Bone bone : List.copyOf(bones)) {
			// Rotated cubes go in a part of their own, as Blockbench's export does.
			Map<String, Bone> subparts = new LinkedHashMap<>();
			List<JsonObject> straight = new ArrayList<>();
			for (JsonObject cube : bone.cubes) {
				if (cube.has("box_uv") && !cube.get("box_uv").getAsBoolean()) {
					problems.add(project.file + ", cube \"" + Project.stringOr(cube, "name", "cube")
							+ "\": uses per-face UV, but entity models need box UV (turn on Box UV for the cube)");
				}
				double[] rotation = Project.vec(cube, "rotation", 0);
				if (Arrays.stream(rotation).allMatch(v -> v == 0)) {
					straight.add(cube);
					continue;
				}
				double[] origin = Project.vec(cube, "origin", 0);
				String key = Arrays.toString(rotation) + Arrays.toString(origin);
				Bone sub = subparts.get(key);
				if (sub == null) {
					String subName = uniqueName(Project.stringOr(cube, "name", "cube") + "_r1", bones);
					sub = new Bone(new Group(subName, origin, rotation), bone.group, new ArrayList<>());
					subparts.put(key, sub);
					bones.add(sub);
				}
				sub.cubes.add(cube);
			}
			boneArray.add(boneJson(new Bone(bone.group, bone.parent, straight), flipY));
			for (Bone sub : subparts.values()) {
				boneArray.add(boneJson(sub, flipY));
			}
		}

		JsonObject out = new JsonObject();
		out.addProperty("texture", texture);
		out.addProperty("texture_width", project.resolutionWidth);
		out.addProperty("texture_height", project.resolutionHeight);
		out.add("bones", boneArray);
		out.add("animations", animations(project, boneNames, problems));
		if (!problems.isEmpty()) {
			throw new BlockbenchException(problems);
		}
		writeJson("assets/" + namespace + "/blockbench/entity/" + name + ".json", out);
	}

	private void collectBones(Project project, Node node, Group parent, List<Bone> bones, List<String> problems) {
		Group group = node.group;
		if (!group.export) {
			return;
		}
		if (!group.name.matches("\\w+")) {
			problems.add(project.file + ": bone \"" + group.name + "\" needs a name of letters, digits and _ only");
		}
		List<JsonObject> cubes = new ArrayList<>();
		Bone bone = new Bone(group, parent, cubes);
		bones.add(bone);
		for (Node child : node.children) {
			if (child.cube != null && project.exported(child.cube)) {
				cubes.add(child.cube);
			} else if (child.group != null) {
				collectBones(project, child, group, bones, problems);
			}
		}
	}

	/** One bone in the game's terms: Blockbench's modded_entity.ts maths, flipped to Minecraft's y-down model space. */
	private static JsonObject boneJson(Bone bone, boolean flipY) {
		Group group = bone.group;
		double[] pivot = group.origin.clone();
		if (bone.parent != null) {
			for (int i = 0; i < 3; i++) {
				pivot[i] -= bone.parent.origin[i];
			}
		}
		pivot[0] *= -1;
		if (flipY) {
			pivot[1] *= -1;
			if (bone.parent == null) {
				pivot[1] += 24;
			}
		}
		JsonObject out = new JsonObject();
		out.addProperty("name", group.name);
		out.add("parent", bone.parent == null ? JsonNull.INSTANCE : new JsonPrimitive(bone.parent.name));
		out.add("pivot", array(pivot));
		out.add("rotation", array(new double[] {Math.toRadians(-group.rotation[0]), Math.toRadians(-group.rotation[1]),
				Math.toRadians(group.rotation[2])}));
		JsonArray cubes = new JsonArray();
		for (JsonObject cube : bone.cubes) {
			double[] from = Project.vec(cube, "from", 0);
			double[] to = Project.vec(cube, "to", 0);
			double[] size = {to[0] - from[0], to[1] - from[1], to[2] - from[2]};
			double[] corner = {
					group.origin[0] - to[0],
					flipY ? -from[1] - size[1] + group.origin[1] : from[1] - group.origin[1],
					from[2] - group.origin[2]};
			JsonObject c = new JsonObject();
			c.add("origin", array(corner));
			c.add("size", array(size));
			c.add("uv", array(Project.vec2(cube, "uv_offset")));
			c.addProperty("inflate", Project.number(cube, "inflate", 0));
			c.addProperty("mirror", cube.has("mirror_uv") && cube.get("mirror_uv").getAsBoolean());
			cubes.add(c);
		}
		out.add("cubes", cubes);
		return out;
	}

	/**
	 * Animations in the game's terms, as Blockbench's Java animation export writes them: rotation in degrees
	 * with x and y negated, position in pixels with x negated (both only for projects saved by Blockbench 5,
	 * which turned those axes round in its files; older files already hold the game's signs), scale as is.
	 * "Step" keyframes hold until just before the next one, and a keyframe with separate before/after values
	 * becomes two a moment apart. Bezier curves aren't supported by the game and become straight lines.
	 */
	private JsonObject animations(Project project, Map<String, String> boneNames, List<String> problems) {
		JsonObject out = new JsonObject();
		if (!project.model.has("animations")) {
			return out;
		}
		boolean flipSigns = project.formatVersionAtLeast(5, 0);
		for (JsonElement element : project.model.getAsJsonArray("animations")) {
			JsonObject animation = element.getAsJsonObject();
			String animName = Project.stringOr(animation, "name", "animation");
			String where = project.file + ", animation \"" + animName + "\"";
			JsonObject anim = new JsonObject();
			anim.addProperty("length", Project.number(animation, "length", 0));
			anim.addProperty("loop", "loop".equals(Project.stringOr(animation, "loop", "once")));
			JsonObject bones = new JsonObject();
			JsonObject animators = animation.has("animators") ? animation.getAsJsonObject("animators") : new JsonObject();
			for (Map.Entry<String, JsonElement> entry : animators.entrySet()) {
				JsonObject animator = entry.getValue().getAsJsonObject();
				String type = Project.stringOr(animator, "type", "bone");
				if (!type.equals("bone") || !animator.has("keyframes")) {
					continue;
				}
				String bone = boneNames.getOrDefault(entry.getKey(), Project.stringOr(animator, "name", null));
				if (bone == null) {
					continue;
				}
				JsonObject channels = new JsonObject();
				for (String channel : List.of("rotation", "position", "scale")) {
					List<JsonObject> keyframes = new ArrayList<>();
					for (JsonElement kf : animator.getAsJsonArray("keyframes")) {
						if (channel.equals(Project.stringOr(kf.getAsJsonObject(), "channel", ""))) {
							keyframes.add(kf.getAsJsonObject());
						}
					}
					if (keyframes.isEmpty()) {
						continue;
					}
					keyframes.sort(Comparator.comparingDouble(kf -> Project.number(kf, "time", 0)));
					JsonArray frames = new JsonArray();
					for (int i = 0; i < keyframes.size(); i++) {
						JsonObject kf = keyframes.get(i);
						double time = Project.number(kf, "time", 0);
						String interpolation = Project.stringOr(kf, "interpolation", "linear");
						if (interpolation.equals("bezier")) {
							warn.accept(where + ": bezier keyframes become straight lines in the game");
						}
						String mapped = interpolation.equals("catmullrom") ? "catmullrom" : "linear";
						JsonArray points = kf.getAsJsonArray("data_points");
						double[] value = point(points.get(0).getAsJsonObject(), channel, flipSigns, where, problems);
						frames.add(frame(time, value, mapped));
						if (points.size() > 1) {
							frames.add(frame(time + 0.001, point(points.get(1).getAsJsonObject(), channel, flipSigns, where, problems), mapped));
						} else if (interpolation.equals("step") && i + 1 < keyframes.size()) {
							frames.add(frame(Project.number(keyframes.get(i + 1), "time", 0) - 0.001, value, "linear"));
						}
					}
					channels.add(channel, frames);
				}
				if (channels.size() > 0) {
					bones.add(bone, channels);
				}
			}
			anim.add("bones", bones);
			out.add(animName, anim);
		}
		return out;
	}

	private static JsonObject frame(double time, double[] value, String interpolation) {
		JsonObject frame = new JsonObject();
		frame.addProperty("time", time);
		frame.add("value", array(value));
		frame.addProperty("interpolation", interpolation);
		return frame;
	}

	private static double[] point(JsonObject point, String channel, boolean flipSigns, String where, List<String> problems) {
		double neutral = channel.equals("scale") ? 1 : 0;
		double[] value = new double[3];
		for (int i = 0; i < 3; i++) {
			String axis = String.valueOf("xyz".charAt(i));
			JsonElement raw = point.get(axis);
			try {
				value[i] = raw == null || raw.isJsonNull() ? neutral : Expression.evaluate(raw.getAsString(), neutral);
			} catch (IllegalArgumentException e) {
				problems.add(where + ": " + channel + " " + axis + " is \"" + raw.getAsString()
						+ "\"; the game can only play plain numbers (and + - * / on them), not Molang queries");
			}
		}
		if (flipSigns) {
			if (channel.equals("position")) {
				value[0] *= -1;
			} else if (channel.equals("rotation")) {
				value[0] *= -1;
				value[1] *= -1;
			}
		}
		return value;
	}

	private static String uniqueName(String wanted, List<Bone> bones) {
		String name = wanted;
		int n = 2;
		while (true) {
			String candidate = name;
			if (bones.stream().noneMatch(b -> b.group.name.equals(candidate))) {
				return candidate;
			}
			name = wanted.replaceAll("_r1$", "") + (wanted.endsWith("_r1") ? "_r" + n : "_" + n);
			n++;
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Output
	// ---------------------------------------------------------------------------------------------

	private boolean existsInResources(String path) {
		return existingResources != null && Files.exists(existingResources.resolve(path));
	}

	private void writeJson(String path, JsonObject json) throws IOException {
		write(path, (GSON.toJson(json) + "\n").getBytes(StandardCharsets.UTF_8));
	}

	private void write(String path, byte[] bytes) throws IOException {
		Path file = output.resolve(path);
		Files.createDirectories(file.getParent());
		Files.write(file, bytes);
		writtenFiles.add(path);
	}

	static JsonArray array(double[] values) {
		JsonArray array = new JsonArray();
		for (double v : values) {
			double rounded = Math.round(v * 1e6) / 1e6;
			if (rounded == Math.rint(rounded) && Math.abs(rounded) < 1e9) {
				array.add((long) rounded);
			} else {
				array.add(rounded);
			}
		}
		return array;
	}

	private static String num(double v) {
		return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
	}

	// ---------------------------------------------------------------------------------------------
	// Reading a .bbmodel
	// ---------------------------------------------------------------------------------------------

	record Group(String name, double[] origin, double[] rotation, String uuid, boolean export) {
		Group(String name, double[] origin, double[] rotation) {
			this(name, origin, rotation, null, true);
		}
	}

	record Bone(Group group, Group parent, List<JsonObject> cubes) {}

	/** An outliner entry: a group with children, or a cube. */
	record Node(Group group, JsonObject cube, List<Node> children) {}

	static final class Texture {
		String name = "texture";
		String id = "0";
		String uuid;
		String folder = "";
		String namespace = "";
		boolean particle;
		double uvWidth;
		double uvHeight;
		String source;

		byte[] png() {
			if (source == null || !source.startsWith("data:image/png;base64,")) {
				return null;
			}
			return Base64.getDecoder().decode(source.substring("data:image/png;base64,".length()));
		}
	}

	/** The parts of a .bbmodel the importer needs, read the same way for Blockbench 4 and 5 files. */
	static final class Project {
		final String file;
		final JsonObject model;
		final String format;
		final String formatVersion;
		final double resolutionWidth;
		final double resolutionHeight;
		final List<Texture> textures = new ArrayList<>();
		final Map<String, JsonObject> elements = new LinkedHashMap<>();
		/** Blockbench 5 keeps groups in their own list; 4 keeps them inline in the outliner. */
		final Map<String, JsonObject> groups = new HashMap<>();
		final List<Node> outliner = new ArrayList<>();

		Project(String file, JsonObject model) {
			this.file = file;
			this.model = model;
			JsonObject meta = model.has("meta") ? model.getAsJsonObject("meta") : new JsonObject();
			this.format = stringOr(meta, "model_format", "java_block");
			this.formatVersion = stringOr(meta, "format_version", stringOr(meta, "format", "4.0"));
			JsonObject resolution = model.has("resolution") ? model.getAsJsonObject("resolution") : new JsonObject();
			this.resolutionWidth = number(resolution, "width", 16);
			this.resolutionHeight = number(resolution, "height", 16);
			if (model.has("textures")) {
				int index = 0;
				for (JsonElement element : model.getAsJsonArray("textures")) {
					JsonObject t = element.getAsJsonObject();
					Texture texture = new Texture();
					texture.name = stringOr(t, "name", "texture_" + index);
					texture.id = stringOr(t, "id", Integer.toString(index));
					texture.uuid = stringOr(t, "uuid", null);
					texture.folder = stringOr(t, "folder", "");
					texture.namespace = stringOr(t, "namespace", "");
					texture.particle = t.has("particle") && t.get("particle").getAsBoolean();
					texture.uvWidth = number(t, "uv_width", 0);
					texture.uvHeight = number(t, "uv_height", 0);
					texture.source = stringOr(t, "source", null);
					textures.add(texture);
					index++;
				}
			}
			if (model.has("elements")) {
				for (JsonElement element : model.getAsJsonArray("elements")) {
					JsonObject e = element.getAsJsonObject();
					elements.put(stringOr(e, "uuid", "element_" + elements.size()), e);
				}
			}
			if (model.has("groups")) {
				for (JsonElement element : model.getAsJsonArray("groups")) {
					JsonObject g = element.getAsJsonObject();
					groups.put(stringOr(g, "uuid", ""), g);
				}
			}
			if (model.has("outliner")) {
				for (JsonElement entry : model.getAsJsonArray("outliner")) {
					Node node = node(entry);
					if (node != null) {
						outliner.add(node);
					}
				}
			}
		}

		private Node node(JsonElement entry) {
			if (entry.isJsonPrimitive()) {
				JsonObject element = elements.get(entry.getAsString());
				if (element == null) {
					return null;
				}
				String type = stringOr(element, "type", "cube");
				return type.equals("cube") ? new Node(null, element, List.of()) : null;
			}
			JsonObject inline = entry.getAsJsonObject();
			String uuid = stringOr(inline, "uuid", "");
			// Blockbench 5: the outliner holds {uuid, children}; the group's fields are in "groups".
			JsonObject fields = groups.getOrDefault(uuid, inline);
			boolean export = !fields.has("export") || fields.get("export").getAsBoolean();
			Group group = new Group(stringOr(fields, "name", "group"), vec(fields, "origin", 0), vec(fields, "rotation", 0), uuid, export);
			List<Node> children = new ArrayList<>();
			if (inline.has("children")) {
				for (JsonElement child : inline.getAsJsonArray("children")) {
					Node node = node(child);
					if (node != null) {
						children.add(node);
					}
				}
			}
			return new Node(group, null, children);
		}

		void requireFormat(String wanted, String label) {
			if (!format.equals(wanted)) {
				throw new BlockbenchException(List.of(file + ": is a \"" + format + "\" project, but this folder needs \"" + label
						+ "\" (in Blockbench: File > Convert Project, or start a new " + label + " model)"));
			}
		}

		boolean formatVersionAtLeast(int major, int minor) {
			String[] parts = formatVersion.split("\\.");
			int maj = Integer.parseInt(parts[0]);
			int min = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
			return maj > major || (maj == major && min >= minor);
		}

		/** Every exported cube in outliner order (the order Blockbench writes elements in). */
		List<JsonObject> cubesInOrder() {
			List<JsonObject> cubes = new ArrayList<>();
			collect(outliner, cubes);
			return cubes;
		}

		private void collect(List<Node> nodes, List<JsonObject> into) {
			for (Node node : nodes) {
				if (node.cube != null && exported(node.cube)) {
					into.add(node.cube);
				} else if (node.group != null && node.group.export) {
					collect(node.children, into);
				}
			}
		}

		boolean exported(JsonObject cube) {
			return !cube.has("export") || cube.get("export").getAsBoolean();
		}

		/** The texture a face points at: by uuid (Blockbench 4.x and 5) or by index (older files). */
		Texture texture(JsonElement ref) {
			if (ref == null || ref.isJsonNull()) {
				return null;
			}
			JsonPrimitive primitive = ref.getAsJsonPrimitive();
			if (primitive.isBoolean()) {
				return null;
			}
			if (primitive.isNumber()) {
				int index = primitive.getAsInt();
				return index >= 0 && index < textures.size() ? textures.get(index) : null;
			}
			String key = primitive.getAsString();
			for (Texture texture : textures) {
				if (key.equals(texture.uuid)) {
					return texture;
				}
			}
			for (Texture texture : textures) {
				if (key.equals(texture.id)) {
					return texture;
				}
			}
			return null;
		}

		String string(String key) {
			return stringOr(model, key, null);
		}

		static String stringOr(JsonObject object, String key, String fallback) {
			JsonElement value = object.get(key);
			return value == null || value.isJsonNull() ? fallback : value.getAsString();
		}

		static double number(JsonObject object, String key, double fallback) {
			JsonElement value = object.get(key);
			if (value == null || value.isJsonNull()) {
				return fallback;
			}
			try {
				return value.getAsDouble();
			} catch (NumberFormatException e) {
				return fallback;
			}
		}

		static double[] vec(JsonObject object, String key, double fallback) {
			JsonElement value = object.get(key);
			if (value == null || !value.isJsonArray()) {
				return new double[] {fallback, fallback, fallback};
			}
			return doubles(value.getAsJsonArray());
		}

		static double[] vec2(JsonObject object, String key) {
			JsonElement value = object.get(key);
			if (value == null || !value.isJsonArray()) {
				return new double[] {0, 0};
			}
			return doubles(value.getAsJsonArray());
		}

		static double[] doubles(JsonArray array) {
			double[] values = new double[array.size()];
			for (int i = 0; i < values.length; i++) {
				values[i] = array.get(i).getAsDouble();
			}
			return values;
		}
	}

	/** Plain numbers with + - * / and brackets: what an animation value can be once it has to be baked. */
	static final class Expression {
		private final String text;
		private int pos;

		private Expression(String text) {
			this.text = text.replace(" ", "");
		}

		static double evaluate(String text, double empty) {
			if (text.isBlank()) {
				return empty;
			}
			Expression e = new Expression(text);
			double value = e.sum();
			if (e.pos != e.text.length()) {
				throw new IllegalArgumentException(text);
			}
			return value;
		}

		private double sum() {
			double value = product();
			while (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
				char op = text.charAt(pos++);
				double next = product();
				value = op == '+' ? value + next : value - next;
			}
			return value;
		}

		private double product() {
			double value = unary();
			while (pos < text.length() && (text.charAt(pos) == '*' || text.charAt(pos) == '/')) {
				char op = text.charAt(pos++);
				double next = unary();
				value = op == '*' ? value * next : value / next;
			}
			return value;
		}

		private double unary() {
			if (pos < text.length() && (text.charAt(pos) == '-' || text.charAt(pos) == '+')) {
				char op = text.charAt(pos++);
				double value = unary();
				return op == '-' ? -value : value;
			}
			if (pos < text.length() && text.charAt(pos) == '(') {
				pos++;
				double value = sum();
				if (pos >= text.length() || text.charAt(pos) != ')') {
					throw new IllegalArgumentException(text);
				}
				pos++;
				return value;
			}
			int start = pos;
			while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
				pos++;
			}
			if (start == pos) {
				throw new IllegalArgumentException(text);
			}
			return Double.parseDouble(text.substring(start, pos).toLowerCase(Locale.ROOT));
		}
	}
}

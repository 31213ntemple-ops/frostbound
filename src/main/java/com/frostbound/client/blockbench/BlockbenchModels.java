package com.frostbound.client.blockbench;

import com.frostbound.Frostbound;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.animation.Animation;
import net.minecraft.client.render.entity.animation.AnimationHelper;
import net.minecraft.client.render.entity.animation.Keyframe;
import net.minecraft.client.render.entity.animation.Transformation;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entity models made in Blockbench. The build turns {@code src/main/blockbench/entity/<name>.bbmodel} into
 * {@code assets/frostbound/blockbench/entity/<name>.json} (buildSrc: BlockbenchImport); this reads that file
 * into what the game uses:
 *
 * <ul>
 *   <li>{@link #registerLayer}: the model's geometry as an entity model layer (bones become model parts, by name).</li>
 *   <li>{@link #animations}: its Blockbench animations as vanilla {@link Animation}s, played with
 *       {@link BlockbenchEntityModel#play} like the warden's or the camel's.</li>
 *   <li>{@link #texture}: the texture that came with it.</li>
 * </ul>
 *
 * Models are read through the resource manager and dropped on every resource reload, so after re-running
 * the import (gradlew importBlockbench, or any build) F3+T in a running game picks up the new version.
 *
 * A model id is {@code frostbound:<name>} for {@code entity/<name>.bbmodel}.
 */
public final class BlockbenchModels {
	private static final Map<Identifier, JsonObject> CACHE = new ConcurrentHashMap<>();
	private static final Map<Identifier, Map<String, Animation>> ANIMATIONS = new ConcurrentHashMap<>();

	private BlockbenchModels() {}

	public static void init() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Frostbound.id("blockbench_models");
			}

			@Override
			public void reload(ResourceManager manager) {
				CACHE.clear();
				ANIMATIONS.clear();
			}
		});
	}

	/** The layer of {@code model} (id "frostbound:<name>", layer "main"), built from its file whenever entity models load. */
	public static EntityModelLayer registerLayer(Identifier model) {
		EntityModelLayer layer = new EntityModelLayer(model, "main");
		EntityModelLayerRegistry.registerModelLayer(layer, () -> texturedModelData(model));
		return layer;
	}

	/** Where the importer puts a model's data: {@code assets/<ns>/blockbench/entity/<path>.json}. */
	public static Identifier file(Identifier model) {
		return model.withPath(path -> "blockbench/entity/" + path + ".json");
	}

	/** The texture the model was made with ({@code <ns>:textures/entity/<name>.png} unless its folder was changed). */
	public static Identifier texture(Identifier model) {
		return Identifier.of(data(model).get("texture").getAsString());
	}

	/** Its geometry: one model part per Blockbench bone (and per rotated cube), named as in Blockbench. */
	public static TexturedModelData texturedModelData(Identifier model) {
		return texturedModelData(data(model));
	}

	public static TexturedModelData texturedModelData(JsonObject data) {
		ModelData modelData = new ModelData();
		Map<String, ModelPartData> parts = new LinkedHashMap<>();
		for (JsonElement element : data.getAsJsonArray("bones")) {
			JsonObject bone = element.getAsJsonObject();
			String name = bone.get("name").getAsString();
			JsonElement parentName = bone.get("parent");
			ModelPartData parent = parentName == null || parentName.isJsonNull() ? modelData.getRoot() : parts.get(parentName.getAsString());
			if (parent == null) {
				throw new IllegalStateException("Blockbench bone " + name + " comes before its parent " + parentName);
			}
			ModelPartBuilder builder = ModelPartBuilder.create();
			for (JsonElement c : bone.getAsJsonArray("cubes")) {
				JsonObject cube = c.getAsJsonObject();
				float[] origin = floats(cube.getAsJsonArray("origin"));
				float[] size = floats(cube.getAsJsonArray("size"));
				float[] uv = floats(cube.getAsJsonArray("uv"));
				boolean mirror = cube.get("mirror").getAsBoolean();
				builder.uv(Math.round(uv[0]), Math.round(uv[1])).mirrored(mirror)
						.cuboid(origin[0], origin[1], origin[2], size[0], size[1], size[2], new Dilation(cube.get("inflate").getAsFloat()))
						.mirrored(false);
			}
			float[] pivot = floats(bone.getAsJsonArray("pivot"));
			float[] rotation = floats(bone.getAsJsonArray("rotation"));
			parts.put(name, parent.addChild(name, builder,
					ModelTransform.of(pivot[0], pivot[1], pivot[2], rotation[0], rotation[1], rotation[2])));
		}
		return TexturedModelData.of(modelData, data.get("texture_width").getAsInt(), data.get("texture_height").getAsInt());
	}

	/** Its animations by their Blockbench names, as the game's own animation format. */
	public static Map<String, Animation> animations(Identifier model) {
		return ANIMATIONS.computeIfAbsent(model, id -> animations(data(id)));
	}

	public static Map<String, Animation> animations(JsonObject data) {
		Map<String, Animation> animations = new LinkedHashMap<>();
		JsonObject all = data.getAsJsonObject("animations");
		for (Map.Entry<String, JsonElement> entry : all.entrySet()) {
			JsonObject animation = entry.getValue().getAsJsonObject();
			Animation.Builder builder = Animation.Builder.create(animation.get("length").getAsFloat());
			if (animation.get("loop").getAsBoolean()) {
				builder.looping();
			}
			for (Map.Entry<String, JsonElement> bone : animation.getAsJsonObject("bones").entrySet()) {
				for (Map.Entry<String, JsonElement> channel : bone.getValue().getAsJsonObject().entrySet()) {
					Transformation.Target target = switch (channel.getKey()) {
						case "rotation" -> Transformation.Targets.ROTATE;
						case "position" -> Transformation.Targets.TRANSLATE;
						default -> Transformation.Targets.SCALE;
					};
					List<Keyframe> keyframes = new ArrayList<>();
					for (JsonElement f : channel.getValue().getAsJsonArray()) {
						JsonObject frame = f.getAsJsonObject();
						float[] v = floats(frame.getAsJsonArray("value"));
						Vector3f vector = switch (channel.getKey()) {
							case "rotation" -> AnimationHelper.createRotationalVector(v[0], v[1], v[2]);
							case "position" -> AnimationHelper.createTranslationalVector(v[0], v[1], v[2]);
							default -> AnimationHelper.createScalingVector(v[0], v[1], v[2]);
						};
						Transformation.Interpolation interpolation = "catmullrom".equals(frame.get("interpolation").getAsString())
								? Transformation.Interpolations.CUBIC : Transformation.Interpolations.LINEAR;
						keyframes.add(new Keyframe(frame.get("time").getAsFloat(), vector, interpolation));
					}
					builder.addBoneAnimation(bone.getKey(), new Transformation(target, keyframes.toArray(Keyframe[]::new)));
				}
			}
			animations.put(entry.getKey(), builder.build());
		}
		return Collections.unmodifiableMap(animations);
	}

	/** The model's data file, read once per resource reload. */
	public static JsonObject data(Identifier model) {
		return CACHE.computeIfAbsent(model, BlockbenchModels::read);
	}

	private static JsonObject read(Identifier model) {
		Identifier file = file(model);
		try (Reader reader = open(file)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException("Can't read Blockbench model " + model + " (" + file
					+ "). Is there a src/main/blockbench/entity/" + model.getPath() + ".bbmodel, and has the build run?", e);
		}
	}

	private static Reader open(Identifier file) throws IOException {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.getResourceManager() != null) {
			Optional<Resource> resource = client.getResourceManager().getResource(file);
			if (resource.isPresent()) {
				return resource.get().getReader();
			}
		}
		// Before the client's resources are up (and in GameTests, which have none): straight from the jar.
		InputStream stream = BlockbenchModels.class.getResourceAsStream("/assets/" + file.getNamespace() + "/" + file.getPath());
		if (stream == null) {
			throw new IOException("not found");
		}
		return new InputStreamReader(stream, StandardCharsets.UTF_8);
	}

	private static float[] floats(JsonArray array) {
		float[] values = new float[array.size()];
		for (int i = 0; i < values.length; i++) {
			values[i] = array.get(i).getAsFloat();
		}
		return values;
	}
}

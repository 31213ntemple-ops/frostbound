package com.frostbound.client;

import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The model of a snowy plant: the plant's own model plus the vanilla 2-pixel snow
 * layer. Everything except the quads (particles, lighting, transforms) comes from the
 * plant, so breaking a snowy flower still shows flower particles.
 */
final class SnowyPlantModel implements BakedModel {
	private final BakedModel plant;
	private final BakedModel snow;

	SnowyPlantModel(BakedModel plant, BakedModel snow) {
		this.plant = plant;
		this.snow = snow;
	}

	/**
	 * In-world rendering. Grass, ferns and flowers are drawn with a random per-block
	 * offset (so meadows don't look like a grid), and the renderer shifts the whole
	 * model by it. The plant keeps that offset, but the snow is moved back by the same
	 * amount so it lines up exactly with the block and the snow next to it.
	 */
	@Override
	public boolean isVanillaAdapter() {
		return false;
	}

	@Override
	public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos, Supplier<Random> random,
			RenderContext context) {
		plant.emitBlockQuads(world, state, pos, random, context);

		Vec3d offset = state.getModelOffset(world, pos);
		if (offset.equals(Vec3d.ZERO)) {
			snow.emitBlockQuads(world, state, pos, random, context);
			return;
		}
		float dx = (float) offset.x, dy = (float) offset.y, dz = (float) offset.z;
		context.pushTransform(quad -> {
			for (int i = 0; i < 4; i++) {
				quad.pos(i, quad.x(i) - dx, quad.y(i) - dy, quad.z(i) - dz);
			}
			return true;
		});
		snow.emitBlockQuads(world, state, pos, random, context);
		context.popTransform();
	}

	/** Used by vanilla code paths that don't know the position (no offset applies there). */
	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, Random random) {
		List<BakedQuad> plantQuads = plant.getQuads(state, face, random);
		List<BakedQuad> snowQuads = snow.getQuads(null, face, random);
		if (snowQuads.isEmpty()) return plantQuads;
		if (plantQuads.isEmpty()) return snowQuads;
		List<BakedQuad> all = new ArrayList<>(plantQuads.size() + snowQuads.size());
		all.addAll(plantQuads);
		all.addAll(snowQuads);
		return all;
	}

	@Override
	public boolean useAmbientOcclusion() {
		return plant.useAmbientOcclusion();
	}

	@Override
	public boolean hasDepth() {
		return plant.hasDepth();
	}

	@Override
	public boolean isSideLit() {
		return plant.isSideLit();
	}

	@Override
	public boolean isBuiltin() {
		return false;
	}

	@Override
	public Sprite getParticleSprite() {
		return plant.getParticleSprite();
	}

	@Override
	public ModelTransformation getTransformation() {
		return plant.getTransformation();
	}

	@Override
	public ModelOverrideList getOverrides() {
		return plant.getOverrides();
	}
}

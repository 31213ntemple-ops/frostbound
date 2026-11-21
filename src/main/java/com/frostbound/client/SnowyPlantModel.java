package com.frostbound.client;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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

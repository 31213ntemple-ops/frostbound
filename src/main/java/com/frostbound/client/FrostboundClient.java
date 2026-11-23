package com.frostbound.client;

import com.frostbound.client.campfire.FireStarterMinigame;
import com.frostbound.client.vessels.VesselClient;
import com.frostbound.environment.SnowyPlants;
import com.frostbound.items.FrostboundItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.ModelRotation;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Client entrypoint. */
public class FrostboundClient implements ClientModInitializer {
	private static final Identifier SNOW_LAYER = Identifier.ofVanilla("block/snow_height2");

	@Override
	public void onInitializeClient() {
		// Thrown ice balls draw as their item, like snowballs.
		EntityRendererRegistry.register(FrostboundItems.ICE_BALL_ENTITY, FlyingItemEntityRenderer::new);

		// Hearts: the frozen heart in the HUD, vessel tooltips, the totem-style pop.
		VesselClient.init();

		// The fire-starting minigame: the bar under the crosshair while you use a fire starter.
		FireStarterMinigame.init();

		// Snowy plants: wrap the model of every "snowy=true" plant state so it also draws a
		// thin snow layer. Vanilla's blockstate files already match the snowy states (they
		// don't mention the property), so no new model or blockstate files are needed.
		ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
			ModelIdentifier id = context.topLevelId();
			if (model == null || id == null || !isSnowyPlantState(id)) {
				return model;
			}
			BakedModel snow = context.baker().bake(SNOW_LAYER, ModelRotation.X0_Y0);
			return snow == null ? model : new SnowyPlantModel(model, snow);
		}));
	}

	private static boolean isSnowyPlantState(ModelIdentifier id) {
		String variant = id.getVariant();
		if (!variant.contains("snowy=true") || variant.contains("half=upper")) {
			return false;
		}
		Block block = Registries.BLOCK.get(id.id());
		return SnowyPlants.isSnowloggable(block);
	}
}

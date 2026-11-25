package com.frostbound.client;

import com.frostbound.client.addons.AddonSlotTooltips;
import com.frostbound.client.alpha.AlphaWolfClient;
import com.frostbound.habanero.Habaneros;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;
import com.frostbound.client.blockbench.BlockbenchModels;
import com.frostbound.client.campfire.FireStarterMinigame;
import com.frostbound.client.campfire.WarmthRing;
import com.frostbound.client.imbuement.FrostParticles;
import com.frostbound.client.imbuement.FrostRenderLayers;
import com.frostbound.client.imbuement.FrostStars;
import com.frostbound.client.imbuement.ImbuementScreen;
import com.frostbound.imbuement.Imbuement;
import com.frostbound.client.vessels.VesselClient;
import com.frostbound.environment.SnowyPlants;
import com.frostbound.items.FrostboundItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
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
		// The ring round lit campfires marking where they warm you (with Tough As Nails).
		WarmthRing.init();

		// The Alpha Wolf and its pack, the Mark of the Pack key, Draining Bite's hollow hearts (in InGameHudMixin).
		AlphaWolfClient.init();

		// Entity models made in Blockbench (src/main/blockbench/entity): dropped on reload so F3+T picks up a re-import.
		BlockbenchModels.init();

		// Item passives (e.g. copper's Conductivity) at the top of tooltips.
		PassiveTooltips.init();

		// The imbuement table's screen; Cold/Icy/Frozen armour's stars, frost (mixin.imbuement.client) and snowflakes.
		HandledScreens.register(Imbuement.SCREEN_HANDLER, ImbuementScreen::new);
		FrostStars.init();
		// Add-on slots ("[🔥] [ ]") under the stats; after FrostStars so they follow the durability line.
		AddonSlotTooltips.init();
		// The habanero crop and wild bush are cut out of their textures.
		BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), Habaneros.HABANERO_CROP, Habaneros.WILD_HABANERO);
		FrostParticles.init();
		FrostRenderLayers.init();

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

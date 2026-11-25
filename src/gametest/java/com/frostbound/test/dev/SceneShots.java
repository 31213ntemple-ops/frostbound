package com.frostbound.test.dev;

import com.frostbound.Frostbound;
import com.frostbound.alpha.AlphaHide;
import com.frostbound.alpha.AlphaWolfEntity;
import com.frostbound.alpha.AlphaWolves;
import com.frostbound.alpha.MarkOfThePack;
import com.frostbound.alpha.PackWolfEntity;
import com.frostbound.alpha.WolfAction;
import com.frostbound.alpha.den.WolfDenLayout;
import com.frostbound.alpha.den.WolfDenPiece;
import com.frostbound.client.blockbench.BlockbenchModels;
import com.frostbound.addons.FlameExtract;
import com.frostbound.addons.GearAddons;
import com.frostbound.copper.CopperGear;
import com.frostbound.habanero.Habaneros;
import com.frostbound.imbuement.FrostTier;
import com.frostbound.imbuement.FrostTiers;
import com.frostbound.imbuement.Imbuement;
import com.frostbound.imbuement.ImbuementScreenHandler;
import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.impl.resource.loader.ModResourcePackUtil;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.animation.AnimationHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.WolfVariants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dev tool, not shipped: with {@code -Dfrostbound.scenes=true} (gradlew runScenes) the client makes a
 * flat world, stages the Alpha Wolf content (the den by day and night, every pose, the armour and the
 * necklace), the imbuement table (its menu, frosty gear's tooltips) and the Blockbench pipeline's test
 * fixtures (after checking them with BlockbenchClientCheck), saves a screenshot of
 * each into build/scenes/screenshots, and quits. For checking how
 * things look without playing through a fight.
 */
public class SceneShots implements ClientModInitializer {
	private record Step(int delay, Consumer<MinecraftServer> serverAction, Consumer<MinecraftClient> clientAction, String shot) {}

	private final List<Step> steps = new ArrayList<>();
	private boolean started;
	private int step;
	private int ticks;
	private BlockPos den = BlockPos.ORIGIN;
	private int ground;

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("frostbound.scenes")) {
			return;
		}
		this.plan();
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(MinecraftClient client) {
		if (!this.started) {
			if (client.currentScreen instanceof TitleScreen) {
				this.started = true;
				client.options.pauseOnLostFocus = false;
				LevelInfo info = new LevelInfo("frostbound-scenes-" + System.currentTimeMillis(), GameMode.CREATIVE, false, Difficulty.NORMAL,
						true, new GameRules(), ModResourcePackUtil.createDefaultDataConfiguration());
				client.createIntegratedServerLoader().createAndStart(info.getLevelName(), info, new GeneratorOptions(12345L, false, false),
						registries -> registries.get(RegistryKeys.WORLD_PRESET).entryOf(WorldPresets.FLAT).value().createDimensionsRegistryHolder(),
						client.currentScreen);
			}
			return;
		}
		if (client.world == null || client.player == null || client.getServer() == null || this.step >= this.steps.size()) {
			return;
		}
		Step current = this.steps.get(this.step);
		if (this.ticks == 0) {
			MinecraftServer server = client.getServer();
			server.execute(() -> current.serverAction.accept(server));
			current.clientAction.accept(client);
		}
		this.ticks++;
		if (this.ticks >= current.delay) {
			if (current.shot != null) {
				ScreenshotRecorder.saveScreenshot(client.runDirectory, current.shot + ".png", client.getFramebuffer(),
						message -> Frostbound.LOGGER.info("Scene: {}", message.getString()));
			}
			this.step++;
			this.ticks = 0;
			if (this.step >= this.steps.size()) {
				client.scheduleStop();
			}
		}
	}

	private void add(int delay, Consumer<MinecraftServer> server, Consumer<MinecraftClient> client, String shot) {
		this.steps.add(new Step(delay, server, client, shot));
	}

	private static void command(MinecraftServer server, String command) {
		server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(), command);
	}

	private static ServerPlayerEntity player(MinecraftServer server) {
		return server.getPlayerManager().getPlayerList().get(0);
	}

	private void plan() {
		// Settle in: daytime, no weather, no HUD.
		this.add(60, server -> {
			ServerWorld world = server.getOverworld();
			command(server, "gamerule doDaylightCycle false");
			command(server, "gamerule doWeatherCycle false");
			command(server, "gamerule doMobSpawning false");
			command(server, "time set 6000");
			command(server, "weather clear");
			this.ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, 0, 40) - 1;
			this.den = new BlockPos(0, this.ground, 40);
			WolfDenPiece.place(world, this.den, WolfDenLayout.generate(4242L), BlockBox.infinite(), true);
		}, client -> client.options.hudHidden = true, null);

		// The den from above, then from its edge (the pack asleep).
		this.add(60, server -> this.camera(server, 0, 18, 22, 0, 40), client -> { }, "01_den_overview");
		this.add(40, server -> this.camera(server, 7, 4, 32, 35, 20), client -> { }, "02_den_sleeping_pack");
		this.add(40, server -> this.camera(server, 3.0, 3.6, 37.0, 45, 30), client -> { }, "03_alpha_asleep_closeup");
		this.add(40, server -> {
			command(server, "time set 18000");
			this.camera(server, -12, 7, 30, -55, 18);
		}, client -> { }, "04_den_at_night");

		// Every pose, side by side (frozen in place with NoAI), on an open patch of ground.
		this.add(60, server -> {
			command(server, "time set 6000");
			ServerWorld world = server.getOverworld();
			WolfAction[] poses = {WolfAction.SLEEPING, WolfAction.CROUCH, WolfAction.POUNCE_WINDUP, WolfAction.LEAPING,
					WolfAction.CHARGE_WINDUP, WolfAction.CHARGING, WolfAction.STUNNED, WolfAction.HOWLING, WolfAction.RECOVERING};
			for (int i = 0; i < poses.length; i++) {
				PackWolfEntity wolf = AlphaWolves.PACK_WOLF.create(world);
				wolf.refreshPositionAndAngles(-20 + i * 2.5, this.ground + 1, -10, 90.0f, 0.0f);
				wolf.setHeadYaw(90.0f);
				wolf.setBodyYaw(90.0f);
				wolf.setVariant(world.getRegistryManager().get(RegistryKeys.WOLF_VARIANT).getEntry(WolfVariants.SNOWY).orElseThrow());
				wolf.setAiDisabled(true);
				wolf.setInSittingPose(poses[i].sits());
				wolf.setAction(poses[i]);
				world.spawnEntity(wolf);
			}
			this.camera(server, -10, 2.2, -16, 0, 8);
		}, client -> { }, "05_poses_side");
		this.add(30, server -> this.camera(server, -10, 3.5, -3, 180, 18), client -> { }, "06_poses_front");

		// The Alpha awake, Bloodied (embers), and mid-howl.
		this.add(50, server -> {
			ServerWorld world = server.getOverworld();
			AlphaWolfEntity alpha = AlphaWolves.ALPHA_WOLF.create(world);
			alpha.refreshPositionAndAngles(20, this.ground + 1, -10, 200.0f, 0.0f);
			alpha.setHeadYaw(200.0f);
			alpha.setBodyYaw(200.0f);
			alpha.setVariant(world.getRegistryManager().get(RegistryKeys.WOLF_VARIANT).getEntry(WolfVariants.BLACK).orElseThrow());
			alpha.setAiDisabled(true);
			alpha.damage(world.getDamageSources().magic(), 50.0f); // below half: Bloodied
			world.spawnEntity(alpha);
			AlphaWolfEntity howler = AlphaWolves.ALPHA_WOLF.create(world);
			howler.refreshPositionAndAngles(24, this.ground + 1, -10, 150.0f, 0.0f);
			howler.setBodyYaw(150.0f);
			howler.setAiDisabled(true);
			howler.setInSittingPose(true);
			howler.setAction(WolfAction.HOWLING);
			world.spawnEntity(howler);
			this.camera(server, 22, 2.4, -14.5, 20, 10);
		}, client -> { }, "07_alpha_bloodied_and_howling");

		// The armour on a stand and on the player, with the necklace (third person, from the front).
		this.add(40, server -> {
			ServerWorld world = server.getOverworld();
			ArmorStandEntity stand = new ArmorStandEntity(world, 40.5, this.ground + 1, -9.5);
			stand.setYaw(180.0f);
			stand.equipStack(EquipmentSlot.HEAD, new ItemStack(AlphaHide.HELMET));
			stand.equipStack(EquipmentSlot.CHEST, new ItemStack(AlphaHide.CHESTPLATE));
			stand.equipStack(EquipmentSlot.LEGS, new ItemStack(AlphaHide.LEGGINGS));
			stand.equipStack(EquipmentSlot.FEET, new ItemStack(AlphaHide.BOOTS));
			world.spawnEntity(stand);
			this.camera(server, 40.5, 2.6, -12.5, 0, 12);
		}, client -> { }, "08_armor_stand");
		this.add(40, server -> {
			ServerPlayerEntity player = player(server);
			player.changeGameMode(GameMode.SURVIVAL);
			player.equipStack(EquipmentSlot.HEAD, new ItemStack(AlphaHide.HELMET));
			player.equipStack(EquipmentSlot.CHEST, new ItemStack(AlphaHide.CHESTPLATE));
			player.equipStack(EquipmentSlot.LEGS, new ItemStack(AlphaHide.LEGGINGS));
			player.equipStack(EquipmentSlot.FEET, new ItemStack(AlphaHide.BOOTS));
			TrinketsApi.getTrinketComponent(player).ifPresent(trinkets -> trinkets.getInventory().get("chest").get("necklace")
					.setStack(0, new ItemStack(MarkOfThePack.MARK_OF_THE_PACK)));
			player.teleport(server.getOverworld(), 50.5, this.ground + 1, -10.5, 0.0f, 10.0f);
		}, client -> client.options.setPerspective(Perspective.THIRD_PERSON_FRONT), "09_player_front");
		this.add(30, server -> player(server).teleport(server.getOverworld(), 50.5, this.ground + 1, -10.5, 45.0f, 25.0f),
				client -> { }, "10_player_front_angle");
		this.add(30, server -> player(server).teleport(server.getOverworld(), 50.5, this.ground + 1, -10.5, 0.0f, 10.0f),
				client -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK), "11_player_back");

		// The imbuement table's menu: empty; a Cold → Icy ring caught mid-glow, twice; and a Cold helmet alone in
		// the centre (the ladder ticks Cold, Icy is next, Frozen is frosted over).
		BlockPos[] table = new BlockPos[1];
		this.add(20, server -> {
			ServerWorld world = server.getOverworld();
			table[0] = new BlockPos(70, this.ground + 1, -10);
			world.setBlockState(table[0], Imbuement.TABLE.getDefaultState());
			ServerPlayerEntity player = player(server);
			player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
			player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
			player.equipStack(EquipmentSlot.LEGS, ItemStack.EMPTY);
			player.equipStack(EquipmentSlot.FEET, ItemStack.EMPTY);
			player.changeGameMode(GameMode.SURVIVAL);
			player.teleport(world, 70.5, this.ground + 1, -7.5, 180.0f, 30.0f);
			player.openHandledScreen(Imbuement.TABLE.getDefaultState().createScreenHandlerFactory(world, table[0]));
		}, client -> client.options.setPerspective(Perspective.FIRST_PERSON), "12_imbuement_menu_empty");
		this.add(14, server -> {
			ItemStack cold = new ItemStack(Items.IRON_SWORD);
			FrostTiers.setTier(cold, FrostTier.COLD);
			fillGrid(player(server), cold, new ItemStack(Items.PACKED_ICE, 4));
		}, client -> { }, "13_imbuement_menu_cold_to_icy");
		this.add(6, server -> { }, client -> { }, "14_imbuement_menu_cold_to_icy_later");
		this.add(14, server -> {
			ItemStack partial = new ItemStack(Items.LEATHER_HELMET);
			FrostTiers.setTier(partial, FrostTier.COLD);
			fillGrid(player(server), partial, ItemStack.EMPTY);
		}, client -> { }, "15_imbuement_menu_ladder");

		// Frosty gear's tooltips, side by side: the (+x) after vanilla's numbers and the durability line.
		this.add(10, server -> {
			ServerPlayerEntity player = player(server);
			fillGrid(player, ItemStack.EMPTY, ItemStack.EMPTY);
			player.closeHandledScreen();
		}, client -> { }, null);
		this.add(30, server -> { }, client -> client.setScreen(new TooltipBoard(0)), "16_tooltip_frozen_diamond_chestplate");
		this.add(10, server -> { }, client -> client.setScreen(new TooltipBoard(1)), "17_tooltip_cold_iron_sword");
		this.add(10, server -> { }, client -> client.setScreen(new TooltipBoard(2)), "18_tooltip_icy_copper_sword");

		// The Blockbench pipeline's test fixtures (src/gametest/blockbench): the client check, the lantern block
		// in the world with the wand in hand, and the critter at rest and mid-animation.
		this.add(10, server -> { }, client -> {
			client.setScreen(null);
			BlockbenchClientCheck.run();
		}, null);
		this.add(40, server -> {
			ServerWorld world = server.getOverworld();
			BlockPos lantern = new BlockPos(90, this.ground + 1, -10);
			world.setBlockState(lantern, BlockbenchFixtures.LANTERN.getDefaultState());
			world.setBlockState(lantern.east(2), net.minecraft.block.Blocks.ICE.getDefaultState());
			ServerPlayerEntity player = player(server);
			player.changeGameMode(GameMode.SURVIVAL);
			player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(BlockbenchFixtures.WAND));
			player.teleport(world, 91.0, this.ground + 1, -12.6, 20.0f, 30.0f);
		}, client -> client.options.hudHidden = false, "19_blockbench_lantern_and_wand");
		this.add(20, server -> { }, client -> {
			client.options.hudHidden = true;
			client.setScreen(new CritterBoard());
		}, "20_blockbench_critter");

		// The Alpha's spiked armour (alpha_wolf_armor.bbmodel) on a row of Alphas in different poses, from
		// the side, the front and the back, then close up.
		this.add(50, server -> {
			ServerWorld world = server.getOverworld();
			WolfAction[] poses = ALPHA_POSES;
			for (int i = 0; i < poses.length; i++) {
				AlphaWolfEntity alpha = AlphaWolves.ALPHA_WOLF.create(world);
				alpha.refreshPositionAndAngles(110 + i * 3.5, this.ground + 1, -10, 90.0f, 0.0f);
				alpha.setHeadYaw(90.0f);
				alpha.setBodyYaw(90.0f);
				alpha.setVariant(world.getRegistryManager().get(RegistryKeys.WOLF_VARIANT).getEntry(WolfVariants.BLACK).orElseThrow());
				alpha.setAiDisabled(true);
				alpha.setInSittingPose(poses[i].sits());
				alpha.setAction(poses[i]);
				world.spawnEntity(alpha);
			}
			this.camera(server, 110 + (poses.length - 1) * 1.75, 2.6, -18, 0, 10);
		}, client -> client.setScreen(null), "21_alpha_armor_side");
		this.add(30, server -> this.camera(server, 110 + (ALPHA_POSES.length - 1) * 1.75, 3.5, -1, 180, 14), client -> { },
				"22_alpha_armor_other_side");
		this.add(30, server -> this.camera(server, 106.0, 2.3, -10, -90, 10), client -> { }, "23_alpha_armor_front");
		this.add(30, server -> this.camera(server, 107.6, 2.6, -12.4, -45, 25), client -> { }, "24_alpha_armor_closeup");
		this.add(30, server -> this.camera(server, 112.4, 2.8, -7.6, 135, 28), client -> { }, "25_alpha_armor_closeup_back");

		// A live hunt (AI on) round a pig that can't be hurt: the pack spreads into its ring, creeping; then
		// the Alpha barks a Pincer, two wolves crouch with pale lines to where they'll land, and leap.
		// Bites are held off so the ring stays tidy for the shot.
		AlphaWolfEntity[] hunter = new AlphaWolfEntity[1];
		this.add(110, server -> {
			ServerWorld world = server.getOverworld();
			PigEntity pig = EntityType.PIG.create(world);
			pig.refreshPositionAndAngles(160.5, this.ground + 1, -9.5, 0.0f, 0.0f);
			pig.setAiDisabled(true);
			pig.setInvulnerable(true);
			world.spawnEntity(pig);
			AlphaWolfEntity alpha = AlphaWolves.ALPHA_WOLF.create(world);
			alpha.refreshPositionAndAngles(150.5, this.ground + 1, -9.5, 0.0f, 0.0f);
			alpha.setVariant(world.getRegistryManager().get(RegistryKeys.WOLF_VARIANT).getEntry(WolfVariants.BLACK).orElseThrow());
			alpha.setPersistent();
			world.spawnEntity(alpha);
			for (PackWolfEntity wolf : alpha.spawnPack(6)) {
				wolf.setLungeCooldown(100000);
			}
			alpha.setCallCooldown(100000);
			alpha.setMoveCooldown(100000);
			alpha.setLungeCooldown(100000);
			alpha.setTarget(pig);
			alpha.setPincerCooldown(100000);
			hunter[0] = alpha;
			this.camera(server, 160.5, 15, -9.5, 0, 90);
		}, client -> { }, "26_hunt_ring_from_above");
		this.add(30, server -> this.camera(server, 151.5, 3.2, -18.5, -40, 16), client -> { }, "27_hunt_ring_stalking");
		this.add(16, server -> hunter[0].setPincerCooldown(0), client -> { }, "28_pincer_order");
		this.add(14, server -> { }, client -> { }, "29_pincer_lines");
		this.add(16, server -> { }, client -> { }, "30_pincer_leap");

		// The campfire warmth ring (needs Tough As Nails, which runScenes loads): a raised step on one side and
		// snow on the other, so the ring has to follow the ground. From above, the side, and from inside it.
		this.add(30, server -> {
			ServerWorld world = server.getOverworld();
			BlockPos fire = new BlockPos(200, this.ground + 1, -10);
			for (int x = 203; x <= 212; x++) {
				for (int z = -20; z <= 0; z++) {
					world.setBlockState(new BlockPos(x, this.ground + 1, z), Blocks.STONE.getDefaultState());
				}
			}
			for (int x = 188; x <= 196; x++) {
				for (int z = -20; z <= 0; z++) {
					world.setBlockState(new BlockPos(x, this.ground + 1, z), Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 2));
				}
			}
			world.setBlockState(fire, Blocks.CAMPFIRE.getDefaultState());
			this.camera(server, 200.5, 11, -9.5, 0, 90);
		}, client -> client.options.setPerspective(Perspective.FIRST_PERSON), "31_campfire_ring_from_above");
		this.add(30, server -> this.camera(server, 200.5, 3.5, -24.5, 0, 18), client -> { }, "32_campfire_ring_side");
		this.add(30, server -> this.camera(server, 202.5, 1.6, -6.5, 150, 30), client -> { }, "33_campfire_ring_inside");

		// Habaneros: the crop's four stages on farmland (ages 0, 2, 4 and 7) and wild bushes on stone, grass and snow.
		this.add(30, server -> {
			ServerWorld world = server.getOverworld();
			int[] ages = {0, 2, 4, 7};
			for (int i = 0; i < ages.length; i++) {
				BlockPos soil = new BlockPos(230 + i, this.ground, -10);
				world.setBlockState(soil, Blocks.FARMLAND.getDefaultState());
				world.setBlockState(soil.up(), ((net.minecraft.block.CropBlock) Habaneros.HABANERO_CROP).withAge(ages[i]));
			}
			net.minecraft.block.Block[] grounds = {Blocks.STONE, Blocks.GRASS_BLOCK, Blocks.SNOW_BLOCK};
			for (int i = 0; i < grounds.length; i++) {
				BlockPos soil = new BlockPos(230 + i, this.ground, -8);
				world.setBlockState(soil, grounds[i].getDefaultState());
				world.setBlockState(soil.up(), Habaneros.WILD_HABANERO.getDefaultState());
			}
			this.camera(server, 231.5, 2.2, -13.0, 0, 30);
		}, client -> client.setScreen(null), "34_habanero_crop_and_wild");

		// Add-on slots under the stats ("[🔥] [ ]"), a spicy dish and the flame extract itself.
		this.add(20, server -> { }, client -> client.setScreen(new TooltipBoard(3)), "35_tooltip_flame_extract_chestplate");
		this.add(10, server -> { }, client -> client.setScreen(new TooltipBoard(4)), "36_tooltip_empty_addon_slots_sword");
		this.add(10, server -> { }, client -> client.setScreen(new TooltipBoard(5)), "37_tooltip_spicy_dish");
		this.add(10, server -> { }, client -> client.setScreen(new TooltipBoard(6)), "38_tooltip_flame_extract");
	}

	/** The poses the armour shots show, left to right. */
	private static final WolfAction[] ALPHA_POSES = {WolfAction.NONE, WolfAction.STALKING, WolfAction.CROUCH, WolfAction.LEAPING,
			WolfAction.SLEEPING, WolfAction.COMMANDING, WolfAction.ROARING};

	/** Puts {@code center} in the middle of the open imbuement menu and a copy of {@code ring} around it. */
	private static void fillGrid(ServerPlayerEntity player, ItemStack center, ItemStack ring) {
		if (player.currentScreenHandler instanceof ImbuementScreenHandler menu) {
			for (int i = 0; i < 9; i++) {
				menu.getSlot(ImbuementScreenHandler.GRID_START + i).setStack(i == ImbuementScreenHandler.CENTER ? center.copy() : ring.copy());
			}
		}
	}

	/** Draws the Blockbench test critter three times: modelled pose, the idle animation at 1 s, and from behind. */
	private static final class CritterBoard extends Screen {
		CritterBoard() {
			super(Text.empty());
		}

		@Override
		public void render(DrawContext context, int mouseX, int mouseY, float delta) {
			super.render(context, mouseX, mouseY, delta);
			Identifier texture = BlockbenchModels.texture(BlockbenchClientCheck.CRITTER);
			float[][] views = {{150, 0}, {150, 1000}, {-30, 0}};
			String[] labels = {"modelled", "idle at 1 s", "from behind"};
			for (int i = 0; i < views.length; i++) {
				BlockbenchClientCheck.Preview model = new BlockbenchClientCheck.Preview(
						BlockbenchModels.texturedModelData(BlockbenchClientCheck.CRITTER).createModel());
				if (views[i][1] > 0) {
					AnimationHelper.animate(model, BlockbenchModels.animations(BlockbenchClientCheck.CRITTER).get("idle"), (long) views[i][1],
							1.0f, new Vector3f());
				}
				int cx = this.width * (2 * i + 1) / 6, cy = this.height * 2 / 3;
				MatrixStack matrices = context.getMatrices();
				matrices.push();
				matrices.translate(cx, cy, 150);
				matrices.scale(90, 90, -90);
				matrices.multiply(new Quaternionf().rotateZ((float) Math.PI).mul(new Quaternionf().rotateX(-20 * (float) (Math.PI / 180))));
				// What an entity renderer does: turn to face the yaw, flip into model space, feet on the ground.
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - views[i][0]));
				matrices.scale(-1, -1, 1);
				matrices.translate(0, -1.501, 0);
				DiffuseLighting.method_34742();
				model.render(matrices, context.getVertexConsumers().getBuffer(RenderLayer.getEntityCutoutNoCull(texture)),
						LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, -1);
				context.draw();
				DiffuseLighting.enableGuiDepthLighting();
				matrices.pop();
				context.drawCenteredTextWithShadow(this.textRenderer, labels[i], cx, cy + 20, 0xFFFFFF);
			}
		}
	}

	/** A screen that just draws the tooltip of one of a few items (frosty gear, add-on slots, a spicy dish). */
	private static final class TooltipBoard extends Screen {
		private final int which;

		TooltipBoard(int which) {
			super(Text.empty());
			this.which = which;
		}

		@Override
		public void render(DrawContext context, int mouseX, int mouseY, float delta) {
			super.render(context, mouseX, mouseY, delta);
			ItemStack plate = new ItemStack(Items.DIAMOND_CHESTPLATE);
			FrostTiers.setTier(plate, FrostTier.FROZEN);
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			FrostTiers.setTier(sword, FrostTier.COLD);
			ItemStack copper = new ItemStack(CopperGear.COPPER_SWORD);
			FrostTiers.setTier(copper, FrostTier.ICY);
			ItemStack lined = GearAddons.withAdded(new ItemStack(Items.IRON_CHESTPLATE), FlameExtract.ADDON);
			FrostTiers.setTier(lined, FrostTier.FROZEN);
			ItemStack plain = new ItemStack(Items.IRON_SWORD);
			ItemStack stew = new ItemStack(net.minecraft.registry.Registries.ITEM.getOrEmpty(Identifier.of("farmersdelight", "beef_stew"))
					.orElse(Items.RABBIT_STEW));
			stew.set(Habaneros.SPICY, net.minecraft.util.Unit.INSTANCE);
			ItemStack[] stacks = {plate, sword, copper, lined, plain, stew, new ItemStack(Habaneros.FLAME_EXTRACT)};
			context.drawItemTooltip(this.textRenderer, stacks[this.which], 20, 30);
		}
	}

	/** Puts the (spectating) player's camera at a spot relative to the ground, looking along yaw/pitch. */
	private void camera(MinecraftServer server, double x, double y, double z, float yaw, float pitch) {
		ServerPlayerEntity player = player(server);
		player.changeGameMode(GameMode.SPECTATOR);
		player.teleport(server.getOverworld(), x, this.ground + y, z, yaw, pitch);
	}
}

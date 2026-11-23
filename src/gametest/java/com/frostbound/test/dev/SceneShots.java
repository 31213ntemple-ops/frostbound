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
import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.impl.resource.loader.ModResourcePackUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.WolfVariants;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
 * necklace), saves a screenshot of each into build/scenes/screenshots, and quits. For checking how
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
			alpha.damage(world.getDamageSources().magic(), 45.0f);
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
	}

	/** Puts the (spectating) player's camera at a spot relative to the ground, looking along yaw/pitch. */
	private void camera(MinecraftServer server, double x, double y, double z, float yaw, float pitch) {
		ServerPlayerEntity player = player(server);
		player.changeGameMode(GameMode.SPECTATOR);
		player.teleport(server.getOverworld(), x, this.ground + y, z, yaw, pitch);
	}
}

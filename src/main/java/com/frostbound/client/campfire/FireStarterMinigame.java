package com.frostbound.client.campfire;

import com.frostbound.campfire.Campfires;
import com.frostbound.campfire.FireStarterAttemptPayload;
import com.frostbound.campfire.FireStarterItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * The fire-starting minigame, client side. While the player holds right-click with a fire starter
 * on a campfire, a marker sweeps back and forth along a bar under the crosshair; each left-click
 * is judged here (is the marker in the green?) and sent to the server, which keeps the real
 * streak and lights the fire (see {@link Campfires}).
 *
 * Clicks are read straight off the attack key, every frame (from the HUD) and at the start of
 * every tick, before vanilla throws away attack presses made while an item is in use. The marker
 * moves on wall-clock time, so a click is judged where the marker was drawn when it was made.
 */
public final class FireStarterMinigame {
	private static final int BAR_WIDTH = 121;
	private static final int BAR_HEIGHT = 7;
	/** Width of the green zone, as a fraction of the bar. */
	private static final float ZONE = 0.22f;
	/** Milliseconds for the marker to cross the bar once, by streak (it speeds up as you go). */
	private static final long[] SWEEP_MS = {900, 760, 620};
	/** After a click, how long the bar flashes the result and ignores clicks. */
	private static final long FEEDBACK_MS = 350;

	private static final int BACKGROUND = 0xC0101010;
	private static final int TRACK = 0xFF3A2A1C;
	private static final int GREEN = 0xFF4CC24C;
	private static final int GREEN_HIT = 0xFFB8FF8A;
	private static final int MISS = 0xFFD23C2C;
	private static final int MARKER = 0xFFFFF4D0;
	private static final int PIP_EMPTY = 0xFF4A3A2A;
	private static final int PIP_LIT = 0xFFFF9A2A;

	private static final Random RANDOM = Random.create();

	private static boolean active;
	private static int streak;
	private static float zoneStart;
	/** When the marker started its current run (it restarts after each click). */
	private static long sweepStart;
	private static long feedbackUntil;
	private static boolean lastHit;

	private FireStarterMinigame() {}

	public static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			update(client);
			pollClicks(client);
		});
		ClientTickEvents.END_CLIENT_TICK.register(FireStarterMinigame::smoke);
		HudRenderCallback.EVENT.register((context, tickCounter) -> {
			MinecraftClient client = MinecraftClient.getInstance();
			update(client);
			pollClicks(client);
			render(context, client);
		});
	}

	private static boolean playing(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		return player != null && player.isUsingItem() && player.getActiveItem().getItem() instanceof FireStarterItem;
	}

	/** Starts a fresh game when the player starts using a fire starter, and ends it when they stop. */
	private static void update(MinecraftClient client) {
		boolean now = playing(client);
		if (now && !active) {
			streak = 0;
			feedbackUntil = 0;
			newRound();
		}
		active = now;
	}

	private static void newRound() {
		zoneStart = 0.08f + RANDOM.nextFloat() * (0.84f - ZONE);
		sweepStart = Util.getMeasuringTimeMs();
	}

	/** Where the marker is, 0 (left) to 1 (right). */
	private static float marker(long now) {
		long sweep = SWEEP_MS[Math.min(streak, SWEEP_MS.length - 1)];
		long t = (now - sweepStart) % (sweep * 2);
		float f = t / (float) sweep;
		return f <= 1.0f ? f : 2.0f - f;
	}

	private static void pollClicks(MinecraftClient client) {
		if (!active || client.currentScreen != null) {
			return;
		}
		while (client.options.attackKey.wasPressed()) {
			long now = Util.getMeasuringTimeMs();
			if (now < feedbackUntil || streak >= Campfires.STREAK_TO_LIGHT) {
				continue; // still showing the last result, or waiting for the fire to catch
			}
			float m = marker(now);
			boolean hit = m >= zoneStart && m <= zoneStart + ZONE;
			click(client, hit, now);
		}
	}

	private static void click(MinecraftClient client, boolean hit, long now) {
		ClientPlayerEntity player = client.player;
		BlockPos pos = Campfires.target(player);
		lastHit = hit;
		feedbackUntil = now + FEEDBACK_MS;
		streak = hit ? streak + 1 : 0;
		if (pos != null) {
			if (hit) {
				player.getWorld().playSound(player, pos, SoundEvents.BLOCK_WOOD_HIT, SoundCategory.PLAYERS,
						0.8f, 1.2f + 0.2f * streak);
			} else {
				player.getWorld().playSound(player, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS,
						0.25f, 1.8f);
			}
		}
		if (streak < Campfires.STREAK_TO_LIGHT) {
			newRound();
		}
		ClientPlayNetworking.send(new FireStarterAttemptPayload(hit));
	}

	/** Wisps of smoke from the logs while you work, more as the streak builds. */
	private static void smoke(MinecraftClient client) {
		if (!active || client.player == null || client.isPaused()) {
			return;
		}
		BlockPos pos = Campfires.target(client.player);
		if (pos == null || RANDOM.nextFloat() > 0.15f + 0.25f * streak) {
			return;
		}
		client.player.getWorld().addParticle(streak > 0 ? ParticleTypes.SMOKE : ParticleTypes.WHITE_SMOKE,
				pos.getX() + 0.3 + RANDOM.nextDouble() * 0.4, pos.getY() + 0.35, pos.getZ() + 0.3 + RANDOM.nextDouble() * 0.4,
				0.0, 0.02 + 0.01 * streak, 0.0);
	}

	private static void render(DrawContext context, MinecraftClient client) {
		if (!active || client.options.hudHidden) {
			return;
		}
		long now = Util.getMeasuringTimeMs();
		int x = (context.getScaledWindowWidth() - BAR_WIDTH) / 2;
		int y = context.getScaledWindowHeight() / 2 + 26;
		boolean feedback = now < feedbackUntil;

		// Frame and track.
		context.fill(x - 2, y - 2, x + BAR_WIDTH + 2, y + BAR_HEIGHT + 2, BACKGROUND);
		context.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, feedback && !lastHit ? MISS : TRACK);

		// Green zone.
		int zoneLeft = x + Math.round(zoneStart * BAR_WIDTH);
		int zoneRight = x + Math.round((zoneStart + ZONE) * BAR_WIDTH);
		if (!(feedback && !lastHit)) {
			context.fill(zoneLeft, y, zoneRight, y + BAR_HEIGHT, feedback ? GREEN_HIT : GREEN);
		}

		// Marker (hidden while the result shows, and once the fire is catching).
		if (!feedback && streak < Campfires.STREAK_TO_LIGHT) {
			float m = marker(now);
			int mx = x + MathHelper.clamp(Math.round(m * (BAR_WIDTH - 1)), 0, BAR_WIDTH - 1);
			context.fill(mx - 1, y - 2, mx + 2, y + BAR_HEIGHT + 2, MARKER);
		}

		// Streak pips.
		int pip = 5;
		int gap = 4;
		int pipsWidth = Campfires.STREAK_TO_LIGHT * pip + (Campfires.STREAK_TO_LIGHT - 1) * gap;
		int px = (context.getScaledWindowWidth() - pipsWidth) / 2;
		int py = y + BAR_HEIGHT + 5;
		for (int i = 0; i < Campfires.STREAK_TO_LIGHT; i++) {
			int left = px + i * (pip + gap);
			context.fill(left - 1, py - 1, left + pip + 1, py + pip + 1, BACKGROUND);
			context.fill(left, py, left + pip, py + pip, i < streak ? PIP_LIT : PIP_EMPTY);
		}

		// Hint.
		Text hint = Text.translatable("frostbound.fire_starter.minigame");
		context.drawCenteredTextWithShadow(client.textRenderer, hint, context.getScaledWindowWidth() / 2,
				y - 13, 0xFFD8D0C0);
	}
}

package com.frostbound.client.vessels;

import com.frostbound.Frostbound;
import com.frostbound.vessels.FrozenHeart;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * How the frozen heart looks in the HUD (positioning is in InGameHudMixin).
 *
 *  - Whole: an ice heart with a dark red heart frozen inside it, in a frosted outline.
 *  - Cracked (half or less left): the same heart, split by cracks and missing a chip.
 *  - It flashes white when it's hit and when it forms, like vanilla hearts do.
 *  - When it breaks, it plays a short shatter animation before its slot goes away.
 *
 * Sprites live in assets/frostbound/textures/gui/sprites/hud/frozen_heart/.
 */
public final class FrozenHeartHud {
	private static final Identifier CONTAINER = sprite("container");
	private static final Identifier CONTAINER_BLINKING = sprite("container_blinking");
	private static final Identifier[] SHATTER = {sprite("shatter_0"), sprite("shatter_1"), sprite("shatter_2")};
	/** Indexed by hardcore (+4), cracked (+2), blinking (+1). full and hardcore_full glint now and then (animated). */
	private static final Identifier[] HEARTS = {
			sprite("full"), sprite("full_blinking"), sprite("cracked"), sprite("cracked_blinking"),
			sprite("hardcore_full"), sprite("hardcore_full_blinking"), sprite("hardcore_cracked"),
			sprite("hardcore_cracked_blinking")};

	/** Ticks each shatter frame stays up. */
	private static final int SHATTER_FRAME_TICKS = 3;
	private static final int SHATTER_TICKS = SHATTER.length * SHATTER_FRAME_TICKS;

	/** What the HUD is showing; follows Vessels.ClientState.frozenHeart. */
	private static float shown = 0.0f;
	private static int blinkUntil = Integer.MIN_VALUE;
	private static int shatterStart = Integer.MIN_VALUE / 2;

	private FrozenHeartHud() {}

	private static Identifier sprite(String name) {
		return Frostbound.id("hud/frozen_heart/" + name);
	}

	/** Called when the server sends a new frozen heart value. */
	public static void onChange(float before, float after, int ticks) {
		if (after < before) {
			// Hit: flash like a vanilla heart does.
			blinkUntil = ticks + 20;
			if (after <= 0.0f) {
				shatterStart = ticks;
			}
		} else if (after > before) {
			// Formed (or mended): a short flash.
			blinkUntil = ticks + 8;
		}
		shown = after;
	}

	public static void reset() {
		shown = 0.0f;
		blinkUntil = Integer.MIN_VALUE;
		shatterStart = Integer.MIN_VALUE / 2;
	}

	/** Whether the frozen heart (or its shatter animation) needs a slot in the health bar. */
	public static boolean isShown(int ticks) {
		return shown > 0.0f || ticks - shatterStart < SHATTER_TICKS;
	}

	public static void draw(DrawContext context, int ticks, int x, int y, boolean hardcore) {
		RenderSystem.enableBlend();
		if (shown <= 0.0f) {
			int frame = Math.max(0, Math.min(SHATTER.length - 1, (ticks - shatterStart) / SHATTER_FRAME_TICKS));
			context.drawGuiTexture(SHATTER[frame], x, y, 9, 9);
			RenderSystem.disableBlend();
			return;
		}
		// Same rhythm as vanilla's heart flashing: three ticks on, three off.
		boolean blinking = blinkUntil > ticks && (blinkUntil - ticks) / 3 % 2 == 1;
		context.drawGuiTexture(blinking ? CONTAINER_BLINKING : CONTAINER, x, y, 9, 9);
		boolean cracked = shown <= FrozenHeart.FULL / 2.0f;
		context.drawGuiTexture(HEARTS[(hardcore ? 4 : 0) + (cracked ? 2 : 0) + (blinking ? 1 : 0)], x, y, 9, 9);
		RenderSystem.disableBlend();
	}
}

package com.frostbound.client.imbuement;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import com.frostbound.imbuement.FrostTier;
import com.frostbound.imbuement.FrostTiers;
import com.frostbound.addons.GearAddon;
import com.frostbound.addons.GearAddons;
import com.frostbound.imbuement.ImbuementScreenHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The imbuement table's screen. The panel (textures/gui/container/imbuement_table.png, painted by
 * tools/ImbuementGuiTexture.java) is frost-crusted, with a snow cap and icicles, and a dark ice recess
 * that holds the ring, the arrow, the result and the tier ladder. On top of it, drawn here:
 *
 * <ul>
 *   <li>Snow drifting down the recess, always.</li>
 *   <li>When the ring makes something: the eight outer slots glow in the new tier's colour, a wave of light
 *       running clockwise round them into the centre; the arrow fills with ice; the result box glows, and
 *       the tier it makes ("Icy ★★") shows over it.</li>
 *   <li>Taking the result: a burst of frost from the result box and a flash over the ring.</li>
 *   <li>The tier ladder: Cold, Icy and Frozen, each with its ring item (and count) and stars. With a piece in
 *       the centre, the tiers it has are ticked, the next one glows and later ones are dimmed. Hovering a
 *       tier says what goes around the piece and what it gives armour and weapons, with the config's numbers.</li>
 * </ul>
 */
public class ImbuementScreen extends HandledScreen<ImbuementScreenHandler> {
	private static final Identifier TEXTURE = Frostbound.id("textures/gui/container/imbuement_table.png");
	// Layout: keep in step with tools/ImbuementGuiTexture.java and ImbuementScreenHandler.
	private static final int RECESS_X0 = 7, RECESS_Y0 = 14, RECESS_X1 = 169, RECESS_Y1 = 106;
	private static final int ARROW_X = 90, ARROW_Y = 40, ARROW_W = 24, ARROW_H = 15;
	private static final int[] STATION_X = {14, 66, 118};
	private static final int STATION_Y = 83;
	private static final int STATION_W = 44;
	// Sprites in the texture.
	private static final int ARROW_U = 180, ARROW_V = 0;
	private static final int GLOW_U = 180, GLOW_V = 20, GLOW_SIZE = 22;
	private static final int BIG_GLOW_U = 180, BIG_GLOW_V = 44, BIG_GLOW_SIZE = 30;
	private static final int STAR_U = 212, EMPTY_STAR_U = 220, CHECK_U = 228, ICON_V = 0, ICON_SIZE = 7;
	private static final int FLAKE_U = 180, FLAKE_V = 80;
	/** Outer grid indices (0–8, row by row) in clockwise order from the top left, for the glow's wave. */
	private static final int[] CLOCKWISE = {0, 1, 2, 5, 8, 7, 6, 3};
	private static final int TOOLTIP_WIDTH = 220;
	/** Ticks the arrow takes to fill once the ring makes something. */
	private static final float FILL_TICKS = 10.0f;

	private final List<Flake> snow = new ArrayList<>();
	private final List<Spark> sparks = new ArrayList<>();
	private final Random random = Random.create();
	private int ticks;
	/** Ticks since the ring started making something, or -1 while it doesn't. */
	private int matchTicks = -1;
	/** Ticks left of the flash after taking a result, and the tier it was. */
	private int flash;
	private int flashColor = FrostTier.COLD.starColor;

	public ImbuementScreen(ImbuementScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 176;
		backgroundHeight = 202;
		titleY = 5;
		playerInventoryTitleY = ImbuementScreenHandler.INVENTORY_Y - 10;
	}

	@Override
	protected void init() {
		super.init();
		titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
		snow.clear();
		for (int i = 0; i < 28; i++) {
			Flake flake = new Flake();
			flake.reset(random, true);
			snow.add(flake);
		}
	}

	// --- Ticking ---

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		ticks++;
		matchTicks = making() == null ? -1 : matchTicks + 1;
		if (flash > 0) {
			flash--;
		}
		for (Flake flake : snow) {
			flake.y += flake.speed;
			if (flake.y > RECESS_Y1 + 2) {
				flake.reset(random, false);
			}
		}
		sparks.removeIf(spark -> ++spark.age >= spark.life);
		for (Spark spark : sparks) {
			spark.x += spark.vx;
			spark.y += spark.vy;
			spark.vx *= 0.9f;
			spark.vy = spark.vy * 0.9f + 0.03f;
		}
	}

	/** The tier the ring makes right now, or null. */
	@Nullable
	private FrostTier target() {
		ItemStack result = handler.getSlot(ImbuementScreenHandler.RESULT).getStack();
		return result.isEmpty() ? null : FrostTiers.tier(result);
	}

	/** What the result slot shows being made: a frost tier ("Icy ★★", in its blue) or an add-on ("Flame Extract", in its colour). */
	private record Making(Text name, int color, int level) {}

	@Nullable
	private Making making() {
		ItemStack result = handler.getSlot(ImbuementScreenHandler.RESULT).getStack();
		if (result.isEmpty()) {
			return null;
		}
		FrostTier tier = FrostTiers.tier(result);
		FrostTier from = centerTier();
		List<GearAddon> addons = GearAddons.of(result);
		if ((from == null || tier.level() > from.level()) || addons.isEmpty()) {
			return new Making(tierName(tier), tier.starColor, tier.level());
		}
		GearAddon added = addons.get(addons.size() - 1);
		return new Making(added.name(), added.color(), 1);
	}

	/** The tier of the piece in the centre, or null if there's no piece there. */
	@Nullable
	private FrostTier centerTier() {
		ItemStack center = handler.getSlot(ImbuementScreenHandler.CENTER_SLOT).getStack();
		return FrostTiers.canImbue(center) ? FrostTiers.tier(center) : null;
	}

	@Override
	protected void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType) {
		Making made = slot != null && slot.id == ImbuementScreenHandler.RESULT && slot.hasStack() ? making() : null;
		super.onMouseClick(slot, slotId, button, actionType);
		if (made != null) {
			burst(made);
		}
	}

	/** Frost flies out of the result box, and the ring flashes. */
	private void burst(Making made) {
		flash = 14;
		flashColor = made.color();
		float cx = ImbuementScreenHandler.RESULT_X + 8, cy = ImbuementScreenHandler.RESULT_Y + 8;
		for (int i = 0; i < 18 + 8 * made.level(); i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			float speed = 0.8f + random.nextFloat() * 2.2f;
			Spark spark = new Spark();
			spark.x = cx;
			spark.y = cy;
			spark.vx = (float) Math.cos(angle) * speed;
			spark.vy = (float) Math.sin(angle) * speed - 0.4f;
			spark.life = 12 + random.nextInt(10);
			spark.big = random.nextInt(3) == 0;
			spark.color = random.nextBoolean() ? 0xFFFFFF : made.color();
			sparks.add(spark);
		}
	}

	// --- Drawing ---

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
		for (int i = 0; i < STATION_X.length; i++) {
			int sx = x + STATION_X[i] - 1, sy = y + STATION_Y - 1;
			if (mouseX >= sx && mouseX < sx + STATION_W && mouseY >= sy && mouseY < sy + 18) {
				context.drawOrderedTooltip(textRenderer, stationTooltip(FrostTiers.STEPS.get(i)), mouseX, mouseY);
			}
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight);
		float time = ticks + delta;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		// Snow in the recess, behind everything else.
		context.enableScissor(x + RECESS_X0, y + RECESS_Y0, x + RECESS_X1, y + RECESS_Y1);
		for (Flake flake : snow) {
			float fx = flake.x + MathHelper.sin(time * 0.05f + flake.phase) * 2.5f;
			float fy = flake.y + flake.speed * delta;
			tint(1.0f, 1.0f, 1.0f, flake.alpha);
			context.drawTexture(TEXTURE, x + Math.round(fx), y + Math.round(fy), FLAKE_U + flake.sprite.u, FLAKE_V, flake.sprite.size,
					flake.sprite.size);
		}
		context.disableScissor();

		// The ring at work: a wave of light round the outer slots, the centre and the result aglow, the arrow filling.
		Making target = making();
		if (target != null) {
			float fade = Math.min(1.0f, (matchTicks + delta) / 6.0f);
			for (int k = 0; k < CLOCKWISE.length; k++) {
				float wave = Math.max(0.0f, MathHelper.cos(time * 0.22f - k * (float) Math.PI / 4));
				glow(context, gridX(CLOCKWISE[k]) - 3, gridY(CLOCKWISE[k]) - 3, target.color(), fade * (0.25f + 0.7f * wave * wave * wave));
			}
			float pulse = 0.55f + 0.3f * MathHelper.sin(time * 0.15f);
			glow(context, gridX(ImbuementScreenHandler.CENTER) - 3, gridY(ImbuementScreenHandler.CENTER) - 3, target.color(), fade * pulse);
			bigGlow(context, target.color(), fade * pulse);
			int filled = Math.round(ARROW_W * Math.min(1.0f, (matchTicks + delta) / FILL_TICKS));
			tint(1.0f, 1.0f, 1.0f, 1.0f);
			context.drawTexture(TEXTURE, x + ARROW_X, y + ARROW_Y, ARROW_U, ARROW_V, filled, ARROW_H);
		}
		if (flash > 0) {
			float strength = (flash - delta) / 14.0f;
			for (int i = 0; i < 9; i++) {
				glow(context, gridX(i) - 3, gridY(i) - 3, flashColor, strength);
			}
			bigGlow(context, 0xFFFFFF, strength);
		}
		tint(1.0f, 1.0f, 1.0f, 1.0f);

		drawLadder(context, time);
		RenderSystem.disableBlend();
	}

	/** The three tiers along the bottom of the recess. */
	private void drawLadder(DrawContext context, float time) {
		FrostTier current = centerTier();
		FrostTier target = target();
		for (int i = 0; i < FrostTiers.STEPS.size(); i++) {
			FrostTiers.Step step = FrostTiers.STEPS.get(i);
			int level = step.to().level();
			int sx = x + STATION_X[i], sy = y + STATION_Y;
			boolean done = current != null && level <= current.level();
			boolean next = current != null && level == current.level() + 1;
			boolean locked = current != null && level > current.level() + 1;

			if (next) {
				float pulse = step.to() == target ? 0.75f + 0.25f * MathHelper.sin(time * 0.3f) : 0.35f + 0.2f * MathHelper.sin(time * 0.12f);
				glow(context, STATION_X[i] - 3, STATION_Y - 3, step.to().starColor, pulse);
				tint(1.0f, 1.0f, 1.0f, 1.0f);
			}
			ItemStack ring = new ItemStack(step.ring(), step.count());
			context.drawItem(ring, sx, sy);
			context.drawItemInSlot(textRenderer, ring, sx, sy);
			if (locked) {
				// Frosted over: not yet.
				context.getMatrices().push();
				context.getMatrices().translate(0.0f, 0.0f, 250.0f);
				context.fill(sx, sy, sx + 16, sy + 16, 0xB0141E2D);
				context.getMatrices().pop();
			}
			for (int star = 0; star < 3; star++) {
				int px = sx + 20 + star * 8, py = sy + 5;
				if (star < level && !locked) {
					int color = FrostTier.of(star + 1).starColor;
					tint((color >> 16 & 0xFF) / 255.0f, (color >> 8 & 0xFF) / 255.0f, (color & 0xFF) / 255.0f, done || next || current == null ? 1.0f : 0.6f);
					context.drawTexture(TEXTURE, px, py, STAR_U, ICON_V, ICON_SIZE, ICON_SIZE);
				} else if (star < level) {
					tint(1.0f, 1.0f, 1.0f, 1.0f);
					context.drawTexture(TEXTURE, px, py, EMPTY_STAR_U, ICON_V, ICON_SIZE, ICON_SIZE);
				}
			}
			tint(1.0f, 1.0f, 1.0f, 1.0f);
			if (done) {
				context.getMatrices().push();
				context.getMatrices().translate(0.0f, 0.0f, 250.0f);
				context.drawTexture(TEXTURE, sx + 11, sy - 2, CHECK_U, ICON_V, ICON_SIZE, ICON_SIZE);
				context.getMatrices().pop();
			}
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		super.drawForeground(context, mouseX, mouseY);
		// What the ring makes, over the result box: "Icy ★★", or an add-on's name.
		Making target = making();
		if (target != null) {
			Text name = target.name();
			int cx = ImbuementScreenHandler.RESULT_X + 8;
			context.drawText(textRenderer, name, cx - textRenderer.getWidth(name) / 2, 22, target.color(), true);
		}
		// Sparks from the last imbuing, over the items.
		if (!sparks.isEmpty()) {
			RenderSystem.enableBlend();
			context.getMatrices().push();
			context.getMatrices().translate(0.0f, 0.0f, 300.0f);
			for (Spark spark : sparks) {
				float alpha = 1.0f - spark.age / (float) spark.life;
				tint((spark.color >> 16 & 0xFF) / 255.0f, (spark.color >> 8 & 0xFF) / 255.0f, (spark.color & 0xFF) / 255.0f, alpha);
				FlakeSprite sprite = spark.big ? FlakeSprite.MEDIUM : FlakeSprite.SMALL;
				context.drawTexture(TEXTURE, Math.round(spark.x) - sprite.size / 2, Math.round(spark.y) - sprite.size / 2,
						FLAKE_U + sprite.u, FLAKE_V, sprite.size, sprite.size);
			}
			tint(1.0f, 1.0f, 1.0f, 1.0f);
			context.getMatrices().pop();
			RenderSystem.disableBlend();
		}
	}

	/** "Icy ★★": the tier's name and its stars, in their colours. */
	private static Text tierName(FrostTier tier) {
		return Text.translatable("frostbound.frost_tier." + tier.name().toLowerCase(java.util.Locale.ROOT))
				.setStyle(Style.EMPTY.withColor(tier.starColor)).append(FrostTiers.stars(tier));
	}

	/** What a tier needs and gives, and where the piece in the centre stands with it. */
	private List<OrderedText> stationTooltip(FrostTiers.Step step) {
		FrostboundConfig.FrostTierOptions c = FrostboundConfig.get().frostTiers;
		String key = "container.frostbound.imbuement_table.tier." + step.to().name().toLowerCase(java.util.Locale.ROOT);
		List<Text> lines = new ArrayList<>();
		lines.add(tierName(step.to()));
		lines.add(Text.translatable("container.frostbound.imbuement_table.ring", step.count(), step.ring().getName()).formatted(Formatting.GRAY));
		Object[] armor = switch (step.to()) {
			case COLD -> new Object[] {num(c.coldArmor), num(c.coldDurability * 100)};
			case ICY -> new Object[] {num(c.icyDurability * 100)};
			default -> new Object[] {num(c.frozenArmor), num(c.frozenToughness)};
		};
		Object[] weapon = switch (step.to()) {
			case COLD -> new Object[] {num(c.coldAttackDamage), num(c.coldDurability * 100)};
			case ICY -> new Object[] {num(c.icyDurability * 100)};
			default -> new Object[] {num(c.frozenAttackDamage)};
		};
		lines.add(Text.translatable(key + ".armor", armor).formatted(Formatting.GRAY));
		lines.add(Text.translatable(key + ".weapon", weapon).formatted(Formatting.GRAY));

		FrostTier current = centerTier();
		if (current != null) {
			int level = step.to().level();
			MutableText status;
			if (level <= current.level()) {
				status = Text.translatable("container.frostbound.imbuement_table.status.done").formatted(Formatting.GREEN);
			} else if (level == current.level() + 1) {
				status = Text.translatable("container.frostbound.imbuement_table.status.next").formatted(Formatting.AQUA);
			} else {
				status = Text.translatable("container.frostbound.imbuement_table.status.locked",
						Text.translatable("frostbound.frost_tier." + FrostTier.of(level - 1).name().toLowerCase(java.util.Locale.ROOT)))
						.formatted(Formatting.RED);
			}
			lines.add(status);
		}
		List<OrderedText> wrapped = new ArrayList<>();
		for (Text line : lines) {
			wrapped.addAll(textRenderer.wrapLines(line, TOOLTIP_WIDTH));
		}
		return wrapped;
	}

	private static String num(double value) {
		return com.frostbound.passives.Passive.number(value);
	}

	// --- Helpers ---

	private static int gridX(int index) {
		return ImbuementScreenHandler.CENTER_X + (index % 3 - 1) * ImbuementScreenHandler.GRID_PITCH;
	}

	private static int gridY(int index) {
		return ImbuementScreenHandler.CENTER_Y + (index / 3 - 1) * ImbuementScreenHandler.GRID_PITCH;
	}

	/** The slot glow (22 × 22) at panel coordinates, tinted. */
	private void glow(DrawContext context, int px, int py, int rgb, float alpha) {
		tint((rgb >> 16 & 0xFF) / 255.0f, (rgb >> 8 & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f, MathHelper.clamp(alpha, 0.0f, 1.0f));
		context.drawTexture(TEXTURE, x + px, y + py, GLOW_U, GLOW_V, GLOW_SIZE, GLOW_SIZE);
	}

	/** The result box's glow (30 × 30), tinted. */
	private void bigGlow(DrawContext context, int rgb, float alpha) {
		tint((rgb >> 16 & 0xFF) / 255.0f, (rgb >> 8 & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f, MathHelper.clamp(alpha, 0.0f, 1.0f));
		context.drawTexture(TEXTURE, x + ImbuementScreenHandler.RESULT_X - 7, y + ImbuementScreenHandler.RESULT_Y - 7, BIG_GLOW_U, BIG_GLOW_V,
				BIG_GLOW_SIZE, BIG_GLOW_SIZE);
	}

	private static void tint(float r, float g, float b, float a) {
		RenderSystem.setShaderColor(r, g, b, a);
	}

	private enum FlakeSprite {
		LARGE(0, 5), MEDIUM(6, 3), SMALL(10, 1);

		final int u;
		final int size;

		FlakeSprite(int u, int size) {
			this.u = u;
			this.size = size;
		}
	}

	/** A snowflake drifting down the recess (panel coordinates). */
	private static final class Flake {
		float x;
		float y;
		float speed;
		float phase;
		float alpha;
		FlakeSprite sprite;

		void reset(Random random, boolean anywhere) {
			x = RECESS_X0 + random.nextFloat() * (RECESS_X1 - RECESS_X0);
			y = anywhere ? RECESS_Y0 + random.nextFloat() * (RECESS_Y1 - RECESS_Y0) : RECESS_Y0 - 5;
			int pick = random.nextInt(10);
			sprite = pick == 0 ? FlakeSprite.LARGE : pick < 4 ? FlakeSprite.MEDIUM : FlakeSprite.SMALL;
			// Bigger flakes are nearer: faster and brighter.
			float near = sprite == FlakeSprite.LARGE ? 1.0f : sprite == FlakeSprite.MEDIUM ? 0.7f : 0.45f;
			speed = 0.15f + near * 0.25f + random.nextFloat() * 0.1f;
			alpha = 0.25f + near * 0.45f;
			phase = random.nextFloat() * 6.28f;
		}
	}

	/** A bit of frost thrown out when a piece is imbued (panel coordinates). */
	private static final class Spark {
		float x;
		float y;
		float vx;
		float vy;
		int age;
		int life;
		boolean big;
		int color;
	}
}

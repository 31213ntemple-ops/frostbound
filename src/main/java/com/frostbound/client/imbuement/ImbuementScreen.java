package com.frostbound.client.imbuement;

import com.frostbound.Frostbound;
import com.frostbound.imbuement.ImbuementScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The imbuement table's screen. The background (textures/gui/container/imbuement_table.png) is the
 * crafting table's with the grid redrawn: the eight outer slots form a ring, and the centre (armour
 * only) is a darker slot set apart from it by a 2 px gap.
 */
public class ImbuementScreen extends HandledScreen<ImbuementScreenHandler> {
	private static final Identifier TEXTURE = Frostbound.id("textures/gui/container/imbuement_table.png");

	public ImbuementScreen(ImbuementScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundHeight = 170;
		playerInventoryTitleY = backgroundHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		titleX = 29;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight);
	}
}

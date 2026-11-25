import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Paints the imbuement table's screen (run from the project root):
 *
 * <pre>
 *   java tools/ImbuementGuiTexture.java
 * </pre>
 *
 * Writes textures/gui/container/imbuement_table.png (256 × 256). The layout must match the constants in
 * ImbuementScreenHandler and client/imbuement/ImbuementScreen:
 *
 *  - (0, 0) 176 × 202: the panel. Frosty blue-grey with a snow cap and icicles on top, a dark ice recess
 *    (x 7–168, y 14–105) holding the 3 × 3 ring (centre slot darker), an empty ice arrow, the big result
 *    box and the three stations of the tier ladder, then the inventory.
 *  - (180, 0) 24 × 15: the arrow filled with ice (the screen draws part of it as it fills).
 *  - (180, 20) 22 × 22 and (180, 44) 30 × 30: soft white glow frames for slots and the result box (tinted in code).
 *  - (212, 0) star, (220, 0) empty star, (228, 0) check mark, 7 × 7 each.
 *  - (180, 80) snowflakes: 5 × 5, 3 × 3, 1 × 1.
 *
 * Deterministic (fixed seed), so running it again gives the same file.
 */
public class ImbuementGuiTexture {
	static final Path OUT = Path.of("src/main/resources/assets/frostbound/textures/gui/container/imbuement_table.png");

	// Layout (keep in step with ImbuementScreenHandler).
	static final int W = 176, H = 202;
	static final int CENTER_X = 48, CENTER_Y = 39, PITCH = 20;
	static final int ARROW_X = 90, ARROW_Y = 40;
	static final int RESULT_X = 124, RESULT_Y = 39;
	static final int[] STATION_X = {14, 66, 118};
	static final int STATION_Y = 83;
	static final int INVENTORY_Y = 120;
	static final int RECESS_X0 = 7, RECESS_Y0 = 14, RECESS_X1 = 168, RECESS_Y1 = 105;

	// Palette: the table's navy wood and ice.
	static final int OUTLINE = 0xFF17202E;
	static final int PANEL = 0xFFD0DBE6, PANEL_HI = 0xFFF4F9FD, PANEL_LO = 0xFF7F90A2, PANEL_FLECK = 0xFFE2EBF3;
	static final int RECESS_TOP = 0xFF1E2A41, RECESS_BOTTOM = 0xFF141E2D, RECESS_SHADOW = 0xFF2A3442;
	static final int SPARKLE = 0xFF4F6F98, SPARKLE_BRIGHT = 0xFF8DB2D8;
	static final int SLOT_DARK = 0xFF0D141E, SLOT_LIGHT = 0xFF8FB0CC, SLOT_FILL = 0xFF3F5874;
	static final int CENTER_FILL = 0xFF253650, CENTER_LIGHT = 0xFFA9CDEA, RUNE = 0xFF6F93B8;
	static final int INV_DARK = 0xFF4B5866, INV_LIGHT = 0xFFFFFFFF, INV_FILL = 0xFF9AA9B7;
	static final int SNOW = 0xFFFFFFFF, SNOW_SHADE = 0xFFE3EFF9, SNOW_SHADOW = 0xFFAFC3D6;
	static final int ICICLE_EDGE = 0xFFB9D6EE, ICICLE_SHADE = 0xFF7FA6C9, ICICLE_TIP = 0xFF9CC0E0;

	static final BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
	static final Random random = new Random(20261124L);

	public static void main(String[] args) throws Exception {
		panel();
		recess();
		ring();
		arrow(ARROW_X, ARROW_Y, false);
		resultBox();
		ladder();
		inventory();
		snowCap();
		sprites();
		Files.createDirectories(OUT.getParent());
		ImageIO.write(img, "png", new File(OUT.toString()));
		System.out.println("wrote " + OUT);
	}

	// --- The panel ---

	static void panel() {
		fill(1, 1, W - 2, H - 2, PANEL);
		// Outline with the corners cut, like vanilla's.
		for (int x = 2; x < W - 2; x++) {
			set(x, 0, OUTLINE);
			set(x, H - 1, OUTLINE);
		}
		for (int y = 2; y < H - 2; y++) {
			set(0, y, OUTLINE);
			set(W - 1, y, OUTLINE);
		}
		set(1, 1, OUTLINE);
		set(W - 2, 1, OUTLINE);
		set(1, H - 2, OUTLINE);
		set(W - 2, H - 2, OUTLINE);
		// Bevel: light top-left, dark bottom-right, two pixels each.
		for (int i = 0; i < 2; i++) {
			for (int x = 1 + i; x < W - 2 - i; x++) {
				set(x, 1 + i, PANEL_HI);
				set(x + 1, H - 2 - i, PANEL_LO);
			}
			for (int y = 1 + i; y < H - 2 - i; y++) {
				set(1 + i, y, PANEL_HI);
				set(W - 2 - i, y + 1, PANEL_LO);
			}
		}
		set(1, 1, OUTLINE);
		set(W - 2, H - 2, OUTLINE);
		// Rime: a scatter of paler flecks, thicker towards the corners.
		for (int n = 0; n < 900; n++) {
			int x = 3 + random.nextInt(W - 6), y = 3 + random.nextInt(H - 6);
			double corner = Math.min(Math.min(x, W - x), Math.min(y, H - y));
			if (img.getRGB(x, y) == PANEL && random.nextDouble() < 1.2 / (1 + corner * 0.25)) {
				set(x, y, PANEL_FLECK);
			}
		}
	}

	static void recess() {
		for (int y = RECESS_Y0; y <= RECESS_Y1; y++) {
			double t = (y - RECESS_Y0) / (double) (RECESS_Y1 - RECESS_Y0);
			for (int x = RECESS_X0; x <= RECESS_X1; x++) {
				set(x, y, mix(RECESS_TOP, RECESS_BOTTOM, t));
			}
		}
		// Inset: dark top and left, light bottom and right.
		for (int x = RECESS_X0; x <= RECESS_X1; x++) {
			set(x, RECESS_Y0 - 1, RECESS_SHADOW);
			set(x, RECESS_Y1 + 1, PANEL_HI);
		}
		for (int y = RECESS_Y0 - 1; y <= RECESS_Y1 + 1; y++) {
			set(RECESS_X0 - 1, y, RECESS_SHADOW);
			set(RECESS_X1 + 1, y, PANEL_HI);
		}
		set(RECESS_X1 + 1, RECESS_Y0 - 1, PANEL);
		set(RECESS_X0 - 1, RECESS_Y1 + 1, PANEL);
		// Ice grain and a few glints, like the deep ice under the table's top.
		for (int n = 0; n < 260; n++) {
			int x = RECESS_X0 + 1 + random.nextInt(RECESS_X1 - RECESS_X0 - 1);
			int y = RECESS_Y0 + 1 + random.nextInt(RECESS_Y1 - RECESS_Y0 - 1);
			set(x, y, mix(img.getRGB(x, y), SPARKLE, 0.35 + random.nextDouble() * 0.3));
		}
		for (int n = 0; n < 14; n++) {
			int x = RECESS_X0 + 3 + random.nextInt(RECESS_X1 - RECESS_X0 - 6);
			int y = RECESS_Y0 + 3 + random.nextInt(RECESS_Y1 - RECESS_Y0 - 6);
			set(x, y, SPARKLE_BRIGHT);
			if (random.nextBoolean()) {
				blend(x - 1, y, SPARKLE_BRIGHT, 0.4);
				blend(x + 1, y, SPARKLE_BRIGHT, 0.4);
				blend(x, y - 1, SPARKLE_BRIGHT, 0.4);
				blend(x, y + 1, SPARKLE_BRIGHT, 0.4);
			}
		}
	}

	// --- The ring of slots, framed by a frost rune ---

	static void ring() {
		int x0 = CENTER_X - PITCH - 4, y0 = CENTER_Y - PITCH - 4;
		int x1 = CENTER_X + PITCH + 16 + 3, y1 = CENTER_Y + PITCH + 16 + 3;
		// A rounded frame two pixels outside the slots.
		for (int x = x0 + 2; x <= x1 - 2; x++) {
			set(x, y0, RUNE);
			set(x, y1, RUNE);
		}
		for (int y = y0 + 2; y <= y1 - 2; y++) {
			set(x0, y, RUNE);
			set(x1, y, RUNE);
		}
		set(x0 + 1, y0 + 1, RUNE);
		set(x1 - 1, y0 + 1, RUNE);
		set(x0 + 1, y1 - 1, RUNE);
		set(x1 - 1, y1 - 1, RUNE);
		// A small ice crystal at the middle of each side.
		int mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
		diamond(mx, y0, SPARKLE_BRIGHT);
		diamond(mx, y1, SPARKLE_BRIGHT);
		diamond(x0, my, SPARKLE_BRIGHT);
		diamond(x1, my, SPARKLE_BRIGHT);

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				int x = CENTER_X + (col - 1) * PITCH - 1, y = CENTER_Y + (row - 1) * PITCH - 1;
				if (row == 1 && col == 1) {
					slot(x, y, CENTER_FILL, SLOT_DARK, CENTER_LIGHT);
					// A pale inner ring marks it as the heart of the table.
					for (int i = 2; i < 16; i++) {
						blend(x + i, y + 2, RUNE, 0.45);
						blend(x + i, y + 15, RUNE, 0.45);
						blend(x + 2, y + i, RUNE, 0.45);
						blend(x + 15, y + i, RUNE, 0.45);
					}
				} else {
					slot(x, y, SLOT_FILL, SLOT_DARK, SLOT_LIGHT);
				}
			}
		}
	}

	static void diamond(int x, int y, int color) {
		set(x, y - 1, color);
		set(x - 1, y, color);
		set(x, y, 0xFFFFFFFF);
		set(x + 1, y, color);
		set(x, y + 1, color);
	}

	// --- The arrow: empty here, filled at (180, 0) ---

	static boolean inArrow(int x, int y) {
		// Shaft x 0–13, y 5–9; head from x 13 to 23, tapering to a point at y 7.
		if (x >= 0 && x <= 13 && y >= 5 && y <= 9) {
			return true;
		}
		return x >= 13 && x <= 23 && Math.abs(y - 7) <= 23 - x - 2 + 1 && Math.abs(y - 7) <= 7;
	}

	static void arrow(int ox, int oy, boolean filled) {
		for (int y = 0; y < 15; y++) {
			for (int x = 0; x < 24; x++) {
				if (!inArrow(x, y)) {
					continue;
				}
				boolean edge = !inArrow(x - 1, y) || !inArrow(x + 1, y) || !inArrow(x, y - 1) || !inArrow(x, y + 1);
				int color;
				if (filled) {
					color = edge ? 0xFFE8F6FF : mix(0xFFD6EDFF, 0xFF79AFE3, y / 14.0);
					if (!edge && (x + y) % 7 == 0) {
						color = 0xFFFFFFFF; // a glint in the ice
					}
				} else {
					color = edge ? 0xFF5E7FA3 : 0xFF25374F;
				}
				set(ox + x, oy + y, color);
			}
		}
	}

	static void resultBox() {
		int x = RESULT_X - 5, y = RESULT_Y - 5;
		for (int i = 0; i < 26; i++) {
			for (int j = 0; j < 26; j++) {
				set(x + i, y + j, SLOT_FILL);
			}
		}
		for (int i = 0; i < 26; i++) {
			set(x + i, y, SLOT_DARK);
			set(x, y + i, SLOT_DARK);
			set(x + i, y + 25, SLOT_LIGHT);
			set(x + 25, y + i, SLOT_LIGHT);
		}
		set(x + 25, y, SLOT_FILL);
		set(x, y + 25, SLOT_FILL);
		// A darker inner well around where the item sits.
		for (int i = 3; i < 23; i++) {
			blend(x + i, y + 3, SLOT_DARK, 0.35);
			blend(x + 3, y + i, SLOT_DARK, 0.35);
		}
		// Frost creeping in from the corners.
		int[][] frost = {{1, 1}, {2, 1}, {1, 2}, {3, 1}, {1, 3}, {24, 24}, {23, 24}, {24, 23}, {22, 24}, {24, 22}};
		for (int[] f : frost) {
			blend(x + f[0], y + f[1], 0xFFDCEBF7, 0.6);
		}
	}

	// --- The tier ladder: three stations joined by a frost line ---

	static void ladder() {
		for (int i = 0; i < STATION_X.length; i++) {
			slot(STATION_X[i] - 1, STATION_Y - 1, SLOT_FILL, SLOT_DARK, SLOT_LIGHT);
			if (i + 1 < STATION_X.length) {
				int from = STATION_X[i] + 18 + 24 + 2, to = STATION_X[i + 1] - 3;
				int y = STATION_Y + 7;
				for (int x = from; x <= to; x++) {
					if ((x - from) % 2 == 0) {
						set(x, y, RUNE);
					}
				}
				// A chevron pointing on to the next tier.
				set(to, y, SPARKLE_BRIGHT);
				set(to - 1, y - 1, SPARKLE_BRIGHT);
				set(to - 1, y + 1, SPARKLE_BRIGHT);
			}
		}
	}

	// --- The player's inventory ---

	static void inventory() {
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				slot(7 + col * 18, INVENTORY_Y - 1 + row * 18, INV_FILL, INV_DARK, INV_LIGHT);
			}
		}
		for (int col = 0; col < 9; col++) {
			slot(7 + col * 18, INVENTORY_Y + 57, INV_FILL, INV_DARK, INV_LIGHT);
		}
	}

	/** An 18 × 18 slot: dark top-left edge, light bottom-right edge. */
	static void slot(int x, int y, int fill, int dark, int light) {
		for (int i = 0; i < 18; i++) {
			for (int j = 0; j < 18; j++) {
				set(x + i, y + j, fill);
			}
		}
		for (int i = 0; i < 17; i++) {
			set(x + i, y, dark);
			set(x, y + i, dark);
			set(x + 1 + i, y + 17, light);
			set(x + 17, y + 1 + i, light);
		}
	}

	// --- Snow on top, icicles under it ---

	static void snowCap() {
		double phase = random.nextDouble() * 10;
		int[] depth = new int[W];
		for (int x = 1; x < W - 1; x++) {
			double lump = Math.sin(x * 0.19 + phase) * 1.3 + Math.sin(x * 0.47 + phase * 2) * 0.8;
			depth[x] = 4 + (int) Math.round(lump);
			int top = (x < 3 || x > W - 4) ? 1 : 0;
			for (int y = top; y < depth[x]; y++) {
				set(x, y, y >= depth[x] - 2 ? SNOW_SHADE : SNOW);
			}
			// A soft blue shadow under the snow, so it sits on the panel.
			set(x, depth[x], SNOW_SHADOW);
		}
		// Icicles hang off the snow at the sides (the title sits in the middle): {x, length, width}.
		int[][] icicles = {{4, 7, 2}, {8, 4, 1}, {11, 9, 2}, {16, 5, 2}, {20, 3, 1}, {149, 4, 1}, {153, 8, 2}, {158, 5, 2},
				{163, 10, 2}, {168, 4, 1}, {171, 6, 2}};
		for (int[] icicle : icicles) {
			int x = icicle[0], length = icicle[1], width = icicle[2];
			int start = Math.max(depth[x], depth[Math.min(W - 2, x + width - 1)]);
			for (int i = 0; i < length; i++) {
				// Tapers: full width at the top, one pixel for the last third.
				int w = i < length * 2 / 3 ? width : 1;
				for (int dx = 0; dx < w; dx++) {
					int color = i == length - 1 ? ICICLE_TIP : (dx == 0 ? SNOW : ICICLE_EDGE);
					set(x + dx, start + i, color);
				}
				set(x + w, start + i, ICICLE_SHADE);
			}
		}
	}

	// --- Sprites the screen draws on top ---

	static void sprites() {
		arrow(180, 0, true);
		glow(180, 20, 22);
		glow(180, 44, 30);
		String[] star = {
				"...X...",
				"..XXX..",
				"XXXXXXX",
				".XXXXX.",
				"..XXX..",
				".XX.XX.",
				".X...X."};
		String[] check = {
				"......X",
				".....XX",
				"X...XX.",
				"XX.XX..",
				".XXX...",
				"..X....",
				"......."};
		for (int y = 0; y < 7; y++) {
			for (int x = 0; x < 7; x++) {
				if (star[y].charAt(x) == 'X') {
					set(212 + x, y, 0xFFFFFFFF);
					boolean edge = x == 0 || x == 6 || y == 0 || y == 6 || star[y].charAt(x - 1) != 'X' || star[y].charAt(x + 1) != 'X'
							|| star[y - 1].charAt(x) != 'X' || star[y + 1].charAt(x) != 'X';
					if (edge) {
						set(220 + x, y, 0xFF5E7FA3);
					}
				}
				if (check[y].charAt(x) == 'X') {
					set(228 + x, y, 0xFF8FE0A0);
				}
			}
		}
		String[] flake = {
				"X.X.X",
				".XXX.",
				"XXXXX",
				".XXX.",
				"X.X.X"};
		for (int y = 0; y < 5; y++) {
			for (int x = 0; x < 5; x++) {
				if (flake[y].charAt(x) == 'X') {
					set(180 + x, 80 + y, (x == 2 && y == 2) ? 0xFFFFFFFF : 0xFFE4F1FC);
				}
			}
		}
		set(187, 80, 0xFFE4F1FC);
		set(186, 81, 0xFFE4F1FC);
		set(187, 81, 0xFFFFFFFF);
		set(188, 81, 0xFFE4F1FC);
		set(187, 82, 0xFFE4F1FC);
		set(190, 80, 0xFFFFFFFF);
	}

	/** A square frame of soft white light, {@code size} wide: brightest two pixels in, fading outwards and inwards. */
	static void glow(int ox, int oy, int size) {
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int d = Math.min(Math.min(x, size - 1 - x), Math.min(y, size - 1 - y));
				int alpha = switch (d) {
					case 0 -> 70;
					case 1 -> 170;
					case 2 -> 120;
					case 3 -> 45;
					default -> 0;
				};
				if (alpha > 0) {
					img.setRGB(ox + x, oy + y, alpha << 24 | 0xFFFFFF);
				}
			}
		}
	}

	// --- Helpers ---

	static void set(int x, int y, int argb) {
		if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
			img.setRGB(x, y, argb);
		}
	}

	static void fill(int x, int y, int w, int h, int argb) {
		for (int j = y; j < y + h; j++) {
			for (int i = x; i < x + w; i++) {
				set(i, j, argb);
			}
		}
	}

	static void blend(int x, int y, int argb, double amount) {
		if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
			img.setRGB(x, y, mix(img.getRGB(x, y), argb, amount));
		}
	}

	static int mix(int a, int b, double t) {
		int ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
		int br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
		int r = (int) Math.round(ar + (br - ar) * t), g = (int) Math.round(ag + (bg - ag) * t), bl = (int) Math.round(ab + (bb - ab) * t);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}
}

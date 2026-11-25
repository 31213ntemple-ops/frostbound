import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * Generates the habanero textures (run from the project root):
 *
 * <pre>
 *   java tools/HabaneroTextures.java path/to/minecraft-client-1.21.1.jar
 * </pre>
 *
 * (Loom keeps the client jar at ~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar.)
 *
 *  - item/habanero, item/habanero_seeds: the pepper and its seeds.
 *  - block/habanero_stage0..3 (the crop), block/wild_habanero (the wild bush).
 *  - item/hot_sauce, item/flame_extract: vanilla's potion bottle with its liquid recoloured (deep red sauce,
 *    a glowing orange extract with a flame inside).
 *  - font/addon_flame: the little fire drawn between an add-on slot's brackets in tooltips.
 *  - mob_effect/spicy: the Spicy effect's icon.
 *
 * Pixel grids share one palette ({@link #PALETTE}); deterministic, so running it again gives the same files.
 */
public class HabaneroTextures {
	static final Path OUT = Path.of("src/main/resources/assets/frostbound/textures");

	static final Map<Character, Integer> PALETTE = Map.ofEntries(
			// The pepper: deep red outline, red-orange body, orange and yellow towards the light.
			Map.entry('D', 0xFF6E2209),
			Map.entry('R', 0xFFC9500F),
			Map.entry('O', 0xFFE5671A),
			Map.entry('Y', 0xFFF4892A),
			Map.entry('H', 0xFFFFB04E),
			// Unripe pepper.
			Map.entry('e', 0xFF4E8A2A),
			Map.entry('E', 0xFF7BB843),
			// Stem and leaves.
			Map.entry('g', 0xFF2F5A1C),
			Map.entry('G', 0xFF4F8A2C),
			Map.entry('l', 0xFF7DB548),
			// Frost on the wild bush's leaves.
			Map.entry('f', 0xFFB9D3C6),
			// Flowers.
			Map.entry('w', 0xFFF2F2E6),
			Map.entry('y', 0xFFE8D25A),
			// Seeds.
			Map.entry('s', 0xFFB98E45),
			Map.entry('S', 0xFFEFD493),
			// Fire.
			Map.entry('1', 0xFF8C1C08),
			Map.entry('2', 0xFFD8401A),
			Map.entry('3', 0xFFF7861E),
			Map.entry('4', 0xFFFFC94A),
			Map.entry('5', 0xFFFFF4C2));

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.err.println("usage: java tools/HabaneroTextures.java <minecraft-client.jar>");
			System.exit(1);
		}
		write(grid(HABANERO, PEPPER), "item/habanero.png");
		write(grid(SEEDS), "item/habanero_seeds.png");
		write(grid(STAGE_0), "block/habanero_stage0.png");
		write(grid(STAGE_1), "block/habanero_stage1.png");
		write(grid(STAGE_2), "block/habanero_stage2.png");
		write(grid(STAGE_3), "block/habanero_stage3.png");
		write(grid(WILD), "block/wild_habanero.png");
		write(grid(FLAME_GLYPH), "font/addon_flame.png");
		write(grid(SPICY_ICON, PEPPER), "mob_effect/spicy.png");
		write(grid(HOT_SAUCE, SAUCE), "item/hot_sauce.png");
		write(grid(FLAME_EXTRACT, FLASK), "item/flame_extract.png");
		System.out.println("done");
	}

	// --- Pixel grids (16 × 16 unless noted; '.' is transparent) ---

	/**
	 * The pepper, in Farmer's Delight's style (like its tomato): no black outline but a darker shade of the
	 * pepper round it, lit from the top left. Lying diagonally like vanilla's carrot: a curved stem and leafy cap
	 * at the top right, a plump lantern-shaped body with ridges running along it, and a crooked tip at the bottom left.
	 */
	static final Map<Character, Integer> PEPPER = Map.ofEntries(
			Map.entry('E', 0xFF6E2209), // outer shadow (bottom and right)
			Map.entry('D', 0xFFAE3A0C), // shadow
			Map.entry('c', 0xFFC9500F), // crease between lobes
			Map.entry('G', 0xFFE5671A), // orange
			Map.entry('F', 0xFFF4892A), // light
			Map.entry('H', 0xFFFFB04E), // highlight
			Map.entry('I', 0xFFFFE2A6), // shine
			Map.entry('A', 0xFF0B6E0B), // stem and cap: Farmer's Delight's leaf greens
			Map.entry('B', 0xFF0A4F0A),
			Map.entry('C', 0xFF2E922B),
			Map.entry('L', 0xFF5DB83A),
			Map.entry('3', 0xFFF7861E)); // heat (Spicy icon)

	static final String[] HABANERO = {
			"................",
			"...........BA...",
			"..........AC....",
			"........BLCB....",
			".....DDBCLCBD...",
			"....DFFHBCBGcE..",
			"...DFHIHFDGcGGE.",
			"...DFHHFGGcGGDE.",
			"..DFFFGGGcGGDDE.",
			"..DFGGGcGGGDDE..",
			"..DGGGcGGGDDE...",
			"..EDGcGGGDDE....",
			"..EDcGGDDEE.....",
			"...EDDDEE.......",
			"..EDDE..........",
			"..EE............"};

	/**
	 * A hot sauce bottle: a red cap on a narrow neck, sloping shoulders, a cream label with a little orange
	 * pepper on it, and the deep red sauce showing through the glass above and below the label. The glass uses
	 * vanilla's bottle blues (light on the left, darker on the right and bottom).
	 */
	static final Map<Character, Integer> SAUCE = Map.ofEntries(
			Map.entry('k', 0xFF6A150F), // cap, dark
			Map.entry('K', 0xFFA82A1D), // cap
			Map.entry('q', 0xFFDA4C3A), // cap, lit
			Map.entry('a', 0xFFD4E5F7), // glass, lit edge
			Map.entry('b', 0xFFB3CFEC), // glass
			Map.entry('y', 0xFF8BADD0), // glass, bottom
			Map.entry('z', 0xFF5D8FC2), // glass, shadowed edge
			Map.entry('h', 0xFFF07A5E), // shine on the sauce
			Map.entry('r', 0xFFC3261B), // sauce
			Map.entry('R', 0xFF961714), // sauce, darker
			Map.entry('d', 0xFF62100E), // sauce, darkest
			Map.entry('w', 0xFFF4E9CC), // label
			Map.entry('W', 0xFFD3C29A), // label, shaded
			Map.entry('p', 0xFFE5671A), // the pepper on the label
			Map.entry('P', 0xFFAE3A0C),
			Map.entry('l', 0xFF2E922B));

	static final String[] HOT_SAUCE = {
			"................",
			"......kqKk......",
			"......kKKk......",
			"......akkz......",
			"......ahRz......",
			"......ahRz......",
			".....ahrRRz.....",
			"....ahrrrRRz....",
			"....ahwwlwWz....",
			"....ahwpPwWz....",
			"....ahwppWWz....",
			"....ahWWWWWz....",
			"....ahrrrRdz....",
			"....ahrrRRdz....",
			"....byrRRddz....",
			".....yzzzzz....."};

	/**
	 * Flame extract: a round flask stoppered with a cork, full of liquid fire that glows from a near-white core out
	 * to deep red at the glass, with a flame licking up from it into the neck.
	 */
	static final Map<Character, Integer> FLASK = Map.ofEntries(
			Map.entry('A', 0xFFC98B4F), // cork
			Map.entry('B', 0xFF8E5A2E),
			Map.entry('C', 0xFFD4E5F7), // glass, lit
			Map.entry('E', 0xFFB3CFEC), // glass
			Map.entry('L', 0xFF8BADD0),
			Map.entry('G', 0xFF5D8FC2), // glass, shadowed
			Map.entry('r', 0xFF9E1F0A), // fire: deep red at the edges
			Map.entry('o', 0xFFDF4C12),
			Map.entry('O', 0xFFF7861E),
			Map.entry('y', 0xFFFFBE38),
			Map.entry('Y', 0xFFFFE88A),
			Map.entry('W', 0xFFFFFCE6), // the core
			Map.entry('f', 0xFFF7861E), // the flame in the neck
			Map.entry('F', 0xFFFFC94A));

	static final String[] FLAME_EXTRACT = {
			"................",
			".......AB.......",
			"......BAAB......",
			"......CEEG......",
			"......E.fG......",
			"......EfFG......",
			".....C.FY.G.....",
			"....C.fYYf.G....",
			"...CoOfYYfOoG...",
			"...COyyYYyOoG...",
			"...EoyYWWYyoG...",
			"...EoOyYYyOrG...",
			"...LroOyyOorG...",
			"....LroOOorG....",
			".....GGGGGG.....",
			"................"};

		static final String[] SEEDS = {
			"................",
			"................",
			"................",
			".........s......",
			"........sSs.....",
			".....s...s......",
			"....sSs.........",
			".....s......s...",
			"...........sSs..",
			"......s.....s...",
			".....sSs........",
			"......s...s.....",
			"..s......sSs....",
			".sSs......s.....",
			"..s.............",
			"................"};

	static final String[] STAGE_0 = {
			"................",
			"................",
			"................",
			"................",
			"................",
			"................",
			"................",
			"................",
			"................",
			"................",
			"................",
			"....l......l....",
			"...lGl....lGl...",
			"....G..l...G....",
			"....g.lGl..g....",
			"....g..g...g....",};

	static final String[] STAGE_1 = {
			"................",
			"................",
			"................",
			"................",
			"................",
			"................",
			"................",
			"......l.........",
			".....lGl...l....",
			"..l...G...lGl...",
			".lGl..G..l.G....",
			"..G.lGg.lGlg....",
			"..g..g...Gg..l..",
			"..g..g...g..lGl.",
			"..g..g...g...g..",
			"..g..g...g...g..",};

	static final String[] STAGE_2 = {
			"................",
			"................",
			"................",
			"................",
			".....l....l.....",
			"....lGl..lwl....",
			"..l..G..lwyw.l..",
			".lGl.G...lGl.Gl.",
			".lGwlGl.l.G.lGl.",
			"..wyw.GlGlg..G..",
			"..lwl.g.Gg..eG..",
			"..eG..g..g.eEg..",
			"..Eg..g..g..eg..",
			"..eg..g..g...g..",
			"...g..g..g...g..",
			"...g..g..g...g..",};

	static final String[] STAGE_3 = {
			"................",
			"................",
			"................",
			".....l....l.....",
			"....lGl..lGl....",
			"..l..G..l.G..l..",
			".lGl.G.lGlG.lGl.",
			"..GDRGl.G.gDRG..",
			"..DOYDGlGlDOYD..",
			"..DOOD.g.GDOOD..",
			"..DRRD.ODgDRRD..",
			"...DD.DYOD.DD...",
			"...g..DOOD...g..",
			"...g..gDDg...g..",
			"...g..g..g...g..",
			"...g..g..g...g..",};

	static final String[] WILD = {
			"................",
			"................",
			"................",
			"................",
			".....f....l.....",
			"....lGl..fGf....",
			"..f..G..l.G..l..",
			".lGf.GDRGlG.fGl.",
			"..G.lDOYD.GDRGf.",
			".fGl.DOOD.DOYD..",
			"..GDRgDRD.DOOD..",
			"..DOYD.g.gDRRD..",
			"..DOOD.g..gDD...",
			"...DD..g..g.g...",
			"...g...g..g.g...",
			"...g...g..g.g...",};

	/** 7 wide, 8 high: drawn at font height between the slot brackets. */
	static final String[] FLAME_GLYPH = {
			"...2...",
			"..23...",
			"..232..",
			".2343.2",
			".23432.",
			"2345432",
			"2345432",
			".23432."};

	/** Drawn over the extract's liquid (the bottle's lower half). */
	static final String[] FLAME_IN_BOTTLE = {
			"................",
			"................",
			"................",
			"................",
			"................",
			"................",
			"................",
			"................",
			"........4.......",
			".......44.......",
			".......454......",
			"......4554......",
			"......4554......",
			".......44.......",
			"................",
			"................"};

	/** 18 × 18: a habanero with heat rising off it. */
	static final String[] SPICY_ICON = {
			"...3..............",
			"..3...3...........",
			"...3.3............",
			"......3.....BA....",
			"....3......AC.....",
			".........BLCB.....",
			"......DDBCLCBD....",
			".....DFFHBCBGcE...",
			"....DFHIHFDGcGGE..",
			"....DFHHFGGcGGDE..",
			"...DFFFGGGcGGDDE..",
			"...DFGGGcGGGDDE...",
			"...DGGGcGGGDDE....",
			"...EDGcGGGDDE.....",
			"...EDcGGDDEE......",
			"....EDDDEE........",
			"...EDDE...........",
			"...EE............."};

	static BufferedImage grid(String[] rows) {
		return grid(rows, PALETTE);
	}

	static BufferedImage grid(String[] rows, Map<Character, Integer> palette) {
		int width = rows[0].length();
		BufferedImage out = new BufferedImage(width, rows.length, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < rows.length; y++) {
			if (rows[y].length() != width) {
				throw new IllegalStateException("row " + y + " is " + rows[y].length() + " wide, not " + width + ": " + rows[y]);
			}
			for (int x = 0; x < width; x++) {
				char c = rows[y].charAt(x);
				if (c != '.') {
					Integer rgb = palette.get(c);
					if (rgb == null) {
						throw new IllegalStateException("no colour for '" + c + "'");
					}
					out.setRGB(x, y, rgb);
				}
			}
		}
		return out;
	}

	/**
	 * Vanilla's potion: the glass (potion.png) over its liquid (potion_overlay.png, a grey mask), the liquid
	 * shaded from {@code dark} (the mask's darkest grey) to {@code light} (its lightest), with {@code inside}
	 * drawn on the liquid if given.
	 */
	static BufferedImage bottle(BufferedImage glass, BufferedImage mask, int dark, int light, String[] inside) {
		BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		BufferedImage extra = inside == null ? null : grid(inside);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int m = mask.getRGB(x, y);
				if ((m >>> 24) != 0) {
					double t = (m & 0xFF) / 255.0;
					int liquid = mix(dark, light, t);
					if (extra != null && (extra.getRGB(x, y) >>> 24) != 0) {
						liquid = extra.getRGB(x, y);
					}
					out.setRGB(x, y, liquid);
				}
				int g = glass.getRGB(x, y);
				if ((g >>> 24) != 0) {
					out.setRGB(x, y, over(out.getRGB(x, y), g));
				}
			}
		}
		return out;
	}

	/** {@code top} drawn over {@code bottom}, with its alpha. */
	static int over(int bottom, int top) {
		double a = (top >>> 24) / 255.0;
		if ((bottom >>> 24) == 0) {
			return top;
		}
		return mix(bottom, top | 0xFF000000, a);
	}

	static int mix(int a, int b, double t) {
		int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	static BufferedImage read(ZipFile jar, String path) throws Exception {
		try (InputStream in = jar.getInputStream(jar.getEntry(path))) {
			return ImageIO.read(in);
		}
	}

	static void write(BufferedImage image, String path) throws Exception {
		Path file = OUT.resolve(path);
		Files.createDirectories(file.getParent());
		ImageIO.write(image, "png", file.toFile());
	}
}

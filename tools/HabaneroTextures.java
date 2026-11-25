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
			Map.entry('D', 0xFF6E1A0C),
			Map.entry('R', 0xFFC2361A),
			Map.entry('O', 0xFFE9621D),
			Map.entry('Y', 0xFFF6972F),
			Map.entry('H', 0xFFFFD27E),
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
		write(grid(HABANERO), "item/habanero.png");
		write(grid(SEEDS), "item/habanero_seeds.png");
		write(grid(STAGE_0), "block/habanero_stage0.png");
		write(grid(STAGE_1), "block/habanero_stage1.png");
		write(grid(STAGE_2), "block/habanero_stage2.png");
		write(grid(STAGE_3), "block/habanero_stage3.png");
		write(grid(WILD), "block/wild_habanero.png");
		write(grid(FLAME_GLYPH), "font/addon_flame.png");
		write(grid(SPICY_ICON), "mob_effect/spicy.png");
		try (ZipFile jar = new ZipFile(args[0])) {
			BufferedImage bottle = read(jar, "assets/minecraft/textures/item/potion.png");
			BufferedImage liquid = read(jar, "assets/minecraft/textures/item/potion_overlay.png");
			write(bottle(bottle, liquid, 0xFF9E1A0E, 0xFFE0481E, null), "item/hot_sauce.png");
			write(bottle(bottle, liquid, 0xFFD2521A, 0xFFFFC248, FLAME_IN_BOTTLE), "item/flame_extract.png");
		}
		System.out.println("done");
	}

	// --- Pixel grids (16 × 16 unless noted; '.' is transparent) ---

	static final String[] HABANERO = {
			"................",
			"........g.......",
			".......gG.......",
			"......glGg......",
			"....DDgGGDD.....",
			"...DROOOOOOD....",
			"...DROYYHOORD...",
			"..DROYHYOOORD...",
			"..DRROYOOORRD...",
			"..DRROOOORRD....",
			"...DRRROORRD....",
			"...DRRRORRD.....",
			"....DRRRRD......",
			".....DRRD.......",
			"......DRD.......",
			".......D........"};

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
			"..................",
			"....3.....3.......",
			"...3.....3....3...",
			"...3.....3...3....",
			"....3.....3..3....",
			"..........g...3...",
			".........gG.......",
			"........glGg......",
			".......DgGGD......",
			"......DROOOOD.....",
			".....DROYYHORD....",
			".....DROYHYOORD...",
			"....DRROYYOOORD...",
			"....DRROOYOORRD...",
			".....DRRROORRD....",
			".....DDRRRRRDD....",
			"......DDRRRDD.....",
			"........DDD......."};

	static BufferedImage grid(String[] rows) {
		int width = rows[0].length();
		BufferedImage out = new BufferedImage(width, rows.length, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < rows.length; y++) {
			if (rows[y].length() != width) {
				throw new IllegalStateException("row " + y + " is " + rows[y].length() + " wide, not " + width + ": " + rows[y]);
			}
			for (int x = 0; x < width; x++) {
				char c = rows[y].charAt(x);
				if (c != '.') {
					Integer rgb = PALETTE.get(c);
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

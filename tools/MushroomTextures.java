import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * Generates the giant mushrooms' drop icons (run from the project root):
 *
 * <pre>
 *   java tools/MushroomTextures.java
 * </pre>
 *
 *  - gui/sprites/hud/spores.png: the spore counter's icon, a little pile of spore dust like vanilla's
 *    glowstone dust, a dusty rose-taupe like a spore print.
 *  - item/mushroom_sample.png: a torn chunk of mushroom: spotted red skin on top, pale flesh with gill
 *    lines along the torn edge.
 *  - block/regrown_*.png: the cap blocks a sprout's burst grows back, blighted versions of vanilla's
 *    red and brown mushroom blocks and mushroom inside (red turns a bruised mauve, brown an ashen grey).
 *    Made from vanilla's own textures, read out of the 1.21.1 client jar (Loom's copy in the Gradle cache,
 *    or the jar given as the first argument): each colour moves to the same place on a new ramp, so the
 *    grain of the texture is kept.
 *
 * Hand-placed pixel grids, one character per pixel, in one palette ({@link #PALETTE}); '.' is clear.
 */
public class MushroomTextures {
	static final Path OUT = Path.of("src/main/resources/assets/frostbound/textures");

	static final Map<Character, Integer> PALETTE = Map.ofEntries(
			// Spore dust: a dusty rose-taupe, like a spore print
			Map.entry('O', 0xFF3A1F22), // rim, underneath
			Map.entry('d', 0xFF6A3B3A), // dust, darkest
			Map.entry('m', 0xFF975C50), // dust, dark
			Map.entry('n', 0xFFBA8068), // dust
			Map.entry('l', 0xFFD9AA8C), // dust, light
			Map.entry('h', 0xFFF0D2BA), // dust, highlight
			Map.entry('w', 0xFFFFF3E6), // glint
			// Mushroom chunk
			Map.entry('K', 0xFF2E1012), // outline
			Map.entry('r', 0xFF7E1A1C), // skin, darkest
			Map.entry('R', 0xFFAE2622), // skin, dark
			Map.entry('S', 0xFFD23A2C), // skin
			Map.entry('T', 0xFFEC6146), // skin, light
			Map.entry('W', 0xFFF6EEE2), // wart
			Map.entry('V', 0xFFCBBDAA), // wart, shadow
			Map.entry('F', 0xFFF3E6CC), // flesh, light
			Map.entry('f', 0xFFDCC6A1), // flesh
			Map.entry('g', 0xFFB89A73), // gills
			Map.entry('G', 0xFF876A4C)); // gills, deep

	public static void main(String[] args) throws Exception {
		// A little pile of spore dust in the style of vanilla's dusts (glowstone, redstone, sugar): a pointed heap,
		// a darker rim all round (darkest underneath), crisp grains, lit from the top left.
		BufferedImage spores = grid(
				"................",
				"................",
				"................",
				".......dd.......",
				"......dnhd......",
				".....dlhwnd.....",
				"....dnlnmlnd....",
				"...dlhmnlhnmd...",
				"..dnlnnhlmnnmd..",
				"..dmlnlnmnlmnd..",
				"..dnnmnlnmmdmO..",
				"...dmnmnmnmdO...",
				"....OmdnmmdO....",
				"......OOOO......",
				"................",
				"................");
		write(spores, "gui/sprites/hud/spores.png");

		// A chunk torn off a cap: red skin with two warts on top, a clean-cut face of pale flesh on the
		// left, a ragged tear at the top right, gills hanging off the torn underside.
		write(grid(
				"................",
				"................",
				"......KKKK......",
				"....KKTTTSK.....",
				"...KTTWWTSSKKK..",
				"...KTWWVSSSKgRK.",
				"..KFSVVSSSWSRRK.",
				"..KFSSSSSSVRRrK.",
				"..KFfSSSRRRRrrK.",
				"..KFfFSRRRrrKKK.",
				"..KFffFRrKKgGK..",
				"..KFfgfKKggGK...",
				"..KfgGfgGgGK....",
				"...KGKgGKgK.....",
				"....K.KK.K......",
				"................"), "item/mushroom_sample.png");

		// Regrown cap blocks: vanilla's mushroom blocks, blighted.
		Path jar = args.length > 0 ? Path.of(args[0])
				: Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.21.1/minecraft-client.jar");
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			write(blight(vanilla(zip, "red_mushroom_block"), ramp(0x7A5360, 0x86606B, 0x926C74, 0x9E787E), ramp(0xCFC9A4, 0xE6E1C0)),
					"block/regrown_red_mushroom_block.png");
			write(blight(vanilla(zip, "brown_mushroom_block"), ramp(0x6A6560, 0x76706A, 0x807A72, 0x8B857B), null),
					"block/regrown_brown_mushroom_block.png");
			write(blight(vanilla(zip, "mushroom_block_inside"), ramp(0x8E8B78, 0x9C9884, 0xA8A38E, 0xB2AD96, 0xBCB79F), null),
					"block/regrown_mushroom_block_inside.png");
		}
		System.out.println("done");
	}

	static BufferedImage vanilla(ZipFile zip, String name) throws Exception {
		var entry = zip.getEntry("assets/minecraft/textures/block/" + name + ".png");
		if (entry == null) {
			throw new IllegalArgumentException("no " + name + " in " + zip.getName());
		}
		try (var in = zip.getInputStream(entry)) {
			return ImageIO.read(in);
		}
	}

	static int[] ramp(int... rgb) {
		return rgb;
	}

	static double luminance(int argb) {
		return 0.299 * (argb >> 16 & 0xFF) + 0.587 * (argb >> 8 & 0xFF) + 0.114 * (argb & 0xFF);
	}

	/**
	 * Recolours a texture: its colours, darkest to lightest, are spread over {@code body} (dark to light).
	 * With {@code spots}, colours brighter than 200 (the red block's white spots) go to {@code spots} instead.
	 */
	static BufferedImage blight(BufferedImage source, int[] body, int[] spots) {
		Set<Integer> seen = new LinkedHashSet<>();
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				seen.add(source.getRGB(x, y));
			}
		}
		List<Integer> colours = new ArrayList<>(seen);
		colours.sort(Comparator.comparingDouble(MushroomTextures::luminance));
		List<Integer> skin = new ArrayList<>(), light = new ArrayList<>();
		for (int c : colours) {
			(spots != null && luminance(c) >= 200 ? light : skin).add(c);
		}
		Map<Integer, Integer> to = new HashMap<>();
		for (int i = 0; i < skin.size(); i++) {
			to.put(skin.get(i), 0xFF000000 | body[(int) Math.round(i * (body.length - 1.0) / Math.max(1, skin.size() - 1))]);
		}
		for (int i = 0; i < light.size(); i++) {
			to.put(light.get(i), 0xFF000000 | spots[Math.min(i, spots.length - 1)]);
		}
		BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				out.setRGB(x, y, to.get(source.getRGB(x, y)));
			}
		}
		return out;
	}

	static BufferedImage grid(String... rows) {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		if (rows.length != 16) {
			throw new IllegalArgumentException(rows.length + " rows");
		}
		for (int y = 0; y < 16; y++) {
			if (rows[y].length() != 16) {
				throw new IllegalArgumentException("row " + y + " is " + rows[y].length() + " wide: " + rows[y]);
			}
			for (int x = 0; x < 16; x++) {
				char c = rows[y].charAt(x);
				if (c != '.') {
					Integer color = PALETTE.get(c);
					if (color == null) {
						throw new IllegalArgumentException("no colour for '" + c + "'");
					}
					img.setRGB(x, y, color);
				}
			}
		}
		return img;
	}

	static void write(BufferedImage image, String path) throws Exception {
		Path file = OUT.resolve(path);
		Files.createDirectories(file.getParent());
		ImageIO.write(image, "png", file.toFile());
	}
}

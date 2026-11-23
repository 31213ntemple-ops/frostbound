import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipFile;

/**
 * Generates the Alpha Wolf's textures (run from the project root):
 *
 * <pre>
 *   java tools/AlphaWolfTextures.java path/to/minecraft-client-1.21.1.jar
 * </pre>
 *
 * (Loom keeps the client jar at ~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar.)
 *
 *  - textures/entity/wolf_sleeping/*.png: every vanilla wolf coat with its eyes closed, for den wolves asleep.
 *  - textures/entity/alpha_wolf*.png: the Alpha's coat (the black wolf with a frost-tipped ruff and a
 *    scar over one eye), angry and asleep.
 *  - the Alpha Hide armour layers and the item icons (Alpha Hide, the four armour pieces, the
 *    Mark of the Pack and the Wolf Tooth), drawn here pixel by pixel so they share one palette.
 *
 * Deterministic (fixed seeds), so running it again gives the same files.
 */
public class AlphaWolfTextures {
	static final Path OUT = Path.of("src/main/resources/assets/frostbound/textures");
	static final String[] COATS = {"wolf", "wolf_ashen", "wolf_black", "wolf_chestnut", "wolf_rusty", "wolf_snowy", "wolf_spotted",
			"wolf_striped", "wolf_woods"};

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.err.println("usage: java tools/AlphaWolfTextures.java <minecraft-client.jar>");
			System.exit(1);
		}
		try (ZipFile jar = new ZipFile(args[0])) {
			for (String coat : COATS) {
				BufferedImage wild = read(jar, "assets/minecraft/textures/entity/wolf/" + coat + ".png");
				write(closeEyes(wild), "entity/wolf_sleeping/" + coat + ".png");
			}
			BufferedImage black = read(jar, "assets/minecraft/textures/entity/wolf/wolf_black.png");
			BufferedImage blackAngry = read(jar, "assets/minecraft/textures/entity/wolf/wolf_black_angry.png");
			write(alphaCoat(blackAngry), "entity/alpha_wolf.png");
			write(closeEyes(alphaCoat(black)), "entity/alpha_wolf_sleeping.png");
		}
		ArmorArt.generate();
		System.out.println("done");
	}

	// --- Wolves ---

	/** Eyes shut: each eye (row 6 of the face: x 4–5 and 8–9) becomes a dark slit in the fur colour. */
	static BufferedImage closeEyes(BufferedImage source) {
		BufferedImage out = copy(source);
		for (int x : new int[] {4, 5, 8, 9}) {
			int fur = source.getRGB(x, 7);
			out.setRGB(x, 6, mix(fur, 0xFF000000, x == 4 || x == 9 ? 0.35 : 0.55));
		}
		return out;
	}

	/** The Alpha: the black wolf with a frost-tipped ruff (the upper body) and a pale scar over its right eye. */
	static BufferedImage alphaCoat(BufferedImage source) {
		BufferedImage out = copy(source);
		Random random = new Random(1802);
		int[] frost = {0xFF8D97A6, 0xFFA9B4C2, 0xFF6F7887, 0xFFC4CDD8};
		for (int y = 0; y <= 12; y++) {
			for (int x = 21; x <= 50; x++) {
				boolean unused = y <= 6 && (x <= 27 || x >= 44);
				if (unused) {
					continue;
				}
				// Thicker frost towards the edges of each face (the tips of the fur).
				double chance = y <= 6 ? 0.28 : (y == 7 || y == 12) ? 0.55 : 0.32;
				if (random.nextDouble() < chance) {
					out.setRGB(x, y, mix(source.getRGB(x, y), frost[random.nextInt(frost.length)], 0.55 + random.nextDouble() * 0.35));
				}
			}
		}
		// Grey muzzle and brow, like an old wolf's.
		for (int[] p : new int[][] {{1, 11}, {2, 11}, {3, 12}, {6, 10}, {7, 11}, {5, 4}, {6, 4}}) {
			out.setRGB(p[0], p[1], mix(source.getRGB(p[0], p[1]), 0xFF8D97A6, 0.45));
		}
		// The scar: across the brow and cheek of the right eye (x 8–9), not through it.
		for (int[] p : new int[][] {{9, 4}, {8, 5}, {9, 7}, {9, 8}}) {
			out.setRGB(p[0], p[1], 0xFF9C8F94);
		}
		return out;
	}

	// --- Helpers ---

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

	static BufferedImage copy(BufferedImage source) {
		BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				out.setRGB(x, y, source.getRGB(x, y));
			}
		}
		return out;
	}

	/** {@code a} blended towards {@code b} by {@code t}, keeping {@code a}'s alpha. */
	static int mix(int a, int b, double t) {
		int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return (a & 0xFF000000) | r << 16 | g << 8 | bl;
	}
}

/**
 * The Alpha Hide set's art, in one palette: black wolf fur with frost-silver tips, dark tanned
 * leather, bone. Used by AlphaWolfTextures (run that one).
 *
 * Icons are hand-placed pixel grids (one character per pixel, see {@link #PALETTE}); symmetric ones
 * give only the left half, mirrored. The armour layers are painted: fur and leather with a little
 * noise, then straps, trims, toggles and frost tips on top.
 */
class ArmorArt {
	static final Path OUT = AlphaWolfTextures.OUT;

	static final Map<Character, Integer> PALETTE = Map.ofEntries(
			Map.entry('K', 0xFF121014), // outline
			Map.entry('D', 0xFF1E1B23), // fur, darkest
			Map.entry('d', 0xFF29252F), // fur, dark
			Map.entry('M', 0xFF37323E), // fur
			Map.entry('G', 0xFF4C4755), // fur, light
			Map.entry('S', 0xFF7C8696), // frost tip
			Map.entry('W', 0xFFB4BFCD), // frost, light
			Map.entry('w', 0xFFE1E8F0), // frost, highlight
			Map.entry('L', 0xFF2B1A12), // leather, darkest
			Map.entry('l', 0xFF45291B), // leather, dark
			Map.entry('h', 0xFF633D25), // leather
			Map.entry('H', 0xFF825233), // leather, light
			Map.entry('B', 0xFFF0E7D0), // bone, light
			Map.entry('b', 0xFFCDBE9E), // bone
			Map.entry('c', 0xFF968767), // bone, dark
			Map.entry('R', 0xFFA8222C), // red
			Map.entry('r', 0xFF5C1219), // red, dark
			Map.entry('I', 0xFFD5DAE1), // silver
			Map.entry('i', 0xFF8B929D), // silver, dark
			Map.entry('p', 0xFF5A3A3E)); // inner ear

	static void generate() throws Exception {
		icons();
		layer1();
		layer2();
		hoodExtras();
	}

	// --- Icons ---

	static void icons() throws Exception {
		// The hood: the Alpha's head, ears up, frost on the brow, the upper jaw hanging over the face.
		write(mirrored(
				"........",
				"..KK....",
				".KSWKKKK",
				".KdSMGdd",
				".KdMMGGd",
				".KdMGSWW",
				".KdSWKKK",
				".KdK.KMG",
				".KdK.KdM",
				".KdK.KBK",
				".KSK..KB",
				"..KK....",
				"........",
				"........",
				"........",
				"........"), "item/alpha_hide_helmet.png");

		// The tunic: a fur mantle over the shoulders, a strap with bone toggles across tanned leather.
		write(mirrored(
				"........",
				"........",
				".KKKKK..",
				".KWSSdK.",
				".KSWdMhK",
				".KdSMhlh",
				".KdMKhHl",
				"..KKKhHL",
				"...KhHLB",
				"...KhLLh",
				"...KLLhH",
				"...KLhHh",
				"...KlSWS",
				"....KSWw",
				".....KKK",
				"........"), "item/alpha_hide_chestplate.png");

		write(mirrored(
				"........",
				"........",
				"....KKKK",
				"...KSWSW",
				"...KhLLB",
				"...KhHhh",
				"...KhHKK",
				"...KdGK.",
				"...KMSK.",
				"...KhHK.",
				"...KLhK.",
				"...KhLK.",
				"...KlhK.",
				"...KKKK.",
				"........",
				"........"), "item/alpha_hide_leggings.png");

		// Boots: frost-tipped fur cuffs, laced leather, claws at the toes.
		write(mirrored(
				"........",
				"........",
				"........",
				"...KKK..",
				"..KSWSK.",
				"..KdSdK.",
				"..KhHlK.",
				"..KhLhK.",
				"..KhHlK.",
				".KlhLhK.",
				"KlhHhlK.",
				"KLlllLK.",
				"KBKBKKK.",
				"........",
				"........",
				"........"), "item/alpha_hide_boots.png");

		// The pelt, laid out flat: ears, the frost ruff, legs splayed, the tail.
		write(mirrored(
				"........",
				"....K...",
				"....KSK.",
				"....KdMK",
				".....KGd",
				".KK...KK",
				"KSdK.KSW",
				".KdMKSWw",
				"..KKMdSM",
				"....KdMG",
				"....KDdM",
				"..KKKDdG",
				".KMdDDdM",
				"KSdKKKDd",
				".KK...KD",
				".......K"), "item/alpha_hide.png");

		// The Mark of the Pack: the Alpha's fang in a silver cap, on a braided cord with two red beads.
		write(grid(
				".K............K.",
				".hK..........Kh.",
				"..lK........Kl..",
				"..KhK......KhK..",
				"...KlK....KlK...",
				"....RK....KR....",
				"....KhK..KhK....",
				".....KlKKlK.....",
				".....KIIIIK.....",
				".....KiIIiK.....",
				"......KBBbK.....",
				"......KBBbK.....",
				"......KBbcK.....",
				".......KBcK.....",
				"........KbK.....",
				".........K......"), "item/mark_of_the_pack.png");

		write(grid(
				"................",
				"................",
				".....KKKK.......",
				"....KrRRrK......",
				"....KcbbcK......",
				"....KBBbcK......",
				".....KBBbK......",
				".....KBBbcK.....",
				"......KBbcK.....",
				"......KBBbK.....",
				".......KBbcK....",
				".......KBbK.....",
				"........KbK.....",
				"........KK......",
				"................",
				"................"), "item/wolf_tooth.png");
	}

	// --- Armour, layer 1: hood, tunic and arms, boots ---

	static void layer1() throws Exception {
		BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		Random rnd = new Random(31);

		// Hood (head box at 0,0; 8×8×8). Top: fur with a frost streak down the middle like the Alpha's crown.
		fur(img, rnd, 8, 0, 8, 8);
		for (int y = 0; y < 8; y++) {
			px(img, 11, y, y % 2 == 0 ? 'S' : 'W');
			px(img, 12, y, y % 3 == 0 ? 'S' : 'G');
		}
		// Sides and back: fur all the way down, frost-tipped at the hem.
		fur(img, rnd, 0, 8, 8, 8);
		fur(img, rnd, 16, 8, 8, 8);
		fur(img, rnd, 24, 8, 8, 8);
		frostHem(img, rnd, 0, 15, 8);
		frostHem(img, rnd, 16, 15, 8);
		frostHem(img, rnd, 24, 14, 8);
		frostHem(img, rnd, 24, 15, 8);
		// Front: just the brow (the jaw is modelled); the face shows through below, framed by the flaps.
		fur(img, rnd, 8, 8, 8, 3);
		for (int x = 8; x < 16; x++) {
			px(img, x, 10, x == 8 || x == 15 ? 'd' : (x % 2 == 0 ? 'W' : 'S'));
		}
		for (int y = 11; y < 16; y++) {
			px(img, 8, y, y == 15 ? 'S' : 'd');
			px(img, 15, y, y == 15 ? 'S' : 'd');
		}
		// Hat layer: a ragged frost-tipped fringe hanging round the sides and back.
		for (int[] face : new int[][] {{32, 8}, {48, 8}, {56, 8}}) {
			for (int x = face[0]; x < face[0] + 8; x++) {
				if (rnd.nextFloat() < 0.6f) {
					px(img, x, 15, rnd.nextBoolean() ? 'S' : 'W');
					if (rnd.nextFloat() < 0.4f) {
						px(img, x, 14, 'G');
					}
				}
			}
		}

		// Tunic (body box at 16,16; 8×12×4): a fur mantle over the shoulders, tanned leather below.
		fur(img, rnd, 20, 16, 8, 4);              // top
		leather(img, rnd, 28, 16, 8, 4);          // bottom
		leather(img, rnd, 16, 20, 4, 12);         // right side
		leather(img, rnd, 20, 20, 8, 12);         // front
		leather(img, rnd, 28, 20, 4, 12);         // left side
		leather(img, rnd, 32, 20, 8, 12);         // back
		fur(img, rnd, 16, 20, 24, 3);             // mantle all round
		fur(img, rnd, 32, 23, 8, 4);              // hanging lower at the back, like a cape
		raggedFrost(img, rnd, 16, 23, 16);
		raggedFrost(img, rnd, 32, 27, 8);
		// A strap across the chest, stitched, with bone toggles; a closure of toggles down the middle.
		for (int i = 0; i < 9; i++) {
			int x = 20 + Math.min(7, i * 7 / 8);
			int y = 23 + i;
			px(img, x, y, 'L');
			if (x + 1 < 28) {
				px(img, x + 1, y, 'l');
			}
		}
		px(img, 22, 25, 'B');
		px(img, 25, 28, 'B');
		px(img, 24, 24, 'b');
		px(img, 23, 27, 'b');
		for (int x = 20; x < 28; x++) {
			px(img, x, 31, x % 2 == 0 ? 'L' : 'l');   // hem
		}
		for (int x = 32; x < 40; x++) {
			px(img, x, 31, x % 2 == 0 ? 'L' : 'l');
		}

		// Arms (40,16; 4×12×4): fur pauldrons, leather sleeves with a strap, fur cuffs.
		fur(img, rnd, 44, 16, 4, 4);
		leather(img, rnd, 48, 16, 4, 4);
		leather(img, rnd, 40, 20, 16, 12);
		fur(img, rnd, 40, 20, 16, 4);
		raggedFrost(img, rnd, 40, 24, 16);
		for (int x = 40; x < 56; x++) {
			px(img, x, 27, 'L');
			fur(img, rnd, x, 30, 1, 2);
			px(img, x, 30, rnd.nextBoolean() ? 'S' : 'W');
		}
		px(img, 45, 27, 'B');

		// Boots (legs at 0,16; 4×12×4, only the lower part): frost-tipped fur cuffs, dark leather, laces, claws.
		leather(img, rnd, 4, 16, 4, 4);           // top (hidden)
		dark(img, 8, 16, 4, 4, 'L');              // sole
		leather(img, rnd, 0, 26, 16, 6);
		for (int x = 0; x < 16; x++) {
			fur(img, rnd, x, 24, 1, 2);
			px(img, x, 24, rnd.nextFloat() < 0.6f ? 'W' : 'S');
			px(img, x, 31, 'L');
		}
		for (int y = 27; y < 31; y++) {                // laces up the front
			px(img, 5, y, y % 2 == 0 ? 'H' : 'l');
			px(img, 6, y, y % 2 == 0 ? 'l' : 'H');
		}
		px(img, 4, 31, 'B');                           // claws at the toe
		px(img, 6, 31, 'B');
		write(img, "models/armor/alpha_hide_layer_1.png");
	}

	// --- Armour, layer 2: leggings ---

	static void layer2() throws Exception {
		BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		Random rnd = new Random(32);
		// Waist (body box, lower rows): a fur-trimmed belt with a bone buckle.
		leather(img, rnd, 16, 26, 24, 6);
		for (int x = 16; x < 40; x++) {
			px(img, x, 26, rnd.nextBoolean() ? 'S' : 'W');
			px(img, x, 27, 'G');
			px(img, x, 29, 'L');
			px(img, x, 30, 'L');
		}
		px(img, 23, 29, 'B');
		px(img, 24, 29, 'B');
		px(img, 23, 30, 'b');
		px(img, 24, 30, 'c');
		// Legs (0,16; 4×12×4): leather trousers, fur over the knees, wrapped shins.
		leather(img, rnd, 0, 20, 16, 12);
		leather(img, rnd, 4, 16, 4, 4);
		for (int x = 0; x < 16; x++) {
			px(img, x, 20, 'l');
		}
		fur(img, rnd, 0, 23, 16, 2);
		for (int x = 0; x < 16; x++) {
			px(img, x, 25, rnd.nextFloat() < 0.5f ? 'S' : 'G');
		}
		for (int y = 27; y < 32; y++) {
			for (int x = 0; x < 16; x++) {
				if ((x + y) % 4 == 0) {
					px(img, x, y, 'L');
				} else if ((x + y) % 4 == 1) {
					px(img, x, y, 'l');
				}
			}
		}
		write(img, "models/armor/alpha_hide_layer_2.png");
	}

	// --- The hood's ears and snout (AlphaHoodRenderer, 32×16) ---

	static void hoodExtras() throws Exception {
		BufferedImage img = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
		Random rnd = new Random(33);
		for (int ear = 0; ear < 2; ear++) {
			int u = ear * 6;
			fur(img, rnd, u, 0, 6, 4);
			px(img, u + 1, 0, 'W');
			px(img, u + 2, 0, 'S');
			// Front: inner ear; tips frosted.
			px(img, u + 1, 1, 'S');
			px(img, u + 2, 1, 'W');
			px(img, u + 1, 2, 'p');
			px(img, u + 2, 2, 'p');
			px(img, u + 1, 3, 'D');
			px(img, u + 2, 3, 'p');
		}
		// Snout (4×2×4 at 0,4): fur on top with a frost ridge, nose and lip at the front, teeth underneath.
		fur(img, rnd, 0, 4, 16, 6);
		for (int y = 4; y < 8; y++) {
			px(img, 5, y, 'S');
			px(img, 6, y, y % 2 == 0 ? 'W' : 'S');
		}
		for (int x = 8; x < 12; x++) {
			for (int y = 4; y < 8; y++) {
				px(img, x, y, 'r');
			}
		}
		for (int i = 0; i < 4; i++) {
			px(img, 8 + i, 4, i % 2 == 0 ? 'B' : 'b');
			px(img, 8 + i, 7, i % 2 == 0 ? 'b' : 'B');
			px(img, 8, 4 + i, i % 2 == 0 ? 'B' : 'b');
			px(img, 11, 4 + i, i % 2 == 0 ? 'b' : 'B');
		}
		// Front face (4,8): the nose on the fur, and a full row of fangs under it.
		px(img, 4, 8, 'd');
		px(img, 5, 8, 'K');
		px(img, 6, 8, 'K');
		px(img, 7, 8, 'd');
		for (int x = 4; x < 8; x++) {
			px(img, x, 9, x == 4 || x == 7 ? 'B' : 'b');
		}
		for (int x = 0; x < 4; x++) {
			px(img, x, 9, x % 2 == 0 ? 'B' : 'D');
			px(img, x + 8, 9, x % 2 == 0 ? 'D' : 'B');
		}
		write(img, "models/armor/alpha_hood.png");
	}

	// --- Painting helpers ---

	static void fur(BufferedImage img, Random rnd, int x0, int y0, int w, int h) {
		char[] tones = {'D', 'd', 'd', 'M', 'M', 'G'};
		for (int y = y0; y < y0 + h; y++) {
			for (int x = x0; x < x0 + w; x++) {
				px(img, x, y, tones[rnd.nextInt(tones.length)]);
			}
		}
	}

	static void leather(BufferedImage img, Random rnd, int x0, int y0, int w, int h) {
		char[] tones = {'l', 'h', 'h', 'h', 'H'};
		for (int y = y0; y < y0 + h; y++) {
			for (int x = x0; x < x0 + w; x++) {
				px(img, x, y, tones[rnd.nextInt(tones.length)]);
			}
		}
	}

	static void dark(BufferedImage img, int x0, int y0, int w, int h, char c) {
		for (int y = y0; y < y0 + h; y++) {
			for (int x = x0; x < x0 + w; x++) {
				px(img, x, y, c);
			}
		}
	}

	/** Frost tips along a hem: a row of silver, the odd one reaching a pixel further. */
	static void frostHem(BufferedImage img, Random rnd, int x0, int y, int w) {
		for (int x = x0; x < x0 + w; x++) {
			px(img, x, y, rnd.nextFloat() < 0.55f ? 'W' : 'S');
		}
	}

	/** The fur's frost-tipped lower edge, uneven: some tips hang a pixel lower over what's below. */
	static void raggedFrost(BufferedImage img, Random rnd, int x0, int y, int w) {
		for (int x = x0; x < x0 + w; x++) {
			px(img, x, y - 1, rnd.nextFloat() < 0.5f ? 'S' : 'G');
			if (rnd.nextFloat() < 0.55f) {
				px(img, x, y, rnd.nextFloat() < 0.5f ? 'W' : 'S');
			}
		}
	}

	static void px(BufferedImage img, int x, int y, char c) {
		img.setRGB(x, y, PALETTE.get(c));
	}

	static BufferedImage grid(String... rows) {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < rows.length; y++) {
			if (rows[y].length() != 16) {
				throw new IllegalArgumentException("row " + y + " is " + rows[y].length() + " wide: " + rows[y]);
			}
			for (int x = 0; x < 16; x++) {
				char c = rows[y].charAt(x);
				if (c != '.') {
					px(img, x, y, c);
				}
			}
		}
		return img;
	}

	/** A symmetric icon from its left half (8 wide). */
	static BufferedImage mirrored(String... halves) {
		String[] rows = new String[halves.length];
		for (int i = 0; i < halves.length; i++) {
			if (halves[i].length() != 8) {
				throw new IllegalArgumentException("half row " + i + " is " + halves[i].length() + " wide: " + halves[i]);
			}
			rows[i] = halves[i] + new StringBuilder(halves[i]).reverse();
		}
		return grid(rows);
	}

	static void write(BufferedImage image, String path) throws Exception {
		Path file = OUT.resolve(path);
		Files.createDirectories(file.getParent());
		ImageIO.write(image, "png", file.toFile());
	}
}

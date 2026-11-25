package com.frostbound.client.campfire;

import com.frostbound.campfire.CampfireWarmth;
import com.frostbound.config.FrostboundConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * A glowing ring on the ground round each lit campfire near you, at the edge of where it warms you
 * ({@link CampfireWarmth}). It follows the ground (up and down steps, over snow), brightens while you're
 * inside, and fades out as you walk away. Only with Tough As Nails (without it campfires don't warm
 * anything); switched in Options → Video Settings → Campfire Warmth Ring, saved in frostbound.json.
 */
public final class WarmthRing {
	public static final String OPTION_KEY = "options.frostbound.campfire_ring";
	private static final int SEGMENTS = 96;
	/** Width of the band, in blocks. */
	private static final double BAND = 0.2;
	/** How far past the ring you can still see it (it fades out over the last half of this). */
	private static final double SHOW_WITHIN = 12.0;
	private static final int RED = 255, GREEN = 150, BLUE = 60;

	private static final List<BlockPos> CAMPFIRES = new ArrayList<>();
	private static int scanIn;

	private WarmthRing() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(WarmthRing::tick);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(WarmthRing::render);
	}

	/** Campfires only warm with Tough As Nails, so the ring and its option only exist with it. */
	public static boolean available() {
		return FabricLoader.getInstance().isModLoaded("toughasnails");
	}

	public static boolean shown() {
		return available() && FrostboundConfig.get().campfire.showWarmthRing;
	}

	/** The on/off button in Video Settings (mixin.campfire.client.VideoOptionsScreenMixin). */
	public static SimpleOption<Boolean> option() {
		return SimpleOption.ofBoolean(OPTION_KEY,
				SimpleOption.constantTooltip(Text.translatable(OPTION_KEY + ".tooltip")),
				FrostboundConfig.get().campfire.showWarmthRing,
				value -> {
					FrostboundConfig.get().campfire.showWarmthRing = value;
					FrostboundConfig.save();
				});
	}

	/** Every half second, the lit campfires close enough to draw (from the loaded chunks' block entities). */
	private static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		PlayerEntity player = client.player;
		if (world == null || player == null || !shown()) {
			CAMPFIRES.clear();
			return;
		}
		if (--scanIn > 0) {
			return;
		}
		scanIn = 10;
		CAMPFIRES.clear();
		double reach = CampfireWarmth.ringRadius() + SHOW_WITHIN;
		int minX = MathHelper.floor(player.getX() - reach) >> 4, maxX = MathHelper.floor(player.getX() + reach) >> 4;
		int minZ = MathHelper.floor(player.getZ() - reach) >> 4, maxZ = MathHelper.floor(player.getZ() + reach) >> 4;
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				if (!world.isChunkLoaded(cx, cz)) {
					continue;
				}
				for (BlockEntity entity : world.getChunk(cx, cz).getBlockEntities().values()) {
					BlockPos pos = entity.getPos();
					if (CampfireWarmth.isWarming(world.getBlockState(pos)) && horizontalDistance(player.getPos(), pos) <= reach
							&& Math.abs(player.getY() - pos.getY()) <= SHOW_WITHIN) {
						CAMPFIRES.add(pos);
					}
				}
			}
		}
	}

	private static void render(WorldRenderContext context) {
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (CAMPFIRES.isEmpty() || player == null || !shown()) {
			return;
		}
		ClientWorld world = context.world();
		VertexConsumerProvider consumers = context.consumers();
		MatrixStack matrices = context.matrixStack();
		if (consumers == null || matrices == null) {
			return;
		}
		Vec3d camera = context.camera().getPos();
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getDebugQuads());
		double radius = CampfireWarmth.ringRadius();
		float time = (world.getTime() % 1000L + context.tickCounter().getTickDelta(false)) / 20.0f;
		for (BlockPos campfire : CAMPFIRES) {
			if (!CampfireWarmth.isWarming(world.getBlockState(campfire))) {
				continue;
			}
			boolean inside = CampfireWarmth.warms(campfire, player.getBlockPos());
			double past = horizontalDistance(player.getPos(), campfire) - radius;
			float fade = (float) MathHelper.clamp(1.0 - (past - SHOW_WITHIN / 2) / (SHOW_WITHIN / 2), 0.0, 1.0);
			float flicker = 0.92f + 0.08f * MathHelper.sin(time * 5.0f + campfire.hashCode());
			int alpha = (int) (255 * (inside ? 0.85f : 0.6f) * fade * flicker);
			if (alpha <= 0) {
				continue;
			}
			drawRing(buffer, matrix, world, campfire, radius, camera, alpha);
		}
		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw(RenderLayer.getDebugQuads());
		}
	}

	private static void drawRing(VertexConsumer buffer, Matrix4f matrix, World world, BlockPos campfire, double radius, Vec3d camera, int alpha) {
		double cx = campfire.getX() + 0.5, cz = campfire.getZ() + 0.5;
		double inner = radius - BAND / 2, outer = radius + BAND / 2;
		double[] ys = new double[SEGMENTS + 1];
		for (int i = 0; i <= SEGMENTS; i++) {
			double angle = (i % SEGMENTS) * MathHelper.TAU / SEGMENTS;
			ys[i] = groundAt(world, cx + Math.cos(angle) * radius, cz + Math.sin(angle) * radius, campfire.getY()) + 0.03;
		}
		for (int i = 0; i < SEGMENTS; i++) {
			double a0 = i * MathHelper.TAU / SEGMENTS, a1 = (i + 1) * MathHelper.TAU / SEGMENTS;
			double cos0 = Math.cos(a0), sin0 = Math.sin(a0), cos1 = Math.cos(a1), sin1 = Math.sin(a1);
			vertex(buffer, matrix, camera, cx + cos0 * inner, ys[i], cz + sin0 * inner, alpha);
			vertex(buffer, matrix, camera, cx + cos0 * outer, ys[i], cz + sin0 * outer, alpha);
			vertex(buffer, matrix, camera, cx + cos1 * outer, ys[i + 1], cz + sin1 * outer, alpha);
			vertex(buffer, matrix, camera, cx + cos1 * inner, ys[i + 1], cz + sin1 * inner, alpha);
		}
	}

	private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3d camera, double x, double y, double z, int alpha) {
		buffer.vertex(matrix, (float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z)).color(RED, GREEN, BLUE, alpha);
	}

	/**
	 * The height of the ground you'd stand on at (x, z), searching outwards from the campfire's level as
	 * far as its warmth reaches up and down: the top of a floor block, a slab or a snow layer.
	 */
	private static double groundAt(World world, double x, double z, int baseY) {
		int bx = MathHelper.floor(x), bz = MathHelper.floor(z);
		int height = Math.max(1, CampfireWarmth.height());
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int offset = 0; offset <= height; offset++) {
			for (int y : offset == 0 ? new int[] {baseY} : new int[] {baseY + offset, baseY - offset}) {
				pos.set(bx, y, bz);
				BlockState state = world.getBlockState(pos);
				double top = top(world, pos, state);
				if (top >= 1.0) {
					continue;
				}
				if (top > 0.0) {
					return y + top;
				}
				BlockPos below = pos.down();
				if (top(world, below, world.getBlockState(below)) >= 1.0) {
					return y;
				}
			}
		}
		return baseY;
	}

	/** How high in its block you'd stand on this one (0 = nothing to stand on). Snow layers by their look, as even one layer covers the ground. */
	private static double top(World world, BlockPos pos, BlockState state) {
		VoxelShape shape = state.isOf(Blocks.SNOW) ? state.getOutlineShape(world, pos) : state.getCollisionShape(world, pos);
		return shape.isEmpty() ? 0.0 : shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
	}

	private static double horizontalDistance(Vec3d from, BlockPos to) {
		double dx = from.x - (to.getX() + 0.5), dz = from.z - (to.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}
}

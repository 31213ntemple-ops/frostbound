package com.frostbound.test.dev;

import com.frostbound.Frostbound;
import com.frostbound.client.blockbench.BlockbenchEntityModel;
import com.frostbound.client.blockbench.BlockbenchModels;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.animation.Animation;
import net.minecraft.client.render.entity.animation.AnimationHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

/**
 * Dev only, run by runScenes: loads the Blockbench test critter through the real client path (resource
 * manager → model parts and vanilla animations) and checks the parts and an animation frame against
 * values worked out from Blockbench's exporter. Throws if anything's off, which stops runScenes.
 * (GameTests check the imported data; they can't load these client classes.)
 */
public final class BlockbenchClientCheck {
	public static final Identifier CRITTER = Frostbound.id("bb_test_critter");
	private static final float DEG = (float) (Math.PI / 180.0);

	private BlockbenchClientCheck() {}

	public static void run() {
		ModelPart root = BlockbenchModels.texturedModelData(CRITTER).createModel();
		check(root.hasChild("bb_main") && root.hasChild("body") && !root.hasChild("head"), "bones: bb_main and body at the root");
		ModelPart body = root.getChild("body");
		ModelPart head = body.getChild("head");
		close(body.pivotY, 18, "body pivot y");
		close(head.pivotZ, -4, "head pivot z");
		close(head.pitch, -10 * DEG, "head pitch");
		close(body.getChild("fin_r1").roll, 22.5f * DEG, "fin roll");
		ModelPart.Cuboid cube = head.getRandomCuboid(Random.create());
		close(cube.minX, -2, "head cube x");
		close(cube.minY, -3, "head cube y");
		close(cube.maxZ, 0, "head cube far z");
		check(BlockbenchModels.texture(CRITTER).equals(Frostbound.id("textures/entity/bb_test_critter.png")), "texture id");

		Animation idle = BlockbenchModels.animations(CRITTER).get("idle");
		check(idle != null && idle.looping() && idle.lengthInSeconds() == 2.0f, "idle animation");
		Preview model = new Preview(root);
		AnimationHelper.animate(model, idle, 1000L, 1.0f, new Vector3f());
		close(head.pitch, (-10 - 20) * DEG, "head pitch at 1 s (modelled -10°, animated -20°)");
		close(head.yaw, -10 * DEG, "head yaw at 1 s");
		close(body.pivotX, -2, "body moved by the step keyframe's x");
		close(body.pivotY, 18 - 2, "and its y (Blockbench's up is the model's minus)");
		close(body.xScale, 1.5f, "body scaled by the scale keyframe");
		Frostbound.LOGGER.info("Blockbench client check passed");
	}

	/** The critter as a model, for previews and the check. */
	public static final class Preview extends BlockbenchEntityModel<Entity> {
		public Preview(ModelPart root) {
			super(root, CRITTER);
		}

		@Override
		protected void pose(Entity entity, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {}
	}

	private static void close(float actual, float expected, String what) {
		check(Math.abs(actual - expected) < 1e-4f, what + ": expected " + expected + ", got " + actual);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException("Blockbench client check failed: " + message);
		}
	}
}

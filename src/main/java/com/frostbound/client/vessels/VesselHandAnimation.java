package com.frostbound.client.vessels;

import com.frostbound.vessels.FrozenVesselItem;
import com.frostbound.vessels.RevitalizedVesselItem;
import com.frostbound.vessels.VesselItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * First person, while a vessel is held in use: it's lifted in toward the middle of the screen and
 * turned to face you. A Frozen Vessel shivers harder as it thaws; a Revitalized Vessel swells on
 * each heartbeat (in time with the sounds in {@link RevitalizedVesselItem#BEATS}).
 */
public final class VesselHandAnimation {
	/** Ticks to lift the vessel into place. */
	private static final float LIFT_TICKS = 6.0f;

	private VesselHandAnimation() {}

	public static void apply(AbstractClientPlayerEntity player, float tickDelta, Hand hand, ItemStack stack,
			MatrixStack matrices) {
		if (!(stack.getItem() instanceof VesselItem vessel) || !player.isUsingItem()
				|| player.getActiveHand() != hand || player.getItemUseTimeLeft() <= 0) {
			return;
		}
		Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		int side = arm == Arm.RIGHT ? 1 : -1;
		// Ticks since the hold started, smoothed between frames.
		float used = vessel.useTicks() - (player.getItemUseTimeLeft() - tickDelta + 1.0f);
		float progress = MathHelper.clamp(used / vessel.useTicks(), 0.0f, 1.0f);
		float lift = MathHelper.clamp(used / LIFT_TICKS, 0.0f, 1.0f);
		lift = lift * lift * (3.0f - 2.0f * lift);

		matrices.translate(side * -0.3f * lift, 0.1f * lift, -0.18f * lift);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 10.0f * lift));

		if (vessel instanceof FrozenVesselItem) {
			float amount = (0.004f + 0.012f * progress) * lift;
			matrices.translate(MathHelper.sin(used * 2.9f) * amount, MathHelper.cos(used * 3.7f) * amount, 0.0f);
		} else if (vessel instanceof RevitalizedVesselItem) {
			float pulse = 0.0f;
			for (int beat : RevitalizedVesselItem.BEATS) {
				float since = used - beat;
				if (since >= 0.0f && since < 4.0f) {
					float fade = 1.0f - since / 4.0f;
					pulse = Math.max(pulse, fade * fade);
				}
			}
			float scale = 1.0f + 0.1f * pulse * lift;
			matrices.scale(scale, scale, scale);
		}
	}
}

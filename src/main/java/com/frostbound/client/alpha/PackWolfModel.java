package com.frostbound.client.alpha;

import com.frostbound.alpha.AlphaWolfEntity;
import com.frostbound.alpha.PackWolfEntity;
import com.frostbound.alpha.WolfAction;
import com.frostbound.config.FrostboundConfig;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.render.entity.model.WolfEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * The vanilla wolf model, posed for whatever a pack wolf is doing ({@link WolfAction}) so every move
 * can be read before it lands:
 *
 *  - SLEEPING: lying down, head on its front paws, tail curled round, breathing slowly. WAKING blends
 *    back up to standing (the Alpha throws its head back to howl halfway up).
 *  - CROUCH (a lunge's tell): front end down, rear up, legs bunched, quivering.
 *  - POUNCE_WINDUP: a deeper crouch, rear swaying side to side like a cat's, tail high.
 *  - LEAPING: stretched out, front paws reaching, hind legs kicked back.
 *  - CHARGE_WINDUP: head down like a bull, a front paw scraping the ground; still once it locks on.
 *  - CHARGING: head low and forward. STUNNED: sitting, head lolling.
 *  - HOWLING / CHORUS: sitting, snout to the sky. ROARING: rears up on its hind legs, then slams down.
 *  - RECOVERING: head low, sides heaving.
 *
 * Every part is reset to its default before posing, so poses never leak into the next frame.
 */
public class PackWolfModel extends WolfEntityModel<PackWolfEntity> {
	private static final float HALF_PI = (float) (Math.PI / 2.0);

	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart realHead;
	private final ModelPart torso;
	private final ModelPart neck;
	private final ModelPart rightHindLeg;
	private final ModelPart leftHindLeg;
	private final ModelPart rightFrontLeg;
	private final ModelPart leftFrontLeg;
	private final ModelPart tail;

	private float tickDelta;

	public PackWolfModel(ModelPart root) {
		super(root);
		this.root = root;
		this.head = root.getChild(EntityModelPartNames.HEAD);
		this.realHead = this.head.getChild("real_head");
		this.torso = root.getChild(EntityModelPartNames.BODY);
		this.neck = root.getChild("upper_body");
		this.rightHindLeg = root.getChild(EntityModelPartNames.RIGHT_HIND_LEG);
		this.leftHindLeg = root.getChild(EntityModelPartNames.LEFT_HIND_LEG);
		this.rightFrontLeg = root.getChild(EntityModelPartNames.RIGHT_FRONT_LEG);
		this.leftFrontLeg = root.getChild(EntityModelPartNames.LEFT_FRONT_LEG);
		this.tail = root.getChild(EntityModelPartNames.TAIL);
	}

	@Override
	public void animateModel(PackWolfEntity wolf, float limbAngle, float limbDistance, float tickDelta) {
		this.root.traverse().forEach(ModelPart::resetTransform);
		this.tickDelta = tickDelta;
		super.animateModel(wolf, limbAngle, limbDistance, tickDelta);
	}

	@Override
	public void setAngles(PackWolfEntity wolf, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		super.setAngles(wolf, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
		WolfAction action = wolf.getAction();
		float p = wolf.actionProgress(this.tickDelta);
		float time = wolf.age + this.tickDelta;
		switch (action) {
			case SLEEPING -> this.lieDown(1.0f, time);
			case WAKING -> this.wake(wolf, p, time);
			case CROUCH -> this.crouch(ease(p / 4.0f), 0.18f, time);
			case POUNCE_WINDUP -> {
				float t = ease(p / 6.0f);
				this.crouch(t * 1.25f, 0.32f, time);
				// The rear sways, faster as it's about to go.
				float sway = MathHelper.sin(p * (0.35f + p * 0.012f)) * 0.09f * t;
				this.torso.yaw += sway;
				this.rightHindLeg.pivotX += sway * 8.0f;
				this.leftHindLeg.pivotX += sway * 8.0f;
				this.tail.pitch = 1.05f;
				this.tail.yaw = sway * 4.0f;
			}
			case LEAPING -> this.stretch();
			case RECOVERING -> this.recover(p);
			case CHARGE_WINDUP -> this.paw(p);
			case CHARGING -> {
				this.headDown(1.0f, 0.35f);
				this.torso.pitch += 0.08f;
				this.tail.pitch = 1.7f;
			}
			case STUNNED -> {
				this.realHead.roll = MathHelper.sin(p * 0.33f) * 0.35f;
				this.head.yaw += MathHelper.sin(p * 0.21f) * 0.3f;
				this.head.pitch = 0.25f + MathHelper.sin(p * 0.17f) * 0.1f;
				this.tail.pitch = 0.4f;
			}
			case HOWLING, CHORUS -> {
				float t = ease(p / 6.0f);
				this.head.pitch = MathHelper.lerp(t, this.head.pitch, -1.05f);
				this.head.pivotY -= 0.6f * t;
				this.realHead.roll = 0.0f;
			}
			case ROARING -> this.roar(p);
			default -> {
			}
		}
	}

	// --- Poses ---

	private static float ease(float t) {
		t = MathHelper.clamp(t, 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}

	/** Lying down (blended in by {@code t}): sphinx-like, head resting on the front paws, tail curled round. */
	private void lieDown(float t, float time) {
		float breathe = MathHelper.sin(time * 0.09f) * 0.25f;
		lerpPivot(this.torso, t, 0.0f, 21.0f + breathe, 2.0f);
		this.torso.pitch = MathHelper.lerp(t, this.torso.pitch, HALF_PI);
		lerpPivot(this.neck, t, -1.0f, 21.0f + breathe * 0.8f, -3.0f);
		this.neck.pitch = MathHelper.lerp(t, this.neck.pitch, HALF_PI);
		lerpPivot(this.head, t, -1.0f, 19.6f + breathe * 0.5f, -7.5f);
		this.head.pitch = MathHelper.lerp(t, this.head.pitch, 0.08f);
		this.head.yaw = MathHelper.lerp(t, this.head.yaw, 0.35f);
		this.realHead.roll = MathHelper.lerp(t, this.realHead.roll, 0.12f);
		// Front legs stretched out along the ground.
		lerpPivot(this.rightFrontLeg, t, -2.5f, 22.6f, -4.0f);
		lerpPivot(this.leftFrontLeg, t, 0.5f, 22.6f, -4.0f);
		this.rightFrontLeg.pitch = MathHelper.lerp(t, this.rightFrontLeg.pitch, -HALF_PI);
		this.leftFrontLeg.pitch = MathHelper.lerp(t, this.leftFrontLeg.pitch, -HALF_PI);
		this.rightFrontLeg.yaw = MathHelper.lerp(t, this.rightFrontLeg.yaw, 0.12f);
		// Hind legs tucked forward along its sides.
		lerpPivot(this.rightHindLeg, t, -3.3f, 22.6f, 6.0f);
		lerpPivot(this.leftHindLeg, t, 1.3f, 22.6f, 6.0f);
		this.rightHindLeg.pitch = MathHelper.lerp(t, this.rightHindLeg.pitch, -HALF_PI);
		this.leftHindLeg.pitch = MathHelper.lerp(t, this.leftHindLeg.pitch, -HALF_PI);
		this.rightHindLeg.yaw = MathHelper.lerp(t, this.rightHindLeg.yaw, 0.45f);
		this.leftHindLeg.yaw = MathHelper.lerp(t, this.leftHindLeg.yaw, -0.45f);
		// Tail laid along the ground and curled round towards the head.
		lerpPivot(this.tail, t, -1.0f, 21.5f, 7.5f);
		this.tail.pitch = MathHelper.lerp(t, this.tail.pitch, HALF_PI + 0.05f);
		this.tail.yaw = MathHelper.lerp(t, this.tail.yaw, 1.1f);
	}

	/** Getting up: head first, then the body; the Alpha howls the pack awake on the way. */
	private void wake(PackWolfEntity wolf, float p, float time) {
		boolean alpha = wolf instanceof AlphaWolfEntity;
		float total = alpha ? AlphaWolfEntity.WAKE_TICKS : PackWolfEntity.WAKE_TICKS;
		float body = ease((p - total * 0.25f) / (total * 0.45f));
		this.lieDown(1.0f - body, time);
		// The head comes up before the body does.
		float headUp = ease(p / (total * 0.3f));
		this.head.pivotY -= 1.5f * headUp * (1.0f - body);
		this.head.yaw *= 1.0f - headUp;
		this.realHead.roll *= 1.0f - headUp;
		if (alpha) {
			float howl = ease((p - total * 0.4f) / 4.0f) * (1.0f - ease((p - total * 0.8f) / 4.0f));
			this.head.pitch = MathHelper.lerp(howl, this.head.pitch, -0.95f);
		}
	}

	/** Front end down and rear up (by {@code tilt}), legs bunched, a quiver of tension. */
	private void crouch(float t, float tilt, float time) {
		float quiver = MathHelper.sin(time * 2.3f) * 0.12f * Math.min(1.0f, t);
		this.torso.pivotY += 2.5f * t;
		this.torso.pivotX += quiver;
		this.torso.pitch += tilt * t;
		this.neck.pivotY += 3.0f * t;
		this.neck.pitch += tilt * t;
		this.head.pivotY += 3.2f * t;
		this.head.pivotZ -= 0.6f * t;
		this.head.pitch += 0.15f * t;
		this.rightFrontLeg.pivotY += 2.5f * t;
		this.leftFrontLeg.pivotY += 2.5f * t;
		this.rightFrontLeg.pitch = -0.75f * Math.min(1.0f, t);
		this.leftFrontLeg.pitch = -0.75f * Math.min(1.0f, t);
		this.rightHindLeg.pivotY += 0.8f * t;
		this.leftHindLeg.pivotY += 0.8f * t;
		this.rightHindLeg.pitch = 0.4f * Math.min(1.0f, t);
		this.leftHindLeg.pitch = 0.4f * Math.min(1.0f, t);
		this.tail.pitch = 1.75f;
	}

	/** Mid-leap: reaching forward, hind legs kicked back. */
	private void stretch() {
		this.torso.pitch -= 0.12f;
		this.neck.pitch -= 0.12f;
		this.head.pitch -= 0.2f;
		this.rightFrontLeg.pitch = -1.25f;
		this.leftFrontLeg.pitch = -1.15f;
		this.rightHindLeg.pitch = 1.15f;
		this.leftHindLeg.pitch = 1.25f;
		this.tail.pitch = HALF_PI - 0.15f;
	}

	/** Winded after a big move: head hanging, sides heaving. */
	private void recover(float p) {
		float heave = MathHelper.sin(p * 0.7f) * 0.35f;
		this.torso.pivotY += 0.8f + heave;
		this.neck.pivotY += 1.2f + heave;
		this.headDown(0.7f, 0.5f);
		this.head.pivotY += heave;
		this.tail.pitch = 0.9f;
	}

	private void headDown(float t, float pitch) {
		this.head.pivotY += 2.2f * t;
		this.head.pivotZ -= 0.5f * t;
		this.head.pitch = MathHelper.lerp(t, this.head.pitch, pitch);
		this.neck.pivotY += 1.2f * t;
	}

	/** Head down like a bull, scraping a front paw back three times; still for the last moment before it goes. */
	private void paw(float p) {
		FrostboundConfig.Charge config = FrostboundConfig.get().alphaWolf.charge;
		float t = ease(p / 6.0f);
		this.crouch(t * 0.6f, 0.1f, p);
		this.headDown(t, 0.6f);
		boolean locked = p >= config.windupTicks - config.lockTicks;
		if (!locked) {
			// Scrapes at ticks 6, 16 and 26 (when the server kicks up the ground).
			float phase = (p - 2.0f) % 10.0f;
			if (p >= 2.0f && phase < 6.0f) {
				float swing = MathHelper.sin(phase / 6.0f * (float) Math.PI);
				this.rightFrontLeg.pitch = -0.4f + 1.1f * swing;
				this.rightFrontLeg.pivotY -= 1.0f * swing;
			}
		} else {
			this.torso.pivotY += 0.6f;
			this.head.pivotY += 0.5f;
		}
		this.tail.pitch = 1.65f;
	}

	/** Rears up on its hind legs, then slams down with its head thrown forward (the shockwave at tick 12). */
	private void roar(float p) {
		float up = ease(p / 10.0f) * (1.0f - ease((p - 11.0f) / 3.0f));
		float after = ease((p - 12.0f) / 3.0f) * (1.0f - ease((p - 24.0f) / 6.0f));
		this.torso.pitch -= 0.55f * up;
		this.torso.pivotY -= 1.0f * up;
		this.neck.pitch -= 0.6f * up;
		this.neck.pivotY -= 3.5f * up;
		this.neck.pivotZ += 1.0f * up;
		this.head.pivotY -= 5.0f * up;
		this.head.pivotZ += 1.8f * up;
		this.head.pitch = MathHelper.lerp(Math.max(up, after), this.head.pitch, -0.55f);
		this.rightFrontLeg.pivotY -= 5.0f * up;
		this.leftFrontLeg.pivotY -= 5.0f * up;
		this.rightFrontLeg.pivotZ += 1.5f * up;
		this.leftFrontLeg.pivotZ += 1.5f * up;
		this.rightFrontLeg.pitch = -0.9f * up;
		this.leftFrontLeg.pitch = -0.7f * up;
		this.rightHindLeg.pitch = -0.25f * up;
		this.leftHindLeg.pitch = -0.25f * up;
		this.head.pivotZ -= 1.0f * after;
		this.tail.pitch = 1.2f;
	}

	private static void lerpPivot(ModelPart part, float t, float x, float y, float z) {
		part.pivotX = MathHelper.lerp(t, part.pivotX, x);
		part.pivotY = MathHelper.lerp(t, part.pivotY, y);
		part.pivotZ = MathHelper.lerp(t, part.pivotZ, z);
	}
}

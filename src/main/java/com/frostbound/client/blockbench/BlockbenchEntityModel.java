package com.frostbound.client.blockbench;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.animation.Animation;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.entity.AnimationState;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

/**
 * An entity model made in Blockbench ({@link BlockbenchModels}). Subclass it for each entity and pose it in
 * {@link #pose}: every frame the parts are put back to how they were modelled, then {@link #pose} runs.
 *
 * <pre>
 * public class FrostSpriteModel extends BlockbenchEntityModel&lt;FrostSpriteEntity&gt; {
 *     public static final Identifier ID = Frostbound.id("frost_sprite");
 *     private final ModelPart head;
 *
 *     public FrostSpriteModel(ModelPart root) {
 *         super(root, ID);
 *         head = part("head");                                   // a bone, by its Blockbench name
 *     }
 *
 *     protected void pose(FrostSpriteEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
 *         walk("walk", limbAngle, limbDistance);                  // a looping Blockbench animation, driven by walking
 *         play(e.attackState, "attack", age);                     // a one-off, started on the entity's AnimationState
 *         head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;     // and any code-driven posing on top
 *     }
 * }
 * </pre>
 */
public abstract class BlockbenchEntityModel<T extends Entity> extends SinglePartEntityModel<T> {
	protected final ModelPart root;
	protected final Identifier model;

	protected BlockbenchEntityModel(ModelPart root, Identifier model) {
		super(RenderLayer::getEntityCutoutNoCull);
		this.root = root;
		this.model = model;
	}

	@Override
	public ModelPart getPart() {
		return root;
	}

	/** A bone by its Blockbench name, anywhere in the model. */
	public ModelPart part(String name) {
		return getChild(name).orElseThrow(() -> new IllegalArgumentException("Blockbench model " + model + " has no bone called " + name));
	}

	/** One of the model's Blockbench animations, by name. */
	public Animation animation(String name) {
		Animation animation = BlockbenchModels.animations(model).get(name);
		if (animation == null) {
			throw new IllegalArgumentException("Blockbench model " + model + " has no animation called " + name
					+ " (it has " + BlockbenchModels.animations(model).keySet() + ")");
		}
		return animation;
	}

	/** Plays {@code name} as far as {@code state} has run (start and stop the state on the entity). */
	protected void play(AnimationState state, String name, float age) {
		updateAnimation(state, animation(name), age);
	}

	/** Runs a looping animation in step with the entity's legs, like vanilla's walking animations. */
	protected void walk(String name, float limbAngle, float limbDistance) {
		animateMovement(animation(name), limbAngle, limbDistance, 2.0f, 2.5f);
	}

	@Override
	public final void setAngles(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		root.traverse().forEach(ModelPart::resetTransform);
		pose(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
	}

	/** Poses the model for this frame (after every part has been reset to its modelled pose). */
	protected abstract void pose(T entity, float limbAngle, float limbDistance, float age, float headYaw, float headPitch);
}

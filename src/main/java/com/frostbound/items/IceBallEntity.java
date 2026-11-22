package com.frostbound.items;

import com.frostbound.config.FrostboundConfig;
import com.frostbound.mobs.FrozenMob;
import net.minecraft.block.AbstractCandleBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;

import java.util.Objects;

/**
 * A thrown ice ball. What it does depends on what it hits:
 *  - a mob or player: damage (extra against {@link FrostboundItems#WEAK_TO_ICE}), Slowness, frosted
 *    screen edges, and puts out a burning target. Frozen mobs are made of ice: they take the
 *    damage but shrug off the Slowness and frost.
 *  - still water: freezes a small disc of frosted ice (melts again, like Frost Walker's).
 *  - fire, a lit campfire or candle: puts it out.
 */
public class IceBallEntity extends ThrownItemEntity {
	private static final byte SHATTER = 3;

	public IceBallEntity(EntityType<? extends IceBallEntity> type, World world) {
		super(type, world);
	}

	public IceBallEntity(World world, LivingEntity owner) {
		super(FrostboundItems.ICE_BALL_ENTITY, owner, world);
	}

	public IceBallEntity(World world, double x, double y, double z) {
		super(FrostboundItems.ICE_BALL_ENTITY, x, y, z, world);
	}

	@Override
	protected Item getDefaultItem() {
		return FrostboundItems.ICE_BALL;
	}

	@Override
	public void tick() {
		World world = getWorld();
		if (world.isClient) {
			if (random.nextInt(3) == 0) {
				world.addParticle(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(), 0, 0, 0);
			}
		} else if (FrostboundConfig.get().iceBalls.freezeWaterRadius > 0
				&& world.getFluidState(getBlockPos()).isEmpty()) {
			// Projectiles normally fly straight through water, so look for it ourselves.
			Vec3d from = getPos();
			BlockHitResult hit = world.raycast(new RaycastContext(from, from.add(getVelocity()),
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.SOURCE_ONLY, this));
			if (hit.getType() == HitResult.Type.BLOCK && world.getBlockState(hit.getBlockPos()).isOf(Blocks.WATER)) {
				setPosition(hit.getPos());
				freezeWater(hit.getBlockPos());
				shatter();
				return;
			}
		}
		super.tick();
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		super.onEntityHit(hit);
		Entity target = hit.getEntity();
		FrostboundConfig.IceBalls config = FrostboundConfig.get().iceBalls;
		float damage = config.damage;
		if (target.getType().isIn(FrostboundItems.WEAK_TO_ICE)) {
			damage *= config.weakToIceMultiplier;
		}
		if (target.isOnFire()) {
			target.extinguishWithSound();
		}
		if (!target.damage(getDamageSources().thrown(this, getOwner()), damage)) {
			return;
		}
		if (target instanceof LivingEntity living && !FrozenMob.isFrozen(living)) {
			if (config.slownessSeconds > 0) {
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
						config.slownessSeconds * 20, Math.max(0, config.slownessLevel - 1)),
						Objects.requireNonNullElse(getOwner(), this));
			}
			if (living.canFreeze()) {
				// Frost the screen edges, staying just under where powder-snow freeze damage starts.
				int ticks = Math.min(living.getFrozenTicks() + 100, living.getMinFreezeDamageTicks() - 1);
				living.setFrozenTicks(Math.max(living.getFrozenTicks(), ticks));
			}
		}
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		super.onBlockHit(hit);
		if (!getWorld().isClient) {
			extinguish(hit.getBlockPos());
			extinguish(hit.getBlockPos().offset(hit.getSide()));
		}
	}

	@Override
	protected void onCollision(HitResult hit) {
		super.onCollision(hit);
		if (!getWorld().isClient) {
			shatter();
		}
	}

	@Override
	public void handleStatus(byte status) {
		if (status == SHATTER) {
			ItemStackParticleEffect chunks = new ItemStackParticleEffect(ParticleTypes.ITEM, getStack());
			for (int i = 0; i < 8; i++) {
				getWorld().addParticle(chunks, getX(), getY(), getZ(), 0, 0, 0);
				getWorld().addParticle(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(),
						(random.nextDouble() - 0.5) * 0.1, random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.1);
			}
		} else {
			super.handleStatus(status);
		}
	}

	private void shatter() {
		getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.NEUTRAL,
				0.4f, 1.6f + random.nextFloat() * 0.3f);
		getWorld().sendEntityStatus(this, SHATTER);
		discard();
	}

	/** A disc of frosted ice on the water surface around where it landed. */
	private void freezeWater(BlockPos center) {
		World world = getWorld();
		int radius = FrostboundConfig.get().iceBalls.freezeWaterRadius;
		BlockState ice = Blocks.FROSTED_ICE.getDefaultState();
		double maxDistSq = (radius + 0.5) * (radius + 0.5);
		for (BlockPos pos : BlockPos.iterate(center.add(-radius, 0, -radius), center.add(radius, 0, radius))) {
			if (pos.getSquaredDistance(center) > maxDistSq) {
				continue;
			}
			BlockState state = world.getBlockState(pos);
			if (state.isOf(Blocks.WATER) && state.getFluidState().isStill()
					&& world.getBlockState(pos.up()).isAir()
					&& ice.canPlaceAt(world, pos)
					&& world.canPlace(ice, pos, ShapeContext.absent())) {
				world.setBlockState(pos, ice);
				world.scheduleBlockTick(pos, Blocks.FROSTED_ICE, MathHelper.nextInt(random, 60, 120));
			}
		}
	}

	/** Same as a water splash potion: fire goes out, lit candles and campfires go dark. */
	private void extinguish(BlockPos pos) {
		World world = getWorld();
		BlockState state = world.getBlockState(pos);
		if (state.isIn(BlockTags.FIRE)) {
			world.breakBlock(pos, false, this);
		} else if (AbstractCandleBlock.isLitCandle(state)) {
			AbstractCandleBlock.extinguish(null, state, world, pos);
		} else if (CampfireBlock.isLitCampfire(state)) {
			world.syncWorldEvent(null, WorldEvents.FIRE_EXTINGUISHED, pos, 0);
			CampfireBlock.extinguish(getOwner(), world, pos, state);
			world.setBlockState(pos, state.with(CampfireBlock.LIT, false));
		}
	}
}

package com.frostbound.alpha;

import com.frostbound.config.FrostboundConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A wolf called by the Mark of the Pack: tame to the player who called it, it fights alongside them
 * like any tame wolf and vanishes in a puff of snow after {@code lifetimeSeconds} (or when its owner
 * leaves). It can't be fed, sat, bred, leashed or armoured (wolf armour would vanish with it), and drops nothing.
 */
public class SummonedWolfEntity extends WolfEntity {
	/** An owner who logs off takes their wolves with them, after this long. */
	private static final int OWNER_GRACE_TICKS = 60;
	private int ticksLeft;
	private int ownerMissingTicks;

	public SummonedWolfEntity(EntityType<? extends SummonedWolfEntity> type, World world) {
		super(type, world);
		this.ticksLeft = AlphaWolfEntity.ticks(FrostboundConfig.get().markOfThePack.lifetimeSeconds);
	}

	public static DefaultAttributeContainer.Builder createSummonedWolfAttributes() {
		return WolfEntity.createWolfAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0);
	}

	public int ticksLeft() {
		return this.ticksLeft;
	}

	public void setTicksLeft(int ticks) {
		this.ticksLeft = ticks;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		this.ownerMissingTicks = this.getOwner() == null ? this.ownerMissingTicks + 1 : 0;
		if (--this.ticksLeft <= 0 || this.ownerMissingTicks > OWNER_GRACE_TICKS) {
			world.spawnParticles(ParticleTypes.POOF, this.getX(), this.getBodyY(0.5), this.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
			world.spawnParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getBodyY(0.5), this.getZ(), 10, 0.4, 0.3, 0.4, 0.02);
			this.discard();
		}
	}

	/** Keeps its 20 health and normal bite instead of a tame wolf's 40. */
	@Override
	protected void updateAttributesForTamed() {
	}

	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		return ActionResult.PASS;
	}

	@Override
	public boolean isBreedingItem(ItemStack stack) {
		return false;
	}

	@Override
	public boolean canBreedWith(AnimalEntity other) {
		return false;
	}

	@Nullable
	@Override
	public WolfEntity createChild(ServerWorld world, PassiveEntity entity) {
		return null;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected int getXpToDrop() {
		return 0;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("FrostboundTicksLeft", this.ticksLeft);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("FrostboundTicksLeft")) {
			this.ticksLeft = nbt.getInt("FrostboundTicksLeft");
		}
	}
}

package com.frostbound.christmas;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import com.frostbound.mobs.FrozenMob;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.LootableInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The presents under Christmas trees: one right-click and something happens (see {@link Outcome}).
 *
 * They're Supplementaries presents placed by the tree structure with the loot table
 * {@link #PRESENT_LOOT}, which is how they're told apart from presents players wrap
 * themselves (those open normally). The loot table also says what a "gift" is.
 * Chances and settings are in config/frostbound.json under "christmas".
 */
public final class ChristmasPresents {
	/** Marks a Christmas-tree present, and is what the GIFT outcome rolls. */
	public static final RegistryKey<LootTable> PRESENT_LOOT =
			RegistryKey.of(RegistryKeys.LOOT_TABLE, Frostbound.id("chests/christmas_present"));

	public enum Outcome { FROZEN_MOBS, GIFT, COAL, EXPLODE }

	private record Fuse(ServerWorld world, BlockPos pos, int explodeAt) {}

	private static final List<Fuse> FUSES = new ArrayList<>();

	private ChristmasPresents() {}

	static void init() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClient() || player.isSpectator()) {
				return ActionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (isFusing(world, pos)) {
				return ActionResult.SUCCESS; // already ticking: no second surprise
			}
			if (!isChristmasPresent(world, pos)) {
				return ActionResult.PASS;
			}
			open((ServerWorld) world, pos, player, roll(world.getRandom()));
			return ActionResult.SUCCESS;
		});
		ServerTickEvents.END_WORLD_TICK.register(ChristmasPresents::tickFuses);
	}

	/** A Supplementaries present that came with a Christmas tree and hasn't been opened. */
	public static boolean isChristmasPresent(World world, BlockPos pos) {
		Identifier id = Registries.BLOCK.getId(world.getBlockState(pos).getBlock());
		return id.getNamespace().equals(Christmas.SUPPLEMENTARIES)
				&& id.getPath().startsWith("present")
				&& world.getBlockEntity(pos) instanceof LootableInventory present
				&& PRESENT_LOOT.equals(present.getLootTable());
	}

	public static Outcome roll(Random random) {
		FrostboundConfig.Presents c = FrostboundConfig.get().christmas.presents;
		int[] weights = {c.frozenMobsWeight, c.giftWeight, c.coalWeight, c.explodeWeight};
		int total = 0;
		for (int w : weights) total += Math.max(0, w);
		if (total == 0) return Outcome.GIFT;
		int pick = random.nextInt(total);
		for (int i = 0; i < weights.length; i++) {
			pick -= Math.max(0, weights[i]);
			if (pick < 0) return Outcome.values()[i];
		}
		return Outcome.GIFT;
	}

	/** Opens the present at {@code pos} with the given outcome. Public so tests can force one. */
	public static void open(ServerWorld world, BlockPos pos, @Nullable PlayerEntity player, Outcome outcome) {
		FrostboundConfig.Presents c = FrostboundConfig.get().christmas.presents;
		// Stop it being a Christmas present (and stop Supplementaries filling it from the loot table).
		if (world.getBlockEntity(pos) instanceof LootableInventory present) {
			present.setLootTable(null);
		}
		Vec3d center = Vec3d.ofCenter(pos);

		switch (outcome) {
			case GIFT -> {
				unwrap(world, pos);
				LootContextParameterSet.Builder params = new LootContextParameterSet.Builder(world)
						.add(LootContextParameters.ORIGIN, center);
				if (player != null) params.luck(player.getLuck()).add(LootContextParameters.THIS_ENTITY, player);
				LootTable table = world.getServer().getReloadableRegistries().getLootTable(PRESENT_LOOT);
				table.generateLoot(params.build(LootContextTypes.CHEST)).forEach(stack -> drop(world, center, stack));
				world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y, center.z, 12, 0.4, 0.4, 0.4, 0);
				world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.7f, 1.4f);
				tell(player, "frostbound.present.gift");
			}
			case COAL -> {
				unwrap(world, pos);
				drop(world, center, new ItemStack(Items.COAL));
				world.spawnParticles(ParticleTypes.SMOKE, center.x, center.y, center.z, 10, 0.3, 0.3, 0.3, 0.01);
				world.playSound(null, pos, SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.BLOCKS, 1f, 1f);
				tell(player, "frostbound.present.coal");
			}
			case FROZEN_MOBS -> {
				unwrap(world, pos);
				int count = c.minMobs + world.getRandom().nextInt(Math.max(1, c.maxMobs - c.minMobs + 1));
				for (int i = 0; i < count; i++) {
					spawnFrozenMob(world, pos, i);
				}
				world.spawnParticles(ParticleTypes.SNOWFLAKE, center.x, center.y, center.z, 40, 0.8, 0.6, 0.8, 0.05);
				world.playSound(null, pos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1f, 0.6f);
				world.playSound(null, pos, SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 0.8f, 1.2f);
				tell(player, "frostbound.present.mobs");
			}
			case EXPLODE -> {
				FUSES.add(new Fuse(world, pos.toImmutable(), world.getServer().getTicks() + Math.max(1, c.fuseTicks)));
				world.playSound(null, pos, SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1f, 1f);
				tell(player, "frostbound.present.explode");
			}
		}
	}

	public static boolean isFusing(World world, BlockPos pos) {
		for (Fuse fuse : FUSES) {
			if (fuse.world == world && fuse.pos.equals(pos)) return true;
		}
		return false;
	}

	private static void tickFuses(ServerWorld world) {
		if (FUSES.isEmpty()) return;
		int now = world.getServer().getTicks();
		for (Iterator<Fuse> it = FUSES.iterator(); it.hasNext(); ) {
			Fuse fuse = it.next();
			if (fuse.world != world) continue;
			Vec3d c = Vec3d.ofCenter(fuse.pos);
			if (now >= fuse.explodeAt) {
				it.remove();
				world.removeBlock(fuse.pos, false);
				world.createExplosion(null, c.x, c.y, c.z, FrostboundConfig.get().christmas.presents.explosionPower,
						World.ExplosionSourceType.TNT);
			} else {
				world.spawnParticles(ParticleTypes.SMOKE, c.x, c.y + 0.5, c.z, 1, 0.1, 0.05, 0.1, 0.01);
			}
		}
	}

	private static void spawnFrozenMob(ServerWorld world, BlockPos pos, int index) {
		List<String> types = FrostboundConfig.get().christmas.presents.frozenMobs;
		if (types == null || types.isEmpty()) return;
		Identifier id = Identifier.tryParse(types.get(world.getRandom().nextInt(types.size())));
		EntityType<?> type = id == null ? null : Registries.ENTITY_TYPE.getOrEmpty(id).orElse(null);
		if (type == null || !(type.create(world) instanceof MobEntity mob)) {
			Frostbound.LOGGER.warn("Christmas presents: '{}' in config isn't a mob, skipping", id);
			return;
		}
		// First one where the present was, the rest beside it.
		int[][] offsets = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		int[] o = offsets[index % offsets.length];
		mob.refreshPositionAndAngles(pos.getX() + 0.5 + o[0], pos.getY(), pos.getZ() + 0.5 + o[1],
				world.getRandom().nextFloat() * 360f, 0f);
		if (!world.isSpaceEmpty(mob)) {
			mob.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, mob.getYaw(), 0f);
		}
		mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.EVENT, null);
		((FrozenMob) mob).frostbound$setFrozen(true);
		world.spawnEntityAndPassengers(mob);
	}

	private static void unwrap(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (!state.isAir()) {
			world.breakBlock(pos, false);
		}
	}

	private static void drop(ServerWorld world, Vec3d at, ItemStack stack) {
		ItemEntity item = new ItemEntity(world, at.x, at.y, at.z, stack);
		item.setToDefaultPickupDelay();
		item.setVelocity(world.getRandom().nextTriangular(0, 0.1), 0.25, world.getRandom().nextTriangular(0, 0.1));
		world.spawnEntity(item);
	}

	private static void tell(@Nullable PlayerEntity player, String key) {
		if (player != null) {
			player.sendMessage(Text.translatable(key), true);
		}
	}
}

package com.frostbound.alpha;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import com.frostbound.passives.Passive;
import com.frostbound.passives.Passives;
import com.mojang.serialization.Codec;
import dev.emi.trinkets.api.TrinketItem;
import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.item.Item;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Mark of the Pack: a fang on a cord, worn in Trinkets' necklace slot (10% from an Alpha). Press its key
 * (J by default, rebindable under Controls → Frostbound) to call {@code wolves} wolves that fight for you
 * for {@code lifetimeSeconds}; then it recharges for {@code cooldownSeconds}.
 *
 * The client only asks ({@link ActivatePayload}); the server checks the necklace is worn and recharged.
 * The recharge is saved on the player (so relogging doesn't reset it) and shows as a cooldown sweep on the item.
 */
public final class MarkOfThePack {
	/** Items that work as a Mark of the Pack (a data pack can add more). */
	public static final TagKey<Item> ITEMS = TagKey.of(RegistryKeys.ITEM, Frostbound.id("mark_of_the_pack"));

	public static final Item MARK_OF_THE_PACK = net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM,
			Frostbound.id("mark_of_the_pack"), new TrinketItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

	/** World time (ticks) at which the Mark can next be used. */
	public static final AttachmentType<Long> READY_AT = AttachmentRegistry.<Long>builder()
			.persistent(Codec.LONG)
			.copyOnDeath()
			.initializer(() -> 0L)
			.buildAndRegister(Frostbound.id("mark_of_the_pack_ready_at"));

	/** The key's name for the tooltip. The client sets this to the bound key; on a server it's never shown. */
	public static Supplier<Text> keyName = () -> Text.literal("J");

	public static final Passive PASSIVE = Passives.register(new Passive("pack_call", Formatting.GOLD, ITEMS, () -> {
		FrostboundConfig.MarkOfThePackOptions c = FrostboundConfig.get().markOfThePack;
		return new Object[] {keyName.get(), c.wolves, Passive.number(c.lifetimeSeconds), Passive.number(c.cooldownSeconds)};
	}));

	/** Client → server: the Mark of the Pack key was pressed. */
	public record ActivatePayload() implements CustomPayload {
		public static final CustomPayload.Id<ActivatePayload> ID = new CustomPayload.Id<>(Frostbound.id("mark_of_the_pack"));
		public static final PacketCodec<RegistryByteBuf, ActivatePayload> CODEC = PacketCodec.unit(new ActivatePayload());

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	private MarkOfThePack() {}

	static void init() {
		PayloadTypeRegistry.playC2S().register(ActivatePayload.ID, ActivatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ActivatePayload.ID, (payload, context) -> activate(context.player()));
	}

	/** Whether {@code player} wears a Mark of the Pack in a trinket slot. */
	public static boolean isWorn(PlayerEntity player) {
		return TrinketsApi.getTrinketComponent(player).map(trinkets -> trinkets.isEquipped(stack -> stack.isIn(ITEMS))).orElse(false);
	}

	/** Ticks until the Mark can be used again (0 = ready). */
	public static long cooldownLeft(PlayerEntity player) {
		long left = player.getAttachedOrElse(READY_AT, 0L) - player.getWorld().getTime();
		long max = AlphaWolfEntity.ticks(FrostboundConfig.get().markOfThePack.cooldownSeconds);
		return left > max ? 0 : Math.max(0, left);
	}

	/** The key was pressed: summons the wolves if the Mark is worn and recharged. Returns the wolves summoned. */
	public static List<SummonedWolfEntity> activate(PlayerEntity player) {
		if (!(player.getWorld() instanceof ServerWorld world) || !player.isAlive() || player.isSpectator() || !isWorn(player)) {
			return List.of();
		}
		long left = cooldownLeft(player);
		if (left > 0) {
			player.sendMessage(Text.translatable("frostbound.mark_of_the_pack.resting", (left + 19) / 20).formatted(Formatting.GRAY), true);
			return List.of();
		}
		FrostboundConfig.MarkOfThePackOptions config = FrostboundConfig.get().markOfThePack;
		List<SummonedWolfEntity> wolves = new ArrayList<>();
		LivingEntity foe = player.getAttacker() != null ? player.getAttacker() : player.getAttacking();
		for (int i = 0; i < config.wolves; i++) {
			SummonedWolfEntity wolf = AlphaWolves.SUMMONED_WOLF.create(world);
			if (wolf == null) {
				continue;
			}
			// Either side of the player, a little behind.
			double side = (i % 2 == 0 ? 1 : -1) * (1.2 + i / 2);
			float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
			Vec3d pos = player.getPos().add(Math.cos(yaw) * side + Math.sin(yaw) * 0.8, 0.0, Math.sin(yaw) * side - Math.cos(yaw) * 0.8);
			wolf.refreshPositionAndAngles(pos.x, pos.y, pos.z, player.getYaw(), 0.0f);
			if (!world.isSpaceEmpty(wolf)) {
				wolf.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0.0f);
			}
			wolf.setVariant(PackWolfEntity.variant(world, net.minecraft.entity.passive.WolfVariants.SNOWY));
			wolf.setOwner(player);
			wolf.setTamed(true, true);
			if (foe != null && foe.isAlive() && foe != player) {
				wolf.setTarget(foe);
			}
			world.spawnEntity(wolf);
			world.spawnParticles(ParticleTypes.SNOWFLAKE, wolf.getX(), wolf.getBodyY(0.5), wolf.getZ(), 15, 0.4, 0.4, 0.4, 0.05);
			world.spawnParticles(ParticleTypes.POOF, wolf.getX(), wolf.getBodyY(0.3), wolf.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
			wolves.add(wolf);
		}
		int cooldown = AlphaWolfEntity.ticks(config.cooldownSeconds);
		player.setAttached(READY_AT, world.getTime() + cooldown);
		player.getItemCooldownManager().set(MARK_OF_THE_PACK, cooldown);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.PLAYERS, 1.5f, 1.1f);
		return wolves;
	}
}

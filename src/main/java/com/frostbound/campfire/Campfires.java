package com.frostbound.campfire;

import com.frostbound.Frostbound;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Early-game fire: the first way to get warm.
 *
 *  - Fire Starter (two sticks): 5 durability, lights campfires.
 *  - Campfire Base (four logs in a ring): an unlit campfire.
 *
 * Hold right-click with the fire starter on a campfire base (or any unlit campfire) to start the
 * fire-starting minigame: a marker sweeps along a bar, and you left-click while it's in the green.
 * Three hits in a row light the fire (1 durability). A miss costs 1 durability and resets the streak.
 *
 * The client runs the bar and judges each click (see client.campfire.FireStarterMinigame), then
 * tells the server with {@link FireStarterAttemptPayload}; the server keeps the streak, takes the
 * durability and lights the fire ({@link #attempt}).
 *
 * Always registered, like the other items.
 */
public final class Campfires {
	/** Hits in a row needed to light the fire. */
	public static final int STREAK_TO_LIGHT = 3;
	public static final int FIRE_STARTER_DURABILITY = 5;
	/** Least ticks between two attempts the server accepts from a client. */
	private static final int MIN_TICKS_BETWEEN_ATTEMPTS = 3;
	/** Farthest (squared, from the block's center) a player can be from the fire they're lighting. */
	private static final double MAX_DISTANCE_SQ = 8.0 * 8.0;

	public static final Block CAMPFIRE_BASE = Registry.register(Registries.BLOCK, Frostbound.id("campfire_base"),
			new CampfireBaseBlock(AbstractBlock.Settings.copy(Blocks.CAMPFIRE)
					.luminance(state -> 0)
					.sounds(BlockSoundGroup.WOOD)
					.pistonBehavior(PistonBehavior.DESTROY)
					.nonOpaque()));

	public static final Item CAMPFIRE_BASE_ITEM = Registry.register(Registries.ITEM, Frostbound.id("campfire_base"),
			new BlockItem(CAMPFIRE_BASE, new Item.Settings()));

	public static final Item FIRE_STARTER = Registry.register(Registries.ITEM, Frostbound.id("fire_starter"),
			new FireStarterItem(new Item.Settings().maxDamage(FIRE_STARTER_DURABILITY)));

	/** One minigame in progress, per player (client and server players are separate objects). */
	static final class Session {
		final BlockPos pos;
		int streak;
		/** World time of the last attempt accepted from the client; -1 before the first. */
		long lastAttemptTick = -1;

		Session(BlockPos pos) {
			this.pos = pos.toImmutable();
		}
	}

	private static final Map<PlayerEntity, Session> SESSIONS = Collections.synchronizedMap(new WeakHashMap<>());

	private Campfires() {}

	/** A campfire base, or an unlit, dry campfire. */
	public static boolean canLight(BlockState state) {
		return state.isOf(CAMPFIRE_BASE) || CampfireBlock.canBeLit(state);
	}

	static void startSession(PlayerEntity player, BlockPos pos) {
		SESSIONS.put(player, new Session(pos));
	}

	static void endSession(PlayerEntity player) {
		SESSIONS.remove(player);
	}

	/** Where the player is lighting a fire, or null. */
	public static BlockPos target(PlayerEntity player) {
		Session session = SESSIONS.get(player);
		return session == null ? null : session.pos;
	}

	/** Hits in a row so far in the player's current minigame (server side). */
	public static int streak(PlayerEntity player) {
		Session session = SESSIONS.get(player);
		return session == null ? 0 : session.streak;
	}

	/**
	 * One click in the minigame, already judged by the client. A miss costs a point of durability and
	 * resets the streak; the third hit in a row lights the fire and costs a point. Returns whether the
	 * attempt counted (the player has to be using a fire starter on something they can still light).
	 */
	public static boolean attempt(PlayerEntity player, boolean hit) {
		World world = player.getWorld();
		Session session = SESSIONS.get(player);
		if (world.isClient || session == null || !player.isUsingItem()
				|| !(player.getActiveItem().getItem() instanceof FireStarterItem)) {
			return false;
		}
		BlockState state = world.getBlockState(session.pos);
		if (!canLight(state) || player.squaredDistanceTo(session.pos.toCenterPos()) > MAX_DISTANCE_SQ) {
			player.stopUsingItem();
			return false;
		}
		ItemStack stack = player.getStackInHand(player.getActiveHand());

		if (!hit) {
			session.streak = 0;
			world.playSound(player, session.pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.25f, 1.8f);
			stack.damage(1, player, LivingEntity.getSlotForHand(player.getActiveHand()));
			return true;
		}

		session.streak++;
		if (session.streak < STREAK_TO_LIGHT) {
			world.playSound(player, session.pos, SoundEvents.BLOCK_WOOD_HIT, SoundCategory.PLAYERS, 0.8f, 1.2f + 0.2f * session.streak);
			return true;
		}

		light(world, session.pos, state, player);
		player.incrementStat(Stats.USED.getOrCreateStat(stack.getItem()));
		stack.damage(1, player, LivingEntity.getSlotForHand(player.getActiveHand()));
		player.stopUsingItem();
		endSession(player);
		return true;
	}

	/**
	 * An attempt sent by a client ({@link FireStarterAttemptPayload}): {@link #attempt}, but ignoring
	 * clicks that come faster than the bar allows. Returns whether it counted.
	 */
	public static boolean receive(PlayerEntity player, boolean hit) {
		Session session = SESSIONS.get(player);
		if (session == null) {
			return false;
		}
		long now = player.getWorld().getTime();
		if (session.lastAttemptTick >= 0 && now - session.lastAttemptTick < MIN_TICKS_BETWEEN_ATTEMPTS) {
			return false;
		}
		session.lastAttemptTick = now;
		return attempt(player, hit);
	}

	/** Turns a campfire base into a lit campfire (facing the same way), or lights an unlit campfire. */
	public static void light(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		BlockState lit = state.isOf(CAMPFIRE_BASE)
				? Blocks.CAMPFIRE.getDefaultState()
						.with(CampfireBlock.FACING, state.get(CampfireBaseBlock.FACING))
						.with(CampfireBlock.LIT, true)
				: state.with(CampfireBlock.LIT, true);
		world.setBlockState(pos, lit, Block.NOTIFY_ALL_AND_REDRAW);
		world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1.0f, 0.9f);
		world.emitGameEvent(player, GameEvent.BLOCK_CHANGE, pos);
	}

	public static void init() {
		PayloadTypeRegistry.playC2S().register(FireStarterAttemptPayload.ID, FireStarterAttemptPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FireStarterAttemptPayload.ID,
				(payload, context) -> receive(context.player(), payload.hit()));

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries ->
				entries.addBefore(Items.FLINT_AND_STEEL, FIRE_STARTER));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries ->
				entries.addBefore(Items.CAMPFIRE, CAMPFIRE_BASE_ITEM));
	}
}

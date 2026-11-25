package com.frostbound.mixin.nothirst;

import com.frostbound.nothirst.NoThirst;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The wandering trader's rare trade: a thirst item comes out as no rare trade at all, so pick again
 * (up to a few times) rather than leave the slot empty.
 */
@Mixin(WanderingTraderEntity.class)
public abstract class WanderingTraderEntityMixin {
	@WrapOperation(method = "fillRecipes", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/village/TradeOffers$Factory;create(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/random/Random;)Lnet/minecraft/village/TradeOffer;"))
	private TradeOffer frostbound$noThirstTrades(TradeOffers.Factory factory, Entity entity, Random random, Operation<TradeOffer> original) {
		TradeOffer offer = NoThirst.filter(original.call(factory, entity, random));
		TradeOffers.Factory[] rare = TradeOffers.WANDERING_TRADER_TRADES.get(2);
		for (int tries = 0; offer == null && NoThirst.isActive() && rare != null && tries < 10; tries++) {
			offer = NoThirst.filter(original.call(rare[random.nextInt(rare.length)], entity, random));
		}
		return offer;
	}
}

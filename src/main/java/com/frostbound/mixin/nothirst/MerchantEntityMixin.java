package com.frostbound.mixin.nothirst;

import com.frostbound.nothirst.NoThirst;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A trade that buys or sells one of Tough As Nails' thirst items (the wandering trader's juices)
 * comes out as "no trade", so vanilla just picks another one from the pool. The wandering trader's
 * one rare trade is picked outside the pool: see WanderingTraderEntityMixin.
 */
@Mixin(MerchantEntity.class)
public abstract class MerchantEntityMixin {
	@WrapOperation(method = "fillRecipesFromPool", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/village/TradeOffers$Factory;create(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/random/Random;)Lnet/minecraft/village/TradeOffer;"))
	private TradeOffer frostbound$noThirstTrades(TradeOffers.Factory factory, Entity entity, Random random, Operation<TradeOffer> original) {
		return NoThirst.filter(original.call(factory, entity, random));
	}
}

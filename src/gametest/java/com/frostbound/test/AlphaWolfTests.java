package com.frostbound.test;

import com.frostbound.alpha.AlphaHide;
import com.frostbound.alpha.AlphaWolfEntity;
import com.frostbound.alpha.AlphaWolves;
import com.frostbound.alpha.DrainingBite;
import com.frostbound.alpha.LifeSteal;
import com.frostbound.alpha.MarkOfThePack;
import com.frostbound.alpha.PackWolfEntity;
import com.frostbound.alpha.SummonedWolfEntity;
import com.frostbound.config.FrostboundConfig;
import com.frostbound.copper.Conductivity;
import com.frostbound.passives.Passive;
import com.frostbound.passives.Passives;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.passive.WolfVariants;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** The Alpha Wolf and its pack, Draining Bite, Enrage, the drops, Alpha Hide armour, Life Steal and the Mark of the Pack. */
public class AlphaWolfTests implements FabricGameTest {
	private static final FrostboundConfig.AlphaWolfOptions CONFIG = FrostboundConfig.get().alphaWolf;

	// --- The boss and its pack ---

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void alphaIsABigUntameableBoss(TestContext ctx) {
		AlphaWolfEntity alpha = ctx.spawnEntity(AlphaWolves.ALPHA_WOLF, 3, 2, 3);
		check(alpha.getMaxHealth() == (float) CONFIG.health, "the Alpha should have " + CONFIG.health + " health, has " + alpha.getMaxHealth());
		check(CONFIG.health == 90.0, "the Alpha should default to 45 hearts");
		check(Math.abs(alpha.getScale() - CONFIG.scale) < 0.001, "the Alpha should be scaled " + CONFIG.scale + ", is " + alpha.getScale());
		check(alpha.getWidth() > 0.6f * 1.4f, "the Alpha's hitbox should be bigger than a wolf's, width " + alpha.getWidth());

		PackWolfEntity wolf = ctx.spawnEntity(AlphaWolves.PACK_WOLF, 1, 2, 1);
		check(wolf.getMaxHealth() == (float) CONFIG.packWolfHealth, "a pack wolf should have " + CONFIG.packWolfHealth + " health");
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BONE, 64));
		for (WolfEntity target : List.of(alpha, wolf)) {
			for (int i = 0; i < 30; i++) {
				target.interactMob(player, Hand.MAIN_HAND);
			}
			check(!target.isTamed(), "pack wolves can't be tamed with bones");
			check(!target.isBreedingItem(new ItemStack(Items.BEEF)), "pack wolves can't be bred");
			check(!target.canBeLeashed(), "pack wolves can't be leashed");
		}
		check(player.getMainHandStack().getCount() == 64, "no bones should have been used up");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void newAlphaBringsAPackOfSeven(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		AlphaWolfEntity alpha = ctx.spawnEntity(AlphaWolves.ALPHA_WOLF, 4, 2, 4);
		alpha.initialize(world, world.getLocalDifficulty(alpha.getBlockPos()), SpawnReason.COMMAND, null);
		check(alpha.getVariant().matchesKey(WolfVariants.BLACK), "the Alpha should be a black wolf");
		check(alpha.packSize() == CONFIG.initialPackSize && CONFIG.initialPackSize == 7,
				"a new pack should be 7 wolves counting the Alpha, is " + alpha.packSize());
		for (PackWolfEntity wolf : alpha.getPack()) {
			check(wolf.getAlpha() == alpha, "every wolf should follow this Alpha");
			if (wolf != alpha) {
				check(!wolf.getVariant().matchesKey(WolfVariants.BLACK), "only the Alpha is black");
				check(wolf.getType() == AlphaWolves.PACK_WOLF, "the pack should be pack wolves");
			}
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void callOfThePackHealsAndSummonsWithinLimits(TestContext ctx) {
		AlphaWolfEntity alpha = ctx.spawnEntity(AlphaWolves.ALPHA_WOLF, 4, 2, 4);
		List<PackWolfEntity> members = alpha.spawnPack(2);
		check(alpha.packSize() == 3, "Alpha plus 2 wolves should be a pack of 3, is " + alpha.packSize());
		alpha.setHealth(50.0f);
		for (PackWolfEntity wolf : members) {
			wolf.setHealth(5.0f);
		}
		// 3 alive: each heals 0.25 hearts × 3 = 1.5 health; 2 wolves join.
		int joined = alpha.answerCall();
		float heal = (float) (CONFIG.callOfThePack.healPerWolf * 3);
		check(heal == 1.5f, "3 wolves alive should heal 1.5 health (0.75 hearts) each by default");
		check(Math.abs(alpha.getHealth() - (50.0f + heal)) < 0.01f, "the Alpha should heal " + heal + ", has " + alpha.getHealth());
		for (PackWolfEntity wolf : members) {
			check(Math.abs(wolf.getHealth() - (5.0f + heal)) < 0.01f, "each wolf should heal " + heal + ", one has " + wolf.getHealth());
		}
		check(joined == CONFIG.callOfThePack.wolvesPerCall && joined == 2, "the howl should bring 2 wolves, brought " + joined);
		check(alpha.packSize() == 5, "the pack should now be 5, is " + alpha.packSize());
		check(alpha.reinforcementsLeft() == CONFIG.callOfThePack.maxReinforcements - 2, "2 reinforcements should be used up");

		// At the size limit nobody joins (healing still happens).
		alpha.spawnPack(CONFIG.maxPackSize - alpha.packSize());
		check(alpha.packSize() == CONFIG.maxPackSize, "pack should be full");
		check(alpha.answerCall() == 0 && alpha.packSize() == CONFIG.maxPackSize, "a full pack can't grow past " + CONFIG.maxPackSize);

		// One short of full: only one joins. Out of reinforcements: none.
		alpha.getPack().stream().filter(w -> w != alpha).findFirst().orElseThrow().discard();
		check(alpha.answerCall() == 1, "one free place should take one wolf");
		alpha.getPack().stream().filter(w -> w != alpha).limit(3).forEach(PackWolfEntity::discard);
		alpha.setReinforcementsLeft(0);
		check(alpha.answerCall() == 0, "with no reinforcements left nobody should come");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
	public void hittingTheAlphaCutsOffItsHowl(TestContext ctx) {
		AlphaWolfEntity alpha = ctx.spawnEntity(AlphaWolves.ALPHA_WOLF, 4, 2, 4);
		alpha.spawnPack(2);
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		player.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(1, 2, 1))));
		player.setInvulnerable(true);
		alpha.setTarget(player);
		alpha.setCallCooldown(0);
		ctx.runAtTick(10, () -> {
			check(alpha.isCalling(), "with its call ready and a target, the Alpha should be howling");
			check(alpha.isInSittingPose(), "the Alpha sits back to howl");
			int pack = alpha.packSize();
			alpha.damage(ctx.getWorld().getDamageSources().playerAttack(player), (float) CONFIG.callOfThePack.interruptDamage + 1.0f);
			ctx.runAtTick(14, () -> {
				check(!alpha.isCalling() && !alpha.isInSittingPose(), "a big enough hit should cut the howl off");
				check(alpha.packSize() == pack, "an interrupted howl brings nobody");
				int retry = (int) Math.round(CONFIG.callOfThePack.interruptedRetrySeconds * 20);
				check(alpha.callCooldown() > retry - 10 && alpha.callCooldown() <= retry,
						"after an interrupted howl the Alpha should wait ~" + retry + " ticks, waits " + alpha.callCooldown());
				ctx.complete();
			});
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void onlyAFewWolvesLungeAtOnce(TestContext ctx) {
		AlphaWolfEntity alpha = ctx.spawnEntity(AlphaWolves.ALPHA_WOLF, 1, 2, 1);
		List<PackWolfEntity> members = alpha.spawnPack(6);
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		player.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(4, 2, 4))));
		player.setInvulnerable(true);
		alpha.setTarget(player);
		alpha.setCallCooldown(10000);
		int[] most = {0};
		ctx.runAtEveryTick(() -> {
			int lunging = (int) members.stream().filter(PackWolfEntity::isLunging).count();
			check(lunging <= CONFIG.simultaneousAttackers, lunging + " pack wolves lunging at once, only "
					+ CONFIG.simultaneousAttackers + " may");
			most[0] = Math.max(most[0], lunging);
		});
		ctx.runAtTick(150, () -> {
			check(members.stream().allMatch(w -> w.getTarget() == player), "every pack wolf should take up the Alpha's fight");
			check(most[0] >= 1, "some wolf should have lunged in 150 ticks");
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void deathEnragesThePackBriefly(TestContext ctx) {
		AlphaWolfEntity alpha = ctx.spawnEntity(AlphaWolves.ALPHA_WOLF, 4, 2, 4);
		List<PackWolfEntity> members = alpha.spawnPack(4);
		members.get(0).kill();
		int ticks = (int) Math.round(CONFIG.enrage.seconds * 20);
		for (PackWolfEntity wolf : List.of(alpha, members.get(1), members.get(2), members.get(3))) {
			StatusEffectInstance strength = wolf.getStatusEffect(StatusEffects.STRENGTH);
			check(strength != null, "every living wolf should be enraged when one dies");
			check(strength.getAmplifier() == CONFIG.enrage.strengthLevel - 1 && strength.getDuration() == ticks,
					"Enrage should be Strength " + CONFIG.enrage.strengthLevel + " for " + ticks + " ticks, is level "
							+ (strength.getAmplifier() + 1) + " for " + strength.getDuration());
		}
		// Two more deaths in the same tick: refreshed, not stacked or lengthened.
		members.get(1).kill();
		members.get(2).kill();
		StatusEffectInstance strength = members.get(3).getStatusEffect(StatusEffects.STRENGTH);
		check(strength.getAmplifier() == CONFIG.enrage.strengthLevel - 1 && strength.getDuration() == ticks,
				"deaths close together shouldn't stack Enrage: level " + (strength.getAmplifier() + 1) + ", " + strength.getDuration() + " ticks");
		check(CONFIG.enrage.seconds == 1.0, "Enrage should default to 1 second");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
	public void killingTheAlphaScattersThePack(TestContext ctx) {
		AlphaWolfEntity alpha = ctx.spawnEntity(AlphaWolves.ALPHA_WOLF, 4, 2, 4);
		List<PackWolfEntity> members = alpha.spawnPack(3);
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		for (PackWolfEntity wolf : members) {
			wolf.setTarget(player);
		}
		alpha.kill();
		for (PackWolfEntity wolf : members) {
			check(wolf.isScattering(), "with the Alpha dead its wolves should scatter");
			check(wolf.getTarget() == null, "a scattering wolf stops fighting");
			check(!wolf.hasStatusEffect(StatusEffects.STRENGTH), "the Alpha's death isn't an Enrage");
			wolf.setTarget(player);
			check(wolf.getTarget() == null, "a scattering wolf can't be drawn back in");
		}
		// No player near: they vanish once they've had a head start.
		ctx.runAtTick(80, () -> {
			check(members.stream().allMatch(PackWolfEntity::isRemoved), "scattered wolves should vanish once away");
			ctx.complete();
		});
	}

	// --- Draining Bite ---

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void drainingBiteTakesMaxHealthForAWhile(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		PackWolfEntity wolf = ctx.spawnEntity(AlphaWolves.PACK_WOLF, 1, 2, 1);
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		float max = player.getMaxHealth();
		float drain = (float) (CONFIG.drainingBite.hearts * 2);
		check(drain == 4.0f, "Draining Bite should default to 2 hearts");

		player.damage(world.getDamageSources().mobAttack(wolf), 2.0f);
		StatusEffectInstance drained = player.getStatusEffect(AlphaWolves.DRAINED);
		check(drained != null, "a pack wolf's bite should drain the player");
		check(drained.getDuration() == (int) Math.round(CONFIG.drainingBite.seconds * 20), "Drained should last 10 s, lasts " + drained.getDuration());
		check(player.getMaxHealth() == max - drain, "max health should drop by " + drain + ", is " + player.getMaxHealth());
		check(player.getHealth() <= player.getMaxHealth(), "health should fit the lower max");
		check(DrainingBite.drainedHearts(player) == 2, "the HUD should show 2 drained hearts, shows " + DrainingBite.drainedHearts(player));

		// More bites while drained: no stacking, no extending.
		player.getStatusEffect(AlphaWolves.DRAINED);
		check(!DrainingBite.onHit(player, world.getDamageSources().mobAttack(wolf), 2.0f, false), "a bite while drained shouldn't re-apply");
		check(player.getMaxHealth() == max - drain, "repeated bites never take more than " + drain);

		// It wears off (or milk cures it) and the max health comes back.
		player.removeStatusEffect(AlphaWolves.DRAINED);
		check(player.getMaxHealth() == max, "max health should come back when Drained ends, is " + player.getMaxHealth());

		// Not from a shield-blocked bite, an ordinary wolf, or a pack wolf's non-bite damage.
		PlayerEntity other = ctx.createMockPlayer(GameMode.SURVIVAL);
		check(!DrainingBite.onHit(other, world.getDamageSources().mobAttack(wolf), 2.0f, true), "a blocked bite shouldn't drain");
		WolfEntity plain = ctx.spawnEntity(EntityType.WOLF, 2, 2, 2);
		other.damage(world.getDamageSources().mobAttack(plain), 2.0f);
		check(!other.hasStatusEffect(AlphaWolves.DRAINED), "an ordinary wolf's bite shouldn't drain");
		check(!DrainingBite.onHit(other, world.getDamageSources().create(Conductivity.DAMAGE_TYPE, wolf), 2.0f, false),
				"only bites drain, not other damage from a pack wolf");
		ctx.complete();
	}

	// --- Drops ---

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void alphaDropsHidesAndRarelyTheMark(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		AlphaWolfEntity alpha = ctx.spawnEntity(AlphaWolves.ALPHA_WOLF, 4, 2, 4);
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(AlphaWolves.ALPHA_WOLF.getLootTableId());
		int marks = 0, rolls = 2000;
		java.util.Set<Integer> toothCounts = new java.util.HashSet<>();
		for (long seed = 0; seed < rolls; seed++) {
			List<ItemStack> drops = table.generateLoot(killedBy(world, alpha, player), seed * 0x9E3779B97F4A7C15L);
			int hides = count(drops, AlphaHide.ALPHA_HIDE);
			check(hides >= 5 && hides <= 7, "the Alpha should drop 5–7 Alpha Hides, dropped " + hides);
			marks += count(drops, MarkOfThePack.MARK_OF_THE_PACK);
			int teeth = count(drops, AlphaWolves.WOLF_TOOTH);
			check(teeth >= 3 && teeth <= 6, "the Alpha should always drop 3–6 Wolf Teeth, dropped " + teeth);
			toothCounts.add(teeth);
		}
		check(marks > rolls * 0.07 && marks < rolls * 0.13, "the Mark should drop about 10% of the time, dropped " + marks + "/" + rolls);
		check(toothCounts.equals(java.util.Set.of(3, 4, 5, 6)), "every count from 3 to 6 teeth should come up, saw " + toothCounts);

		PackWolfEntity wolf = ctx.spawnEntity(AlphaWolves.PACK_WOLF, 1, 2, 1);
		LootTable wolfTable = world.getServer().getReloadableRegistries().getLootTable(AlphaWolves.PACK_WOLF.getLootTableId());
		int wolfTeeth = 0;
		for (long seed = 0; seed < rolls; seed++) {
			List<ItemStack> drops = wolfTable.generateLoot(killedBy(world, wolf, player), seed * 0x9E3779B97F4A7C15L);
			check(drops.stream().allMatch(s -> s.isOf(AlphaWolves.WOLF_TOOTH) && s.getCount() == 1), "pack wolves drop at most one tooth");
			wolfTeeth += drops.size();
		}
		check(wolfTeeth > rolls * 0.07 && wolfTeeth < rolls * 0.13, "pack wolves should drop a tooth about 10% of the time, " + wolfTeeth + "/" + rolls);
		ctx.complete();
	}

	private static LootContextParameterSet killedBy(ServerWorld world, net.minecraft.entity.LivingEntity victim, PlayerEntity player) {
		DamageSource source = world.getDamageSources().playerAttack(player);
		return new LootContextParameterSet.Builder(world)
				.add(LootContextParameters.THIS_ENTITY, victim)
				.add(LootContextParameters.ORIGIN, victim.getPos())
				.add(LootContextParameters.DAMAGE_SOURCE, source)
				.add(LootContextParameters.ATTACKING_ENTITY, player)
				.add(LootContextParameters.DIRECT_ATTACKING_ENTITY, player)
				.add(LootContextParameters.LAST_DAMAGE_PLAYER, player)
				.build(LootContextTypes.ENTITY);
	}

	private static int count(List<ItemStack> stacks, Item item) {
		return stacks.stream().filter(s -> s.isOf(item)).mapToInt(ItemStack::getCount).sum();
	}

	// --- Alpha Hide armour ---

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void alphaHideIsAnInsulatedIronAlternative(TestContext ctx) {
		checkArmor(AlphaHide.HELMET, 2, 165);
		checkArmor(AlphaHide.CHESTPLATE, 5, 240);
		checkArmor(AlphaHide.LEGGINGS, 4, 225);
		checkArmor(AlphaHide.BOOTS, 2, 195);
		int set = AlphaHide.ARMOR.stream().mapToInt(item -> ((ArmorItem) item).getProtection()).sum();
		int iron = ((ArmorItem) Items.IRON_HELMET).getProtection() + ((ArmorItem) Items.IRON_CHESTPLATE).getProtection()
				+ ((ArmorItem) Items.IRON_LEGGINGS).getProtection() + ((ArmorItem) Items.IRON_BOOTS).getProtection();
		check(set == 13 && set < iron, "a full set should give 13 armour, a little under iron's " + iron + "; gives " + set);

		TagKey<Item> heating = TagKey.of(RegistryKeys.ITEM, Identifier.of("toughasnails", "heating_armor"));
		for (Item item : AlphaHide.ARMOR) {
			ItemStack stack = new ItemStack(item);
			check(stack.isIn(ItemTags.FREEZE_IMMUNE_WEARABLES), item + " should keep you from freezing");
			check(stack.isIn(heating), item + " should be warm clothing for Tough As Nails");
			check(stack.isIn(AlphaHide.PACK_INSTINCT_ITEMS) && stack.isIn(AlphaHide.INSULATED_ITEMS), item + " should have its passives");
		}
		check(new ItemStack(AlphaHide.HELMET).isIn(ItemTags.HEAD_ARMOR), "the cap should be head armour (enchantable)");

		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		check(player.canFreeze(), "a player without fur should be able to freeze");
		player.equipStack(EquipmentSlot.FEET, new ItemStack(AlphaHide.BOOTS));
		check(!player.canFreeze(), "any Alpha Hide piece should stop freezing");

		ItemStack hide = new ItemStack(AlphaHide.ALPHA_HIDE);
		check(craft(ctx, 3, 2, List.of(hide, hide, hide, hide, ItemStack.EMPTY, hide)).isOf(AlphaHide.HELMET), "5 hides make a cap");
		check(craft(ctx, 3, 3, List.of(hide, ItemStack.EMPTY, hide, hide, hide, hide, hide, hide, hide)).isOf(AlphaHide.CHESTPLATE), "8 hides make a tunic");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void packInstinctTriggersOnceThenRests(TestContext ctx) {
		FrostboundConfig.PackInstinctOptions config = FrostboundConfig.get().packInstinct;
		check(config.belowHealth == 8.0, "Pack Instinct should default to below 4 hearts");
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(AlphaHide.HELMET));
		player.setHealth(7.0f);
		check(!AlphaHide.tick(player), "one piece isn't a set");

		player.equipStack(EquipmentSlot.CHEST, new ItemStack(AlphaHide.CHESTPLATE));
		player.setHealth(9.0f);
		check(!AlphaHide.tick(player), "above 4 hearts nothing happens");
		player.setHealth(7.0f);
		check(AlphaHide.tick(player), "2 pieces below 4 hearts should trigger");
		StatusEffectInstance regen = player.getStatusEffect(StatusEffects.REGENERATION);
		check(regen != null && regen.getAmplifier() == config.regenerationLevel - 1, "2 pieces give Regeneration " + config.regenerationLevel);
		check(!player.hasStatusEffect(StatusEffects.STRENGTH), "2 pieces don't give Strength");

		// Bobbing over and under the line doesn't trigger it again until it has rested.
		player.clearStatusEffects();
		player.setHealth(10.0f);
		AlphaHide.tick(player);
		player.setHealth(6.0f);
		check(!AlphaHide.tick(player), "Pack Instinct shouldn't trigger again while resting");
		check(AlphaHide.cooldownLeft(player) > 0, "it should be resting");
		check(player.getItemCooldownManager().isCoolingDown(AlphaHide.HELMET), "the rest should show on the armour icons");

		// Rested, with all 4: Regeneration and Strength.
		player.setAttached(AlphaHide.READY_AT, 0L);
		player.equipStack(EquipmentSlot.LEGS, new ItemStack(AlphaHide.LEGGINGS));
		player.equipStack(EquipmentSlot.FEET, new ItemStack(AlphaHide.BOOTS));
		check(AlphaHide.tick(player), "rested, it should trigger again");
		check(player.hasStatusEffect(StatusEffects.REGENERATION) && player.hasStatusEffect(StatusEffects.STRENGTH),
				"4 pieces give Regeneration and Strength");
		ctx.complete();
	}

	// --- Life Steal ---

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void lifeStealBooksAreCraftedFromTeeth(TestContext ctx) {
		RegistryEntry<Enchantment> lifeSteal = lifeSteal(ctx);
		ItemStack tooth = new ItemStack(AlphaWolves.WOLF_TOOTH);
		ItemStack book = new ItemStack(Items.BOOK);
		ItemStack e = ItemStack.EMPTY;

		ItemStack one = craft(ctx, 3, 3, List.of(e, tooth, e, tooth, book, tooth, e, tooth, e));
		check(one.isOf(Items.ENCHANTED_BOOK) && storedLevel(one, lifeSteal) == 1, "a book with 4 teeth on its sides makes Life Steal I");
		check(craft(ctx, 3, 3, List.of(tooth, e, tooth, e, book, e, tooth, e, tooth)).isEmpty(), "teeth on the diagonals don't count");

		ItemStack two = craft(ctx, 3, 3, List.of(tooth, tooth, tooth, tooth, one, tooth, tooth, tooth, tooth));
		check(two.isOf(Items.ENCHANTED_BOOK) && storedLevel(two, lifeSteal) == 2, "Life Steal I with 8 teeth around it makes Life Steal II");
		check(craft(ctx, 3, 3, List.of(tooth, tooth, tooth, tooth, book, tooth, tooth, tooth, tooth)).isEmpty(), "a plain book with 8 teeth makes nothing");
		check(craft(ctx, 3, 3, List.of(tooth, tooth, tooth, tooth, two, tooth, tooth, tooth, tooth)).isEmpty(), "there's no Life Steal III");
		check(lifeSteal.value().getMaxLevel() == 2, "Life Steal should go to II");
		check(lifeSteal.value().isAcceptableItem(new ItemStack(Items.IRON_SWORD)) && lifeSteal.value().isAcceptableItem(new ItemStack(Items.BOW))
				&& lifeSteal.value().isAcceptableItem(new ItemStack(Items.CROSSBOW)), "Life Steal goes on swords, bows and crossbows");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void lifeStealHealsTheSameForMeleeAndRanged(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		RegistryEntry<Enchantment> lifeSteal = lifeSteal(ctx);
		double perLevel = FrostboundConfig.get().lifeSteal.healPerLevel;

		// Melee, Life Steal II: heals 2 × 10% of what the pig lost.
		PlayerEntity fighter = ctx.createMockPlayer(GameMode.SURVIVAL);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.addEnchantment(lifeSteal, 2);
		fighter.setStackInHand(Hand.MAIN_HAND, sword);
		fighter.setHealth(10.0f);
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, 1, 2, 1);
		pig.damage(world.getDamageSources().playerAttack(fighter), 5.0f);
		checkNear(fighter.getHealth(), 10.0f + (float) (5.0 * 2 * perLevel), "melee Life Steal II on a 5 damage hit");

		// Ranged, Life Steal I on the bow that shot the arrow: the same rule.
		PlayerEntity archer = ctx.createMockPlayer(GameMode.SURVIVAL);
		ItemStack bow = new ItemStack(Items.BOW);
		bow.addEnchantment(lifeSteal, 1);
		archer.setHealth(10.0f);
		ArrowEntity arrow = new ArrowEntity(world, archer, new ItemStack(Items.ARROW), bow);
		PigEntity pig2 = ctx.spawnEntity(EntityType.PIG, 3, 2, 1);
		pig2.damage(world.getDamageSources().arrow(arrow, archer), 4.0f);
		checkNear(archer.getHealth(), 10.0f + (float) (4.0 * perLevel), "an arrow from a Life Steal I bow");

		// Overkill heals only what the target had left.
		fighter.setHealth(10.0f);
		PigEntity weak = ctx.spawnEntity(EntityType.PIG, 5, 2, 1);
		weak.setHealth(2.0f);
		weak.damage(world.getDamageSources().playerAttack(fighter), 9.0f);
		checkNear(fighter.getHealth(), 10.0f + (float) (2.0 * 2 * perLevel), "overkill should heal only for the 2 health the pig had");

		// No heal from arcs, unenchanted weapons, or against armour stands.
		fighter.setHealth(10.0f);
		PigEntity pig3 = ctx.spawnEntity(EntityType.PIG, 1, 2, 4);
		pig3.damage(world.getDamageSources().create(Conductivity.DAMAGE_TYPE, fighter), 4.0f);
		checkNear(fighter.getHealth(), 10.0f, "a Conductivity arc shouldn't heal");
		PlayerEntity plain = ctx.createMockPlayer(GameMode.SURVIVAL);
		plain.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		plain.setHealth(10.0f);
		PigEntity pig4 = ctx.spawnEntity(EntityType.PIG, 3, 2, 4);
		pig4.damage(world.getDamageSources().playerAttack(plain), 4.0f);
		checkNear(plain.getHealth(), 10.0f, "an unenchanted sword shouldn't heal");
		ctx.complete();
	}

	private static RegistryEntry<Enchantment> lifeSteal(TestContext ctx) {
		return ctx.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(LifeSteal.KEY);
	}

	private static int storedLevel(ItemStack book, RegistryEntry<Enchantment> enchantment) {
		return book.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).getLevel(enchantment);
	}

	// --- Mark of the Pack ---

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
	public void markOfThePackCallsTwoWolves(TestContext ctx) {
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		player.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(4, 2, 4))));
		check(MarkOfThePack.activate(player).isEmpty(), "without the Mark worn nothing happens");

		Map<String, Map<String, TrinketInventory>> slots = TrinketsApi.getTrinketComponent(player).orElseThrow().getInventory();
		check(slots.containsKey("chest") && slots.get("chest").containsKey("necklace"), "players should have a necklace slot, have " + slots.keySet());
		check(new ItemStack(MarkOfThePack.MARK_OF_THE_PACK).isIn(TagKey.of(RegistryKeys.ITEM, Identifier.of("trinkets", "chest/necklace"))),
				"the Mark should fit the necklace slot");
		slots.get("chest").get("necklace").setStack(0, new ItemStack(MarkOfThePack.MARK_OF_THE_PACK));
		check(MarkOfThePack.isWorn(player), "the Mark should count as worn");

		List<SummonedWolfEntity> wolves = MarkOfThePack.activate(player);
		FrostboundConfig.MarkOfThePackOptions config = FrostboundConfig.get().markOfThePack;
		check(wolves.size() == config.wolves && config.wolves == 2, "the Mark should call 2 wolves, called " + wolves.size());
		for (SummonedWolfEntity wolf : wolves) {
			check(wolf.isAlive() && wolf.isTamed() && player.getUuid().equals(wolf.getOwnerUuid()), "called wolves should be the player's");
			check(wolf.ticksLeft() == (int) Math.round(config.lifetimeSeconds * 20), "called wolves should last 30 s");
		}
		check(MarkOfThePack.activate(player).isEmpty(), "it should recharge before working again");
		check(MarkOfThePack.cooldownLeft(player) == (long) Math.round(config.cooldownSeconds * 20), "the recharge should be 60 s");
		check(player.getItemCooldownManager().isCoolingDown(MarkOfThePack.MARK_OF_THE_PACK), "the recharge should show on the item");

		// They vanish when their time is up.
		wolves.forEach(wolf -> wolf.setTicksLeft(3));
		ctx.runAtTick(15, () -> {
			check(wolves.stream().allMatch(SummonedWolfEntity::isRemoved), "called wolves should vanish when their time is up");
			ctx.complete();
		});
	}

	// --- Tooltips ---

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void alphaRewardTooltips(TestContext ctx) {
		checkPassive(AlphaHide.PACK_INSTINCT, "Pack Instinct", Formatting.DARK_RED,
				"Below 4 hearts: Regeneration II with 2 pieces worn, plus Strength I with all 4. Rests 60 seconds after.");
		checkPassive(AlphaHide.INSULATED, "Insulated", Formatting.WHITE,
				"Lined with fur: you can't freeze, and temperature mods count it as warm clothing.");
		checkPassive(MarkOfThePack.PASSIVE, "Pack Call", Formatting.GOLD,
				"Press J to call 2 wolves that fight for you for 30 seconds. Recharges in 60 seconds.");
		check(Passives.of(new ItemStack(AlphaHide.CHESTPLATE)).containsAll(List.of(AlphaHide.INSULATED, AlphaHide.PACK_INSTINCT)),
				"Alpha Hide armour should show Insulated and Pack Instinct");
		check(Passives.of(new ItemStack(MarkOfThePack.MARK_OF_THE_PACK)).equals(List.of(MarkOfThePack.PASSIVE)), "the Mark should show Pack Call");
		ctx.complete();
	}

	private static void checkPassive(Passive passive, String name, Formatting color, String description) {
		Text title = passive.title();
		check(title.getString().equals(name), "title should be " + name + ", is " + title.getString());
		check(!title.getStyle().isBold() && color.getColorValue().equals(title.getStyle().getColor().getRgb()), name + " should be " + color.getName());
		check(passive.description().getString().equals(description), name + " should read \"" + description + "\", reads \"" + passive.description().getString() + "\"");
	}

	// --- Helpers ---

	private static void checkArmor(Item item, int protection, int durability) {
		check(((ArmorItem) item).getProtection() == protection, item + " should give " + protection + " armour");
		check(new ItemStack(item).getMaxDamage() == durability, item + " should have " + durability + " durability, has " + new ItemStack(item).getMaxDamage());
	}

	private static ItemStack craft(TestContext ctx, int width, int height, List<ItemStack> stacks) {
		CraftingRecipeInput input = CraftingRecipeInput.create(width, height, stacks);
		return ctx.getWorld().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, ctx.getWorld())
				.map(entry -> entry.value().craft(input, ctx.getWorld().getRegistryManager()))
				.orElse(ItemStack.EMPTY);
	}

	private static void checkNear(float actual, float expected, String what) {
		check(Math.abs(actual - expected) < 0.01f, what + ": expected health " + expected + ", got " + actual);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}

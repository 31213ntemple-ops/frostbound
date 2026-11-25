package com.frostbound.imbuement;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import com.frostbound.copper.Conductivity;
import com.frostbound.passives.Passive;
import com.frostbound.passives.Passives;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * The material bonuses of Icy and Frozen armour, one per armour material, each a passive in the tooltip.
 * Every one counts per piece worn and adds up, and a Frozen piece counts its stronger number, so a mixed
 * set works too. Numbers are in {@code frostBonuses} in config/frostbound.json; which items have each is
 * an item tag ({@code frostbound:snowstride}, ...), so a data pack can give modded armour one.
 *
 * <ul>
 *   <li>Leather, Snowstride: faster on snow and ice. Cheap armour, but quick in a frozen world.</li>
 *   <li>Copper, Superconductor: any melee hit can arc like copper's Conductivity (cold copper conducts best).</li>
 *   <li>Chainmail, Frostmail: less damage from arrows and other projectiles (strays, skeletons).</li>
 *   <li>Iron, Cold Iron: more melee damage to undead, as in the old stories of cold iron.</li>
 *   <li>Gold, Hearthgold: slowly heals. Gold keeps the warmth in.</li>
 *   <li>Diamond, Diamond Dust: melee attackers take part of the damage back as frost.</li>
 *   <li>Netherite, Quenched: less fire and lava damage, like quenched metal.</li>
 *   <li>Turtle shell, Tidefrost: the helmet's Water Breathing lasts longer.</li>
 *   <li>Weapons, Frostbite: melee hits slow the target (counts the weapon in the main hand, not armour).</li>
 * </ul>
 */
public final class FrostBonuses {
	/** Damage type of Diamond Dust's frost, so deaths read "was frozen while trying to hurt". */
	public static final RegistryKey<DamageType> DIAMOND_DUST_DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Frostbound.id("diamond_dust"));
	private static final Identifier SNOWSTRIDE_SPEED = Frostbound.id("snowstride");
	private static final int TICKS_PER_SECOND = 20;

	/** One material bonus: the items that have it, its {icy, frozen} numbers and its tooltip passive. */
	public static final class Bonus {
		public final TagKey<Item> items;
		private final Function<FrostboundConfig.FrostBonusOptions, FrostboundConfig.Tiered> option;
		public final Passive passive;

		private Bonus(String id, Formatting color, Function<FrostboundConfig.FrostBonusOptions, FrostboundConfig.Tiered> option,
				Function<Double, Object[]> descriptionArgs) {
			this.items = TagKey.of(RegistryKeys.ITEM, Frostbound.id(id));
			this.option = option;
			this.passive = Passives.register(new Passive(id, color,
					stack -> stack.isIn(items) && FrostTiers.tier(stack).atLeast(FrostTier.ICY),
					stack -> descriptionArgs.apply(perPiece(stack))));
		}

		/** This piece's number: the Icy or Frozen one, or 0 if it isn't one of the items or isn't Icy yet. */
		public double perPiece(ItemStack stack) {
			if (!stack.isIn(items)) {
				return 0.0;
			}
			FrostboundConfig.Tiered numbers = option.apply(FrostboundConfig.get().frostBonuses);
			return switch (FrostTiers.tier(stack)) {
				case ICY -> numbers.icy;
				case FROZEN -> numbers.frozen;
				default -> 0.0;
			};
		}

		/** The sum over every armour piece {@code entity} wears. */
		public double total(LivingEntity entity) {
			double total = 0.0;
			for (ItemStack stack : entity.getArmorItems()) {
				total += perPiece(stack);
			}
			return total;
		}
	}

	// Each name is coloured after what it does, matching the potion or effect it's closest to.
	/** White: snow, and quick feet on it. */
	public static final Bonus SNOWSTRIDE = new Bonus("snowstride", Formatting.WHITE, o -> o.snowstride, v -> percent(v));
	/** Yellow: electricity. */
	public static final Bonus SUPERCONDUCTOR = new Bonus("superconductor", Formatting.YELLOW, o -> o.superconductor, v -> percent(v));
	/** Blue: protection. */
	public static final Bonus FROSTMAIL = new Bonus("frostmail", Formatting.BLUE, o -> o.frostmail, v -> percent(v));
	/** Red: extra damage. */
	public static final Bonus COLD_IRON = new Bonus("cold_iron", Formatting.RED, o -> o.coldIron, v -> percent(v));
	/** Pink: healing, like Regeneration. */
	public static final Bonus HEARTHGOLD = new Bonus("hearthgold", Formatting.LIGHT_PURPLE, o -> o.hearthgold, v -> new Object[] {Passive.number(v)});
	/** Aqua: frost dealt back. */
	public static final Bonus DIAMOND_DUST = new Bonus("diamond_dust", Formatting.AQUA, o -> o.diamondDust, v -> percent(v));
	/** Orange: fire, like Fire Resistance. */
	public static final Bonus QUENCHED = new Bonus("quenched", Formatting.GOLD, o -> o.quenched, v -> percent(v));
	/** Dark aqua: water, like Water Breathing. */
	public static final Bonus TIDEFROST = new Bonus("tidefrost", Formatting.DARK_AQUA, o -> o.tidefrost, v -> new Object[] {Passive.number(v)});
	/** Aqua: frost dealt, like Diamond Dust (which is on armour, so the two never share a tooltip). */
	public static final Bonus FROSTBITE = new Bonus("frostbite", Formatting.AQUA, o -> o.frostbite, v -> new Object[] {Passive.number(v)});

	/** Healing Hearthgold has built up towards the next half heart, per player. */
	private static final Map<PlayerEntity, Double> WARMTH = new WeakHashMap<>();

	private FrostBonuses() {}

	private static Object[] percent(double value) {
		return new Object[] {Passive.number(value * 100)};
	}

	static void init() {
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			for (ServerPlayerEntity player : world.getPlayers()) {
				tick(player);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, baseDamage, damageTaken, blocked) -> {
			if (!blocked && damageTaken > 0.0f) {
				diamondDust(target, source, damageTaken);
				frostbite(target, source);
			}
		});
	}

	/** The once-a-tick bonuses: Snowstride, Hearthgold and Tidefrost. */
	public static void tick(PlayerEntity player) {
		if (player.getWorld().isClient) {
			return;
		}
		// Snowstride: a speed modifier while standing on snow or ice.
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			double boost = isOnSnowOrIce(player) ? SNOWSTRIDE.total(player) : 0.0;
			EntityAttributeModifier current = speed.getModifier(SNOWSTRIDE_SPEED);
			if (current == null || current.value() != boost) {
				speed.removeModifier(SNOWSTRIDE_SPEED);
				if (boost > 0.0) {
					speed.addTemporaryModifier(new EntityAttributeModifier(SNOWSTRIDE_SPEED, boost,
							EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				}
			}
		}

		// Hearthgold: each piece heals half a heart every N seconds, so the rates add up.
		double perSecond = 0.0;
		for (ItemStack stack : player.getArmorItems()) {
			double seconds = HEARTHGOLD.perPiece(stack);
			if (seconds > 0.0) {
				perSecond += 1.0 / seconds;
			}
		}
		if (perSecond > 0.0 && player.isAlive()) {
			double warmth = WARMTH.getOrDefault(player, 0.0) + perSecond / TICKS_PER_SECOND;
			if (warmth >= 1.0 - 1e-9 && player.getHealth() < player.getMaxHealth()) {
				player.heal(1.0f);
				warmth -= 1.0;
			}
			WARMTH.put(player, Math.min(warmth, 1.0));
		} else {
			WARMTH.remove(player);
		}

		// Tidefrost: vanilla gives 10 s of Water Breathing every tick the turtle shell is above water; this tops it up.
		double extraSeconds = TIDEFROST.perPiece(player.getEquippedStack(EquipmentSlot.HEAD));
		if (extraSeconds > 0.0 && !player.isSubmergedIn(FluidTags.WATER)) {
			int ticks = 200 + (int) Math.round(extraSeconds * TICKS_PER_SECOND);
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, ticks, 0, false, false, true));
		}
	}

	/** On snow (a layer, a block, powder snow, snowy grass) or ice, with feet on the ground. */
	public static boolean isOnSnowOrIce(LivingEntity entity) {
		if (!entity.isOnGround()) {
			return false;
		}
		BlockState feet = entity.getWorld().getBlockState(entity.getBlockPos());
		BlockState below = entity.getWorld().getBlockState(entity.getSteppingPos());
		return feet.isIn(BlockTags.SNOW) || below.isIn(BlockTags.SNOW) || below.isIn(BlockTags.ICE)
				|| (below.contains(Properties.SNOWY) && below.get(Properties.SNOWY));
	}

	/**
	 * Damage {@code target} takes from {@code source}, after Cold Iron (the attacker's), Frostmail and
	 * Quenched (the target's). Called at the start of LivingEntity.damage (mixin.imbuement.FrostBonusDamageMixin).
	 */
	public static float modifyDamage(LivingEntity target, DamageSource source, float amount) {
		if (source.getAttacker() instanceof LivingEntity attacker && Conductivity.isMelee(source)
				&& target.getType().isIn(EntityTypeTags.UNDEAD)) {
			amount *= (float) (1.0 + COLD_IRON.total(attacker));
		}
		if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
			amount *= (float) Math.max(0.0, 1.0 - FROSTMAIL.total(target));
		}
		if (source.isIn(DamageTypeTags.IS_FIRE)) {
			amount *= (float) Math.max(0.0, 1.0 - QUENCHED.total(target));
		}
		return amount;
	}

	/** Diamond Dust: a melee attacker takes a share of the damage it dealt back, as frost. */
	private static void diamondDust(LivingEntity target, DamageSource source, float damageTaken) {
		if (!(source.getAttacker() instanceof LivingEntity attacker) || attacker == target || !Conductivity.isMelee(source)
				|| !(target.getWorld() instanceof ServerWorld world)) {
			return;
		}
		double share = DIAMOND_DUST.total(target);
		if (share <= 0.0) {
			return;
		}
		attacker.damage(world.getDamageSources().create(DIAMOND_DUST_DAMAGE, target), (float) (damageTaken * share));
		world.spawnParticles(ParticleTypes.SNOWFLAKE, attacker.getX(), attacker.getBodyY(0.6), attacker.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
		world.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.BLOCK_GLASS_BREAK,
				SoundCategory.PLAYERS, 0.4f, 1.8f);
	}

	/** Frostbite: a melee hit with an Icy or Frozen weapon slows the target for a few seconds. */
	private static void frostbite(LivingEntity target, DamageSource source) {
		if (!(source.getAttacker() instanceof LivingEntity attacker) || attacker == target || !Conductivity.isMelee(source)
				|| !target.isAlive() || !(target.getWorld() instanceof ServerWorld world)) {
			return;
		}
		double seconds = FROSTBITE.perPiece(attacker.getMainHandStack());
		if (seconds <= 0.0) {
			return;
		}
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, (int) Math.round(seconds * TICKS_PER_SECOND), 0), attacker);
		world.spawnParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getBodyY(0.5), target.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
	}
}

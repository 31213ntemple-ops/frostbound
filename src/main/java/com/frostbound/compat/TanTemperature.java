package com.frostbound.compat;

import com.frostbound.Frostbound;
import com.frostbound.addons.FlameExtract;
import com.frostbound.habanero.Habaneros;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Warmth from Frostbound in Tough As Nails' temperature: one step warmer while Spicy, and one more while fully
 * insulated by flame extract. Like TAN's own warmth from food and clothing, this never pushes you into Hot (it
 * stops at Warm) unless you were Hot already.
 *
 * Registered through TAN's API ({@code TemperatureHelper.registerPlayerTemperatureModifier}) by reflection, so
 * Frostbound builds and runs without TAN. Done when the first server starts (TAN sets its API up in its own init).
 */
public final class TanTemperature {
	private static boolean registered;

	private TanTemperature() {}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("toughasnails")) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTING.register(server -> register());
	}

	/** Steps warmer Frostbound makes {@code player}: Spicy and full insulation, one each. */
	public static int warmth(PlayerEntity player) {
		int steps = 0;
		if (player.hasStatusEffect(Habaneros.SPICY_EFFECT)) {
			steps++;
		}
		if (FlameExtract.isFullyInsulated(player)) {
			steps++;
		}
		return steps;
	}

	/** {@code level} (TAN's TemperatureLevel: ICY, COLD, NEUTRAL, WARM, HOT) moved {@code steps} warmer, stopping at Warm. */
	public static <E extends Enum<E>> E warmer(E level, int steps) {
		E[] levels = level.getDeclaringClass().getEnumConstants();
		int hot = levels.length - 1;
		int from = level.ordinal();
		int to = Math.min(hot, from + steps);
		if (to == hot && from != hot) {
			to = hot - 1;
		}
		return levels[Math.max(from, to)];
	}

	private static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		try {
			Class<?> modifier = Class.forName("toughasnails.api.temperature.IPlayerTemperatureModifier");
			Method register = Class.forName("toughasnails.api.temperature.TemperatureHelper")
					.getMethod("registerPlayerTemperatureModifier", modifier);
			Object proxy = Proxy.newProxyInstance(TanTemperature.class.getClassLoader(), new Class<?>[] {modifier}, (self, method, args) -> {
				if (method.getName().equals("modify") && args != null && args.length == 2) {
					return modify((PlayerEntity) args[0], args[1]);
				}
				return switch (method.getName()) {
					case "hashCode" -> System.identityHashCode(self);
					case "equals" -> self == args[0];
					case "toString" -> "Frostbound warmth";
					default -> null;
				};
			});
			register.invoke(null, proxy);
		} catch (ReflectiveOperationException | LinkageError e) {
			Frostbound.LOGGER.warn("Could not hook Frostbound's warmth (spicy food, insulation) into Tough As Nails", e);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object modify(PlayerEntity player, Object level) {
		int steps = warmth(player);
		return steps == 0 || !(level instanceof Enum e) ? level : warmer(e, steps);
	}
}

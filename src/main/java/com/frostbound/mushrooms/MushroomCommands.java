package com.frostbound.mushrooms;

import com.frostbound.Frostbound;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;

/**
 * {@code /frostbound mushroom place <shape> [pos]}, {@code /frostbound mushroom regrow} (puts the nearest
 * one back whole), {@code /frostbound mushroom remove}, and {@code /frostbound spores [set <count>]} (your
 * spores). Operators only.
 */
final class MushroomCommands {
	/** How far (blocks, from the mushroom's origin) the regrow/remove commands look for one. */
	private static final double REACH = 48.0;
	private static final SimpleCommandExceptionType NONE_NEAR = new SimpleCommandExceptionType(
			Text.literal("No giant mushroom within " + (int) REACH + " blocks"));
	private static final SimpleCommandExceptionType UNKNOWN_SHAPE = new SimpleCommandExceptionType(
			Text.literal("Unknown mushroom shape"));

	private MushroomCommands() {}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal(Frostbound.MOD_ID)
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("mushroom")
						.then(CommandManager.literal("place")
								.then(CommandManager.argument("shape", StringArgumentType.word())
										.suggests((ctx, builder) -> CommandSource.suggestMatching(
												Arrays.stream(GiantMushroomShape.values()).map(GiantMushroomShape::id), builder))
										.executes(ctx -> place(ctx, BlockPos.ofFloored(ctx.getSource().getPosition())))
										.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
												.executes(ctx -> place(ctx, BlockPosArgumentType.getBlockPos(ctx, "pos"))))))
						.then(CommandManager.literal("regrow").executes(ctx -> {
							Mushroom m = nearest(ctx.getSource());
							GiantMushrooms.regrow(ctx.getSource().getWorld(), m);
							ctx.getSource().sendFeedback(() -> Text.literal("Regrew the " + m.shape.id() + " at " + m.origin.toShortString()), true);
							return 1;
						}))
						.then(CommandManager.literal("remove").executes(ctx -> {
							Mushroom m = nearest(ctx.getSource());
							GiantMushrooms.remove(ctx.getSource().getWorld(), m);
							ctx.getSource().sendFeedback(() -> Text.literal("Removed the " + m.shape.id() + " at " + m.origin.toShortString()), true);
							return 1;
						})))
				.then(CommandManager.literal("spores")
						.executes(ctx -> {
							int count = Spores.get(ctx.getSource().getPlayerOrThrow());
							ctx.getSource().sendFeedback(() -> Text.translatable("frostbound.spores.count", count), false);
							return count;
						})
						.then(CommandManager.literal("set")
								.then(CommandManager.argument("count", IntegerArgumentType.integer(0))
										.executes(ctx -> {
											int count = IntegerArgumentType.getInteger(ctx, "count");
											Spores.set(ctx.getSource().getPlayerOrThrow(), count);
											ctx.getSource().sendFeedback(() -> Text.translatable("frostbound.spores.count", count), true);
											return count;
										})))));
	}

	private static int place(CommandContext<ServerCommandSource> ctx, BlockPos pos) throws CommandSyntaxException {
		GiantMushroomShape shape = GiantMushroomShape.byId(StringArgumentType.getString(ctx, "shape"));
		if (shape == null) {
			throw UNKNOWN_SHAPE.create();
		}
		GiantMushrooms.place(ctx.getSource().getWorld(), pos, shape);
		ctx.getSource().sendFeedback(() -> Text.literal("Placed a giant " + shape.id() + " at " + pos.toShortString()), true);
		return 1;
	}

	private static Mushroom nearest(ServerCommandSource source) throws CommandSyntaxException {
		Mushroom m = MushroomStore.get(source.getWorld()).nearest(source.getPosition(), REACH);
		if (m == null) {
			throw NONE_NEAR.create();
		}
		return m;
	}
}

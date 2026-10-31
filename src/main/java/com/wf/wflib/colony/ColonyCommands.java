package com.wf.wflib.colony;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /wflib colony ...}. */
public final class ColonyCommands {
    private ColonyCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wflib")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("colony")
                        .executes(ctx -> ColonyDebug.status(ctx.getSource()))
                        .then(Commands.literal("list")
                                .executes(ctx -> ColonyDebug.list(ctx.getSource())))
                        .then(Commands.literal("nearby")
                                .executes(ctx -> ColonyDebug.nearby(ctx.getSource(), 2_000))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(16, 1_000_000))
                                        .executes(ctx -> ColonyDebug.nearby(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("inspect")
                                .executes(ctx -> ColonyDebug.inspect(ctx.getSource())))
                        .then(Commands.literal("provoke")
                                .executes(ctx -> ColonyDebug.provoke(ctx.getSource(), 100.0))
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0, 1.0E9))
                                        .executes(ctx -> ColonyDebug.provoke(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "amount")))))
                        .then(Commands.literal("grow")
                                .executes(ctx -> ColonyDebug.grow(ctx.getSource(), 50.0))
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(-1.0E9, 1.0E9))
                                        .executes(ctx -> ColonyDebug.grow(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "amount")))))
                        .then(Commands.literal("expand")
                                .executes(ctx -> ColonyDebug.expand(ctx.getSource())))
                        .then(Commands.literal("bud")
                                .executes(ctx -> ColonyDebug.bud(ctx.getSource(), 1))
                                .then(Commands.argument("cells", IntegerArgumentType.integer(1, 1_000))
                                        .executes(ctx -> ColonyDebug.bud(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "cells")))))
                        .then(Commands.literal("raze")
                                .executes(ctx -> ColonyDebug.raze(ctx.getSource())))
                        .then(Commands.literal("warbands")
                                .executes(ctx -> ColonyDebug.warbands(ctx.getSource())))
                        .then(Commands.literal("found")
                                .executes(ctx -> ColonyDebug.found(ctx.getSource())))
                        .then(Commands.literal("nest")
                                .executes(ctx -> ColonyDebug.nest(ctx.getSource())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> ColonyDebug.clear(ctx.getSource())))
                        .then(Commands.literal("fastforward")
                                .then(Commands.argument("rounds", IntegerArgumentType.integer(1, 100_000))
                                        .executes(ctx -> ColonyDebug.fastForward(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "rounds")))))
                        .then(Commands.literal("bugs")
                                .executes(ctx -> ColonyDebug.bugs(ctx.getSource())))
                        .then(Commands.literal("squads")
                                .executes(ctx -> ColonyDebug.squads(ctx.getSource())))
                        .then(Commands.literal("evolution")
                                .executes(ctx -> ColonyDebug.evolution(ctx.getSource()))
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                                        .executes(ctx -> ColonyDebug.evolution(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "value")))))
                        .then(Commands.literal("dispatch")
                                .executes(ctx -> ColonyDebug.dispatch(ctx.getSource(), 8, false))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 10_000))
                                        .executes(ctx -> ColonyDebug.dispatch(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "count"), false))
                                        .then(Commands.literal("flying")
                                                .executes(ctx -> ColonyDebug.dispatch(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "count"), true)))))
                        .then(Commands.literal("materialise")
                                .executes(ctx -> ColonyDebug.materialise(ctx.getSource(), 64))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 10_000))
                                        .executes(ctx -> ColonyDebug.materialise(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "count")))))));
    }

}

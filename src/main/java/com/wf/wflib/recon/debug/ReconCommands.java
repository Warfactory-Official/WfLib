package com.wf.wflib.recon.debug;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /wflib recon ...}. */
public final class ReconCommands {
    private ReconCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wflib")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("recon")
                        .executes(ctx -> ReconDebug.status(ctx.getSource()))
                        .then(Commands.literal("tracks")
                                .executes(ctx -> ReconDebug.tracks(ctx.getSource())))
                        .then(Commands.literal("explain")
                                .executes(ctx -> ReconDebug.explain(ctx.getSource())))
                        .then(Commands.literal("collect")
                                .executes(ctx -> ReconDebug.collect(ctx.getSource(), 256.0))
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0, 2048.0))
                                        .executes(ctx -> ReconDebug.collect(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "radius")))))
                        .then(Commands.literal("selftest")
                                .executes(ctx -> ReconDebug.selfTest(ctx.getSource())))
                        .then(Commands.literal("events")
                                .executes(ctx -> ReconDebug.events(ctx.getSource(), 20))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1,
                                                com.wf.wflib.recon.api.SeismicLog.MAX_CAPACITY))
                                        .executes(ctx -> ReconDebug.events(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "count")))))
                        .then(Commands.literal("weather")
                                .executes(ctx -> ReconDebug.weather(ctx.getSource()))
                                .then(Commands.literal("demo")
                                        .executes(ctx -> ReconDebug.weatherDemo(ctx.getSource()))))
                        .then(Commands.literal("demo")
                                .executes(ctx -> ReconDebug.demo(ctx.getSource())))
                        .then(Commands.literal("bind")
                                .executes(ctx -> ReconDebug.bindings(ctx.getSource(),
                                        ReconDebug.BIND_RADIUS))
                                .then(Commands.literal("list")
                                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(
                                                        0.0, ReconDebug.BIND_MAX_RADIUS))
                                                .executes(ctx -> ReconDebug.bindings(ctx.getSource(),
                                                        DoubleArgumentType.getDouble(ctx, "radius")))))
                                .then(Commands.argument("net", StringArgumentType.string())
                                        .suggests(ReconDebug::suggestNets)
                                        .executes(ctx -> ReconDebug.bind(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "net"),
                                                ReconDebug.BIND_RADIUS))
                                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(
                                                        0.0, ReconDebug.BIND_MAX_RADIUS))
                                                .executes(ctx -> ReconDebug.bind(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "net"),
                                                        DoubleArgumentType.getDouble(ctx, "radius"))))))
                        .then(Commands.literal("grid")
                                .executes(ctx -> ReconDebug.grid(ctx.getSource()))
                                .then(Commands.literal("demo")
                                        .executes(ctx -> ReconDebug.gridDemo(ctx.getSource())))
                                .then(Commands.literal("remint")
                                        .executes(ctx -> ReconDebug.remint(ctx.getSource())))
                                .then(Commands.literal("adopt")
                                        .then(Commands.argument("uuid", StringArgumentType.string())
                                                .executes(ctx -> ReconDebug.adopt(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "uuid"))))))));
    }
}

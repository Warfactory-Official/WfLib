package com.wf.wflib.orbital.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /wflib orbital ...}. */
public final class OrbitalCommands {
    private OrbitalCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wflib")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("orbital")
                        .executes(ctx -> OrbitalDebug.status(ctx.getSource()))
                        .then(Commands.literal("demo")
                                .executes(ctx -> OrbitalDebug.demo(ctx.getSource())))
                        .then(Commands.literal("selftest")
                                .executes(ctx -> OrbitalDebug.selfTest(ctx.getSource())))
                        .then(Commands.literal("catalogue")
                                .executes(ctx -> OrbitalDebug.catalogue(ctx.getSource())))
                        .then(Commands.literal("survey")
                                .executes(ctx -> OrbitalDebug.survey(ctx.getSource())))
                        .then(Commands.literal("launch")
                                .then(Commands.argument("payload", StringArgumentType.word())
                                        .suggests(OrbitalDebug::suggestPayloads)
                                        .executes(ctx -> OrbitalDebug.launch(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "payload"), "leo"))
                                        .then(Commands.argument("orbit", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                        new String[]{"leo", "meo", "heo"}, b))
                                                .executes(ctx -> OrbitalDebug.launch(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "payload"),
                                                        StringArgumentType.getString(ctx, "orbit"))))))
                        .then(Commands.literal("cmd")
                                .then(Commands.argument("callsign", StringArgumentType.word())
                                        .suggests(OrbitalDebug::suggestCallsigns)
                                        .then(Commands.argument("verb", StringArgumentType.greedyString())
                                                .executes(ctx -> OrbitalDebug.command(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "callsign"),
                                                        StringArgumentType.getString(ctx, "verb"))))))
                        .then(Commands.literal("verbs")
                                .then(Commands.argument("callsign", StringArgumentType.word())
                                        .suggests(OrbitalDebug::suggestCallsigns)
                                        .executes(ctx -> OrbitalDebug.verbs(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "callsign")))))
                        .then(Commands.literal("at")
                                .then(Commands.argument("callsign", StringArgumentType.word())
                                        .suggests(OrbitalDebug::suggestCallsigns)
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(
                                                        -1200000, 1200000))
                                                .executes(ctx -> OrbitalDebug.at(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "callsign"),
                                                        IntegerArgumentType.getInteger(ctx, "ticks"))))))
                        .then(Commands.literal("deorbit")
                                .then(Commands.argument("callsign", StringArgumentType.word())
                                        .suggests(OrbitalDebug::suggestCallsigns)
                                        .executes(ctx -> OrbitalDebug.deorbit(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "callsign")))))));
    }

}

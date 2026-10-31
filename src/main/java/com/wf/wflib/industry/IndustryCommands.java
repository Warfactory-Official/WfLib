package com.wf.wflib.industry;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /wflib industry ...}. */
public final class IndustryCommands {
    private IndustryCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wflib")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("industry")
                        .executes(ctx -> IndustryDebug.status(ctx.getSource()))
                        .then(Commands.literal("bases")
                                .executes(ctx -> IndustryDebug.bases(ctx.getSource())))
                        .then(Commands.literal("cells")
                                .executes(ctx -> IndustryDebug.cells(ctx.getSource())))
                        .then(Commands.literal("here")
                                .executes(ctx -> IndustryDebug.here(ctx.getSource())))
                        .then(Commands.literal("rescan")
                                .executes(ctx -> IndustryDebug.rescan(ctx.getSource(), 8))
                                .then(Commands.argument("radiusChunks", IntegerArgumentType.integer(0, 32))
                                        .executes(ctx -> IndustryDebug.rescan(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "radiusChunks")))))));
    }

}

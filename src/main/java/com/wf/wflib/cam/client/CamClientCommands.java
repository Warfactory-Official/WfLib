package com.wf.wflib.cam.client;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** {@code /wfview}, {@code /wffeed cull skip|evict}. */
public final class CamClientCommands {
    private CamClientCommands() {
    }

    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(OffscreenViewDebug.command());
        event.getDispatcher().register(Commands.literal("wffeed").then(Commands.literal("cull")
                .then(Commands.literal("skip").executes(ctx -> probe(ctx.getSource(), FeedProbe.Mode.SKIP)))
                .then(Commands.literal("evict").executes(ctx -> probe(ctx.getSource(), FeedProbe.Mode.EVICT)))));
    }

    private static int probe(CommandSourceStack source, FeedProbe.Mode mode) {
        FeedProbe.arm(mode);
        source.sendSuccess(() -> Component.literal("feed probe armed: " + mode), false);
        return 1;
    }
}

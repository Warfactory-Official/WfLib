package com.wf.wflib.stream.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Locale;

/** {@code /wfstream audit|map}. */
public final class StreamClientCommands {
    private StreamClientCommands() {
    }

    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfstream")
                .then(Commands.literal("audit").executes(ctx -> {
                    int[] counts = ClientChunkStreamAudit.run(Minecraft.getInstance().level, "wflib-stream-audit-client.txt");
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                            "held=%d offView=%d undrawn=%d", counts[0], counts[1], counts[2])), false);
                    return 1;
                }))
                .then(Commands.literal("map")
                        .executes(ctx -> map(ctx.getSource(), 12))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                                .executes(ctx -> map(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))))));
    }

    private static int map(CommandSourceStack source, int radius) {
        if (Minecraft.getInstance().level == null) {
            return 0;
        }
        String summary = ClientStreamDebug.map(radius);
        source.sendSuccess(() -> Component.literal("chunk map -> logs (" + summary + ")"), false);
        return 1;
    }
}

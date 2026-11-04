package com.wf.wflib.labs;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.wf.wflib.labs.api.LabProducer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.Path;

/** {@code /wflabs run <id|all> [k=v ...]}, {@code list}, {@code record start|stop}. */
final class LabsCommands {
    private LabsCommands() {
    }

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wflabs")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> {
                    com.wf.wflib.labs.api.LabProducers.REGISTRY.values().forEach(p -> ctx.getSource()
                            .sendSuccess(() -> Component.literal(p.id() + " -> " + p.table().name()), false));
                    return 1;
                }))
                .then(Commands.literal("run")
                        .then(Commands.argument("what", StringArgumentType.word())
                                .executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "what"), ""))
                                .then(Commands.argument("grid", StringArgumentType.greedyString())
                                        .executes(ctx -> run(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "what"),
                                                StringArgumentType.getString(ctx, "grid"))))))
                .then(Commands.literal("record")
                        .then(Commands.literal("start").executes(ctx -> {
                            Path dir = LabRecording.start(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Recording to " + dir), true);
                            return 1;
                        }))
                        .then(Commands.literal("stop").executes(ctx -> {
                            Path dir = LabRecording.stop();
                            ctx.getSource().sendSuccess(() -> Component.literal("Recorded " + dir), true);
                            return 1;
                        }))));
    }

    private static int run(CommandSourceStack src, String what, String grid) {
        try {
            Path out = LabRun.execute(src.getServer(), what, LabRun.parse(grid));
            src.sendSuccess(() -> Component.literal("Lab output " + out), true);
            return 1;
        } catch (RuntimeException e) {
            src.sendFailure(Component.literal(String.valueOf(e.getMessage())));
            return 0;
        }
    }
}

package com.wf.wflib.mist.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.wf.wflib.mist.api.WFFluids;
import com.wf.wflib.mist.api.GasCloud;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /wflib gas}. */
public final class MistDebug {
    private MistDebug() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wflib")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("gas")
                        .executes(ctx -> spawnGas(ctx.getSource(), GasCloud.DEFAULT_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                                .executes(ctx -> spawnGas(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius"))))));
    }

    /** The {@link GasCloud#spawn} call the chemical warhead makes, without flying a missile at something. */
    private static int spawnGas(CommandSourceStack src, int radius) {
        Vec3 pos = src.getPosition();
        int cells = GasCloud.spawn(src.getLevel(), WFFluids.MUSTARD_GAS.get(), pos, radius,
                GasCloud.DEFAULT_MAX_CELLS, GasCloud.DEFAULT_DURATION);
        src.sendSuccess(() -> Component.literal("Released mustard gas at " + BlockPos.containing(pos)
                + ": " + cells + " cells"), true);
        return cells;
    }
}

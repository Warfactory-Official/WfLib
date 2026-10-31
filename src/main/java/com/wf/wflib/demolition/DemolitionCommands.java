package com.wf.wflib.demolition;

import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /wflib demolition ...}. */
public final class DemolitionCommands {
    private DemolitionCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wflib")
                .requires(src -> src.hasPermission(2))
                .then(demolitionCommand()));
    }
    /** {@code /wflib demolition ...}: the mining-charge side of the mod. */
    private static LiteralArgumentBuilder<CommandSourceStack> demolitionCommand() {
        return Commands.literal("demolition")
                .then(Commands.literal("detonate")
                        .then(Commands.argument("at", BlockPosArgument.blockPos())
                                .executes(ctx -> detonateCharge(ctx.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(ctx, "at")))))
                .then(Commands.literal("fire")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> fireOnCommand(ctx.getSource(),
                                        EntityArgument.getEntities(ctx, "targets")))));
    }

    /**
     * Fires detonatable entities (mines), without a detonator, the way {@code detonate} fires a charge without one.
     */
    private static int fireOnCommand(CommandSourceStack source,
                                     java.util.Collection<? extends Entity> targets) {
        ServerLevel level = source.getLevel();
        int fired = 0;
        for (Entity entity : targets) {
            if (entity instanceof com.wf.wflib.demolition.api.IDetonatableEntity detonatable
                    && detonatable.detonateOnCommand(level, null)) {
                fired++;
            }
        }
        int count = fired;
        if (count == 0) {
            source.sendFailure(Component.literal("Nothing detonatable in that selection."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Fired " + count + " on command"), true);
        return count;
    }

    /** Fire the mining charge at a position without a detonator. */
    private static int detonateCharge(CommandSourceStack source, BlockPos at) {
        ServerLevel level = source.getLevel();
        if (!com.wf.wflib.demolition.api.IDetonatable.isExplosive(level.getBlockState(at))) {
            source.sendFailure(Component.literal("No detonatable charge at "
                    + at.getX() + ", " + at.getY() + ", " + at.getZ()));
            return 0;
        }
        com.wf.wflib.demolition.api.IDetonatable.tryDetonate(level, at, null);
        source.sendSuccess(() -> Component.literal("Detonated charge at "
                + at.getX() + ", " + at.getY() + ", " + at.getZ()), true);
        return 1;
    }

}

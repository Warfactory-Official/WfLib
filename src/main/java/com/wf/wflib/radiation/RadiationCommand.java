package com.wf.wflib.radiation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.wf.wflib.hazard.ContaminationUtil;
import com.wf.wflib.hazard.LivingHazardProps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.Collection;

/** NTM {@code /hbmrad} as {@code /wfrad}; {@code get} and {@code geiger} added for checks. */
public final class RadiationCommand {

    private RadiationCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wfrad").requires(s -> s.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("set").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0D))
                                .executes(RadiationCommand::setChunk))))
                .then(Commands.literal("get").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(RadiationCommand::getChunk)))
                .then(Commands.literal("geiger").executes(RadiationCommand::geiger))
                .then(Commands.literal("clearall").executes(RadiationCommand::clearAll))
                .then(Commands.literal("resetplayers").executes(RadiationCommand::resetPlayers))
                .then(Commands.literal("player").then(Commands.argument("targets", EntityArgument.players())
                        .executes(RadiationCommand::queryPlayers)
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0D))
                                .executes(RadiationCommand::setPlayers)))));
    }

    private static int setChunk(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        double value = DoubleArgumentType.getDouble(ctx, "value");
        RadiationSystemNT.setRadForCoord(ctx.getSource().getLevel(), pos, value);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.wflib.wfrad.set", pos.getX(), pos.getY(),
                pos.getZ(), value), true);
        return 1;
    }

    private static int getChunk(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        double value = RadiationSystemNT.getRadForCoord(ctx.getSource().getLevel(), pos);
        ctx.getSource().sendSuccess(() -> Component.literal(pos.toShortString() + ": " + value + " RAD"), false);
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    private static int geiger(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ContaminationUtil.printGeigerData(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int clearAll(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        String dim = level.dimension().location().toString();
        Fallout.clear(level);
        if (!RadiationSystemNT.jettisonData(level)) {
            ctx.getSource().sendFailure(Component.translatable("commands.wflib.wfrad.clearall.failed", dim));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.wflib.wfrad.clearall", dim), true);
        return 1;
    }

    private static int resetPlayers(CommandContext<CommandSourceStack> ctx) {
        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            LivingHazardProps.getData(p).radiation = 0D;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.wflib.wfrad.resetplayers"), true);
        return 1;
    }

    private static int queryPlayers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer p : targets) {
            ctx.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + ": "
                    + LivingHazardProps.getData(p).radiation + " RAD"), false);
        }
        return targets.size();
    }

    private static int setPlayers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        double value = Mth.clamp(DoubleArgumentType.getDouble(ctx, "value"), 0D, LivingHazardProps.RAD_CAP);
        for (ServerPlayer p : targets) LivingHazardProps.getData(p).radiation = value;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.wflib.wfrad.player", targets.size(), value),
                true);
        return targets.size();
    }
}

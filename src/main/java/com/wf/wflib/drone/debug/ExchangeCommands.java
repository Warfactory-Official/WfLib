package com.wf.wflib.drone.debug;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wf.wflib.drone.api.DroneMission;
import com.wf.wflib.drone.api.Exchange;
import com.wf.wflib.drone.api.ExchangeMode;
import com.wf.wflib.drone.api.StationCode;
import com.wf.wflib.drone.api.StationKind;
import com.wf.wflib.drone.api.StationRecord;
import com.wf.wflib.drone.api.StationRole;
import com.wf.wflib.drone.api.DronePadBlockEntity;
import com.wf.wflib.drone.exchange.ExchangeData;
import com.wf.wflib.drone.exchange.ExchangeManager;
import com.wf.wflib.drone.api.StationData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** {@code /wflib station ...}, {@code /wflib exchange ...}. */
public final class ExchangeCommands {
    private ExchangeCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wflib")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("station")
                        .then(stationRoleCommand())
                        .then(Commands.literal("code")
                                .executes(ctx -> stationCode(ctx.getSource())))
                        .then(Commands.literal("allow")
                                .then(Commands.argument("code", StringArgumentType.word())
                                        .executes(ctx -> stationAllow(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "code"), true))))
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("code", StringArgumentType.word())
                                        .executes(ctx -> stationAllow(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "code"), false))))
                        .then(Commands.literal("send")
                                .then(Commands.argument("code", StringArgumentType.word())
                                        .executes(ctx -> stationSend(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "code")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> stationList(ctx.getSource()))))
                .then(Commands.literal("exchange")
                        .then(Commands.literal("list")
                                .executes(ctx -> exchangeList(ctx.getSource())))
                        .then(Commands.literal("purge")
                                .executes(ctx -> exchangePurge(ctx.getSource())))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("id", UuidArgument.uuid())
                                        .executes(ctx -> exchangeCancel(ctx.getSource(),
                                                UuidArgument.getUuid(ctx, "id")))))));
    }

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> ROLES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(StationRole.values()).map(StationRole::id).toList(), builder);
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> KINDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(StationKind.values()).map(StationKind::id).toList(), builder);

    @Nullable
    private static DronePadBlockEntity nearestPad(CommandSourceStack src) {
        return DronePadBlockEntity.nearest(src.getLevel(), src.getPosition());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> stationRoleCommand() {
        return Commands.literal("role")
                .executes(ctx -> stationRoles(ctx.getSource()))
                .then(Commands.literal("set")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests(KINDS)
                                .executes(ctx -> stationKind(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "kind")))))
                .then(Commands.literal("on")
                        .then(Commands.argument("role", StringArgumentType.word())
                                .suggests(ROLES)
                                .executes(ctx -> stationRole(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "role"), true))))
                .then(Commands.literal("off")
                        .then(Commands.argument("role", StringArgumentType.word())
                                .suggests(ROLES)
                                .executes(ctx -> stationRole(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "role"), false))));
    }

    private static int stationRoles(CommandSourceStack src) {
        StationRecord record = ownStation(src);
        if (record == null) {
            return 0;
        }
        src.sendSuccess(() -> Component.literal("This station is a " + record.kind() + ".")
                .withStyle(ChatFormatting.AQUA), false);
        for (StationRole role : StationRole.values()) {
            boolean on = record.has(role);
            src.sendSuccess(() -> Component.literal(String.format("  %s %-10s %s",
                            on ? "✔" : "✘", role.id(), role.description()))
                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY), false);
        }
        return 1;
    }

    private static int stationKind(CommandSourceStack src, String id) {
        StationRecord record = ownStation(src);
        if (record == null) {
            return 0;
        }
        StationKind kind = StationKind.byId(id);
        if (kind == null) {
            src.sendFailure(Component.literal("No such kind. Try: "
                    + String.join(", ", java.util.Arrays.stream(StationKind.values())
                    .map(StationKind::id).toList())));
            return 0;
        }
        StationData.get(src.getLevel()).setRoles(record.code(), kind.roles());
        src.sendSuccess(() -> Component.literal("This station is now a " + kind.id() + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int stationRole(CommandSourceStack src, String id, boolean on) {
        StationRecord record = ownStation(src);
        if (record == null) {
            return 0;
        }
        StationRole role = StationRole.byId(id);
        if (role == null) {
            src.sendFailure(Component.literal("No such role. Try: "
                    + String.join(", ", java.util.Arrays.stream(StationRole.values())
                    .map(StationRole::id).toList())));
            return 0;
        }
        boolean changed = StationData.get(src.getLevel()).setRole(record.code(), role, on);
        src.sendSuccess(() -> Component.literal(changed
                ? role.id() + (on ? " on. Now a " : " off. Now a ") + record.kind() + "."
                : "No change."), false);
        return changed ? 1 : 0;
    }

    /**
     * @return the station record for the pad the caller is standing at, complaining to them if there is not
     *      one. Every station command is about <em>your</em> station, which is the only one whose position you are
     *      allowed to know.
     */
    @Nullable
    private static StationRecord ownStation(CommandSourceStack src) {
        DronePadBlockEntity pad = nearestPad(src);
        if (pad == null) {
            src.sendFailure(Component.literal("Stand near a drone pad."));
            return null;
        }
        StationRecord record = StationData.get(src.getLevel()).byCode(pad.stationCode(src.getLevel()));
        if (record == null) {
            src.sendFailure(Component.literal("That pad is not registered."));
        }
        return record;
    }

    private static int stationCode(CommandSourceStack src) {
        DronePadBlockEntity pad = nearestPad(src);
        if (pad == null) {
            src.sendFailure(Component.literal("Stand near a drone pad."));
            return 0;
        }
        String code = pad.stationCode(src.getLevel());
        StationRecord record = StationData.get(src.getLevel()).byCode(code);
        int allowed = record == null ? 0 : record.allowed().size();
        src.sendSuccess(() -> Component.literal("Station code: " + StationCode.pretty(code))
                .withStyle(ChatFormatting.AQUA), false);
        src.sendSuccess(() -> Component.literal("Accepting handshakes from " + allowed + " station(s). "
                + "Give this code to anyone you want to trade with; it reveals nothing about where you are.")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int stationAllow(CommandSourceStack src, String rawCode, boolean allow) {
        DronePadBlockEntity pad = nearestPad(src);
        if (pad == null) {
            src.sendFailure(Component.literal("Stand near a drone pad."));
            return 0;
        }
        String peer = StationCode.normalise(rawCode);
        if (!StationCode.valid(peer)) {
            src.sendFailure(Component.literal("That is not a valid station code."));
            return 0;
        }
        String own = pad.stationCode(src.getLevel());
        StationData stations = StationData.get(src.getLevel());
        boolean changed = allow ? stations.allow(own, peer) : stations.revoke(own, peer);
        src.sendSuccess(() -> Component.literal(changed
                ? (allow ? "Now accepting from " + StationCode.pretty(peer)
                         : "No longer accepting from " + StationCode.pretty(peer))
                : "No change."), false);
        return changed ? 1 : 0;
    }

    /** Send the nearest pad's cargo to another station by handshake. */
    private static int stationSend(CommandSourceStack src, String rawCode) {
        DronePadBlockEntity pad = nearestPad(src);
        if (pad == null) {
            src.sendFailure(Component.literal("Stand near a drone pad."));
            return 0;
        }
        String peer = StationCode.normalise(rawCode);
        if (!StationCode.valid(peer)) {
            src.sendFailure(Component.literal("That is not a valid station code."));
            return 0;
        }
        DroneMission mission = pad.mission();
        mission.mode = ExchangeMode.HANDSHAKE;
        mission.recipientCode = peer;
        pad.setMission(mission);
        String error = pad.dispatch();
        if (error != null) {
            // Whatever the pad said, and it is written not to say anything revealing.
            src.sendFailure(Component.literal("Refused: " + error));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Handshake away. The meeting point is not reported.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int stationList(CommandSourceStack src) {
        List<StationRecord> stations = StationData.get(src.getLevel()).all();
        if (stations.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No stations registered."), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal(stations.size() + " station(s):")
                .withStyle(ChatFormatting.GOLD), false);
        for (StationRecord record : stations) {
            String line = String.format("• %s  %s  %s %d, %d, %d  allows %d",
                    StationCode.pretty(record.code()), record.kind(),
                    record.dimension().location().getPath(),
                    record.pos().getX(), record.pos().getY(), record.pos().getZ(), record.allowed().size());
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return stations.size();
    }

    private static int exchangeList(CommandSourceStack src) {
        List<Exchange> exchanges = ExchangeData.get(src.getLevel()).all();
        if (exchanges.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No exchanges."), false);
            return 0;
        }
        for (Exchange exchange : exchanges) {
            String line = String.format("• %s  %s  %s <-> %s  at %d, %d  %s",
                    exchange.id().toString().substring(0, 8), exchange.phase(),
                    StationCode.pretty(exchange.sender().code()),
                    StationCode.pretty(exchange.recipient().code()),
                    (int) exchange.rendezvous().x, (int) exchange.rendezvous().z, exchange.note());
            src.sendSuccess(() -> Component.literal(line).withStyle(
                    exchange.active() ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY), false);
        }
        return exchanges.size();
    }

    /**
     * Cancel every live exchange. The testing counterpart of {@code drone clear}.
     */
    private static int exchangePurge(CommandSourceStack src) {
        List<Exchange> active = ExchangeData.get(src.getLevel()).active();
        for (Exchange exchange : active) {
            ExchangeManager.cancel(src.getServer(), exchange, "purged by an operator");
        }
        src.sendSuccess(() -> Component.literal("Cancelled " + active.size() + " exchange(s)."), true);
        return active.size();
    }

    private static int exchangeCancel(CommandSourceStack src, UUID id) {
        Exchange exchange = ExchangeData.get(src.getLevel()).byId(id);
        if (exchange == null) {
            src.sendFailure(Component.literal("No such exchange."));
            return 0;
        }
        ExchangeManager.cancel(src.getServer(), exchange, "cancelled by an operator");
        src.sendSuccess(() -> Component.literal("Exchange cancelled."), true);
        return 1;
    }
}

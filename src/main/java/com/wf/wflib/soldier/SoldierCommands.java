package com.wf.wflib.soldier;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.wf.wflib.soldier.api.SoldierServices;
import com.wf.wflib.soldier.api.SoldierSpec;
import com.wf.wflib.soldier.api.Soldiers;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** {@code /soldiers spawn|clear|profile|check|routebench|async}: bench and debug tools (permission level 2). */
final class SoldierCommands {
    private SoldierCommands() {
    }

    static void onRegisterCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("soldiers").requires(s -> s.hasPermission(2) || SoldierEditor.allowed(s))
                .then(Commands.literal("spawn").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("side", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(SoldierServices.sides(), b))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, Soldiers.CAP))
                                        .executes(c -> spawn(c.getSource(), StringArgumentType.getString(c, "side"),
                                                IntegerArgumentType.getInteger(c, "count"), 12))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(2, 64))
                                                .executes(c -> spawn(c.getSource(), StringArgumentType.getString(c, "side"),
                                                        IntegerArgumentType.getInteger(c, "count"),
                                                        IntegerArgumentType.getInteger(c, "radius")))))))
                .then(VoiceCommands.node().requires(s -> s.hasPermission(2)))
                .then(Commands.literal("clear").requires(s -> s.hasPermission(2)).executes(c -> {
                    Soldiers.removeAll(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("soldiers removed"), false);
                    return 1;
                }))
                .then(Commands.literal("profile").requires(s -> s.hasPermission(2))
                        .executes(c -> profile(c.getSource(), false))
                        .then(Commands.literal("reset").executes(c -> profile(c.getSource(), true))))
                .then(Commands.literal("check").requires(s -> s.hasPermission(2)).executes(c -> check(c.getSource().getLevel(), c.getSource().getPosition(), 12, c.getSource()))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(4, 40))
                                .executes(c -> check(c.getSource().getLevel(), c.getSource().getPosition(),
                                        IntegerArgumentType.getInteger(c, "radius"), c.getSource()))))
                .then(Commands.literal("light").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("auto").executes(c -> light(c.getSource(), "auto")))
                        .then(Commands.literal("on").executes(c -> light(c.getSource(), "on")))
                        .then(Commands.literal("off").executes(c -> light(c.getSource(), "off"))))
                .then(Commands.literal("routebench").requires(s -> s.hasPermission(2)).executes(c -> routeBench(c.getSource())))
                .then(Commands.literal("async").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("on").executes(c -> {
                            SoldierJobs.enabled(true);
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(c -> {
                            SoldierJobs.enabled(false);
                            return 1;
                        })));
        SoldierEditorCommands.add(root);
        event.getDispatcher().register(root);
    }

    private static int light(net.minecraft.commands.CommandSourceStack source, String mode) {
        int n = SoldierAi.lightAll(mode);
        source.sendSuccess(() -> Component.literal("weapon lights " + mode + " for " + n + " soldiers"
                + ("auto".equals(mode) ? " (on in the dark: night, storms, caves)" : "")), false);
        return n;
    }

    private static int spawn(net.minecraft.commands.CommandSourceStack source, String side, int count, int radius) {
        SoldierSpec spec = SoldierServices.spec(side);
        if (spec == null) {
            source.sendFailure(Component.literal("sides: " + SoldierServices.sides() + " (and a usable gun must be installed)"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        Vec3 centre = source.getPosition();
        ServerPlayer owner = source.getEntity() instanceof ServerPlayer p ? p : null;
        Random rng = new Random();
        int made = 0;
        for (int i = 0; i < count * 6 && made < count; i++) {
            double x = centre.x + (rng.nextDouble() * 2 - 1) * radius, z = centre.z + (rng.nextDouble() * 2 - 1) * radius;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            if (Math.abs(y - centre.y) > 6) continue;
            if (Soldiers.spawn(level, new Vec3(x, y, z), rng.nextFloat() * 360, spec, owner) != null) made++;
        }
        int total = made;
        source.sendSuccess(() -> Component.literal("spawned " + total + " " + side), false);
        return made;
    }

    private static int profile(net.minecraft.commands.CommandSourceStack source, boolean reset) {
        var report = SoldierProfiler.report(reset);
        report.addProperty("server_tick_ms_avg", Math.round(source.getServer().getAverageTickTimeNanos() / 1e4) / 100.0);
        source.sendSuccess(() -> Component.literal(report.toString()), false);
        return 1;
    }

    /** Compares the live world with its block copy on random cells and sight lines around the caller. */
    private static int check(ServerLevel level, Vec3 centre, int radius, net.minecraft.commands.CommandSourceStack source) {
        BlockPos min = BlockPos.containing(centre).offset(-radius - 2, -8, -radius - 2);
        BlockPos max = BlockPos.containing(centre).offset(radius + 2, 8, radius + 2);
        boolean complete = false;
        for (int i = 0; i < 40 && !(complete = SoldierSections.ensure(level, min, max)); i++) {
            // keep building: 12 sections a call
        }
        if (!complete) {
            source.sendFailure(Component.literal("chunks around you are not loaded"));
            return 0;
        }
        var dimension = level.dimension();
        SnapshotTerrain copy = new SnapshotTerrain((x, y, z) -> SoldierSections.cell(dimension, x, y, z));
        LevelTerrain live = new LevelTerrain(level, source.getEntity());
        Random rng = new Random(7);
        int cells = 0, walkDiffs = 0, rays = 0, rayDiffs = 0, blockedByCopyOnly = 0;
        List<BlockPos> sample = new ArrayList<>();
        for (int i = 0; i < 4000; i++) {
            BlockPos p = BlockPos.containing(centre).offset(rng.nextInt(2 * radius + 1) - radius, rng.nextInt(9) - 4, rng.nextInt(2 * radius + 1) - radius);
            cells++;
            boolean a = live.walkable(p), b = copy.walkable(p);
            if (a != b) walkDiffs++;
            if (a && sample.size() < 400) sample.add(p);
        }
        for (int i = 0; i + 1 < sample.size(); i += 2) {
            Vec3 from = Vec3.atBottomCenterOf(sample.get(i)).add(0, 1.6, 0), to = Vec3.atBottomCenterOf(sample.get(i + 1)).add(0, 1.0, 0);
            boolean seesLive = BotTactics.clearRay(level, from, to, source.getEntity()), seesCopy = copy.sees(from, to);
            rays++;
            if (seesLive != seesCopy) {
                rayDiffs++;
                if (!seesCopy) blockedByCopyOnly++;
            }
        }
        int w = walkDiffs, rc = rayDiffs, only = blockedByCopyOnly, c = cells, r = rays;
        source.sendSuccess(() -> Component.literal(String.format("walkable: %d/%d cells differ (%.2f%%); sight: %d/%d lines differ (%.1f%%, %d blocked only in the copy)",
                w, c, 100.0 * w / c, rc, r, r == 0 ? 0 : 100.0 * rc / r, only)), false);
        return 1;
    }

    /** Random routes within 30 blocks of the caller: the live search, the same search over the copy, and what handing it to a worker costs the tick thread. */
    private static int routeBench(net.minecraft.commands.CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 centre = source.getPosition();
        BlockPos base = BlockPos.containing(centre);
        SoldierSections.ensure(level, base.offset(-45, -8, -45), base.offset(45, 8, 45));
        for (int i = 0; i < 30 && !SoldierSections.ensure(level, base.offset(-45, -8, -45), base.offset(45, 8, 45)); i++) {
            // build the rest
        }
        var dimension = level.dimension();
        SnapshotTerrain copy = new SnapshotTerrain((x, y, z) -> SoldierSections.cell(dimension, x, y, z));
        LevelTerrain live = new LevelTerrain(level, source.getEntity());
        Random rng = new Random(11);
        List<BlockPos[]> pairs = new ArrayList<>();
        for (int guard = 0; guard < 4000 && pairs.size() < 40; guard++) {
            BlockPos a = base.offset(rng.nextInt(61) - 30, rng.nextInt(3) - 1, rng.nextInt(61) - 30);
            BlockPos b = base.offset(rng.nextInt(61) - 30, rng.nextInt(3) - 1, rng.nextInt(61) - 30);
            if (live.walkable(a) && live.walkable(b) && a.distSqr(b) > 100) pairs.add(new BlockPos[]{a, b});
        }
        if (pairs.isEmpty()) {
            source.sendFailure(Component.literal("no walkable pairs near you"));
            return 0;
        }
        List<Vec3> eyes = List.of(centre.add(0, 1.6, 0));
        long liveNs = 0, copyNs = 0, submitNs = 0;
        int found = 0, foundCopy = 0;
        for (BlockPos[] pair : pairs) {
            long t = System.nanoTime();
            var r1 = BotTactics.routeAvoiding(live, pair[0], pair[1], eyes, List.of());
            liveNs += System.nanoTime() - t;
            t = System.nanoTime();
            var r2 = BotTactics.routeAvoiding(copy, pair[0], pair[1], eyes, List.of());
            copyNs += System.nanoTime() - t;
            t = System.nanoTime();
            var job = SoldierJobs.route(level, pair[0], pair[1], eyes, List.of());
            submitNs += System.nanoTime() - t;
            if (job != null) job.cancel(true);
            if (!r1.isEmpty()) found++;
            if (!r2.isEmpty()) foundCopy++;
        }
        int n = pairs.size(), f1 = found, f2 = foundCopy;
        double liveMs = liveNs / 1e6 / n, copyMs = copyNs / 1e6 / n, submitMs = submitNs / 1e6 / n;
        source.sendSuccess(() -> Component.literal(String.format("%d routes: live %.2f ms each (%d found), copy %.2f ms each (%d found), handing one to a worker costs the tick %.3f ms",
                n, liveMs, f1, copyMs, f2, submitMs)), false);
        return 1;
    }
}

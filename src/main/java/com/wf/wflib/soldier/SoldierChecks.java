package com.wf.wflib.soldier;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** {@code /soldiers check [radius]}, {@code /soldiers async <on|off>}: debugging for the worker-side search. */
final class SoldierChecks {
    private SoldierChecks() {
    }

    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("soldiers").requires(s -> s.hasPermission(2))
                .then(Commands.literal("check").executes(c -> check(c.getSource().getLevel(), c.getSource().getPosition(), 12, c.getSource()))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(4, 40))
                                .executes(c -> check(c.getSource().getLevel(), c.getSource().getPosition(),
                                        IntegerArgumentType.getInteger(c, "radius"), c.getSource()))))
                .then(Commands.literal("routebench").executes(c -> routeBench(c.getSource())))
                .then(Commands.literal("async")
                        .then(Commands.literal("on").executes(c -> {
                            SoldierJobs.enabled(true);
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(c -> {
                            SoldierJobs.enabled(false);
                            return 1;
                        }))));
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

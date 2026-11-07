package com.wf.wflib.soldier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Route searches off the server thread. The tick thread makes sure the block copies the search will read exist
 * ({@link SoldierSections#ensure}), captures what the search needs, and hands it over; the soldier keeps its old route
 * until the answer is back, usually the next tick.
 */
final class SoldierJobs {
    private static final AtomicInteger NUMBER = new AtomicInteger();
    private static volatile ExecutorService pool;
    private static volatile boolean enabled = true;
    /** Blocks around the start/goal box a search may wander (the A* gives up after 5000 nodes anyway). */
    private static final int MARGIN = 12, VERTICAL = 10;

    private SoldierJobs() {
    }

    static void enabled(boolean on) {
        enabled = on;
    }

    static boolean enabled() {
        return enabled;
    }

    private static ExecutorService pool() {
        ExecutorService p = pool;
        if (p == null) {
            synchronized (SoldierJobs.class) {
                if (pool == null) {
                    int threads = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 2));
                    pool = Executors.newFixedThreadPool(threads, r -> {
                        Thread t = new Thread(r, "wflib-soldier-" + NUMBER.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    });
                }
                p = pool;
            }
        }
        return p;
    }

    static void shutdown() {
        ExecutorService p = pool;
        pool = null;
        if (p != null) p.shutdownNow();
        SoldierSections.clear();
    }

    /**
     * Starts a route search on a worker over a block copy; null when the copy is not ready yet (chunk not loaded, too many
     * sections to build this tick), in which case the caller asks again later. Tick thread only.
     */
    static Future<List<Vec3>> route(ServerLevel level, BlockPos start, BlockPos goal, List<Vec3> threats, List<BlockPos> occupied) {
        BlockPos min = new BlockPos(Math.min(start.getX(), goal.getX()) - MARGIN, Math.min(start.getY(), goal.getY()) - VERTICAL,
                Math.min(start.getZ(), goal.getZ()) - MARGIN);
        BlockPos max = new BlockPos(Math.max(start.getX(), goal.getX()) + MARGIN, Math.max(start.getY(), goal.getY()) + VERTICAL,
                Math.max(start.getZ(), goal.getZ()) + MARGIN);
        if (!SoldierSections.ensure(level, min, max)) return null;
        var dimension = level.dimension();
        SnapshotTerrain terrain = new SnapshotTerrain((x, y, z) -> SoldierSections.cell(dimension, x, y, z));
        List<Vec3> eyes = List.copyOf(threats);
        List<BlockPos> taken = List.copyOf(occupied);
        SoldierProfiler.routes++;
        SoldierProfiler.asyncJobs.increment();
        Callable<List<Vec3>> job = () -> BotTactics.routeAvoiding(terrain, start, goal, eyes, taken);
        return pool().submit(job);
    }
}

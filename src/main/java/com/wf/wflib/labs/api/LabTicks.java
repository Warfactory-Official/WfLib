package com.wf.wflib.labs.api;

import com.wf.wflib.core.api.WorkerPool;
import com.wf.wflib.labs.LabsSystem;
import com.wf.wflib.testkit.api.ForcedChunks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.EventHooks;

import java.util.Collection;
import java.util.concurrent.ForkJoinPool;

/**
 * Real server ticks for producers that run game entities over time ({@link LabProducer#ticksWorld()}). Headless runs
 * only ({@code -Dwflabs}, {@code -Dwflabs.serve}): the lab then owns the server thread between ticks. Server thread.
 */
public final class LabTicks {
    private static final long HOLD_TIMEOUT_NANOS = 300_000_000_000L;

    private LabTicks() {
    }

    /** True while a headless lab owns the server thread outside any tick. */
    public static boolean available() {
        return LabsSystem.betweenTicks();
    }

    /**
     * One server tick as {@code MinecraftServer.tickServer} runs it (tick count, events, tick rate, every level), then
     * the queued server and chunk tasks the main loop runs between ticks. No autosave, no gametest start. Tick count
     * gates queued tasks (entity section loads among them): never tick a level without it. {@link WorkerPool} jobs
     * started this tick finish before the next (recon passes adopt on a fixed tick => deterministic rows).
     */
    public static void tick(MinecraftServer server) {
        if (!available()) {
            throw new IllegalStateException("lab world ticks need a headless run (-Dwflabs / -Dwflabs.serve)");
        }
        server.tickCount++;
        EventHooks.fireServerTickPre(() -> true, server);
        server.tickRateManager().tick();
        server.tickChildren(() -> true);
        EventHooks.fireServerTickPost(() -> true, server);
        if (WorkerPool.pool() instanceof ForkJoinPool pool) {
            while (!pool.isQuiescent()) {
                Thread.onSpinWait();
            }
        }
        drain(server);
    }

    private static boolean drain(MinecraftServer server) {
        boolean any = false;
        while (server.pollTask()) {
            any = true;
        }
        for (ServerLevel level : server.getAllLevels()) {
            while (level.getChunkSource().pollTask()) {
                any = true;
            }
        }
        return any;
    }

    public static void tick(MinecraftServer server, int n) {
        for (int i = 0; i < n; i++) {
            tick(server);
        }
    }

    /** Ticks until {@code level} game time is a multiple of {@code period}: timers keyed on game time start in phase. */
    public static void align(ServerLevel level, int period) {
        while (level.getGameTime() % period != 0L) {
            tick(level.getServer());
        }
    }

    /** Forces {@code chunks} and ticks until every one is entity-ticking with its entity sections loaded (block entities tick only then). */
    public static void hold(ServerLevel level, Collection<ChunkPos> chunks) {
        ForcedChunks.set(level, chunks, true);
        for (ChunkPos c : chunks) {
            level.getChunk(c.x, c.z);
        }
        long t0 = System.nanoTime();
        for (int i = 0; ; i++) {
            boolean all = true;
            for (ChunkPos c : chunks) {
                if (!level.isPositionEntityTicking(c.getWorldPosition()) || !level.areEntitiesLoaded(c.toLong())) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return;
            }
            if (System.nanoTime() - t0 > HOLD_TIMEOUT_NANOS) {
                throw new IllegalStateException(chunks.size() + " chunks not entity-ticking after " + i + " ticks");
            }
            tick(level.getServer());
            if (!drain(level.getServer())) {
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
            }
        }
    }

    public static void release(ServerLevel level, Collection<ChunkPos> chunks) {
        ForcedChunks.set(level, chunks, false);
    }
}

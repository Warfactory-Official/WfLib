package com.wf.wflib.aef.nuke;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

public final class BombForkJoinPool {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<Level>, Set<IJobCancellable>> JOBS_BY_DIM = new HashMap<>();
    private static final Object LOCK = new Object();
    private static ForkJoinPool pool;
    private static int refs;

    private BombForkJoinPool() {
    }

    public static ForkJoinPool acquire() {
        synchronized (LOCK) {
            if (pool == null || pool.isShutdown()) {
                pool = new ForkJoinPool(computeWorkers(), ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                        (thread, error) -> LOGGER.error("Bomb ForkJoinPool worker crashed in {}", thread.getName(), error),
                        false);
            }
            refs++;
            return pool;
        }
    }

    public static void release(ForkJoinPool acquiredPool) {
        ForkJoinPool toShutdown;
        synchronized (LOCK) {
            if (acquiredPool != pool) return;
            if (refs <= 0) throw new IllegalStateException("BombForkJoinPool reference underflow");
            refs--;
            toShutdown = maybeShutdownLocked();
        }
        if (toShutdown != null) toShutdown.shutdown();
    }

    public static void register(ForkJoinPool acquiredPool, ResourceKey<Level> dimension, IJobCancellable job) {
        synchronized (LOCK) {
            if (acquiredPool != pool) return;
            JOBS_BY_DIM.computeIfAbsent(dimension, k -> Collections.newSetFromMap(new IdentityHashMap<>())).add(job);
        }
    }

    public static void unregister(ForkJoinPool acquiredPool, ResourceKey<Level> dimension, IJobCancellable job) {
        ForkJoinPool toShutdown = null;
        synchronized (LOCK) {
            if (acquiredPool != pool) return;
            Set<IJobCancellable> set = JOBS_BY_DIM.get(dimension);
            if (set == null) return;
            set.remove(job);
            if (set.isEmpty()) {
                JOBS_BY_DIM.remove(dimension);
                toShutdown = maybeShutdownLocked();
            }
        }
        if (toShutdown != null) toShutdown.shutdown();
    }

    public static void onLevelUnload(ResourceKey<Level> dimension) {
        List<IJobCancellable> jobs = null;
        synchronized (LOCK) {
            Set<IJobCancellable> set = JOBS_BY_DIM.remove(dimension);
            if (set != null && !set.isEmpty()) jobs = new ArrayList<>(set);
        }
        if (jobs != null) {
            for (IJobCancellable job : jobs) {
                try {
                    job.cancelJob();
                } catch (Throwable t) {
                    LOGGER.error("Failed to cancel bomb job on dimension unload {}", dimension.location(), t);
                }
            }
        }
        ForkJoinPool toShutdown;
        synchronized (LOCK) {
            toShutdown = maybeShutdownLocked();
        }
        if (toShutdown != null) toShutdown.shutdown();
    }

    public static void onServerStopped() {
        List<IJobCancellable> jobs = new ArrayList<>();
        ForkJoinPool toStop;
        synchronized (LOCK) {
            for (Set<IJobCancellable> set : JOBS_BY_DIM.values()) jobs.addAll(set);
            JOBS_BY_DIM.clear();
            refs = 0;
            toStop = pool;
            pool = null;
        }
        for (IJobCancellable job : jobs) {
            try {
                job.cancelJob();
            } catch (Throwable t) {
                LOGGER.error("Failed to cancel bomb job on server stop", t);
            }
        }
        if (toStop != null && !toStop.isShutdown()) toStop.shutdown();
    }

    private static ForkJoinPool maybeShutdownLocked() {
        if (pool == null || refs != 0 || !JOBS_BY_DIM.isEmpty()) return null;
        ForkJoinPool p = pool;
        pool = null;
        return p;
    }

    private static int computeWorkers() {
        int processors = Runtime.getRuntime().availableProcessors();
        int workers = NukeConfig.maxThreads <= 0
                ? processors + NukeConfig.maxThreads
                : Math.min(NukeConfig.maxThreads, processors);
        return Math.max(1, workers);
    }

    public interface IJobCancellable {
        void cancelJob();
    }
}

package com.wf.wflib.core.api;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared off-thread worker pool; lives exactly as long as the server so threads cannot leak across a world reload. */
public final class WorkerPool {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile ExecutorService pool;
    private static volatile int poolSize;

    private WorkerPool() {
    }

    /** Server starting: marks the calling thread as the {@link WorldThread}. */
    public static void startup() {
        shutdown();
        WorldThread.mark();
        int workers = Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors() - 2));
        poolSize = workers;
        pool = Executors.newWorkStealingPool(workers);
        LOGGER.debug("[wflib] worker pool started with {} workers", workers);
    }

    public static void shutdown() {
        ExecutorService p = pool;
        if (p != null) {
            p.shutdownNow();
            pool = null;
        }
        poolSize = 0;
        WorldThread.clear();
    }

    public static int poolSize() {
        return poolSize;
    }

    /** @return the pool, or null before startup. */
    public static ExecutorService pool() {
        return pool;
    }
}

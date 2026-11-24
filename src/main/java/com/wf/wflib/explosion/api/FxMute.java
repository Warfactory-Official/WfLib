package com.wf.wflib.explosion.api;

import java.util.concurrent.atomic.AtomicInteger;

/** Calling thread: wflib FX packets + {@link com.wf.wflib.audio.api.WfAudio} dropped inside {@link #run}. */
public final class FxMute {
    /** Threads inside {@link #run}; 0 => {@link #active} skips the thread-local. */
    private static final AtomicInteger RUNNING = new AtomicInteger();
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private FxMute() {
    }

    public static boolean active() {
        return RUNNING.get() != 0 && DEPTH.get()[0] > 0;
    }

    public static void run(Runnable action) {
        int[] depth = DEPTH.get();
        depth[0]++;
        RUNNING.incrementAndGet();
        try {
            action.run();
        } finally {
            RUNNING.decrementAndGet();
            depth[0]--;
        }
    }
}

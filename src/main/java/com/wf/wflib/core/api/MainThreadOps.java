package com.wf.wflib.core.api;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Lock-free MPSC op queue for work that only the server thread may do. Server-thread callers run ops inline after
 * draining anything queued earlier (FIFO preserved); workers enqueue.
 */
final class MainThreadOps {

    private static final Logger LOGGER = LogUtils.getLogger();

    private record Entry(Object scope, Runnable op) {
    }

    private final ConcurrentLinkedQueue<Entry> queue = new ConcurrentLinkedQueue<>();

    void enqueue(Object scope, Runnable op) {
        this.queue.add(new Entry(scope, op));
    }

    /** Server thread only. */
    void drain() {
        Entry e;
        while ((e = this.queue.poll()) != null) {
            try {
                e.op.run();
            } catch (Throwable t) {
                LOGGER.error("[wflib] deferred main-thread op failed", t);
            }
        }
    }

    boolean pending() {
        return !this.queue.isEmpty();
    }

    /** Server thread: drain, then run inline. */
    void runOnMain(Runnable op) {
        if (!this.queue.isEmpty()) {
            this.drain();
        }
        op.run();
    }

    void dropScope(Object scope) {
        this.queue.removeIf(e -> e.scope == scope);
    }

    void clear() {
        this.queue.clear();
    }
}

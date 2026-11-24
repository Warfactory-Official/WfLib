package com.wf.wflib.sim.api;

import com.wf.wflib.core.api.WorldThread;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/** Work handed from region workers to the world thread; FIFO per inbox. */
public final class WorldInbox<T> {

    private final ConcurrentLinkedQueue<T> queue = new ConcurrentLinkedQueue<>();

    /** Unarmed {@link WorldThread} => not off-thread. */
    public static boolean offWorld() {
        return WorldThread.armed() && !WorldThread.isWorldThread();
    }

    /** Any thread. */
    public void post(T item) {
        this.queue.add(item);
    }

    /** World thread only: {@code sink} runs in post order. */
    public void drain(Consumer<T> sink) {
        T item;
        while ((item = this.queue.poll()) != null) {
            sink.accept(item);
        }
    }

    public boolean isEmpty() {
        return this.queue.isEmpty();
    }

    public void clear() {
        this.queue.clear();
    }
}

package com.wf.wflib.physics.step;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Any-thread {@link #add}; {@link #drain}, {@link #clear} step thread only. Add during a step => next drain.
 * Pinned (level instance): adds from the draining thread skip the inbox: plain append, no atomics.
 */
final class BodyRegistry<T> {

    private final ConcurrentLinkedQueue<T> inbox = new ConcurrentLinkedQueue<>();
    private final List<T> ordered = new ArrayList<>();
    private final Comparator<T> order;
    private final boolean pinned;
    /** Pinned: first drainer; null until then => inbox. */
    private volatile Thread owner;
    private boolean unsorted;

    BodyRegistry(Comparator<T> order) {
        this(order, false);
    }

    BodyRegistry(Comparator<T> order, boolean pinned) {
        this.order = order;
        this.pinned = pinned;
    }

    void add(T body) {
        if (owner == Thread.currentThread()) {
            if (!unsorted && !ordered.isEmpty() && order.compare(ordered.get(ordered.size() - 1), body) > 0) {
                unsorted = true;
            }
            ordered.add(body);
        } else {
            inbox.offer(body);
        }
    }

    /** Pending + undrained-since-clear, sorted by the comparator; valid until {@link #clear}. */
    List<T> drain() {
        if (pinned && owner == null) {
            owner = Thread.currentThread();
        }
        if (!inbox.isEmpty()) {
            for (T body; (body = inbox.poll()) != null; ) {
                ordered.add(body);
            }
            unsorted = true;
        }
        if (unsorted) {
            ordered.sort(order);
            unsorted = false;
        }
        return ordered;
    }

    void clear() {
        ordered.clear();
        unsorted = false;
    }

    /** Any thread: an add not yet drained. */
    boolean pending() {
        return !inbox.isEmpty();
    }
}

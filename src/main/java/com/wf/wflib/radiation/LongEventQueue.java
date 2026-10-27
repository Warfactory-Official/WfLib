package com.wf.wflib.radiation;

import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * Sweep threads offer, server thread polls only past the sweep join. Divergence: NTM's lock-free XADD queue
 * replaced; offers are rare (fog chance, destroy only at tickDelay > 1), so an uncontended monitor suffices.
 */
final class LongEventQueue {

    static final long EMPTY = Long.MIN_VALUE;

    private final LongArrayList items = new LongArrayList(64);
    private int head;

    synchronized void offer(long v) {
        if (v == EMPTY) throw new IllegalArgumentException("Long.MIN_VALUE is reserved as EMPTY sentinel");
        items.add(v);
    }

    synchronized long poll() {
        if (head == items.size()) {
            if (head != 0) {
                items.clear();
                head = 0;
            }
            return EMPTY;
        }
        return items.getLong(head++);
    }

    synchronized void clear() {
        items.clear();
        head = 0;
    }
}

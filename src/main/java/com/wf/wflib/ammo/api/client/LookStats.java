package com.wf.wflib.ammo.api.client;

import java.util.concurrent.atomic.LongAdder;

/**
 * Look draw cost (harness reads): {@code RoundsVisual} frame ns + look instances placed; LOD sprites drawn; tracer
 * pass ns (sprites + tracer streaks, CPU incl. {@code endBatch}). Render thread only.
 */
public final class LookStats {
    public static final LongAdder frames = new LongAdder();
    public static final LongAdder nanos = new LongAdder();
    public static final LongAdder placed = new LongAdder();
    public static final LongAdder sprites = new LongAdder();
    public static final LongAdder tracerNanos = new LongAdder();

    private LookStats() {
    }

    public static void reset() {
        frames.reset();
        nanos.reset();
        placed.reset();
        sprites.reset();
        tracerNanos.reset();
    }
}

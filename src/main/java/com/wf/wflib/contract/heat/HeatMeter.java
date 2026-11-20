package com.wf.wflib.contract.heat;

/**
 * One faction's heat on one operator: rises with kills, falls with time. Stored as the value at a moment, so decay costs
 * nothing between looks and survives restarts.
 */
public final class HeatMeter {
    /** Game ticks for one point to cool: ten minutes. */
    public static final long TICKS_PER_POINT = 12_000;
    public static final int MAX = 100;

    private double value;
    private long at;

    public HeatMeter(double value, long at) {
        this.value = value;
        this.at = at;
    }

    /** The heat at {@code now}, after cooling. */
    public double value(long now) {
        return Math.max(0, value - Math.max(0, now - at) / (double) TICKS_PER_POINT);
    }

    /** Adds (or, negative, removes) heat at {@code now}; clamped to 0..{@link #MAX}. */
    public void add(double delta, long now) {
        value = Math.min(MAX, Math.max(0, value(now) + delta));
        at = now;
    }

    public double raw() {
        return value;
    }

    public long at() {
        return at;
    }
}

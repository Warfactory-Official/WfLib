package com.wf.wflib.fx.api;

/**
 * Level game time seen from a Flashback replay, which can seek: time then jumps back. Everything timed against the
 * game clock (flashes age as {@code now - start}: negative age => a peak many times over and never expiring) must be
 * dropped, not left to run. Live play never rewinds, so it never reports one.
 */
public final class ReplayClock {
    private long last = Long.MIN_VALUE;

    /** True when {@code now} is before the last time seen, in a replay. Records {@code now}. */
    public boolean rewound(long now, boolean replay) {
        boolean back = replay && now < last;
        last = now;
        return back;
    }

    public void reset() {
        last = Long.MIN_VALUE;
    }
}

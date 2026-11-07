package com.wf.wflib.soldier;

import java.util.Random;

/** Cover exposure lasts across firing bursts; changing the trigger must not bounce the body up and down. */
final class BotPeek {
    enum Phase { HIDDEN, ENTERING, WATCHING, RETURNING }

    static final int SETTLE_TICKS = 12;
    private final Random random;
    private Phase phase = Phase.HIDDEN;
    private long until = Long.MIN_VALUE;
    private int cycles;

    BotPeek(int seed) {
        random = new Random(seed);
    }

    void reset(long now) {
        phase = Phase.HIDDEN;
        until = now + 16 + random.nextInt(20);
    }

    void tick(long now, boolean ready, boolean interrupt) {
        if (until == Long.MIN_VALUE) reset(now);
        if (interrupt && exposed()) {
            phase = Phase.RETURNING;
            until = now + SETTLE_TICKS;
        }
        if (now < until) return;
        switch (phase) {
            case HIDDEN -> {
                if (!ready || interrupt) return;
                phase = Phase.ENTERING;
                until = now + SETTLE_TICKS;
                cycles++;
            }
            case ENTERING -> {
                phase = Phase.WATCHING;
                until = now + 40 + random.nextInt(31);
            }
            case WATCHING -> {
                phase = Phase.RETURNING;
                until = now + SETTLE_TICKS;
            }
            case RETURNING -> {
                phase = Phase.HIDDEN;
                until = now + 26 + random.nextInt(25);
            }
        }
    }

    /** An enemy is in plain sight: stay up and shoot, whatever the cycle was doing. */
    void engage(long now) {
        if (phase == Phase.WATCHING) {
            until = Math.max(until, now + 20);
        } else if (phase != Phase.ENTERING) {
            phase = Phase.WATCHING;
            until = now + 30;
        }
    }

    boolean exposed() {
        return phase == Phase.ENTERING || phase == Phase.WATCHING;
    }

    boolean settled() {
        return phase == Phase.WATCHING;
    }

    boolean hidden() {
        return phase == Phase.HIDDEN;
    }

    Phase phase() {
        return phase;
    }

    int cycles() {
        return cycles;
    }
}

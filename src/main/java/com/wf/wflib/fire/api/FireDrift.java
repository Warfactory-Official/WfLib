package com.wf.wflib.fire.api;

/** Fall + wind of an airborne {@link FireLingeringEntity}; pure in (game time, position) => server and client step identically with no sync. */
public final class FireDrift {
    /** b/tick. */
    public static final double TERMINAL = 0.06, ACCEL = 0.004;
    /** Share of the gap to the wind velocity closed per tick. */
    private static final double RELAX = 0.1;

    private FireDrift() {
    }

    /** Wind b/tick at {@code (x, z)}: heading turns slowly with time and position, speed gusts 0.015-0.055. */
    public static void wind(long gameTime, double x, double z, double[] out) {
        double heading = gameTime * 0.0009 + 1.7 * Math.sin(gameTime * 0.0021 + z * 0.011) + x * 0.004;
        double speed = 0.035 + 0.02 * Math.sin(gameTime * 0.013 + x * 0.05 + z * 0.04);
        out[0] = Math.cos(heading) * speed;
        out[1] = Math.sin(heading) * speed;
    }

    /** One tick of velocity {@code v} ({@code vx, vy, vz}): sinks toward {@link #TERMINAL}, horizontal relaxes to the wind. */
    public static void step(long gameTime, double x, double z, double[] v, double[] windScratch) {
        wind(gameTime, x, z, windScratch);
        v[0] += (windScratch[0] - v[0]) * RELAX;
        v[1] = Math.max(v[1] - ACCEL, -TERMINAL);
        v[2] += (windScratch[1] - v[2]) * RELAX;
    }
}

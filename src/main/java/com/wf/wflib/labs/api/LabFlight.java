package com.wf.wflib.labs.api;

import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.RoundArc;

/**
 * Level flight along +x from the muzzle through {@link RoundArc} (the {@code Rounds} recurrence): state where the
 * path first reaches each range.
 */
public final class LabFlight {
    private static final double VOID = -1.0e9;

    private LabFlight() {
    }

    /**
     * @param reached false => the round expired (life) before the range; other fields then 0
     * @param tick    tick whose step crosses the range
     * @param time    s
     * @param speed   m/s, the step crossing the range
     * @param drop    m below the muzzle line
     */
    public record Sample(double range, boolean reached, int tick, double time, double speed, double drop) {
    }

    /** @param muzzleMs m/s; rockets burn {@code motorAccel} for {@code burnTicks} along +x */
    public static Sample[] fly(KineticPreset preset, double muzzleMs, double[] ranges) {
        int n = preset.lifeTicks();
        double[] p = new double[3 * n];
        double v = muzzleMs / 20.0;
        int steps = preset.motorAccel() > 0.0f
                ? RoundArc.predictPowered(preset, 0, 0, 0, v, 0, 0, 1, 0, 0, n, VOID, n, p)
                : RoundArc.predict(preset, 0, 0, 0, v, 0, 0, n, VOID, false, n, p);
        Sample[] out = new Sample[ranges.length];
        int k = 0;
        for (int r = 0; r < ranges.length; r++) {
            double range = ranges[r];
            if (range <= 0.0) {
                out[r] = new Sample(range, true, 0, 0.0, muzzleMs, 0.0);
                continue;
            }
            while (k < steps && p[3 * k] < range) {
                k++;
            }
            if (k >= steps) {
                out[r] = new Sample(range, false, 0, 0.0, 0.0, 0.0);
                continue;
            }
            double x0 = k == 0 ? 0.0 : p[3 * (k - 1)];
            double y0 = k == 0 ? 0.0 : p[3 * (k - 1) + 1];
            double x1 = p[3 * k];
            double y1 = p[3 * k + 1];
            double f = (range - x0) / (x1 - x0);
            double dx = x1 - x0;
            double dy = y1 - y0;
            out[r] = new Sample(range, true, k + 1, (k + f) / 20.0, Math.sqrt(dx * dx + dy * dy) * 20.0,
                    -(y0 + f * dy));
        }
        return out;
    }
}

package com.wf.wflib.round.api;

/** {@link Rounds} flight continued dry, without collision: bit-equal to its steps in open air. */
public final class RoundArc {

    private RoundArc() {
    }

    /**
     * Positions after each of up to {@code ticks} steps from {@code (x, v)} with {@code life} left, into
     * {@code out[3k..3k+2]}; stops where {@link Rounds} expires the round (life, or below {@code voidY} descending).
     * {@code tumbling} => {@link Ricochet#decay}.
     * @return steps written
     */
    public static int predict(KineticPreset preset, double x, double y, double z, double vx, double vy, double vz,
                              int life, double voidY, boolean tumbling, int ticks, double[] out) {
        int n = Math.min(ticks, out.length / 3);
        float gravity = preset.gravity();
        for (int k = 0; k < n; k++) {
            if (--life <= 0 || y < voidY && vy <= 0.0) {
                return k;
            }
            x += vx;
            y += vy;
            z += vz;
            out[3 * k] = x;
            out[3 * k + 1] = y;
            out[3 * k + 2] = z;
            double decay = Ricochet.decay(preset, Math.sqrt(vx * vx + vy * vy + vz * vz), tumbling);
            vx = vx * decay;
            vy = vy * decay - gravity;
            vz = vz * decay;
        }
        return n;
    }

    /**
     * {@link #predict} with a motor: {@link KineticPreset#motorAccel} along {@code nose} (normalized; zero below
     * 1e-4) added after the drag/gravity update of each of the first {@link KineticPreset#burnTicks} steps.
     */
    public static int predictPowered(KineticPreset preset, double x, double y, double z, double vx, double vy,
                                     double vz, double noseX, double noseY, double noseZ, int life, double voidY,
                                     int ticks, double[] out) {
        double length = Math.sqrt(noseX * noseX + noseY * noseY + noseZ * noseZ);
        double accel = preset.motorAccel();
        double tx = 0, ty = 0, tz = 0;
        if (length >= 1.0E-4) {
            tx = noseX / length * accel;
            ty = noseY / length * accel;
            tz = noseZ / length * accel;
        }
        int burn = preset.burnTicks();
        int n = Math.min(ticks, out.length / 3);
        float gravity = preset.gravity();
        for (int k = 0; k < n; k++) {
            if (--life <= 0 || y < voidY && vy <= 0.0) {
                return k;
            }
            x += vx;
            y += vy;
            z += vz;
            out[3 * k] = x;
            out[3 * k + 1] = y;
            out[3 * k + 2] = z;
            double decay = preset.decay(Math.sqrt(vx * vx + vy * vy + vz * vz));
            vx = vx * decay;
            vy = vy * decay - gravity;
            vz = vz * decay;
            if (k < burn) {
                vx += tx;
                vy += ty;
                vz += tz;
            }
        }
        return n;
    }
}

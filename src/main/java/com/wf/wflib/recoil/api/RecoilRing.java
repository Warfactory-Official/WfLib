package com.wf.wflib.recoil.api;

/**
 * One shooter's recoil over a ring of the last {@link #SHOTS} shots, stepped per frame; seconds, deg
 * {@code [yaw, pitch]}. Per shot: camera receives {@code kick(t) - applied}, weapon leads by {@code total - applied}
 * => camera + lead == total at every step. Shots with both factors 0 carry {@link #spring} weights only. Shot index =
 * shots since {@link #reset}. Mutable, allocation-free.
 */
public final class RecoilRing {
    public static final int SHOTS = 32;
    /** Draw axes and {@link #spring} channels. */
    public static final int PITCH = 0, YAW = 1, ROLL = 2;

    private final double[] shotAt = new double[SHOTS];
    private final float[] fPitch = new float[SHOTS];
    private final float[] fYaw = new float[SHOTS];
    private final float[] uPitch = new float[SHOTS];
    private final float[] uYaw = new float[SHOTS];
    private final float[] applied = new float[SHOTS * 2];
    private final float[] total = new float[SHOTS * 2];
    private final float[] weight = new float[SHOTS * 3];
    private final float[] acc = new float[2];
    private final float[] tmp = new float[2];
    private int count;
    private double lastShot;
    private double prev = Double.NaN;

    public void reset() {
        count = 0;
        acc[0] = acc[1] = 0;
        prev = Double.NaN;
    }

    public int shots() {
        return count;
    }

    /** Seconds since the previous shot; infinite before the first. */
    public float gap(double now) {
        return count == 0 ? Float.POSITIVE_INFINITY : (float) (now - lastShot);
    }

    /**
     * Seeded draws ({@link RecoilCurves#uPitch}, {@link RecoilCurves#uYaw}); spring weights pitch = peak climb x
     * {@code visualGain}, yaw 0, roll = that x {@code 1 + rollJitter (2 uRoll - 1)}.
     */
    public void shot(RecoilProfile p, long seed, double now, float fPitch, float fYaw, float visualGain,
                     float rollJitter) {
        float up = RecoilCurves.uPitch(seed, count), uy = RecoilCurves.uYaw(p, seed, count);
        float peak = RecoilCurves.peakPitch(p, up, fPitch) * visualGain;
        float roll = peak * (1 + rollJitter * (2 * RecoilCurves.uRoll(seed, count) - 1));
        shot(p, now, up, uy, fPitch, fYaw, peak, 0, roll);
    }

    /** Caller-supplied draws and {@link #spring} weights. */
    public void shot(RecoilProfile p, double now, float uPitch, float uYaw, float fPitch, float fYaw, float wPitch,
                     float wYaw, float wRoll) {
        int k = count % SHOTS;
        shotAt[k] = now;
        this.fPitch[k] = fPitch;
        this.fYaw[k] = fYaw;
        this.uPitch[k] = uPitch;
        this.uYaw[k] = uYaw;
        applied[2 * k] = applied[2 * k + 1] = 0;
        RecoilCurves.total(p, uPitch, uYaw, fPitch, fYaw, tmp);
        total[2 * k] = tmp[0];
        total[2 * k + 1] = tmp[1];
        weight[3 * k + PITCH] = wPitch;
        weight[3 * k + YAW] = wYaw;
        weight[3 * k + ROLL] = wRoll;
        if (Double.isNaN(prev)) {
            prev = now;
        }
        count++;
        lastShot = now;
    }

    /** Camera delta over {@code (previous step, now]}: kicks minus recovery. */
    public void step(RecoilProfile p, double now, float[] out) {
        out[0] = out[1] = 0;
        if (count == 0) {
            return;
        }
        for (int i = Math.max(0, count - SHOTS); i < count; i++) {
            int k = i % SHOTS;
            if (fPitch[k] == 0 && fYaw[k] == 0) {
                continue;
            }
            RecoilCurves.kick(p, uPitch[k], uYaw[k], since(now, k), fPitch[k], fYaw[k], tmp);
            out[0] += tmp[0] - applied[2 * k];
            out[1] += tmp[1] - applied[2 * k + 1];
            applied[2 * k] = tmp[0];
            applied[2 * k + 1] = tmp[1];
        }
        acc[0] += out[0] * p.pullFraction();
        acc[1] += out[1] * p.pullFraction();
        float f = RecoilCurves.recovery(p, (float) (prev - lastShot), (float) Math.max(0, now - prev));
        out[0] -= acc[0] * (1 - f);
        out[1] -= acc[1] * (1 - f);
        acc[0] *= f;
        acc[1] *= f;
        prev = now;
    }

    private float since(double now, int k) {
        return (float) Math.max(0, now - shotAt[k]);
    }

    /** Weapon ahead of the camera as of the last {@link #step}. */
    public void lead(float[] out) {
        out[0] = out[1] = 0;
        for (int i = Math.max(0, count - SHOTS); i < count; i++) {
            int k = i % SHOTS;
            out[0] += total[2 * k] - applied[2 * k];
            out[1] += total[2 * k + 1] - applied[2 * k + 1];
        }
    }

    /**
     * {@code channel} weights through a damped spring ({@code omega} rad/s, {@code zeta < 1}): sum of weight x
     * {@code e^(-zeta omega t) sin(omega_d t)}, first peak 1. Closed form => frame-rate independent. Past
     * {@code 8 / (zeta omega)} (< 4e-4) skipped.
     */
    public float spring(double now, int channel, float omega, float zeta) {
        float a = zeta * omega, b = omega * (float) Math.sqrt(1 - zeta * zeta);
        float tPeak = (float) Math.atan2(b, a) / b;
        float norm = 1 / ((float) Math.exp(-a * tPeak) * (float) Math.sin(b * tPeak));
        float sum = 0;
        for (int i = Math.max(0, count - SHOTS); i < count; i++) {
            int k = i % SHOTS;
            float t = since(now, k);
            if (a * t < 8) {
                sum += weight[3 * k + channel] * (float) Math.exp(-a * t) * (float) Math.sin(b * t);
            }
        }
        return sum * norm;
    }

    /** Muzzle flip of all active shots at {@code now}, added to {@code out}. */
    public void flipAt(RecoilProfile p, double now, float[] out) {
        for (int i = Math.max(0, count - SHOTS); i < count; i++) {
            int k = i % SHOTS;
            RecoilCurves.flip(p, uPitch[k], uYaw[k], since(now, k), fPitch[k], fYaw[k], out);
        }
    }

    /** Exact weapon lead at {@code now}; independent of the last {@link #step}. */
    public void leadAt(RecoilProfile p, double now, float[] out) {
        out[0] = out[1] = 0;
        for (int i = Math.max(0, count - SHOTS); i < count; i++) {
            int k = i % SHOTS;
            RecoilCurves.lead(p, uPitch[k], uYaw[k], since(now, k), fPitch[k], fYaw[k], tmp);
            out[0] += tmp[0];
            out[1] += tmp[1];
        }
    }

    /** Sight/POI drift at {@code now}: sum of each active shot's {@link RecoilCurves#drift}. */
    public void driftAt(RecoilProfile p, double now, float[] out) {
        out[0] = out[1] = 0;
        for (int i = Math.max(0, count - SHOTS); i < count; i++) {
            int k = i % SHOTS;
            RecoilCurves.drift(p, uPitch[k], uYaw[k], since(now, k), fPitch[k], fYaw[k], tmp);
            out[0] += tmp[0];
            out[1] += tmp[1];
        }
    }
}

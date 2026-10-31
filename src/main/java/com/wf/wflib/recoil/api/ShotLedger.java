package com.wf.wflib.recoil.api;

/**
 * Server copy of a shooter's last {@link RecoilRing#SHOTS} shots: time + {@code variants} factor pairs per shot
 * (e.g. both ends of an uncertain client state). Draws seeded per shot index as {@link RecoilRing#shot(RecoilProfile,
 * long, double, float, float, float, float)} => same lead/drift as the client. Time in caller units, {@code unitsPerSecond}
 * of them per second. Shot index = shots since {@link #reset}.
 */
public final class ShotLedger {
    private final int variants;
    private final double unitsPerSecond;
    private final double[] at = new double[RecoilRing.SHOTS];
    /** {@code [pitch0, yaw0, pitch1, yaw1, ..]} per shot. */
    private final float[] factors;
    private final float[] tmp = new float[2];
    private int count;

    public ShotLedger(int variants, double unitsPerSecond) {
        this.variants = variants;
        this.unitsPerSecond = unitsPerSecond;
        factors = new float[RecoilRing.SHOTS * 2 * variants];
    }

    public void reset() {
        count = 0;
    }

    public int shots() {
        return count;
    }

    /** Seconds since the previous shot; infinite before the first. */
    public float gap(double t) {
        return count == 0 ? Float.POSITIVE_INFINITY : (float) ((t - at[(count - 1) % RecoilRing.SHOTS]) / unitsPerSecond);
    }

    /** {@code pitchYaw}: {@code 2 * variants} factors, laid out as {@link #factors}. */
    public void add(double t, float[] pitchYaw) {
        int n = 2 * variants;
        if (pitchYaw.length != n) {
            throw new IllegalArgumentException("expected " + n + " factors, got " + pitchYaw.length);
        }
        int k = count % RecoilRing.SHOTS;
        at[k] = t;
        System.arraycopy(pitchYaw, 0, factors, n * k, n);
        count++;
    }

    /** Weapon lead {@code [yaw, pitch]} at {@code t} from earlier shots, factor pair {@code variant}. */
    public void lead(RecoilProfile p, long seed, double t, int variant, float[] out) {
        out[0] = out[1] = 0;
        for (int i = Math.max(0, count - RecoilRing.SHOTS); i < count; i++) {
            int f = factor(i, variant);
            RecoilCurves.lead(p, RecoilCurves.uPitch(seed, i), RecoilCurves.uYaw(p, seed, i), since(t, i), factors[f],
                    factors[f + 1], tmp);
            out[0] += tmp[0];
            out[1] += tmp[1];
        }
    }

    /** Sight/POI drift {@code [yaw, pitch]} at {@code t}, factor pair {@code variant}. */
    public void drift(RecoilProfile p, long seed, double t, int variant, float[] out) {
        out[0] = out[1] = 0;
        for (int i = Math.max(0, count - RecoilRing.SHOTS); i < count; i++) {
            int f = factor(i, variant);
            RecoilCurves.drift(p, RecoilCurves.uPitch(seed, i), RecoilCurves.uYaw(p, seed, i), since(t, i), factors[f],
                    factors[f + 1], tmp);
            out[0] += tmp[0];
            out[1] += tmp[1];
        }
    }

    private int factor(int shot, int variant) {
        return 2 * (variants * (shot % RecoilRing.SHOTS) + variant);
    }

    private float since(double t, int shot) {
        return (float) ((t - at[shot % RecoilRing.SHOTS]) / unitsPerSecond);
    }
}

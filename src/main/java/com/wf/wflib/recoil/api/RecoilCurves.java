package com.wf.wflib.recoil.api;

import com.wf.wflib.core.api.Seeded;

import java.util.List;

/**
 * Per-shot recoil, outputs {@code [yaw, pitch]} deg, MC signs (muzzle climb = negative pitch). {@code uPitch},
 * {@code uYaw}: the shot's draws in [0, 1); seeded by {@code (seed, shot)} via {@link #uPitch} / {@link #uYaw} so the
 * server recomputes them.
 * <ul>
 * <li>camera: {@link #kick} over time, summed over shots; decays by {@link #recovery}.</li>
 * <li>weapon offset: {@link #lead} = {@link #total} - {@link #kick} + {@link #flip}.</li>
 * <li>sight/POI drift: {@link #drift} = {@link #total} held, then decayed by {@link #recovery}.</li>
 * </ul>
 */
public final class RecoilCurves {
    private RecoilCurves() {
    }

    /** Piecewise-linear over {@code lerp(min, max, u)}; before the first key its value, past the last held. */
    public static float curve(List<RecoilProfile.CurveKey> keys, float u, float t) {
        RecoilProfile.CurveKey k0 = keys.getFirst();
        if (t <= k0.time()) {
            return value(k0, u);
        }
        for (int i = 1; i < keys.size(); i++) {
            RecoilProfile.CurveKey k1 = keys.get(i);
            if (t < k1.time()) {
                float f = (t - k0.time()) / (k1.time() - k0.time());
                float v0 = value(k0, u);
                return v0 + (value(k1, u) - v0) * f;
            }
            k0 = k1;
        }
        return value(k0, u);
    }

    private static float value(RecoilProfile.CurveKey k, float u) {
        return k.min() + (k.max() - k.min()) * u;
    }

    public static float uPitch(long seed, int shot) {
        return Seeded.unit(seed, shot, RecoilRing.PITCH);
    }

    /** {@code wander > 1}: smoothstep value noise over the shot index (mid-knot spread narrows). */
    public static float uYaw(RecoilProfile p, long seed, int shot) {
        float wander = p.wander();
        if (wander <= 1) {
            return Seeded.unit(seed, shot, RecoilRing.YAW);
        }
        float x = shot / wander;
        int i = (int) Math.floor(x);
        float f = x - i;
        f = f * f * (3 - 2 * f);
        float a = Seeded.unit(seed, i, RecoilRing.YAW);
        return a + (Seeded.unit(seed, i + 1, RecoilRing.YAW) - a) * f;
    }

    public static float uRoll(long seed, int shot) {
        return Seeded.unit(seed, shot, RecoilRing.ROLL);
    }

    /** Camera offset of one shot {@code t} s after it. */
    public static void kick(RecoilProfile p, float uPitch, float uYaw, float t, float fPitch, float fYaw, float[] out) {
        out[0] = fYaw * curve(p.yaw(), uYaw, t);
        out[1] = -fPitch * curve(p.pitch(), uPitch, t);
    }

    /** Settled {@link #kick}. */
    public static void total(RecoilProfile p, float uPitch, float uYaw, float fPitch, float fYaw, float[] out) {
        kick(p, uPitch, uYaw, Float.POSITIVE_INFINITY, fPitch, fYaw, out);
    }

    /** Largest |pitch| of the shot's curve, deg. */
    public static float peakPitch(RecoilProfile p, float uPitch, float fPitch) {
        float peak = 0;
        for (RecoilProfile.CurveKey k : p.pitch()) {
            peak = Math.max(peak, Math.abs(value(k, uPitch)));
        }
        return fPitch * peak;
    }

    /** Muzzle flip {@code t} s after the shot, added to {@code out}. */
    public static void flip(RecoilProfile p, float uPitch, float uYaw, float t, float fPitch, float fYaw, float[] out) {
        RecoilProfile.Flip f = p.flip();
        if (f.pitch() == 0 && f.yaw() == 0) {
            return;
        }
        float g;
        if (t < f.attack()) {
            float s = t / f.attack();
            g = s * s * (3 - 2 * s);
        } else {
            g = (float) Math.exp(-(t - f.attack()) / f.decay());
        }
        out[0] += fYaw * f.yaw() * (2 * uYaw - 1) * g;
        out[1] -= fPitch * f.pitch() * (0.85F + 0.3F * uPitch) * g;
    }

    /** Weapon ahead of the camera {@code t} s after the shot; 0 once the curve settles, plus {@link #flip}. */
    public static void lead(RecoilProfile p, float uPitch, float uYaw, float t, float fPitch, float fYaw, float[] out) {
        float yaw = fYaw * curve(p.yaw(), uYaw, Float.POSITIVE_INFINITY);
        float pitch = -fPitch * curve(p.pitch(), uPitch, Float.POSITIVE_INFINITY);
        kick(p, uPitch, uYaw, t, fPitch, fYaw, out);
        out[0] = yaw - out[0];
        out[1] = pitch - out[1];
        flip(p, uPitch, uYaw, t, fPitch, fYaw, out);
    }

    /** Factor on the accumulated offset over {@code [since, since + dt]} s after the last shot. */
    public static float recovery(RecoilProfile p, float since, float dt) {
        float active = Math.min(dt, since + dt - p.recoveryDelay());
        return active <= 0 ? 1.0F : (float) Math.exp(-p.recoveryRate() * active);
    }

    /** {@link #total} held, then decayed by {@link #recovery} over the {@code t} s since the shot. */
    public static void drift(RecoilProfile p, float uPitch, float uYaw, float t, float fPitch, float fYaw, float[] out) {
        total(p, uPitch, uYaw, fPitch, fYaw, out);
        float f = recovery(p, 0, t);
        out[0] *= f;
        out[1] *= f;
    }
}

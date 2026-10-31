package com.wf.wflib.recoil;

import com.wf.wflib.core.api.Seeded;
import com.wf.wflib.recoil.api.RecoilCurves;
import com.wf.wflib.recoil.api.RecoilProfile;
import com.wf.wflib.recoil.api.RecoilProfile.CurveKey;
import com.wf.wflib.recoil.api.RecoilRing;
import com.wf.wflib.recoil.api.ShotLedger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Hashes / tables recorded from the pre-consolidation dependent copies (2026-10-30). */
class RecoilGoldenTest {
    static final long SEED = 0x1234_5678_9ABCL;
    static final RecoilProfile M4 = new RecoilProfile(
            List.of(new CurveKey(0, 0, 0), new CurveKey(0.05F, 0.8F, 0.8F), new CurveKey(1, 3.95F, 3.95F)),
            List.of(new CurveKey(0, 0, 0), new CurveKey(1, -3.16F, 3.16F)), 0.04F, 8.68F, 1, 1,
            RecoilProfile.Flip.NONE);
    static final RecoilProfile WANDER = new RecoilProfile(M4.pitch(), M4.yaw(), 0.1F, 6, 8, 0.6F,
            new RecoilProfile.Flip(3, 0.5F, 0.03F, 0.06F));

    /** Fixed 3-key station curve: rise / settle s; recovery 0.15 s, 5/s; factor = peak climb deg. */
    static final float RISE = 0.05F, SETTLE = 0.18F;
    static final RecoilProfile FIXED = new RecoilProfile(
            List.of(new CurveKey(0, 0, 0), new CurveKey(RISE, 0.7F, 1), new CurveKey(SETTLE, 0.15F, 0.25F)),
            List.of(new CurveKey(0, 0, 0), new CurveKey(RISE, -0.35F, 0.35F), new CurveKey(SETTLE, -0.14F, 0.14F)),
            0.15F, 5, 1, 1, RecoilProfile.Flip.NONE);

    private long h;

    private void f(float x) {
        h = h * 31 + Float.floatToIntBits(x);
    }

    private long ring(RecoilProfile p) {
        h = 17;
        RecoilRing rc = new RecoilRing();
        float[] o = new float[2];
        int shot = 0;
        for (int fr = 0; fr <= 3 * 144; fr++) {
            float now = 10 + fr / 144F;
            while (shot < 40 && 10 + 0.07F * shot <= now) {
                f(rc.gap(10 + 0.07F * shot));
                rc.shot(p, SEED, 10 + 0.07F * shot, 0.8F + 0.01F * shot, 0.6F - 0.005F * shot, 1.3F, 0.4F);
                shot++;
            }
            rc.step(p, now, o);
            f(o[0]);
            f(o[1]);
            rc.lead(o);
            f(o[0]);
            f(o[1]);
            rc.leadAt(p, now, o);
            f(o[0]);
            f(o[1]);
            rc.driftAt(p, now, o);
            f(o[0]);
            f(o[1]);
            o[0] = o[1] = 0;
            rc.flipAt(p, now, o);
            f(o[0]);
            f(o[1]);
            f(rc.spring(now, RecoilRing.PITCH, 35, 0.45F));
            f(rc.spring(now, RecoilRing.ROLL, 22, 0.35F));
            f(rc.gap(now));
        }
        return h;
    }

    private long ledger(RecoilProfile p) {
        h = 17;
        ShotLedger s = new ShotLedger(2, 20);
        float[] o = new float[2];
        double t = 0;
        for (int i = 0; i < 40; i++) {
            t = 100 + 1.4 * i;
            f(s.gap(t));
            query(s, p, t, o);
            float g = 1 + 0.01F * i;
            s.add(t, new float[] {0.8F * g, 0.6F * g, 1.0F * g, 0.75F * g});
        }
        for (int k = 0; k < 40; k++) {
            query(s, p, t + 0.5 * k, o);
        }
        return h;
    }

    private void query(ShotLedger s, RecoilProfile p, double t, float[] o) {
        for (int v = 0; v < 2; v++) {
            s.lead(p, SEED, t, v, o);
            f(o[0]);
            f(o[1]);
            s.drift(p, SEED, t, v, o);
            f(o[0]);
            f(o[1]);
        }
    }

    @Test
    void ringIsBitIdenticalToThePackCopy() {
        assertEquals(0x683e19f3408dbe3dL, ring(M4));
        assertEquals(0x9c92408a28c22333L, ring(WANDER));
    }

    @Test
    void ledgerIsBitIdenticalToTheServerCopy() {
        assertEquals(0x60adfb10c9917aa2L, ledger(M4));
        assertEquals(0x8d66343fb008fcdaL, ledger(WANDER));
    }

    @Test
    void seededIsBitIdenticalToTheAimHash() {
        h = 17;
        for (int a = 0; a < 1000; a++) {
            for (int b = 0; b < 100; b++) {
                f(Seeded.unit(SEED, a, b));
            }
            h = h * 31 + Seeded.mix(a);
            h = h * 31 + Seeded.playerSeed(new UUID(a * 7L, ~a));
        }
        assertEquals(0xe282a65240e9c7a2L, h);
    }

    /** Pre-consolidation fixed curve: {@code 0 -> hi} over RISE, {@code hi -> lo} by SETTLE, lo held. */
    private static float fixedCurve(float t, float hi, float lo) {
        if (t <= 0) {
            return 0;
        }
        if (t < RISE) {
            return hi * t / RISE;
        }
        if (t < SETTLE) {
            return hi + (lo - hi) * (t - RISE) / (SETTLE - RISE);
        }
        return lo;
    }

    @Test
    void fixedStationCurveIsAThreeKeyProfile() {
        float[] out = new float[2];
        for (float p : new float[] {0.04F, 0.15F, 0.6F}) {
            for (float u = 0; u < 1; u += 0.0625F) {
                float hiPitch = p * (0.7F + 0.3F * u), yaw = p * 0.35F * (2 * u - 1);
                for (float t = -0.01F; t < 0.3F; t += 0.0025F) {
                    RecoilCurves.kick(FIXED, u, u, t, p, p, out);
                    assertEquals(fixedCurve(t, yaw, 0.4F * yaw), out[0], 1e-6F * p, "yaw t=" + t);
                    assertEquals(-fixedCurve(t, hiPitch, p * (0.15F + 0.1F * u)), out[1], 1e-6F * p, "pitch t=" + t);
                }
            }
        }
    }

    /** Pre-consolidation station ring: cumulative step {@code [yaw, pitch]}, spring pitch / yaw / roll every 0.25 s. */
    private static final float[][] STATION = {
            {0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
            {-0.05179032F, -0.1700545F, 0.060832612F, 0.0F, -0.015390739F},
            {-0.012116171F, -0.23205186F, 0.054173093F, 0.0F, 0.06793443F},
            {0.028173108F, -0.26169342F, 0.023201047F, 0.0F, -0.076727875F},
            {-0.02047024F, -0.29561457F, -0.0026197406F, 0.0F, 0.0056717424F},
            {-0.0052633793F, -0.112358F, 0.10821077F, -0.035279382F, 0.076127246F},
            {-0.0052633793F, -0.112358F, 0.017343175F, 0.0075792745F, -0.053819116F},
            {-0.0019362892F, -0.041334216F, -4.5018325E-5F, 3.3723464E-4F, -0.0065808636F},
            {-5.547556E-4F, -0.011842457F, 0.0F, -4.0960603E-7F, 3.4935342E-4F},
            {-1.5893922E-4F, -0.003392921F, 0.0F, 0.0F, 1.6929532E-4F},
            {-4.553584E-5F, -9.7208656E-4F, 0.0F, 0.0F, 0.0F},
            {-1.3045197E-5F, -2.7850532E-4F, 0.0F, 0.0F, 0.0F},
            {-3.736466E-6F, -7.979086E-5F, 0.0F, 0.0F, 0.0F}};

    /**
     * Station shot: draw order {@code uPitch, uYaw, side, spread} (+ remote yaw weight); manual = kick + jolt, remote =
     * jolt only.
     */
    private static void stationShot(RecoilRing ring, double now, float impulse, boolean manual, SplittableRandom r) {
        float up = r.nextFloat(), uy = r.nextFloat();
        float side = r.nextBoolean() ? 1 : -1, spread = 0.6F + 0.8F * r.nextFloat();
        if (manual) {
            float p = Math.clamp(0.025F * (float) Math.sqrt(impulse), 0.04F, 0.6F);
            ring.shot(FIXED, now, up, uy, p, p, 0.5F * p, 0, 0.4F * p * side * spread);
        } else {
            float j = Math.clamp(0.05F * (float) (Math.log1p(impulse / 10F) / Math.log(2)), 0.02F, 0.4F);
            ring.shot(FIXED, now, up, uy, 0, 0, j, 0.4F * j * (2 * r.nextFloat() - 1), 0.6F * j * side * spread);
        }
    }

    @Test
    void fixedStationRingMatchesThePreConsolidationRing() {
        float m2 = 0.042F * 890;
        RecoilRing ring = new RecoilRing();
        SplittableRandom rnd = new SplittableRandom(7);
        float[] o = new float[2];
        float cy = 0, cp = 0;
        int manual = 0, remote = 0;
        for (int fr = 0; fr <= 300; fr++) {
            double now = fr * 0.01;
            while (manual < 9 && 0.11 * manual <= now) {
                stationShot(ring, 0.11 * manual, m2, true, rnd);
                manual++;
            }
            while (remote < 3 && 1.2 + 0.1 * remote <= now) {
                stationShot(ring, 1.2 + 0.1 * remote, m2, false, rnd);
                remote++;
            }
            ring.step(FIXED, now, o);
            cy += o[0];
            cp += o[1];
            if (fr % 25 == 0) {
                float[] g = STATION[fr / 25];
                assertEquals(g[0], cy, 1e-6F, "yaw at " + now);
                assertEquals(g[1], cp, 1e-6F, "pitch at " + now);
                assertEquals(g[2], ring.spring(now, RecoilRing.PITCH, 30, 0.45F), 0, "spring pitch at " + now);
                assertEquals(g[3], ring.spring(now, RecoilRing.YAW, 28, 0.35F), 0, "spring yaw at " + now);
                assertEquals(g[4], ring.spring(now, RecoilRing.ROLL, 22, 0.35F), 0, "spring roll at " + now);
            }
        }
    }
}

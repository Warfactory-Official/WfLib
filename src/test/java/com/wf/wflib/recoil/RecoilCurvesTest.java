package com.wf.wflib.recoil;

import com.wf.wflib.recoil.api.RecoilCurves;
import com.wf.wflib.recoil.api.RecoilProfile;
import com.wf.wflib.recoil.api.RecoilProfile.CurveKey;
import com.wf.wflib.recoil.api.RecoilRing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.wf.wflib.recoil.RecoilGoldenTest.M4;
import static com.wf.wflib.recoil.RecoilGoldenTest.SEED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoilCurvesTest {
    /** Single key at t=0: whole kick on the first step (a {@code kick(t) - kick(t - dt)} delta loses it). */
    static final RecoilProfile STEP = new RecoilProfile(List.of(new CurveKey(0, 2, 3)),
            List.of(new CurveKey(0, -1, 1)), 10, 8, 1, 1, RecoilProfile.Flip.NONE);
    static final RecoilProfile.Flip FLIP = new RecoilProfile.Flip(3, 0.5F, 0.03F, 0.06F);

    private static RecoilProfile with(RecoilProfile p, float delay, float rate, float wander, float pull,
                                      RecoilProfile.Flip flip) {
        return new RecoilProfile(p.pitch(), p.yaw(), delay, rate, wander, pull, flip);
    }

    private static void total(RecoilProfile p, int shot, float fPitch, float fYaw, float[] out) {
        RecoilCurves.total(p, RecoilCurves.uPitch(SEED, shot), RecoilCurves.uYaw(p, SEED, shot), fPitch, fYaw, out);
    }

    private static void kick(RecoilProfile p, int shot, float t, float[] out) {
        RecoilCurves.kick(p, RecoilCurves.uPitch(SEED, shot), RecoilCurves.uYaw(p, SEED, shot), t, 1, 1, out);
    }

    private static void lead(RecoilProfile p, int shot, float t, float fPitch, float fYaw, float[] out) {
        RecoilCurves.lead(p, RecoilCurves.uPitch(SEED, shot), RecoilCurves.uYaw(p, SEED, shot), t, fPitch, fYaw, out);
    }

    private static void flip(RecoilProfile p, int shot, float t, float[] out) {
        RecoilCurves.flip(p, RecoilCurves.uPitch(SEED, shot), RecoilCurves.uYaw(p, SEED, shot), t, 1, 1, out);
    }

    private static void shot(RecoilRing ring, RecoilProfile p, double now, float fPitch, float fYaw, float gain) {
        ring.shot(p, SEED, now, fPitch, fYaw, gain, 0.4F);
    }

    @Test
    void curveIsPiecewiseLinearAndHoldsItsEnds() {
        assertEquals(0, RecoilCurves.curve(M4.pitch(), 0.3F, 0));
        assertEquals(0.4F, RecoilCurves.curve(M4.pitch(), 0.3F, 0.025F), 1e-6);
        assertEquals(0.8F + (3.95F - 0.8F) * 0.5F, RecoilCurves.curve(M4.pitch(), 0.3F, 0.525F), 1e-5);
        assertEquals(3.95F, RecoilCurves.curve(M4.pitch(), 0.3F, 5), 1e-6);
        assertEquals(-3.16F + 6.32F * 0.25F, RecoilCurves.curve(M4.yaw(), 0.25F, 9), 1e-5, "u lerps [min, max]");
    }

    @Test
    void kickClimbsLeadsAndSettles() {
        float[] k = new float[2], tot = new float[2], lead = new float[2];
        total(M4, 7, 1, 1, tot);
        assertEquals(-3.95F, tot[1], 1e-5, "muzzle climb = negative MC pitch");
        for (float t : new float[] {0, 0.02F, 0.3F, 2}) {
            kick(M4, 7, t, k);
            lead(M4, 7, t, 1, 1, lead);
            assertEquals(tot[0], k[0] + lead[0], 1e-5);
            assertEquals(tot[1], k[1] + lead[1], 1e-5);
        }
        assertEquals(0, lead[1], 1e-6, "settled");
        total(M4, 8, 1, 1, k);
        assertNotEquals(tot[0], k[0], "per-shot yaw draw");
        total(M4, 7, 1, 1, k);
        assertArrayEquals(tot, k, "shot draw deterministic");
    }

    @Test
    void ringDeliversTheSingleKeyKick() {
        RecoilRing rc = new RecoilRing();
        float[] d = new float[2], lead = new float[2], tot = new float[2];
        shot(rc, STEP, 1, 1, 1, 1);
        rc.lead(lead);
        total(STEP, 0, 1, 1, tot);
        assertArrayEquals(tot, lead, 1e-6F, "before any step the weapon carries all of it");
        rc.step(STEP, 1.01F, d);
        assertTrue(tot[1] <= -2, "pitch kick >= 2 deg up");
        assertArrayEquals(tot, d, 1e-6F);
        rc.lead(lead);
        assertArrayEquals(new float[] {0, 0}, lead, 1e-6F);
    }

    @Test
    void ringPlusLeadIsTotalAtEveryFrame() {
        float[] d = new float[2], lead = new float[2], tot = new float[2], cam = new float[2];
        RecoilProfile slow = with(M4, 100, 0, 1, 1, RecoilProfile.Flip.NONE);
        RecoilRing noRecover = new RecoilRing();
        shot(noRecover, slow, 0, 1, 1, 1);
        total(slow, 0, 1, 1, tot);
        for (int f = 1; f <= 36; f++) {
            noRecover.step(slow, f / 144F, d);
            cam[0] += d[0];
            cam[1] += d[1];
            noRecover.lead(lead);
            assertEquals(tot[0], cam[0] + lead[0], 1e-5, "frame " + f);
            assertEquals(tot[1], cam[1] + lead[1], 1e-5, "frame " + f);
        }
        assertTrue(Math.abs(lead[1]) > 0.1F, "curve still rising at 0.25 s => lead nonzero");
    }

    /** One shot: first lobe peaks at peakPitch, overshoots negative, decays; roll weight within 1 +- jitter x peak. */
    @Test
    void kickSpringPeaksOvershootsAndSettles() {
        RecoilRing rc = new RecoilRing();
        shot(rc, M4, 10, 0.8F, 0.6F, 1);
        float peak = RecoilCurves.peakPitch(M4, RecoilCurves.uPitch(SEED, 0), 0.8F);
        float omega = 35, zeta = 0.45F, b = omega * (float) Math.sqrt(1 - zeta * zeta);
        float tPeak = (float) Math.atan2(b, zeta * omega) / b;
        assertEquals(peak, rc.spring(10 + tPeak, RecoilRing.PITCH, omega, zeta), 1e-4F);
        assertTrue(rc.spring(10 + tPeak + (float) Math.PI / b, RecoilRing.PITCH, omega, zeta) < -0.1F * peak,
                "overshoot");
        assertEquals(0, rc.spring(11, RecoilRing.PITCH, omega, zeta), 1e-3F * peak);
        float roll = rc.spring(10 + tPeak, RecoilRing.ROLL, omega, zeta);
        assertTrue(roll >= 0.6F * peak - 1e-4F && roll <= 1.4F * peak + 1e-4F, "roll " + roll);
        assertEquals(0, rc.spring(10 + tPeak, RecoilRing.YAW, omega, zeta));
    }

    @Test
    void visualGainScalesSpringOnly() {
        RecoilRing a = new RecoilRing(), b = new RecoilRing();
        shot(a, M4, 10, 0.8F, 0.6F, 1);
        shot(b, M4, 10, 0.8F, 0.6F, 2.5F);
        assertEquals(2.5F * a.spring(10.04F, RecoilRing.PITCH, 35, 0.45F), b.spring(10.04F, RecoilRing.PITCH, 35, 0.45F), 1e-5F);
        assertEquals(2.5F * a.spring(10.04F, RecoilRing.ROLL, 22, 0.35F), b.spring(10.04F, RecoilRing.ROLL, 22, 0.35F), 1e-5F);
        float[] la = new float[2], lb = new float[2];
        a.leadAt(M4, 10.05F, la);
        b.leadAt(M4, 10.05F, lb);
        assertEquals(la[1], lb[1], 0);
    }

    @Test
    void wanderSmoothsYawAndLeavesPitch() {
        RecoilProfile w = with(M4, 0, 8, 8, 1, RecoilProfile.Flip.NONE);
        float[] a = new float[2], b = new float[2], c = new float[2], d = new float[2];
        float steps = 0, steps0 = 0;
        for (int i = 0; i < 64; i++) {
            total(w, i, 1, 1, a);
            total(w, i + 1, 1, 1, b);
            total(M4, i, 1, 1, c);
            total(M4, i + 1, 1, 1, d);
            steps += Math.abs(b[0] - a[0]);
            steps0 += Math.abs(d[0] - c[0]);
            assertEquals(c[1], a[1], 0);
        }
        assertTrue(steps < 0.5F * steps0, steps + " vs " + steps0);
    }

    @Test
    void pullFractionLeavesTheRestOfTheCameraOffset() {
        RecoilProfile full = with(M4, 0, 8, 1, 1, RecoilProfile.Flip.NONE);
        RecoilProfile half = with(M4, 0, 8, 1, 0.5F, RecoilProfile.Flip.NONE);
        float[] o = new float[2], total = new float[2];
        float[] sums = new float[2];
        int n = 0;
        for (RecoilProfile r : List.of(full, half)) {
            RecoilRing rc = new RecoilRing();
            shot(rc, r, 0, 1, 1, 1);
            for (float t = 0.05F; t < 20; t += 0.05F) {
                rc.step(r, t, o);
                sums[n] += o[1];
            }
            n++;
        }
        total(full, 0, 1, 1, total);
        assertEquals(0, sums[0], 1e-3F);
        assertEquals(0.5F * total[1], sums[1], 1e-2F);
    }

    @Test
    void flipLiftsTheWeaponNotTheCamera() {
        RecoilProfile plain = with(M4, 0, 8, 1, 1, RecoilProfile.Flip.NONE), flipped = with(M4, 0, 8, 1, 1, FLIP);
        float[] a = new float[2], b = new float[2];
        for (float t : new float[] {0, 0.01F, 0.03F, 0.1F, 0.4F}) {
            kick(plain, 0, t, a);
            kick(flipped, 0, t, b);
            assertArrayEquals(a, b, 0);
        }
        lead(plain, 0, 0.03F, 1, 1, a);
        lead(flipped, 0, 0.03F, 1, 1, b);
        assertTrue(b[1] - a[1] < -2.5F && b[1] - a[1] > -3.5F, "peak up at attack: " + (b[1] - a[1]));
        assertTrue(Math.abs(b[0] - a[0]) <= 0.5F + 1e-5F);
        lead(plain, 0, 0.4F, 1, 1, a);
        lead(flipped, 0, 0.4F, 1, 1, b);
        assertEquals(a[1], b[1], 0.01F);
        lead(plain, 0, 0, 1, 1, a);
        lead(flipped, 0, 0, 1, 1, b);
        assertEquals(a[1], b[1], 1e-6F);
    }

    @Test
    void flipAtSumsActiveShots() {
        RecoilProfile flipped = with(M4, 0, 8, 1, 1, FLIP);
        RecoilRing rc = new RecoilRing();
        shot(rc, flipped, 1, 1, 1, 1);
        shot(rc, flipped, 1.05F, 1, 1, 1);
        float[] viaRing = new float[2], expected = new float[2];
        rc.flipAt(flipped, 1.08F, viaRing);
        flip(flipped, 0, 0.08F, expected);
        flip(flipped, 1, 0.03F, expected);
        assertArrayEquals(expected, viaRing, 1e-3F);
    }

    @Test
    void leadAtShotTimeMatchesTheCurve() {
        RecoilRing rc = new RecoilRing();
        shot(rc, M4, 10, 0.8F, 0.6F, 1);
        float[] actual = new float[2], expected = new float[2], frame = new float[2];
        rc.step(M4, 10.01F, frame);
        rc.leadAt(M4, 10.08F, actual);
        lead(M4, 0, 0.08F, 0.8F, 0.6F, expected);
        assertArrayEquals(expected, actual, 1e-5F);
    }

    @Test
    void resetDropsResidualAndRestartsShotIndex() {
        RecoilProfile r = with(STEP, 0, 8, 1, 1, RecoilProfile.Flip.NONE);
        RecoilRing rc = new RecoilRing();
        float[] d = new float[2], tot = new float[2];
        shot(rc, r, 0, 1, 1, 1);
        shot(rc, r, 0, 1, 1, 1);
        rc.step(r, 0.01F, d);
        rc.reset();
        assertEquals(0, rc.shots());
        shot(rc, r, 5, 1, 1, 1);
        rc.step(r, 5.01F, d);
        total(r, 0, 1, 1, tot);
        float f = RecoilCurves.recovery(r, 0, 0.01F);
        assertEquals(tot[1] * f, d[1], 1e-5, "recovery on this shot only, shot index 0 again");
        assertEquals(tot[0] * f, d[0], 1e-5);
    }

    @Test
    void recoveryWaitsOutItsDelay() {
        assertEquals(1, RecoilCurves.recovery(M4, 0, 0.03F), "inside delay");
        assertEquals((float) Math.exp(-8.68 * 0.01), RecoilCurves.recovery(M4, 0, 0.05F), 1e-6, "straddles delay");
        assertEquals((float) Math.exp(-8.68 * 0.05), RecoilCurves.recovery(M4, 1, 0.05F), 1e-6);
    }

    @Test
    void zeroFactorShotsCarrySpringOnly() {
        RecoilRing rc = new RecoilRing();
        rc.shot(M4, 0, 0.5F, 0.5F, 0, 0, 0.3F, 0.1F, 0.2F);
        float[] d = new float[2], sum = new float[2];
        for (int f = 1; f <= 100; f++) {
            rc.step(M4, f * 0.01, d);
            sum[0] += d[0];
            sum[1] += d[1];
        }
        assertArrayEquals(new float[] {0, 0}, sum, 0);
        assertTrue(rc.spring(0.03, RecoilRing.YAW, 28, 0.35F) > 0);
    }
}

package com.wf.wflib.client.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForearmTwistTest {
    private static final float DT = 1 / 60F, E = 1e-4F;

    private static float rad(float deg) {
        return (float) Math.toRadians(deg);
    }

    /** Hand rolls {@code from} -> {@code deg} at {@code degPerS}. */
    private static void roll(ForearmTwist t, float from, float deg, float degPerS) {
        t.update(rad(from), DT);
        int n = (int) Math.ceil(Math.abs(deg - from) / degPerS / DT);
        for (int i = 1; i <= n; i++) {
            t.update(rad(from + (deg - from) * i / n), DT);
        }
    }

    private static void hold(ForearmTwist t, float deg, int frames) {
        for (int i = 0; i < frames; i++) {
            t.update(rad(deg), DT);
        }
    }

    @Test
    void movingRollStaysContinuousPastFortyFive() {
        ForearmTwist t = new ForearmTwist();
        roll(t, 0, 85, 400);
        assertEquals(rad(85), t.roll(), E);
        assertEquals(0, t.base(), E);
    }

    @Test
    void stillWindUpTurnsTheArmNotTheFist() {
        ForearmTwist t = new ForearmTwist();
        roll(t, 0, 98, 400);
        hold(t, 98, 30);
        assertEquals(rad(98), t.roll(), E, "fist stays on the hand (TACZ M4A1 inspect end)");
        assertEquals(rad(90), t.base(), E, "corkscrew 8 deg at rest");
    }

    @Test
    void pistolReloadHoldKeepsTheFist() {
        ForearmTwist t = new ForearmTwist();
        roll(t, -10, 110, 130);
        hold(t, 110, 60);
        assertEquals(rad(110), t.roll(), E, "TACZ glock_17 reloadEmpty hold: no fist spin");
        assertTrue(Math.abs(t.roll() - t.base()) <= rad(45) + E, "corkscrew " + Math.toDegrees(t.roll() - t.base()));
    }

    @Test
    void adsDriftPastFortyFiveIsKept() {
        ForearmTwist t = new ForearmTwist();
        roll(t, -43, -55, 100);
        hold(t, -55, 60);
        assertEquals(rad(-55), t.roll(), E);
        assertEquals(0, t.base(), E, "TACZ left hand hip -> ADS: no arm turn");
    }

    @Test
    void turnIsRateLimited() {
        ForearmTwist t = new ForearmTwist();
        roll(t, 0, 98, 400);
        t.update(rad(98), DT);
        float before = t.base();
        t.update(rad(98), DT);
        assertTrue(Math.abs(t.base() - before) <= ForearmTwist.RELAX * DT + E, "no pop while turning");
    }

    @Test
    void firstPoseStartsSmall() {
        ForearmTwist t = new ForearmTwist();
        t.update(rad(170), DT);
        assertEquals(rad(-10), t.roll(), E);
    }

    @Test
    void fastRollKeepsTheCorkscrewBounded() {
        ForearmTwist t = new ForearmTwist();
        t.update(0, DT);
        float prev = 0, max = 0;
        int n = (int) (200 / 2000F / DT);
        for (int i = 1; i <= n + 30; i++) {
            t.update(rad(200F * Math.min(i, n) / n), DT);
            assertTrue(Math.abs(t.base() - prev) < rad(60), "arm jump " + Math.toDegrees(t.base() - prev));
            prev = t.base();
            max = Math.max(max, Math.abs(t.roll() - t.base()));
        }
        assertTrue(max < rad(170), "peak " + Math.toDegrees(max));
        assertTrue(Math.abs(t.roll() - t.base()) <= rad(45) + E, "rest " + Math.toDegrees(t.roll() - t.base()));
    }
}

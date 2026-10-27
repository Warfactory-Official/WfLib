package com.wf.wflib.client.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForearmTwistTest {
    private static final float DT = 1 / 60F, E = 1e-4F, LIMIT = rad(120);

    private static float rad(float deg) {
        return (float) Math.toRadians(deg);
    }

    /** Twist -> {@code deg} over {@code frames}, then held {@code hold} frames; asserts no whole-arm roll throughout. */
    private static float to(ForearmTwist t, float from, float deg, int frames, int hold) {
        float max = 0;
        for (int i = 1; i <= frames + hold; i++) {
            t.update(rad(from + (deg - from) * Math.min(i, frames) / frames), 0, LIMIT, DT);
            max = Math.max(max, Math.abs(t.base()));
        }
        return max;
    }

    @Test
    void sprintToAdsKeepsTheArm() {
        ForearmTwist t = new ForearmTwist();
        t.update(rad(37), 0, LIMIT, DT);
        float max = to(t, 37, 70, 18, 60);
        max = Math.max(max, to(t, 70, -40, 15, 90));
        assertEquals(0, max, E, "SPAS right: hip 37 -> STALKER sprint 59..77 -> ADS -40 (old: arm turned +90, -90)");
        assertEquals(rad(-40), t.roll(), E);
    }

    @Test
    void hipToAdsSwingStaysInTheForearm() {
        ForearmTwist t = new ForearmTwist();
        t.update(rad(53), 0, LIMIT, DT);
        assertEquals(0, to(t, 53, -63, 15, 90), E, "right hip -> ADS p95 / p5 over every default gun");
    }

    @Test
    void pistolInspectRangeStaysInTheForearm() {
        ForearmTwist t = new ForearmTwist();
        t.update(rad(27), 0, LIMIT, DT);
        float max = to(t, 27, -67, 40, 20);
        max = Math.max(max, to(t, -67, 118, 60, 20));
        max = Math.max(max, to(t, 118, 27, 40, 20));
        assertEquals(0, max, E, "G17 inspect right -67..118 (old: 720 deg of arm turns)");
    }

    @Test
    void excessPastTheLimitRollsTheWholeArm() {
        ForearmTwist t = new ForearmTwist();
        t.update(0, 0, LIMIT, DT);
        to(t, 0, 150, 30, 10);
        assertEquals(rad(150), t.roll(), E, "fist on the hand");
        assertEquals(rad(30), t.base(), E, "excess 30 deg past 120");
    }

    @Test
    void rollUnwrapsAcrossAHalfTurn() {
        ForearmTwist t = new ForearmTwist();
        t.update(rad(170), 0, LIMIT, DT);
        t.update(rad(179), 0, LIMIT, DT);
        t.update(rad(-179), 0, LIMIT, DT);
        assertEquals(rad(181), t.roll(), E);
    }

    @Test
    void firstPoseStartsWithinAHalfTurn() {
        ForearmTwist t = new ForearmTwist();
        t.update(rad(200), 0, LIMIT, DT);
        assertEquals(rad(-160), t.roll(), E);
    }

    @Test
    void windingUnwindsWithoutJumps() {
        ForearmTwist t = new ForearmTwist();
        t.update(0, 0, LIMIT, DT);
        float prev = 0;
        int n = 120;
        for (int i = 1; i <= n + 120; i++) {
            float twist = 400F * Math.min(i, n) / n;
            t.update(rad(twist), 0, LIMIT, DT);
            assertEquals(rad(twist), t.roll(), E, "fist exact");
            float step = Math.abs(t.base() - prev);
            assertTrue(step <= ForearmTwist.UNWIND * DT + rad(400F / n) + E, "arm jump " + Math.toDegrees(step));
            prev = t.base();
        }
        assertEquals(rad(40), t.roll() - t.base(), E, "Sten reloadEmpty-style 400 deg spin: one turn unwound");
        assertEquals(rad(360), t.base(), E);
    }

    @Test
    void undefinedTwistNearReversedHandIsIgnored() {
        ForearmTwist t = new ForearmTwist();
        t.update(rad(20), 0, LIMIT, DT);
        for (int i = 0; i < 60; i++) {
            t.update(rad(i % 2 == 0 ? 120 : -100), rad(170), LIMIT, DT);
        }
        assertEquals(rad(20), t.roll(), E, "swing 170: twist flips frame to frame, roll held");
        assertEquals(0, t.base(), E);
        t.update(rad(60), rad(120), LIMIT, DT);
        assertEquals(rad(40), t.roll(), E, "swing 120: half confidence");
    }
}

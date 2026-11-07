package com.wf.wflib.soldier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ThrowPitchTest {
    /** Same integration as ThrownCharge: returns where the arc thrown at {@code pitch} comes down to {@code dy}. */
    private static double landing(float pitch, double dy, double speed) {
        double r = Math.toRadians(-pitch), vx = Math.cos(r) * speed, vy = Math.sin(r) * speed, x = 0, y = 0;
        for (int t = 0; t < 200 && !(vy < 0 && y < dy); t++) {
            x += vx;
            y += vy;
            vx *= 0.995;
            vy = vy * 0.995 - 0.024525;
        }
        return x;
    }

    @Test
    void arcsLandOnTargetAndPreferALob() {
        for (double dist : new double[]{8, 14, 20, 26}) {
            for (double dy : new double[]{-1.5, 0, 3}) {
                float pitch = SoldierAi.throwPitch(dist, dy, 0.9);
                assertTrue(Math.abs(landing(pitch, dy, 0.9) - dist) < 0.8, dist + "/" + dy + " pitch " + pitch);
                if (dist >= 14) assertTrue(pitch <= -20, "lob expected at " + dist + ": " + pitch);   // negative = up
            }
        }
    }
}

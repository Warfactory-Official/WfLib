package com.wf.wflib.fire.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireDriftTest {

    @Test
    void sinksToTerminalNeverFaster() {
        double[] v = new double[3], w = new double[2];
        for (int t = 0; t < 100; t++) {
            FireDrift.step(t, 0, 0, v, w);
            assertTrue(v[1] >= -FireDrift.TERMINAL - 1e-12 && v[1] < 0.0, "t " + t + " vy " + v[1]);
        }
        assertEquals(-FireDrift.TERMINAL, v[1], 1e-12);
    }

    @Test
    void windStaysInTheGustRange() {
        double[] w = new double[2];
        for (long t = 0; t < 20000; t += 7) {
            FireDrift.wind(t, t * 0.3, -t * 0.2, w);
            double speed = Math.hypot(w[0], w[1]);
            assertTrue(speed >= 0.015 - 1e-9 && speed <= 0.055 + 1e-9, "t " + t + " speed " + speed);
        }
    }

    @Test
    void sameInputsSameStep() {
        double[] a = new double[3], b = new double[3], w = new double[2];
        for (int t = 0; t < 50; t++) {
            FireDrift.step(1000 + t, 12.5, -3.25, a, w);
            FireDrift.step(1000 + t, 12.5, -3.25, b, w);
        }
        assertEquals(a[0], b[0], 0.0);
        assertEquals(a[2], b[2], 0.0);
    }
}

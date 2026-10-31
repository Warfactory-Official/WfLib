package com.wf.wflib.artifact.detect;

import org.junit.jupiter.api.Test;
import com.wf.wflib.artifact.api.DetectorTier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectorTierTest {
    @Test
    void beepsFasterAsItNears() {
        for (DetectorTier t : DetectorTier.values()) {
            assertEquals(DetectorTier.BEEP_NEAR, t.beepInterval(0.0D), 1e-9);
            assertEquals(DetectorTier.BEEP_FAR, t.beepInterval(t.radius), 1e-9);
            assertEquals(DetectorTier.BEEP_FAR, t.beepInterval(t.radius * 3), 1e-9);
            double last = 0.0D;
            for (double d = 0.0D; d <= t.radius; d += 1.0D) {
                double i = t.beepInterval(d);
                assertTrue(i >= last, t + " at " + d);
                last = i;
            }
        }
    }

    @Test
    void betterDetectorsFindMoreAndShowFarther() {
        DetectorTier[] t = DetectorTier.values();
        for (int i = 1; i < t.length; i++) {
            assertTrue(t[i].rank > t[i - 1].rank);
            assertTrue(t[i].reveal > t[i - 1].reveal);
            assertTrue(t[i].reveal < t[i].radius);
        }
    }
}

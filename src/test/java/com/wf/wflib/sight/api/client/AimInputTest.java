package com.wf.wflib.sight.api.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimInputTest {
    /** Key down for ticks {@code [from, from + len)}, up at {@code from + len}; returns the next tick. */
    static int press(AimInput in, int from, int len) {
        for (int t = from; t < from + len; t++) {
            in.tick(true, t);
        }
        in.tick(false, from + len);
        return from + len + 1;
    }

    @Test
    void tapTogglesOnRelease() {
        AimInput in = new AimInput();
        in.tick(true, 0);
        assertFalse(in.ads, "not on press");
        in.tick(false, 1);
        assertTrue(in.ads, "hip -> ADS");
        press(in, 5, AimInput.HOLD_TICKS);
        assertFalse(in.ads, "ADS -> hip at the longest tap");
    }

    @Test
    void holdZoomsAndKeepsAds() {
        for (boolean start : new boolean[]{false, true}) {
            AimInput in = new AimInput();
            in.ads = start;
            for (int t = 0; t < 20; t++) {
                in.tick(true, t);
                assertEquals(t >= AimInput.HOLD_TICKS, in.hold, "hold at " + t);
                assertEquals(start, in.ads);
            }
            in.tick(false, 20);
            assertFalse(in.hold, "zoom released");
            assertEquals(start, in.ads, "ADS unchanged after a hold");
        }
    }

    @Test
    void cancelAndOff() {
        AimInput in = new AimInput();
        press(in, 0, 1);
        in.tick(true, 3);
        in.cancel();
        in.tick(false, 4);
        assertTrue(in.ads, "cancelled press: no toggle");
        for (int t = 5; t < 12; t++) {
            in.tick(true, t);
        }
        assertTrue(in.hold);
        in.off();
        assertFalse(in.ads || in.hold);
        in.tick(false, 12);
        assertFalse(in.ads, "release after off: no toggle");
    }

    @Test
    void sprintGate() {
        AimInput in = new AimInput();
        assertTrue(in.sprintAllowed(0));
        assertFalse(in.sprintAllowed(0.1F), "ADS ramping out");
        in.ads = true;
        assertFalse(in.sprintAllowed(0), "wanted, not yet raised");
        assertFalse(in.sprintAllowed(1));
        in.hold = true;
        in.ads = false;
        assertTrue(in.sprintAllowed(0), "hip zoom keeps sprint");
    }

    @Test
    void holdZoomRange() {
        assertEquals(1, AimInput.zoom(0));
        assertEquals(AimInput.HOLD_ZOOM, AimInput.zoom(1), 1e-6);
        assertTrue(AimInput.HOLD_ZOOM >= 1.3F && AimInput.HOLD_ZOOM <= 1.5F);
    }
}

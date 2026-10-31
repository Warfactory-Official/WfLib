package com.wf.wflib.physics.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Standing rider (feet 0, head 1.8) under a hull box overhanging from +x (bottom 1.5): shallowest axis = down. */
class DeckClipTest {
    private static final float[] OVERHANG = {-0.2f, 1.5f, -2, 2, 3, 2};

    private static double[] push(boolean mayDescend) {
        double[] out = new double[3];
        DeckClip.depenetrate(OVERHANG, 1, 0, 0.9, 0, 0.3, 0.9, 0.3, mayDescend, 4, out);
        return out;
    }

    @Test
    void airborneRiderIsPushedDown() {
        double[] out = push(true);
        assertEquals(-0.3, out[1], 1e-5);
        assertEquals(0, out[0]);
    }

    @Test
    void groundedRiderIsPushedSidewaysNotIntoTheGround() {
        double[] out = push(false);
        assertEquals(0, out[1]);
        assertEquals(-0.5, out[0], 1e-5);
    }
}

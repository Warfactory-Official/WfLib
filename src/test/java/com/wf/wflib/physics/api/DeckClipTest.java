package com.wf.wflib.physics.api;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckClipTest {
    /** Standing rider (feet 0, head 1.8) under a hull box overhanging from +x (bottom 1.5): shallowest axis = down. */
    private static final float[] OVERHANG = {-0.2f, 1.5f, -2, 2, 3, 2};
    private static final float[] OVERHANG_FRAME = {0.9f, 2.25f, 0, 1.1f, 0.75f, 2, 0, 0, 0, 1};

    /** Ramp slab 4 long (z), 0.2 thick, pitched 30 deg: high end at z<0, lip at z>0 on y=0. */
    private static final double SLOPE = Math.toRadians(30);
    private static final float[] RAMP_FRAME = new float[DeckSnapshot.FRAME];
    private static final float[] RAMP = new float[6];

    static {
        Quaternionf q = new Quaternionf().rotateX((float) SLOPE);
        RAMP_FRAME[0] = 0;
        RAMP_FRAME[1] = (float) (Math.sin(SLOPE) * 2 - 0.1);
        RAMP_FRAME[2] = 0;
        RAMP_FRAME[3] = 1;
        RAMP_FRAME[4] = 0.1f;
        RAMP_FRAME[5] = 2;
        RAMP_FRAME[6] = q.x;
        RAMP_FRAME[7] = q.y;
        RAMP_FRAME[8] = q.z;
        RAMP_FRAME[9] = q.w;
        // Carrier-frame AABB of the slab, as AbstractVehicle publishes it.
        double ry = Math.cos(SLOPE) * 0.1 + Math.sin(SLOPE) * 2;
        double rz = Math.sin(SLOPE) * 0.1 + Math.cos(SLOPE) * 2;
        RAMP[0] = -1;
        RAMP[1] = (float) (RAMP_FRAME[1] - ry);
        RAMP[2] = (float) -rz;
        RAMP[3] = 1;
        RAMP[4] = (float) (RAMP_FRAME[1] + ry);
        RAMP[5] = (float) rz;
    }

    /** Slab top surface height at z (frame centre on the ramp's mid-line). */
    private static double rampTop(double z) {
        return RAMP_FRAME[1] - Math.tan(SLOPE) * z + 0.1 / Math.cos(SLOPE);
    }

    private static double[] push(boolean mayDescend) {
        double[] out = new double[3];
        DeckClip.depenetrate(OVERHANG, OVERHANG_FRAME, 1, 0, 0.9, 0, 0.3, 0.9, 0.3, mayDescend, 4, out);
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

    @Test
    void riderFallsOntoTheSlopeNotTheBoundingBoxTop() {
        // Feet 0.5 above the slab at z=1; AABB top is ~1.0 higher than the surface there.
        double feet = rampTop(1) + 0.5;
        double got = DeckClip.clipY(RAMP, RAMP_FRAME, 1, 0, feet + 0.9, 1, 0.3, 0.9, 0.3, -2);
        assertTrue(feet + got > RAMP[4] - 2, "fell through");
        // Uphill box edge (z = 0.7) lands.
        assertEquals(rampTop(0.7), feet + got, 1e-4);
    }

    @Test
    void walkingUphillStepsAlongTheSlope() {
        double feet = rampTop(0.7) + 2 * DeckClip.EPSILON;
        double[] out = new double[3];
        DeckClip.sweep(RAMP, RAMP_FRAME, 1, 0, feet + 0.9, 1, 0.3, 0.9, 0.3,
                0, -0.08, -0.2, 0, -0.2, 0.6, true, out);
        assertEquals(-0.2, out[2], 1e-9);
        assertTrue(out[1] > 0, "climbed " + out[1]);
    }

    @Test
    void underTheRaisedEndIsOpen() {
        // Rider 0.9 tall on the ground under the high end: inside the AABB, clear of the slab.
        assertFalse(DeckClip.anyBoxNear(RAMP, RAMP_FRAME, 1, 0, 0.45, -1.5, 0.3, 0.45, 0.3, 0, 0, 0));
        double got = DeckClip.clipZ(RAMP, RAMP_FRAME, 1, 0, 0.45, -1.5, 0.3, 0.45, 0.3, 0.2);
        assertEquals(0.2, got, 0);
    }
}

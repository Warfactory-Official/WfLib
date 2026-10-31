package com.wf.wflib.physics.rigid;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvexContactsTest {

    private static final float MARGIN = 0.1f;

    private final ConvexContacts narrow = new ConvexContacts();
    private final ContactBuffer out = new ContactBuffer();
    private final Polytope ground = new Polytope().box(-5, -1, -5, 5, 0, 5);
    private final Polytope body = new Polytope();

    @Test
    void aBoxOnTheGroundTouchesOnItsFourBottomCorners() {
        body.box(0, 0.499f, 0, new Quaternionf(), 0.5f, 0.5f, 0.5f);
        assertEquals(4, narrow.collide(ground, body, MARGIN, out));
        for (int k = 0; k < out.size(); k++) {
            assertEquals(1, out.normalY(k), 1.0e-6, "a -> b");
            assertEquals(-0.001, out.separation(k), 1.0e-5);
        }
    }

    @Test
    void aBoxBeyondTheMarginMakesNoContact() {
        body.box(0, 0.5f + MARGIN + 0.01f, 0, new Quaternionf(), 0.5f, 0.5f, 0.5f);
        assertEquals(0, narrow.collide(ground, body, MARGIN, out));
    }

    @Test
    void aSpeculativeGapIsPositive() {
        body.box(0, 0.55f, 0, new Quaternionf(), 0.5f, 0.5f, 0.5f);
        assertTrue(narrow.collide(ground, body, MARGIN, out) > 0);
        for (int k = 0; k < out.size(); k++) {
            assertEquals(0.05, out.separation(k), 1.0e-5);
        }
    }

    @Test
    void aBoxStoodOnACornerMakesOnePointUnderItself() {
        Quaternionf upright = new Quaternionf().rotationTo(1, 1, 1, 0, 1, 0);
        float height = 0.5f * (float) Math.sqrt(3) - 0.002f;
        body.box(0.25f, height, -0.25f, upright, 0.5f, 0.5f, 0.5f);
        assertEquals(1, narrow.collide(ground, body, MARGIN, out));
        assertEquals(1, out.normalY(0), 1.0e-5);
        assertEquals(-0.002, out.separation(0), 1.0e-4);
        assertEquals(0.25, out.x(0), 1.0e-4);
        assertEquals(-0.25, out.z(0), 1.0e-4);
    }

    @Test
    void crossedRidgesMakeOneEdgeContact() {
        float ridge = 0.1f * (float) Math.sqrt(2);
        Polytope lower = new Polytope().box(0, 0, 0, new Quaternionf().rotateZ((float) Math.PI / 4), 0.1f, 0.1f, 2);
        body.box(0, 2 * ridge - 0.01f, 0, new Quaternionf().rotateX((float) Math.PI / 4), 2, 0.1f, 0.1f);
        assertEquals(1, narrow.collide(lower, body, MARGIN, out));
        assertEquals(1, Math.abs(out.normalY(0)), 1.0e-4);
        assertEquals(-0.01, out.separation(0), 1.0e-4);
        assertEquals(0, out.x(0), 1.0e-4);
        assertEquals(0, out.z(0), 1.0e-4);
    }

    @Test
    void collisionIsDeterministicAcrossRepeatedCalls() {
        body.box(0.1f, 0.49f, 0.2f, new Quaternionf().rotateY(0.3f), 0.5f, 0.5f, 0.5f);
        narrow.collide(ground, body, MARGIN, out);
        int n = out.size();
        float[] first = new float[n * 7];
        for (int k = 0; k < n; k++) {
            first[k * 7] = out.x(k);
            first[k * 7 + 1] = out.y(k);
            first[k * 7 + 2] = out.z(k);
            first[k * 7 + 3] = out.normalX(k);
            first[k * 7 + 4] = out.normalY(k);
            first[k * 7 + 5] = out.normalZ(k);
            first[k * 7 + 6] = out.separation(k);
        }
        out.clear();
        assertEquals(n, narrow.collide(ground, body, MARGIN, out));
        for (int k = 0; k < n; k++) {
            assertEquals(Float.floatToRawIntBits(first[k * 7]), Float.floatToRawIntBits(out.x(k)));
            assertEquals(Float.floatToRawIntBits(first[k * 7 + 6]), Float.floatToRawIntBits(out.separation(k)));
        }
    }
}

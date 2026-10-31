package com.wf.wflib.physics.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Section (0, 0, 0); ground = blocks y 0..1 past the padded section. */
class SurfaceBakeTest {

    private static BoxBuffer ground() {
        BoxBuffer boxes = new BoxBuffer();
        boxes.add(-8, 0, -8, 24, 1, 24);
        return boxes;
    }

    /** Highest triangle over (px, pz), NaN none. */
    private static double height(SurfaceSection s, double px, double pz) {
        double best = Double.NaN;
        int x = (int) Math.floor(px), z = (int) Math.floor(pz);
        for (int t = s.first(x, z); t < s.end(x, z); t++) {
            double h = s.height(t, px, pz);
            if (!Double.isNaN(h) && !(h <= best)) {
                best = h;
            }
        }
        return best;
    }

    @Test
    void flatGroundHasNoHill() {
        assertEquals(0, SurfaceBake.bake(0, 0, 0, ground()).triangleCount());
    }

    @Test
    void oneBlockStepBecomesAnSCurveCuttingAndFillingHalfABlock() {
        BoxBuffer boxes = ground();
        boxes.add(8, 1, -8, 24, 2, 24);
        SurfaceSection s = SurfaceBake.bake(0, 0, 0, boxes);
        // Corners x 6..10: 1, 1.125, 1.5, 1.875, 2 (1-2-1 blur, corner = mean of its columns).
        double[] corner = {1, 1.125, 1.5, 1.875, 2};
        for (double x = 6.05; x < 10; x += 0.1) {
            int i = (int) Math.floor(x) - 6;
            double expected = corner[i] + (corner[i + 1] - corner[i]) * (x - Math.floor(x));
            assertEquals(expected, height(s, x, 4.5), 1.0e-3, "x " + x);
        }
        assertTrue(s.covers(8.0, 1.5, 4.5, 2.0), "step face");
        assertTrue(s.covers(8.0, 2.0, 4.5, 2.0), "crest corner, cut 0.5");
        assertFalse(s.covers(8.5, 2.6, 4.5, 2.6), "above the hill");
    }

    @Test
    void twoBlockWallStaysSharp() {
        BoxBuffer boxes = ground();
        boxes.add(8, 1, -8, 24, 3, 24);
        SurfaceSection s = SurfaceBake.bake(0, 0, 0, boxes);
        assertEquals(0, s.triangleCount());
        assertFalse(s.covers(8.0, 2.5, 4.5, 3.0));
    }

    @Test
    void oneToOneStairsBecomeAPlaneThroughTheTreadCentres() {
        BoxBuffer boxes = ground();
        for (int x = 4; x < 24; x++) {
            boxes.add(x, 1, -8, x + 1, 1 + (x - 3), 24);
        }
        SurfaceSection s = SurfaceBake.bake(0, 0, 0, boxes);
        for (double x = 6.5; x < 14; x += 0.25) {
            // Tread x has top x - 2 at its centre: plane y = x - 2.5 + 0.5.
            assertEquals(x - 2.5, height(s, x, 4.5), 1.0e-3, "x " + x);
        }
    }

    @Test
    void lonePillarBecomesABump() {
        BoxBuffer boxes = ground();
        boxes.add(8, 1, 8, 9, 2, 9);
        SurfaceSection s = SurfaceBake.bake(0, 0, 0, boxes);
        assertTrue(s.triangleCount() > 0);
        float[] v = new float[9];
        for (int t = 0; t < s.triangleCount(); t++) {
            s.vertices(t, v);
            for (int i = 0; i < 9; i += 3) {
                assertTrue(v[i] >= 6 && v[i] <= 11 && v[i + 2] >= 6 && v[i + 2] <= 11, v[i] + "," + v[i + 2]);
            }
        }
        assertTrue(height(s, 8.5, 8.5) < 2 && height(s, 8.5, 8.5) > 1.4);
    }

    @Test
    void caveFloorStepUnderARoofBecomesTheSameHill() {
        BoxBuffer boxes = ground();
        boxes.add(8, 1, -8, 24, 2, 24);
        boxes.add(-8, 4, -8, 24, 6, 24);
        SurfaceSection s = SurfaceBake.bake(0, 0, 0, boxes);
        double[] corner = {1, 1.125, 1.5, 1.875, 2};
        for (double x = 6.05; x < 10; x += 0.1) {
            int i = (int) Math.floor(x) - 6;
            assertEquals(corner[i] + (corner[i + 1] - corner[i]) * (x - Math.floor(x)), height(s, x, 4.5), 1.0e-3, "x " + x);
        }
        float[] v = new float[9];
        for (int t = 0; t < s.triangleCount(); t++) {
            s.vertices(t, v);
            assertTrue(v[1] < 4 && v[4] < 4 && v[7] < 4, "flat roof top stays flat");
        }
    }

    @Test
    void lowRoofKeepsAStepFloorApart() {
        BoxBuffer boxes = ground();
        boxes.add(8, 1, -8, 24, 2, 24);
        // Floor 1 under a roof at 1.6 (x < 8) never meets the step top at 2: no shared air.
        boxes.add(-8, 1.6, -8, 8, 2, 24);
        assertEquals(0, SurfaceBake.bake(0, 0, 0, boxes).triangleCount());
    }

    @Test
    void ledgeUndersideIsCoveredOnlyFromAbove() {
        BoxBuffer boxes = ground();
        boxes.add(-8, 1, -8, 8, 4, 24);
        // One-block ledge (floor 5) over a cave (floor 1) beside floor 4.
        boxes.add(8, 4, -8, 24, 5, 24);
        SurfaceSection s = SurfaceBake.bake(0, 0, 0, boxes);
        assertTrue(s.covers(8.25, 4.0, 4.5, 4.7), "hull on the hill");
        assertFalse(s.covers(8.25, 4.0, 4.5, 3.9), "hull in the cave");
        assertTrue(Double.isNaN(height(s, 12.5, 4.5)) || height(s, 12.5, 4.5) > 4, "cave floor flat");
    }

    @Test
    void bakeTime() {
        BoxBuffer boxes = ground();
        for (int i = 0; i < 6; i++) {
            boxes.add(-6 + 4 * i, 1, -8, 24, 2 + i, 24);
        }
        for (int i = 0; i < 20000; i++) {
            SurfaceBake.bake(0, 0, 0, boxes);
        }
        long t = System.nanoTime();
        SurfaceSection s = null;
        for (int i = 0; i < 5000; i++) {
            s = SurfaceBake.bake(0, 0, 0, boxes);
        }
        System.out.println("[baketime] " + (System.nanoTime() - t) / 5000 / 1000.0 + " us, triangles " + s.triangleCount());
    }
}

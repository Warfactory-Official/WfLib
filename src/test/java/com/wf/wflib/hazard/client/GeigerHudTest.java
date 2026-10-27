package com.wf.wflib.hazard.client;

import org.junit.jupiter.api.Test;

import static com.wf.wflib.hazard.client.GeigerHud.DIGIT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GeigerHudTest {
    private static int[] masks(double v, int dp) {
        int[] m = new int[4];
        assertEquals(dp, GeigerHud.format(v, m), "point for " + v);
        return m;
    }

    @Test
    void autoranges() {
        assertArrayEquals(new int[]{DIGIT[0], DIGIT[0], DIGIT[0], DIGIT[0]}, masks(0, 0));
        assertArrayEquals(new int[]{DIGIT[1], DIGIT[2], DIGIT[3], DIGIT[4]}, masks(1.234, 0));
        assertArrayEquals(new int[]{DIGIT[1], DIGIT[0], DIGIT[0], DIGIT[0]}, masks(9.9996, 1));
        assertArrayEquals(new int[]{DIGIT[1], DIGIT[2], DIGIT[3], DIGIT[5]}, masks(123.45, 2));
        assertArrayEquals(new int[]{DIGIT[4], DIGIT[2], DIGIT[4], DIGIT[0]}, masks(42.4, 1));
        assertArrayEquals(new int[]{DIGIT[4], DIGIT[2], DIGIT[4], DIGIT[2]}, masks(4242, 3));
        assertArrayEquals(new int[]{0, GeigerHud.SEG_O, GeigerHud.SEG_L, 0}, masks(10000, 3));
        assertArrayEquals(new int[]{DIGIT[0], DIGIT[0], DIGIT[0], DIGIT[0]}, masks(-1, 0));
    }

    @Test
    void barsAreLogarithmic() {
        assertEquals(0, GeigerHud.bars(0, 14));
        assertEquals(1, GeigerHud.bars(0.01, 14));
        assertEquals(3, GeigerHud.bars(1, 14));
        assertEquals(14, GeigerHud.bars(1e6, 14));
    }
}

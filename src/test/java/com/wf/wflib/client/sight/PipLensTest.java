package com.wf.wflib.client.sight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PipLensTest {
    @Test
    void pictureFollowsTheLensOnScreenWithinTheCap() {
        assertEquals(1152, PipLens.pictureSize(1100, 0, 2048));
        assertEquals(2048, PipLens.pictureSize(3000, 1152, 2048));
        assertEquals(128, PipLens.pictureSize(10, 0, 2048));
        // shrinks only below 60 % of the current picture
        assertEquals(1152, PipLens.pictureSize(800, 1152, 2048));
        assertEquals(640, PipLens.pictureSize(600, 1152, 2048));
        // lowered cap applies at once
        assertEquals(512, PipLens.pictureSize(1100, 1152, 512));
    }
}

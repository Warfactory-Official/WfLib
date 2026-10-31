package com.wf.wflib.ui.api.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SkinPartTest {
    @Test
    void frontFaceRegionsOfTheSixtyFourSquareSkin() {
        assertArrayEquals(new int[]{8, 8, 8, 8}, SkinPart.Part.HEAD.region(false, false));
        assertArrayEquals(new int[]{40, 8, 8, 8}, SkinPart.Part.HEAD.region(true, true));
        assertArrayEquals(new int[]{20, 36, 8, 12}, SkinPart.Part.TORSO.region(false, true));
        assertArrayEquals(new int[]{44, 20, 4, 12}, SkinPart.Part.RIGHT_ARM.region(false, false));
        assertArrayEquals(new int[]{44, 36, 3, 12}, SkinPart.Part.RIGHT_ARM.region(true, true));
        assertArrayEquals(new int[]{36, 52, 3, 12}, SkinPart.Part.LEFT_ARM.region(true, false));
        assertArrayEquals(new int[]{52, 52, 4, 12}, SkinPart.Part.LEFT_ARM.region(false, true));
        assertArrayEquals(new int[]{4, 20, 4, 12}, SkinPart.Part.RIGHT_LEG.region(true, false));
        assertArrayEquals(new int[]{4, 52, 4, 12}, SkinPart.Part.LEFT_LEG.region(false, true));
    }
}

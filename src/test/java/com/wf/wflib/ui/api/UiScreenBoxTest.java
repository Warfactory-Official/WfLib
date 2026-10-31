package com.wf.wflib.ui.api;

import com.wf.wflib.ui.api.widget.Stack;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class UiScreenBoxTest {
    private static final UiLayout SHEET = new UiLayout(ResourceLocation.fromNamespaceAndPath("test", "sheet"),
            new Stack(List.of()), 0).withDesignSize(453, 152);

    @Test
    void designedAbsoluteLayoutCentresOnWholePixels() {
        float[] box = new float[4];
        UiScreen.box(641, 360, SHEET, true, box);
        assertArrayEquals(new float[]{94, 104, 453, 152}, box);
    }

    @Test
    void otherwiseTheWholeGui() {
        float[] box = new float[4];
        UiScreen.box(640, 360, SHEET, false, box);
        assertArrayEquals(new float[]{0, 0, 640, 360}, box);
        UiScreen.box(640, 360, new UiLayout(ResourceLocation.fromNamespaceAndPath("test", "hud"), new Stack(List.of())),
                true, box);
        assertArrayEquals(new float[]{0, 0, 640, 360}, box);
    }
}

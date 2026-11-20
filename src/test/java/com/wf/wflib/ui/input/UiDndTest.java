package com.wf.wflib.ui.input;

import com.wf.wflib.ui.api.input.UiDnd;
import com.wf.wflib.ui.api.input.UiDrop;
import com.wf.wflib.ui.api.input.UiInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDndTest {
    private static final UiDrop TARGET = (item, payload, x, y) -> {
    };

    private static void frame(UiInput input, Runnable draw) {
        input.dispatch();
        input.begin();
        draw.run();
        input.end();
    }

    @Test
    void pointerLocalFollowsTransformAndClip() {
        UiInput input = new UiInput();
        input.pointer(114, 58, 0);
        float[] out = new float[2];
        frame(input, () -> {
            input.push().translate(100, 50).scale(2);
            assertTrue(input.pointerLocal(out));
            assertEquals(7, out[0], 1e-4);
            assertEquals(4, out[1], 1e-4);
            input.pushClip(0, 0, 6, 10);
            assertFalse(input.pointerLocal(out));
            input.popClip();
            input.pop();
        });
    }

    @Test
    void offerResolvesAfterThePublishingFrame() {
        UiInput input = new UiInput();
        UiDnd dnd = input.dnd();
        dnd.begin("payload");
        assertTrue(dnd.active());
        frame(input, () -> dnd.offer(TARGET, "item", 3, 4));
        UiDnd.Offer at = dnd.end();
        assertSame(TARGET, at.target());
        assertEquals("item", at.item());
        assertEquals(3, at.x());
        assertFalse(dnd.active());
    }

    @Test
    void anOfferNotRepeatedNextFrameIsGone() {
        UiInput input = new UiInput();
        input.dnd().begin("payload");
        frame(input, () -> input.dnd().offer(TARGET, null, 0, 0));
        frame(input, () -> {
        });
        assertNull(input.dnd().end());
    }

    @Test
    void resetDropsTheCarry() {
        UiInput input = new UiInput();
        input.dnd().begin("payload");
        input.reset();
        assertFalse(input.dnd().active());
    }
}

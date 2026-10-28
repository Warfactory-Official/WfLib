package com.wf.wflib.smoking;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokingTimelineTest {

    @Test
    void litAtFourSeconds() {
        assertFalse(SmokingTimeline.lit(79));
        assertTrue(SmokingTimeline.lit(80));
    }

    @Test
    void exhalesInTheGammaWindows() {
        assertFalse(SmokingTimeline.exhaling(139));
        assertTrue(SmokingTimeline.exhaling(140));
        assertTrue(SmokingTimeline.exhaling(161));
        assertFalse(SmokingTimeline.exhaling(162));
        assertTrue(SmokingTimeline.exhaling(209));
    }

    @Test
    void dragsJustBeforeEachExhale() {
        assertFalse(SmokingTimeline.dragging(115));
        assertTrue(SmokingTimeline.dragging(116));
        assertTrue(SmokingTimeline.dragging(139));
        assertFalse(SmokingTimeline.dragging(140));
        assertTrue(SmokingTimeline.dragging(190));
    }

    @Test
    void theWristHangsThenReachesTheMouthThenHangsAgain() {
        Vector3f hanging = SmokingTimeline.wrist(0.0F, new Vector3f());
        Vector3f mouth = SmokingTimeline.wrist(4.0F, new Vector3f());
        Vector3f end = SmokingTimeline.wrist(10.5F, new Vector3f());
        assertEquals(0.875F, hanging.y, 1.0e-6F);
        assertTrue(mouth.y < 0.0F && mouth.z < -0.25F, mouth.toString());
        assertEquals(0.0F, hanging.distance(end), 1.0e-6F);
    }
}

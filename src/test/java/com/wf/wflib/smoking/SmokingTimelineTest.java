package com.wf.wflib.smoking;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokingTimelineTest {

    @Test
    void litAtTwoPointEightSeconds() {
        assertFalse(SmokingTimeline.lit(55));
        assertTrue(SmokingTimeline.lit(56));
    }

    @Test
    void exhalesInTheGammaWindows() {
        assertFalse(SmokingTimeline.exhaling(115));
        assertTrue(SmokingTimeline.exhaling(116));
        assertTrue(SmokingTimeline.exhaling(137));
        assertFalse(SmokingTimeline.exhaling(138));
        assertTrue(SmokingTimeline.exhaling(185));
    }

    @Test
    void dragsJustBeforeEachExhale() {
        assertFalse(SmokingTimeline.dragging(91));
        assertTrue(SmokingTimeline.dragging(92));
        assertTrue(SmokingTimeline.dragging(115));
        assertFalse(SmokingTimeline.dragging(116));
        assertTrue(SmokingTimeline.dragging(166));
    }

    @Test
    void theWristHangsThenReachesTheMouthThenHangsAgain() {
        Vector3f hanging = SmokingTimeline.wrist(0.0F, new Vector3f());
        Vector3f mouth = SmokingTimeline.wrist(2.8F, new Vector3f());
        Vector3f end = SmokingTimeline.wrist(9.3F, new Vector3f());
        assertEquals(0.875F, hanging.y, 1.0e-6F);
        assertTrue(mouth.y < 0.0F && mouth.z < -0.25F, mouth.toString());
        assertEquals(0.0F, hanging.distance(end), 1.0e-6F);
    }
}

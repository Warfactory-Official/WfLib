package com.wf.wflib.soldier;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotMovementTest {
    @Test void exactlyOnePeerYieldsInAHeadOnMeeting() {
        assertFalse(BotMovement.yields("WF_A1", 2, "wf_a2", 2));
        assertTrue(BotMovement.yields("wf_a2", 2, "WF_A1", 2));
    }

    @Test void grenadeEscapeHasPriorityOverARescuerWhoHasPriorityOverOrdinaryTravel() {
        assertTrue(BotMovement.yields("A", 2, "Z", 1));
        assertTrue(BotMovement.yields("A", 1, "Z", 0));
        assertFalse(BotMovement.yields("Z", 0, "A", 1));
    }
}

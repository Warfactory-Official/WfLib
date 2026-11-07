package com.wf.wflib.soldier;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoiceCadenceTest {
    @Test void speechCannotOverlapAndEventsHaveSeparateCooldowns() {
        var gate = new VoiceCadence();
        assertTrue(gate.ready("contact", 0));
        gate.played("contact", 0, 40, 0);
        assertFalse(gate.ready("grenade", 47));
        assertTrue(gate.ready("grenade", 48));
        assertFalse(gate.ready("contact", 199));
        assertTrue(gate.ready("contact", 200));
    }

    @Test void variantsDoNotImmediatelyRepeatAndSingleClipWorks() {
        var gate = new VoiceCadence();
        gate.played("contact", 1, 40, 0);
        assertEquals(2, gate.variant("contact", 3, 1));
        assertEquals(0, gate.variant("contact", 1, Integer.MIN_VALUE));
    }

    @Test void aSquadCanWarnAboutAGrenadeDuringOtherSpeechWithoutChorusingTheWarning() {
        var gate = new VoiceCadence();
        gate.played("attack", 0, 80, 0);
        assertFalse(gate.ready("grenade", 10));
        assertTrue(gate.ready("grenade", 10, true));
        gate.played("grenade", 0, 20, 10);
        assertFalse(gate.ready("grenade", 11, true));
        assertFalse(gate.ready("contact", 50));
    }

    @Test void idleSpeechHasALongerCooldown() {
        var gate = new VoiceCadence();
        gate.played("idle", 0, 40, 0);
        assertFalse(gate.ready("idle", 599));
        assertTrue(gate.ready("idle", 600));
    }
}

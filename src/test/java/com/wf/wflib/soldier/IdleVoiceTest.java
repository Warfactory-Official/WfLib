package com.wf.wflib.soldier;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IdleVoiceTest {
    private static final IdleVoice.Context CALM = new IdleVoice.Context(false, false, false, false, 3000);

    @Test void aPackWithOnlyPlainIdleBehavesAsBefore() {
        for (int r = 0; r < 50; r++) assertEquals("idle", IdleVoice.pick(Set.of("idle", "contact"), CALM, r));
    }

    @Test void noIdleLineAtAllIsSilence() {
        assertNull(IdleVoice.pick(Set.of("contact", "attack"), CALM, 0));
        assertNull(IdleVoice.pick(Set.of("idle_night"), CALM, 0), "a night line is not in the running by day");
    }

    @Test void situationsBringTheirVariantsIntoTheRunning() {
        var all = Set.of("idle", "idle_alert", "idle_hurt", "idle_night", "idle_rain", "idle_chat", "idle_bored");
        assertEquals(Set.of("idle"), seen(all, CALM));
        assertEquals(Set.of("idle", "idle_night"), seen(all, new IdleVoice.Context(true, false, false, false, 3000)));
        assertEquals(Set.of("idle", "idle_rain", "idle_chat"), seen(all, new IdleVoice.Context(false, true, false, true, 3000)));
        assertEquals(Set.of("idle", "idle_alert", "idle_hurt"), seen(all, new IdleVoice.Context(false, false, true, false, 100)));
        assertEquals(Set.of("idle", "idle_bored"), seen(all, new IdleVoice.Context(false, false, false, false, IdleVoice.BORED)));
    }

    @Test void aVariantWithoutItsLineIsNeverPicked() {
        var night = new IdleVoice.Context(true, true, true, true, 0);
        assertEquals(Set.of("idle"), seen(Set.of("idle"), night));
    }

    @Test void weightsFavourTheSituationalLine() {
        var night = new IdleVoice.Context(true, false, false, false, 3000);
        int variants = 0;
        for (int r = 0; r < 500; r++) if (IdleVoice.pick(Set.of("idle", "idle_night"), night, r).equals("idle_night")) variants++;
        assertEquals(300, variants);   // 3 of 5 shares over a full cycle of rolls
    }

    @Test void idleVariantsShareOneCooldownAndAcksAnother() {
        var gate = new VoiceCadence();
        gate.played("idle_chat", 0, 40, 0);
        assertFalse(gate.ready("idle_bored", 599));
        assertFalse(gate.ready("idle", 599));
        assertTrue(gate.ready("idle_night", 600));
        gate.played("ack_follow", 0, 20, 1000);
        assertFalse(gate.ready("ack_hold", 1020));
        assertTrue(gate.ready("ack_hold", 1040));
        assertTrue(gate.ready("contact", 1028), "acks do not use up the combat callouts");
    }

    private static Set<String> seen(Set<String> available, IdleVoice.Context c) {
        Set<String> out = new HashSet<>();
        for (int r = 0; r < 200; r++) out.add(IdleVoice.pick(available, c, r));
        return out;
    }
}

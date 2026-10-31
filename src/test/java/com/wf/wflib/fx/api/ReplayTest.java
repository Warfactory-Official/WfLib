package com.wf.wflib.fx.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ReplayTest {
    /** No Flashback on the class path: nothing is a replay, nothing is skipped, nothing throws. */
    @Test
    void withoutFlashbackNothingIsAReplay() {
        assertFalse(Replay.active());
        assertFalse(Replay.exporting());
        assertFalse(Replay.skipping());
        assertFalse(Replay.screenshot());
        assertFalse(Replay.present());
    }
}

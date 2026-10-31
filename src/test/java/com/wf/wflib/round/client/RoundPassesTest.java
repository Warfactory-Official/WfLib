package com.wf.wflib.round.client;

import com.wf.wflib.round.api.client.RoundPasses;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundPassesTest {
    /** Shot of 3 pellets live at the pass: heard until all 3 end, whichever ends first. */
    @Test
    void pelletsFoldUntilTheLastEnds() {
        RoundPasses.Fold f = new RoundPasses.Fold();
        assertTrue(f.pass(7, 3));
        assertFalse(f.pass(7, 3), "second pellet of the shot");
        f.ended(7);
        f.ended(7);
        assertTrue(f.heard(7), "one pellet still flies");
        assertFalse(f.pass(7, 1));
        f.ended(7);
        assertFalse(f.heard(7));
        assertTrue(f.pass(7, 1), "next shot, same key");
    }
}

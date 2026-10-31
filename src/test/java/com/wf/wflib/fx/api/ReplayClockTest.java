package com.wf.wflib.fx.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayClockTest {
    @Test
    void aSeekBackInAReplayIsARewind() {
        ReplayClock c = new ReplayClock();
        assertFalse(c.rewound(1000, true));
        assertFalse(c.rewound(1001, true));
        assertFalse(c.rewound(1001, true), "paused: same tick");
        assertTrue(c.rewound(400, true), "seeked back");
        assertFalse(c.rewound(401, true), "playing on from the new time");
    }

    @Test
    void liveTimeNeverCounts() {
        ReplayClock c = new ReplayClock();
        c.rewound(1000, false);
        assertFalse(c.rewound(995, false), "a live time packet landing behind the client's tick is drift");
    }

    @Test
    void leavingAReplayDoesNotCarryItsTime() {
        ReplayClock c = new ReplayClock();
        c.rewound(5000, true);
        c.reset();
        assertFalse(c.rewound(10, true));
    }
}

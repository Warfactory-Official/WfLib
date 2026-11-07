package com.wf.wflib.soldier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BotPeekTest {
    @Test
    void waitsForTheBodyThenHoldsAcrossSeveralBursts() {
        BotPeek peek = new BotPeek(7);
        peek.reset(0);
        long now = 0;
        while (!peek.exposed()) peek.tick(++now, true, false);
        long began = now;
        assertFalse(peek.settled());
        for (int i = 1; i < BotPeek.SETTLE_TICKS; i++) {
            peek.tick(++now, true, false);
            assertFalse(peek.settled(), "must not shoot during the animation");
        }
        peek.tick(++now, true, false);
        assertTrue(peek.settled());
        for (int i = 0; i < 40; i++) {
            assertTrue(peek.settled(), "a short burst or pause must not retract the peek");
            peek.tick(++now, true, false);
        }
        assertTrue(now - began >= 52);
    }

    @Test
    void suppressionInterruptsOnceAndDoesNotRestartTheRetractionEveryTick() {
        BotPeek peek = new BotPeek(3);
        for (int tick = 0; tick < 50; tick++) peek.tick(tick, true, false);
        assertTrue(peek.exposed());
        peek.tick(50, true, true);
        assertEquals(BotPeek.Phase.RETURNING, peek.phase());
        for (int tick = 51; tick <= 100; tick++) peek.tick(tick, true, true);
        assertTrue(peek.hidden());
        assertEquals(1, peek.cycles());
    }

    @Test
    void neverStartsWithoutAReadyFiringPosition() {
        BotPeek peek = new BotPeek(1);
        for (int tick = 0; tick < 200; tick++) peek.tick(tick, false, false);
        assertTrue(peek.hidden());
        assertEquals(0, peek.cycles());
    }
}

package com.wf.wflib.contract.difficulty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wf.wflib.contract.difficulty.Progression.Curve;
import com.wf.wflib.contract.difficulty.Progression.Mode;
import org.junit.jupiter.api.Test;

class ProgressionTest {
    @Test
    void flatNeverMoves() {
        var c = new Curve(Mode.FLAT, 3, 1, 8, 7);
        assertEquals(3, c.at(0), 1e-9);
        assertEquals(3, c.at(500), 1e-9);
    }

    @Test
    void linearClimbsAndStopsAtTheMax() {
        var c = new Curve(Mode.LINEAR, 1, 0.5, 4, 7);
        assertEquals(1, c.at(0), 1e-9);
        assertEquals(3, c.at(4), 1e-9);
        assertEquals(4, c.at(1000), 1e-9);
    }

    @Test
    void steppedHoldsBetweenSteps() {
        var c = new Curve(Mode.STEPPED, 0, 0.1, 10, 7);          // 0.7 every 7 days
        assertEquals(0, c.at(6), 1e-9);
        assertEquals(0.7, c.at(7), 1e-9);
        assertEquals(0.7, c.at(13), 1e-9);
        assertEquals(1.4, c.at(14), 1e-9);
    }

    @Test
    void exponentialIsSlowThenSteep() {
        var c = new Curve(Mode.EXPONENTIAL, 1, 0.1, 10, 7);
        double early = c.at(5) - c.at(0), late = c.at(20) - c.at(15);
        assertTrue(late > early * 2, early + " then " + late);
        assertEquals(10, c.at(10_000), 1e-9);
    }

    @Test
    void aStandingOperatorMeetsMoreOnDayOne() {
        var c = new Curve(Mode.LINEAR, 0.5, 0.1, 8, 7);
        double rookie = Progression.playerTier(c, 0, 0, 0.25), veteran = Progression.playerTier(c, 0, 4, 0.25);
        assertEquals(0.5, rookie, 1e-9);
        assertEquals(2.5, veteran, 1e-9);
        assertEquals(8, Progression.playerTier(c, 0, 4, 1.0), 1e-9);
    }

    @Test
    void badSettingsAreRefusedWithAReason() {
        assertThrows(IllegalArgumentException.class, () -> new Curve(Mode.LINEAR, 5, 0.1, 4, 7));
        assertThrows(IllegalArgumentException.class, () -> new Curve(Mode.LINEAR, 1, -1, 4, 7));
        assertThrows(IllegalArgumentException.class, () -> new Curve(Mode.LINEAR, 1, 1, 11, 7));
        assertThrows(IllegalArgumentException.class, () -> Mode.of("sideways"));
    }

    @Test
    void tiersScaleTheJob() {
        assertEquals(4, Tier.squadSize(4, 0));
        assertEquals(8, Tier.squadSize(4, 4));
        assertEquals(0, Tier.grade(0));
        assertEquals(3, Tier.grade(9));
        assertTrue(Tier.money(500, 4, 1) > Tier.money(500, 0, 1));
        assertEquals(0, Tier.gateRank(0.5));
        assertEquals(4, Tier.gateRank(9));
    }
}

package com.wf.wflib.soldier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A soldier's head turns onto a new aim over a few ticks, not in one snap, and still turns fast when it has far to go. */
class AimTurnTest {
    @Test
    void aNewAimErrorIsEasedInNotSnapped() {
        float yaw = 0, target = 9;                       // a typical re-rolled aim error
        float first = SoldierAi.turn(target - yaw);
        assertTrue(first < 5f, "first tick moved " + first + " of 9 degrees");
        float peak = 0;
        int ticks = 0;
        for (; ticks < 30 && Math.abs(target - yaw) > 0.5f; ticks++) {
            float step = SoldierAi.turn(target - yaw);
            peak = Math.max(peak, Math.abs(step));
            yaw += step;
        }
        assertTrue(ticks >= 4 && ticks <= 12, "settled in " + ticks + " ticks");
        assertTrue(peak < 5f, "fastest tick " + peak);
    }

    @Test
    void aFarTargetStillTurnsAtTheTurnLimit() {
        assertEquals(10f, SoldierAi.turn(120f), 1e-6);
        assertEquals(-10f, SoldierAi.turn(-120f), 1e-6);
    }

    @Test
    void anAimInsideTheFireConeIsReachedQuickly() {
        float yaw = 0;
        int ticks = 0;
        while (Math.abs(90 - yaw) > 6f && ticks < 40) {  // FIRE_CONE
            yaw += SoldierAi.turn(90 - yaw);
            ticks++;
        }
        assertTrue(ticks <= 12, "a 90 degree swing took " + ticks + " ticks to get in the fire cone");
    }
}

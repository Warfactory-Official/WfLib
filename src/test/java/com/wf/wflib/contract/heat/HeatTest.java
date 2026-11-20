package com.wf.wflib.contract.heat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HeatTest {
    @Test
    void killsHeatItUpAndTimeCoolsIt() {
        var m = new HeatMeter(0, 0);
        m.add(10, 0);
        assertEquals(10, m.value(0), 1e-9);
        assertEquals(9, m.value(HeatMeter.TICKS_PER_POINT), 1e-9);
        assertEquals(0, m.value(HeatMeter.TICKS_PER_POINT * 50), 1e-9);
    }

    @Test
    void addingAfterATimeCoolsFirst() {
        var m = new HeatMeter(10, 0);
        m.add(2, HeatMeter.TICKS_PER_POINT * 4);
        assertEquals(8, m.value(HeatMeter.TICKS_PER_POINT * 4), 1e-9);
    }

    @Test
    void heatIsClampedBothWays() {
        var m = new HeatMeter(0, 0);
        m.add(500, 0);
        assertEquals(HeatMeter.MAX, m.value(0), 1e-9);
        m.add(-500, 0);
        assertEquals(0, m.value(0), 1e-9);
    }

    @Test
    void huntSizeStepsUpWithHeat() {
        assertEquals(0, HuntSize.hunters(39.9));
        assertEquals(4, HuntSize.hunters(40));
        assertEquals(6, HuntSize.hunters(60));
        assertEquals(8, HuntSize.hunters(80));
        assertEquals(10, HuntSize.hunters(100));
    }

    @Test
    void restrictionIsAHigherLineThanHunting() {
        assertFalse(HuntSize.restricted(69.9));
        assertTrue(HuntSize.restricted(70));
        assertTrue(HuntSize.RESTRICT_AT > HuntSize.HUNT_AT);
    }

    @Test
    void aBribeNeverBuysMoreThanThereIs() {
        assertEquals(10, HuntSize.bribePoints(80));
        assertEquals(4, HuntSize.bribePoints(3.2));
        assertEquals(1, HuntSize.bribePoints(0.4));
    }

    @Test
    void aBribeCostsMoreTheHotterYouAre() {
        assertTrue(HuntSize.bribeCost(60, 10) > HuntSize.bribeCost(10, 10));
    }
}

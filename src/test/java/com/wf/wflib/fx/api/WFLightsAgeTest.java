package com.wf.wflib.fx.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WFLightsAgeTest {
    @Test
    void anOrdinaryFrameKeepsItsAge() {
        assertEquals(0.0, WFLights.age(100.0, 100.0, false));
        assertEquals(0.75, WFLights.age(100.75, 100.0, false));
    }

    /** The one-frame export on the flash's own tick would otherwise render it at full peak. */
    @Test
    void aScreenshotOnTheFlashTickIsNotAtItsPeak() {
        double age = WFLights.age(100.0, 100.0, true);
        assertEquals(WFLights.SCREENSHOT_PHASE, age);
        double life = 1.5;
        double fade = 1 - age / life;
        assertTrue(fade * fade < 0.5, "fade " + fade * fade);
    }

}

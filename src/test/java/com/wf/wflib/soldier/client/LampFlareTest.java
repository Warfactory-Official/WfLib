package com.wf.wflib.soldier.client;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LampFlareTest {
    @Test
    void theFlareShowsDownTheBeamAndFadesOutPastTheCone() {
        Vector3f beam = new Vector3f(0, 0, 1);
        assertEquals(1, LampFlare.visibility(beam, new Vector3f(0, 0, 1), 5, 22), 1e-6, "looking down the beam");
        assertEquals(0, LampFlare.visibility(beam, new Vector3f(0, 0, -1), 5, 22), 1e-6, "behind the lamp");
        Vector3f edge = new Vector3f((float) Math.sin(Math.toRadians(28)), 0, (float) Math.cos(Math.toRadians(28)));
        float v = LampFlare.visibility(beam, edge, 5, 22);
        assertTrue(v > 0 && v < 1, "inside the fade band (5 to 33 degrees): " + v);
    }

    @Test
    void aFarLampStillGlintsButNotForEver() {
        assertEquals(1, LampFlare.distanceFade(30, 40), 1e-6);
        assertEquals(0, LampFlare.distanceFade(100, 40), 1e-6);
    }
}

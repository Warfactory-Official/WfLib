package com.wf.wflib.soldier.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** An off-screen lamp follows the entity's eye and view, which the AI snaps every few ticks: the drawn frame must not. */
class LightFrameSmoothingTest {
    private static double angle(SoldierLightClient.Frame f, Vector3f to) {
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, f.fx * to.x + f.fy * to.y + f.fz * to.z))));
    }

    @Test
    void aimJitterIsHeldBackAndTheAverageDirectionKept() throws InterruptedException {
        SoldierLightClient.Frame f = new SoldierLightClient.Frame();
        Vector3f up = new Vector3f(0, 1, 0), base = new Vector3f(1, 0, 0);
        f.ease(0, 0, 0, base, up, 0.35F);
        double worstInput = 0, worstOutput = 0;
        for (int i = 0; i < 40; i++) {
            // +-9 degrees of yaw, flipped every 5 ticks (50 ms frames here), as the aim error re-roll does.
            double yaw = Math.toRadians((i / 5 % 2 == 0 ? 9 : -9));
            Vector3f target = new Vector3f((float) Math.cos(yaw), 0, (float) Math.sin(yaw));
            Thread.sleep(50);
            f.ease(0, 0, 0, target, up, 0.35F);
            worstInput = Math.max(worstInput, Math.toDegrees(Math.acos(target.dot(base))));
            if (i > 8) worstOutput = Math.max(worstOutput, angle(f, base));
        }
        assertTrue(worstInput > 8.5, "the jitter was applied");
        assertTrue(worstOutput < 0.6 * worstInput, "smoothed swing " + worstOutput + " deg against " + worstInput + " in");
    }

    @Test
    void aTeleportLandsAtOnceAndANewLampStartsOnItsTarget() {
        SoldierLightClient.Frame f = new SoldierLightClient.Frame();
        Vector3f up = new Vector3f(0, 1, 0);
        f.ease(5, 6, 7, new Vector3f(0, 0, 1), up, 0.35F);
        assertEquals(5, f.x, 1e-9);
        assertEquals(1, f.fz, 1e-6);
        f.ease(500, 6, 7, new Vector3f(0, 0, 1), up, 0.35F);
        assertEquals(500, f.x, 1e-9, "a jump past 3 m is a teleport, not a glide across the map");
    }
}

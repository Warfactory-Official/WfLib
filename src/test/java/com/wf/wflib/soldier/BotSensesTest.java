package com.wf.wflib.soldier;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotSensesTest {
    @Test void aShotBehindTheSoldierCanBeHeardWithoutBeingSeen() {
        Vec3 eye = new Vec3(0, 1.62, 0), shot = new Vec3(0, 1.62, -25);
        assertFalse(BotSenses.inView(eye, new Vec3(0, 0, 1), shot));
        assertTrue(BotSenses.hears(eye, shot, true));
        assertFalse(BotSenses.hears(eye, shot, false));
        assertTrue(BotSenses.hears(eye, new Vec3(0, 1.62, -10), false));
    }

    @Test void aWatchScanIsGradualAndStaysNearItsAssignedFacing() {
        for (int tick = 0; tick < 1000; tick++) {
            assertTrue(Math.abs(BotSenses.scan(90, tick, 42) - 90) <= 75);
            assertTrue(Math.abs(BotSenses.scan(90, tick + 1, 42) - BotSenses.scan(90, tick, 42)) < 1.4);
        }
    }

    @org.junit.jupiter.api.Test
    void awarenessBuildsOverTimeAndDependsOnDistanceAndMotion() {
        assertEquals(1f, BotSenses.detectionGain(3, 80, 0, true, 0));
        float near = BotSenses.detectionGain(10, 80, 0, false, 0);
        float far = BotSenses.detectionGain(60, 80, 0, false, 0);
        float runningFar = BotSenses.detectionGain(60, 80, 0.25, false, 0);
        float crouched = BotSenses.detectionGain(10, 80, 0, true, 0);
        float underFire = BotSenses.detectionGain(10, 80, 0, false, 1);
        org.junit.jupiter.api.Assertions.assertTrue(near < 1 && near > far, "a glimpse is not yet noticed, and nearer is faster");
        org.junit.jupiter.api.Assertions.assertTrue(runningFar > far, "movement draws the eye");
        org.junit.jupiter.api.Assertions.assertTrue(crouched < near && underFire < near);
        org.junit.jupiter.api.Assertions.assertTrue(1 / near > 3 && 1 / near < 14, "noticing a standing person at 10 blocks takes well under a second");
    }

    @org.junit.jupiter.api.Test
    void aLitLampMakesSomeoneStandOutAndDarknessHidesThem() {
        float day = BotSenses.exposure(15, false), dusk = BotSenses.exposure(7, false), night = BotSenses.exposure(2, false);
        assertEquals(1f, day, 1e-6);
        assertTrue(day > dusk && dusk > night && night >= BotSenses.DARK_FLOOR, "darker is harder to see");
        assertTrue(BotSenses.exposure(0, true) > day, "a lamp beats full daylight");
        float unlitAtNight = BotSenses.detectionGain(50, 80, 0, false, 0, night);
        float litAtNight = BotSenses.detectionGain(50, 80, 0, false, 0, BotSenses.exposure(2, true));
        assertTrue(litAtNight > 4 * unlitAtNight, "lit " + litAtNight + " vs unlit " + unlitAtNight);
        assertEquals(1f, BotSenses.detectionGain(3, 80, 0, false, 0, night), "arm's length is seen at once whatever the light");
    }
}

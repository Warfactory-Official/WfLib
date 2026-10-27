package com.wf.wflib.anomaly;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MincerTuningTest {
    private static final MincerTuning VORTEX = (MincerTuning) AnomalyType.VORTEX.behaviour();
    private static final MincerTuning WHIRLIGIG = (MincerTuning) AnomalyType.WHIRLIGIG.behaviour();

    @Test
    void pullIsZeroAtTheEdgeAndHeldAtTheCoreEdgeInside() {
        double r = VORTEX.radius();
        assertEquals(0.0D, VORTEX.pull(r + 0.1D, false));
        assertEquals(0.0D, VORTEX.pull(r, false), 1e-12);
        double coreEdge = VORTEX.pull(VORTEX.core() * r, false);
        assertEquals(Math.pow(0.64D, 5) * VORTEX.pull(), coreEdge, 1e-9);
        assertEquals(coreEdge, VORTEX.pull(0.0D, false));
        assertTrue(VORTEX.pull(0.7D * r, false) < VORTEX.pull(0.65D * r, false));
    }

    @Test
    void tearOnlyInsideTheCoreStrongestAtTheCentre() {
        double r = VORTEX.radius();
        assertEquals((float) (VORTEX.maxPower() * MincerTuning.FULL_HIT), VORTEX.hit(0.0D, true), 1e-5);
        assertEquals(0.0F, VORTEX.hit(VORTEX.core() * r + 0.01D, false));
        assertEquals((float) (VORTEX.maxPower() * 0.64D * MincerTuning.FULL_HIT), VORTEX.hit(VORTEX.core() * r, false),
                1e-4);
    }

    @Test
    void whirligigCatchesPlayersNearerTheMiddleThanOthers() {
        double r = WHIRLIGIG.radius();
        assertTrue(WHIRLIGIG.caught(0.7D * r, false));
        assertTrue(!WHIRLIGIG.caught(0.7D * r, true));
        assertTrue(WHIRLIGIG.caught(0.5D * r, true));
        assertEquals(0.0F, WHIRLIGIG.hit(0.7D * r, true));
        assertTrue(WHIRLIGIG.hit(0.7D * r, false) > 0.0F);
        // It pulls harder than the Vortex at the same depth.
        assertTrue(WHIRLIGIG.pull(0.85D * r, true) > VORTEX.pull(0.85D * VORTEX.radius(), true));
    }

    @Test
    void smallBodiesAndTheOuterRingDoNotTripIt() {
        assertTrue(MincerTuning.small(0.4F, 0.7F)); // chicken
        assertTrue(MincerTuning.small(0.5F, 0.9F)); // bat
        assertTrue(!MincerTuning.small(0.9F, 0.9F)); // pig
        assertTrue(!MincerTuning.small(0.6F, 1.8F)); // player
        assertTrue(VORTEX.trips(VORTEX.trigger() * VORTEX.radius()));
        assertTrue(!VORTEX.trips(VORTEX.trigger() * VORTEX.radius() + 0.01D));
    }

    @Test
    void launchGoesUpAndEveryWayAtLaunchSpeed() {
        RandomSource random = RandomSource.create(42L);
        boolean[] quadrant = new boolean[4];
        for (int i = 0; i < 2000; i++) {
            Vec3 v = WHIRLIGIG.launch(random);
            double speed = v.length();
            assertTrue(speed >= WHIRLIGIG.launchMin() - 1e-9
                    && speed <= WHIRLIGIG.launchMin() + WHIRLIGIG.launchSpread() + 1e-9, "speed " + speed);
            assertTrue(v.y > 0.0D);
            quadrant[(v.x >= 0 ? 0 : 1) + (v.z >= 0 ? 0 : 2)] = true;
        }
        assertTrue(quadrant[0] && quadrant[1] && quadrant[2] && quadrant[3]);
    }

    @Test
    void idleFloatCycles() {
        assertTrue(VORTEX.idleFloat(0));
        assertTrue(VORTEX.idleFloat(VORTEX.teleTicks() - 1));
        assertTrue(!VORTEX.idleFloat(VORTEX.teleTicks()));
        assertTrue(VORTEX.idleFloat(VORTEX.teleTicks() + VORTEX.telePause()));
    }
}

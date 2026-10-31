package com.wf.wflib.suppression;

import com.wf.wflib.geom.api.Segments;
import com.wf.wflib.suppression.api.Suppression;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuppressionTest {
    @Test
    void nearMissesSuppressOnlyInsideTheRadius() {
        Vec3 eye = new Vec3(0, 0, 0);
        assertEquals(new Vec3(0, 1, 0), Segments.closest(new Vec3(-20, 1, 0), new Vec3(20, 1, 0), eye));
        double fast = Suppression.radius(900 / 20.0);
        double slow = Suppression.radius(300 / 20.0);
        assertTrue(slow < fast, "slower round, smaller near-miss radius");
        assertTrue(Suppression.impulse(1, fast) > Suppression.impulse(2, fast));
        assertEquals(0, Suppression.impulse(fast, fast));
        assertEquals(0, Suppression.nearMiss(7, 7, false, 0.5, 45), "own round never suppresses");
        assertEquals(0, Suppression.nearMiss(7, 8, true, 0.5, 45), "resting round");
        assertTrue(Suppression.nearMiss(7, 8, false, 0.5, 45) > 0, "another shooter's near miss");
    }
}

package com.wf.wflib.soldier;

import com.norwood.ahf.stance.Posture;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BotTacticsTest {
    @Test
    void plannedLeanMirrorsTheActualStanceGeometryAndRotatesWithTheThreat() {
        Vec3 feet = new Vec3(10, 81, -5);
        Vec3 right = BotTactics.peekEye(feet, Posture.STAND, 1, 0);
        Vec3 left = BotTactics.peekEye(feet, Posture.STAND, -1, 0);
        assertEquals(right.y, left.y, 1e-6);
        assertEquals(feet.x * 2, right.x + left.x, 1e-6);
        assertTrue(right.x < feet.x - 0.4, "right lean facing south moves the eye west");
        assertTrue(right.y < feet.y + BotTactics.STAND_EYE, "lean lowers the head as well as moving it sideways");
        Vec3 east = BotTactics.peekEye(feet, Posture.STAND, 1, -90);
        assertEquals(feet.x, east.x, 1e-6);
        assertTrue(east.z > feet.z + 0.4);
    }
}

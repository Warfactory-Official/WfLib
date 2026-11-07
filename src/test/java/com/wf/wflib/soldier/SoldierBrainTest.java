package com.wf.wflib.soldier;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoldierBrainTest {
    @Test
    void escortSlotsStayNearTheOwnerAndSpreadOut() {
        Vec3 owner = new Vec3(10, 64, -3);
        Set<Vec3> distinct = new HashSet<>();
        for (int i = 1; i <= 8; i++) {
            Vec3 slot = SoldierBrain.slot(owner, ("Ally_" + i).hashCode());
            double distance = slot.subtract(owner).horizontalDistance();
            assertTrue(distance >= 2.5 && distance <= 4.5, "slot " + i + " at " + distance);
            assertEquals(owner.y, slot.y);
            distinct.add(slot);
        }
        assertTrue(distinct.size() >= 6, "eight friendlies share " + distinct.size() + " spots");
    }

    @Test
    void aChaseGoesOncePerContactAndNotWhileItIsInSight() {
        assertTrue(SoldierBrain.worthChasing(100, 0, 0, -1), "out of sight a while: go and look");
        assertFalse(SoldierBrain.worthChasing(30, 0, 0, -1), "only just lost");
        assertFalse(SoldierBrain.worthChasing(100, 0, 3, -1), "still in sight");
        assertFalse(SoldierBrain.worthChasing(100, 0, 0, 0), "already went there and found nothing");
        assertTrue(SoldierBrain.worthChasing(400, 300, 0, 0), "a newer contact is a new chase");
    }
}

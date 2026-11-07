package com.wf.wflib.soldier;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

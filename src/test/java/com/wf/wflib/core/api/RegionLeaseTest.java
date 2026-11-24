package com.wf.wflib.core.api;

import com.wf.wflib.physics.api.PhysicsBody;
import com.wf.wflib.physics.api.RegionHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionLeaseTest {

    @Test
    void noHostRunsInline() {
        int[] ran = {0};
        RegionLease.onMain(null, BlockPos.ZERO, () -> ran[0]++);
        assertEquals(1, ran[0]);
    }

    @Test
    void hostWithoutLeaseRunsInline() {
        RegionHost host = new RegionHost() {
            public int currentRegion() {
                return -1;
            }

            public boolean owns(PhysicsBody body) {
                return false;
            }

            public boolean owns(ServerLevel level, BlockPos pos) {
                return false;
            }

            public void runOnOwner(ServerLevel level, BlockPos pos, Runnable work) {
                throw new AssertionError();
            }
        };
        int[] ran = {0};
        host.runLeased(null, BlockPos.ZERO, () -> ran[0]++);
        assertEquals(1, ran[0]);
    }

    @Test
    void leaseAreaWithoutTessellateIsNoOp() {
        RegionLease.leaseArea(null, new AABB(0, 0, 0, 100, 10, 100));
    }
}

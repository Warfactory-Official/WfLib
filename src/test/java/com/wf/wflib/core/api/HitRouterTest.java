package com.wf.wflib.core.api;

import com.wf.wflib.physics.api.PhysicsBody;
import com.wf.wflib.physics.api.RegionHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitRouterTest {

    /** Caller region 1 owns x < 100; the rest is owned elsewhere. */
    private static final class Fake implements RegionHost {
        final List<BlockPos> queued = new ArrayList<>();
        final List<Runnable> work = new ArrayList<>();

        public int currentRegion() {
            return 1;
        }

        public boolean owns(PhysicsBody body) {
            return false;
        }

        public boolean owns(ServerLevel level, BlockPos pos) {
            return pos.getX() < 100;
        }

        public void runOnOwner(ServerLevel level, BlockPos pos, Runnable w) {
            queued.add(pos);
            work.add(w);
        }
    }

    @Test
    void ownedPositionRunsInline() {
        Fake host = new Fake();
        assertFalse(HitRouter.route(host, null, new BlockPos(5, 64, 0), () -> {
        }));
        assertTrue(host.queued.isEmpty());
    }

    @Test
    void foreignPositionIsQueuedToOwnerNotRun() {
        Fake host = new Fake();
        int[] ran = {0};
        assertTrue(HitRouter.route(host, null, new BlockPos(500, 64, 0), () -> ran[0]++));
        assertEquals(List.of(new BlockPos(500, 64, 0)), host.queued);
        assertEquals(0, ran[0]);
        host.work.get(0).run();
        assertEquals(1, ran[0]);
    }
}

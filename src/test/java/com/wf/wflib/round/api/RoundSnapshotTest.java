package com.wf.wflib.round.api;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoundSnapshotTest {

    private static RoundBatch batch() {
        RoundBatch b = new RoundBatch();
        for (int i = 0; i < 3; i++) {
            b.key[i] = 10 + i;
            b.x[i] = i * 10;
            b.y[i] = 64 + i;
            b.z[i] = -i;
            b.vx[i] = i + 0.5;
            b.vy[i] = -1;
            b.vz[i] = 2;
            b.shooter[i] = i == 1 ? 7 : -1;
        }
        b.size = 3;
        return b;
    }

    private static RoundSnapshot published(RoundBatch b) {
        RoundSnapshot s = new RoundSnapshot();
        s.demand();
        s.publish(b);
        return s;
    }

    @Test
    void copiesPositionAndVelocityByKey() {
        RoundSnapshot s = published(batch());
        assertEquals(new Vec3(10, 65, -1), s.position(11));
        assertEquals(new Vec3(1.5, -1, 2), s.velocity(11));
        assertNull(s.position(99));
        assertNull(s.velocity(99));
        assertEquals(7, s.shooterId(11));
        assertEquals(-1, s.shooterId(10));
        assertEquals(-1, s.shooterId(99));
    }

    @Test
    void isDetachedFromTheBatch() {
        RoundBatch b = batch();
        RoundSnapshot s = published(b);
        b.x[0] = 999;
        b.remove(0);
        assertEquals(new Vec3(0, 64, 0), s.position(10));
    }

    @Test
    void withinFiltersByBox() {
        RoundSnapshot s = published(batch());
        assertArrayEquals(new long[]{10, 11}, s.within(new AABB(-1, 60, -5, 15, 70, 5)));
        assertArrayEquals(new long[0], s.within(new AABB(100, 0, 100, 101, 1, 101)));
        assertArrayEquals(new long[0], new RoundSnapshot().within(new AABB(-1e9, -1e9, -1e9, 1e9, 1e9, 1e9)));
    }

    @Test
    void readSeesAtMostOneResolveStale() {
        RoundBatch b = batch();
        RoundSnapshot s = published(b);
        b.x[0] = 5;
        assertEquals(new Vec3(0, 64, 0), s.position(10));
        s.publish(b);
        assertEquals(new Vec3(5, 64, 0), s.position(10));
        b.remove(0);
        s.publish(b);
        assertNull(s.position(10));
        assertEquals(2, s.within(new AABB(-1e9, -1e9, -1e9, 1e9, 1e9, 1e9)).length);
    }

    @Test
    void unreadSnapshotIsNeverBuilt() {
        RoundSnapshot s = new RoundSnapshot();
        for (int i = 0; i < 100; i++) {
            s.publish(batch());
        }
        assertEquals(0, s.builds);
        assertNull(s.position(10));
    }

    @Test
    void demandExpiresAndRearms() {
        RoundBatch b = batch();
        RoundSnapshot s = published(b);
        for (int i = 1; i < RoundSnapshot.DEMAND_TICKS; i++) {
            s.publish(b);
        }
        assertEquals(RoundSnapshot.DEMAND_TICKS, s.builds);
        assertEquals(new Vec3(0, 64, 0), s.position(10));
        s.demand();
        s.publish(b);
        assertEquals(RoundSnapshot.DEMAND_TICKS + 1, s.builds);
        int built = s.builds;
        for (int i = 0; i < RoundSnapshot.DEMAND_TICKS; i++) {
            s.publish(b);
        }
        assertEquals(built + RoundSnapshot.DEMAND_TICKS - 1, s.builds);
        s.publish(b);
        assertEquals(built + RoundSnapshot.DEMAND_TICKS - 1, s.builds);
        assertNull(s.position(10));
        s.demand();
        s.publish(b);
        assertEquals(new Vec3(0, 64, 0), s.position(10));
    }

    @Test
    void growsPastInitialCapacity() {
        RoundBatch b = new RoundBatch();
        RoundSnapshot s = new RoundSnapshot();
        s.demand();
        for (int n : new int[]{3, 70, 5000, 10}) {
            b = RoundSnapshotBench.batch(n);
            s.demand();
            s.publish(b);
            assertEquals(n, s.within(new AABB(-1e9, -1e9, -1e9, 1e9, 1e9, 1e9)).length);
        }
    }

    @Test
    void concurrentReadsNeverTear() throws Exception {
        RoundBatch b = RoundSnapshotBench.batch(500);
        RoundSnapshot s = new RoundSnapshot();
        java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicInteger torn = new java.util.concurrent.atomic.AtomicInteger();
        Thread reader = new Thread(() -> {
            while (!stop.get()) {
                s.demand();
                Vec3 p = s.position(0);
                Vec3 v = s.velocity(499);
                if (p != null && p.x != p.y || v != null && v.x != v.y) {
                    torn.incrementAndGet();
                }
            }
        });
        reader.start();
        for (int t = 1; t < 200000; t++) {
            for (int i = 0; i < b.size; i++) {
                b.x[i] = t;
                b.y[i] = t;
                b.vx[i] = t;
                b.vy[i] = t;
            }
            s.publish(b);
        }
        stop.set(true);
        reader.join();
        assertEquals(0, torn.get());
    }
}

package com.wf.wflib.explosion.api;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class BlastExposureTest {

    private static final Vec3 SOURCE = new Vec3(0, 64, 0);

    /** Vanilla Explosion.getSeenPercent grid, verbatim, for equivalence. */
    private static float vanilla(Vec3 source, AABB b, BlastExposure.RayTest t) {
        double d0 = 1.0 / ((b.maxX - b.minX) * 2.0 + 1.0);
        double d1 = 1.0 / ((b.maxY - b.minY) * 2.0 + 1.0);
        double d2 = 1.0 / ((b.maxZ - b.minZ) * 2.0 + 1.0);
        double d3 = (1.0 - Math.floor(1.0 / d0) * d0) / 2.0;
        double d4 = (1.0 - Math.floor(1.0 / d1) * d1) / 2.0;
        double d5 = (1.0 - Math.floor(1.0 / d2) * d2) / 2.0;
        int i = 0, j = 0;
        for (double x = 0; x <= 1; x += d0)
            for (double y = 0; y <= 1; y += d1)
                for (double z = 0; z <= 1; z += d2) {
                    Vec3 v = new Vec3(net.minecraft.util.Mth.lerp(x, b.minX, b.maxX) + d3,
                            net.minecraft.util.Mth.lerp(y, b.minY, b.maxY) + d4,
                            net.minecraft.util.Mth.lerp(z, b.minZ, b.maxZ) + d5);
                    if (!t.blocked(v, source)) i++;
                    j++;
                }
        return (float) i / j;
    }

    private static AABB box(double cx, double cz, double w, double h, double d) {
        return new AABB(cx - w / 2, 64, cz - d / 2, cx + w / 2, 64 + h, cz + d / 2);
    }

    @Test
    void smallBoxesMatchVanillaExactly() {
        BlastExposure.RayTest wall = (from, to) -> from.x > 10.2;
        for (double[] dims : new double[][]{{0.6, 1.8, 0.6}, {0.9, 1.4, 0.9}, {1.4, 1.6, 1.4}, {1.4, 2.7, 1.4},
                {0.25, 0.25, 0.25}}) {
            for (double cx : new double[]{3, 10, 10.5, 20}) {
                AABB b = box(cx, 2, dims[0], dims[1], dims[2]);
                assertEquals(vanilla(SOURCE, b, wall), BlastExposure.seen(SOURCE, b, BlastExposure.MAX_SAMPLES, wall), 0f);
            }
        }
    }

    @Test
    void hugeBoxIsBoundedAndStillSeesPartialCover() {
        AABB ship = box(40, 0, 30, 20, 120);
        AtomicLong rays = new AtomicLong();
        BlastExposure.RayTest half = (from, to) -> {
            rays.incrementAndGet();
            return from.z > 0;
        };
        float seen = BlastExposure.seen(SOURCE, ship, BlastExposure.MAX_SAMPLES, half);
        assertTrue(rays.get() <= BlastExposure.MAX_SAMPLES);
        assertTrue(seen > 0.2f && seen < 0.8f, "partial cover kept, got " + seen);
    }

    @Test
    void manyHugeEntitiesStayWithinTheWorkBudget() {
        AtomicLong rays = new AtomicLong();
        BlastExposure.Meter meter = new BlastExposure.Meter((from, to) -> {
            rays.incrementAndGet();
            return false;
        });
        for (int i = 0; i < 5000; i++) {
            assertEquals(1.0f, meter.seen(SOURCE, box(40 + i % 50, i % 30, 30, 20, 120)));
        }
        double bound = BlastExposure.WORK_BUDGET / 40.0 + 5000;
        assertTrue(rays.get() < bound, "rays " + rays.get());
        assertTrue(meter.degraded() > 0);
        assertTrue(meter.spent() < BlastExposure.WORK_BUDGET + 5000 * 80.0);
    }

    @Test
    void smallBlastNeverDegrades() {
        BlastExposure.Meter meter = new BlastExposure.Meter((from, to) -> false);
        for (int i = 0; i < 100; i++) {
            meter.seen(SOURCE, box(20, i, 0.6, 1.8, 0.6));
        }
        assertEquals(0, meter.degraded());
    }

    @Test
    void absurdBoxDoesNotLoopForever() {
        AABB b = new AABB(0, 0, 0, 1e6, 1e6, 1e6);
        assertTrue(BlastExposure.samples(b, BlastExposure.MAX_SAMPLES) <= BlastExposure.MAX_SAMPLES);
    }
}

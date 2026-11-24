package com.wf.wflib.explosion.api;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla {@code Explosion.getSeenPercent} with bounded cost. Vanilla grid = (2w+1)(2h+1)(2d+1) rays per box:
 * cubic in hitbox size (ship/vehicle box => 1e5..1e6 rays, each a full-length block traversal).
 * Box grid coarsened to {@link #MAX_SAMPLES}; identical to vanilla at or below it.
 */
public final class BlastExposure {

    public static final int MAX_SAMPLES = 128;
    /** Per-{@link Meter} sum of samples*distance; past it each further box gets one centre ray. */
    public static final double WORK_BUDGET = 1_000_000.0;

    /** {@code true} = segment blocked. */
    @FunctionalInterface
    public interface RayTest {
        boolean blocked(Vec3 from, Vec3 to);
    }

    private BlastExposure() {
    }

    private static int axisUpper(double step) {
        return (int) Math.floor(1.0 / step) + 1;
    }

    /** Per-axis grid step: vanilla {@code 1/(extent*2+1)}, grown until samples <= {@code max}. */
    private static double[] steps(AABB box, int max) {
        double[] d = {
                1.0 / ((box.maxX - box.minX) * 2.0 + 1.0),
                1.0 / ((box.maxY - box.minY) * 2.0 + 1.0),
                1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0)};
        while ((long) axisUpper(d[0]) * axisUpper(d[1]) * axisUpper(d[2]) > max) {
            d[0] *= 1.25;
            d[1] *= 1.25;
            d[2] *= 1.25;
        }
        return d;
    }

    /** Upper bound of rays {@link #seen} casts. */
    public static int samples(AABB box, int max) {
        double[] d = steps(box, max);
        return axisUpper(d[0]) * axisUpper(d[1]) * axisUpper(d[2]);
    }

    public static float seen(Vec3 source, AABB box, int max, RayTest test) {
        double[] d = steps(box, max);
        double ox = (1.0 - Math.floor(1.0 / d[0]) * d[0]) / 2.0;
        double oy = (1.0 - Math.floor(1.0 / d[1]) * d[1]) / 2.0;
        double oz = (1.0 - Math.floor(1.0 / d[2]) * d[2]) / 2.0;
        int open = 0;
        int total = 0;
        for (double fx = 0.0; fx <= 1.0; fx += d[0]) {
            for (double fy = 0.0; fy <= 1.0; fy += d[1]) {
                for (double fz = 0.0; fz <= 1.0; fz += d[2]) {
                    Vec3 at = new Vec3(Mth.lerp(fx, box.minX, box.maxX) + ox,
                            Mth.lerp(fy, box.minY, box.maxY) + oy,
                            Mth.lerp(fz, box.minZ, box.maxZ) + oz);
                    if (!test.blocked(at, source)) {
                        open++;
                    }
                    total++;
                }
            }
        }
        return (float) open / total;
    }

    /** Work accounting for one blast's entity stage. Single owner: not thread-safe. */
    public static final class Meter {
        private final RayTest test;
        private double spent;
        private int degraded;

        public Meter(RayTest test) {
            this.test = test;
        }

        public float seen(Vec3 source, AABB box) {
            int n = samples(box, MAX_SAMPLES);
            double dist = source.distanceTo(box.getCenter());
            double cost = n * dist;
            if (spent + cost > WORK_BUDGET) {
                degraded++;
                spent += dist;
                return test.blocked(box.getCenter(), source) ? 0.0f : 1.0f;
            }
            spent += cost;
            return BlastExposure.seen(source, box, MAX_SAMPLES, test);
        }

        public double spent() {
            return spent;
        }

        /** Boxes that got the single-ray fallback. */
        public int degraded() {
            return degraded;
        }
    }
}

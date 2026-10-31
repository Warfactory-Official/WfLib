package com.wf.wflib.sway;

import net.minecraft.world.phys.Vec3;

import java.util.SplittableRandom;

/** Drivers shared by the golden test and the recorder that ran them over the pre-consolidation copies. */
final class W3Golden {
    interface Wobble {
        void add(float deg, double time, long seed, float[] out);
    }

    interface Camera {
        void add(float amp, float intensity, float ads, float aimFloor, double time, long seed, float[] out);
    }

    interface Closest {
        Vec3 of(Vec3 a, Vec3 b, Vec3 p);
    }

    interface Acoustic {
        void of(double ax, double ay, double az, double bx, double by, double bz, double px, double py, double pz,
                double[] out);
    }

    interface Impulse {
        float of(int localId, int shooterId, boolean resting, double d, double blocksPerTick);
    }

    private long h = 17;

    private void f(float x) {
        h = h * 31 + Float.floatToIntBits(x);
    }

    private void d(double x) {
        h = h * 31 + Double.doubleToRawLongBits(x);
    }

    static long wobble(Wobble w) {
        W3Golden g = new W3Golden();
        SplittableRandom r = new SplittableRandom(1);
        float[] out = new float[2];
        for (int i = 0; i < 100_000; i++) {
            out[0] = (float) r.nextDouble(-1, 1);
            out[1] = (float) r.nextDouble(-1, 1);
            float deg = i % 17 == 0 ? 0 : (float) r.nextDouble(-0.5, 5);
            w.add(deg, r.nextDouble(0, 2e6), r.nextLong(), out);
            g.f(out[0]);
            g.f(out[1]);
        }
        return g.h;
    }

    static long camera(Camera c) {
        W3Golden g = new W3Golden();
        SplittableRandom r = new SplittableRandom(2);
        float[] out = new float[2];
        for (int i = 0; i < 100_000; i++) {
            out[0] = out[1] = 0;
            float intensity = i % 5 == 0 ? 0 : (float) r.nextDouble(0, 1);
            float ads = i % 3 == 0 ? 0 : (float) r.nextDouble(0, 1);
            float floor = i % 4 == 0 ? (float) r.nextDouble(0, 1) : 0;
            c.add((float) r.nextDouble(0, 5), intensity, ads, floor, i * 1.0, r.nextLong(), out);
            g.f(out[0]);
            g.f(out[1]);
        }
        return g.h;
    }

    static long closest(Closest c) {
        W3Golden g = new W3Golden();
        SplittableRandom r = new SplittableRandom(3);
        for (int i = 0; i < 100_000; i++) {
            Vec3 a = new Vec3(r.nextDouble(-50, 50), r.nextDouble(-50, 50), r.nextDouble(-50, 50));
            Vec3 b = i % 13 == 0 ? a : a.add(r.nextDouble(-8, 8), r.nextDouble(-8, 8), r.nextDouble(-8, 8));
            Vec3 p = a.add(r.nextDouble(-10, 10), r.nextDouble(-10, 10), r.nextDouble(-10, 10));
            Vec3 q = c.of(a, b, p);
            g.d(q.x);
            g.d(q.y);
            g.d(q.z);
        }
        return g.h;
    }

    static long acoustic(Acoustic c) {
        W3Golden g = new W3Golden();
        SplittableRandom r = new SplittableRandom(4);
        double[] out = new double[2];
        for (int i = 0; i < 100_000; i++) {
            double ax = r.nextDouble(-50, 50), ay = r.nextDouble(-50, 50), az = r.nextDouble(-50, 50);
            boolean same = i % 13 == 0;
            c.of(ax, ay, az, same ? ax : ax + r.nextDouble(-8, 8), same ? ay : ay + r.nextDouble(-8, 8),
                    same ? az : az + r.nextDouble(-8, 8), ax + r.nextDouble(-10, 10), ay + r.nextDouble(-10, 10),
                    az + r.nextDouble(-10, 10), out);
            g.d(out[0]);
            g.d(out[1]);
        }
        return g.h;
    }

    static long impulse(Impulse im) {
        W3Golden g = new W3Golden();
        SplittableRandom r = new SplittableRandom(5);
        for (int i = 0; i < 100_000; i++) {
            g.f(im.of(7, i % 9 == 0 ? 7 : 8, i % 11 == 0, r.nextDouble(0, 7), r.nextDouble(0, 120)));
        }
        return g.h;
    }
}

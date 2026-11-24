package com.wf.wflib.round.api;

import org.junit.jupiter.api.Test;

class RoundSnapshotBench {

    static RoundBatch batch(int n) {
        RoundBatch b = new RoundBatch();
        while (b.key.length < n) {
            b.key = java.util.Arrays.copyOf(b.key, n);
            b.preset = java.util.Arrays.copyOf(b.preset, n);
            b.x = java.util.Arrays.copyOf(b.x, n);
            b.y = java.util.Arrays.copyOf(b.y, n);
            b.z = java.util.Arrays.copyOf(b.z, n);
            b.vx = java.util.Arrays.copyOf(b.vx, n);
            b.vy = java.util.Arrays.copyOf(b.vy, n);
            b.vz = java.util.Arrays.copyOf(b.vz, n);
            b.shooter = java.util.Arrays.copyOf(b.shooter, n);
        }
        for (int i = 0; i < n; i++) {
            b.key[i] = i;
            b.x[i] = i;
            b.vy[i] = 2;
        }
        b.size = n;
        return b;
    }

    @Test
    void nsPerSnapshotBuild() {
        for (int n : new int[]{100, 1000, 5000}) {
            RoundBatch b = batch(n);
            RoundSnapshot s = new RoundSnapshot();
            int iters = 20000;
            for (int i = 0; i < iters; i++) {
                s.demand();
                s.publish(b);
            }
            long t = System.nanoTime();
            for (int i = 0; i < iters; i++) {
                s.demand();
                s.publish(b);
            }
            long demanded = (System.nanoTime() - t) / iters;
            RoundSnapshot idle = new RoundSnapshot();
            t = System.nanoTime();
            for (int i = 0; i < iters; i++) {
                idle.publish(b);
            }
            long undemanded = (System.nanoTime() - t) / iters;
            System.out.println("BENCH after n=" + n + " ns/resolve demanded=" + demanded + " undemanded=" + undemanded);
        }
    }
}

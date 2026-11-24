package com.wf.wflib.round.api;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.VarHandle;
import java.util.Arrays;

/**
 * One level's rounds for region workers. World thread {@link #publish}es at the end of each resolve; workers read.
 * Built only while demanded: a read arms {@link #DEMAND_TICKS} resolves, idle => empty + arrays released.
 * Double-buffered seqlock: publish cost O(n) arraycopy-class, 0 allocation at steady n; reads <= 1 resolve stale.
 * Read right after arming sees empty until the next resolve.
 */
final class RoundSnapshot {

    static final int DEMAND_TICKS = 20;

    private static final class Buf {
        final long[] key;
        final double[] pos;
        final double[] vel;
        final int[] shooter;
        final KineticPreset[] preset;
        /** Odd while being written. */
        volatile int seq;
        int size;

        Buf(int capacity) {
            this.key = new long[capacity];
            this.pos = new double[capacity * 3];
            this.vel = new double[capacity * 3];
            this.shooter = new int[capacity];
            this.preset = new KineticPreset[capacity];
        }
    }

    private static final Buf EMPTY = new Buf(0);

    private volatile Buf front = EMPTY;
    /** World thread only. */
    private Buf back;
    private volatile int demand;
    /** Builds since construction; world thread only. */
    int builds;

    void demand() {
        if (this.demand != DEMAND_TICKS) {
            this.demand = DEMAND_TICKS;
        }
    }

    /** World thread. @param b null = no rounds */
    void publish(@Nullable RoundBatch b) {
        int d = this.demand;
        if (d <= 0) {
            if (this.front != EMPTY) {
                this.front = EMPTY;
                this.back = null;
            }
            return;
        }
        this.demand = d - 1;
        this.builds++;
        int n = b == null ? 0 : b.size;
        Buf w = this.back;
        if (w == null || w.key.length < n) {
            w = new Buf(Math.max(64, Integer.highestOneBit(Math.max(n, 1) * 2 - 1)));
        }
        int s = w.seq + 1;
        w.seq = s;
        VarHandle.storeStoreFence();
        w.size = n;
        if (n > 0) {
            double[] pos = w.pos;
            double[] vel = w.vel;
            for (int i = 0; i < n; i++) {
                int j = 3 * i;
                pos[j] = b.x[i];
                pos[j + 1] = b.y[i];
                pos[j + 2] = b.z[i];
                vel[j] = b.vx[i];
                vel[j + 1] = b.vy[i];
                vel[j + 2] = b.vz[i];
            }
            System.arraycopy(b.key, 0, w.key, 0, n);
            System.arraycopy(b.shooter, 0, w.shooter, 0, n);
            System.arraycopy(b.preset, 0, w.preset, 0, n);
        }
        w.seq = s + 1;
        Buf old = this.front;
        this.front = w;
        this.back = old == EMPTY ? null : old;
    }

    private static int indexOf(Buf b, long k) {
        long[] key = b.key;
        for (int i = 0, n = b.size; i < n; i++) {
            if (key[i] == k) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    Vec3 position(long k) {
        for (; ; ) {
            Buf b = this.front;
            int v = b.seq;
            if ((v & 1) == 0) {
                int i = indexOf(b, k);
                Vec3 r = i < 0 ? null : new Vec3(b.pos[3 * i], b.pos[3 * i + 1], b.pos[3 * i + 2]);
                VarHandle.loadLoadFence();
                if (b.seq == v) {
                    return r;
                }
            }
            Thread.onSpinWait();
        }
    }

    @Nullable
    Vec3 velocity(long k) {
        for (; ; ) {
            Buf b = this.front;
            int v = b.seq;
            if ((v & 1) == 0) {
                int i = indexOf(b, k);
                Vec3 r = i < 0 ? null : new Vec3(b.vel[3 * i], b.vel[3 * i + 1], b.vel[3 * i + 2]);
                VarHandle.loadLoadFence();
                if (b.seq == v) {
                    return r;
                }
            }
            Thread.onSpinWait();
        }
    }

    @Nullable
    KineticPreset preset(long k) {
        for (; ; ) {
            Buf b = this.front;
            int v = b.seq;
            if ((v & 1) == 0) {
                int i = indexOf(b, k);
                KineticPreset r = i < 0 ? null : b.preset[i];
                VarHandle.loadLoadFence();
                if (b.seq == v) {
                    return r;
                }
            }
            Thread.onSpinWait();
        }
    }

    /** Entity id, -1 none or ended. */
    int shooterId(long k) {
        for (; ; ) {
            Buf b = this.front;
            int v = b.seq;
            if ((v & 1) == 0) {
                int i = indexOf(b, k);
                int r = i < 0 ? -1 : b.shooter[i];
                VarHandle.loadLoadFence();
                if (b.seq == v) {
                    return r;
                }
            }
            Thread.onSpinWait();
        }
    }

    long[] within(AABB box) {
        for (; ; ) {
            Buf b = this.front;
            int v = b.seq;
            if ((v & 1) == 0) {
                int n = b.size;
                long[] found = new long[n];
                int c = 0;
                for (int i = 0; i < n; i++) {
                    if (box.contains(b.pos[3 * i], b.pos[3 * i + 1], b.pos[3 * i + 2])) {
                        found[c++] = b.key[i];
                    }
                }
                VarHandle.loadLoadFence();
                if (b.seq == v) {
                    return Arrays.copyOf(found, c);
                }
            }
            Thread.onSpinWait();
        }
    }
}

package com.wf.wflib.physics.rigid;

import java.util.Arrays;
import java.util.function.IntPredicate;

/** Growable contact points: position, normal, separation, two tags, caller flags. Reused. */
public final class ContactBuffer {

    /** Normals closer than this share a cluster. */
    private static final float SAME_NORMAL = 0.98f;
    private static final int PER_CLUSTER = 4;

    float[] px = new float[64], py = new float[64], pz = new float[64];
    float[] nx = new float[64], ny = new float[64], nz = new float[64];
    float[] separation = new float[64];
    int[] tagA = new int[64], tagB = new int[64];
    private int[] flags = new int[64];
    private int[] cluster = new int[64];
    private boolean[] keep = new boolean[64];
    private int count;

    public void clear() {
        count = 0;
    }

    public int size() {
        return count;
    }

    public void add(float x, float y, float z, float normalX, float normalY, float normalZ, float gap, int a, int b) {
        if (count == px.length) {
            grow();
        }
        px[count] = x;
        py[count] = y;
        pz[count] = z;
        nx[count] = normalX;
        ny[count] = normalY;
        nz[count] = normalZ;
        separation[count] = gap;
        tagA[count] = a;
        tagB[count] = b;
        flags[count] = 0;
        count++;
    }

    public float x(int i) { return px[i]; }
    public float y(int i) { return py[i]; }
    public float z(int i) { return pz[i]; }
    public float normalX(int i) { return nx[i]; }
    public float normalY(int i) { return ny[i]; }
    public float normalZ(int i) { return nz[i]; }
    public float separation(int i) { return separation[i]; }
    public int tagA(int i) { return tagA[i]; }
    public int tagB(int i) { return tagB[i]; }
    public int flags(int i) { return flags[i]; }
    public void flag(int i, int flag) { flags[i] = flag; }

    /** Rewrites point i; tags kept. */
    public void set(int i, float x, float y, float z, float normalX, float normalY, float normalZ, float gap, int flag) {
        px[i] = x;
        py[i] = y;
        pz[i] = z;
        nx[i] = normalX;
        ny[i] = normalY;
        nz[i] = normalZ;
        separation[i] = gap;
        flags[i] = flag;
    }

    /** Keeps points the test accepts, order kept. */
    public void retain(IntPredicate keep) {
        retain(0, keep);
    }

    /** As {@link #retain(IntPredicate)} over [from, size). */
    public void retain(int from, IntPredicate keep) {
        int kept = from;
        for (int i = from; i < count; i++) {
            if (!keep.test(i)) {
                continue;
            }
            move(i, kept++);
        }
        count = kept;
    }

    private void move(int from, int to) {
        px[to] = px[from];
        py[to] = py[from];
        pz[to] = pz[from];
        nx[to] = nx[from];
        ny[to] = ny[from];
        nz[to] = nz[from];
        separation[to] = separation[from];
        tagA[to] = tagA[from];
        tagB[to] = tagB[from];
        flags[to] = flags[from];
    }

    /**
     * Points [from, size) clustered by normal; each cluster kept to 4: deepest, farthest from it, widest triangle,
     * widest quad (Jolt PruneContactPoints shape). Order kept.
     */
    public void reduce(int from) {
        int clusters = 0;
        for (int i = from; i < count; i++) {
            int found = -1;
            for (int j = from; j < i && found < 0; j++) {
                if (nx[i] * nx[j] + ny[i] * ny[j] + nz[i] * nz[j] > SAME_NORMAL) {
                    found = cluster[j];
                }
            }
            cluster[i] = found >= 0 ? found : clusters++;
            keep[i] = false;
        }
        for (int c = 0; c < clusters; c++) {
            int members = 0;
            for (int i = from; i < count; i++) {
                if (cluster[i] == c) {
                    members++;
                }
            }
            if (members <= PER_CLUSTER) {
                for (int i = from; i < count; i++) {
                    keep[i] |= cluster[i] == c;
                }
                continue;
            }
            int first = -1;
            for (int i = from; i < count; i++) {
                if (cluster[i] == c && (first < 0 || separation[i] < separation[first])) {
                    first = i;
                }
            }
            int second = -1;
            float far = -1;
            for (int i = from; i < count; i++) {
                if (cluster[i] != c) {
                    continue;
                }
                float d = distanceSquared(i, first);
                if (d > far) {
                    far = d;
                    second = i;
                }
            }
            int third = -1;
            float area = -1;
            for (int i = from; i < count; i++) {
                if (cluster[i] != c) {
                    continue;
                }
                float a = area(first, second, i);
                if (a > area) {
                    area = a;
                    third = i;
                }
            }
            int fourth = -1;
            float spread = -1;
            for (int i = from; i < count; i++) {
                if (cluster[i] != c || i == first || i == second || i == third) {
                    continue;
                }
                float a = area(first, second, i) + area(second, third, i) + area(third, first, i);
                if (a > spread) {
                    spread = a;
                    fourth = i;
                }
            }
            keep[first] = true;
            keep[second] = true;
            keep[third] = true;
            if (fourth >= 0) {
                keep[fourth] = true;
            }
        }
        int kept = from;
        for (int i = from; i < count; i++) {
            if (keep[i]) {
                move(i, kept++);
            }
        }
        count = kept;
    }

    private float distanceSquared(int i, int j) {
        float dx = px[i] - px[j], dy = py[i] - py[j], dz = pz[i] - pz[j];
        return dx * dx + dy * dy + dz * dz;
    }

    /** Twice the triangle area squared. */
    private float area(int i, int j, int k) {
        float ax = px[j] - px[i], ay = py[j] - py[i], az = pz[j] - pz[i];
        float bx = px[k] - px[i], by = py[k] - py[i], bz = pz[k] - pz[i];
        float cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
        return cx * cx + cy * cy + cz * cz;
    }

    private void grow() {
        int capacity = px.length * 2;
        px = Arrays.copyOf(px, capacity);
        py = Arrays.copyOf(py, capacity);
        pz = Arrays.copyOf(pz, capacity);
        nx = Arrays.copyOf(nx, capacity);
        ny = Arrays.copyOf(ny, capacity);
        nz = Arrays.copyOf(nz, capacity);
        separation = Arrays.copyOf(separation, capacity);
        tagA = Arrays.copyOf(tagA, capacity);
        tagB = Arrays.copyOf(tagB, capacity);
        flags = Arrays.copyOf(flags, capacity);
        cluster = Arrays.copyOf(cluster, capacity);
        keep = Arrays.copyOf(keep, capacity);
    }
}

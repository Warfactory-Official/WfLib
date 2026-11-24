package com.wf.wflib.physics.regression;

import com.wf.wflib.physics.api.BoxBuffer;
import com.wf.wflib.physics.api.ConvexHull;
import com.wf.wflib.physics.rigid.ContactBuffer;
import com.wf.wflib.physics.rigid.ConvexContacts;
import com.wf.wflib.physics.rigid.NarrowNative;
import com.wf.wflib.physics.rigid.Polytope;
import com.wf.wflib.physics.rigid.RigidSolver;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minecraft-free copy of the RigidStep pipeline over the real leaf engine ({@link RigidSolver}, {@link ConvexContacts},
 * {@link NarrowNative}): id-sorted capture, swept-bounds islands, one solve per island, flush. World = per-section box
 * grid. Candidate cache mirrors StepBody: still body, native, gather inside padded bounds, same world version =>
 * boxes + envelope reused in the frame of their first solve, contacts shifted to the solver origin.
 */
final class HeadlessStep implements AutoCloseable {

    static final float MARGIN = 0.1f;
    static final float GRAVITY = -9.8f / 400;
    static final float FRICTION = 0.5f;
    static final float HULL_RESTITUTION = 0.05f;
    static final double CANDIDATE_PAD = 2;
    static final float CACHE_SPEED = 0.02f, CACHE_SPIN = 0.005f;
    private static final float DEPTH = 1.0f;

    enum Order {FORWARD, REVERSE, SHUFFLED}

    static final class Options {
        boolean nativeNarrow = NarrowNative.available();
        boolean cache = true;
        int threads = 1;
        int iterations = 1;
        Order order = Order.FORWARD;
        long orderSeed;
        /** Non-null: helpers and per-thread scratch shared with other instances (RigidStep partitions: one pool). */
        Shared shared;
    }

    /** RigidStep's process-wide pool + per-thread scratch, for several instances stepping concurrently. */
    static final class Shared implements AutoCloseable {
        final ExecutorService pool;
        final int helpers;
        final ThreadLocal<Scratch> scratch;

        Shared(int helpers, int iterations) {
            this.helpers = helpers;
            pool = Executors.newFixedThreadPool(helpers, task -> {
                Thread thread = new Thread(task, "headless-shared");
                thread.setDaemon(true);
                return thread;
            });
            scratch = ThreadLocal.withInitial(() -> new Scratch(iterations));
        }

        @Override
        public void close() {
            pool.shutdownNow();
        }
    }

    /** Section-bucketed static boxes; version bumps on any edit (the snapshot-identity stand-in). */
    static final class World {
        final Long2ObjectOpenHashMap<List<double[]>> buckets = new Long2ObjectOpenHashMap<>();
        long version;

        /** Box must lie inside one 16x16 xz section. */
        World add(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            long key = key(Math.floorDiv((int) Math.floor(minX), 16), Math.floorDiv((int) Math.floor(minZ), 16));
            if (key != key(Math.floorDiv((int) Math.floor(maxX - 1e-9), 16), Math.floorDiv((int) Math.floor(maxZ - 1e-9), 16))) {
                throw new IllegalArgumentException("box spans sections");
            }
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(new double[]{minX, minY, minZ, maxX, maxY, maxZ});
            version++;
            return this;
        }

        /** Floor top y=0, one slab per section over [x0, x1) x [z0, z1), both multiples of 16. */
        World floor(int x0, int z0, int x1, int z1) {
            for (int x = x0; x < x1; x += 16) {
                for (int z = z0; z < z1; z += 16) {
                    add(x, -1, z, x + 16, 0, z + 16);
                }
            }
            return this;
        }

        void clear() {
            buckets.clear();
            version++;
        }

        void collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, BoxBuffer out) {
            out.clear();
            for (int sx = Math.floorDiv((int) Math.floor(minX), 16); sx <= Math.floorDiv((int) Math.floor(maxX), 16); sx++) {
                for (int sz = Math.floorDiv((int) Math.floor(minZ), 16); sz <= Math.floorDiv((int) Math.floor(maxZ), 16); sz++) {
                    List<double[]> list = buckets.get(key(sx, sz));
                    if (list == null) {
                        continue;
                    }
                    for (double[] b : list) {
                        if (b[0] < maxX && b[3] > minX && b[1] < maxY && b[4] > minY && b[2] < maxZ && b[5] > minZ) {
                            out.add(b[0], b[1], b[2], b[3], b[4], b[5]);
                        }
                    }
                }
            }
        }

        private static long key(int sx, int sz) {
            return (long) sx << 32 | sz & 0xFFFFFFFFL;
        }
    }

    static final class Body {
        final int id;
        double x, y, z;
        final Quaternionf q = new Quaternionf();
        float ex, ey, ez, mass;
        final Vector3f v = new Vector3f(), w = new Vector3f();
        ConvexHull hull;

        int index, slot;
        int contacts, cacheHits, cacheMisses;
        float[] warm = new float[0];
        int warmCount, contactFrom;
        double minX, minY, minZ, maxX, maxY, maxZ;

        boolean candValid;
        long geometryVersion, candWorldVersion;
        final double[] candOrigin = new double[3];
        double cMinX, cMinY, cMinZ, cMaxX, cMaxY, cMaxZ;
        float[] candBoxes = new float[6 * 16];
        int candCount;
        final float[] envelope = new float[6];

        Body(int id, double x, double y, double z, float ex, float ey, float ez, float mass) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.ex = ex;
            this.ey = ey;
            this.ez = ez;
            this.mass = mass;
        }

        Body rotated(Quaternionf rotation) {
            q.set(rotation).normalize();
            return this;
        }

        Body moving(float vx, float vy, float vz) {
            v.set(vx, vy, vz);
            return this;
        }

        Body hull(ConvexHull shape) {
            hull = shape;
            return this;
        }

        float speedSquared() {
            return v.lengthSquared();
        }
    }

    private static final class Scratch {
        final RigidSolver solver;
        final ConvexContacts narrow = new ConvexContacts();
        final NarrowNative nat = NarrowNative.available() ? new NarrowNative() : null;
        final ContactBuffer buffer = new ContactBuffer();
        final BoxBuffer boxes = new BoxBuffer();
        final Polytope box = new Polytope(), shape = new Polytope(), other = new Polytope();
        final float[] frame = new float[NarrowNative.FRAME];
        final float[] noTriangles = new float[0];
        final long[] handle = new long[1];

        Scratch(int iterations) {
            solver = new RigidSolver(RigidSolver.SUBSTEPS, iterations);
        }
    }

    /** Pair's last contacts: anchor in the lower body's frame, normal, impulses (n, t1, t2); 9 floats each. */
    private static final class PairCache {
        float[] points = new float[0];
        int count;
    }

    private static final class Island {
        final List<Body> members = new ArrayList<>();
        final IntArrayList pairs = new IntArrayList();
        final List<PairCache> caches = new ArrayList<>();
        int contacts;
    }

    private final java.util.Map<Long, PairCache> pairCaches = new java.util.HashMap<>();

    final World world;
    final Options options;
    private final ThreadLocal<Scratch> scratch;
    private ExecutorService pool;
    private final int helpers;
    long tick;
    int lastIslands, lastContacts;

    HeadlessStep(World world, Options options) {
        this.world = world;
        this.options = options;
        if (options.shared != null) {
            scratch = options.shared.scratch;
            pool = options.shared.pool;
            helpers = options.shared.helpers;
            return;
        }
        this.scratch = ThreadLocal.withInitial(() -> new Scratch(options.iterations));
        helpers = options.threads - 1;
        if (options.threads > 1) {
            pool = Executors.newFixedThreadPool(options.threads - 1, task -> {
                Thread thread = new Thread(task, "headless-step");
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    @Override
    public void close() {
        if (pool != null && options.shared == null) {
            pool.shutdownNow();
        }
    }

    /** One tick over {@code registered} (any order): sorted by id here, as RigidStep does. */
    void step(List<Body> registered) {
        List<Body> steps = new ArrayList<>(registered);
        steps.sort(Comparator.comparingInt(b -> b.id));
        for (int i = 0; i < steps.size(); i++) {
            Body b = steps.get(i);
            b.index = i;
            bounds(b);
        }
        List<Island> islands = islands(steps);
        List<Island> order = new ArrayList<>(islands);
        switch (options.order) {
            case REVERSE -> java.util.Collections.reverse(order);
            case SHUFFLED -> java.util.Collections.shuffle(order, new java.util.Random(options.orderSeed + tick));
            default -> {
            }
        }
        if (pool == null) {
            for (Island island : order) {
                solve(island);
            }
        } else {
            AtomicInteger cursor = new AtomicInteger();
            CountDownLatch done = new CountDownLatch(order.size());
            Throwable[] failure = new Throwable[1];
            Runnable job = () -> {
                for (int k; (k = cursor.getAndIncrement()) < order.size(); ) {
                    try {
                        solve(order.get(k));
                    } catch (Throwable t) {
                        failure[0] = t;
                    }
                    done.countDown();
                }
            };
            for (int t = 0; t < helpers; t++) {
                pool.execute(job);
            }
            job.run();
            try {
                done.await();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            if (failure[0] != null) {
                throw new IllegalStateException(failure[0]);
            }
        }
        lastIslands = islands.size();
        lastContacts = 0;
        for (Island island : islands) {
            lastContacts += island.contacts;
        }
        tick++;
    }

    private static void bounds(Body b) {
        Matrix3f r = new Matrix3f().set(b.q);
        double hx = Math.abs(r.m00) * b.ex + Math.abs(r.m10) * b.ey + Math.abs(r.m20) * b.ez;
        double hy = Math.abs(r.m01) * b.ex + Math.abs(r.m11) * b.ey + Math.abs(r.m21) * b.ez;
        double hz = Math.abs(r.m02) * b.ex + Math.abs(r.m12) * b.ey + Math.abs(r.m22) * b.ez;
        b.minX = b.x - hx - MARGIN + Math.min(0, b.v.x);
        b.maxX = b.x + hx + MARGIN + Math.max(0, b.v.x);
        b.minY = b.y - hy - MARGIN + Math.min(0, b.v.y);
        b.maxY = b.y + hy + MARGIN + Math.max(0, b.v.y);
        b.minZ = b.z - hz - MARGIN + Math.min(0, b.v.z);
        b.maxZ = b.z + hz + MARGIN + Math.max(0, b.v.z);
    }

    /** Pairs ascending (i, j); island root = lowest member index. */
    private List<Island> islands(List<Body> steps) {
        int n = steps.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        List<int[]> pairs = new ArrayList<>();
        double reach = 0;
        for (int i = 0; i < n; i++) {
            Body a = steps.get(i);
            for (int j = i + 1; j < n; j++) {
                Body b = steps.get(j);
                if (a.minX - reach < b.maxX && a.maxX + reach > b.minX && a.minY - reach < b.maxY && a.maxY + reach > b.minY
                        && a.minZ - reach < b.maxZ && a.maxZ + reach > b.minZ) {
                    pairs.add(new int[]{i, j});
                    int ra = root(parent, i), rb = root(parent, j);
                    parent[Math.max(ra, rb)] = Math.min(ra, rb);
                }
            }
        }
        List<Island> islands = new ArrayList<>();
        int[] islandOf = new int[n];
        for (int i = 0; i < n; i++) {
            int r = root(parent, i);
            if (r == i) {
                islandOf[i] = islands.size();
                islands.add(new Island());
            } else {
                islandOf[i] = islandOf[r];
            }
            Island island = islands.get(islandOf[i]);
            steps.get(i).slot = island.members.size();
            island.members.add(steps.get(i));
        }
        for (int[] p : pairs) {
            Island island = islands.get(islandOf[p[0]]);
            island.pairs.add(steps.get(p[0]).slot);
            island.pairs.add(steps.get(p[1]).slot);
            island.caches.add(pairCaches.computeIfAbsent((long) steps.get(p[0]).id << 32 | steps.get(p[1]).id, k -> new PairCache()));
        }
        return islands;
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private void solve(Island island) {
        Scratch s = scratch.get();
        RigidSolver solver = s.solver;
        Body first = island.members.get(0);
        solver.reset(first.x, first.y, first.z);
        solver.addBody();
        for (Body b : island.members) {
            RigidSolver.Body body = solver.addBody();
            body.inverseMass = 1 / b.mass;
            float k = b.mass / 3;
            Matrix3f r = new Matrix3f().set(b.q);
            Matrix3f local = new Matrix3f().scaling(1 / (k * (b.ey * b.ey + b.ez * b.ez)),
                    1 / (k * (b.ex * b.ex + b.ez * b.ez)), 1 / (k * (b.ex * b.ex + b.ey * b.ey)));
            r.mul(local, body.inverseInertia).mul(r.transpose(new Matrix3f()));
            body.com.set((float) (b.x - solver.origin[0]), (float) (b.y - solver.origin[1]), (float) (b.z - solver.origin[2]));
            body.velocity.set(b.v);
            body.spin.set(b.w);
            body.acceleration.set(0, GRAVITY, 0);
        }
        for (int m = 0; m < island.members.size(); m++) {
            worldContacts(s, solver, island.members.get(m), m + 1);
        }
        int[] pairFrom = new int[island.caches.size()], pairTo = new int[island.caches.size()];
        for (int p = 0; p < island.pairs.size(); p += 2) {
            int ia = island.pairs.getInt(p), ib = island.pairs.getInt(p + 1);
            Body a = island.members.get(ia), b = island.members.get(ib);
            int from = solver.contactCount();
            pose(s.shape, a, solver.origin);
            pose(s.other, b, solver.origin);
            s.buffer.clear();
            if (s.shape.boundsMeet(s.other, MARGIN)) {
                s.narrow.collide(s.shape, s.other, MARGIN, s.buffer);
                s.buffer.reduce(0);
            }
            for (int k = 0; k < s.buffer.size(); k++) {
                RigidSolver.Contact c = solver.addContact(ia + 1, ib + 1, s.buffer.x(k), s.buffer.y(k), s.buffer.z(k),
                        s.buffer.normalX(k), s.buffer.normalY(k), s.buffer.normalZ(k), s.buffer.separation(k));
                c.friction = FRICTION;
                c.restitution = HULL_RESTITUTION;
                warm(c, a.q, c.anchorA, island.caches.get(p / 2).points, island.caches.get(p / 2).count);
            }
            pairFrom[p / 2] = from;
            pairTo[p / 2] = solver.contactCount();
        }
        island.contacts = solver.contactCount();
        solver.solve();
        for (int p = 0; p < island.pairs.size(); p += 2) {
            PairCache cache = island.caches.get(p / 2);
            Body a = island.members.get(island.pairs.getInt(p));
            int count = pairTo[p / 2] - pairFrom[p / 2];
            if (cache.points.length < count * 9) {
                cache.points = new float[count * 9];
            }
            store(solver, a.q, pairFrom[p / 2], count, false, cache.points);
            cache.count = count;
        }
        for (int m = 0; m < island.members.size(); m++) {
            Body b = island.members.get(m);
            int count = b.contacts;
            if (b.warm.length < count * 9) {
                b.warm = new float[count * 9];
            }
            store(solver, b.q, b.contactFrom, count, true, b.warm);
            b.warmCount = count;
        }
        for (int m = 0; m < island.members.size(); m++) {
            Body b = island.members.get(m);
            RigidSolver.Body sb = solver.body(m + 1);
            b.x += sb.dp.x;
            b.y += sb.dp.y;
            b.z += sb.dp.z;
            sb.dq.mul(b.q, b.q).normalize();
            b.v.set(sb.velocity);
            b.w.set(sb.spin);
        }
    }

    private static void warm(RigidSolver.Contact c, Quaternionf q, Vector3f anchor, float[] points, int count) {
        Vector3f local = q.transformInverse(new Vector3f(anchor));
        int best = -1;
        float bestDistance = 0.15f * 0.15f;
        for (int m = 0; m < count; m++) {
            int o = m * 9;
            float dx = points[o] - local.x, dy = points[o + 1] - local.y, dz = points[o + 2] - local.z;
            float d = dx * dx + dy * dy + dz * dz;
            if (d < bestDistance && points[o + 3] * c.normal.x + points[o + 4] * c.normal.y + points[o + 5] * c.normal.z > 0.95f) {
                bestDistance = d;
                best = m;
            }
        }
        if (best >= 0) {
            c.normalImpulse = points[best * 9 + 6];
            c.tangentImpulse1 = points[best * 9 + 7];
            c.tangentImpulse2 = points[best * 9 + 8];
        }
    }

    private static void store(RigidSolver solver, Quaternionf q, int from, int count, boolean anchorB, float[] out) {
        Vector3f local = new Vector3f();
        for (int k = 0; k < count; k++) {
            RigidSolver.Contact c = solver.contact(from + k);
            q.transformInverse(local.set(anchorB ? c.anchorB : c.anchorA));
            int o = k * 9;
            out[o] = local.x;
            out[o + 1] = local.y;
            out[o + 2] = local.z;
            out[o + 3] = c.normal.x;
            out[o + 4] = c.normal.y;
            out[o + 5] = c.normal.z;
            out[o + 6] = c.normalImpulse;
            out[o + 7] = c.tangentImpulse1;
            out[o + 8] = c.tangentImpulse2;
        }
    }

    private static void pose(Polytope p, Body b, double[] origin) {
        float cx = (float) (b.x - origin[0]), cy = (float) (b.y - origin[1]), cz = (float) (b.z - origin[2]);
        if (b.hull != null) {
            p.hull(b.hull, cx, cy, cz, b.q);
        } else {
            p.box(cx, cy, cz, b.q, b.ex, b.ey, b.ez);
        }
    }

    private void worldContacts(Scratch s, RigidSolver solver, Body b, int solverBody) {
        double[] solverOrigin = solver.origin;
        ContactBuffer buffer = s.buffer;
        buffer.clear();
        NarrowNative nat = options.nativeNarrow ? s.nat : null;
        boolean cacheable = nat != null && options.cache && b.v.lengthSquared() <= CACHE_SPEED * CACHE_SPEED
                && b.w.lengthSquared() <= CACHE_SPIN * CACHE_SPIN;
        boolean reuse = cacheable && b.candValid && b.candWorldVersion == world.version && b.minX >= b.cMinX
                && b.minY >= b.cMinY && b.minZ >= b.cMinZ && b.maxX <= b.cMaxX && b.maxY <= b.cMaxY && b.maxZ <= b.cMaxZ;
        double[] origin = reuse ? b.candOrigin : solverOrigin;
        boolean reachable = true;
        if (reuse) {
            b.cacheHits++;
        } else {
            b.cacheMisses++;
            double pad = cacheable ? CANDIDATE_PAD : 0;
            double lx = b.minX - pad, ly = b.minY - pad, lz = b.minZ - pad, hx = b.maxX + pad, hy = b.maxY + pad, hz = b.maxZ + pad;
            world.collect(lx, ly, lz, hx, hy, hz, s.boxes);
            reachable = !s.boxes.isEmpty();
            b.candValid = cacheable && reachable;
            b.geometryVersion++;
            if (reachable) {
                candidates(b, s.boxes, origin);
                if (b.candValid) {
                    System.arraycopy(solverOrigin, 0, b.candOrigin, 0, 3);
                    b.candWorldVersion = world.version;
                    b.cMinX = lx;
                    b.cMinY = ly;
                    b.cMinZ = lz;
                    b.cMaxX = hx;
                    b.cMaxY = hy;
                    b.cMaxZ = hz;
                }
            } else {
                b.candCount = 0;
            }
        }
        if (reachable) {
            if (nat != null) {
                nativeContacts(s, nat, b, origin, cacheable && b.candValid);
            } else {
                javaContacts(s, b, origin);
            }
        }
        if (origin != solverOrigin) {
            float dx = (float) (origin[0] - solverOrigin[0]), dy = (float) (origin[1] - solverOrigin[1]),
                    dz = (float) (origin[2] - solverOrigin[2]);
            for (int k = 0, size = buffer.size(); k < size; k++) {
                buffer.set(k, buffer.x(k) + dx, buffer.y(k) + dy, buffer.z(k) + dz, buffer.normalX(k), buffer.normalY(k),
                        buffer.normalZ(k), buffer.separation(k), buffer.flags(k));
            }
        }
        b.contacts = buffer.size();
        b.contactFrom = solver.contactCount();
        for (int k = 0, size = buffer.size(); k < size; k++) {
            RigidSolver.Contact c = solver.addContact(0, solverBody, buffer.x(k), buffer.y(k), buffer.z(k),
                    buffer.normalX(k), buffer.normalY(k), buffer.normalZ(k), buffer.separation(k));
            c.friction = FRICTION;
            warm(c, b.q, c.anchorB, b.warm, b.warmCount);
        }
    }

    private void candidates(Body b, BoxBuffer boxes, double[] origin) {
        int n = boxes.size();
        if (b.candBoxes.length < n * 6) {
            b.candBoxes = new float[Math.max(n * 6, b.candBoxes.length * 2)];
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float[] f = b.candBoxes;
            f[i * 6] = (float) (boxes.minX(i) - origin[0]);
            f[i * 6 + 1] = (float) (boxes.minY(i) - origin[1]);
            f[i * 6 + 2] = (float) (boxes.minZ(i) - origin[2]);
            f[i * 6 + 3] = (float) (boxes.maxX(i) - origin[0]);
            f[i * 6 + 4] = (float) (boxes.maxY(i) - origin[1]);
            f[i * 6 + 5] = (float) (boxes.maxZ(i) - origin[2]);
            minX = Math.min(minX, f[i * 6]);
            minY = Math.min(minY, f[i * 6 + 1]);
            minZ = Math.min(minZ, f[i * 6 + 2]);
            maxX = Math.max(maxX, f[i * 6 + 3]);
            maxY = Math.max(maxY, f[i * 6 + 4]);
            maxZ = Math.max(maxZ, f[i * 6 + 5]);
        }
        b.candCount = n;
        b.envelope[0] = minX;
        b.envelope[1] = minY;
        b.envelope[2] = minZ;
        b.envelope[3] = maxX;
        b.envelope[4] = maxY;
        b.envelope[5] = maxZ;
    }

    private void nativeContacts(Scratch s, NarrowNative nat, Body b, double[] origin, boolean resident) {
        float[] f = s.frame;
        f[0] = (float) (b.x - origin[0]);
        f[1] = (float) (b.y - origin[1]);
        f[2] = (float) (b.z - origin[2]);
        f[3] = b.q.x;
        f[4] = b.q.y;
        f[5] = b.q.z;
        f[6] = b.q.w;
        f[7] = b.ex;
        f[8] = b.ey;
        f[9] = b.ez;
        f[10] = b.hull == null ? new Vector3f(b.ex, b.ey, b.ez).length() : Float.NaN;
        s.handle[0] = b.hull == null ? 0 : NarrowNative.hull(b.hull);
        int count = nat.worldContacts(1, f, s.handle, true, b.envelope, b.candBoxes, b.candCount, s.noTriangles, 0, MARGIN,
                DEPTH, resident ? b : null, b.geometryVersion);
        for (int k = 0; k < count; k++) {
            s.buffer.add(nat.x(k), nat.y(k), nat.z(k), nat.normalX(k), nat.normalY(k), nat.normalZ(k), nat.separation(k),
                    nat.tagA(k), nat.tagB(k));
        }
        s.buffer.reduce(0);
    }

    private void javaContacts(Scratch s, Body b, double[] origin) {
        pose(s.shape, b, origin);
        for (int i = 0; i < b.candCount; i++) {
            float[] f = b.candBoxes;
            if (Polytope.boundsMeet(f[i * 6], f[i * 6 + 1], f[i * 6 + 2], f[i * 6 + 3], f[i * 6 + 4], f[i * 6 + 5], s.shape, MARGIN)) {
                s.box.box(f[i * 6], f[i * 6 + 1], f[i * 6 + 2], f[i * 6 + 3], f[i * 6 + 4], f[i * 6 + 5]);
                s.box.tag = i;
                s.narrow.collide(s.box, s.shape, MARGIN, s.buffer);
            }
        }
        s.buffer.reduce(0);
    }

    static long mix(long h, long v) {
        h ^= v;
        h *= 0x100000001B3L;
        return h ^ (h >>> 29);
    }

    static long hash(long h, List<Body> bodies) {
        List<Body> sorted = new ArrayList<>(bodies);
        sorted.sort(Comparator.comparingInt(b -> b.id));
        for (Body b : sorted) {
            h = mix(h, b.id);
            h = mix(h, Double.doubleToRawLongBits(b.x));
            h = mix(h, Double.doubleToRawLongBits(b.y));
            h = mix(h, Double.doubleToRawLongBits(b.z));
            h = mix(h, Float.floatToRawIntBits(b.q.x));
            h = mix(h, Float.floatToRawIntBits(b.q.y));
            h = mix(h, Float.floatToRawIntBits(b.q.z));
            h = mix(h, Float.floatToRawIntBits(b.q.w));
            h = mix(h, Float.floatToRawIntBits(b.v.x));
            h = mix(h, Float.floatToRawIntBits(b.v.y));
            h = mix(h, Float.floatToRawIntBits(b.v.z));
            h = mix(h, Float.floatToRawIntBits(b.w.x));
            h = mix(h, Float.floatToRawIntBits(b.w.y));
            h = mix(h, Float.floatToRawIntBits(b.w.z));
        }
        return h;
    }

    static String describe(Body b) {
        return String.format(java.util.Locale.ROOT, "#%d (%.6f %.6f %.6f) v %s", b.id, b.x, b.y, b.z, Arrays.toString(new float[]{b.v.x, b.v.y, b.v.z}));
    }
}

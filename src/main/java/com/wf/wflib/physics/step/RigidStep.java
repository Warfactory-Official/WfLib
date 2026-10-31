package com.wf.wflib.physics.step;

import com.mojang.logging.LogUtils;
import com.wf.wflib.physics.api.BodyState;
import com.wf.wflib.physics.api.Coupling;
import com.wf.wflib.physics.api.PhysicsBody;
import com.wf.wflib.physics.api.PhysicsSettings;
import com.wf.wflib.physics.api.StepResult;
import com.wf.wflib.physics.api.SurfaceBake;
import com.wf.wflib.physics.rigid.ContactBuffer;
import com.wf.wflib.physics.rigid.ConvexContacts;
import com.wf.wflib.physics.rigid.NarrowNative;
import com.wf.wflib.physics.rigid.Polytope;
import com.wf.wflib.physics.rigid.RigidSolver;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Every server body that ticked, stepped once per level tick: capture, islands of touching
 * hulls, one {@link RigidSolver} solve + integration per island on the pool, flush in id order. Tick thread except
 * island tasks.
 */
public final class RigidStep {

    private static final Logger LOGGER = LogUtils.getLogger();
    static final boolean DEBUG = Boolean.getBoolean("wflib.physics.debug");
    private static final Map<ServerLevel, RigidStep> LEVELS = new WeakHashMap<>();
    private static volatile ExecutorService pool;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    /** Pair's last contacts: anchor in the lower id's hull frame, world normal, impulses (n, t1, t2). */
    private static final class PairCache {
        float[] points = new float[0];
        int count;
        long used;
    }

    private static final class Scratch {
        final RigidSolver solver = new RigidSolver(Integer.getInteger("wflib.physics.substeps", RigidSolver.SUBSTEPS), Integer.getInteger("wflib.physics.iterations", 1));
        final ConvexContacts narrow = new ConvexContacts();
        @Nullable final NarrowNative nativeNarrow = NarrowNative.available() ? new NarrowNative() : null;
        final ContactBuffer buffer = new ContactBuffer();
    }

    private static final class Island {
        final List<StepBody> members = new ArrayList<>();
        /** Member index pairs. */
        final List<int[]> pairs = new ArrayList<>();
        final List<PairCache> caches = new ArrayList<>();
        boolean live;
        int contacts;
        Future<?> future;
    }

    /** Bench counters, tick thread; off unless {@link #stats}. */
    private static boolean stats;

    public static void setStats(boolean on) {
        stats = on;
    }
    private static long statNanos, statTicks, statIslands, statBodies, statContacts;

    /** Per stepped tick since the last drain: step us, islands, bodies, contacts. */
    public static String drainStats() {
        long ticks = Math.max(1, statTicks);
        String line = String.format(java.util.Locale.ROOT, "step %.0f us islands %.1f bodies %.1f contacts %.1f",
                statNanos / 1000.0 / ticks, (double) statIslands / ticks, (double) statBodies / ticks, (double) statContacts / ticks);
        statNanos = statTicks = statIslands = statBodies = statContacts = 0;
        return line;
    }

    private final List<StepBody> registered = new ArrayList<>();
    private final List<PhysicsBody> near = new ArrayList<>();
    private final Long2ObjectOpenHashMap<PairCache> pairCaches = new Long2ObjectOpenHashMap<>();
    private long tick;

    private RigidStep() {}

    /** Body's tick: steps at this level's {@link #tick}. */
    public static void register(PhysicsBody body) {
        LEVELS.computeIfAbsent((ServerLevel) body.level(), level -> new RigidStep()).registered.add(StepBody.of(body));
    }

    /** Client tick thread: one predicted body, no neighbours, live world. */
    public static StepResult predict(PhysicsBody body) {
        StepBody step = StepBody.of(body);
        step.result.woken = false;
        step.predicting = true;
        step.capture(false);
        predict(step);
        return step.result;
    }

    /** Warm-start caches (prediction snapshot). */
    public static void writeWarm(PhysicsBody body, FriendlyByteBuf buf) {
        StepBody.of(body).writeWarm(buf);
    }

    public static void readWarm(PhysicsBody body, FriendlyByteBuf buf) {
        StepBody.of(body).readWarm(buf);
    }

    /** {@code ServerTicks.LEVEL_POST} at {@code TickOrder.PHYSICS}: coupling, carried and prediction slots run after. */
    public static void tick(ServerLevel level) {
        RigidStep step = LEVELS.get(level);
        if (step != null) {
            step.step();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LEVELS.remove(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LEVELS.clear();
        ExecutorService running = pool;
        pool = null;
        if (running != null) {
            running.shutdown();
            try {
                if (!running.awaitTermination(5, TimeUnit.SECONDS)) {
                    running.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                running.shutdownNow();
            }
        }
    }

    private void step() {
        if (registered.isEmpty()) {
            evict();
            return;
        }
        long startedAt = stats ? System.nanoTime() : 0;
        List<StepBody> steps = new ArrayList<>(registered.size());
        registered.sort(Comparator.comparingInt(step -> step.owner.getId()));
        PhysicsSettings settings = PhysicsSettings.current();
        boolean async = settings.asyncStep();
        for (int i = 0, size = registered.size(); i < size; i++) {
            StepBody step = registered.get(i);
            if (step.owner.isRemoved() || !step.owner.steppable()) {
                continue;
            }
            step.result.woken = false;
            step.capture(async);
            steps.add(step);
        }
        registered.clear();
        // Towed before its tractor ticked (or a sleeping tractor): coupling rows need both bodies.
        for (int i = 0; i < steps.size(); i++) {
            StepBody step = steps.get(i);
            PhysicsBody tractor = step.coupling == null ? null : step.coupling.tractor();
            if (tractor != null) {
                wake(tractor, steps, async);
            }
            for (PhysicsBody towed : step.owner.coupledBehind()) {
                wake(towed, steps, async);
            }
        }
        for (int i = 0, captured = steps.size(); i < captured; i++) {
            near.clear();
            steps.get(i).owner.neighbours(steps.get(i).gather, near);
            for (int k = 0, size = near.size(); k < size; k++) {
                wake(near.get(k), steps, async);
            }
        }
        List<Island> islands = islands(steps);
        for (Island island : islands) {
            if (!island.live && async) {
                try {
                    island.future = pool(settings).submit(() -> solve(island, settings));
                } catch (RejectedExecutionException shuttingDown) {
                    island.future = null;
                }
            }
        }
        for (Island island : islands) {
            if (island.future == null) {
                solve(island, settings);
                continue;
            }
            try {
                island.future.get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                hold(island, interrupted);
            } catch (ExecutionException failed) {
                hold(island, failed.getCause());
            }
        }
        // Carriers before riders: a rider's deck carry reads its carrier's flushed snapshot.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0, size = steps.size(); i < size; i++) {
                StepBody step = steps.get(i);
                PhysicsBody carrier = step.owner.deck().carrier();
                boolean rider = carrier != null && StepBody.active(carrier);
                if (rider == (pass == 1) && !step.owner.isRemoved()) {
                    step.owner.complete(step.result);
                }
            }
        }
        for (StepBody step : steps) {
            step.active = false;
        }
        evict();
        if (stats) {
            statNanos += System.nanoTime() - startedAt;
            statTicks++;
            statIslands += islands.size();
            statBodies += steps.size();
            for (Island island : islands) {
                statContacts += island.contacts;
            }
        }
    }

    private static void wake(PhysicsBody body, List<StepBody> steps, boolean async) {
        StepBody step = StepBody.of(body);
        if (!step.active && body.wakeForStep()) {
            step.result.woken = true;
            step.capture(async);
            steps.add(step);
        }
    }

    private static void hold(Island island, Throwable cause) {
        LOGGER.error("Rigid step failed for {}; held this tick", island.members.get(0).owner, cause);
        for (StepBody member : island.members) {
            member.hold();
        }
    }

    private static void predict(StepBody step) {
        Island island = new Island();
        island.members.add(step);
        island.live = true;
        try {
            solve(island, PhysicsSettings.current());
        } catch (RuntimeException failed) {
            LOGGER.error("Predicted step failed for {}; held", step.owner, failed);
            step.hold();
        }
        step.active = false;
    }

    /** Swept bounds meeting + hull contact allowed => same island. */
    private List<Island> islands(List<StepBody> steps) {
        int n = steps.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            StepBody towed = steps.get(i);
            if (towed.coupling != null && StepBody.active(towed.coupling.tractor())) {
                int j = steps.indexOf(StepBody.peek(towed.coupling.tractor()));
                int ra = root(parent, i), rb = root(parent, j);
                parent[Math.max(ra, rb)] = Math.min(ra, rb);
            }
        }
        for (int i = 0; i < n; i++) {
            StepBody a = steps.get(i);
            for (int j = i + 1; j < n; j++) {
                StepBody b = steps.get(j);
                double reach = a.margin + b.margin;
                if (!a.gather.inflate(reach).intersects(b.gather) || !a.owner.collidesWith(b.owner)) {
                    continue;
                }
                pairs.add(new int[]{i, j});
                int ra = root(parent, i), rb = root(parent, j);
                parent[Math.max(ra, rb)] = Math.min(ra, rb);
            }
        }
        List<Island> islands = new ArrayList<>();
        int[] islandOf = new int[n];
        int[] slot = new int[n];
        for (int i = 0; i < n; i++) {
            int r = root(parent, i);
            if (r == i) {
                islandOf[i] = islands.size();
                islands.add(new Island());
            } else {
                islandOf[i] = islandOf[r];
            }
            Island island = islands.get(islandOf[i]);
            slot[i] = island.members.size();
            island.members.add(steps.get(i));
            island.live |= steps.get(i).live;
        }
        for (int[] pair : pairs) {
            Island island = islands.get(islandOf[pair[0]]);
            island.pairs.add(new int[]{slot[pair[0]], slot[pair[1]]});
            PairCache cache = pairCaches.computeIfAbsent(key(steps.get(pair[0]).owner, steps.get(pair[1]).owner),
                    k -> new PairCache());
            cache.used = tick;
            island.caches.add(cache);
        }
        return islands;
    }

    /** Island task. */
    private static void solve(Island island, PhysicsSettings settings) {
        Scratch scratch = SCRATCH.get();
        RigidSolver solver = scratch.solver;
        StepBody first = island.members.get(0);
        solver.reset(first.comX, first.comY, first.comZ);
        solver.addBody();
        for (StepBody member : island.members) {
            member.addBody(solver);
            if (!island.pairs.isEmpty()) {
                member.poseShapes(solver.origin);
            }
        }
        for (StepBody member : island.members) {
            member.worldContacts(solver, scratch.narrow, settings.nativeNarrowphase() ? scratch.nativeNarrow : null,
                    scratch.buffer);
        }
        int[] pairFrom = new int[island.pairs.size()];
        for (int p = 0, size = island.pairs.size(); p < size; p++) {
            int[] pair = island.pairs.get(p);
            pairFrom[p] = solver.contactCount();
            hullContacts(solver, scratch, island.members.get(pair[0]), island.members.get(pair[1]), island.caches.get(p));
        }
        island.contacts = solver.contactCount();
        for (StepBody member : island.members) {
            member.couplingJoint = -1;
            Coupling coupling = member.coupling;
            if (coupling != null && StepBody.active(coupling.tractor())) {
                member.couplingJoint = solver.jointCount();
                member.couplingJoint(solver, StepBody.peek(coupling.tractor()));
            }
        }
        solver.solve();
        for (StepBody member : island.members) {
            if (member.couplingJoint >= 0) {
                RigidSolver.Joint joint = solver.joint(member.couplingJoint);
                member.storeCoupling(joint);
                if (DEBUG) {
                    LOGGER.info(String.format(java.util.Locale.ROOT, "[towjoint] %d J %.4f,%.4f,%.4f yaw %.4f axle %.4f",
                            member.owner.getId(), joint.impulse.x, joint.impulse.y, joint.impulse.z,
                            joint.yawImpulse, joint.axleImpulse));
                }
            }
        }
        for (int p = 0, size = island.pairs.size(); p < size; p++) {
            int to = p + 1 < size ? pairFrom[p + 1] : solver.contactCount();
            int[] pair = island.pairs.get(p);
            store(solver, island.members.get(pair[0]), island.members.get(pair[1]), island.caches.get(p), pairFrom[p], to);
        }
        for (StepBody member : island.members) {
            member.finish(solver);
        }
    }

    /** a = lower id (sorted); normal a -> b. */
    private static void hullContacts(RigidSolver solver, Scratch scratch, StepBody a, StepBody b, PairCache cache) {
        ContactBuffer buffer = scratch.buffer;
        buffer.clear();
        float margin = Math.max(a.margin, b.margin);
        for (int i = 0; i < a.hullShapeCount; i++) {
            Polytope shapeA = a.hullShapes.get(i);
            for (int j = 0; j < b.hullShapeCount; j++) {
                Polytope shapeB = b.hullShapes.get(j);
                if (shapeA.boundsMeet(shapeB, margin)) {
                    // Per piece pair: one reduction over the pair kept 4 points whose deepest flipped between pieces
                    // each tick => light hull on light hull rocked period 2, 7 cm deep (offline harness); per pair 8 mm, still.
                    int from = buffer.size();
                    scratch.narrow.collide(shapeA, shapeB, margin, buffer);
                    buffer.reduce(from);
                }
            }
        }
        opposing(buffer);
        Vector3f local = new Vector3f();
        for (int k = 0, size = buffer.size(); k < size; k++) {
            RigidSolver.Contact c = solver.addContact(a.body, b.body, buffer.x(k), buffer.y(k), buffer.z(k),
                    buffer.normalX(k), buffer.normalY(k), buffer.normalZ(k), buffer.separation(k));
            c.friction = BodyState.FRICTION;
            c.restitution = StepBody.HULL_RESTITUTION;
            c.debugTagA = buffer.tagA(k);
            c.debugTagB = buffer.tagB(k);
            a.hullRotation.transformInverse(local.set(c.anchorA));
            int best = -1;
            float bestDistance = 0.15f * 0.15f;
            for (int m = 0; m < cache.count; m++) {
                int o = m * 9;
                float dx = cache.points[o] - local.x, dy = cache.points[o + 1] - local.y, dz = cache.points[o + 2] - local.z;
                float distance = dx * dx + dy * dy + dz * dz;
                if (distance < bestDistance && cache.points[o + 3] * c.normal.x + cache.points[o + 4] * c.normal.y
                        + cache.points[o + 5] * c.normal.z > 0.95f) {
                    bestDistance = distance;
                    best = m;
                }
            }
            if (best >= 0) {
                c.normalImpulse = cache.points[best * 9 + 6];
                c.tangentImpulse1 = cache.points[best * 9 + 7];
                c.tangentImpulse2 = cache.points[best * 9 + 8];
            }
        }
    }

    /**
     * Drops points against the pair's depth-weighted normal. Deep overlap => piece SAT axes disagree (+n and -n in
     * one pair); kept => tank thrown up 0.76 b/t by a light hull 1.1 inside it.
     */
    private static void opposing(ContactBuffer buffer) {
        float sx = 0, sy = 0, sz = 0;
        for (int k = 0, size = buffer.size(); k < size; k++) {
            float depth = Math.max(0, -buffer.separation(k));
            sx += buffer.normalX(k) * depth;
            sy += buffer.normalY(k) * depth;
            sz += buffer.normalZ(k) * depth;
        }
        if (sx == 0 && sy == 0 && sz == 0) {
            return;
        }
        float x = sx, y = sy, z = sz;
        buffer.retain(k -> buffer.normalX(k) * x + buffer.normalY(k) * y + buffer.normalZ(k) * z >= 0);
    }

    private static void store(RigidSolver solver, StepBody a, StepBody b, PairCache cache, int from, int to) {
        int count = to - from;
        if (cache.points.length < count * 9) {
            cache.points = new float[Math.max(count * 9, cache.points.length * 2)];
        }
        Vector3f local = new Vector3f();
        boolean touching = false;
        for (int k = 0; k < count; k++) {
            RigidSolver.Contact c = solver.contact(from + k);
            a.hullRotation.transformInverse(local.set(c.anchorA));
            int o = k * 9;
            cache.points[o] = local.x;
            cache.points[o + 1] = local.y;
            cache.points[o + 2] = local.z;
            cache.points[o + 3] = c.normal.x;
            cache.points[o + 4] = c.normal.y;
            cache.points[o + 5] = c.normal.z;
            cache.points[o + 6] = c.normalImpulse;
            cache.points[o + 7] = c.tangentImpulse1;
            cache.points[o + 8] = c.tangentImpulse2;
            touching |= c.totalNormalImpulse > 0;
            // Supported by the other hull counts as ground.
            if (c.totalNormalImpulse > 0) {
                if (c.normal.y > SurfaceBake.SUPPORT_NORMAL) {
                    b.result.grounded = true;
                } else if (c.normal.y < -SurfaceBake.SUPPORT_NORMAL) {
                    a.result.grounded = true;
                }
            }
        }
        cache.count = count;
        if (DEBUG) {
            StringBuilder line = new StringBuilder(String.format(java.util.Locale.ROOT, "[rigid] pair %d-%d n=%d", a.owner.getId(), b.owner.getId(), count));
            for (int k = 0; k < count; k++) {
                RigidSolver.Contact c = solver.contact(from + k);
                line.append(String.format(java.util.Locale.ROOT, " (%d/%d n %.2f %.2f %.2f s0 %.3f J %.3f m %.1f rA %.2f %.2f %.2f rB %.2f %.2f %.2f)", c.debugTagA, c.debugTagB, c.normal.x, c.normal.y, c.normal.z, c.debugSeparation(), c.totalNormalImpulse, c.debugMass(), c.anchorA.x, c.anchorA.y, c.anchorA.z, c.anchorB.x, c.anchorB.y, c.anchorB.z));
            }
            line.append(" invI_b ").append(solver.body(b.body).inverseInertia).append(" invM_b ").append(solver.body(b.body).inverseMass);
            LOGGER.info(line.toString());
        }
        if (touching) {
            a.result.touched.add(b.owner);
            b.result.touched.add(a.owner);
        }
    }

    /** Pair unseen this tick => gone. */
    private void evict() {
        ObjectIterator<Long2ObjectMap.Entry<PairCache>> entries = pairCaches.long2ObjectEntrySet().fastIterator();
        while (entries.hasNext()) {
            if (entries.next().getValue().used < tick) {
                entries.remove();
            }
        }
        tick++;
    }

    private static long key(PhysicsBody a, PhysicsBody b) {
        int low = Math.min(a.getId(), b.getId()), high = Math.max(a.getId(), b.getId());
        return (long) low << 32 | high & 0xFFFFFFFFL;
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static ExecutorService pool(PhysicsSettings settings) {
        ExecutorService current = pool;
        if (current == null) {
            synchronized (RigidStep.class) {
                current = pool;
                if (current == null) {
                    AtomicInteger index = new AtomicInteger();
                    int configured = settings.threads();
                    int size = configured > 0 ? configured : Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
                    current = Executors.newFixedThreadPool(size, task -> {
                        Thread thread = new Thread(task, "wflib-physics-" + index.getAndIncrement());
                        thread.setDaemon(true);
                        thread.setPriority(Thread.NORM_PRIORITY - 1);
                        return thread;
                    });
                    pool = current;
                }
            }
        }
        return current;
    }
}

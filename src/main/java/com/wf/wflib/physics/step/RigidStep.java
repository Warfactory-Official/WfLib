package com.wf.wflib.physics.step;

import com.mojang.logging.LogUtils;
import com.wf.wflib.physics.api.BodyState;
import com.wf.wflib.physics.api.ChunkCollisionCache;
import com.wf.wflib.physics.api.Coupling;
import com.wf.wflib.physics.api.DriveSurface;
import com.wf.wflib.physics.api.PhysicsBody;
import com.wf.wflib.physics.api.PhysicsSettings;
import com.wf.wflib.physics.api.RegionHost;
import com.wf.wflib.physics.api.StepResult;
import com.wf.wflib.physics.api.SurfaceBake;
import com.wf.wflib.physics.predict.PredictionAcks;
import com.wf.wflib.physics.rigid.ContactBuffer;
import com.wf.wflib.physics.rigid.ConvexContacts;
import com.wf.wflib.physics.rigid.NarrowNative;
import com.wf.wflib.physics.rigid.Polytope;
import com.wf.wflib.physics.rigid.RigidSolver;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Every server body that ticked, stepped once per level tick: capture, islands of touching
 * hulls, one {@link RigidSolver} solve + integration per island on the pool, flush in id order. Tick thread except
 * island tasks.
 * <p>Partitions: under a {@link RegionHost} a body registered in region R joins R's instance, stepped on R's thread
 * at R's tick end ({@link #stepPartition}); main-thread registrations join the level's instance (LEVEL_POST). Bodies of
 * another partition never enter a step: neighbours are not woken, a rider / towed body whose carrier / tractor is
 * elsewhere is held (warned).
 */
public final class RigidStep {

    private static final Logger LOGGER = LogUtils.getLogger();
    static final boolean DEBUG = Boolean.getBoolean("wflib.physics.debug");
    private static final Map<ServerLevel, RigidStep> LEVELS = new ConcurrentHashMap<>();
    private static volatile ExecutorService pool;
    private static volatile int poolSize;
    private static final int BATCH = 4;
    private static final double SWEEP_SLACK = 1.0e-3;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    /** Level-instance {@link #posts} between idle sweeps; idle partition = none stepped for {@link #IDLE} posts. */
    private static final int SWEEP = 200, IDLE = 1200;
    private static final long WARN_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final AtomicLong crossWarned = new AtomicLong(System.nanoTime() - WARN_NANOS);
    private static final AtomicBoolean noHostWarned = new AtomicBoolean();
    /** Bound host (mod init), null: none. */
    @Nullable
    private static volatile RegionHost installed;
    /** {@link #installed} when {@link PhysicsSettings#regionStep} resolves on, else null; set at LEVEL_POST. */
    @Nullable
    private static volatile RegionHost regionHost;
    private static final ThreadLocal<Route> ROUTE = ThreadLocal.withInitial(Route::new);

    /** Calling thread's last partition: register without a boxed map lookup. */
    private static final class Route {
        RigidStep level;
        int region;
        RigidStep partition;
    }

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
        /** Member slot pairs, flattened (a, b), a < b, ascending by (a, b). */
        final IntArrayList pairs = new IntArrayList();
        final List<PairCache> caches = new ArrayList<>();
        boolean live;
        int contacts;
        /** Set by the claiming thread, read after the job's latch. */
        Throwable failure;
    }

    /** Bench counters, any step thread; off unless {@link #stats}. */
    private static volatile boolean stats;

    public static void setStats(boolean on) {
        stats = on;
    }
    private static final LongAdder statNanos = new LongAdder(), statTicks = new LongAdder(), statIslands = new LongAdder(),
            statBodies = new LongAdder(), statContacts = new LongAdder();

    public static final String[] STAGES = {"capture", "wake", "islands", "solve", "complete"};

    /**
     * Last stepped tick (any partition), written only while {@link #stats}; immutable, safe to read from the client
     * thread of an integrated server. {@code region} -1 = level step.
     */
    public record Tick(long totalNanos, long[] stageNanos, int islands, int bodies, int contacts, boolean async,
                       int region) {
    }

    private static volatile Tick lastTick;

    /** Null until a tick stepped with stats on. */
    public static Tick lastTick() {
        return lastTick;
    }

    /** Per stepped tick (all partitions) since the last drain: step us, islands, bodies, contacts; region mode. */
    public static String drainStats() {
        long ticks = Math.max(1, statTicks.sumThenReset());
        return String.format(Locale.ROOT, "step %.0f us islands %.1f bodies %.1f contacts %.1f regions %s",
                statNanos.sumThenReset() / 1000.0 / ticks, (double) statIslands.sumThenReset() / ticks,
                (double) statBodies.sumThenReset() / ticks, (double) statContacts.sumThenReset() / ticks, regionMode());
    }

    /** {@code on}: region partitions step at region tick end; {@code off}; {@code off (no host)}: on asked, none bound. */
    public static String regionMode() {
        return regionHost != null ? "on" : installed == null && PhysicsSettings.current().regionStep()
                == PhysicsSettings.RegionStep.ON ? "off (no host)" : "off";
    }

    /** Active host, null: every body steps at LEVEL_POST. */
    @Nullable
    public static RegionHost regionHost() {
        return regionHost;
    }

    /** Mod init, once. */
    public static void install(@Nullable RegionHost host) {
        installed = host;
        refreshMode();
    }

    /** OFF => null; AUTO => host; ON => host, logged once when null. */
    @Nullable
    static RegionHost resolve(PhysicsSettings.RegionStep mode, @Nullable RegionHost host) {
        return switch (mode) {
            case OFF -> null;
            case AUTO -> host;
            case ON -> {
                if (host == null && noHostWarned.compareAndSet(false, true)) {
                    LOGGER.warn("physics regionStep=on but no region host is installed: stepping per level");
                }
                yield host;
            }
        };
    }

    /** Main thread, LEVEL_POST: registrations after this use the new mode; already-registered bodies keep their path. */
    static void refreshMode() {
        RegionHost resolved = resolve(PhysicsSettings.current().regionStep(), installed);
        if (resolved != regionHost) {
            regionHost = resolved;
        }
    }

    private static final Comparator<StepBody> BODY_ORDER = Comparator.comparingInt(step -> step.owner.getId());
    private final BodyRegistry<StepBody> registered;
    /** Pre-capture holds this tick (carrier / tractor in another partition): completed failed, id order. */
    private final List<StepBody> held = new ArrayList<>();
    private final List<PhysicsBody> near = new ArrayList<>();
    private final Long2ObjectOpenHashMap<PairCache> pairCaches = new Long2ObjectOpenHashMap<>();
    private long tick;
    private final LongArrayList pairKeys = new LongArrayList();
    private double[] bounds = new double[0];
    private double[] lo = new double[0], hi = new double[0];
    private float[] margins = new float[0];
    private int[] order = new int[0];

    /** -1: level instance; else region id. */
    final int region;
    /** Partitions: routing host; level instance: null (read {@link #regionHost} per step). */
    @Nullable
    private final RegionHost host;
    /** Level instance only. */
    @Nullable
    private final ConcurrentHashMap<Integer, RigidStep> partitions;
    /** Level instance: LEVEL_POST count, main thread. */
    private volatile long posts;
    /** Partition: level {@link #posts} at its last step; idle sweep retires it. */
    private volatile long lastStep;
    /** Partition dropped from the map: registrations reroute. */
    private volatile boolean retired;
    /** Level instance: unloaded; late registrations from region workers are dropped, never re-create partitions. */
    private volatile boolean closed;
    /** Partition: own drive surface (DriveSurface is single-threaded); level instance: the level's, null. */
    @Nullable
    private DriveSurface drive;

    RigidStep() {
        region = -1;
        host = null;
        registered = new BodyRegistry<>(BODY_ORDER, true);
        partitions = new ConcurrentHashMap<>();
    }

    private RigidStep(RigidStep level, int region, RegionHost host) {
        this.region = region;
        this.host = host;
        registered = new BodyRegistry<>(BODY_ORDER, false);
        partitions = null;
        lastStep = level.posts;
    }

    /**
     * Islands claimed through an atomic cursor by pool workers and the tick thread; each unit is one multi-body island
     * or up to {@link #BATCH} consecutive single-body islands. Per-tick object: a late worker finds the cursor spent.
     */
    private static final class Job implements Runnable {
        final Island[] claim;
        final int[] unitStart;
        final int units;
        final PhysicsSettings settings;
        final AtomicInteger cursor = new AtomicInteger();
        final CountDownLatch done;

        Job(Island[] claim, int[] unitStart, int units, PhysicsSettings settings) {
            this.claim = claim;
            this.unitStart = unitStart;
            this.units = units;
            this.settings = settings;
            this.done = new CountDownLatch(units);
        }

        @Override
        public void run() {
            for (int unit; (unit = cursor.getAndIncrement()) < units; ) {
                for (int k = unitStart[unit], end = unitStart[unit + 1]; k < end; k++) {
                    try {
                        solve(claim[k], settings);
                    } catch (Throwable failed) {
                        claim[k].failure = failed;
                    }
                }
                done.countDown();
            }
        }
    }

    /** Any thread, body's tick: steps at this level's next {@link #tick}, or at its region's tick end. */
    public static void register(PhysicsBody body) {
        ServerLevel level = (ServerLevel) body.level();
        RigidStep step = LEVELS.get(level);
        if (step == null) {
            step = LEVELS.computeIfAbsent(level, k -> new RigidStep());
        }
        step.route(StepBody.of(body), regionHost);
    }

    /** Level instance. {@code host} null => this instance (vanilla path); else the calling thread's partition. */
    void route(StepBody body, @Nullable RegionHost host) {
        if (host == null) {
            registered.add(body);
            return;
        }
        int region = host.currentRegion();
        body.partition = region;
        if (region < 0) {
            registered.add(body);
            return;
        }
        if (closed) {
            return;
        }
        // offer -> read retired vs retire -> read inbox (all volatile): one side sees the other, never both miss.
        // Retired: kept (inbox seen, un-retired) or unmapped => once into the successor.
        RigidStep partition = partition(region, host);
        partition.registered.add(body);
        while (partition.retired) {
            if (closed) {
                return;
            }
            if (partitions.get(region) != partition) {
                partition = partition(region, host);
                partition.registered.add(body);
            } else {
                Thread.onSpinWait();
            }
        }
    }

    private RigidStep partition(int region, RegionHost host) {
        Route route = ROUTE.get();
        RigidStep cached = route.partition;
        if (route.level == this && route.region == region && cached != null && !cached.retired) {
            return cached;
        }
        RigidStep partition = partitions.computeIfAbsent(region, r -> new RigidStep(this, r, host));
        route.level = this;
        route.region = region;
        route.partition = partition;
        return partition;
    }

    /** Level instance: live partition or null; never creates. */
    @Nullable
    RigidStep partition(int region) {
        Route route = ROUTE.get();
        RigidStep cached = route.partition;
        if (route.level == this && route.region == region && cached != null && !cached.retired) {
            return cached;
        }
        return partitions.get(region);
    }

    /**
     * Region thread, inside the region scope, after its entities ticked: steps the bodies registered there this tick,
     * then their prediction acks. Throws => the host's region failure (serial fallback).
     */
    public static void stepPartition(ServerLevel level, int region) {
        RigidStep step = LEVELS.get(level);
        if (step != null) {
            step.stepRegion(region);
        }
        PredictionAcks.sendRegion(level, region);
    }

    /** Level instance. */
    void stepRegion(int region) {
        RigidStep partition = partition(region);
        if (partition != null) {
            partition.lastStep = posts;
            partition.step(partition.host);
        }
    }

    /** Client tick thread: one predicted body, no neighbours, live world. */
    public static StepResult predict(PhysicsBody body) {
        StepBody step = StepBody.of(body);
        step.result.woken = false;
        step.predicting = true;
        step.capture(false, null);
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

    /**
     * {@code ServerTicks.LEVEL_POST} at {@code TickOrder.PHYSICS}: the level instance only (partition -1); coupling,
     * carried and prediction slots run after.
     */
    public static void tick(ServerLevel level) {
        refreshMode();
        RigidStep step = LEVELS.get(level);
        if (step != null) {
            step.post();
        }
    }

    /** Level instance, main thread. */
    void post() {
        if (++posts % SWEEP == 0 && !partitions.isEmpty()) {
            retireIdle();
        }
        step(regionHost);
    }

    /** Idle partition with an empty inbox => removed; a register racing it sees {@link #retired} and reroutes. */
    private void retireIdle() {
        for (java.util.Iterator<RigidStep> it = partitions.values().iterator(); it.hasNext(); ) {
            RigidStep partition = it.next();
            if (posts - partition.lastStep < IDLE) {
                continue;
            }
            partition.retired = true;
            if (partition.registered.pending()) {
                partition.retired = false;
                continue;
            }
            it.remove();
        }
    }

    /** No step of this level runs: partitions emptied (a thread's {@link Route} may still name one). */
    void forget() {
        closed = true;
        for (RigidStep partition : partitions.values()) {
            partition.retired = true;
            partition.registered.drain();
            partition.registered.clear();
            partition.near.clear();
        }
        partitions.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RigidStep step = LEVELS.remove(level);
            if (step != null) {
                step.forget();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LEVELS.values().forEach(RigidStep::forget);
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

    /** {@code host} null: vanilla path, no partition checks. */
    private void step(@Nullable RegionHost host) {
        List<StepBody> pending = registered.drain();
        if (pending.isEmpty()) {
            evict();
            return;
        }
        boolean stats = RigidStep.stats;
        long startedAt = stats ? System.nanoTime() : 0;
        long[] stage = stats ? new long[STAGES.length] : null;
        long mark = startedAt;
        List<StepBody> steps = new ArrayList<>(pending.size());
        PhysicsSettings settings = PhysicsSettings.current();
        boolean async = settings.asyncStep();
        for (int i = 0, size = pending.size(); i < size; i++) {
            StepBody step = pending.get(i);
            if (step.active || step.owner.isRemoved() || !step.owner.steppable()) {
                continue;
            }
            if (host != null && severed(host, step)) {
                step.skip();
                held.add(step);
                continue;
            }
            step.result.woken = false;
            step.stepping = this;
            step.capture(async, drive(step));
            steps.add(step);
        }
        registered.clear();
        if (stats) {
            long now = System.nanoTime();
            stage[0] = now - mark;
            mark = now;
        }
        // Towed before its tractor ticked (or a sleeping tractor): coupling rows need both bodies.
        for (int i = 0; i < steps.size(); i++) {
            StepBody step = steps.get(i);
            PhysicsBody tractor = step.coupling == null ? null : step.coupling.tractor();
            if (tractor != null) {
                wake(tractor, steps, async, host);
            }
            for (PhysicsBody towed : step.owner.coupledBehind()) {
                wake(towed, steps, async, host);
            }
        }
        for (int i = 0, captured = steps.size(); i < captured; i++) {
            near.clear();
            steps.get(i).owner.neighbours(steps.get(i).gather, near);
            for (int k = 0, size = near.size(); k < size; k++) {
                wake(near.get(k), steps, async, host);
            }
        }
        near.clear();
        if (stats) {
            long now = System.nanoTime();
            stage[1] = now - mark;
            mark = now;
        }
        List<Island> islands = islands(steps);
        if (stats) {
            long now = System.nanoTime();
            stage[2] = now - mark;
            mark = now;
        }
        if (async) {
            solveShared(islands, settings);
        } else {
            for (Island island : islands) {
                solve(island, settings);
            }
        }
        if (stats) {
            long now = System.nanoTime();
            stage[3] = now - mark;
            mark = now;
        }
        // Carriers before riders: a rider's deck carry reads its carrier's flushed snapshot.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0, size = steps.size(); i < size; i++) {
                StepBody step = steps.get(i);
                PhysicsBody carrier = step.owner.deck().carrier();
                boolean rider = carrier != null && StepBody.member(carrier, this);
                if (rider == (pass == 1) && !step.owner.isRemoved()) {
                    step.owner.complete(step.result);
                }
            }
        }
        for (int i = 0, size = held.size(); i < size; i++) {
            StepBody step = held.get(i);
            if (!step.owner.isRemoved()) {
                step.owner.complete(step.result);
            }
            step.active = false;
        }
        held.clear();
        for (StepBody step : steps) {
            step.active = false;
            step.stepping = null;
        }
        evict();
        if (stats) {
            long end = System.nanoTime();
            stage[4] = end - mark;
            int contacts = 0;
            for (Island island : islands) {
                contacts += island.contacts;
            }
            lastTick = new Tick(end - startedAt, stage, islands.size(), steps.size(), contacts, async, region);
            statNanos.add(System.nanoTime() - startedAt);
            statTicks.increment();
            statIslands.add(islands.size());
            statBodies.add(steps.size());
            statContacts.add(contacts);
        }
    }

    /** Live islands stay on the tick thread; the rest are claimed by workers and the tick thread. */
    private static void solveShared(List<Island> islands, PhysicsSettings settings) {
        int count = islands.size();
        Island[] claim = new Island[count];
        int[] unitStart = new int[count + 1];
        int claimed = 0, units = 0, batch = 0;
        boolean anyLive = false;
        for (int i = 0; i < count; i++) {
            Island island = islands.get(i);
            if (island.live) {
                anyLive = true;
                continue;
            }
            boolean single = island.members.size() == 1;
            if (!single || batch == 0 || batch == BATCH) {
                unitStart[units++] = claimed;
                batch = 0;
            }
            claim[claimed++] = island;
            batch = single ? batch + 1 : 0;
        }
        unitStart[units] = claimed;
        Job job = new Job(claim, unitStart, units, settings);
        if (units > 0) {
            ExecutorService workers = pool(settings);
            for (int h = Math.min(units - (anyLive ? 0 : 1), poolSize); h > 0; h--) {
                try {
                    workers.execute(job);
                } catch (RejectedExecutionException shuttingDown) {
                    break;
                }
            }
        }
        for (Island island : islands) {
            if (island.live) {
                solve(island, settings);
            }
        }
        job.run();
        boolean interrupted = false;
        while (true) {
            try {
                job.done.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        for (Island island : islands) {
            if (island.failure != null) {
                hold(island, island.failure);
            }
        }
    }

    private void wake(PhysicsBody body, List<StepBody> steps, boolean async, @Nullable RegionHost host) {
        if (host != null && !reachable(host, body)) {
            crossed(body);
            return;
        }
        StepBody step = StepBody.of(body);
        if (!step.active && body.wakeForStep()) {
            step.result.woken = true;
            step.stepping = this;
            step.capture(async, drive(step));
            steps.add(step);
        }
    }

    /** Partitions only: an own surface, built on first capture. */
    @Nullable
    private DriveSurface drive(StepBody step) {
        if (region < 0) {
            return null;
        }
        DriveSurface surface = drive;
        if (surface == null) {
            drive = surface = new DriveSurface(ChunkCollisionCache.of(step.owner.level()));
        }
        return surface;
    }

    /**
     * {@code body} may join this step: a member; level instance => last registered on the main thread; partition =>
     * the region owns its position and it was not last registered on the main thread.
     */
    boolean reachable(RegionHost host, PhysicsBody body) {
        StepBody known = StepBody.peek(body);
        if (known != null && known.stepping == this) {
            return true;
        }
        if (region < 0) {
            return known != null && known.partition == -1;
        }
        return (known == null || known.partition != -1) && host.owns(body);
    }

    /** Carrier or tractor (last capture's coupling) unreachable => body must not step here this tick. */
    private boolean severed(RegionHost host, StepBody step) {
        PhysicsBody carrier = step.owner.deck().carrier();
        Coupling coupling = step.state.coupling;
        PhysicsBody other = carrier != null && !reachable(host, carrier) ? carrier
                : coupling != null && !reachable(host, coupling.tractor()) ? coupling.tractor() : null;
        if (other == null) {
            return false;
        }
        crossed(step.owner, other);
        return true;
    }

    private void crossed(PhysicsBody body) {
        if (warnDue()) {
            LOGGER.warn("Rigid step partition {}: {} belongs to another partition; not woken", region, body);
        }
    }

    private void crossed(PhysicsBody body, PhysicsBody other) {
        if (warnDue()) {
            LOGGER.warn("Rigid step partition {}: {} coupled / carried by {} of another partition; held this tick",
                    region, body, other);
        }
    }

    private static boolean warnDue() {
        long now = System.nanoTime(), last = crossWarned.get();
        return now - last >= WARN_NANOS && crossWarned.compareAndSet(last, now);
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

    /** Swept bounds meeting + hull contact allowed => same island. Island root = lowest member index. */
    private List<Island> islands(List<StepBody> steps) {
        int n = steps.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            steps.get(i).index = i;
        }
        for (int i = 0; i < n; i++) {
            StepBody towed = steps.get(i);
            if (towed.coupling != null && StepBody.member(towed.coupling.tractor(), this)) {
                union(parent, i, StepBody.peek(towed.coupling.tractor()).index);
            }
        }
        sweep(steps, parent);
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
        for (int p = 0, size = pairKeys.size(); p < size; p++) {
            long key = pairKeys.getLong(p);
            int i = (int) (key >>> 32), j = (int) key;
            Island island = islands.get(islandOf[i]);
            island.pairs.add(slot[i]);
            island.pairs.add(slot[j]);
            PairCache cache = pairCaches.computeIfAbsent(key(steps.get(i).owner, steps.get(j).owner),
                    k -> new PairCache());
            cache.used = tick;
            island.caches.add(cache);
        }
        return islands;
    }

    /**
     * {@link #pairKeys} ascending (i << 32 | j), i < j: pair order = contact order in the solve. Sweep = candidate
     * filter only; exact test bitwise {@code a.gather.inflate(a.margin + b.margin).intersects(b.gather)}, a = lower index.
     */
    private void sweep(List<StepBody> steps, int[] parent) {
        int n = steps.size();
        if (order.length < n) {
            int capacity = Math.max(n, order.length * 2);
            order = new int[capacity];
            lo = new double[capacity];
            hi = new double[capacity];
            margins = new float[capacity];
            bounds = new double[capacity * 6];
        }
        double[] lo = this.lo, hi = this.hi, bounds = this.bounds;
        float[] margins = this.margins;
        int[] order = this.order;
        for (int i = 0; i < n; i++) {
            StepBody body = steps.get(i);
            AABB box = body.gather;
            int o = i * 6;
            bounds[o] = box.minX;
            bounds[o + 1] = box.minY;
            bounds[o + 2] = box.minZ;
            bounds[o + 3] = box.maxX;
            bounds[o + 4] = box.maxY;
            bounds[o + 5] = box.maxZ;
            margins[i] = body.margin;
            lo[i] = box.minX - body.margin - SWEEP_SLACK;
            hi[i] = box.maxX + body.margin + SWEEP_SLACK;
            order[i] = i;
        }
        IntArrays.quickSort(order, 0, n, (x, y) -> {
            int c = Double.compare(lo[x], lo[y]);
            return c != 0 ? c : Integer.compare(x, y);
        });
        pairKeys.clear();
        for (int p = 0; p < n; p++) {
            int s = order[p];
            double limit = hi[s];
            for (int q = p + 1; q < n; q++) {
                int t = order[q];
                if (lo[t] >= limit) {
                    break;
                }
                int i = Math.min(s, t), j = Math.max(s, t);
                double reach = margins[i] + margins[j];
                int a = i * 6, b = j * 6;
                if (!(bounds[a] - reach < bounds[b + 3] && bounds[a + 3] + reach > bounds[b]
                        && bounds[a + 1] - reach < bounds[b + 4] && bounds[a + 4] + reach > bounds[b + 1]
                        && bounds[a + 2] - reach < bounds[b + 5] && bounds[a + 5] + reach > bounds[b + 2])
                        || !steps.get(i).owner.collidesWith(steps.get(j).owner)) {
                    continue;
                }
                pairKeys.add((long) i << 32 | j);
                union(parent, i, j);
            }
        }
        Arrays.sort(pairKeys.elements(), 0, pairKeys.size());
    }

    private static void union(int[] parent, int a, int b) {
        int ra = root(parent, a), rb = root(parent, b);
        parent[Math.max(ra, rb)] = Math.min(ra, rb);
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
        int pairCount = island.caches.size();
        int[] pairFrom = new int[pairCount];
        for (int p = 0; p < pairCount; p++) {
            pairFrom[p] = solver.contactCount();
            hullContacts(solver, scratch, island.members.get(island.pairs.getInt(p * 2)),
                    island.members.get(island.pairs.getInt(p * 2 + 1)), island.caches.get(p));
        }
        island.contacts = solver.contactCount();
        for (StepBody member : island.members) {
            member.couplingJoint = -1;
            Coupling coupling = member.coupling;
            if (coupling != null && StepBody.member(coupling.tractor(), member.stepping)) {
                member.couplingJoint = solver.jointCount();
                member.couplingJoint(solver, StepBody.peek(coupling.tractor()));
            }
        }
        solver.solve();
        for (StepBody member : island.members) {
            if (member.couplingJoint >= 0) {
                RigidSolver.Joint joint = solver.joint(member.couplingJoint);
                member.storeCoupling(solver, member.couplingJoint);
                if (DEBUG) {
                    LOGGER.info(String.format(Locale.ROOT, "[towjoint] %d J %.4f,%.4f,%.4f yaw %.4f axle %.4f",
                            member.owner.getId(), joint.impulse.x, joint.impulse.y, joint.impulse.z,
                            joint.yawImpulse, joint.axleImpulse));
                }
            }
        }
        for (int p = 0; p < pairCount; p++) {
            int to = p + 1 < pairCount ? pairFrom[p + 1] : solver.contactCount();
            store(solver, island.members.get(island.pairs.getInt(p * 2)), island.members.get(island.pairs.getInt(p * 2 + 1)),
                    island.caches.get(p), pairFrom[p], to);
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
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "[rigid] pair %d-%d n=%d", a.owner.getId(), b.owner.getId(), count));
            for (int k = 0; k < count; k++) {
                RigidSolver.Contact c = solver.contact(from + k);
                line.append(String.format(Locale.ROOT, " (%d/%d n %.2f %.2f %.2f s0 %.3f J %.3f m %.1f rA %.2f %.2f %.2f rB %.2f %.2f %.2f)", c.debugTagA, c.debugTagB, c.normal.x, c.normal.y, c.normal.z, c.debugSeparation(), c.totalNormalImpulse, c.debugMass(), c.anchorA.x, c.anchorA.y, c.anchorA.z, c.anchorB.x, c.anchorB.y, c.anchorB.z));
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
                    poolSize = size;
                    pool = current;
                }
            }
        }
        return current;
    }
}

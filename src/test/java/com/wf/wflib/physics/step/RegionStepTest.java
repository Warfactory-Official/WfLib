package com.wf.wflib.physics.step;

import com.wf.wflib.physics.api.BodyState;
import com.wf.wflib.physics.api.CellTops;
import com.wf.wflib.physics.api.CollisionProvider;
import com.wf.wflib.physics.api.CompoundBody;
import com.wf.wflib.physics.api.ConvexHull;
import com.wf.wflib.physics.api.Coupling;
import com.wf.wflib.physics.api.DeckView;
import com.wf.wflib.physics.api.HullPiece;
import com.wf.wflib.physics.api.OrientedBoxes;
import com.wf.wflib.physics.api.PhysicsBody;
import com.wf.wflib.physics.api.PhysicsSettings;
import com.wf.wflib.physics.api.PhysicsSettings.RegionStep;
import com.wf.wflib.physics.api.RegionHost;
import com.wf.wflib.physics.api.SpringRows;
import com.wf.wflib.physics.api.StepResult;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real {@link RigidStep} partition plumbing without a level: routing, partition steps, idle retirement, toggle, and
 * the cross-partition hold. Fake bodies record which step context saw them ({@link Fake#steppable} runs once per
 * stepped registration); non-steppable bodies never reach capture (which needs a live level: gametest only).
 */
class RegionStepTest {

    /** Region bound to the calling thread, -1 = main / outside scope. */
    private static final ThreadLocal<Integer> BOUND = ThreadLocal.withInitial(() -> -1);
    /** Step context label of the calling thread ("level" or "region N"). */
    private static final ThreadLocal<String> CONTEXT = ThreadLocal.withInitial(() -> "none");

    private static final class Host implements RegionHost {
        final Map<PhysicsBody, Integer> position = new ConcurrentHashMap<>();

        @Override
        public int currentRegion() {
            return BOUND.get();
        }

        @Override
        public boolean owns(PhysicsBody body) {
            Integer region = position.get(body);
            return region != null && region == BOUND.get();
        }

        @Override
        public boolean owns(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void runOnOwner(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, Runnable work) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class Deck implements DeckView {
        PhysicsBody carrier;

        public boolean active() { return carrier != null; }
        public PhysicsBody carrier() { return carrier; }
        public int deckCount() { return 0; }
        public float deckFrame(int i, int k) { return 0; }
        public ConvexHull deckHull(int i) { return null; }
        public boolean landing(int i) { return false; }
        public Quaternionf deckRotation() { return new Quaternionf(); }
        public double pivotX() { return 0; }
        public double pivotY() { return 0; }
        public double pivotZ() { return 0; }
        public void supportContacts(int count) { }
    }

    private record Tow(PhysicsBody tractor) implements Coupling {
        public boolean axle() { return false; }
        public Vector3f impulse() { return new Vector3f(); }
        public float yawImpulse() { return 0; }
        public float axleImpulse() { return 0; }
        public float rollImpulse() { return 0; }
        public void impulses(float yaw, float axle, float roll) { }
    }

    private static final class Fake implements PhysicsBody {
        final int id;
        final BodyState state = new BodyState();
        final Deck deck = new Deck();
        boolean steppable;
        final List<String> seen = new CopyOnWriteArrayList<>();
        final List<String> events = new CopyOnWriteArrayList<>();

        Fake(int id) {
            this.id = id;
        }

        public int getId() { return id; }
        public Level level() { return null; }
        public boolean isRemoved() { return false; }

        public boolean steppable() {
            seen.add(CONTEXT.get());
            return steppable;
        }

        public BodyState bodyState() { return state; }
        public void capture(BodyState s, boolean async) { throw new AssertionError("captured " + id); }
        public HullPiece mainPiece() { return null; }
        public CompoundBody compound() { return null; }
        public DeckView deck() { return deck; }
        public void neighbours(AABB bounds, List<PhysicsBody> out) { }
        public List<? extends PhysicsBody> coupledBehind() { return List.of(); }
        public boolean wakeForStep() { return false; }
        public boolean collidesWith(PhysicsBody other) { return true; }
        public void providerBoxes(AABB b, List<CollisionProvider.Session> s, IntArrayList base, OrientedBoxes out) { }
        public void springs(SpringRows rows, CellTops surface) { }
        public void integrate(StepResult r) { }
        public void complete(StepResult r) { events.add("complete failed=" + r.failed + " " + CONTEXT.get()); }
        public void hold() { events.add("hold " + CONTEXT.get()); }

        @Override
        public String toString() {
            return "fake#" + id;
        }
    }

    private RigidStep level;
    private Host host;

    @BeforeEach
    void setUp() {
        PhysicsSettings.override(PhysicsSettings.DEFAULTS.withAsync(false));
        level = new RigidStep();
        host = new Host();
    }

    @AfterEach
    void tearDown() {
        RigidStep.install(null);
        PhysicsSettings.override(null);
        BOUND.remove();
        CONTEXT.remove();
    }

    /** Host installed, regionStep auto: the level step applies the partition rule. */
    private void activate() {
        RigidStep.install(host);
        PhysicsSettings.override(PhysicsSettings.DEFAULTS.withAsync(false).withRegionStep(RegionStep.AUTO));
        RigidStep.refreshMode();
        assertSame(host, RigidStep.regionHost());
    }

    private void register(Fake body, int region, RegionHost via) {
        BOUND.set(region);
        try {
            level.route(StepBody.of(body), via);
        } finally {
            BOUND.set(-1);
        }
    }

    private void stepRegion(int region) {
        BOUND.set(region);
        CONTEXT.set("region " + region);
        try {
            level.stepRegion(region);
        } finally {
            BOUND.set(-1);
            CONTEXT.set("none");
        }
    }

    private void post() {
        CONTEXT.set("level");
        try {
            level.post();
        } finally {
            CONTEXT.set("none");
        }
    }

    @Test
    void noHostRegistersOnTheLevelStepOnly() {
        Fake a = new Fake(1);
        register(a, 3, null);
        assertEquals(StepBody.UNKNOWN, StepBody.peek(a).partition, "vanilla path writes nothing");
        stepRegion(3);
        assertEquals(List.of(), a.seen);
        post();
        assertEquals(List.of("level"), a.seen);
        assertNull(level.partition(3));
    }

    @Test
    void mainThreadUnderAHostIsTheLevelPartition() {
        Fake a = new Fake(1);
        register(a, -1, host);
        assertEquals(-1, StepBody.peek(a).partition);
        assertNull(level.partition(-1));
        post();
        assertEquals(List.of("level"), a.seen);
    }

    @Test
    void eachRegionStepsOnlyItsOwnBodiesAndTheLevelStepNone() {
        Fake a = new Fake(1), b = new Fake(2), c = new Fake(3);
        register(a, 4, host);
        register(b, 7, host);
        register(c, 4, host);
        post();
        assertEquals(List.of(), a.seen);
        stepRegion(7);
        stepRegion(4);
        stepRegion(4);
        assertEquals(List.of("region 4"), a.seen);
        assertEquals(List.of("region 7"), b.seen);
        assertEquals(List.of("region 4"), c.seen);
        assertEquals(4, level.partition(4).region);
        assertEquals(7, StepBody.peek(b).partition);
    }

    /** Registered twice in one tick (a rerouted register re-adds): handled once (active dedup). */
    @Test
    void aDuplicateRegistrationIsHandledOnce() {
        Fake tractor = new Fake(1), towed = new Fake(2);
        register(tractor, 8, host);
        towed.steppable = true;
        towed.state.coupling = new Tow(tractor);
        register(towed, 2, host);
        register(towed, 2, host);
        stepRegion(2);
        assertEquals(List.of("region 2"), towed.seen);
        assertEquals(List.of("hold region 2", "complete failed=true region 2"), towed.events);
    }

    /** N region threads register and step concurrently with the main thread's level steps and idle sweeps. */
    @Test
    void concurrentRegionsLoseDuplicateAndCrossNothing() throws Exception {
        int regions = 8, perRegion = 40, ticks = 300;
        Fake[][] bodies = new Fake[regions][perRegion];
        for (int r = 0; r < regions; r++) {
            for (int k = 0; k < perRegion; k++) {
                bodies[r][k] = new Fake(r * perRegion + k);
            }
        }
        Fake[] mains = new Fake[16];
        for (int k = 0; k < mains.length; k++) {
            mains[k] = new Fake(10_000 + k);
        }
        CyclicBarrier tick = new CyclicBarrier(regions + 1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> threads = new ArrayList<>();
        for (int r = 0; r < regions; r++) {
            int region = 100 + r;
            Fake[] own = bodies[r];
            Thread thread = new Thread(() -> {
                try {
                    for (int t = 0; t < ticks; t++) {
                        tick.await();
                        // Sparse regions: some ticks register nothing (lets partitions go idle and retire).
                        if ((t + region) % 3 != 0) {
                            for (int k = own.length - 1; k >= 0; k--) {
                                register(own[k], region, host);
                            }
                        }
                        stepRegion(region);
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });
            threads.add(thread);
            thread.start();
        }
        int expectedRegion = 0;
        for (int t = 0; t < ticks; t++) {
            tick.await();
            for (Fake main : mains) {
                register(main, -1, host);
            }
            // Async host: LEVEL_POST overlaps region ticks; many posts per tick drive idle sweeps through the race.
            for (int p = 0; p < 10; p++) {
                post();
            }
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertNull(failure.get());
        for (int r = 0; r < regions; r++) {
            int region = 100 + r;
            int registered = 0;
            for (int t = 0; t < ticks; t++) {
                registered += (t + region) % 3 != 0 ? 1 : 0;
            }
            for (Fake body : bodies[r]) {
                assertEquals(registered, body.seen.size(), body + " stepped " + body.seen.size() + " of " + registered);
                for (String context : body.seen) {
                    assertEquals("region " + region, context, body + " crossed");
                }
            }
            expectedRegion += registered;
        }
        assertTrue(expectedRegion > 0);
        for (Fake main : mains) {
            assertEquals(ticks, main.seen.size(), main + " stepped " + main.seen.size());
            assertTrue(main.seen.stream().allMatch("level"::equals), main + " crossed");
        }
    }

    @Test
    void anIdlePartitionRetiresAndANewOneTakesItsBodies() {
        Fake a = new Fake(1);
        register(a, 5, host);
        stepRegion(5);
        RigidStep first = level.partition(5);
        assertNotNull(first);
        for (int p = 0; p < 1400; p++) {
            post();
        }
        assertNull(level.partition(5), "idle partition kept");
        register(a, 5, host);
        RigidStep second = level.partition(5);
        assertNotNull(second);
        assertFalse(second == first);
        stepRegion(5);
        assertEquals(List.of("region 5", "region 5"), a.seen);
    }

    @Test
    void aPartitionWithPendingBodiesIsNeverRetired() {
        Fake a = new Fake(1);
        register(a, 5, host);
        stepRegion(5);
        RigidStep first = level.partition(5);
        for (int p = 0; p < 1199; p++) {
            post();
        }
        register(a, 5, host);
        post();
        assertSame(first, level.partition(5), "idle sweep at post 1200 must keep a partition with an inbox");
        stepRegion(5);
        assertEquals(List.of("region 5", "region 5"), a.seen);
    }

    // ---- cross-partition coupling / carry ----

    @Test
    void aTowedBodyWhoseTractorIsInAnotherRegionIsHeldNotCaptured() {
        Fake tractor = new Fake(1), towed = new Fake(2);
        host.position.put(tractor, 8);
        host.position.put(towed, 3);
        register(tractor, 8, host);
        towed.steppable = true;
        towed.state.coupling = new Tow(tractor);
        register(towed, 3, host);
        stepRegion(3);
        assertEquals(List.of("hold region 3", "complete failed=true region 3"), towed.events);
        assertTrue(StepBody.peek(towed).result.failed);
        assertFalse(StepBody.peek(towed).active);
        stepRegion(8);
        assertEquals(List.of("region 8"), tractor.seen);
    }

    @Test
    void aRiderWhoseCarrierIsInAnotherRegionIsHeld() {
        Fake carrier = new Fake(1), rider = new Fake(2);
        host.position.put(carrier, 8);
        host.position.put(rider, 3);
        rider.steppable = true;
        rider.deck.carrier = carrier;
        register(rider, 3, host);
        stepRegion(3);
        assertEquals(List.of("hold region 3", "complete failed=true region 3"), rider.events);
    }

    @Test
    void theLevelStepHoldsABodyCoupledToARegionBody() {
        activate();
        Fake tractor = new Fake(1), towed = new Fake(2);
        register(tractor, 8, host);
        towed.steppable = true;
        towed.state.coupling = new Tow(tractor);
        register(towed, -1, host);
        post();
        assertEquals(List.of("hold level", "complete failed=true level"), towed.events);
    }

    /** Vanilla path: no partition rule; the same coupled body proceeds to capture. */
    @Test
    void withoutAHostNothingIsHeld() {
        Fake tractor = new Fake(1), towed = new Fake(2);
        towed.steppable = true;
        towed.state.coupling = new Tow(tractor);
        register(towed, -1, null);
        AssertionError captured = org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, this::post);
        assertEquals("captured 2", captured.getMessage());
        assertEquals(List.of(), towed.events);
    }

    @Test
    void registrationAfterUnloadCreatesNoPartition() {
        Fake a = new Fake(1);
        register(a, 5, host);
        level.forget();
        register(a, 5, host);
        register(a, 6, host);
        assertNull(level.partition(5));
        assertNull(level.partition(6));
    }

    @Test
    void foreignCaptureIsNeverAMember() {
        Fake a = new Fake(1);
        RigidStep other = new RigidStep();
        StepBody body = StepBody.of(a);
        body.stepping = other;
        assertTrue(StepBody.member(a, other));
        assertFalse(StepBody.member(a, level), "captured by another step");
        assertFalse(StepBody.member(a, null), "predicted/idle bodies match no step");
        body.stepping = null;
        assertFalse(StepBody.member(a, other));
    }

    @Test
    void reachabilityFollowsOwnershipAndLastRegistration() {
        RigidStep partition;
        Fake same = new Fake(1), other = new Fake(2), main = new Fake(3), unknown = new Fake(4), elsewhere = new Fake(5);
        register(same, 3, host);
        register(other, 9, host);
        register(main, -1, host);
        host.position.put(same, 3);
        host.position.put(other, 9);
        host.position.put(main, 3);
        host.position.put(unknown, 3);
        host.position.put(elsewhere, 9);
        partition = level.partition(3);
        BOUND.set(3);
        try {
            assertTrue(partition.reachable(host, same));
            assertFalse(partition.reachable(host, other), "other region's body");
            assertFalse(partition.reachable(host, main), "main-thread body in this region's chunks");
            assertTrue(partition.reachable(host, unknown), "never registered, owned");
            assertFalse(partition.reachable(host, elsewhere), "never registered, not owned");
        } finally {
            BOUND.set(-1);
        }
        assertTrue(level.reachable(host, main));
        assertFalse(level.reachable(host, same));
        assertFalse(level.reachable(host, unknown), "level step never wakes an unregistered body under a host");
    }

    // ---- toggle ----

    @Test
    void toggleResolvesPerModeAndHostPresence() {
        assertNull(RigidStep.resolve(RegionStep.OFF, host));
        assertNull(RigidStep.resolve(RegionStep.OFF, null));
        assertSame(host, RigidStep.resolve(RegionStep.AUTO, host));
        assertNull(RigidStep.resolve(RegionStep.AUTO, null));
        assertSame(host, RigidStep.resolve(RegionStep.ON, host));
        assertNull(RigidStep.resolve(RegionStep.ON, null));
    }

    @Test
    void toggleSwitchesAtRuntimeAndModeIsReported() {
        Object[][] cases = {
                {RegionStep.AUTO, true, true, "on"}, {RegionStep.ON, true, true, "on"}, {RegionStep.OFF, true, false, "off"},
                {RegionStep.AUTO, false, false, "off"}, {RegionStep.ON, false, false, "off (no host)"},
                {RegionStep.OFF, false, false, "off"}};
        for (Object[] c : cases) {
            RigidStep.install((Boolean) c[1] ? host : null);
            PhysicsSettings.override(PhysicsSettings.DEFAULTS.withAsync(false).withRegionStep((RegionStep) c[0]));
            RigidStep.refreshMode();
            assertEquals((Boolean) c[2], RigidStep.regionHost() != null, c[0] + " host " + c[1]);
            assertEquals(c[3], RigidStep.regionMode(), c[0] + " host " + c[1]);
            assertTrue(RigidStep.drainStats().endsWith("regions " + c[3]));
        }
    }

    /**
     * Switch edge: a body's path is fixed when it registers. Registered on before the flip => its region steps it;
     * after => the level step; nobody is stepped twice or lost.
     */
    @Test
    void switchEdgeKeepsEveryBodyOnTheRegisteringPath() {
        RigidStep.install(host);
        PhysicsSettings.override(PhysicsSettings.DEFAULTS.withAsync(false).withRegionStep(RegionStep.AUTO));
        RigidStep.refreshMode();
        Fake early = new Fake(1), late = new Fake(2);
        register(early, 6, RigidStep.regionHost());
        PhysicsSettings.override(PhysicsSettings.DEFAULTS.withAsync(false).withRegionStep(RegionStep.OFF));
        RigidStep.refreshMode();
        register(late, 6, RigidStep.regionHost());
        post();
        stepRegion(6);
        assertEquals(List.of("region 6"), early.seen);
        assertEquals(List.of("level"), late.seen);
        PhysicsSettings.override(PhysicsSettings.DEFAULTS.withAsync(false).withRegionStep(RegionStep.ON));
        RigidStep.refreshMode();
        register(early, 6, RigidStep.regionHost());
        register(late, -1, RigidStep.regionHost());
        stepRegion(6);
        post();
        assertEquals(List.of("region 6", "region 6"), early.seen);
        assertEquals(List.of("level", "level"), late.seen);
    }

    @Test
    void retiredRouteCacheNeverSwallowsARegistration() throws Exception {
        Fake a = new Fake(1);
        AtomicBoolean stop = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicLong posts = new java.util.concurrent.atomic.AtomicLong();
        java.util.Set<RigidStep> instances = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        int rounds = 600;
        Thread region = new Thread(() -> {
            try {
                for (int i = 0; i < rounds; i++) {
                    register(a, 11, host);
                    stepRegion(11);
                    instances.add(level.partition(11));
                    if (i % 3 == 0) {
                        // Idle past IDLE posts: the next sweep may retire it while the next register runs.
                        for (long mark = posts.get() + 1300; posts.get() < mark; ) {
                            Thread.onSpinWait();
                        }
                    }
                }
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            } finally {
                stop.set(true);
            }
        });
        region.start();
        while (!stop.get()) {
            post();
            posts.incrementAndGet();
        }
        region.join();
        assertNull(failure.get());
        assertEquals(rounds, a.seen.size());
        assertTrue(instances.size() > 1, "never retired: vacuous");
    }
}

package com.wf.wflib.physics.regression;

import com.wf.wflib.physics.rigid.NarrowNative;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step-pipeline regression net over {@link HeadlessStep}. Bitwise where the engine guarantees it; tolerance where it
 * does not (candidate cache shifts the solve origin => float rounding differs, never bitwise vs uncached).
 */
class PhysicsRegressionTest {

    private static final long SEED = 0xCBF29CE484222325L;

    private static HeadlessStep.Options options(boolean nativeNarrow, boolean cache, int threads) {
        HeadlessStep.Options o = new HeadlessStep.Options();
        o.nativeNarrow = nativeNarrow;
        o.cache = cache;
        o.threads = threads;
        return o;
    }

    /** Trace hash over every tick. */
    private static long run(Supplier<Scenes.Scene> scene, HeadlessStep.Options options, int ticks) {
        Scenes.Scene s = scene.get();
        long h = SEED;
        try (HeadlessStep step = new HeadlessStep(s.world(), options)) {
            for (int t = 0; t < ticks; t++) {
                step.step(s.bodies());
                h = HeadlessStep.hash(h, s.bodies());
            }
        }
        return h;
    }

    private static boolean[] paths() {
        return NarrowNative.available() ? new boolean[]{false, true} : new boolean[]{false};
    }

    // ---- determinism ----

    @Test
    void identicalScenesReproduceBitwiseOnEveryPath() {
        for (boolean nat : paths()) {
            for (Supplier<Scenes.Scene> scene : List.<Supplier<Scenes.Scene>>of(Scenes::resting, Scenes::restingHull,
                    () -> Scenes.islands(5, 10), Scenes::fast)) {
                assertEquals(run(scene, options(nat, true, 1), 150), run(scene, options(nat, true, 1), 150), "native=" + nat);
            }
        }
    }

    /** Pins pipeline drift; PhysicsGoldenTest pins the leaves. Failure prints the actual array: paste into PINNED when physics changes on purpose. */
    @Test
    void tracesMatchThePinnedHashes() {
        long[] actual = {
                run(() -> Scenes.islands(5, 10), options(false, false, 1), 120),
                run(Scenes::fast, options(false, false, 1), 120),
                NarrowNative.available() ? run(() -> Scenes.islands(5, 10), options(true, false, 1), 120) : PINNED[2],
                NarrowNative.available() ? run(Scenes::fast, options(true, false, 1), 120) : PINNED[3]};
        assertEquals(Arrays.toString(PINNED), Arrays.toString(actual), "java islands, java fast, native islands, native fast");
    }

    private static final long[] PINNED = {340276023078987201L, -1891595065068279762L, 340276023078987201L, -1891595065068279762L};

    @Test
    void javaAndNativeNarrowphaseGiveTheSameTrace() {
        Assumptions.assumeTrue(NarrowNative.available(), "natives not built");
        for (Supplier<Scenes.Scene> scene : List.<Supplier<Scenes.Scene>>of(Scenes::resting, Scenes::restingHull,
                () -> Scenes.islands(5, 10), Scenes::fast)) {
            assertEquals(run(scene, options(false, false, 1), 120), run(scene, options(true, false, 1), 120));
        }
    }

    // ---- sync vs async ----

    @Test
    void asyncIslandSolvesMatchSyncBitwise() {
        for (boolean nat : paths()) {
            long sync = run(() -> Scenes.islands(5, 10), options(nat, true, 1), 150);
            for (int threads : new int[]{2, 4, 8}) {
                assertEquals(sync, run(() -> Scenes.islands(5, 10), options(nat, true, threads), 150),
                        "native=" + nat + " threads=" + threads);
            }
            long fastSync = run(Scenes::fast, options(nat, true, 1), 150);
            assertEquals(fastSync, run(Scenes::fast, options(nat, true, 4), 150));
        }
    }

    // ---- candidate cache ----

    /** Resident box words (same owner, version, layout) vs words rewritten every call: native output identical. */
    @Test
    void residentNativeWordsEqualFreshWordsBitwise() {
        Assumptions.assumeTrue(NarrowNative.available(), "natives not built");
        Random random = new Random(3);
        NarrowNative resident = new NarrowNative(), fresh = new NarrowNative();
        Object owner = new Object();
        float[] envelope = {-100, -100, -100, 100, 100, 100};
        for (int scene = 0; scene < 200; scene++) {
            int boxCount = 1 + random.nextInt(12);
            float[] boxes = new float[boxCount * 6];
            for (int i = 0; i < boxCount; i++) {
                float x = random.nextFloat() * 6 - 3, y = random.nextFloat() * 2 - 1, z = random.nextFloat() * 6 - 3;
                boxes[i * 6] = x;
                boxes[i * 6 + 1] = y;
                boxes[i * 6 + 2] = z;
                boxes[i * 6 + 3] = x + 0.3f + random.nextFloat() * 2;
                boxes[i * 6 + 4] = y + 0.3f + random.nextFloat() * 2;
                boxes[i * 6 + 5] = z + 0.3f + random.nextFloat() * 2;
            }
            for (int call = 0; call < 5; call++) {
                float[] frame = new float[NarrowNative.FRAME];
                Quaternionf q = new Quaternionf().rotateYXZ(random.nextFloat() * 6, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).normalize();
                frame[0] = random.nextFloat() * 2 - 1;
                frame[1] = random.nextFloat() * 2;
                frame[2] = random.nextFloat() * 2 - 1;
                frame[3] = q.x;
                frame[4] = q.y;
                frame[5] = q.z;
                frame[6] = q.w;
                frame[7] = 0.4f + random.nextFloat();
                frame[8] = 0.4f + random.nextFloat();
                frame[9] = 0.4f + random.nextFloat();
                frame[10] = 2;
                int a = resident.worldContacts(1, frame, new long[1], true, envelope, boxes, boxCount, new float[0], 0, 0.1f, 1f, owner, scene);
                int b = fresh.worldContacts(1, frame, new long[1], true, envelope, boxes, boxCount, new float[0], 0, 0.1f, 1f, null, 0);
                assertEquals(b, a, "count scene " + scene + " call " + call);
                for (int k = 0; k < a; k++) {
                    assertEquals(Float.floatToRawIntBits(fresh.x(k)), Float.floatToRawIntBits(resident.x(k)));
                    assertEquals(Float.floatToRawIntBits(fresh.y(k)), Float.floatToRawIntBits(resident.y(k)));
                    assertEquals(Float.floatToRawIntBits(fresh.z(k)), Float.floatToRawIntBits(resident.z(k)));
                    assertEquals(Float.floatToRawIntBits(fresh.normalX(k)), Float.floatToRawIntBits(resident.normalX(k)));
                    assertEquals(Float.floatToRawIntBits(fresh.normalY(k)), Float.floatToRawIntBits(resident.normalY(k)));
                    assertEquals(Float.floatToRawIntBits(fresh.normalZ(k)), Float.floatToRawIntBits(resident.normalZ(k)));
                    assertEquals(Float.floatToRawIntBits(fresh.separation(k)), Float.floatToRawIntBits(resident.separation(k)));
                    assertEquals(fresh.tagA(k), resident.tagA(k));
                    assertEquals(fresh.source(k), resident.source(k));
                }
            }
        }
    }

    /** Version bump => resident words replaced: a changed box set never answers from stale words. */
    @Test
    void residentWordsFollowTheVersion() {
        Assumptions.assumeTrue(NarrowNative.available(), "natives not built");
        NarrowNative resident = new NarrowNative(), fresh = new NarrowNative();
        Object owner = new Object();
        float[] envelope = {-100, -100, -100, 100, 100, 100};
        float[] frame = {0, 0.45f, 0, 0, 0, 0, 1, 0.5f, 0.5f, 0.5f, 1};
        float[] floor = {-5, -1, -5, 5, 0, 5};
        float[] pit = {-5, -1, -5, 5, -0.5f, 5};
        assertTrue(resident.worldContacts(1, frame, new long[1], true, envelope, floor, 1, new float[0], 0, 0.1f, 1f, owner, 1) > 0);
        int after = resident.worldContacts(1, frame, new long[1], true, envelope, pit, 1, new float[0], 0, 0.1f, 1f, owner, 2);
        int expected = fresh.worldContacts(1, frame, new long[1], true, envelope, pit, 1, new float[0], 0, 0.1f, 1f, null, 0);
        assertEquals(expected, after);
        assertEquals(0, after, "pit floor 0.5 below the body is out of reach");
    }

    /**
     * Pipeline: cache hits on a still body (non-vacuous), results track the uncached trace within float rounding of the
     * shifted solve origin; moving body never hits.
     */
    @Test
    void cachedStillBodyTracksTheUncachedTrace() {
        Assumptions.assumeTrue(NarrowNative.available(), "natives not built");
        for (Supplier<Scenes.Scene> scene : List.<Supplier<Scenes.Scene>>of(Scenes::resting, Scenes::restingHull)) {
            Scenes.Scene cached = scene.get(), plain = scene.get();
            try (HeadlessStep a = new HeadlessStep(cached.world(), options(true, true, 1));
                 HeadlessStep b = new HeadlessStep(plain.world(), options(true, false, 1))) {
                for (int t = 0; t < 300; t++) {
                    a.step(cached.bodies());
                    b.step(plain.bodies());
                    HeadlessStep.Body x = cached.bodies().get(0), y = plain.bodies().get(0);
                    assertEquals(y.contacts, x.contacts, "contact count tick " + t);
                    assertEquals(y.x, x.x, 1e-3, "x tick " + t);
                    assertEquals(y.y, x.y, 1e-3, "y tick " + t);
                    assertEquals(y.z, x.z, 1e-3, "z tick " + t);
                }
                assertTrue(cached.bodies().get(0).cacheHits > 200, "hits " + cached.bodies().get(0).cacheHits);
                assertEquals(0, plain.bodies().get(0).cacheHits);
            }
        }
        Scenes.Scene fast = Scenes.fast();
        try (HeadlessStep a = new HeadlessStep(fast.world(), options(true, true, 1))) {
            for (int t = 0; t < 20; t++) {
                a.step(fast.bodies());
            }
            assertEquals(0, fast.bodies().get(0).cacheHits, "fast body must always miss");
        }
    }

    /** World edit => cache invalidated: the body falls through the removed floor exactly as the uncached run does. */
    @Test
    void cacheInvalidatesWhenTheWorldChanges() {
        Assumptions.assumeTrue(NarrowNative.available(), "natives not built");
        double[] finalY = new double[2];
        for (int mode = 0; mode < 2; mode++) {
            Scenes.Scene s = Scenes.resting();
            try (HeadlessStep step = new HeadlessStep(s.world(), options(true, mode == 0, 1))) {
                for (int t = 0; t < 120; t++) {
                    step.step(s.bodies());
                }
                s.world().clear();
                s.world().add(48, -1, 48, 64, 0, 64);
                for (int t = 0; t < 40; t++) {
                    step.step(s.bodies());
                }
                finalY[mode] = s.bodies().get(0).y;
            }
        }
        assertTrue(finalY[0] < -0.5, "cached run kept resting on the removed floor: y=" + finalY[0]);
        assertEquals(finalY[1], finalY[0], 1e-3);
    }

    /** Body leaving the padded candidate bounds re-collects: crossing the pad is seamless against uncached. */
    @Test
    void creepingBodyLeavingThePadStaysCorrect() {
        Assumptions.assumeTrue(NarrowNative.available(), "natives not built");
        Scenes.Scene a = Scenes.fast(), b = Scenes.fast();
        a.bodies().get(0).moving(0.015f, 0, 0);
        b.bodies().get(0).moving(0.015f, 0, 0);
        try (HeadlessStep cached = new HeadlessStep(a.world(), options(true, true, 1));
             HeadlessStep plain = new HeadlessStep(b.world(), options(true, false, 1))) {
            for (int t = 0; t < 600; t++) {
                cached.step(a.bodies());
                plain.step(b.bodies());
                assertEquals(b.bodies().get(0).contacts, a.bodies().get(0).contacts, "tick " + t);
                assertEquals(b.bodies().get(0).x, a.bodies().get(0).x, 1e-3, "tick " + t);
                assertEquals(b.bodies().get(0).y, a.bodies().get(0).y, 1e-3, "tick " + t);
            }
        }
        assertTrue(a.bodies().get(0).cacheMisses >= 2, "pad crossed => recollected");
    }

    // ---- registration order ----

    /** Property: any registration order => same trace (RigidStep sorts by id). */
    @Test
    void shuffledRegistrationOrderGivesAnIdenticalFinalHash() {
        for (boolean nat : paths()) {
            long expected = run(() -> Scenes.islands(5, 10), options(nat, true, 1), 40);
            for (long seed = 0; seed < 150; seed++) {
                Scenes.Scene s = Scenes.islands(5, 10);
                Random random = new Random(seed);
                long h = SEED;
                try (HeadlessStep step = new HeadlessStep(s.world(), options(nat, true, 1))) {
                    List<HeadlessStep.Body> registered = new ArrayList<>(s.bodies());
                    for (int t = 0; t < 40; t++) {
                        Collections.shuffle(registered, random);
                        step.step(registered);
                        h = HeadlessStep.hash(h, s.bodies());
                    }
                }
                assertEquals(expected, h, "native=" + nat + " seed " + seed);
            }
        }
    }

    // ---- multi-island independence ----

    @Test
    void islandSolveOrderDoesNotChangeTheResult() {
        for (boolean nat : paths()) {
            long forward = run(() -> Scenes.islands(5, 10), options(nat, true, 1), 100);
            HeadlessStep.Options reverse = options(nat, true, 1);
            reverse.order = HeadlessStep.Order.REVERSE;
            assertEquals(forward, run(() -> Scenes.islands(5, 10), reverse, 100), "reverse");
            for (long seed = 0; seed < 20; seed++) {
                HeadlessStep.Options shuffled = options(nat, true, 1);
                shuffled.order = HeadlessStep.Order.SHUFFLED;
                shuffled.orderSeed = seed * 7919;
                assertEquals(forward, run(() -> Scenes.islands(5, 10), shuffled, 100), "seed " + seed);
            }
        }
    }

    @Test
    void anIslandIsUnaffectedByDistantIslands() {
        for (boolean nat : paths()) {
            Scenes.Scene all = Scenes.islandsInterleaved(5, 10);
            Scenes.Scene alone = Scenes.islandsInterleaved(5, 10);
            // keep cluster 0 only: ids i * 5 + 1 ... of islands()' first cluster
            List<HeadlessStep.Body> kept = new ArrayList<>();
            for (HeadlessStep.Body b : alone.bodies()) {
                if ((b.id - 1) % 5 == 0) {
                    kept.add(b);
                }
            }
            assertEquals(10, kept.size());
            try (HeadlessStep a = new HeadlessStep(all.world(), options(nat, true, 1));
                 HeadlessStep b = new HeadlessStep(alone.world(), options(nat, true, 1))) {
                for (int t = 0; t < 120; t++) {
                    a.step(all.bodies());
                    b.step(kept);
                }
            }
            for (HeadlessStep.Body k : kept) {
                HeadlessStep.Body twin = all.bodies().stream().filter(o -> o.id == k.id).findFirst().orElseThrow();
                assertEquals(Double.doubleToRawLongBits(twin.x), Double.doubleToRawLongBits(k.x), "x #" + k.id);
                assertEquals(Double.doubleToRawLongBits(twin.y), Double.doubleToRawLongBits(k.y), "y #" + k.id);
                assertEquals(Double.doubleToRawLongBits(twin.z), Double.doubleToRawLongBits(k.z), "z #" + k.id);
                assertEquals(Float.floatToRawIntBits(twin.v.y), Float.floatToRawIntBits(k.v.y), "vy #" + k.id);
            }
        }
    }

    @Test
    void separatedClustersFormSeparateIslands() {
        Scenes.Scene s = Scenes.islands(5, 10);
        try (HeadlessStep step = new HeadlessStep(s.world(), options(false, false, 1))) {
            step.step(s.bodies());
            assertEquals(5, step.lastIslands);
        }
    }

    // ---- resting / sleeping ----

    /** A body the engine may skip (resting) must really be at rest: sub-mm motion, steady 4-point support. */
    @Test
    void aRestingBodySettlesToTrueRest() {
        for (boolean nat : paths()) {
            for (Supplier<Scenes.Scene> scene : List.<Supplier<Scenes.Scene>>of(Scenes::resting, Scenes::restingHull)) {
                Scenes.Scene s = scene.get();
                HeadlessStep.Body b = s.bodies().get(0);
                try (HeadlessStep step = new HeadlessStep(s.world(), options(nat, true, 1))) {
                    for (int t = 0; t < 200; t++) {
                        step.step(s.bodies());
                    }
                    double y0 = b.y;
                    float restTop = b.ey;
                    assertEquals(restTop, y0, 3e-3, "rest height");
                    for (int t = 0; t < 100; t++) {
                        double x = b.x, y = b.y, z = b.z;
                        step.step(s.bodies());
                        assertTrue(Math.abs(b.x - x) < 1e-4 && Math.abs(b.y - y) < 1e-4 && Math.abs(b.z - z) < 1e-4,
                                "drift tick " + t);
                        assertTrue(b.v.length() < 2e-3 && b.w.length() < 2e-3, "speed " + b.v.length() + " spin " + b.w.length());
                        assertTrue(b.contacts >= 3 && b.contacts <= 8, "contacts " + b.contacts);
                    }
                }
            }
        }
    }

    /** Freezing a settled body (skip the solve) stays within a millimetre of solving it on: the skip's license. */
    @Test
    void skippingASettledBodyMatchesSolvingIt() {
        Scenes.Scene solved = Scenes.resting(), frozen = Scenes.resting();
        try (HeadlessStep a = new HeadlessStep(solved.world(), options(NarrowNative.available(), true, 1));
             HeadlessStep b = new HeadlessStep(frozen.world(), options(NarrowNative.available(), true, 1))) {
            for (int t = 0; t < 200; t++) {
                a.step(solved.bodies());
                b.step(frozen.bodies());
            }
            HeadlessStep.Body f = frozen.bodies().get(0), s = solved.bodies().get(0);
            double y = f.y;
            for (int t = 0; t < 400; t++) {
                a.step(solved.bodies());
            }
            assertEquals(y, s.y, 1e-3);
            assertEquals(f.x, s.x, 1e-3);
            assertEquals(f.z, s.z, 1e-3);
        }
    }

    /** A settled body hit by a neighbour must wake: nudged pair moves, not frozen. */
    @Test
    void aSettledBodyReactsToAnImpact() {
        Scenes.Scene s = Scenes.resting();
        s.bodies().add(new HeadlessStep.Body(2, 14, 0.6 + 0.001, 8, 1.8f, 0.6f, 0.9f, 12).moving(-0.3f, 0, 0));
        try (HeadlessStep step = new HeadlessStep(s.world(), options(NarrowNative.available(), true, 1))) {
            double x0 = s.bodies().get(0).x;
            for (int t = 0; t < 60; t++) {
                step.step(s.bodies());
            }
            assertTrue(Math.abs(s.bodies().get(0).x - x0) > 0.05, "struck body did not move");
        }
    }

    // ---- contact count / solver iteration sanity ----

    @Test
    void contactCountsStayBoundedOnEveryScene() {
        for (boolean nat : paths()) {
            for (Supplier<Scenes.Scene> scene : List.<Supplier<Scenes.Scene>>of(Scenes::resting, Scenes::restingHull,
                    () -> Scenes.islands(5, 10), Scenes::fast)) {
                Scenes.Scene s = scene.get();
                try (HeadlessStep step = new HeadlessStep(s.world(), options(nat, true, 1))) {
                    for (int t = 0; t < 200; t++) {
                        step.step(s.bodies());
                        for (HeadlessStep.Body b : s.bodies()) {
                            assertTrue(b.contacts <= 12, "world contacts " + b.contacts + " tick " + t);
                        }
                        assertTrue(step.lastContacts <= s.bodies().size() * 40, "island contacts " + step.lastContacts);
                    }
                }
            }
        }
    }

    /** Penetration of an 8-box stack is non-increasing in iteration count; no run gains energy or blows up. */
    @Test
    void moreSolverIterationsNeverDeepenPenetrationOrAddEnergy() {
        double previous = Double.MAX_VALUE;
        for (int iterations = 1; iterations <= 3; iterations++) {
            Scenes.Scene s = Scenes.islands(1, 8);
            HeadlessStep.Options o = options(NarrowNative.available(), true, 1);
            o.iterations = iterations;
            double deepest = 0, energy = 0;
            try (HeadlessStep step = new HeadlessStep(s.world(), o)) {
                for (int t = 0; t < 240; t++) {
                    step.step(s.bodies());
                    if (t >= 120) {
                        for (HeadlessStep.Body b : s.bodies()) {
                            if (b.id <= 2) {
                                deepest = Math.max(deepest, 0.5 - b.y);
                            }
                        }
                    }
                }
                for (HeadlessStep.Body b : s.bodies()) {
                    energy += 0.5 * b.mass * b.v.lengthSquared();
                    assertTrue(Double.isFinite(b.x + b.y + b.z));
                }
            }
            assertTrue(deepest <= previous + 1e-3, "iterations " + iterations + " penetration " + deepest + " > " + previous);
            assertTrue(deepest < 0.06, "floor penetration " + deepest + " iterations " + iterations);
            assertTrue(energy < 0.05, "residual energy " + energy + " iterations " + iterations);
            previous = deepest;
        }
    }

    /** Light boxes under a heavy slab, the solver's supported stacking case (see RigidSolverTest). */
    @Test
    void aLightBottomTowerSettlesAtTheDefaultIterationCount() {
        Scenes.Scene s = new Scenes.Scene(new HeadlessStep.World().floor(0, 0, 32, 32), new ArrayList<>());
        s.bodies().add(new HeadlessStep.Body(1, 7.35, 0.5, 8, 0.5f, 0.5f, 0.5f, 1.5f));
        s.bodies().add(new HeadlessStep.Body(2, 8.7, 0.5, 8, 0.55f, 0.5f, 0.5f, 1.5f));
        HeadlessStep.Body top = new HeadlessStep.Body(3, 8, 1.5, 8, 1.3f, 0.5f, 0.7f, 65);
        s.bodies().add(top);
        try (HeadlessStep step = new HeadlessStep(s.world(), options(NarrowNative.available(), true, 1))) {
            for (int t = 0; t < 400; t++) {
                step.step(s.bodies());
            }
        }
        assertEquals(1.5, top.y, 0.02);
        assertTrue(top.w.length() < 1e-3, "rocking " + top.w.length());
    }

    @Test
    void resultsAreFinite() {
        Scenes.Scene s = Scenes.fast();
        try (HeadlessStep step = new HeadlessStep(s.world(), options(NarrowNative.available(), true, 1))) {
            for (int t = 0; t < 400; t++) {
                step.step(s.bodies());
                HeadlessStep.Body b = s.bodies().get(0);
                assertTrue(Double.isFinite(b.x + b.y + b.z) && Float.isFinite(b.v.x + b.v.y + b.v.z + b.w.x + b.w.y + b.w.z)
                        && Float.isFinite(b.q.x + b.q.y + b.q.z + b.q.w), HeadlessStep.describe(b));
            }
        }
        assertNotEquals(0.0, s.bodies().get(0).x);
    }

}

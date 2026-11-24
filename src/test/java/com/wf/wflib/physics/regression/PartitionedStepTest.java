package com.wf.wflib.physics.regression;

import com.wf.wflib.physics.rigid.NarrowNative;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Region partitions over {@link HeadlessStep}: one instance per partition (own pair caches, as RigidStep's), all
 * stepped concurrently each tick from their own threads sharing one helper pool + per-thread scratch. Disjoint islands
 * => bitwise the single level step.
 */
class PartitionedStepTest {

    private static final int ISLANDS = 6, PER_ISLAND = 8, TICKS = 120;

    private static boolean[] paths() {
        return NarrowNative.available() ? new boolean[]{false, true} : new boolean[]{false};
    }

    private static HeadlessStep.Options options(boolean nativeNarrow, int threads, HeadlessStep.Shared shared) {
        HeadlessStep.Options o = new HeadlessStep.Options();
        o.nativeNarrow = nativeNarrow;
        o.threads = threads;
        o.shared = shared;
        return o;
    }

    /** Per-tick trace hash of every body, one step over all. */
    private static long global(long seed, boolean nativeNarrow, int threads) {
        Scenes.Scene s = Scenes.islandsInterleaved(ISLANDS, PER_ISLAND, seed);
        long h = 1;
        try (HeadlessStep step = new HeadlessStep(s.world(), options(nativeNarrow, threads, null))) {
            for (int t = 0; t < TICKS; t++) {
                step.step(s.bodies());
                h = HeadlessStep.hash(h, s.bodies());
            }
        }
        return h;
    }

    /** Island c (ids c + 1, c + 1 + ISLANDS, ...) -> partition drawn from {@code seed}; partitions step concurrently. */
    private static long partitioned(long seed, boolean nativeNarrow, int threads, int partitions) throws Exception {
        Scenes.Scene s = Scenes.islandsInterleaved(ISLANDS, PER_ISLAND, seed);
        Random random = new Random(seed * 31 + partitions);
        int[] partitionOf = new int[ISLANDS];
        for (int c = 0; c < ISLANDS; c++) {
            partitionOf[c] = random.nextInt(partitions);
        }
        List<List<HeadlessStep.Body>> groups = new ArrayList<>();
        for (int p = 0; p < partitions; p++) {
            groups.add(new ArrayList<>());
        }
        for (HeadlessStep.Body b : s.bodies()) {
            groups.get(partitionOf[(b.id - 1) % ISLANDS]).add(b);
        }
        // Partition registration order != id order: the step sorts.
        for (List<HeadlessStep.Body> group : groups) {
            java.util.Collections.shuffle(group, random);
        }
        ExecutorService regions = Executors.newFixedThreadPool(partitions);
        HeadlessStep.Shared shared = threads > 1 ? new HeadlessStep.Shared(threads - 1, 1) : null;
        List<HeadlessStep> steps = new ArrayList<>();
        try {
            for (int p = 0; p < partitions; p++) {
                steps.add(new HeadlessStep(s.world(), options(nativeNarrow, threads, shared)));
            }
            long h = 1;
            int contacts = 0;
            for (int t = 0; t < TICKS; t++) {
                List<Callable<Integer>> tasks = new ArrayList<>();
                for (int p = 0; p < partitions; p++) {
                    HeadlessStep step = steps.get(p);
                    List<HeadlessStep.Body> group = groups.get(p);
                    tasks.add(() -> {
                        step.step(group);
                        return step.lastContacts;
                    });
                }
                for (Future<Integer> done : regions.invokeAll(tasks)) {
                    contacts += done.get();
                }
                h = HeadlessStep.hash(h, s.bodies());
            }
            assertTrue(contacts > 0, "vacuous: no contacts");
            return h;
        } finally {
            steps.forEach(HeadlessStep::close);
            if (shared != null) {
                shared.close();
            }
            regions.shutdownNow();
        }
    }

    @Test
    void disjointIslandPartitionsMatchTheLevelStepBitwise() throws Exception {
        for (boolean nat : paths()) {
            for (long seed = 0; seed < 4; seed++) {
                long expected = global(seed, nat, 1);
                for (int threads : new int[]{1, 2, 4}) {
                    assertEquals(expected, global(seed, nat, threads), "level step threads " + threads);
                    for (int partitions : new int[]{2, 3, ISLANDS}) {
                        assertEquals(expected, partitioned(seed, nat, threads, partitions),
                                "native=" + nat + " seed " + seed + " threads " + threads + " partitions " + partitions);
                    }
                }
            }
        }
    }

    /** One partition holding every island == the level step (partition instance is the level step's code). */
    @Test
    void onePartitionIsTheLevelStep() throws Exception {
        for (boolean nat : paths()) {
            assertEquals(global(9, nat, 1), partitioned(9, nat, 2, 1));
        }
    }
}

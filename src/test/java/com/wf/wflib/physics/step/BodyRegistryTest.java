package com.wf.wflib.physics.step;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyRegistryTest {

    private static final int THREADS = 8, PER_THREAD = 20_000;

    @Test
    void concurrentAddAndDrainLosesNothing() throws Exception {
        BodyRegistry<Integer> registry = new BodyRegistry<Integer>(Comparator.<Integer>naturalOrder());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean producing = new AtomicBoolean(true);
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> producers = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            int base = t * PER_THREAD;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < PER_THREAD; i++) {
                        registry.add(base + i);
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });
            producers.add(thread);
            thread.start();
        }
        List<Integer> seen = new ArrayList<>();
        Thread stepper = new Thread(() -> {
            try {
                start.await();
                while (producing.get()) {
                    step(registry, seen);
                }
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            }
        });
        stepper.start();
        start.countDown();
        for (Thread thread : producers) {
            thread.join();
        }
        producing.set(false);
        stepper.join();
        step(registry, seen);
        assertNull(failure.get());
        assertEquals(THREADS * PER_THREAD, seen.size());
        List<Integer> sorted = new ArrayList<>(seen);
        Collections.sort(sorted);
        for (int i = 0; i < sorted.size(); i++) {
            assertEquals(i, sorted.get(i));
        }
    }

    @Test
    void drainIsSortedAndClearEmpties() {
        BodyRegistry<Integer> registry = new BodyRegistry<Integer>(Comparator.<Integer>naturalOrder());
        for (int i : new int[] {5, 1, 4, 2, 3}) {
            registry.add(i);
        }
        assertEquals(List.of(1, 2, 3, 4, 5), registry.drain());
        registry.clear();
        assertTrue(registry.drain().isEmpty());
    }

    @Test
    void pendingSeesUndrainedAddsOnly() {
        BodyRegistry<Integer> registry = new BodyRegistry<Integer>(Comparator.<Integer>naturalOrder());
        assertTrue(!registry.pending());
        registry.add(1);
        assertTrue(registry.pending());
        registry.drain();
        assertTrue(!registry.pending());
        registry.clear();
    }

    /** One step: drain asserts sorted, records, clears. */
    private static void step(BodyRegistry<Integer> registry, List<Integer> seen) {
        List<Integer> batch = registry.drain();
        for (int i = 1; i < batch.size(); i++) {
            assertTrue(batch.get(i - 1) <= batch.get(i));
        }
        seen.addAll(batch);
        registry.clear();
    }
}

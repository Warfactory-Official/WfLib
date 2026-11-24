package com.wf.wflib.core.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainThreadOpsTest {

    @Test
    void drainRunsInEnqueueOrder() {
        MainThreadOps ops = new MainThreadOps();
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int n = i;
            ops.enqueue("a", () -> seen.add(n));
        }
        assertTrue(ops.pending());
        ops.drain();
        assertEquals(List.of(0, 1, 2, 3, 4), seen);
        assertFalse(ops.pending());
    }

    @Test
    void inlineRunFollowsQueuedOps() {
        MainThreadOps ops = new MainThreadOps();
        List<String> seen = new ArrayList<>();
        ops.enqueue("a", () -> seen.add("queued"));
        ops.runOnMain(() -> seen.add("inline"));
        assertEquals(List.of("queued", "inline"), seen);
    }

    @Test
    void failingOpDoesNotAbortDrain() {
        MainThreadOps ops = new MainThreadOps();
        AtomicInteger ran = new AtomicInteger();
        ops.enqueue("a", () -> {
            throw new IllegalStateException("boom");
        });
        ops.enqueue("a", ran::incrementAndGet);
        ops.drain();
        assertEquals(1, ran.get());
    }

    @Test
    void dropScopeRemovesOnlyThatScope() {
        MainThreadOps ops = new MainThreadOps();
        List<String> seen = new ArrayList<>();
        ops.enqueue("a", () -> seen.add("a"));
        ops.enqueue("b", () -> seen.add("b"));
        ops.dropScope("a");
        ops.drain();
        assertEquals(List.of("b"), seen);
    }

    @Test
    void concurrentProducersLoseNothingAndKeepPerProducerOrder() throws Exception {
        MainThreadOps ops = new MainThreadOps();
        int producers = 4;
        int each = 5000;
        List<int[]> seen = new ArrayList<>();
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int p = 0; p < producers; p++) {
            int id = p;
            Thread t = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = 0; i < each; i++) {
                    int n = i;
                    ops.enqueue("s", () -> seen.add(new int[]{id, n}));
                }
            });
            threads.add(t);
            t.start();
        }
        go.countDown();
        for (Thread t : threads) {
            t.join();
        }
        ops.drain();
        assertEquals(producers * each, seen.size());
        int[] last = new int[producers];
        java.util.Arrays.fill(last, -1);
        for (int[] e : seen) {
            assertTrue(e[1] > last[e[0]]);
            last[e[0]] = e[1];
        }
    }
}

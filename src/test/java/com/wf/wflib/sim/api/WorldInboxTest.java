package com.wf.wflib.sim.api;

import com.wf.wflib.core.api.WorldThread;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldInboxTest {

    @AfterEach
    void disarm() {
        WorldThread.clear();
    }

    @Test
    void offWorldOnlyWhenArmedAndForeign() throws Exception {
        assertFalse(WorldInbox.offWorld());
        WorldThread.mark();
        assertFalse(WorldInbox.offWorld());
        AtomicBoolean foreign = new AtomicBoolean();
        Thread t = new Thread(() -> foreign.set(WorldInbox.offWorld()));
        t.start();
        t.join();
        assertTrue(foreign.get());
    }

    @Test
    void concurrentPostsAllDrainFifoPerProducer() throws Exception {
        WorldInbox<int[]> inbox = new WorldInbox<>();
        int producers = 8;
        int each = 5000;
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
                    inbox.post(new int[]{id, i});
                }
            });
            threads.add(t);
            t.start();
        }
        go.countDown();
        for (Thread t : threads) {
            t.join();
        }
        int[] next = new int[producers];
        int[] total = {0};
        inbox.drain(v -> {
            assertEquals(next[v[0]]++, v[1]);
            total[0]++;
        });
        assertEquals(producers * each, total[0]);
        assertTrue(inbox.isEmpty());
    }
}

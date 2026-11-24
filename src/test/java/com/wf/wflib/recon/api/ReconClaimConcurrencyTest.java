package com.wf.wflib.recon.api;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconClaimConcurrencyTest {

    @Test
    void exactlyOneBatteryTakesATrack() throws Exception {
        int threads = 8, rounds = 500;
        ReconNetwork net = new ReconNetwork(1L);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int r = 0; r < rounds; r++) {
            TrackId track = new TrackId(r);
            AtomicInteger winners = new AtomicInteger();
            CountDownLatch go = new CountDownLatch(1);
            Future<?>[] fs = new Future<?>[threads];
            for (int t = 0; t < threads; t++) {
                long claimant = 100 + t;
                fs[t] = pool.submit(() -> {
                    go.await();
                    if (net.claim(track, claimant, 10L, 120)) {
                        winners.incrementAndGet();
                    }
                    return null;
                });
            }
            go.countDown();
            for (Future<?> f : fs) {
                f.get();
            }
            assertEquals(1, winners.get(), "round " + r);
        }
        pool.shutdownNow();
    }

    @Test
    void claimExpiresAndHolderRenews() {
        ReconNetwork net = new ReconNetwork(2L);
        TrackId track = new TrackId(7);
        assertTrue(net.claim(track, 1, 0L, 10));
        assertTrue(net.claim(track, 1, 5L, 10));
        assertFalse(net.claim(track, 2, 14L, 10));
        assertTrue(net.claimedByOther(track, 2, 14L));
        assertTrue(net.claim(track, 2, 15L, 10));
    }
}

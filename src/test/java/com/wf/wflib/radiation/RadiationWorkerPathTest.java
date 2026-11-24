package com.wf.wflib.radiation;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadiationWorkerPathTest {

    private static class Recorder implements RadiationWriteQueue.Sink<String> {
        final List<String> log = new ArrayList<>();

        public void add(String w, long pos, double a) {
            log.add("add " + w + " " + pos + " " + a);
        }

        public void emit(String w, long pos, double e, double s) {
            log.add("emit " + w + " " + pos + " " + e + " " + s);
        }

        public void set(String w, long pos, double a) {
            log.add("set " + w + " " + pos + " " + a);
        }

        public void mark(String w, long sck) {
            log.add("mark " + w + " " + sck);
        }

        public void dirty(String w, long ck, int slot) {
            log.add("dirty " + w + " " + ck + " " + slot);
        }
    }

    @Test
    void drainIsFifoAndEmptiesQueue() {
        RadiationWriteQueue<String> q = new RadiationWriteQueue<>();
        q.add("a", 1, 2.5);
        q.set("a", 2, 3.0);
        q.emit("b", 3, 4.0, 5.0);
        q.mark("b", 6);
        q.dirty("a", 7, 3);
        Recorder r = new Recorder();
        assertEquals(5, q.drain(r, 100));
        assertEquals(List.of("add a 1 2.5", "set a 2 3.0", "emit b 3 4.0 5.0", "mark b 6", "dirty a 7 3"), r.log);
        assertTrue(q.isEmpty());
    }

    @Test
    void drainRespectsLimitAndKeepsRemainderInOrder() {
        RadiationWriteQueue<String> q = new RadiationWriteQueue<>();
        for (int i = 0; i < 5; i++) q.mark("w", i);
        Recorder r = new Recorder();
        assertEquals(2, q.drain(r, 2));
        assertEquals(3, q.drain(r, 10));
        assertEquals(List.of("mark w 0", "mark w 1", "mark w 2", "mark w 3", "mark w 4"), r.log);
    }

    @Test
    void failingOpDoesNotLoseTheRest() {
        RadiationWriteQueue<String> q = new RadiationWriteQueue<>();
        q.mark("w", 1);
        q.mark("w", 2);
        q.mark("w", 3);
        List<Long> seen = new ArrayList<>();
        RadiationWriteQueue.Sink<String> sink = new Recorder() {
            @Override
            public void mark(String w, long sck) {
                seen.add(sck);
                if (sck == 2) throw new IllegalStateException("boom");
            }
        };
        assertThrows(IllegalStateException.class, () -> q.drain(sink, 10));
        assertEquals(List.of(1L, 2L, 3L), seen);
        assertTrue(q.isEmpty());
    }

    @Test
    void concurrentProducersLoseNothingAndKeepPerProducerOrder() throws Exception {
        RadiationWriteQueue<String> q = new RadiationWriteQueue<>();
        int producers = 4, each = 5000;
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int p = 0; p < producers; p++) {
            long base = (long) p << 32;
            Thread t = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = 0; i < each; i++) q.mark("w", base | i);
            });
            threads.add(t);
            t.start();
        }
        go.countDown();
        for (Thread t : threads) t.join();
        long[] next = new long[producers];
        AtomicInteger count = new AtomicInteger();
        RadiationWriteQueue.Sink<String> sink = new Recorder() {
            @Override
            public void mark(String w, long sck) {
                int p = (int) (sck >>> 32);
                assertEquals(next[p]++, sck & 0xFFFFFFFFL);
                count.incrementAndGet();
            }
        };
        q.drain(sink, Integer.MAX_VALUE);
        assertEquals(producers * each, count.get());
    }

    private static final long CK = 0x0000_0001_0000_0002L;

    /** One chunk id 0, 2 sections: slot0 per {@code kind0}, slot1 per {@code kind1}. */
    private static RadiationSnapshot snapshot(int kind0, int kind1, short[] pockets, double[] multiData) {
        Long2IntOpenHashMap map = new Long2IntOpenHashMap();
        map.defaultReturnValue(-1);
        map.put(CK, 0);
        long kinds = kind0 | ((long) kind1 << 2);
        short[][] pd = {pockets, null};
        double[][] multi = {multiData, null};
        return new RadiationSnapshot(map, new long[]{CK}, new long[]{kinds}, new double[]{7.0D, 9.0D},
                pd, multi, new Object[2], 2, 1);
    }

    private static long pos(int x, int y, int z) {
        return net.minecraft.core.BlockPos.asLong(x, y, z);
    }

    @Test
    void snapshotReadsUniformSingleAndMulti() {
        short[] pockets = new short[4096];
        java.util.Arrays.fill(pockets, (short) -1);
        pockets[SectionKeys.blockPosToLocal(pos(1, 2, 3))] = 1;
        pockets[SectionKeys.blockPosToLocal(pos(0, 0, 0))] = 0;
        RadiationSnapshot multi = snapshot(RadiationSystemNT.KIND_MULTI, RadiationSystemNT.KIND_UNI, pockets,
                new double[]{10.0D, 0.0D, 20.0D, 0.0D});
        assertEquals(20.0D, multi.read(CK, 0, pos(1, 2, 3)));
        assertEquals(10.0D, multi.read(CK, 0, pos(0, 0, 0)));
        assertEquals(9.0D, multi.read(CK, 1, pos(5, 5, 5)));
        RadiationSnapshot single = snapshot(RadiationSystemNT.KIND_SINGLE, RadiationSystemNT.KIND_UNI, pockets, null);
        assertEquals(7.0D, single.read(CK, 0, pos(1, 2, 3)));
    }

    @Test
    void unknownChunkReadsZeroWithoutRebuildRequest() {
        RadiationSnapshot s = snapshot(RadiationSystemNT.KIND_UNI, RadiationSystemNT.KIND_UNI, null, null);
        assertEquals(0.0D, s.read(CK + 1, 0, pos(0, 0, 0)));
    }

    @Test
    void unreadySectionRequestsRebuildOncePerSnapshot() {
        short[] pockets = new short[4096];
        java.util.Arrays.fill(pockets, (short) -1);
        RadiationSnapshot s = snapshot(RadiationSystemNT.KIND_NONE, RadiationSystemNT.KIND_MULTI, pockets,
                new double[]{1.0D, 0.0D});
        assertTrue(Double.isNaN(s.read(CK, 0, pos(0, 0, 0))));
        assertEquals(0.0D, s.read(CK, 0, pos(0, 0, 0)));
        assertTrue(Double.isNaN(s.read(CK, 1, pos(0, 0, 0))));
        assertEquals(0.0D, s.read(CK, 1, pos(0, 0, 0)));
    }

    @Test
    void concurrentUnreadyReadsClaimExactlyOnce() throws Exception {
        RadiationSnapshot s = snapshot(RadiationSystemNT.KIND_NONE, RadiationSystemNT.KIND_UNI, null, null);
        AtomicInteger claims = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            Thread th = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = 0; i < 1000; i++) {
                    if (Double.isNaN(s.read(CK, 0, pos(0, 0, 0)))) claims.incrementAndGet();
                }
            });
            threads.add(th);
            th.start();
        }
        go.countDown();
        for (Thread t : threads) t.join();
        assertEquals(1, claims.get());
    }
}

package com.wf.wflib.missile;

import com.wf.wflib.missile.api.IMissileListener;
import com.wf.wflib.missile.api.MissileListenerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissileListenerConcurrencyTest {

    private record Fixed(Vec3 center, double range) implements IMissileListener {
        public Vec3 listenerCenter() {
            return center;
        }

        public double listenerRange() {
            return range;
        }

        public boolean listenerValid() {
            return true;
        }
    }

    @Test
    void registerRefreshDeregisterWhileMissilesRead() throws Exception {
        MissileListenerData data = new MissileListenerData();
        int writers = 6, perWriter = 300;
        ExecutorService pool = Executors.newFixedThreadPool(writers + 2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean();
        List<Future<?>> readers = new ArrayList<>();
        for (int r = 0; r < 2; r++) {
            readers.add(pool.submit(() -> {
                go.await();
                while (!stop.get()) {
                    data.views();
                    data.noteThreat(new Vec3(5, 64, 5), 1L);
                }
                return null;
            }));
        }
        List<Future<?>> ws = new ArrayList<>();
        for (int w = 0; w < writers; w++) {
            int base = w * perWriter;
            ws.add(pool.submit(() -> {
                go.await();
                for (int i = 0; i < perWriter; i++) {
                    BlockPos pos = new BlockPos(base + i, 70, 0);
                    Fixed l = new Fixed(Vec3.atCenterOf(pos), 280.0);
                    data.register(pos, l);
                    data.register(pos, l);
                    if (i % 3 == 0) {
                        data.register(pos, new Fixed(Vec3.atCenterOf(pos), 300.0));
                    }
                    if (i % 5 == 0) {
                        data.deregister(pos);
                    }
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : ws) {
            f.get();
        }
        stop.set(true);
        for (Future<?> f : readers) {
            f.get();
        }
        pool.shutdownNow();
        int expected = writers * (perWriter - perWriter / 5);
        assertEquals(expected, data.views().size());
    }

    /** A region worker registering must not wait on a main-thread wakeup pass that is itself waiting for that worker. */
    @Test
    void registerDoesNotBlockWhileWakeupPassWaitsOnTheWorld() throws Exception {
        MissileListenerData data = new MissileListenerData();
        BlockPos known = new BlockPos(1, 70, 1);
        data.register(known, new Fixed(Vec3.atCenterOf(known), 100.0));
        data.noteThreat(Vec3.atCenterOf(known), 5L);
        CountDownLatch inWorld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MissileListenerData.World stuck = new MissileListenerData.World() {
            public void force(BlockPos pos, boolean add) {
            }

            public boolean loaded(BlockPos pos) {
                return true;
            }

            public boolean listensAt(BlockPos pos) {
                inWorld.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return true;
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> main = pool.submit(() -> data.tickWakeups(stuck, 5L));
        assertTrue(inWorld.await(10, TimeUnit.SECONDS));
        BlockPos fresh = new BlockPos(9, 70, 9);
        Future<?> worker = pool.submit(() -> data.register(fresh, new Fixed(Vec3.atCenterOf(fresh), 50.0)));
        worker.get(10, TimeUnit.SECONDS);
        release.countDown();
        main.get(10, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertEquals(2, data.views().size());
    }
}

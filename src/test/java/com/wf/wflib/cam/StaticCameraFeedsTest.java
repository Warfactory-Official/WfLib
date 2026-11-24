package com.wf.wflib.cam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticCameraFeedsTest {

    private static final ResourceKey<Level> DIM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("test", "dim"));

    @AfterEach
    void reset() {
        StaticCameraFeeds.shutdown();
    }

    @Test
    void concurrentAllocationGivesOneNegativeIdPerPosition() throws Exception {
        int threads = 8, positions = 2000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<int[]>> results = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int shift = t * 251;
            results.add(pool.submit(() -> {
                go.await();
                int[] ids = new int[positions];
                for (int i = 0; i < positions; i++) {
                    int p = (i + shift) % positions;
                    ids[p] = StaticCameraFeeds.idFor(GlobalPos.of(DIM, new BlockPos(p, 64, 0)));
                    GlobalPos back = StaticCameraFeeds.posFor(ids[p]);
                    assertTrue(back != null && back.pos().getX() == p, "reverse map visible once id is returned");
                }
                return ids;
            }));
        }
        go.countDown();
        int[] first = results.get(0).get();
        Set<Integer> distinct = new HashSet<>();
        for (int p = 0; p < positions; p++) {
            assertTrue(first[p] < 0);
            distinct.add(first[p]);
        }
        for (Future<int[]> f : results) {
            assertEquals(positions, f.get().length);
            for (int p = 0; p < positions; p++) {
                assertEquals(first[p], f.get()[p], "every thread sees the same id for a position");
            }
        }
        pool.shutdownNow();
        assertEquals(positions, distinct.size());
        assertEquals(positions, StaticCameraFeeds.allocated());
    }
}

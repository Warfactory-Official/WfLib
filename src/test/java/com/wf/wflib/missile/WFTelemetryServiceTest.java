package com.wf.wflib.missile;

import com.wf.wflib.missile.api.WFTelemetry;
import com.wf.wflib.missile.api.WFTelemetryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WFTelemetryServiceTest {

    @AfterEach
    void reset() {
        WFTelemetryService.clear();
    }

    @Test
    void evictsEldestFirst() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < WFTelemetryService.MAX_TRACKED + 1; i++) {
            UUID id = new UUID(0, i);
            ids.add(id);
            WFTelemetryService.open(id, 0);
        }
        assertFalse(WFTelemetryService.isTracked(ids.get(0)));
        assertTrue(WFTelemetryService.isTracked(ids.get(1)));
        assertEquals(WFTelemetryService.MAX_TRACKED, WFTelemetryService.all().size());
    }

    @Test
    void openIsIdempotentAndCloseRemoves() {
        UUID id = new UUID(1, 1);
        WFTelemetry a = WFTelemetryService.open(id, 0);
        assertSame(a, WFTelemetryService.open(id, 5));
        WFTelemetryService.close(id);
        assertFalse(WFTelemetryService.isTracked(id));
        assertEquals(0, WFTelemetryService.all().size());
    }

    @Test
    void concurrentOpenKeepsCapAndConsistency() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> done = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            long base = t * 10_000L;
            done.add(pool.submit(() -> {
                for (int i = 0; i < 2000; i++) {
                    UUID id = new UUID(base, i);
                    WFTelemetryService.open(id, 0);
                    if (i % 3 == 0) {
                        WFTelemetryService.close(id);
                    }
                }
            }));
        }
        for (Future<?> f : done) {
            f.get();
        }
        pool.shutdown();
        assertTrue(WFTelemetryService.all().size() <= WFTelemetryService.MAX_TRACKED);
        for (WFTelemetry t : WFTelemetryService.all()) {
            assertTrue(t != null);
        }
    }
}

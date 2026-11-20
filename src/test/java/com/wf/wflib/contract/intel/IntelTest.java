package com.wf.wflib.contract.intel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Random;
import org.junit.jupiter.api.Test;

class IntelTest {
    @Test
    void qualityFollowsTheWeights() {
        var counts = new EnumMap<Intel.Quality, Integer>(Intel.Quality.class);
        var r = new Random(1);
        for (int i = 0; i < 20_000; i++) counts.merge(Intel.roll(r), 1, Integer::sum);
        assertTrue(counts.get(Intel.Quality.POOR) > counts.get(Intel.Quality.FAIR));
        assertTrue(counts.get(Intel.Quality.FAIR) > counts.get(Intel.Quality.GOOD));
        assertEquals(0.15, counts.get(Intel.Quality.GOOD) / 20_000.0, 0.02);
    }

    @Test
    void goodIntelIsExactAndPoorIntelIsLoose() {
        var r = new Random(2);
        assertEquals(0, Intel.offset(Intel.Quality.GOOD, r)[0]);
        for (int i = 0; i < 500; i++) {
            int[] o = Intel.offset(Intel.Quality.POOR, r);
            assertTrue(Math.hypot(o[0], o[1]) <= Intel.Quality.POOR.jitter + 1);
        }
    }

    @Test
    void anUnknownQualityNameIsTheWorst() {
        assertEquals(Intel.Quality.POOR, Intel.byName("nonsense"));
        assertEquals(Intel.Quality.GOOD, Intel.byName("good"));
    }
}

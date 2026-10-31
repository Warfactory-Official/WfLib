package com.wf.wflib.barrel;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import com.wf.wflib.barrel.api.BarrelSpec;
import com.wf.wflib.barrel.api.BarrelState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrelStateTest {
    private static final BarrelSpec S = BarrelSpec.DEFAULT;

    @Test
    void coolsByOneTimeConstant() {
        BarrelState hot = new BarrelState(520, 0, 0);
        BarrelState c = hot.at(S, Math.round(S.tau() * 20));
        assertEquals(BarrelState.AMBIENT + 500 / Math.E, c.temp(), 0.01);
        assertEquals(hot, hot.at(S, 0));
        assertEquals(hot, hot.at(S, -5));
    }

    @Test
    void stepwiseCoolingEqualsOneStep() {
        BarrelState hot = new BarrelState(780, 0, 0);
        BarrelState stepped = hot;
        for (long t = 100; t <= 4000; t += 100) {
            stepped = stepped.at(S, t);
        }
        BarrelState once = hot.at(S, 4000);
        assertEquals(once.temp(), stepped.temp(), 1e-3);
        assertEquals(once.wear(), stepped.wear(), 1e-5);
        assertTrue(once.wear() > 0);
    }

    @Test
    void wearOnlyAboveItsThreshold() {
        assertEquals(0, new BarrelState(S.wear().from() - 1, 0, 0).at(S, 20 * 600).wear());
        // Held at max for life seconds (refired every tick) => worn out.
        BarrelState b = new BarrelState(S.max(), 0, 0);
        for (long t = 1; t <= Math.round(S.wear().life() * 20); t++) {
            b = new BarrelState(S.max(), b.at(S, t).at(), b.at(S, t).wear());
        }
        assertEquals(1, b.wear(), 0.01);
    }

    @Test
    void firingHeatsAndCaps() {
        BarrelState b = BarrelState.NEW;
        for (int i = 0; i < 1000; i++) {
            b = b.fired(S, 1000);
        }
        assertEquals(S.max(), b.temp());
        assertEquals(BarrelState.AMBIENT + S.perShot(), BarrelState.NEW.fired(S, 0).temp());
    }

    @Test
    void rampsClampAtBothEnds() {
        assertEquals(0, new BarrelState(100, 0, 0).spread(S));
        assertEquals(S.spread().value(), new BarrelState(S.max(), 0, 0).spread(S), 1e-6);
        assertEquals(S.spread().value() + S.wear().spread(), new BarrelState(S.max(), 0, 1).spread(S), 1e-6);
        assertEquals(0, BarrelState.NEW.jamChance(S));
        assertEquals(0, BarrelState.NEW.cookOffRate(S));
    }

    @Test
    void hazardIsExponential() {
        assertFalse(BarrelState.happens(0, 1, 0));
        assertTrue(BarrelState.happens(1, 1, 0.632));
        assertFalse(BarrelState.happens(1, 1, 0.633));
    }

    @Test
    void codecDefaultsAndValidation() {
        BarrelSpec s = BarrelSpec.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"x:m249\",\"perShot\":3,\"swap\":{\"coop\":2}}")).getOrThrow();
        assertEquals(3, s.perShot());
        assertEquals(2, s.swap().coop());
        assertEquals(S.swap().self(), s.swap().self());
        assertTrue(s.quickChange());
        assertTrue(BarrelSpec.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"spread\":{\"from\":5,\"to\":5,\"value\":1}}")).isError());
        assertTrue(BarrelSpec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"max\":10}")).isError());
    }
}

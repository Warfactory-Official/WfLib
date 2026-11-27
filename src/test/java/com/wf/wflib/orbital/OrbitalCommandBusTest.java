package com.wf.wflib.orbital;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrbitalCommandBusTest {

    @Test
    void numberRejectsNonFinite() {
        assertNull(OrbitalCommandBus.number("NaN"));
        assertNull(OrbitalCommandBus.number("Infinity"));
        assertNull(OrbitalCommandBus.number("-Infinity"));
        assertNull(OrbitalCommandBus.number("1e999"));
        assertNull(OrbitalCommandBus.number("x"));
        assertEquals(-12.5, OrbitalCommandBus.number("-12.5"));
    }
}

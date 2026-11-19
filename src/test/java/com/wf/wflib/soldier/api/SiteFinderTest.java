package com.wf.wflib.soldier.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SiteFinderTest {
    @Test
    void roughnessIsTheHeightRange() {
        assertEquals(0, SiteFinder.roughness(new int[]{64, 64, 64}));
        assertEquals(7, SiteFinder.roughness(new int[]{60, 64, 67, 63}));
    }

    @Test
    void aMissingSampleMakesTheSpotUnusable() {
        assertEquals(-1, SiteFinder.roughness(new int[]{64, Integer.MIN_VALUE, 64}));
    }

    @Test
    void badRequestsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new SiteFinder.Request(net.minecraft.world.phys.Vec3.ZERO, 100, 50, 10, 30, false, 5));
        assertThrows(IllegalArgumentException.class, () -> new SiteFinder.Request(net.minecraft.world.phys.Vec3.ZERO, 10, 50, 1, 30, false, 5));
    }
}

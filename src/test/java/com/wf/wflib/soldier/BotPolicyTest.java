package com.wf.wflib.soldier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotPolicyTest {
    @Test
    void doesNotSwapOneHalfEmptyMagazineForAnother() {
        assertFalse(BotPolicy.worthReloading(8, 30, 8), "same magazine");
        assertFalse(BotPolicy.worthReloading(8, 30, 10), "barely more");
        assertTrue(BotPolicy.worthReloading(8, 30, 30), "a full spare");
        assertTrue(BotPolicy.worthReloading(0, 30, 3), "anything beats an empty gun");
        assertFalse(BotPolicy.worthReloading(0, 30, 0), "no spare at all");
    }

    @Test
    void dryOnlyWhenEmptyWithNothingToLoad() {
        assertTrue(BotPolicy.dry(0, 0));
        assertFalse(BotPolicy.dry(0, 5));
        assertFalse(BotPolicy.dry(3, 0));
    }

    @Test
    void grenadesAreRateLimitedPerSoldierAndPerTeam() {
        assertFalse(BotPolicy.cooledDown(1000, 500, -9999, BotPolicy.FRAG_COOLDOWN, BotPolicy.TEAM_FRAG_COOLDOWN));
        assertFalse(BotPolicy.cooledDown(1000, -9999, 800, BotPolicy.FRAG_COOLDOWN, BotPolicy.TEAM_FRAG_COOLDOWN));
        assertTrue(BotPolicy.cooledDown(1000, -9999, -9999, BotPolicy.FRAG_COOLDOWN, BotPolicy.TEAM_FRAG_COOLDOWN));
    }

    @Test
    void pushesOnlyAStaleNearbyContactWhenReady() {
        assertTrue(BotPolicy.shouldPush(200, 100, 20, 0, true, true, 5, 6));
        assertFalse(BotPolicy.shouldPush(200, 150, 20, 0, true, true, 5, 6), "contact still fresh");
        assertFalse(BotPolicy.shouldPush(200, 100, 40, 0, true, true, 5, 6), "too far");
        assertFalse(BotPolicy.shouldPush(200, 100, 20, 0.6f, true, true, 5, 6), "pinned");
        assertFalse(BotPolicy.shouldPush(200, 100, 20, 0, false, true, 5, 6), "low on ammunition");
        assertFalse(BotPolicy.shouldPush(200, 100, 20, 0, true, false, 5, 6), "squad says hold");
        assertFalse(BotPolicy.shouldPush(200, 100, 20, 0, true, true, 40, 6), "too far from its post");
    }
}

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

    @Test
    void aRealFightOutlastsAGlimpse() {
        assertFalse(BotPolicy.forgotten(300, 0, false), "a glimpse lasts 15 s");
        assertTrue(BotPolicy.forgotten(301, 0, false));
        assertFalse(BotPolicy.forgotten(1200, 0, true), "an enemy it was fighting lasts a minute");
        assertTrue(BotPolicy.forgotten(1201, 0, true));
    }

    @Test
    void theLightComesOnInTheDarkAndStaysOnThroughDusk() {
        assertFalse(BotPolicy.wantsLight(false, 15), "full daylight");
        assertFalse(BotPolicy.wantsLight(false, 7), "dim, not dark yet");
        assertTrue(BotPolicy.wantsLight(false, 6), "dark enough to switch on");
        assertTrue(BotPolicy.wantsLight(true, 8), "already on: stays on until clearly bright");
        assertFalse(BotPolicy.wantsLight(true, 9), "bright again");
    }

    @Test
    void aSoundIsWorthALookOnceKnownEvenIfTheShootingGoesOn() {
        assertTrue(BotPolicy.shouldInvestigate(100, 60, 30, 0, true, true, 5, 6));
        assertFalse(BotPolicy.shouldInvestigate(100, 90, 30, 0, true, true, 5, 6), "only just heard");
        assertFalse(BotPolicy.shouldInvestigate(100, 60, 4, 0, true, true, 5, 6), "close enough to have looked");
        assertFalse(BotPolicy.shouldInvestigate(100, 60, 70, 0, true, true, 5, 6), "beyond earshot");
        assertFalse(BotPolicy.shouldInvestigate(100, 60, 30, 0.5f, true, true, 5, 6), "pinned");
        assertFalse(BotPolicy.shouldInvestigate(100, 60, 30, 0, false, true, 5, 6), "low on ammunition");
        assertFalse(BotPolicy.shouldInvestigate(100, 60, 30, 0, true, false, 5, 6), "squad says hold");
        assertFalse(BotPolicy.shouldInvestigate(100, 60, 30, 0, true, true, 60, 6), "too far from its post");
    }
}

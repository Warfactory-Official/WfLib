package com.wf.wflib.contract.blacksite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class CaptureTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
    /** a and b are in one group; c is alone. */
    private final Function<UUID, Set<UUID>> groups = p -> p.equals(c) ? Set.of(c) : Set.of(a, b);

    private void run(Capture cap, Set<UUID> in, int seconds) {
        for (int i = 0; i < seconds; i++) cap.step(in, groups);
    }

    @Test
    void aLonePlayerTakesANeutralSiteInSixtySeconds() {
        var cap = new Capture();
        run(cap, Set.of(c), Capture.NEUTRAL_SECONDS - 1);
        assertNull(cap.holder());
        run(cap, Set.of(c), 1);
        assertEquals(c, cap.holder());
    }

    @Test
    void takingAHeldSiteNeedsTwiceAsLong() {
        var cap = new Capture();
        cap.restore(a, 0, Map.of());
        run(cap, Set.of(c), Capture.HELD_SECONDS - 1);
        assertEquals(a, cap.holder());
        run(cap, Set.of(c), 1);
        assertEquals(c, cap.holder());
    }

    @Test
    void theHoldersGroupInRangeFreezesAChallenge() {
        var cap = new Capture();
        cap.restore(a, 0, Map.of());
        run(cap, Set.of(c, b), 500);
        assertEquals(a, cap.holder());
        assertEquals(0, cap.progress());
    }

    @Test
    void anAbandonedChallengeDecays() {
        var cap = new Capture();
        run(cap, Set.of(c), 30);
        assertEquals(30, cap.progress());
        run(cap, Set.of(), 10);
        assertEquals(20, cap.progress());
    }

    @Test
    void rivalGroupsTogetherMakeNoProgress() {
        var cap = new Capture();
        run(cap, Set.of(a, c), 500);
        assertNull(cap.holder());
    }

    @Test
    void theDayIsSplitAmongThoseWhoWereThere() {
        var cap = new Capture();
        cap.restore(a, 0, Map.of());
        for (int i = 0; i < Capture.DAY_SECONDS; i++) cap.step(i < 600 ? Set.of(a, b) : Set.of(a), groups);
        var pay = cap.closeDay(10_000, 0.25);
        assertEquals(10_000, pay.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(2, pay.size());
        assertEquals(5_000, pay.get(a));
    }

    @Test
    void aPassingFriendDoesNotCollect() {
        var cap = new Capture();
        cap.restore(a, 0, Map.of());
        for (int i = 0; i < Capture.DAY_SECONDS; i++) cap.step(i < 60 ? Set.of(a, b) : Set.of(a), groups);
        var pay = cap.closeDay(10_000, 0.25);
        assertEquals(Set.of(a), pay.keySet());
        assertEquals(10_000, pay.get(a));
    }

    @Test
    void nobodyTherePaysNothingAndTheCountsReset() {
        var cap = new Capture();
        cap.restore(a, 0, Map.of(a, 5));
        assertTrue(cap.closeDay(10_000, 0.25).isEmpty());
        assertFalse(cap.presence().containsKey(a));
    }
}

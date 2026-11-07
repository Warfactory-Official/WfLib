package com.wf.wflib.soldier;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BotGrenadesTest {
    private static final UUID FIRST = new UUID(0, 1), SECOND = new UUID(0, 2);
    private static final Vec3 EYE = new Vec3(0, 1.62, 0), FORWARD = new Vec3(0, 0, 1);

    @Test
    void sightNeedsRangeFieldOfViewAndAnUnblockedRay() {
        assertTrue(BotGrenades.visible(EYE, FORWARD, new Vec3(2, 0, 6), true));
        assertFalse(BotGrenades.visible(EYE, FORWARD, new Vec3(0, 0, -3), true));
        assertFalse(BotGrenades.visible(EYE, FORWARD, new Vec3(0, 0, 6), false));
        assertFalse(BotGrenades.visible(EYE, FORWARD, new Vec3(0, 0, 19), true));
    }

    @Test
    void hearingWorksBehindUsButQuietSoundsAndWallsReduceItsRange() {
        Vec3 behind = new Vec3(0, 0, -8);
        assertTrue(BotGrenades.audible(EYE, behind, true, 1));
        assertFalse(BotGrenades.audible(EYE, behind, false, 1));
        assertFalse(BotGrenades.audible(EYE, behind, true, 0.2f));
        assertTrue(BotGrenades.audible(EYE, new Vec3(0, 0, -2), false, 1));
        assertFalse(BotGrenades.audible(EYE, EYE, true, 0));
    }

    @Test
    void repeatedSightDoesNotPostponeTheReactionForever() {
        BotGrenades memory = new BotGrenades();
        memory.notice(FIRST, Vec3.ZERO, 100, "seen");
        assertTrue(memory.reacting(105).isEmpty());
        for (int now = 101; now <= 110; now++) memory.notice(FIRST, new Vec3(now, 0, 0), now, "seen");
        assertEquals(1, memory.reacting(110).size());
        assertEquals(new Vec3(110, 0, 0), memory.reacting(110).getFirst().at());
    }

    @Test
    void losingSightKeepsTheLastObservedLocationUntilItExpires() {
        BotGrenades memory = new BotGrenades();
        Vec3 heard = new Vec3(3, 0, -2);
        memory.notice(FIRST, heard, 100, "heard");
        memory.expire(180, id -> true);
        assertEquals(heard, memory.reacting(180).getFirst().at());
        assertEquals("heard", memory.reacting(180).getFirst().sense());
        memory.expire(201, id -> true);
        assertEquals(0, memory.count());
    }

    @Test
    void detonatedGrenadesAreForgottenWithoutDroppingOtherThreats() {
        BotGrenades memory = new BotGrenades();
        memory.notice(FIRST, Vec3.ZERO, 0, "seen");
        memory.notice(SECOND, FORWARD, 0, "heard");
        memory.expire(20, SECOND::equals);
        assertEquals(1, memory.count());
        assertEquals(FORWARD, memory.reacting(20).getFirst().at());
        memory.clear();
        assertTrue(memory.reacting(21).isEmpty());
    }

    @Test
    void safetyMustClearEveryKnownGrenade() {
        List<Vec3> dangers = List.of(new Vec3(-5, 0, 0), new Vec3(5, 0, 0));
        assertEquals(5, BotGrenades.clearance(Vec3.ZERO, dangers));
        assertTrue(BotGrenades.clearance(new Vec3(13, 0, 0), dangers) < BotGrenades.SAFE_DISTANCE);
        assertTrue(BotGrenades.clearance(new Vec3(0, 0, 13), dangers) >= BotGrenades.SAFE_DISTANCE);
        assertEquals(Double.POSITIVE_INFINITY, BotGrenades.clearance(Vec3.ZERO, List.of()));
    }
}

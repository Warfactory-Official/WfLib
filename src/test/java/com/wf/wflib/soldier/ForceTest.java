package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierOrder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ForceTest {
    @Test void aFollowerWalksUntilItIsFarBehindTheOwnerRunsOrAFightStarts() {
        assertFalse(SoldierBrain.followSprint(false, 4, false, false), "close and calm: walk");
        assertFalse(SoldierBrain.followSprint(false, 12, false, false), "a gap that is not far yet: walk");
        assertTrue(SoldierBrain.followSprint(false, 20, false, false), "far behind: run");
        assertTrue(SoldierBrain.followSprint(true, 10, false, false), "still catching up: keep running");
        assertFalse(SoldierBrain.followSprint(true, 6, false, false), "caught up: walk again");
        assertTrue(SoldierBrain.followSprint(false, 5, true, false), "the owner sprints: so does it");
        assertFalse(SoldierBrain.followSprint(false, 2, true, false), "right beside a sprinting owner: no need yet");
        assertTrue(SoldierBrain.followSprint(false, 2, false, true), "a fight: run");
    }

    @Test void soldierNamesFitAPlayerNameAndKeepTheirSideApart() {
        var rng = new java.util.Random(7);
        var seen = new java.util.HashSet<String>();
        for (int i = 0; i < 2000; i++) {
            for (boolean hostile : new boolean[]{false, true}) {
                String n = com.wf.wflib.soldier.api.SoldierNames.random(rng, hostile);
                assertTrue(n.matches("[A-Za-z0-9_]{2,13}"), n);
                assertEquals(n, Companion.clean(n), "a callsign keeps the whole name");
                if (!hostile) seen.add(n);
            }
        }
        assertTrue(seen.size() > 500, "names vary: " + seen.size());
    }

    private static Territory gate(List<Vec3> route) {
        return new Territory("gate", "North gate", "friendly", "minecraft:overworld", new Vec3(100, 64, 100), 20, 40, 120, route);
    }

    @Test void aTerritoryIsACylinder() {
        var t = gate(List.of());
        assertTrue(t.contains(new Vec3(110, 70, 110)));
        assertFalse(t.contains(new Vec3(121, 70, 100)));
        assertFalse(t.contains(new Vec3(100, 130, 100)), "above its ceiling");
        assertFalse(t.contains(new Vec3(100, 30, 100)), "below its floor");
    }

    @Test void territoryRejectsWhatCannotBeSaved() {
        assertThrows(IllegalArgumentException.class, () -> new Territory("Bad Id", "x", "", "minecraft:overworld", Vec3.ZERO, 10, 0, 10, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Territory("a", "x", "", "minecraft:overworld", Vec3.ZERO, 1, 0, 10, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Territory("a", "x", "", "minecraft:overworld", Vec3.ZERO, 10, 10, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Territory("a", "x", "", "minecraft:overworld", new Vec3(Double.NaN, 0, 0), 10, 0, 10, List.of()));
    }

    @Test void territorySurvivesItsJson() {
        var t = gate(List.of(new Vec3(95, 64, 95), new Vec3(105, 64, 105)));
        assertEquals(t, Territory.read(t.json()));
    }

    @Test void aRoutePatrolIsWalkedInOrderAndWraps() {
        var t = gate(List.of(new Vec3(1, 64, 1), new Vec3(2, 64, 2), new Vec3(3, 64, 3)));
        var rng = RandomSource.create(1);
        assertEquals(new Vec3(1, 64, 1), t.patrolPoint(0, null, rng, 99));
        assertEquals(new Vec3(3, 64, 3), t.patrolPoint(2, null, rng, 99));
        assertEquals(new Vec3(1, 64, 1), t.patrolPoint(3, null, rng, 99));
        assertEquals(new Vec3(3, 64, 3), t.patrolPoint(-1, null, rng, 99));
    }

    @Test void aRoutelessPatrolStaysInsideAndMovesOn() {
        var t = gate(List.of());
        var rng = RandomSource.create(5);
        Vec3 last = t.centre();
        for (int i = 0; i < 200; i++) {
            Vec3 next = t.patrolPoint(i, last, rng, 99);
            assertTrue(t.contains(next), "point " + next);
            last = next;
        }
        for (int i = 0; i < 100; i++) assertTrue(t.patrolPoint(i, null, rng, 3).distanceTo(t.centre()) <= 3.0001, "a guard strays no further than its wander");
    }

    @Test void ordersNeedWhatTheyAreAbout() {
        assertThrows(IllegalArgumentException.class, () -> SoldierOrder.of(SoldierOrder.Kind.MOVE));
        assertThrows(IllegalArgumentException.class, () -> SoldierOrder.of(SoldierOrder.Kind.PATROL));
        assertThrows(IllegalArgumentException.class, () -> SoldierOrder.at(SoldierOrder.Kind.MOVE, new Vec3(0, Double.POSITIVE_INFINITY, 0)));
        assertEquals("Patrol gate", SoldierOrder.in(SoldierOrder.Kind.PATROL, "gate").label());
        assertEquals(SoldierOrder.Kind.GUARD, SoldierOrder.Kind.of("Guard"));
        assertNull(SoldierOrder.Kind.of("dance"));
    }

    @Test void orderIdsAreUniqueAndEveryKindHasAnAckThePackFormatKnows() {
        var ids = new java.util.HashSet<String>();
        for (var k : SoldierOrder.Kind.values()) {
            assertTrue(ids.add(k.id), k.id);
            assertTrue(VoicePack.EVENTS.contains(k.ack), k.ack);
        }
    }

    @Test void ordersTranslateToWhatTheAiUnderstands() {
        var b = new SoldierAi.Bot("Wolf_1");
        Vec3 here = new Vec3(1, 64, 1), there = new Vec3(40, 64, 40);
        Orders.apply(b, here, SoldierOrder.at(SoldierOrder.Kind.MOVE, there));
        assertEquals(SoldierAi.Mode.ADVANCE, b.mode);
        assertEquals(there, b.anchor);
        Orders.apply(b, here, SoldierOrder.of(SoldierOrder.Kind.HOLD));
        assertEquals(SoldierAi.Mode.HOLD, b.mode);
        assertEquals(here, b.anchor);
        assertEquals(here, b.order.pos(), "a hold remembers its spot so a resupply trip can come back to it");
        Orders.apply(b, here, SoldierOrder.FOLLOW);
        assertNull(b.anchor, "the escort brain picks the slot beside the owner");
        Orders.apply(b, here, SoldierOrder.in(SoldierOrder.Kind.PATROL, "gate"));
        assertEquals(SoldierAi.Mode.ADVANCE, b.mode);
        Orders.apply(b, here, SoldierOrder.in(SoldierOrder.Kind.GUARD, "gate"));
        assertEquals(SoldierAi.Mode.HOLD, b.mode);
    }

    @Test void aNewOrderEndsTheLastOnesLeftovers() {
        var b = new SoldierAi.Bot("Wolf_1");
        b.patrolPoint = new Vec3(5, 5, 5);
        b.dwell = 100;
        b.depotId = "d1";
        b.triedDepots.add("d1");
        Orders.apply(b, Vec3.ZERO, SoldierOrder.at(SoldierOrder.Kind.SUPPRESS, new Vec3(9, 64, 9)));
        assertNotNull(b.suppressAt);
        Orders.apply(b, Vec3.ZERO, SoldierOrder.FOLLOW);
        assertNull(b.patrolPoint);
        assertEquals(-1, b.dwell);
        assertEquals("", b.depotId);
        assertTrue(b.triedDepots.isEmpty());
        assertNull(b.suppressAt);
        assertFalse(b.threats.containsKey(SoldierBrain.SUPPRESS_KEY));
    }

    @Test void aGarrisonSurvivesItsJsonAndRaisesFromWhatExists() {
        var g = new Garrison(5, List.of("rifle", "medic"), SoldierOrder.Kind.GUARD, 90);
        var t = gate(List.of()).withGarrison(g);
        assertEquals(t, Territory.read(t.json()));
        assertNull(Territory.read(gate(List.of()).json()).garrison(), "a territory without a garrison reads back without one");
        assertEquals("rifle", g.unitFor(0, List.of("rifle", "medic", "mg")));
        assertEquals("medic", g.unitFor(3, List.of("rifle", "medic", "mg")));
        assertNull(g.unitFor(0, List.of("mg")), "none of its variants exist any more");
        assertEquals("mg", new Garrison(1, List.of(), SoldierOrder.Kind.PATROL, 0).unitFor(5, List.of("rifle", "mg")), "no list means every variant in turn");
    }

    @Test void aGarrisonRejectsWhatItCannotDo() {
        assertThrows(IllegalArgumentException.class, () -> new Garrison(-1, List.of(), SoldierOrder.Kind.PATROL, 0));
        assertThrows(IllegalArgumentException.class, () -> new Garrison(1, List.of(), SoldierOrder.Kind.MOVE, 0));
        assertThrows(IllegalArgumentException.class, () -> new Garrison(1, List.of("Bad Id"), SoldierOrder.Kind.GUARD, 0));
        assertThrows(IllegalArgumentException.class, () -> new Garrison(1, List.of(), SoldierOrder.Kind.GUARD, -5));
    }

    @Test void anOrderSurvivesTheTagItIsSavedIn() {
        for (var o : List.of(SoldierOrder.FOLLOW, SoldierOrder.at(SoldierOrder.Kind.MOVE, new Vec3(1.5, 64, -3)), SoldierOrder.in(SoldierOrder.Kind.GUARD, "gate")))
            assertEquals(o, Tags.order(Tags.order(o)));
        var junk = new net.minecraft.nbt.CompoundTag();
        junk.putString("kind", "dance");
        assertEquals(SoldierOrder.FOLLOW, Tags.order(junk), "an order that no longer exists reads as follow");
    }

    @Test void dragAndDropRearrangesTheRosterByTeamAndPosition() {
        var book = new SquadBook();
        var owner = java.util.UUID.randomUUID();
        var ids = new java.util.ArrayList<java.util.UUID>();
        for (int i = 0; i < 5; i++) {
            var c = new Companion(java.util.UUID.randomUUID(), "S" + i, "friendly", com.wf.wflib.soldier.api.SoldierRole.RIFLEMAN, null);
            c.fireTeam = i < 3 ? 0 : 1;
            book.add(owner, c);
            ids.add(c.id);
        }
        book.place(owner, List.of(ids.get(4)), 0, 1);                 // the last of Bravo goes second in Alpha
        var names = book.of(owner).stream().map(c -> c.callsign + c.fireTeam).toList();
        assertEquals(List.of("S00", "S40", "S10", "S20", "S31"), names);
        book.place(owner, List.of(ids.get(0), ids.get(1)), 1, 99);   // far past the end: last in Bravo, in their own order
        names = book.of(owner).stream().map(c -> c.callsign + c.fireTeam).toList();
        assertEquals(List.of("S40", "S20", "S31", "S01", "S11"), names);
    }

    @Test void anUnpaidSoldierTurnsHostileOnlyWhenEnabledAndByThePercentage() {
        assertFalse(Wages.defects(false, 100, 0.0), "off means never");
        assertFalse(Wages.defects(true, 0, 0.0), "0 percent means never");
        assertTrue(Wages.defects(true, 100, 0.999));
        assertTrue(Wages.defects(true, 2.0, 0.0199));
        assertFalse(Wages.defects(true, 2.0, 0.02));
    }

    @Test void offersAndCostsRejectOrDefaultWhatIsUnusable() {
        assertThrows(IllegalArgumentException.class, () -> new com.wf.wflib.soldier.api.Offer("", "x", "rifle", null, 0));
        assertThrows(IllegalArgumentException.class, () -> new com.wf.wflib.soldier.api.Offer("a", "x", "", null, 0));
        var o = new com.wf.wflib.soldier.api.Offer("a", null, "rifle", null, -3);
        assertEquals("rifle", o.title());
        assertSame(com.wf.wflib.soldier.api.Cost.FREE, o.cost());
        assertEquals(0, o.delaySeconds());
        assertSame(com.wf.wflib.soldier.api.Cost.FREE, com.wf.wflib.soldier.api.Cost.all(List.of(com.wf.wflib.soldier.api.Cost.FREE, com.wf.wflib.soldier.api.Cost.currency(0))));
        assertFalse(com.wf.wflib.soldier.api.Cost.currency(5).affordable(null), "currency with no wallet installed is not affordable");
    }
}

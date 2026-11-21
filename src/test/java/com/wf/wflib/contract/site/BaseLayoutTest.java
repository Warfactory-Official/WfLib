package com.wf.wflib.contract.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wf.wflib.contract.site.BasePlan.Piece;
import com.wf.wflib.contract.site.BasePlan.Role;
import com.wf.wflib.contract.site.BasePlan.Type;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BaseLayoutTest {
    private static BasePlan plan(BaseKind k, long seed) {
        return BaseLayout.plan(k, 1000, -500, 70, new Random(seed), new SiteSurveyTest.Fake());
    }

    private static boolean overlap(Piece a, Piece b) {
        return Math.abs(a.x() - b.x()) <= (a.w() + b.w()) / 2 && Math.abs(a.z() - b.z()) <= (a.d() + b.d()) / 2;
    }

    @Test
    void insideBuildingsNeverOverlapAndStayInsideTheWall() {
        var solid = List.of(Type.COMMAND, Type.BARRACKS, Type.DEPOT, Type.BUNKER, Type.BAY, Type.TOWER, Type.MESS, Type.MEDICAL, Type.ARMORY, Type.CONTAINERS, Type.GENERATOR, Type.HELIPAD);
        for (BaseKind k : BaseKind.values()) {
            for (long seed = 0; seed < 60; seed++) {
                var p = plan(k, seed);
                var inside = p.pieces().stream().filter(x -> solid.contains(x.type())).toList();
                for (int i = 0; i < inside.size(); i++) {
                    for (int j = i + 1; j < inside.size(); j++) {
                        assertFalse(overlap(inside.get(i), inside.get(j)), k + " seed " + seed + ": " + inside.get(i) + " overlaps " + inside.get(j));
                    }
                }
                if (p.wall() != null) {
                    for (Piece x : inside) {
                        if (x.type() == Type.TOWER) continue;
                        assertTrue(Math.abs(x.x() - 1000) + x.w() / 2 < p.wall().half() && Math.abs(x.z() + 500) + x.d() / 2 < p.wall().half(), k + " seed " + seed + ": " + x + " pokes through the wall");
                    }
                }
            }
        }
    }

    @Test
    void everyArchetypeHasItsPartsAndPosts() {
        for (BaseKind k : BaseKind.values()) {
            var p = plan(k, 7);
            assertEquals(1, p.pieces().stream().filter(x -> x.type() == Type.COMMAND).count());
            assertEquals(k.towers, p.pieces().stream().filter(x -> x.type() == Type.TOWER).count(), k + " towers");
            assertTrue(p.zones().stream().anyMatch(z -> z.role() == Role.COMMAND));
            assertTrue(p.zones().stream().filter(z -> z.role() == Role.TOWER).count() == k.towers);
            assertTrue(p.zones().stream().filter(z -> z.role() == Role.WALL_PATROL).allMatch(z -> z.route().size() >= 4));
            assertEquals(k.outposts, p.zones().stream().filter(z -> z.role() == Role.OUTPOST).count(), k + " outposts");
            assertTrue(p.zones().stream().allMatch(z -> z.weight() > 0));
            assertNotNull(p.cache());
        }
        var fort = plan(BaseKind.FORTRESS, 3);
        assertTrue(fort.zones().size() >= 20, "a fortress has many posts: " + fort.zones().size());
        assertEquals(2, fort.zones().stream().filter(z -> z.role() == Role.GATE).count());
        assertTrue(fort.zones().stream().anyMatch(z -> z.role() == Role.OUTER_PATROL));
    }

    @Test
    void theCacheIsInsideTheCommandBuilding() {
        for (long seed = 0; seed < 30; seed++) {
            var p = plan(BaseKind.COMPOUND, seed);
            var c = p.pieces().stream().filter(x -> x.type() == Type.COMMAND).findFirst().orElseThrow();
            assertTrue(Math.abs(p.cache()[0] - c.x()) < c.w() / 2 && Math.abs(p.cache()[2] - c.z()) < c.d() / 2, "seed " + seed);
        }
    }

    @Test
    void vehiclesParkInsideTheirBaysAndABiggerForceWantsABiggerBase() {
        var p = plan(BaseKind.FORTRESS, 11);
        assertEquals(BaseKind.FORTRESS.bays, p.vehicles().size());
        for (var s : p.vehicles()) {
            assertTrue(p.pieces().stream().anyMatch(x -> x.type() == Type.BAY && x.x() == s.x() && x.z() == s.z()));
        }
        assertEquals(BaseKind.OUTPOST, BaseKind.forForce(3));
        assertEquals(BaseKind.COMPOUND, BaseKind.forForce(10));
        assertEquals(BaseKind.FORTRESS, BaseKind.forForce(30));
    }

    @Test
    void outpostsAvoidWaterAndTheLayoutSurvivesBadGround() {
        var wet = new SiteSurveyTest.Fake();
        wet.wet = (x, z) -> Math.hypot(x - 1000, z + 500) > 50;       // water everywhere past the footprint
        var p = BaseLayout.plan(BaseKind.FORTRESS, 1000, -500, 70, new Random(5), wet);
        assertEquals(0, p.zones().stream().filter(z -> z.role() == Role.OUTPOST).count());
    }

    @Test
    void aWalledBaseIsLaidOutOnStreets() {
        var lots = List.of(Type.COMMAND, Type.BARRACKS, Type.MESS, Type.MEDICAL, Type.ARMORY, Type.GENERATOR, Type.CONTAINERS, Type.YARD, Type.DEPOT, Type.BUNKER, Type.BAY, Type.HELIPAD);
        for (BaseKind k : List.of(BaseKind.COMPOUND, BaseKind.FORTRESS)) {
            for (long seed = 0; seed < 40; seed++) {
                var p = plan(k, seed);
                var paths = p.pieces().stream().filter(x -> x.type() == Type.PATH).toList();
                var buildings = p.pieces().stream().filter(x -> lots.contains(x.type()) && Math.abs(x.x() - 1000) < k.wall && Math.abs(x.z() + 500) < k.wall).toList();
                assertTrue(p.pieces().stream().filter(x -> x.type() == Type.ROAD).count() >= 6, k + " seed " + seed + ": roads, a lane and a plaza");
                assertTrue(buildings.size() >= (k == BaseKind.FORTRESS ? 14 : 9), k + " seed " + seed + ": only " + buildings.size() + " buildings");
                for (Piece b : buildings) {
                    assertTrue(paths.stream().anyMatch(x -> Math.abs(x.x() - b.x()) <= (x.w() + b.w()) / 2 + 1 && Math.abs(x.z() - b.z()) <= (x.d() + b.d()) / 2 + 1),
                            k + " seed " + seed + ": " + b + " has no path to a road");
                }
                assertEquals(1, p.pieces().stream().filter(x -> x.type() == Type.TOWER).map(Piece::style).distinct().count(), k + " seed " + seed + ": towers of one design");
            }
        }
    }

    @Test
    void mostCompoundsHaveRoomForAPad() {
        int with = 0;
        for (long seed = 0; seed < 40; seed++) if (plan(BaseKind.COMPOUND, seed).pieces().stream().anyMatch(x -> x.type() == Type.HELIPAD)) with++;
        assertTrue(with >= 20, "a pad (or a silo) in " + with + " of 40 compounds");
    }
}

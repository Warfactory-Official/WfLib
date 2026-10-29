package com.wf.wflib.anomaly.site;

import com.wf.wflib.anomaly.site.AnomalyCaves.Slot;
import com.wf.wflib.anomaly.site.AnomalyMix.Loot;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaveLayoutTest {
    @Test
    void layoutHoldsExactlyTheBudgetedArtefactsOnFloorsInsideTheChamber() {
        for (int tier = 1; tier <= AnomalyMix.TIERS; tier++) {
            for (int artefacts = 0; artefacts <= 9; artefacts++) {
                for (long seed = 0; seed < 25; seed++) {
                    CavePlan plan = CavePlan.roll(RandomSource.create(seed), tier, artefacts, l -> true);
                    CaveLayout a = CaveLayout.build(plan, -300, 700, -20);
                    CaveLayout b = CaveLayout.build(plan, -300, 700, -20);
                    String where = "t" + tier + " n" + artefacts + " seed " + seed;
                    assertEquals(a.anomalies(), b.anomalies(), where);
                    assertEquals(a.loot(), b.loot(), where);
                    assertFalse(a.anomalies().isEmpty(), where);
                    assertEquals(artefacts, a.loot().isEmpty() ? 0 : a.loot().size(), where);
                    for (var p : a.anomalies()) {
                        assertTrue(a.u(p.pos().getX(), p.pos().getZ()) < 1.0D, where + " " + p);
                        int f = a.floor(p.pos().getX(), p.pos().getZ());
                        int c = a.ceiling(p.pos().getX(), p.pos().getZ());
                        assertTrue(p.pos().getY() > f && p.pos().getY() < c, where + " " + p + " f" + f + " c" + c);
                    }
                    for (var s : a.loot()) {
                        assertTrue(a.u(s.pos().getX(), s.pos().getZ()) < 1.0D, where);
                        assertEquals(a.floor(s.pos().getX(), s.pos().getZ()) + 1, s.pos().getY(), where);
                        assertTrue(a.box().isInside(s.pos()), where);
                    }
                }
            }
        }
    }

    @Test
    void everyUniqueHasExactlyOneHostSlot() {
        for (long world : new long[] {1L, 20261014L, -77L}) {
            int count = 37;
            for (Loot l : Loot.values()) {
                int hosts = 0;
                for (int i = 0; i < count; i++) {
                    hosts += new Slot(i, count, 0, 0, 1, 1, 0L, world).hosts(l) ? 1 : 0;
                }
                assertEquals(1, hosts, l + " world " + world);
            }
        }
    }

    @Test
    void uniquesOnlyAtTheirHost() {
        Slot s = new Slot(3, 10, 0, 0, 4, 40, 99L, 5L);
        CavePlan plan = CavePlan.roll(RandomSource.create(s.seed()), 4, s.artefacts(), s::allows);
        assertEquals(40, plan.artefacts().size());
        for (Loot l : plan.artefacts()) {
            assertTrue(l.share != AnomalyMix.UNIQUE_SHARE || s.hosts(l), l.toString());
        }
        Slot other = new Slot(4, 10, 0, 0, 4, 400, 99L, 5L);
        CavePlan many = CavePlan.roll(RandomSource.create(other.seed()), 4, other.artefacts(), other::allows);
        assertTrue(many.artefacts().stream().distinct().count() > 10);
    }
}

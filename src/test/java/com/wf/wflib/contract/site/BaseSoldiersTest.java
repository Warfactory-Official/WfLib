package com.wf.wflib.contract.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wf.wflib.contract.site.BasePlan.Role;
import com.wf.wflib.soldier.api.SoldierOrder;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BaseSoldiersTest {
    private static BasePlan plan(BaseKind k) {
        return BaseLayout.plan(k, 0, 0, 70, new Random(4), new SiteSurveyTest.Fake());
    }

    @Test
    void theContractsSquadsKeepTheirPlacesAndTheFirstHoldsTheCommandPost() {
        var p = plan(BaseKind.COMPOUND);
        var out = BaseSoldiers.assign(p, List.of(new BaseSoldiers.Group(List.of("a"), 1), new BaseSoldiers.Group(List.of("b"), 4)), 5, List.of("a", "b"), u -> "rifle", new Random(1));
        assertEquals(List.of("a"), out.get(0).units());
        assertEquals(1, out.get(0).count());
        var command = p.zones().stream().filter(z -> z.role() == Role.COMMAND).findFirst().orElseThrow();
        assertEquals(command.x() + 0.5, out.get(0).at().x);
        assertEquals(SoldierOrder.Kind.GUARD, out.get(0).mode());
        assertEquals(SoldierOrder.Kind.PATROL, out.get(1).mode(), "the second squad walks the wall");
        assertEquals(2, out.size(), "nothing extra was asked for");
    }

    @Test
    void aBiggerBaseGetsMoreSoldiersSpreadOverItsPosts() {
        var p = plan(BaseKind.FORTRESS);
        var out = BaseSoldiers.assign(p, List.of(new BaseSoldiers.Group(List.of("a"), 2), new BaseSoldiers.Group(List.of("b"), 4)), 30, List.of("a", "b"), u -> "rifle", new Random(2));
        assertEquals(30, out.stream().mapToInt(s -> s.count()).sum());
        assertTrue(out.size() >= 12, "spread over many posts: " + out.size());
        assertTrue(out.stream().allMatch(s -> s.at() != null && s.radius() >= 2));
        assertTrue(out.stream().filter(s -> s.mode() == SoldierOrder.Kind.PATROL).allMatch(s -> s.route().size() >= 4));
    }

    @Test
    void towersTakeMarksmenAndNestsGunnersWhenTheForceHasThem() {
        var p = plan(BaseKind.FORTRESS);
        var out = BaseSoldiers.assign(p, List.of(new BaseSoldiers.Group(List.of("r"), 2)), 40, List.of("r", "s", "g"),
                u -> u.equals("s") ? "sniper" : u.equals("g") ? "mg" : "rifle", new Random(3));
        var towerZones = p.zones().stream().filter(z -> z.role() == Role.TOWER).toList();
        boolean sniperOnTower = out.stream().anyMatch(s -> s.units().equals(List.of("s"))
                && towerZones.stream().anyMatch(z -> Math.abs(z.x() + 0.5 - s.at().x) < 1 && Math.abs(z.z() + 0.5 - s.at().z) < 1));
        assertTrue(sniperOnTower);
        assertNotNull(out.get(0).at());
    }

    @Test
    void withNothingToRaiseFromTheForceStaysAsAsked() {
        var out = BaseSoldiers.assign(plan(BaseKind.OUTPOST), List.of(new BaseSoldiers.Group(List.of("a"), 3)), 20, List.of(), u -> "rifle", new Random(1));
        assertEquals(1, out.size());
    }
}

package com.wf.wflib.armor.hull;

import com.google.gson.Gson;
import com.wf.wflib.armor.api.hull.HullOutcome;
import com.wf.wflib.armor.api.hull.HullProfile;
import com.wf.wflib.armor.api.hull.HullResolver;
import com.wf.wflib.armor.api.hull.HullResult;
import com.wf.wflib.armor.api.hull.PlateWalk;
import com.wf.wflib.damage.api.Threat;
import com.wf.wflib.damage.api.ThreatKind;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Resolver over scripted crossings: head-on along -z, entry at z = 3, exit at z = -3. */
class HullWalkTest {
    private static final float EPS = 1e-2F;

    private static FakeHull hull(String plates) {
        FakeHull hull = new FakeHull();
        hull.profile = new Gson().fromJson("{\"front\":200,\"side\":80,\"rear\":40,\"top\":30,\"bottom\":20,"
                + "\"plates\":" + plates + "}", HullProfile.class);
        hull.profile.validate();
        return hull;
    }

    private static FakeHull.Hit cross(FakeHull h, float z, String plate, boolean entering) {
        Vector3f n = h.rotation.transform(new Vector3f(0, 0, entering ? 1 : -1));
        return new FakeHull.Hit(0, h.world(0, 1, z), n, plate, entering);
    }

    private static HullResult shoot(FakeHull h, Threat t) {
        Vec3 start = h.world(0, 1, 10), at = h.world(0, 1, 3);
        return HullResolver.resolve(h, start, at.subtract(start), at, t, 100);
    }

    private static Threat ap(float pen) {
        return new Threat(ThreatKind.KINETIC, 30, pen, 1f, 70, 1.0f, 0, 0);
    }

    private static final String SKIN_AND_BACK = "{\"skin\":{\"layers\":[{\"mm\":10,\"mat\":\"rha\"}]},"
            + "\"back\":{\"layers\":[{\"mm\":20,\"mat\":\"rha\"}]}}";

    private static FakeHull twoPlates() {
        FakeHull h = hull(SKIN_AND_BACK);
        h.script = List.of(cross(h, 3, "skin", true), cross(h, -3, "back", false));
        return h;
    }

    @Test
    void bothPlatesAreWalkedTheEntryPlateDecidesTheOutcome() {
        FakeHull h = twoPlates();
        HullResult r = shoot(h, ap(100));
        assertEquals(HullOutcome.OVERMATCH, r.outcome(), "ratio 100 / 10 of the entry plate");
        assertTrue(r.through());
        assertTrue(r.exits());
        assertTrue(r.outcome().isThrough());
        assertEquals(90, r.interiorPen(), EPS);
        assertEquals(70, r.residualPen(), EPS);
        assertEquals(10, r.effectiveThickness(), EPS);
        assertEquals(30, r.walk().needed(), EPS);
        assertEquals(10, r.nominalThickness(), EPS, "entry plate");
        assertEquals(2, r.walk().defeated());
        assertEquals(100, r.hullDamage());
    }

    @Test
    void crossingsCarryTheirDistanceAndTheBudgetAlongTheLine() {
        HullResult r = shoot(twoPlates(), ap(100));
        assertEquals(2, r.walk().crossings());
        assertEquals(7, r.walk().crossingAt(0), EPS);
        assertEquals(13, r.walk().crossingAt(1), EPS);
        assertTrue(r.walk().crossingEntering(0));
        assertFalse(r.walk().crossingEntering(1));
        assertEquals(100, r.walk().residualAt(0, 100), EPS, "before the entry plate: entry pen");
        assertEquals(90, r.walk().residualAt(8, 100), EPS, "between the plates");
        assertEquals(70, r.walk().residualAt(14, 100), EPS, "behind the far wall");
        assertEquals(0, none().crossings());
    }

    private static com.wf.wflib.armor.api.hull.WalkResult none() {
        return com.wf.wflib.armor.api.hull.WalkResult.NONE;
    }

    @Test
    void theSecondPlateStopsWhatTheFirstLetBy() {
        HullResult r = shoot(twoPlates(), ap(25));
        assertEquals(HullOutcome.OVERMATCH, r.outcome());
        assertTrue(r.through(), "it got behind the entry plate");
        assertEquals(15, r.interiorPen(), EPS);
        assertFalse(r.exits(), "and died in the far wall");
        assertEquals(0, r.residualPen());
        assertEquals(0, r.exitSpeed(1000, 2.0f));
        assertEquals(1, r.walk().defeated());
        assertEquals(1, r.walk().stoppedAt());
        assertEquals(1, r.walk().crossing(1));
        HullResult thin = shoot(twoPlates(), ap(12));
        assertEquals(HullOutcome.PENETRATION, thin.outcome());
        assertEquals(2, thin.interiorPen(), EPS);
        assertEquals(1, thin.walk().stoppedAt());
        HullResult graze = shoot(twoPlates(), ap(9.9F));
        assertEquals(HullOutcome.PARTIAL, graze.outcome());
        assertFalse(graze.through(), "ruling 1: the ramp keeps its damage, nothing behind is reached");
        assertFalse(graze.outcome().isThrough());
        assertEquals(0, graze.interiorPen());
        assertTrue(graze.hullDamage() > 0);
        HullResult first = shoot(twoPlates(), ap(5));
        assertEquals(HullOutcome.ABSORBED, first.outcome());
        assertEquals(0, first.walk().stoppedAt());
    }

    @Test
    void throughIffInteriorPenAcrossTheRamp() {
        FakeHull h = twoPlates();
        for (float pen = 1; pen < 80; pen += 1.7F) {
            HullResult r = shoot(h, ap(pen));
            assertEquals(r.interiorPen() > 0, r.outcome().isThrough(), "pen " + pen + " " + r.outcome());
            assertEquals(r.through(), r.outcome().isThrough());
            assertTrue(!r.exits() || r.through());
            assertEquals(pen > 30, r.exits(), "pen " + pen);
        }
    }

    @Test
    void unboundLaterCrossingsCostNothing() {
        FakeHull h = hull(SKIN_AND_BACK);
        h.script = List.of(cross(h, 3, "skin", true), cross(h, 1, null, true), cross(h, -3, null, false));
        assertEquals(90, shoot(h, ap(100)).residualPen(), EPS);
    }

    @Test
    void theTwoFacesOfOneSheetAreOneLayer() {
        FakeHull h = hull(SKIN_AND_BACK);
        h.script = List.of(cross(h, 3, "skin", true), cross(h, 2.95F, "skin", false));
        assertEquals(90, shoot(h, ap(100)).residualPen(), EPS);
        h.script = List.of(cross(h, 3, "skin", true), cross(h, 2.5F, "skin", false));
        assertEquals(80, shoot(h, ap(100)).residualPen(), EPS, "half a metre apart is another sheet");
        h.script = List.of(cross(h, 3, "skin", true), cross(h, 2.95F, "back", false));
        assertEquals(70, shoot(h, ap(100)).residualPen(), EPS, "a different plate always counts");
    }

    @Test
    void gapsInsideAPlateCostAShapedCharge() {
        FakeHull h = hull("{\"sk\":{\"layers\":[{\"mm\":8,\"mat\":\"rha\"},{\"gap\":0.6},{\"mm\":30,\"mat\":\"rha\"}]}}");
        h.script = List.of(cross(h, 3, "sk", true));
        Threat heat = new Threat(ThreatKind.HEAT, 100, 200, 1f, 80, 1.0f, 0, 0);
        float f = 1 / (1 + PlateWalk.STANDOFF_LOSS * 0.6F);
        HullResult r = shoot(h, heat);
        assertEquals((200 - 8) * f - 30, r.residualPen(), EPS);
        assertEquals((8 * f + 30) / f, r.effectiveThickness(), EPS);
        Threat tandem = new Threat(ThreatKind.TANDEM_HEAT, 100, 200, 1f, 80, 1.0f, 0, 0);
        assertEquals(200 - 38, shoot(h, tandem).residualPen(), EPS);
        assertEquals(38, shoot(h, ap(1000)).effectiveThickness(), EPS);
    }

    @Test
    void overmatchReadsTheOuterLayerOnly() {
        FakeHull h = hull("{\"sk\":{\"facing\":\"front\",\"slope\":87,\"layers\":[{\"mm\":8,\"mat\":\"rha\"},"
                + "{\"gap\":0.6},{\"mm\":100,\"mat\":\"rha\"}]}}");
        h.script = List.of(cross(h, 3, "sk", true));
        Threat big = new Threat(ThreatKind.KINETIC, 30, 500, 1f, 70, 1.0f, 1.0f, 0);
        assertNotEquals(HullOutcome.RICOCHET, shoot(h, big).outcome(), "30 mm vs 8 mm outer skin overmatches");
        Threat small = new Threat(ThreatKind.KINETIC, 7, 500, 1f, 70, 1.0f, 1.0f, 0);
        assertEquals(HullOutcome.RICOCHET, shoot(h, small).outcome());
        assertFalse(shoot(h, small).through());
        assertEquals(0, shoot(h, small).walk().layers());
    }

    @Test
    void aProfileWithoutPlatesStaysAtTheEntry() {
        FakeHull h = new FakeHull();
        h.profile = new Gson().fromJson("{\"front\":200,\"side\":80,\"rear\":40,\"top\":30}", HullProfile.class);
        HullResult r = shoot(h, ap(300));
        assertNotNull(r);
        assertEquals(1, r.walk().layers());
        assertEquals(100, r.residualPen(), EPS);
    }

    @Test
    void anEntryOutsideTheHitIsAMiss() {
        FakeHull h = hull(SKIN_AND_BACK);
        h.script = List.of(cross(h, -3, "back", false));
        assertNull(shoot(h, ap(100)));
    }

    @Test
    void exitSpeedInvertsThePenetrationLaw() {
        HullResult r = shoot(twoPlates(), ap(100));
        assertEquals(70, r.residualPen(), EPS);
        assertEquals(1000 * (float) Math.sqrt(0.7), r.exitSpeed(1000, 2.0f), 0.5F);
        assertEquals(700, r.exitSpeed(1000, 1.0f), 0.5F);
        assertEquals(1000 * (float) Math.pow(0.7, 1 / 1.43), r.exitSpeed(1000, 1.43f), 0.5F);
        assertEquals(0, shoot(twoPlates(), ap(12)).exitSpeed(1000, 2.0f));
    }

    @Test
    void realBoxCrossingsCarryAThreatThroughTheHull() {
        FakeHull h = hull("{\"p\":{\"layers\":[{\"mm\":10,\"mat\":\"rha\"}]}}");
        h.plate = "p";
        Vec3 start = h.world(0, 1, 10), at = h.world(0, 1, 3);
        HullResult r = HullResolver.resolve(h, start, at.subtract(start), at, ap(100), 100);
        assertNotNull(r);
        assertEquals(80, r.residualPen(), EPS, "front and rear skin of the main box");
        assertEquals(2, r.walk().layers());
    }

    private static final com.wf.wflib.armor.api.hull.EraType K5 = new com.wf.wflib.armor.api.hull.EraType("t:k5", 0.5f,
            0.3f, 0.0f, 20, 40, 1000, 0.1f, 0, 0, 0.1f, null, null);

    private static FakeHull arrayHull() {
        com.wf.wflib.armor.api.hull.EraTypes.register(K5);
        FakeHull h = hull("{\"arr\":{\"layers\":[{\"era\":\"t:k5\",\"cells\":8},{\"mm\":10,\"mat\":\"rha\"}]}}");
        h.script = List.of(centre(h, 3, "arr", true), centre(h, -3, "arr", false));
        return h;
    }

    /** Off every brick edge: u at 0.5 of a cell, v at 0.4. */
    private static FakeHull.Hit centre(FakeHull h, float z, String plate, boolean entering) {
        Vector3f n = h.rotation.transform(new Vector3f(0, 0, entering ? 1 : -1));
        return new FakeHull.Hit(0, h.world(0.2f, 1.1f, z), n, plate, entering);
    }

    private static HullResult shootArray(FakeHull h, Threat t) {
        Vec3 start = h.world(0.2f, 1.1f, 10), at = h.world(0.2f, 1.1f, 3);
        return HullResolver.resolve(h, start, at.subtract(start), at, t, 100);
    }

    private static Threat heat(float pen) {
        return new Threat(ThreatKind.HEAT, 80, pen, 1f, 80, 1.0f, 0, 0);
    }

    @Test
    void anIntegralArrayCutsTheJetAndReportsTheBrickItSetOff() {
        FakeHull h = arrayHull();
        HullResult r = shootArray(h, heat(100));
        assertEquals(1, r.walk().eraHits().size());
        int cell = r.walk().eraHits().get(0).cell();
        assertTrue(cell >= 0 && cell < 8);
        float cut = 100 * (1 - 0.5f * 0.3f) - 10;
        assertEquals(cut, r.interiorPen(), 0.5F, "the head-on shot gets the minimum obliquity effect");
        assertFalse(h.spent.contains("arr:" + cell), "resolve never spends a brick");
    }

    @Test
    void aSpentBrickCutsNothing() {
        FakeHull h = arrayHull();
        int cell = shootArray(h, heat(100)).walk().eraHits().get(0).cell();
        h.spent.add("arr:" + cell);
        HullResult r = shootArray(h, heat(100));
        assertTrue(r.walk().eraHits().isEmpty());
        assertEquals(90, r.interiorPen(), EPS);
    }

    @Test
    void theExitCrossingDoesNotWalkTheArrayAgain() {
        assertEquals(1, shootArray(arrayHull(), heat(300)).walk().eraHits().size());
    }

    @Test
    void aLeadStepSpendsTheBudgetBeforeTheEntryPlate() {
        FakeHull h = twoPlates();
        HullResult r = HullResolver.resolve(h, h.world(0, 1, 10), h.world(0, 1, 3).subtract(h.world(0, 1, 10)),
                h.world(0, 1, 3), ap(100), 100, walk -> walk.cut(0.5f));
        assertEquals(40, r.interiorPen(), EPS, "50 left behind the lead, 10 spent");
        assertEquals(20, r.effectiveThickness(), EPS, "the entry plate needs twice the pen behind a half cut");
    }
}

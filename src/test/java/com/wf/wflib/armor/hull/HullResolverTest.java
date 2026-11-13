package com.wf.wflib.armor.hull;

import com.wf.wflib.armor.api.hull.HullFace;
import com.wf.wflib.armor.api.hull.HullFaces;
import com.wf.wflib.armor.api.hull.HullOutcome;
import com.wf.wflib.armor.api.hull.HullProfile;
import com.wf.wflib.armor.api.hull.HullResolver;
import com.wf.wflib.armor.api.hull.HullResult;
import com.wf.wflib.armor.api.hull.HullWear;
import com.wf.wflib.damage.api.Penetrator;
import com.wf.wflib.damage.api.Threat;
import com.wf.wflib.damage.api.ThreatKind;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HullResolverTest {
    private static FakeHull boxes(float front) {
        FakeHull hull = new FakeHull();
        hull.mesh = false;
        HullProfile p = new HullProfile();
        p.front = front;
        p.side = 50;
        p.top = 30;
        p.ce = new HullFaces();
        p.ce.front = 2 * front;
        hull.profile = p;
        return hull;
    }

    /** Head-on at the front face centre: face cos 1. */
    private static HullResult frontal(FakeHull hull, float pen, ThreatKind kind) {
        Vec3 start = hull.world(0, 1, 10), hit = hull.world(0, 1, 3);
        return HullResolver.resolve(hull, start, hit.subtract(start), hit, new Threat(kind, 20, pen), 100);
    }

    @Test
    void ratioBandsPickTheOutcome() {
        FakeHull hull = boxes(200);
        float[] ratio = {0.5F, 0.74F, 0.76F, 0.99F, 1.01F, 1.49F, 1.51F, 3};
        HullOutcome[] want = {HullOutcome.ABSORBED, HullOutcome.ABSORBED, HullOutcome.PARTIAL, HullOutcome.PARTIAL,
                HullOutcome.PENETRATION, HullOutcome.PENETRATION, HullOutcome.OVERMATCH, HullOutcome.OVERMATCH};
        for (int i = 0; i < ratio.length; i++) {
            HullResult r = frontal(hull, 200 * ratio[i], ThreatKind.KINETIC);
            assertNotNull(r);
            assertEquals(want[i], r.outcome(), "ratio " + ratio[i]);
            assertEquals(200, r.effectiveThickness(), 1e-2F);
        }
        assertEquals(100 * 0.06F * 0.5F / 0.75F, frontal(hull, 100, ThreatKind.KINETIC).hullDamage(), 1e-3F, "spall");
        assertEquals(100, frontal(hull, 600, ThreatKind.KINETIC).hullDamage());
    }

    @Test
    void shapedChargeReadsCe() {
        FakeHull hull = boxes(200);
        assertEquals(400, frontal(hull, 300, ThreatKind.HEAT).nominalThickness());
        assertEquals(200, frontal(hull, 300, ThreatKind.KINETIC).nominalThickness());
        hull.profile.ce.front = 0;
        assertEquals(200, frontal(hull, 300, ThreatKind.HEAT).nominalThickness(), "ce 0 => ke");
    }

    private static Threat dart(float caliber, float pen, Penetrator p) {
        return new Threat(ThreatKind.KINETIC, caliber, pen, 1f, p.ricochetDeg, p.slopeExp, p.overmatchTD, 0);
    }

    @Test
    void ricochetFollowsTheThreatNotTheHull() {
        FakeHull hull = boxes(200);
        Vec3 hit = hull.world(1.3F, 2, -1.5F), start = hull.world(1.3F, 2.6F, -5.5F);
        Vec3 dir = hit.subtract(start).normalize();
        HullResult r = HullResolver.resolve(hull, start, dir, hit, dart(20, 100, Penetrator.AP), 100);
        assertNotNull(r);
        assertEquals(HullOutcome.RICOCHET, r.outcome());
        assertEquals(0, r.hullDamage());
        HullResolver.Plate p = HullResolver.plate(hull, hull.profile, start, hit.add(dir.scale(0.1)), dir, false);
        assertEquals(HullFace.TOP, p.face(), "entered through the roof of the main box");
        assertEquals(0.6 / Math.hypot(0.6, 4), p.cos(), 1e-3);
        assertTrue(p.surfaceIncidence() > 80 && p.surfaceIncidence() < 82, "incidence " + p.surfaceIncidence());
        assertEquals(HullOutcome.RICOCHET,
                HullResolver.resolve(hull, start, dir, hit, dart(20, 100, Penetrator.APDS), 100).outcome());
        assertEquals(HullOutcome.RICOCHET,
                HullResolver.resolve(hull, start, dir, hit, new Threat(ThreatKind.HEAT, 100, 100), 100).outcome(),
                "fuze graze 80");
        assertNotEquals(HullOutcome.RICOCHET,
                HullResolver.resolve(hull, start, dir, hit, dart(20, 100, Penetrator.APFSDS), 100).outcome(),
                "dart 82 holds");
        assertNotEquals(HullOutcome.RICOCHET,
                HullResolver.resolve(hull, start, dir, hit, new Threat(ThreatKind.HESH, 100, 100), 100).outcome());
        assertNotEquals(HullOutcome.RICOCHET,
                HullResolver.resolve(hull, start, dir, hit, dart(40, 100, Penetrator.AP), 100).outcome(),
                "caliber 40 / 30 mm roof >= 1 overmatches");
        assertEquals(HullOutcome.RICOCHET,
                HullResolver.resolve(hull, start, dir, hit, dart(20, 400, Penetrator.AP), 100).outcome(),
                "pen alone no longer overmatches");
    }

    @Test
    void wearThinsThePlate() {
        FakeHull hull = boxes(200);
        int region = HullWear.region(hull.main, hull.world(0, 1, 3), null);
        hull.wear.apply(region, 1e6F, HullWear.Kind.KINETIC, 200);
        assertEquals(1, hull.wear.wear(region));
        assertEquals(200 * 0.6F, frontal(hull, 150, ThreatKind.KINETIC).effectiveThickness(), 1e-2F);
    }

    @Test
    void unarmouredOrMissedIsNull() {
        FakeHull hull = boxes(0);
        hull.profile.side = 0;
        hull.profile.top = 0;
        assertNull(frontal(hull, 100, ThreatKind.KINETIC));
        FakeHull armoured = boxes(200);
        Vec3 start = armoured.world(0, 20, 10), end = armoured.world(0, 20, -10);
        assertNull(HullResolver.resolve(armoured, start, end.subtract(start), end, new Threat(ThreatKind.KINETIC, 20, 100), 100));
    }

    @Test
    void facesBreakTiesTowardTopThenFront() {
        assertEquals(HullFace.TOP, HullFace.of(new Vector3f(1, 1, 1)));
        assertEquals(HullFace.BOTTOM, HullFace.of(new Vector3f(0, -1, 1)));
        assertEquals(HullFace.FRONT, HullFace.of(new Vector3f(1, 0, 1)));
        assertEquals(HullFace.REAR, HullFace.of(new Vector3f(0.5F, 0, -1)));
        assertEquals(HullFace.SIDE, HullFace.of(new Vector3f(-1, 0.2F, 0.5F)));
        assertEquals(new Vector3f(0, 0, -1), HullFace.aspectAxis(new Vector3f(0.5F, 0, -1)));
    }
}

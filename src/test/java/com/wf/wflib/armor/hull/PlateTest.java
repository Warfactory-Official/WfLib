package com.wf.wflib.armor.hull;

import com.google.gson.Gson;
import com.wf.wflib.armor.api.hull.HullProfile;
import com.wf.wflib.armor.api.hull.HullResolver;
import com.wf.wflib.armor.api.hull.PlateMaterial;
import com.wf.wflib.armor.api.hull.PlateMaterials;
import com.wf.wflib.armor.api.hull.PlateSpec;
import com.wf.wflib.damage.api.Penetrator;
import com.wf.wflib.damage.api.Threat;
import com.wf.wflib.damage.api.ThreatKind;
import com.wf.wflib.armor.api.hull.HullOutcome;
import com.wf.wflib.armor.api.hull.HullResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlateTest {
    private static final float EPS = 1e-4F;

    private static PlateSpec spec(String json) {
        PlateSpec s = new Gson().fromJson(json, PlateSpec.class);
        s.compile("t");
        return s;
    }

    private static FakeHull hull(String plates) {
        FakeHull hull = new FakeHull();
        hull.profile = new Gson().fromJson("{\"front\":200,\"side\":80,\"rear\":40,\"top\":30,\"bottom\":20,"
                + "\"parts\":{\"turret\":{\"front\":300}},"
                + "\"zones\":[{\"name\":\"z\",\"face\":\"front\",\"min\":[-1.6,0,1.5],\"max\":[1.6,2,3.3],\"ke\":110}],"
                + "\"plates\":" + plates + "}", HullProfile.class);
        hull.profile.validate();
        return hull;
    }

    @Test
    void losBecomesNormalThickness() {
        PlateSpec s = spec("{\"slope\":60,\"facing\":\"front\",\"los\":{\"ke\":940,\"ce\":1400}}");
        assertEquals(470, s.nominal(false), 1e-2F);
        assertEquals(700, s.nominal(true), 1e-2F);
        assertEquals(940, spec("{\"facing\":\"top\",\"los\":{\"ke\":940}}").nominal(false), EPS, "top: no slope factor");
        assertEquals(940, spec("{\"facing\":\"top\",\"los\":{\"ke\":940}}").nominal(true), EPS, "ce 0 => ke");
    }

    @Test
    void layerUsesMaterialFactors() {
        assertEquals(50, spec("{\"layers\":[{\"mm\":50,\"mat\":\"rha\"}]}").nominal(false), EPS);
        PlateSpec al = spec("{\"facing\":\"side\",\"layers\":[{\"mm\":40,\"mat\":\"aluminium\"}]}");
        assertEquals(14, al.nominal(false), EPS);
        assertEquals(16, al.nominal(true), EPS);
        PlateMaterials.register(new PlateMaterial("composite_test", 2f, 3f, 0f, 0.5f));
        assertEquals(200, spec("{\"layers\":[{\"mm\":100,\"mat\":\"composite_test\"}]}").nominal(false), EPS);
    }

    @Test
    void layersSumAndGapsSitBetweenSolids() {
        PlateSpec s = spec("{\"layers\":[{\"mm\":8,\"mat\":\"rha\"},{\"gap\":0.6},{\"mm\":30,\"mat\":\"cast\"}]}");
        assertEquals(8 + 30 * 0.92F, s.nominal(false), EPS);
        assertEquals(8, s.outer(false), EPS);
        assertEquals(8, s.outer(true), EPS);
    }

    private static void assertVec(Vector3f want, Vector3f got) {
        assertNotNull(got);
        assertEquals(want.x, got.x, EPS, "x " + got);
        assertEquals(want.y, got.y, EPS, "y " + got);
        assertEquals(want.z, got.z, EPS, "z " + got);
    }

    @Test
    void facingAndSlopeGiveTheRestNormal() {
        float c = (float) Math.cos(Math.toRadians(35)), u = (float) Math.sin(Math.toRadians(35));
        assertVec(new Vector3f(0, u, c), spec("{\"slope\":35,\"facing\":\"front\",\"layers\":[{\"mm\":9,\"mat\":\"rha\"}]}").restNormal(0));
        assertVec(new Vector3f(0, u, -c), spec("{\"slope\":35,\"facing\":\"rear\",\"layers\":[{\"mm\":9,\"mat\":\"rha\"}]}").restNormal(0));
        PlateSpec side = spec("{\"slope\":35,\"facing\":\"side\",\"layers\":[{\"mm\":9,\"mat\":\"rha\"}]}");
        assertVec(new Vector3f(c, u, 0), side.restNormal(0.7F));
        assertVec(new Vector3f(-c, u, 0), side.restNormal(-0.7F));
        assertVec(new Vector3f(0, 1, 0), spec("{\"facing\":\"top\",\"layers\":[{\"mm\":9,\"mat\":\"rha\"}]}").restNormal(0));
        assertVec(new Vector3f(0, -1, 0), spec("{\"facing\":\"bottom\",\"layers\":[{\"mm\":9,\"mat\":\"rha\"}]}").restNormal(0));
        assertVec(new Vector3f(-c, u, 0), spec("{\"slope\":35,\"facing\":90,\"layers\":[{\"mm\":9,\"mat\":\"rha\"}]}").restNormal(0));
        assertVec(new Vector3f(0, 0, 1), spec("{\"facing\":\"front\",\"layers\":[{\"mm\":9,\"mat\":\"rha\"}]}").restNormal(0));
        assertNull(spec("{\"curved\":true,\"layers\":[{\"mm\":9,\"mat\":\"rha\"}]}").restNormal(0));
        assertNull(spec("{\"layers\":[{\"mm\":9,\"mat\":\"rha\"}]}").restNormal(0));
    }

    @Test
    void invalidDeclarationsFailFast() {
        String[] bad = {
                "{\"layers\":[{\"mm\":9,\"mat\":\"rha\"},{\"gap\":0.6}]}",
                "{\"layers\":[{\"gap\":0.6},{\"mm\":9,\"mat\":\"rha\"}]}",
                "{\"layers\":[{\"gap\":0.6}]}",
                "{\"layers\":[{\"mm\":9,\"mat\":\"rha\"},{\"gap\":0.6},{\"gap\":0.6},{\"mm\":9,\"mat\":\"rha\"}]}",
                "{\"layers\":[{\"mm\":9,\"mat\":\"rha\",\"gap\":0.6}]}",
                "{\"layers\":[{\"mm\":9,\"mat\":\"rha\"},{\"mm\":0,\"mat\":\"rha\"}]}",
                "{\"layers\":[{\"mm\":9,\"mat\":\"unobtainium\"}]}",
                "{\"layers\":[{\"mm\":9}]}",
                "{}",
                "{\"slope\":30,\"layers\":[{\"mm\":9,\"mat\":\"rha\"}]}",
                "{\"los\":{\"ke\":900}}",
                "{\"curved\":true,\"facing\":\"front\",\"layers\":[{\"mm\":9,\"mat\":\"rha\"}]}",
                "{\"facing\":\"front\",\"los\":{\"ke\":900},\"layers\":[{\"mm\":9,\"mat\":\"rha\"}]}",
                "{\"facing\":\"sideways\",\"layers\":[{\"mm\":9,\"mat\":\"rha\"}]}",
                "{\"slope\":95,\"facing\":\"front\",\"layers\":[{\"mm\":9,\"mat\":\"rha\"}]}",
        };
        for (String b : bad) {
            PlateSpec s = new Gson().fromJson(b, PlateSpec.class);
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> s.compile("p1"), b);
            assertTrue(e.getMessage().startsWith("plate 'p1'"), e.getMessage());
        }
    }

    private static HullResolver.Plate hit(FakeHull hull, Vec3 start, Vec3 hit) {
        Vec3 dir = hit.subtract(start).normalize();
        return HullResolver.plate(hull, hull.profile, start, hit.add(dir.scale(0.1)), dir, false);
    }

    @Test
    void precedenceIsPlateZonePartHull() {
        FakeHull hull = hull("{\"p\":{\"facing\":\"front\",\"layers\":[{\"mm\":50,\"mat\":\"rha\"}]}}");
        Vec3 start = hull.world(0, 0.5F, 10), at = hull.world(0, 0.5F, 3);
        HullResolver.Plate z = hit(hull, start, at);
        assertEquals("z", z.zone());
        assertNull(z.plateId());
        assertEquals(110, z.nominal());
        hull.plate = "p";
        HullResolver.Plate p = hit(hull, start, at);
        assertEquals("p", p.plateId());
        assertNull(p.zone(), "plate beats the zone");
        assertEquals(50, p.nominal());
        hull.plate = null;
        hull.profile.zones = java.util.List.of();
        assertEquals(200, hit(hull, start, at).nominal(), "hull faces last");
        Vector3f aim = new Vector3f(0, 0, -1);
        hull.turretWorld.transform(aim);
        HullResolver.Plate part = HullResolver.plate(hull, hull.profile, 1, hull.world(0, 2.4F, 1.5F),
                new Vector3f(0, 0, 1), null, new Vec3(aim), false);
        assertEquals("turret", part.part());
        assertEquals(300, part.nominal(), "part faces beat hull faces");
        assertEquals(50, HullResolver.plate(hull, hull.profile, 1, hull.world(0, 2.4F, 1.5F),
                new Vector3f(0, 0, 1), "p", new Vec3(aim), false).nominal(), "plate beats part faces");
    }

    @Test
    void sideMirrorsByStruckSign() {
        FakeHull hull = hull("{\"s\":{\"slope\":30,\"facing\":\"side\",\"layers\":[{\"mm\":20,\"mat\":\"rha\"}]}}");
        hull.profile.zones = java.util.List.of();
        hull.plate = "s";
        double c = Math.cos(Math.toRadians(30)), u = Math.sin(Math.toRadians(30));
        double want = (c + 0.5 * u) / Math.sqrt(1.25);
        for (int sign : new int[] {1, -1}) {
            Vector3f d = hull.rotation.transform(new Vector3f(-sign, -0.5F, 0).normalize());
            Vec3 dir = new Vec3(d);
            Vec3 at = hull.world(1.6F * sign, 1.2F, 0);
            Vec3 start = at.subtract(dir.scale(4));
            HullResolver.Plate p = HullResolver.plate(hull, hull.profile, start, at.add(dir.scale(0.1)), dir, false);
            assertEquals("s", p.plateId());
            assertEquals(want, p.cos(), 1e-3, "side " + sign);
            assertEquals(want, p.surfaceCos(), 1e-3);
            assertTrue(new Vec3(p.normal()).dot(dir) < 0, "normal toward the shooter");
            assertEquals(20 / want, p.effective(), 0.05);
        }
    }

    @Test
    void curvedAndUndeclaredFallBackToTheMeshNormal() {
        FakeHull hull = hull("{\"c\":{\"curved\":true,\"layers\":[{\"mm\":25,\"mat\":\"rha\"}]},"
                + "\"u\":{\"layers\":[{\"mm\":25,\"mat\":\"cast\"}]}}");
        Vec3 start = hull.world(0.3F, 1.2F, 10), at = hull.world(0.3F, 1.2F, 3);
        Vec3 off = hull.world(2.5F, 1.9F, 9);
        hull.plate = "c";
        for (Vec3 from : new Vec3[] {start, off}) {
            HullResolver.Plate p = hit(hull, from, at);
            assertEquals(p.meshCos(), p.cos(), 0);
            assertEquals(p.meshCos(), p.surfaceCos(), 0);
            assertEquals(25, p.nominal());
        }
        hull.plate = "u";
        HullResolver.Plate p = hit(hull, off, at);
        assertEquals(p.meshCos(), p.cos(), 0);
        assertEquals(23, p.nominal(), EPS);
    }

    @Test
    void declaredNormalDrivesRicochetAndEffectiveThickness() {
        FakeHull hull = hull("{\"ufp\":{\"slope\":80,\"facing\":\"front\",\"layers\":[{\"mm\":50,\"mat\":\"rha\"}]}}");
        hull.profile.zones = java.util.List.of();
        hull.plate = "ufp";
        Vec3 start = hull.world(0, 1, 10), at = hull.world(0, 1, 3);
        Vec3 dir = at.subtract(start);
        HullResolver.Plate p = hit(hull, start, at);
        assertEquals(0, p.meshIncidence(), 0.5F, "mesh face is square on");
        assertEquals(80, p.incidence(), 0.5F);
        assertEquals(50 / Math.cos(Math.toRadians(80)), p.effective(), 1.0);
        assertEquals(HullOutcome.RICOCHET, resolve(hull, start, dir, at, Penetrator.AP, 30).outcome());
        HullResult dart = resolve(hull, start, dir, at, Penetrator.APFSDS, 120);
        assertNotEquals(HullOutcome.RICOCHET, dart.outcome(), "dart ricochet 82 holds at 80");
        assertEquals(HullOutcome.OVERMATCH, dart.outcome(), "pen 1000 vs 288 mm effective");
        assertEquals(p.effective(), dart.effectiveThickness(), 0.05F, "entry plate only; the rear skin is the exit");
        assertEquals(1000 - 2 * p.effective(), dart.residualPen(), 0.1F);
    }

    private static HullResult resolve(FakeHull hull, Vec3 start, Vec3 dir, Vec3 at, Penetrator p, float caliber) {
        Threat t = new Threat(ThreatKind.KINETIC, caliber, 1000, 1f, p.ricochetDeg, p.slopeExp, p.overmatchTD, 0);
        return HullResolver.resolve(hull, start, dir, at, t, 100);
    }

    @Test
    void unknownPlateIdIsALoadError() {
        FakeHull hull = hull("{}");
        hull.plate = "ghost";
        Vec3 start = hull.world(0, 1, 10), at = hull.world(0, 1, 3);
        assertThrows(IllegalStateException.class, () -> hit(hull, start, at));
    }

    @Test
    void plateOnlyProfileIsDefined() {
        HullProfile p = new Gson().fromJson("{\"plates\":{\"a\":{\"layers\":[{\"mm\":9,\"mat\":\"rha\"}]}}}", HullProfile.class);
        assertTrue(p.isDefined());
        assertTrue(!new HullProfile().isDefined());
    }

    @Test
    void anEraArrayIsTheFirstLayerAndAddsNoRhae() {
        com.wf.wflib.armor.api.hull.EraTypes.register(new com.wf.wflib.armor.api.hull.EraType("t:k5", 0.5f, 0.3f, 0, 20,
                40, 1000, 0.1f, 0, 0, 0.1f, null, null));
        PlateSpec los = spec("{\"facing\":\"front\",\"los\":{\"ke\":500},\"layers\":[{\"era\":\"t:k5\",\"cells\":12}]}");
        assertEquals(500, los.nominal(false), EPS);
        assertEquals(12, los.eraCells());
        assertEquals("t:k5", los.era().id());
        PlateSpec solid = spec("{\"layers\":[{\"era\":\"t:k5\",\"cells\":4},{\"mm\":30,\"mat\":\"rha\"}],\"curved\":true}");
        assertEquals(30, solid.nominal(false), EPS);
        assertEquals(30, solid.outer(false), EPS);
        for (String bad : new String[] {
                "{\"curved\":true,\"layers\":[{\"mm\":30,\"mat\":\"rha\"},{\"era\":\"t:k5\",\"cells\":4}]}",
                "{\"curved\":true,\"layers\":[{\"era\":\"t:k5\",\"cells\":4}]}",
                "{\"curved\":true,\"layers\":[{\"era\":\"t:k5\",\"cells\":65},{\"mm\":30,\"mat\":\"rha\"}]}",
                "{\"curved\":true,\"layers\":[{\"era\":\"nope\",\"cells\":4},{\"mm\":30,\"mat\":\"rha\"}]}"}) {
            assertThrows(IllegalStateException.class, () -> spec(bad), bad);
        }
    }

    @Test
    void theGridMapsNearbyPointsToNeighbouringCellsAndCoversEveryCell() {
        Vector3f n = new Vector3f(0, 0, 1);
        boolean[] seen = new boolean[10];
        for (float x = -3; x < 3; x += 0.1f) {
            for (float y = 0; y < 2; y += 0.1f) {
                seen[com.wf.wflib.armor.api.hull.EraGrid.cell(new Vector3f(x, y, 0), n, 10)] = true;
            }
        }
        for (boolean b : seen) {
            assertTrue(b);
        }
        int[] out = new int[8];
        int count = com.wf.wflib.armor.api.hull.EraGrid.neighbours(0, 10, out);
        assertTrue(count >= 3 && count <= 8);
        for (int i = 0; i < count; i++) {
            assertNotEquals(0, out[i]);
        }
        assertEquals(com.wf.wflib.armor.api.hull.EraGrid.cell(new Vector3f(0.05f, 0.05f, 0), n, 10),
                com.wf.wflib.armor.api.hull.EraGrid.cell(new Vector3f(0.2f, 0.1f, 5), n, 10), "same brick, depth ignored");
    }
}

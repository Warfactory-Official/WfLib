package com.wf.wflib.armor.hull;

import com.google.gson.Gson;
import com.wf.wflib.armor.api.hull.HullOutcome;
import com.wf.wflib.armor.api.hull.HullProfile;
import com.wf.wflib.armor.api.hull.HullResolver;
import com.wf.wflib.armor.api.hull.HullResult;
import com.wf.wflib.armor.api.hull.HullWear;
import com.wf.wflib.damage.api.ThreatKind;
import com.wf.wflib.geom.api.OBB;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hash recorded from the pre-consolidation hull armour copy driven through {@link FakeHull} (2026-10-30). */
class HullGoldenTest {
    static final String PROFILE = """
            {"front":200,"side":80,"rear":40,"top":30,"bottom":20,"ce":{"front":500,"side":150},
             "ricochet_angle":68,"wear_floor":0.55,
             "parts":{"turret":{"front":300,"side":120,"rear":60,"top":40,"ce":{"front":700}}},
             "zones":[{"name":"lfp","face":"front","min":[-1.6,-1.0,1.5],"max":[1.6,0.9,3.3],"ke":110,"ce":0,"plate":false},
                      {"name":"mantlet","part":"turret","min":[-0.7,1.9,0.8],"max":[0.7,2.9,2.0],"ke":150,"ce":260,"plate":true}]}""";
    static final float[] PEN = {5, 40, 120, 300, 900};

    long h = 17;
    final java.util.Set<Object> seen = new java.util.HashSet<>();

    void f(float x) {
        h = h * 31 + Float.floatToIntBits(x);
    }

    void d(double x) {
        h = h * 31 + Double.doubleToLongBits(x);
    }

    void v(Vec3 x) {
        d(x.x);
        d(x.y);
        d(x.z);
    }

    void v(Vector3f x) {
        f(x.x);
        f(x.y);
        f(x.z);
    }

    static FakeHull hull() {
        FakeHull hull = new FakeHull();
        hull.profile = new Gson().fromJson(PROFILE, HullProfile.class);
        return hull;
    }

    /** Shot {@code i}: start, velocity, struck point (first box crossing, else the aim point). */
    static Vec3[] shot(FakeHull hull, int i) {
        double az = i * 0.7, el = -0.6 + (i % 13) * 0.1;
        Vec3 out = new Vec3(Math.cos(el) * Math.sin(az), Math.sin(el), Math.cos(el) * Math.cos(az));
        Vec3 aim = hull.world((i % 5 - 2) * 0.6F, 0.4F + (i % 7) * 0.35F, (i % 9 - 4) * 0.7F);
        Vec3 start = aim.add(out.scale(25));
        Vec3 velocity = out.scale(-900);
        OBB.SegmentHit hit = OBB.first(hull.getOBBs(), start, aim.subtract(out.scale(25)));
        return new Vec3[] {start, velocity, hit == null ? aim : hit.point()};
    }

    static void wear(HullWear w) {
        w.apply(4, 300, HullWear.Kind.BLAST, 200);
        w.apply(12, 80, HullWear.Kind.KINETIC, 80);
        w.apply(40, 500, HullWear.Kind.THERMAL, 30);
    }

    private void result(HullResult r) {
        if (r == null) {
            f(-1);
            return;
        }
        seen.add(r.outcome());
        f(r.outcome().ordinal());
        f(r.hullDamage());
        f(r.partDamage());
        f(r.nominalThickness());
        f(r.effectiveThickness());
        f(r.penetration());
        v(r.normal());
        v(r.faceNormal());
    }

    private void plate(HullResolver.Plate p) {
        if (p == null) {
            f(-1);
            return;
        }
        seen.add(String.valueOf(p.part()) + "/" + p.zone());
        if (p.plate()) {
            seen.add("plate");
        }
        v(p.point());
        v(p.normal());
        f(p.face().ordinal());
        v(p.faceNormal());
        h = h * 31 + java.util.Objects.hashCode(p.part()) + 7L * java.util.Objects.hashCode(p.zone());
        f(p.nominal());
        f(p.plate() ? 1 : 0);
        f(p.wear());
        f(p.cos());
        f(p.surfaceCos());
    }

    long run() {
        h = 17;
        FakeHull hull = hull();
        wear(hull.wear);
        for (boolean mesh : new boolean[] {true, false}) {
            hull.mesh = mesh;
            for (int i = 0; i < 400; i++) {
                Vec3[] s = shot(hull, i);
                ThreatKind kind = ThreatKind.values()[i % ThreatKind.values().length];
                result(HullResolver.resolve(hull, s[0], s[1], s[2], PEN[i % PEN.length], 100, kind));
                Vec3 dir = s[1].normalize();
                plate(HullResolver.plate(hull, hull.profile, s[0], s[2].add(dir.scale(0.1)), dir, kind.isShapedCharge()));
                f(HullWear.region(hull.main, s[2], i % 2 == 0 ? null : new Vector3f(0, 1, 0).rotateX(i)));
            }
        }
        CompoundTag tag = new CompoundTag();
        hull.wear.save(tag);
        HullWear loaded = new HullWear();
        loaded.load(tag);
        HullWear synced = new HullWear();
        synced.fromSync(hull.wear.toSync());
        for (int r = 0; r < HullWear.COUNT; r++) {
            f(loaded.wear(r));
            f(synced.wear(r));
            f(hull.wear.thicknessScale(r, 0.55F));
        }
        return h;
    }

    @Test
    void resolverIsBitIdenticalToThePreConsolidationCopy() {
        assertEquals(GOLDEN, run());
        for (Object o : new Object[] {HullOutcome.RICOCHET, HullOutcome.ABSORBED, HullOutcome.PARTIAL,
                HullOutcome.PENETRATION, HullOutcome.OVERMATCH, "null/null", "null/lfp", "turret/null",
                "turret/mantlet", "plate"}) {
            assertTrue(seen.contains(o), "scenario never reaches " + o + ": " + seen);
        }
    }

    static final long GOLDEN = 0x297903725a5fe63L;
}

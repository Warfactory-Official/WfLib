package com.wf.wflib.round.api;

import com.wf.wflib.ammo.AmmoCatalogue;
import com.wf.wflib.ammo.api.Ammo;
import com.wf.wflib.ammo.api.AmmoPresets;
import com.wf.wflib.ammo.api.AmmoVariant;
import com.wf.wflib.damage.api.Penetrator;
import com.wf.wflib.damage.api.Threat;
import com.wf.wflib.damage.api.ThreatKind;
import com.wf.wflib.labs.api.LabFlight;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Armour pen retained vs range per AP round: {@code pen * (v / v0)^penExponent}, level flight. */
class PenRangeTest {
    private static final double[] RANGES = {0, 500, 1000, 2000};
    /** Retained fraction at 500 m: sub-calibre ~85 to 90 %, published. */
    private static final Map<String, Double> MIN_AT_500 = Map.of("25x137/ap", 0.80, "30x165/ap", 0.85,
            "30x150b/ap", 0.93, "105mm/apfsds", 0.95, "120mm/apfsds", 0.95, "125mm/apfsds", 0.95,
            "120mm_l30/apfsds", 0.95, "12.7x99/slap", 0.85);

    @BeforeAll
    static void define() {
        AmmoCatalogue.define();
    }

    private static ResourceLocation rl(String p) {
        return ResourceLocation.fromNamespaceAndPath("wflib", p);
    }

    private static double[] pen(String id) {
        AmmoVariant v = AmmoCatalogue.variant(rl(id));
        KineticPreset p = AmmoPresets.preset(AmmoCatalogue.calibre(v.calibre()), v);
        LabFlight.Sample[] flight = LabFlight.fly(p, v.speed(), RANGES);
        double[] out = new double[RANGES.length];
        StringBuilder row = new StringBuilder(id + " exp " + p.penExponent() + " pen mm @");
        for (int i = 0; i < out.length; i++) {
            out[i] = flight[i].reached()
                    ? Rounds.threat(p, new Vec3(flight[i].speed() / 20.0, 0, 0)).penetrationMm() : Double.NaN;
            row.append(' ').append((int) RANGES[i]).append("m=").append(Math.round(out[i] * 10) / 10.0);
        }
        System.out.println(row);
        return out;
    }

    @Test
    void subCalibreRoundsKeepPenetrationAtRange() {
        for (Map.Entry<String, Double> e : MIN_AT_500.entrySet()) {
            double[] pen = pen(e.getKey());
            assertEquals(AmmoCatalogue.variant(rl(e.getKey())).armorPenMm(), pen[0], 1e-3, e.getKey() + " muzzle");
            assertTrue(pen[1] / pen[0] >= e.getValue(), e.getKey() + " 500 m keeps " + pen[1] / pen[0]);
            assertTrue(pen[1] > pen[2] && pen[2] > pen[3], e.getKey() + " monotone");
        }
    }

    @Test
    void fullCalibreApFallsFasterThanDart() {
        double[] ap = pen("85x629r/ap");
        double[] dart = pen("120mm/apfsds");
        assertTrue(ap[2] / ap[0] < dart[2] / dart[0], "AP 1.43 vs rod 1.0 at 1000 m");
        for (String id : new String[]{"20x102/ap", "37x263b/ap", "88x571r/ap", "57x441r/ap"}) {
            pen(id);
        }
    }

    @Test
    void exponentScalesArmourPenOnly() {
        KineticPreset p = KineticPreset.builder(rl("t"), null, rl("inert")).speed(10).caliber(30)
                .armorPenetration(100).blockPen(100).penExponent(1.0).build();
        Threat half = Rounds.threat(p, new Vec3(5, 0, 0));
        assertEquals(50, half.penetrationMm(), 1e-3);
        assertEquals(25, BlockPen.capacity(100, 10, 5), 1e-9, "block pen keeps v^2");
        KineticPreset q = KineticPreset.builder(rl("t2"), null, rl("inert")).speed(10).caliber(30)
                .armorPenetration(100).build();
        assertEquals(25, Rounds.threat(q, new Vec3(5, 0, 0)).penetrationMm(), 1e-3, "default 2");
    }

    @Test
    void classesCarryThreatVariables() {
        Threat dart = Rounds.threat(AmmoPresets.preset(AmmoCatalogue.calibre(rl("120mm")),
                AmmoCatalogue.variant(rl("120mm/apfsds"))));
        assertEquals(82, dart.ricochetDeg());
        assertEquals(1.0f, dart.slopeExp());
        assertEquals(0, dart.overmatchTD());
        AmmoVariant ap = AmmoCatalogue.variant(rl("85x629r/ap"));
        Threat t = Rounds.threat(AmmoPresets.preset(AmmoCatalogue.calibre(ap.calibre()), ap));
        assertEquals(Penetrator.AP.ricochetDeg, t.ricochetDeg());
        assertEquals(1.0f, t.overmatchTD());
        assertEquals(0.16f, t.fillerKg());
        assertEquals(ThreatKind.HESH, Rounds.threat(AmmoPresets.preset(AmmoCatalogue.calibre(rl("120mm_l30")),
                AmmoCatalogue.variant(rl("120mm_l30/he")))).kind());
        assertEquals(ThreatKind.HESH, Rounds.threat(AmmoPresets.preset(AmmoCatalogue.calibre(rl("105mm")),
                AmmoCatalogue.variant(rl("105mm/he")))).kind());
        assertEquals(0.8f, AmmoCatalogue.variant(rl("122mm_d25/ap")).fillerKg());
        assertEquals(0.15f, AmmoCatalogue.variant(rl("76x385r/ap")).fillerKg());
        Threat ball = Rounds.threat(AmmoPresets.preset(AmmoCatalogue.calibre(rl("7.62x51")),
                AmmoCatalogue.variant(rl("7.62x51/ball"))));
        assertEquals(ThreatKind.SMALL_ARMS, ball.kind());
        assertEquals(2.0f, AmmoCatalogue.variant(rl("7.62x51/ball")).penExponent());
    }
}

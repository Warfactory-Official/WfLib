package com.wf.wflib.explosion.api;

import com.wf.wflib.ammo.AmmoCatalogue;
import com.wf.wflib.ammo.api.AmmoPresets;
import com.wf.wflib.ammo.api.AmmoVariant;
import com.wf.wflib.damage.api.ThreatKind;
import com.wf.wflib.round.api.KineticPreset;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlastPayloadTest {

    @BeforeAll
    static void define() {
        AmmoCatalogue.define();
    }

    private static KineticPreset preset(AmmoVariant v) {
        return AmmoPresets.preset(AmmoCatalogue.calibre(v.calibre()), v);
    }

    @Test
    void sizeOnlyBlastIsOneComponent() {
        BlastPayload p = BlastPayload.ofSize(5f);
        assertEquals(BlastPayload.KG_PER_SIZE_CUBED * 125f, p.blast().tntKg(), 1e-6);
        assertNull(p.fragments());
        assertNull(p.jet());
        assertNull(p.efp());
        assertNull(p.hesh());
        assertNull(p.thermal());
    }

    @Test
    void chargeScalesWithTheCubeOfSize() {
        assertEquals(8f, BlastPayload.kgForSize(10f) / BlastPayload.kgForSize(5f), 1e-5);
    }

    @Test
    void invalidComponentsFailFast() {
        assertThrows(IllegalArgumentException.class, () -> new BlastPayload(null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new BlastPayload.Blast(0f));
        assertThrows(IllegalArgumentException.class, () -> new BlastPayload.Fragments(0, 1f, 1f));
        assertThrows(IllegalArgumentException.class, () -> new BlastPayload.Jet(100f, 90f));
        assertThrows(IllegalArgumentException.class, () -> new BlastPayload.Efp(100f, 0f));
        assertThrows(IllegalArgumentException.class, () -> new BlastPayload.Hesh(-1f));
        assertThrows(IllegalArgumentException.class, () -> new BlastPayload.Thermal(0f));
    }

    @Test
    void resolveOverridesChargeAndJetPenOnly() {
        BlastPayload declared = BlastPayload.ofSize(6f).withJet(300f, 22f).withThermal(3f);
        BlastPayload p = BlastPayload.resolve(declared, 2.5f, 700f);
        assertEquals(BlastPayload.kgForSize(2.5f), p.blast().tntKg(), 1e-6);
        assertEquals(700f, p.jet().penMm(), 0f);
        assertEquals(22f, p.jet().halfAngleDeg(), 0f);
        assertEquals(3f, p.thermal().radius(), 0f);
        assertSame(declared, BlastPayload.resolve(declared, 0f, 0f));
        BlastPayload noJet = BlastPayload.ofSize(4f);
        assertNull(BlastPayload.resolve(noJet, 0f, 500f).jet());
        assertNull(BlastPayload.resolve(null, 0f, 500f));
        assertEquals(BlastPayload.kgForSize(3f), BlastPayload.resolve(null, 3f, 0f).blast().tntKg(), 1e-6);
    }

    @Test
    void everyExplosiveAmmoVariantCarriesAPayload() {
        int explosive = 0;
        for (AmmoVariant v : AmmoCatalogue.variants()) {
            if (v.explosive() == null) {
                continue;
            }
            explosive++;
            BlastPayload p = preset(v).payload();
            assertNotNull(p, v.id() + " payload");
            if (v.explosive().warhead().getPath().equals("shaped_charge")) {
                assertNotNull(p.jet(), v.id() + " jet");
            }
            if (v.threat() == ThreatKind.HESH) {
                assertNotNull(p.hesh(), v.id() + " hesh");
                assertEquals(AmmoCatalogue.calibre(v.calibre()).bulletMm(), p.hesh().calibreMm(), 1e-3);
            }
            if (v.explosive().warhead().getPath().equals("fragmentation")) {
                assertNotNull(p.fragments(), v.id() + " fragments");
            }
        }
        assertTrue(explosive > 30, "explosive variants " + explosive);
    }

    @Test
    void aShapedChargeRoundCarriesItsOwnPenAsTheJet() {
        for (AmmoVariant v : AmmoCatalogue.variants()) {
            if (v.explosive() != null && v.explosive().warhead().getPath().equals("shaped_charge")
                    && v.armorPenMm() > 0) {
                BlastPayload p = preset(v).payload();
                if (p.jet() != null) {
                    assertEquals(v.armorPenMm(), p.jet().penMm(), 1e-3, v.id().toString());
                }
            }
        }
    }

    @Test
    void anAphePayloadIsItsFiller() {
        int seen = 0;
        for (AmmoVariant v : AmmoCatalogue.variants()) {
            if (v.fillerKg() > 0 && v.explosive() == null) {
                seen++;
                assertEquals(v.fillerKg(), preset(v).payload().blast().tntKg(), 0f, v.id().toString());
            }
        }
        assertTrue(seen >= 3, "APHE variants " + seen);
    }

    @Test
    void explicitDeclarationsWinOverTheDerivation() {
        AmmoVariant l31 = AmmoCatalogue.variant(ResourceLocation.fromNamespaceAndPath("wflib", "120mm_l30/he"));
        BlastPayload p = preset(l31).payload();
        assertEquals(3f, p.blast().tntKg(), 0f);
        assertEquals(120f, p.hesh().calibreMm(), 0f);
        AmmoVariant m795 = AmmoCatalogue.variant(ResourceLocation.fromNamespaceAndPath("wflib", "155mm/he"));
        assertEquals(1800, preset(m795).payload().fragments().count());
    }
}

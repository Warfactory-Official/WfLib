package com.wf.wflib.anomaly;

import com.wf.wflib.anomaly.site.AnomalyMix;
import com.wf.wflib.anomaly.site.AnomalyMix.Kind;
import com.wf.wflib.anomaly.site.AnomalyMix.Spot;
import com.wf.wflib.anomaly.site.SitePlan;
import com.wf.wflib.anomaly.site.SiteStyle;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SitePlanTest {
    private static final int ROLLS = 3000;

    @Test
    void tiersGetHarderBiggerAndRicher() {
        RandomSource r = RandomSource.create(7L);
        double[] loot = new double[SitePlan.TIERS + 1];
        for (int tier = 1; tier <= SitePlan.TIERS; tier++) {
            for (int i = 0; i < ROLLS; i++) {
                SitePlan p = SitePlan.roll(r, tier, SiteStyle.PLAINS, null, null);
                assertTrue(p.motif().minTier <= tier, p.motif() + " opened at tier " + tier);
                assertTrue(p.overlay() == null || p.overlay().minTier <= tier);
                assertTrue(p.radius() <= SitePlan.MAX_RADIUS);
                assertFalse(p.anomalies().isEmpty());
                assertEquals(tier, p.anomalies().get(0).rank, "tier " + tier + " headline " + p.anomalies().get(0));
                int fields = 0;
                for (Kind k : p.anomalies()) {
                    assertTrue(k.rank <= tier, k + " in tier " + tier);
                    assertTrue(k.spot != Spot.CEILING || p.motif().ceilings, k + " in a " + p.motif());
                    fields += k.spot == Spot.FIELD ? 1 : 0;
                }
                assertTrue(fields <= 1, "two fields in one site");
                loot[tier] += p.artefacts().size();
            }
            loot[tier] /= ROLLS;
            assertEquals(AnomalyMix.expectedArtefacts(tier), loot[tier], 0.05D, "tier " + tier + " artefacts");
        }
    }

    @Test
    void theBadlandsRunToRavinesNotBogs() {
        RandomSource r = RandomSource.create(3L);
        int ravines = 0;
        int bogs = 0;
        for (int i = 0; i < ROLLS; i++) {
            SitePlan.Motif m = SitePlan.roll(r, 2, SiteStyle.BADLANDS, null, null).motif();
            ravines += m == SitePlan.Motif.RAVINE ? 1 : 0;
            bogs += m == SitePlan.Motif.BOG ? 1 : 0;
        }
        assertTrue(ravines > bogs * 10, ravines + " ravines, " + bogs + " bogs");
    }
}

package com.wf.wflib.anomaly;

import com.wf.wflib.anomaly.cluster.ClusterPlan;
import com.wf.wflib.anomaly.cluster.ClusterPlan.Design;
import com.wf.wflib.anomaly.cluster.ClusterPlan.Kind;
import com.wf.wflib.anomaly.cluster.ClusterPlan.Spot;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterPlanTest {
    private static final int ROLLS = 4000;

    @Test
    void tiersGetHarderBiggerAndRicher() {
        RandomSource r = RandomSource.create(7L);
        double[] meanLoot = new double[ClusterPlan.TIERS + 1];
        for (int tier = 1; tier <= ClusterPlan.TIERS; tier++) {
            int maxRadius = 0;
            for (int i = 0; i < ROLLS; i++) {
                ClusterPlan p = ClusterPlan.roll(r, tier, null);
                assertTrue(p.design().minTier <= tier, p.design() + " opened at tier " + tier);
                assertTrue(p.radius() <= ClusterPlan.MAX_RADIUS);
                maxRadius = Math.max(maxRadius, p.radius());
                assertFalse(p.anomalies().isEmpty());
                // The headline is the tier's own rank, and nothing is past it.
                assertEquals(tier, p.anomalies().get(0).rank, "tier " + tier + " headline " + p.anomalies().get(0));
                int fields = 0;
                for (Kind k : p.anomalies()) {
                    assertTrue(k.rank <= tier, k + " in tier " + tier);
                    assertTrue(k.spot != Spot.CEILING || p.design().ceilings, k + " in a " + p.design());
                    fields += k.spot == Spot.FIELD ? 1 : 0;
                }
                assertTrue(fields <= 1, "two fields in one cluster");
                for (Kind k : Kind.values()) {
                    long n = p.anomalies().stream().filter(q -> q == k).count();
                    assertTrue(n <= k.cap, k + " x" + n);
                }
                meanLoot[tier] += p.artefacts().size();
            }
            meanLoot[tier] /= ROLLS;
            assertEquals(ClusterPlan.expectedArtefacts(tier), meanLoot[tier], 0.05D, "tier " + tier + " artefacts");
            if (tier > 1) {
                assertTrue(meanLoot[tier] > meanLoot[tier - 1]);
            }
        }
    }

    @Test
    void aDesignLeansToItsElement() {
        RandomSource r = RandomSource.create(11L);
        int fire = 0;
        int total = 0;
        for (int i = 0; i < ROLLS; i++) {
            for (Kind k : ClusterPlan.roll(r, 2, Design.FISSURE).anomalies()) {
                fire += k.element == ClusterPlan.Element.FIRE ? 1 : 0;
                total++;
            }
        }
        // Fire is 2 of the 12 kinds open at tier 2 (neither of its rank), but weighted x6 in a fissure.
        assertTrue(fire > total * 0.3D, "fire share " + (double) fire / total);
    }

    @Test
    void sameSeedSamePlan() {
        assertEquals(ClusterPlan.roll(RandomSource.create(5L), 3, null), ClusterPlan.roll(RandomSource.create(5L), 3, null));
    }
}

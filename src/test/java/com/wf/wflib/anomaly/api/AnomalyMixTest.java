package com.wf.wflib.anomaly.api;

import com.wf.wflib.artifact.api.Element;
import com.wf.wflib.anomaly.api.AnomalyMix.Kind;
import com.wf.wflib.anomaly.api.AnomalyMix.Loot;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnomalyMixTest {
    @Test
    void lootNeverBelowItsTierAndEveryTierReachesAll() {
        RandomSource r = RandomSource.create(3L);
        for (int tier = 1; tier <= AnomalyMix.TIERS; tier++) {
            Set<Loot> seen = EnumSet.noneOf(Loot.class);
            for (int i = 0; i < 20000; i++) {
                Loot l = AnomalyMix.pickLoot(r, tier, List.of(Kind.ELECTRO));
                assertTrue(l.minTier <= tier, l + " at tier " + tier);
                seen.add(l);
            }
            for (Loot l : Loot.values()) {
                assertEquals(l.minTier <= tier, seen.contains(l), l + " at tier " + tier);
            }
        }
    }

    @Test
    void lootLeansToTheSitesElements() {
        RandomSource r = RandomSource.create(5L);
        int electric = 0;
        int n = 20000;
        for (int i = 0; i < n; i++) {
            electric += AnomalyMix.pickLoot(r, 4, List.of(Kind.ELECTRO, Kind.NET, Kind.FLASH)).element
                    == Element.ELECTRIC ? 1 : 0;
        }
        long kinds = java.util.Arrays.stream(Loot.values()).filter(l -> l.element == Element.ELECTRIC).count();
        assertTrue(electric > n * (double) kinds / Loot.values().length * 1.5D, "electric " + electric);
    }

    @Test
    void uniquesAreRareTierFourFinds() {
        RandomSource r = RandomSource.create(9L);
        int unique = 0;
        int n = 50000;
        for (int i = 0; i < n; i++) {
            unique += AnomalyMix.pickLoot(r, 4, List.of(Kind.GRABBER)).share == AnomalyMix.UNIQUE_SHARE ? 1 : 0;
        }
        assertTrue(unique > 0 && unique < n / 50, "uniques " + unique);
        for (com.wf.wflib.artifact.api.Artifact a : com.wf.wflib.artifact.api.Artifact.UNIQUE) {
            assertEquals(4, a.minTier());
        }
    }

    @Test
    void everyArtifactIsLoot() {
        assertEquals(com.wf.wflib.artifact.api.Artifact.ALL.size(), Loot.values().length);
    }
}

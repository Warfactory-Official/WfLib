package com.wf.wflib.artifact;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ArtifactsTest {
    @Test
    void immunitiesStackOneAfterAnother() {
        float crystal = Artifact.CRYSTAL.immunity(Artifact.Hit.BURN);
        float itcher = Artifact.ITCHER.immunity(Artifact.Hit.BURN);
        assertEquals((1 - crystal) * (1 - itcher), Artifacts.damageScale(List.of(Artifact.CRYSTAL, Artifact.ITCHER),
                Artifact.Hit.BURN), 1e-6F);
        assertEquals(1.0F, Artifacts.damageScale(List.of(Artifact.CRYSTAL), null));
        assertEquals(1.0F - Artifact.CRYSTAL.immunity(Artifact.Hit.CHEMICAL),
                Artifacts.damageScale(List.of(Artifact.CRYSTAL), Artifact.Hit.CHEMICAL), 1e-6F);
    }

    @Test
    void sameKindStacksMultiplicatively() {
        Artifact sponge = Artifact.SPONGE;
        List<Artifact> worn = List.of(sponge, sponge, sponge, sponge, sponge, sponge, sponge, sponge);
        float s = Artifacts.damageScale(worn, Artifact.Hit.STRIKE);
        assertEquals((float) Math.pow(1 - sponge.immunity(Artifact.Hit.STRIKE), 8), s, 1e-6F);
    }

    @Test
    void psyStacksAndWorsens() {
        float moon = Artifact.MOONLIGHT.immunity(Artifact.Hit.PSY);
        float ring = Artifact.RING.immunity(Artifact.Hit.PSY);
        assertEquals(1 - (1 - moon) * (1 - ring),
                1 - Artifacts.damageScale(List.of(Artifact.MOONLIGHT, Artifact.RING), Artifact.Hit.PSY), 1e-6F);
        assertEquals(ring, 1 - Artifacts.damageScale(List.of(Artifact.RING), Artifact.Hit.PSY), 1e-6F);
    }

    @Test
    void shieldSoaksUpToItsBudget() {
        assertArrayEquals(new double[] {0.0D, 3.0D}, Artifacts.shield(2.0D, 5.0D), 1e-9);
        assertArrayEquals(new double[] {1.0D, 0.0D}, Artifacts.shield(6.0D, 5.0D), 1e-9);
        assertArrayEquals(new double[] {6.0D, 0.0D}, Artifacts.shield(4.0D, -2.0D), 1e-9);
    }

    @Test
    void shieldRateIsGammaUnitsPerGameSecond() {
        assertEquals(0.00043F * Artifact.TIME_FACTOR * Artifact.RAD_PER_UNIT,
                Artifacts.radShieldPerSecond(List.of(Artifact.SOUL)), 1e-4F);
        assertEquals(0.0F, Artifacts.radShieldPerSecond(List.of(Artifact.CRYSTAL)));
    }

    @Test
    void restoresRunOnGameTime() {
        assertEquals(0.0003F * 6.0F * 20.0F, Artifact.SOUL.healthPerSecond(20.0F), 1e-6F);
        assertEquals(0.00408F * 6.0F, Artifact.GLASS_BEADS.bleedingPerSecond(), 1e-6F);
        assertEquals(0.00066D * 6.0D / 20.0D, Artifact.BATTERY.staminaPerTick(), 1e-9D);
    }

    @Test
    void idsAreUnique() {
        assertEquals(Artifact.ALL.size(), Artifact.ALL.stream().map(Artifact::id).distinct().count());
    }
}

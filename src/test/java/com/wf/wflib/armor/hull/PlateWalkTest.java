package com.wf.wflib.armor.hull;

import com.wf.wflib.armor.api.hull.PlateMaterial;
import com.wf.wflib.armor.api.hull.PlateMaterials;
import com.wf.wflib.armor.api.hull.PlateWalk;
import com.wf.wflib.armor.api.hull.WalkResult;
import com.wf.wflib.damage.api.Threat;
import com.wf.wflib.damage.api.ThreatKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One row per rule of the plan's layer table. */
class PlateWalkTest {
    private static final float EPS = 1e-3F;
    private static final PlateMaterial RHA = PlateMaterials.get("rha");
    private static final PlateMaterial NERA = PlateMaterials.get("nera");
    private static final PlateMaterial AL = PlateMaterials.get("aluminium");

    private static Threat threat(ThreatKind kind, float caliber, float pen, float slopeExp) {
        return new Threat(kind, caliber, pen, 1f, 70, slopeExp, 0, 0);
    }

    private static PlateWalk walk(ThreatKind kind, float pen) {
        return new PlateWalk(threat(kind, 30, pen, 1.0f));
    }

    @Test
    void kineticSolidLayerCostsMmTimesKe() {
        WalkResult r = walk(ThreatKind.KINETIC, 100).solid(50, RHA, 1, 1).result();
        assertTrue(r.through());
        assertEquals(50, r.residualPen(), EPS);
        assertEquals(50, r.needed(), EPS);
        assertEquals(1, r.defeated());
        assertEquals(-1, r.stoppedAt());
        assertEquals(14, walk(ThreatKind.KINETIC, 100).solid(40, AL, 1, 1).result().needed(), EPS);
    }

    @Test
    void kineticSlopeUsesTheThreatExponent() {
        float cos = 0.5F;
        assertEquals(100, new PlateWalk(threat(ThreatKind.KINETIC, 30, 500, 1.0f)).solid(50, RHA, 1, cos).result()
                .needed(), EPS);
        assertEquals(50 / Math.pow(0.5, 1.3), new PlateWalk(threat(ThreatKind.KINETIC, 30, 500, 1.3f))
                .solid(50, RHA, 1, cos).result().needed(), 0.01);
        assertEquals(50 / 0.05, walk(ThreatKind.KINETIC, 9999).solid(50, RHA, 1, 0).result().needed(), 0.1,
                "cos floored at 0.05");
    }

    @Test
    void thicknessScaleThinsTheLayer() {
        assertEquals(30, walk(ThreatKind.KINETIC, 100).solid(50, RHA, 0.6F, 1).result().needed(), EPS);
    }

    @Test
    void shapedChargeReadsCeAndObliquity() {
        assertEquals(200, walk(ThreatKind.HEAT, 500).solid(100, NERA, 1, 1).result().needed(), EPS);
        assertEquals(110, walk(ThreatKind.KINETIC, 500).solid(100, NERA, 1, 1).result().needed(), EPS);
        assertEquals(400, walk(ThreatKind.HEAT, 500).solid(100, NERA, 1, 0.5F).result().needed(), EPS);
        assertEquals(400, walk(ThreatKind.TANDEM_HEAT, 500).solid(100, NERA, 1, 0.5F).result().needed(), EPS);
    }

    @Test
    void layerThatIsNotDefeatedStopsTheWalk() {
        PlateWalk w = walk(ThreatKind.KINETIC, 100);
        w.solid(60, RHA, 1, 1).solid(60, RHA, 1, 1).solid(5, RHA, 1, 1);
        assertFalse(w.alive());
        WalkResult r = w.result();
        assertFalse(r.through());
        assertEquals(0, r.residualPen());
        assertEquals(1, r.defeated());
        assertEquals(2, r.layers(), "the third was never entered");
        assertEquals(1, r.stoppedAt());
        assertEquals(120, r.needed(), EPS, "needed counts the stopping layer, not those behind it");
        assertEquals(40, r.residualAfter(0), EPS);
        assertEquals(0, r.residualAfter(1));
    }

    @Test
    void budgetEqualToCostDoesNotGoThrough() {
        WalkResult r = walk(ThreatKind.KINETIC, 50).solid(50, RHA, 1, 1).result();
        assertFalse(r.through());
        assertEquals(0, r.defeated());
    }

    @Test
    void layersSumAndRecordPerLayerFigures() {
        PlateMaterials.register(new PlateMaterial("walk_test", 1f, 1f, 0.5f, 0.25f));
        PlateWalk w = walk(ThreatKind.KINETIC, 60);
        w.solid(20, RHA, 1, 1).nextCrossing().solid(30, PlateMaterials.get("walk_test"), 1, 1);
        WalkResult r = w.result();
        assertTrue(r.through());
        assertEquals(10, r.residualPen(), EPS);
        assertEquals(2, r.defeated());
        assertEquals(40, r.residualAfter(0), EPS);
        assertEquals(10, r.residualAfter(1), EPS);
        assertEquals(0, r.crossing(0));
        assertEquals(1, r.crossing(1));
        assertEquals(1.0F, r.spall(0));
        assertEquals(0.25F, r.spall(1));
        assertEquals(50, r.needed(), EPS);
    }

    @Test
    void manyLayersGrowTheRecords() {
        PlateWalk w = walk(ThreatKind.KINETIC, 1000);
        for (int i = 0; i < 10; i++) {
            w.solid(10, RHA, 1, 1);
        }
        WalkResult r = w.result();
        assertEquals(10, r.layers());
        assertEquals(900, r.residualPen(), EPS);
        assertEquals(990, r.residualAfter(0), EPS);
    }

    @Test
    void gapCostsAShapedChargeTheStandoffLoss() {
        float f = 1 / (1 + PlateWalk.STANDOFF_LOSS * 0.6F);
        WalkResult r = walk(ThreatKind.HEAT, 100).solid(10, RHA, 1, 1).gap(0.6F).solid(10, RHA, 1, 1).result();
        assertEquals((100 - 10) * f - 10, r.residualPen(), EPS);
        float needed = (10 * f + 10) / f;
        assertEquals(needed, r.needed(), EPS);
        assertTrue(walk(ThreatKind.HEAT, needed + 0.01F).solid(10, RHA, 1, 1).gap(0.6F).solid(10, RHA, 1, 1)
                .result().through());
        assertFalse(walk(ThreatKind.HEAT, needed - 0.01F).solid(10, RHA, 1, 1).gap(0.6F).solid(10, RHA, 1, 1)
                .result().through());
    }

    @Test
    void tandemIgnoresTheGap() {
        WalkResult r = walk(ThreatKind.TANDEM_HEAT, 100).solid(10, RHA, 1, 1).gap(0.6F).solid(10, RHA, 1, 1).result();
        assertEquals(80, r.residualPen(), EPS);
        assertEquals(20, r.needed(), EPS);
    }

    @Test
    void largeKineticAndEfpIgnoreTheGap() {
        for (ThreatKind kind : new ThreatKind[] {ThreatKind.KINETIC, ThreatKind.EFP, ThreatKind.HE}) {
            WalkResult r = walk(kind, 100).solid(10, RHA, 1, 1).gap(0.6F).solid(10, RHA, 1, 1).result();
            assertEquals(80, r.residualPen(), EPS, kind.name());
        }
    }

    @Test
    void smallArmsYawAfterTheFirstSkin() {
        Threat ball = threat(ThreatKind.SMALL_ARMS, 7.62F, 100, 1.0f);
        float yaw = PlateWalk.yaw(7.62F, 0.6F);
        assertTrue(yaw < 0.6F && yaw >= 0.5F, "yaw " + yaw);
        WalkResult r = new PlateWalk(ball).solid(8, RHA, 1, 1).gap(0.6F).solid(10, RHA, 1, 1).result();
        assertEquals((100 - 8) * yaw - 10, r.residualPen(), EPS);
        assertEquals(100 - 10, new PlateWalk(ball).gap(0.6F).solid(10, RHA, 1, 1).result().residualPen(), EPS,
                "nothing disturbed it yet");
        assertTrue(PlateWalk.yaw(7.62F, 0.05F) > PlateWalk.yaw(7.62F, 0.6F));
        assertTrue(PlateWalk.yaw(7.62F, 0.6F) > PlateWalk.yaw(7.62F, 60F) - 1e-6F);
        assertTrue(PlateWalk.yaw(20F, 0.6F) > PlateWalk.yaw(5.56F, 0.6F), "a bigger bullet yaws less");
    }

    @Test
    void heshTransmitsThroughSolidsAndStopsAtAGap() {
        PlateMaterials.register(new PlateMaterial("walk_half", 1f, 1f, 0.5f, 1f));
        ThreatKind k = ThreatKind.HESH;
        assertEquals(1.0F, walk(k, 100).solid(10, RHA, 1, 1).result().transmit());
        assertEquals(0.5F, walk(k, 100).solid(10, PlateMaterials.get("walk_half"), 1, 1).result().transmit());
        assertEquals(0.25F, walk(k, 100).solid(10, PlateMaterials.get("walk_half"), 1, 1)
                .solid(10, PlateMaterials.get("walk_half"), 1, 1).result().transmit());
        assertEquals(0, walk(k, 100).solid(10, NERA, 1, 1).result().transmit(), "composite stops it");
        assertEquals(0, walk(k, 100).solid(10, RHA, 1, 1).gap(0.2F).solid(10, RHA, 1, 1).result().transmit());
        assertEquals(1.0F, WalkResult.NONE.transmit());
    }

    @Test
    void spallLinerCostsNothing() {
        WalkResult r = walk(ThreatKind.KINETIC, 50).solid(40, RHA, 1, 1).solid(5, PlateMaterials.get("spall_liner"), 1, 1)
                .result();
        assertTrue(r.through());
        assertEquals(10, r.residualPen(), EPS);
        assertEquals(0.3F, r.spall(1), EPS);
    }

    @Test
    void markKeepsWhatTheEntryPlateLeft() {
        WalkResult r = walk(ThreatKind.KINETIC, 100).solid(30, RHA, 1, 1).mark().solid(80, RHA, 1, 1).result();
        assertEquals(70, r.interiorPen(), EPS);
        assertEquals(30, r.interiorNeeded(), EPS);
        assertEquals(0, r.residualPen());
        assertEquals(110, r.needed(), EPS);
        WalkResult unmarked = walk(ThreatKind.KINETIC, 100).solid(30, RHA, 1, 1).result();
        assertEquals(unmarked.residualPen(), unmarked.interiorPen());
        assertEquals(unmarked.needed(), unmarked.interiorNeeded());
        assertEquals(0, WalkResult.NONE.interiorPen());
    }

    @Test
    void emptyWalkKeepsTheBudget() {
        WalkResult r = walk(ThreatKind.KINETIC, 77).result();
        assertTrue(r.through());
        assertEquals(77, r.residualPen());
        assertEquals(0, r.needed());
    }

    @Test
    void throughIffEntryPenExceedsNeeded() {
        for (ThreatKind kind : new ThreatKind[] {ThreatKind.KINETIC, ThreatKind.HEAT, ThreatKind.SMALL_ARMS}) {
            WalkResult probe = walk(kind, 1000).solid(12, RHA, 1, 0.8F).gap(0.3F).solid(20, NERA, 1, 0.7F)
                    .gap(0.1F).solid(5, RHA, 1, 1).result();
            float needed = probe.needed();
            for (float d : new float[] {-0.5F, 0.5F}) {
                WalkResult r = walk(kind, needed + d).solid(12, RHA, 1, 0.8F).gap(0.3F).solid(20, NERA, 1, 0.7F)
                        .gap(0.1F).solid(5, RHA, 1, 1).result();
                assertEquals(d > 0, r.through(), kind + " at needed " + needed + " " + d);
            }
        }
    }

    private static final com.wf.wflib.armor.api.hull.EraType BRICK = new com.wf.wflib.armor.api.hull.EraType("t:brick",
            0.5f, 0.2f, 0.0f, 20, 40, 1000, 0.1f, 0, 0, 0.1f, null, null);

    @Test
    void cutScalesTheBudgetAndWhatTheLayersNeed() {
        WalkResult r = walk(ThreatKind.KINETIC, 100).cut(0.5f).solid(30, RHA, 1, 1).result();
        assertTrue(r.through());
        assertEquals(20, r.residualPen(), EPS, "50 left, 30 spent");
        assertEquals(60, r.needed(), EPS, "entry pen that just defeats the plate behind a half cut");
        assertFalse(walk(ThreatKind.KINETIC, 100).cut(0.5f).solid(50, RHA, 1, 1).result().through());
    }

    @Test
    void aLiveBrickItSetsOffCutsAndIsListedNeverSpentByTheWalk() {
        PlateWalk w = new PlateWalk(threat(ThreatKind.HEAT, 80, 200, 1.0f));
        WalkResult r = w.era("p", 3, BRICK, true, 0.5f, 0.5f).solid(20, RHA, 1, 1).result();
        assertEquals(1, r.eraHits().size());
        assertEquals(3, r.eraHits().get(0).cell());
        assertEquals("p", r.eraHits().get(0).plate());
        float keep = 1 - 0.5f * (float) (Math.sqrt(0.75) / Math.sin(Math.toRadians(60)));
        assertEquals(200 * keep - 20, r.residualPen(), 0.05);
    }

    @Test
    void aSpentBrickAThreatBelowItsInitiationAndSmallArmsLeaveTheBudgetAlone() {
        assertEquals(180, new PlateWalk(threat(ThreatKind.HEAT, 80, 200, 1.0f)).era("p", 0, BRICK, false, 0.5f, 0.5f)
                .solid(20, RHA, 1, 1).result().residualPen(), EPS);
        WalkResult kinetic = new PlateWalk(threat(ThreatKind.KINETIC, 12, 200, 1.0f))
                .era("p", 0, BRICK, true, 0.5f, 0.5f).solid(20, RHA, 1, 1).result();
        assertEquals(180, kinetic.residualPen(), EPS, "12 mm < 20 mm");
        assertTrue(kinetic.eraHits().isEmpty());
        assertTrue(new PlateWalk(threat(ThreatKind.SMALL_ARMS, 12.7f, 200, 2.0f)).era("p", 0, BRICK, true, 0.5f, 0.5f)
                .result().eraHits().isEmpty());
    }

    @Test
    void anHeShellSetsABrickOffWithoutAnyCut() {
        PlateWalk w = new PlateWalk(threat(ThreatKind.HE, 76, 100, 1.0f));
        WalkResult r = w.era("p", 1, BRICK, true, 0.5f, 0.5f).result();
        assertEquals(1, r.eraHits().size());
        assertEquals(100, r.residualPen(), EPS);
    }

    @Test
    void tandemCutAppliesOnlyWhereTheTypeDeclaresIt() {
        com.wf.wflib.armor.api.hull.EraType relikt = new com.wf.wflib.armor.api.hull.EraType("t:relikt", 0.5f, 0.2f,
                0.4f, 20, 40, 1000, 0.1f, 0, 0, 0.1f, null, null);
        float full = 1.0f;
        assertEquals(200, new PlateWalk(threat(ThreatKind.TANDEM_HEAT, 80, 200, 1.0f)).era("p", 0, BRICK, true, full, 0.5f)
                .result().residualPen(), EPS, "Kontakt class: precursor spends the brick, the jet passes");
        assertTrue(new PlateWalk(threat(ThreatKind.TANDEM_HEAT, 80, 200, 1.0f)).era("p", 0, relikt, true, full, 0.5f)
                .result().residualPen() < 200);
    }

    @Test
    void aThinBackPlateTakesTheFlyerInsteadOfTheBrickFragments() {
        com.wf.wflib.armor.api.hull.EraType type = new com.wf.wflib.armor.api.hull.EraType("t:flyer", 0.5f, 0.2f, 0, 20,
                40, 1000, 0.1f, 30, 0, 0.1f, new com.wf.wflib.explosion.api.BlastPayload.Fragments(6, 3, 900),
                new com.wf.wflib.explosion.api.BlastPayload.Fragments(1, 150, 1100));
        assertEquals(150, type.payload(10).fragments().massG(), EPS);
        assertEquals(3, type.payload(60).fragments().massG(), EPS);
        assertEquals(0.1f, type.payload(60).blast().tntKg(), EPS);
    }
}

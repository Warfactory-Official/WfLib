package com.wf.wflib.anomaly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnomalyPathTest {
    private static List<Vec3> square() {
        return List.of(new Vec3(5, 1, 0), new Vec3(0, 1, 5), new Vec3(-5, 1, 0), new Vec3(0, 1, -5));
    }

    @Test
    void curvePassesThroughItsPointsAndResamplesEvenly() {
        List<Vec3> curve = AnomalyPath.catmullRom(square(), 24);
        for (int k = 0; k < 4; k++) {
            assertEquals(0.0D, curve.get(k * 24).distanceTo(square().get(k)), 1e-9);
        }
        float[] xyz = AnomalyPath.resample(curve, AnomalyPath.COMET_SPEED);
        int n = xyz.length / 3;
        for (int k = 0; k < n; k++) {
            int j = (k + 1) % n;
            double step = new Vec3(xyz[k * 3], xyz[k * 3 + 1], xyz[k * 3 + 2])
                    .distanceTo(new Vec3(xyz[j * 3], xyz[j * 3 + 1], xyz[j * 3 + 2]));
            assertEquals(AnomalyPath.COMET_SPEED, step, 0.03D, "a tick's travel each, the seam too");
        }
    }

    @Test
    void positionLoopsAndInterpolatesAndSurvivesSaving() {
        float[] xyz = {0, 0, 0, 2, 0, 0, 2, 0, 2};
        AnomalyPath path = new AnomalyPath(xyz);
        assertEquals(new Vec3(1, 0, 0), path.at(0.5D));
        assertEquals(path.at(1.25D), path.at(1.25D + 3 * 7));
        assertEquals(path.at(2.5D), path.at(-0.5D));
        CompoundTag tag = new CompoundTag();
        path.save(tag);
        AnomalyPath back = AnomalyPath.load(tag);
        assertNotNull(back);
        assertEquals(path.at(1.7D), back.at(1.7D));
        assertNull(AnomalyPath.load(new CompoundTag()), "no path saved: none");
    }

    @Test
    void smoothingGlidesOverASpikeWithoutDiggingIn() {
        float[] xyz = new float[60];
        for (int k = 0; k < 20; k++) {
            xyz[k * 3 + 1] = k == 10 ? 3.0F : 1.0F;
        }
        float[] raw = xyz.clone();
        AnomalyPath.smoothHeights(xyz, 4, 0.5D);
        assertTrue(xyz[10 * 3 + 1] < 3.0F, "the crest is rounded off");
        for (int k = 0; k < 20; k++) {
            assertTrue(xyz[k * 3 + 1] >= raw[k * 3 + 1] - 0.5F - 1e-6F, "never more than the sag under the raw");
        }
    }

    @Test
    void roundsAreCaughtWhereTheyEnterAndOnlyComingIn() {
        Vec3 c = Vec3.ZERO;
        Vec3 at = AnomalyBlockEntity.entry(new Vec3(-20, 0, 0), new Vec3(30, 0, 0), c, 6.0D);
        assertNotNull(at);
        assertEquals(-6.0D, at.x, 1e-9);
        assertNull(AnomalyBlockEntity.entry(new Vec3(-20, 0, 0), new Vec3(10, 0, 0), c, 6.0D), "not there yet");
        assertNull(AnomalyBlockEntity.entry(new Vec3(-20, 0, 0), new Vec3(-30, 0, 0), c, 6.0D), "going away");
        assertNull(AnomalyBlockEntity.entry(new Vec3(-2, 0, 0), new Vec3(30, 0, 0), c, 6.0D), "fired from inside");
        assertNull(AnomalyBlockEntity.entry(new Vec3(-20, 9, 0), new Vec3(30, 0, 0), c, 6.0D), "passes over");
    }
}

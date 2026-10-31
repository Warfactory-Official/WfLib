package com.wf.wflib.armor.hull;

import com.wf.wflib.armor.api.hull.HullWear;
import net.minecraft.nbt.CompoundTag;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HullWearTest {
    @Test
    void blastSpreadsToSameFaceNeighbours() {
        HullWear w = new HullWear();
        int centre = HullWear.index(HullWear.Face.TOP, 1, 1);
        float added = w.apply(centre, 400, HullWear.Kind.BLAST, 100);
        assertEquals(400 * 0.2F / (100 * HullWear.POINTS_PER_MM), added, 1e-6F);
        for (int[] n : new int[][] {{0, 1}, {2, 1}, {1, 0}, {1, 2}}) {
            assertEquals(added * 0.5F, w.wear(HullWear.index(HullWear.Face.TOP, n[0], n[1])), 1e-6F);
        }
        assertEquals(0, w.wear(HullWear.index(HullWear.Face.TOP, 0, 0)), "diagonal untouched");
        assertEquals(0, w.wear(HullWear.index(HullWear.Face.FRONT, 1, 1)), "other face untouched");
        assertTrue(w.dirty());
    }

    @Test
    void syncQuantisesAndNbtIsExact() {
        HullWear w = new HullWear();
        w.apply(7, 123, HullWear.Kind.THERMAL, 37);
        HullWear synced = new HullWear();
        synced.fromSync(w.toSync());
        assertFalse(w.dirty());
        assertEquals(w.wear(7), synced.wear(7), 0.5F / 255);
        CompoundTag tag = new CompoundTag();
        w.save(tag);
        assertTrue(tag.contains("ArmorWear"));
        HullWear loaded = new HullWear();
        loaded.load(tag);
        for (int r = 0; r < HullWear.COUNT; r++) {
            assertEquals(w.wear(r), loaded.wear(r));
        }
        CompoundTag empty = new CompoundTag();
        new HullWear().save(empty);
        assertFalse(empty.contains("ArmorWear"), "unworn => no tag");
    }

    @Test
    void regionByNormalElseNearestSide() {
        FakeHull hull = new FakeHull();
        assertEquals(-1, HullWear.region(null, hull.world(0, 0, 0), null));
        int byPosition = HullWear.region(hull.main, hull.world(1.5F, 1, 2.9F), null);
        assertEquals(HullWear.Face.FRONT, HullWear.face(byPosition), "z 2.9/3 > x 1.5/1.6");
        Vector3f left = hull.rotation.transform(new Vector3f(1, 0, 0));
        assertEquals(HullWear.Face.LEFT, HullWear.face(HullWear.region(hull.main, hull.world(1.5F, 1, 2.9F), left)));
        assertEquals(HullWear.index(HullWear.Face.FRONT, 2, 1), byPosition);
        assertEquals(1, new HullWear().thicknessScale(-1, 0.6F));
    }
}

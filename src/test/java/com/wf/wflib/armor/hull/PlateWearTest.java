package com.wf.wflib.armor.hull;

import com.google.gson.Gson;
import com.wf.wflib.armor.api.hull.PlateSpec;
import com.wf.wflib.armor.api.hull.PlateWear;
import net.minecraft.nbt.CompoundTag;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlateWearTest {
    private static PlateSpec spec(String json) {
        PlateSpec s = new Gson().fromJson(json, PlateSpec.class);
        s.compile("t");
        return s;
    }

    @Test
    void brittleFollowsMaterial() {
        assertTrue(spec("{\"layers\":[{\"mm\":40,\"mat\":\"laminated\"}]}").brittle());
        assertTrue(spec("{\"layers\":[{\"mm\":6,\"mat\":\"glass\"}]}").brittle());
        assertFalse(spec("{\"layers\":[{\"mm\":6,\"mat\":\"rha\"}]}").brittle());
        assertEquals(14, spec("{\"layers\":[{\"mm\":40,\"mat\":\"laminated\"}]}").nominal(false), 1e-4F);
    }

    @Test
    void everyHitWearsAndDropsOldestChip() {
        PlateWear w = new PlateWear();
        assertEquals(1, w.thicknessScale("ws"));
        for (int i = 0; i < PlateWear.MAX_CHIPS + 2; i++) {
            w.hit("ws", 3, new Vector3f(0.1f * i, 1.2f, 1.9f), 0, 14);
        }
        PlateWear.State s = w.state("ws");
        assertNotNull(s);
        assertEquals(PlateWear.MAX_CHIPS, s.count());
        assertEquals(1.0f, s.wear(), 1e-6F);
        assertEquals(PlateWear.FLOOR, w.thicknessScale("ws"), 1e-6F);
        assertEquals(0.2f, s.point(0, new Vector3f()).x, 1e-3F, "two oldest dropped");
        assertEquals(1.2f, s.point(0, new Vector3f()).y, 1e-3F);
        assertEquals(1, w.thicknessScale("other"));
    }

    @Test
    void damageBeyondMinimumWearsMore() {
        PlateWear w = new PlateWear();
        float soft = w.hit("a", 0, new Vector3f(), 1, 14);
        float hard = w.hit("b", 0, new Vector3f(), 40, 14);
        assertEquals(PlateWear.MIN_HIT, soft, 1e-6F);
        assertEquals(40 / (14 * PlateWear.POINTS_PER_MM), hard, 1e-6F);
    }

    @Test
    void syncRoundTripAndRepair() {
        PlateWear w = new PlateWear();
        w.hit("ws", 200, new Vector3f(-0.5f, 1.1f, 2.0f), 10, 14);
        assertTrue(w.dirty());
        CompoundTag tag = w.toSync();
        assertFalse(w.dirty());
        PlateWear client = new PlateWear();
        client.fromSync(tag);
        assertEquals(w.wear("ws"), client.wear("ws"), 0);
        Vector3f p = client.state("ws").point(0, new Vector3f());
        assertEquals(-0.5f, p.x, 1e-3F);
        assertEquals(2.0f, p.z, 1e-3F);
        assertEquals(w.state("ws").variant(0), client.state("ws").variant(0));
        assertEquals(200, client.state("ws").node(0));
        assertTrue(w.repair("ws"));
        assertEquals(1, w.thicknessScale("ws"));
        assertFalse(w.repair("ws"));
    }
}

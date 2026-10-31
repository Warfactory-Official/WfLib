package com.wf.wflib.particle.xr;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XrDefsTest {
    private static String resource(String path) throws IOException {
        try (InputStream in = XrDefsTest.class.getResourceAsStream("/assets/wflib/xray/particles/" + path)) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    @Test
    void readsAnEffectsSlotsByTheirSortedNames() throws IOException {
        XrDefs.Effect e = XrDefs.Effect.parse("anomaly2\\effects\\electra2_flash_new_00",
                resource("anomaly2/effects/electra2_flash_new_00.pe"));
        assertEquals(24, e.maxParticles());
        assertEquals(0.1F, e.timeLimit(), 1e-6);
        assertEquals(com.wf.wflib.particle.api.XrBlend.ALPHA_ADD, e.blend());
        assertEquals("wflib:textures/xray/unrealengine/electricblast2.png", e.texture().toString());
        assertEquals(8, e.frameDimX());
        assertEquals(64, e.frameCount());
        XrDefs.Action killOld = e.actions().get(0);
        assertEquals(XrDefs.KILL_OLD, killOld.type());
        assertEquals(3.0F, killOld.f(0), 1e-6); // Age Limit
        XrDefs.Action source = e.actions().get(1);
        assertEquals(XrDefs.SOURCE, source.type());
        assertEquals(1000.0F, source.f(3), 1e-3); // Rate, after Age Sigma, Color\Alpha, Parent Motion
        assertEquals(1.0F, source.f(1), 1e-6); // Color\Alpha
        assertEquals(5, source.d(4).type()); // Velocity: a sphere
        assertArrayEquals(new float[]{1, 1, 1}, new float[]{source.d(0).a()[0], source.d(0).a()[1],
                source.d(0).a()[2]}, 1e-6F); // Color: white
        XrDefs.Action size = e.actions().get(3);
        assertArrayEquals(new float[]{6, 6, 6}, size.v(0), 1e-6F); // Scale
        assertArrayEquals(new float[]{2, 2, 2}, size.v(1), 1e-6F); // Size
    }

    @Test
    void groupsKeepOnlyEnabledEffects() throws IOException {
        XrDefs.Group hit = XrDefs.Group.parse(resource("zones_mine/zone_acidic_mine_hit.pg"));
        assertEquals(2.0F, hit.timeLimit(), 1e-6);
        assertEquals(3, hit.entries().size(), "two of the five are flagged off");
        XrDefs.Group blast = XrDefs.Group.parse(resource("anomaly2/electra2_blast.pg"));
        assertEquals(12, blast.entries().size());
        assertTrue(blast.entries().stream().anyMatch(g -> g.time0() > 0.0F), "some start late");
    }

    @Test
    void sphereDomainsStayWithinTheirRadius() {
        XrDefs.Domain sphere = new XrDefs.Domain(5, new float[]{0, 1, 0, 0, 5, 0, 0, 0, 0});
        RandomSource random = RandomSource.create(9L);
        float[] out = new float[3];
        for (int i = 0; i < 1000; i++) {
            sphere.generate(random, out);
            float d = (float) Math.sqrt(out[0] * out[0] + (out[1] - 1) * (out[1] - 1) + out[2] * out[2]);
            assertTrue(d <= 5.0F + 1e-4F, "inside radius 5: " + d);
        }
    }
}

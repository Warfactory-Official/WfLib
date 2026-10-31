package com.wf.wflib.sway;

import com.wf.wflib.audio.api.Acoustics;
import com.wf.wflib.geom.api.Segments;
import com.wf.wflib.sway.api.PainSway;
import com.wf.wflib.suppression.api.Suppression;
import com.wf.wflib.sway.client.PainCameraProbe;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Hashes recorded from the pre-consolidation dependent copies (2026-10-30); camera = same wobble scaled 0.72. */
class SwayGoldenTest {
    @Test
    void wobbleMatchesTheWeaponPainSway() {
        assertEquals(0xf73029e99c709044L, W3Golden.wobble(PainSway::wobble));
    }

    @Test
    void cameraPainIsTheWobbleScaled() {
        assertEquals(0xdc923bdbab1381ddL, W3Golden.camera(PainCameraProbe::add));
    }

    @Test
    void segmentClosestMatchesTheSuppressionCopy() {
        assertEquals(0x6dacac6f2868af12L, W3Golden.closest(Segments::closest));
    }

    @Test
    void acousticsClosestUnchanged() {
        assertEquals(0x59aaacfd8a5bc6b0L, W3Golden.acoustic(Acoustics::closest));
    }

    @Test
    void suppressionImpulseUnchanged() {
        assertEquals(0x93cc45c4264f488bL, W3Golden.impulse(Suppression::nearMiss));
    }

    /** {@code Rounds.nearMiss} inlines the parameter (hot loop); same doubles for every non-degenerate step. */
    @Test
    void serverNearMissParameterIsSegmentsT() {
        SplittableRandom r = new SplittableRandom(6);
        for (int i = 0; i < 100_000; i++) {
            Vec3 from = new Vec3(r.nextDouble(-50, 50), r.nextDouble(-50, 50), r.nextDouble(-50, 50));
            Vec3 to = from.add(r.nextDouble(-8, 8), r.nextDouble(-8, 8), r.nextDouble(-8, 8));
            Vec3 eye = from.add(r.nextDouble(-10, 10), r.nextDouble(-10, 10), r.nextDouble(-10, 10));
            Vec3 d = to.subtract(from);
            double len2 = d.lengthSqr();
            double server = len2 == 0.0 ? 0.0 : eye.subtract(from).dot(d) / len2;
            assertEquals(Double.doubleToRawLongBits(server), Double.doubleToRawLongBits(Segments.t(from, to, eye)));
        }
    }
}

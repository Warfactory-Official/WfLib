package com.wf.wflib.sway.client;

import com.wf.wflib.sway.api.client.CameraSway;
import net.minecraft.client.player.LocalPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraSwayMixTest {
    static final class Fixed implements CameraSway.Source {
        float yaw, pitch;
        int resets;

        @Override
        public void offset(LocalPlayer p, double time, float[] out) {
            out[0] += yaw;
            out[1] += pitch;
        }

        @Override
        public void reset() {
            resets++;
        }
    }

    @Test
    void twoSourcesApplyAsOneDelta() {
        Fixed a = new Fixed(), b = new Fixed();
        List<CameraSway.Source> both = List.of(a, b);
        CameraSwayMix mix = new CameraSwayMix();
        float[] d = new float[2];
        a.yaw = 1;
        b.yaw = 0.5F;
        b.pitch = -2;
        assertTrue(mix.step(both, null, 1, true, d));
        assertArrayEquals(new float[]{1.5F, -2}, d);
        a.yaw = 2;
        assertTrue(mix.step(both, null, 2, true, d));
        assertArrayEquals(new float[]{1, 0}, d, "delta vs the applied sum");
        assertFalse(mix.step(both, null, 3, true, d), "unchanged sum writes nothing");
        assertTrue(mix.step(both, null, 4, false, d));
        assertArrayEquals(new float[]{-2.5F, 2}, d, "not live => back to 0");
        assertEquals(1, a.resets);
    }

    @Test
    void logoutResetForgetsTheAppliedOffset() {
        Fixed a = new Fixed();
        CameraSwayMix mix = new CameraSwayMix();
        float[] d = new float[2];
        a.yaw = 3;
        mix.step(List.of(a), null, 1, true, d);
        mix.reset(List.of(a));
        assertEquals(1, a.resets);
        assertTrue(mix.step(List.of(a), null, 1, true, d));
        assertArrayEquals(new float[]{3, 0}, d, "next world starts from 0, no undo");
    }
}

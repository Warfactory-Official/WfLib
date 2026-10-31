package com.wf.wflib.sway.client;

import com.wf.wflib.sway.api.PainSway;
import net.minecraft.world.entity.player.Player;

/** Test access to {@link PainCamera#add}. */
public final class PainCameraProbe {
    private PainCameraProbe() {
    }

    public static void add(float amp, float intensity, float ads, float aimFloor, double time, long seed, float[] out) {
        PainCamera.add(new PainSway.Source() {
            @Override
            public float intensity(Player p) {
                return intensity;
            }

            @Override
            public float amplitudeDeg() {
                return amp;
            }
        }, intensity, ads, aimFloor, time, seed, out);
    }
}

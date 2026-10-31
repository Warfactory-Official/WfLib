package com.wf.wflib.sway.client;

import com.wf.wflib.sway.api.client.CameraSway;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;

final class CameraSwayMix {
    private final float[] sum = new float[2];
    private float appliedYaw;
    private float appliedPitch;

    /** {@code live} false => sources reset, target 0. Returns false when the delta is 0. */
    boolean step(List<CameraSway.Source> sources, LocalPlayer p, double time, boolean live, float[] delta) {
        sum[0] = sum[1] = 0;
        for (CameraSway.Source s : sources) {
            if (live) {
                s.offset(p, time, sum);
            } else {
                s.reset();
            }
        }
        delta[0] = sum[0] - appliedYaw;
        delta[1] = sum[1] - appliedPitch;
        if (delta[0] == 0 && delta[1] == 0) {
            return false;
        }
        appliedYaw = sum[0];
        appliedPitch = sum[1];
        return true;
    }

    void reset(List<CameraSway.Source> sources) {
        for (CameraSway.Source s : sources) {
            s.reset();
        }
        appliedYaw = appliedPitch = 0;
    }
}

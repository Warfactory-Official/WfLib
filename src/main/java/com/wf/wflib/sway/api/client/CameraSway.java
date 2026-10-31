package com.wf.wflib.sway.api.client;

import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.client.player.LocalPlayer;

/**
 * The one writer of sway into the local player's rotation: sources summed, applied as the delta vs the last applied
 * sum. Skipped while paused / a screen is open; spectator => every source reset, offset 0.
 */
public final class CameraSway {
    public interface Source {
        /** Adds this source's offset, degrees {@code [yaw, pitch]}; {@code time} = unpaused client ticks. */
        void offset(LocalPlayer p, double time, float[] yawPitchDeg);

        void reset();
    }

    public static final OrderedRegistry<Source> SOURCES = OrderedRegistry.create("sway", "camera sway sources");

    private CameraSway() {
    }
}

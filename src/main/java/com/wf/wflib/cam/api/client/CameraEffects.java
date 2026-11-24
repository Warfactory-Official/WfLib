package com.wf.wflib.cam.api.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Share of third-party camera motion (Camera Overhaul pitch/roll offsets) let through: min over gates, 0 => none.
 * Aiming views register 0 so the picture stays on the look vector; hip and walking keep it.
 */
public final class CameraEffects {

    /** @return 0..1, 1 = effects fully allowed */
    @FunctionalInterface
    public interface Gate {
        float weight(float partialTick);
    }

    private static final List<Gate> GATES = new CopyOnWriteArrayList<>();

    private CameraEffects() {
    }

    public static void register(Gate gate) {
        GATES.add(gate);
    }

    public static float weight(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (FeedPass.active() || mc.getCameraEntity() != mc.player) {
            return 0.0f;
        }
        float w = 1.0f;
        for (Gate gate : GATES) {
            w = Math.min(w, gate.weight(partialTick));
        }
        return Mth.clamp(w, 0.0f, 1.0f);
    }
}

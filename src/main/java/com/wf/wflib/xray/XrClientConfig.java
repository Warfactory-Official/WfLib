package com.wf.wflib.xray;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code wflib-xray-client.toml}: STALKER (GAMMA) screen and camera effects (anomalies near, hits, psy, healing). */
public final class XrClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue STRENGTH;
    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        STRENGTH = b.comment("Post-process effects (colour, double vision, noise), 0 off .. 1 as GAMMA.")
                .defineInRange("strength", 1.0D, 0.0D, 1.0D);
        CAMERA_SHAKE = b.comment("Camera kicks and sway, 0 off .. 1 as GAMMA.")
                .defineInRange("cameraShake", 1.0D, 0.0D, 1.0D);
        SPEC = b.build();
    }

    private XrClientConfig() {
    }
}

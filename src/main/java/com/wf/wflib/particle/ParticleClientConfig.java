package com.wf.wflib.particle;

import com.wf.gemrender.water.Absorbance;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code wflib-particle-client.toml}. */
public final class ParticleClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.EnumValue<Resolution> RESOLUTION;

    public enum Resolution {
        FULL(1), HALF(2), QUARTER(4);

        final int factor;

        Resolution(int factor) {
            this.factor = factor;
        }
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        RESOLUTION = b
                .comment("Resolution smoke, gas and dust are drawn at before being scaled up to the screen.",
                        "Soft smoke loses nothing visible at QUARTER (1/16 of the pixels), and a screen full of",
                        "smoke costs a fraction of FULL. Edges of blocks and models in front of the smoke stay",
                        "sharp. FULL only if you see smoke bleeding around thin objects.")
                .defineEnum("particleResolution", Resolution.QUARTER);
        SPEC = b.build();
    }

    private ParticleClientConfig() {
    }

    /** Loaded value -> GemRender, one-way. */
    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            Absorbance.getInstance().resolutionFactor(RESOLUTION.get().factor);
        }
    }
}

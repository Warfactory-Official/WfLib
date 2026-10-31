package com.wf.wflib.mist.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code wflib-mist-client.toml}. */
public final class MistClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue VOLUMETRIC;
    private static final ModConfigSpec.ConfigValue<String> QUALITY;
    private static final String VOLUMETRIC_PROPERTY = "wflib.volumetricGas";
    private static final String QUALITY_PROPERTY = "wflib.volumetricGasQuality";

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        VOLUMETRIC = b
                .comment("Draw gas clouds by raymarching a density field through the cloud's bounding box",
                        "instead of stacking hundreds of billboard particles. One draw call per cloud, with",
                        "self-shadowing, so a cloud reads as lit smoke with real depth rather than as a pile",
                        "of sprites, and it no longer thins out when you walk into it, because there are no",
                        "sprites to run out of. The cost moves from particle count to screen coverage: a",
                        "distant cloud is nearly free and one filling the screen is the expensive case, which",
                        "is the opposite of how the billboard path behaves. Needs Flywheel.")
                .define("volumetric", false);
        QUALITY = b
                .comment("Steps the raymarch takes, and whether it marches a second time toward the key",
                        "light for self-shadowing: LOW is 24 steps and no shadowing, which reads as flat fog;",
                        "MEDIUM is 48 with a 4-step light march; HIGH is 96 with 6. MEDIUM is the first",
                        "setting that looks like smoke. Ignored unless volumetric is on.")
                .define("volumetricQuality", "MEDIUM");
        SPEC = b.build();
    }

    private MistClientConfig() {
    }

    /** System property {@code wflib.volumetricGas} overrides (dev runs); unbaked SPEC => false. */
    public static boolean volumetricGas() {
        String override = System.getProperty(VOLUMETRIC_PROPERTY);
        if (override != null) {
            return Boolean.parseBoolean(override);
        }
        return SPEC.isLoaded() && VOLUMETRIC.get();
    }

    /** System property {@code wflib.volumetricGasQuality} overrides; unbaked SPEC => MEDIUM. */
    public static String volumetricGasQuality() {
        String override = System.getProperty(QUALITY_PROPERTY);
        if (override != null) {
            return override;
        }
        return SPEC.isLoaded() ? QUALITY.get() : "MEDIUM";
    }
}

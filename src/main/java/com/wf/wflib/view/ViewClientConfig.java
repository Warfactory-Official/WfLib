package com.wf.wflib.view;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code wflib-view-client.toml}. */
public final class ViewClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ARM_TEST_SKIN;
    private static final String ARM_TEST_SKIN_PROPERTY = "wflib.armTestSkin";

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ARM_TEST_SKIN = b
                .comment("Draw the first-person arms with a labelled test texture instead of your skin: each side",
                        "of the arm its own colour, the elbow crease (IN) and elbow point (ELB) marked, and the",
                        "fist split into palm, back, thumb, pinky and knuckles. For checking how arms and hands",
                        "sit on a gun. Off by default.")
                .define("armTestSkin", false);
        SPEC = b.build();
    }

    private ViewClientConfig() {
    }

    /** System property {@code wflib.armTestSkin} overrides; unbaked SPEC => false. */
    public static boolean armTestSkin() {
        String override = System.getProperty(ARM_TEST_SKIN_PROPERTY);
        if (override != null) {
            return Boolean.parseBoolean(override);
        }
        return SPEC.isLoaded() && ARM_TEST_SKIN.get();
    }
}

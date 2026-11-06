package com.wf.wflib.ui.api;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code wflib-ui-client.toml}. */
public final class UiClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue KEY_HINTS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        KEY_HINTS = b.comment("Show keybind hints in the bottom corners of the HUD (guns, vehicles).")
                .define("keyHints", true);
        SPEC = b.build();
    }

    private UiClientConfig() {
    }

    public static boolean keyHints() {
        return !SPEC.isLoaded() || KEY_HINTS.get();
    }

    public static void toggleKeyHints() {
        KEY_HINTS.set(!KEY_HINTS.get());
        KEY_HINTS.save();
    }
}

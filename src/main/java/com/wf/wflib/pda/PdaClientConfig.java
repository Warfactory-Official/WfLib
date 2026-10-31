package com.wf.wflib.pda;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code wflib-pda-client.toml}. Also changed from the gear in the PDA's top bar. */
public final class PdaClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> THEME;
    public static final ModConfigSpec.BooleanValue EFFECTS;
    public static final ModConfigSpec.BooleanValue SCANLINES;
    public static final ModConfigSpec.BooleanValue FLICKER;
    public static final ModConfigSpec.BooleanValue NOISE;
    public static final ModConfigSpec.BooleanValue VIGNETTE;
    public static final ModConfigSpec.BooleanValue SOUNDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        THEME = b.comment("Theme id: classic, gamma, zone or phosphor (old tactical selects gamma).")
                .define("theme", "classic");
        EFFECTS = b.comment("Master switch for the screen effects (scanlines, flicker, noise, vignette, bezel).")
                .define("effects", true);
        SCANLINES = b.define("scanlines", true);
        FLICKER = b.define("flicker", false);
        NOISE = b.define("noise", true);
        VIGNETTE = b.define("vignette", true);
        SOUNDS = b.comment("Bundled PDA hover and click sounds (including tab/page changes).").define("sounds", true);
        SPEC = b.build();
    }

    private PdaClientConfig() {
    }
}
